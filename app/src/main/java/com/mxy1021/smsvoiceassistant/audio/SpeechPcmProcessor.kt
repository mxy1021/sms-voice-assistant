package com.mxy1021.smsvoiceassistant.audio

import kotlin.math.*

object SpeechGain {
    val levels = listOf(0, 3, 6, 9)
    fun restored(value: Int) = when (value) {
        in levels -> value
        12, 15, 18 -> 9 // Candidate-only settings safely step down after the user's scope change.
        else -> 0
    }
}

/** Bounded offline processing of the already complete PCM. No padding, time stretch or global effect.
 * Energy gating is deliberately not semantic VAD or a LUFS/SPL measurement.
 */
object SpeechPcmProcessor {
    const val TARGET_ACTIVE_DBFS = -12.0
    const val MAX_BASE_BOOST_DB = 9.0
    private val ceiling = 10.0.pow(-2.0 / 20)
    private val interpolatedCeiling = 10.0.pow(-1.2 / 20)
    data class Result(val baseGainDb: Double, val minimumLimiterGain: Double,
                      val finalCorrectionDb: Double)

    fun process(data: ByteArray, rate: Int, channels: Int, gainDb: Int,
                cancelled: () -> Boolean = { false }): Result {
        require(gainDb in SpeechGain.levels && rate in 8000..48000 && channels in 1..2)
        require(data.isNotEmpty() && data.size <= 1048576 && data.size % (2 * channels) == 0)
        val frames = data.size / (channels * 2)
        require(frames.toLong() * 1000 <= rate.toLong() * 5000)
        fun checkAlive() { if (cancelled() || Thread.currentThread().isInterrupted) throw InterruptedException() }
        fun sample(index: Int) = ((data[2*index].toInt() and 255) or
            (data[2*index+1].toInt() shl 8)).toShort().toDouble() / 32768
        fun put(index: Int, value: Double) {
            // The envelope, not integer clipping, establishes the ceiling before quantization.
            check(abs(value) < 1.0)
            val quantized = (value * 32768).roundToInt()
            data[2*index] = quantized.toByte(); data[2*index+1] = (quantized shr 8).toByte()
        }
        val window = max(1, rate / 50)
        var energy = 0.0; var activeSamples = 0
        for (start in 0 until frames step window) {
            checkAlive()
            val end = min(frames, start + window)
            var sum = 0.0
            for (i in start*channels until end*channels) { val v = sample(i); sum += v*v }
            val count = (end-start)*channels
            if (sum / count > 1e-5) { energy += sum; activeSamples += count }
        }
        if (activeSamples == 0) return Result(0.0, 1.0, 0.0) // Preserve silence/low noise exactly.
        val rmsDb = 10 * log10(energy / activeSamples)
        val baseDb = (TARGET_ACTIVE_DBFS - rmsDb).coerceIn(-12.0, MAX_BASE_BOOST_DB)
        val gain = 10.0.pow((baseDb + gainDb) / 20)
        val envelope = FloatArray(frames)
        try {
            // Stereo-linked required gain; backward pass anticipates peaks with a 5ms attack.
            // min(required, future envelope) guarantees each sample fits before conversion.
            val attack = exp(1.0 / (rate * .005))
            var next = 1.0
            for (frame in frames-1 downTo 0) {
                if (frame % window == 0) checkAlive()
                var peak = 0.0
                for (ch in 0 until channels) peak = max(peak, abs(sample(frame*channels+ch))*gain)
                val required = if (peak > ceiling) ceiling / peak else 1.0
                next = min(required, min(1.0, next * attack))
                envelope[frame] = next.toFloat()
            }
            val release = exp(-1.0 / (rate * .050))
            var previous = 1.0; var minimum = 1.0
            for (frame in 0 until frames) {
                if (frame % window == 0) checkAlive()
                previous = min(envelope[frame].toDouble(), 1 - (1-previous)*release)
                minimum = min(minimum, previous)
                for (ch in 0 until channels) {
                    val index = frame*channels+ch
                    put(index, sample(index)*gain*previous)
                }
            }
            // Inter-sample overshoot guard: 4x/32-tap estimate, then uniform attenuation.
            // This does not introduce a new limiter discontinuity or change PCM duration.
            var truePeak = 0.0
            for (i in 0 until frames*channels) truePeak = max(truePeak, abs(sample(i)))
            for (coefficients in phases) for (frame in 0 until frames) {
                if (frame % window == 0) checkAlive()
                for (ch in 0 until channels) {
                    var value = 0.0
                    for (n in coefficients.indices) {
                        val source = frame + n - 15
                        if (source in 0 until frames) value += sample(source*channels+ch)*coefficients[n]
                    }
                    truePeak = max(truePeak, abs(value))
                }
            }
            val correction = min(1.0, interpolatedCeiling / truePeak)
            if (correction < 1) for (frame in 0 until frames) {
                if (frame % window == 0) checkAlive()
                for (ch in 0 until channels) { val i = frame*channels+ch; put(i, sample(i)*correction) }
            }
            checkAlive()
            return Result(baseDb, minimum, 20*log10(correction))
        } finally { envelope.fill(0f) }
    }

    private val phases = (1..3).map { phase ->
        DoubleArray(32) { n ->
            val t = n - 15 - phase/4.0
            (if (abs(t) < 1e-12) 1.0 else sin(PI*t)/(PI*t)) * (.5 + .5*cos(PI*t/16))
        }.also { coefficients -> val sum = coefficients.sum(); coefficients.indices.forEach { coefficients[it] /= sum } }
    }
}

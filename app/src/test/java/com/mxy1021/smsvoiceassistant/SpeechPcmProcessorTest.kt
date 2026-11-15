package com.mxy1021.smsvoiceassistant

import com.mxy1021.smsvoiceassistant.audio.*
import com.mxy1021.smsvoiceassistant.settings.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class SpeechPcmProcessorTest {
    private fun signal(amplitude: Double, frames: Int = 2400, channels: Int = 1,
                       frequency: Double = 1000.0): ByteArray = ByteArray(frames*channels*2).also { data ->
        for (f in 0 until frames) for (ch in 0 until channels) {
            val v = (sin(2*PI*frequency*f/24000)*amplitude*(if (ch == 0) 1.0 else .5)*32767).roundToInt()
            val i = (f*channels+ch)*2; data[i] = v.toByte(); data[i+1] = (v shr 8).toByte()
        }
    }
    private fun value(data: ByteArray, i: Int) = ((data[2*i].toInt() and 255) or
        (data[2*i+1].toInt() shl 8)).toShort().toDouble()/32768
    private fun rms(data: ByteArray) = sqrt((0 until data.size/2).sumOf { value(data,it).pow(2) }/(data.size/2))
    private fun peak(data: ByteArray) = (0 until data.size/2).maxOf { abs(value(data,it)) }

    @Test fun quietSpeechGetsBoundedBaseBoostAndSilenceIsUnchanged() {
        val quiet = signal(.01); val original = rms(quiet)
        val result = SpeechPcmProcessor.process(quiet,24000,1,0)
        assertEquals(9.0,result.baseGainDb,1e-9)
        assertEquals(9.0,20*log10(rms(quiet)/original),.01)
        val silence = ByteArray(240000)
        assertEquals(0.0,SpeechPcmProcessor.process(silence,24000,1,9).baseGainDb,0.0)
        assertTrue(silence.all { it == 0.toByte() })
    }
    @Test fun ordinaryUtterancesNormalizeToSameActiveRms() {
        val a = signal(.20); val b = signal(.30)
        SpeechPcmProcessor.process(a,24000,1,0); SpeechPcmProcessor.process(b,24000,1,0)
        assertEquals(-12.0,20*log10(rms(a)),.02)
        assertEquals(20*log10(rms(a)),20*log10(rms(b)),.02)
    }
    @Test fun fourNominalGainsAreExactBeforeLimiter() {
        // A very quiet but active signal reaches the base cap, leaving headroom.
        SpeechGain.levels.forEach { db ->
            val data = signal(.0046); val before = rms(data)
            val result = SpeechPcmProcessor.process(data,24000,1,db)
            assertEquals(9.0,result.baseGainDb,1e-9)
            assertEquals(db+9.0,20*log10(rms(data)/before),.05)
            assertEquals(1.0,result.minimumLimiterGain,0.0)
        }
    }
    @Test fun highGainHasSmoothGainReductionInsteadOfHardClipping() {
        val data = signal(.6,24000,1,300.0)
        val result = SpeechPcmProcessor.process(data,24000,1,9)
        assertTrue(result.minimumLimiterGain < 1)
        assertTrue(peak(data) < .80)
        assertTrue((0 until data.size/2).none { abs(value(data,it)) >= 32767.0/32768 })
        // After attack, measure residual against the fundamental (hard clipping adds harmonics).
        val first = 2400; val count = data.size/2-first
        var sine = 0.0; var cosine = 0.0; var energy = 0.0
        for (i in first until data.size/2) {
            val v = value(data,i); val phase = 2*PI*300*i/24000
            sine += v*sin(phase); cosine += v*cos(phase); energy += v*v
        }
        val fundamental = 2*(sine*sine+cosine*cosine)/count
        assertTrue("residual/fundamental must stay below 3%", sqrt(max(0.0,energy-fundamental)/fundamental) < .03)
    }
    @Test fun negativeFullScaleAndAlternatingPeaksRemainSafeAtEveryGain() {
        SpeechGain.levels.forEach { db ->
            val data = ByteArray(9600) { i -> when(i%4) {0 -> 0; 1 -> -128; else -> 127}.toByte() }
            SpeechPcmProcessor.process(data,24000,1,db)
            assertTrue(peak(data) <= .80)
        }
    }
    @Test fun stereoIsLinkedWithoutChangingBalanceOrDuration() {
        val data = signal(.5,12000,2)
        val length = data.size
        SpeechPcmProcessor.process(data,24000,2,9)
        assertEquals(length,data.size)
        for (f in 0 until data.size/4) if (abs(value(data,f*2)) > .05)
            assertEquals(.5,value(data,f*2+1)/value(data,f*2),.002)
    }
    @Test fun silenceAndLowNoiseDoNotBecomeLoudOrGainDcOffset() {
        val data = signal(.0001); val before = data.clone()
        SpeechPcmProcessor.process(data,24000,1,9)
        assertArrayEquals(before,data)
    }
    @Test fun cancelledProcessingThrowsBeforeAnyPlaybackCanBeAdmitted() {
        val data = signal(.2,24000)
        var checks = 0
        assertThrows(InterruptedException::class.java) {
            SpeechPcmProcessor.process(data,24000,1,9) { ++checks > 55 }
        }
        assertTrue(checks > 55)
    }
    @Test fun invalidFormatLengthGainAndOverBudgetAreRejectedWhole() {
        listOf(-3,1,7,12,15,18,19).forEach { db -> assertThrows(IllegalArgumentException::class.java) {
            SpeechPcmProcessor.process(signal(.1),24000,1,db)
        } }
        assertThrows(IllegalArgumentException::class.java) { SpeechPcmProcessor.process(ByteArray(3),24000,1,0) }
        assertThrows(IllegalArgumentException::class.java) { SpeechPcmProcessor.process(ByteArray(240002),24000,1,0) }
        assertThrows(IllegalArgumentException::class.java) { SpeechPcmProcessor.process(ByteArray(1048578),48000,2,0) }
        assertEquals(0.0,SpeechPcmProcessor.process(ByteArray(960000),48000,2,0).baseGainDb,0.0)
    }
    @Test fun legacyGainAndEveryNewStepPersistAcrossProcessAndBoot() {
        SpeechGain.levels.forEach { gain ->
            val store = MemorySettingsStore(mutableMapOf("speechGainDb" to gain,"target" to "synthetic", "bootRestoreEnabled" to true))
            val repo = SettingsRepository(store,2); val before = repo.snapshot()
            assertEquals(gain,before.speechGainDb)
            assertEquals(before,SettingsRepository(store,3).snapshot())
        }
        listOf(12,15,18).forEach { removed ->
            val store = MemorySettingsStore(mutableMapOf("speechGainDb" to removed,"target" to "synthetic"))
            assertEquals(9,SettingsRepository(store,2).snapshot().speechGainDb)
            assertEquals(9,store.data["speechGainDb"])
            assertEquals("synthetic",store.data["target"])
        }
    }
    @Test fun themeSwitchesPersistWithoutInvalidatingSpeechOrChangingOtherSettings() {
        val store = MemorySettingsStore(); val repo = SettingsRepository(store,1)
        repo.selectDevice("synthetic"); repo.setEnabled(true); repo.setSpeechGain(6)
        val before = repo.snapshot(); val permit = repo.permit(true)!!
        DisplayMode.entries.forEach { mode ->
            repo.setDisplayMode(mode)
            assertEquals(before.copy(displayMode=mode),SettingsRepository(store,1).snapshot())
            assertTrue(repo.valid(permit))
        }
        assertTrue(DisplayMode.SYSTEM.dark(true)); assertFalse(DisplayMode.SYSTEM.dark(false))
        assertTrue(DisplayMode.DARK.dark(false)); assertFalse(DisplayMode.LIGHT.dark(true))
        assertEquals(DisplayMode.SYSTEM,DisplayMode.fromCode(999))
        repo.setSpeechGain(9); assertFalse(repo.valid(permit))
    }
}

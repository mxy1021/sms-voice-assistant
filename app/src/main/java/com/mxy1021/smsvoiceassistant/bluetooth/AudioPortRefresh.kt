package com.mxy1021.smsvoiceassistant.bluetooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.os.Handler
import android.os.Looper
import com.mxy1021.smsvoiceassistant.audio.Stage0Log

/** Bounded foreground cache recovery, never a speech session or proof of a matching target.
 * Android refreshes its audio-port generation before dispatching an AudioTrack routing event.
 * This track has volume zero and NEVER receives PCM (not even silence), requests no focus,
 * and can only prefer an existing Bluetooth output. No hidden API or address substitution.
 */
class AudioPortRefresh(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var track: AudioTrack? = null
    private var listener: AudioRouting.OnRoutingChangedListener? = null
    private var timeout: Runnable? = null
    val running get() = track != null

    fun start(complete: (Boolean) -> Unit) {
        cancel()
        fun finish(observed: Boolean) {
            cancel()
            Stage0Log.state("context", "audioPortRefresh", "routingCallback=$observed;pcmBytesWritten=0;focusRequested=false;released=true")
            complete(observed)
        }
        try {
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                finish(false); return
            }
            val audio = context.getSystemService(AudioManager::class.java)
            val output = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { BluetoothProbe.bluetooth(it) }
            if (output == null) { finish(false); return }
            val minimum = AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minimum <= 0) { finish(false); return }
            val player = AudioTrack.Builder().setAudioAttributes(BluetoothProbe.attributes)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(minimum).build()
            track = player
            if (player.state != AudioTrack.STATE_INITIALIZED || player.setVolume(0f) != AudioTrack.SUCCESS ||
                !player.setPreferredDevice(output)) { finish(false); return }
            listener = AudioRouting.OnRoutingChangedListener {
                // Route readiness is deliberately NOT inferred here; the caller must run full inspection.
                if (track === player) finish(true)
            }.also { player.addOnRoutingChangedListener(it, main) }
            timeout = Runnable { if (track === player) finish(false) }.also { main.postDelayed(it, 800) }
            player.play() // No write(): impossible to output speech, a code or any PCM samples.
        } catch (_: Exception) { finish(false) }
    }

    fun cancel() {
        timeout?.let { main.removeCallbacks(it) }; timeout = null
        val player = track; track = null
        listener?.let { callback -> runCatching { player?.removeOnRoutingChangedListener(callback) } }
        listener = null
        runCatching { player?.stop() }
        runCatching { player?.release() }
    }
}

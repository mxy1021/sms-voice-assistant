package com.mxy1021.smsvoiceassistant

import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mxy1021.smsvoiceassistant.audio.SpeechDependencies
import com.mxy1021.smsvoiceassistant.audio.SpeechResult
import com.mxy1021.smsvoiceassistant.sms.SmsSource
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Opt-in test APK only. No receiver, service, production switch, mapping or persistent control. */
@RunWith(AndroidJUnit4::class)
class HeadsetMediaProbeTest {
    @Test fun observeActualHeadsetKeyDuringOneSpeechSession() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Requires coordinated physical headset action", args.getString("stage11MediaProbe") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as ProbeApplication
        fun <T> main(block: () -> T): T {
            var result: T? = null
            instrumentation.runOnMainSync { result = block() }
            @Suppress("UNCHECKED_CAST") return result as T
        }
        assumeTrue(main { app.smsCoordinator.preflight() == null && app.session == null })
        val stopKey = args.getString("observedStopKey")?.toIntOrNull()
        val done = CompletableFuture<SpeechResult>()
        var media: MediaSession? = null
        var created = false; var released = false; var keyUps = 0; var cancelled = false
        fun releaseMedia() {
            media?.let { session ->
                try { session.isActive = false; session.setCallback(null); session.release() }
                finally { released = true; media = null }
            }
        }
        val deps = object : SpeechDependencies {
            override fun requestFocus(audio: AudioManager, request: AudioFocusRequest): Int {
                val result = super.requestFocus(audio, request)
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    val session = MediaSession(app, "SVA_TEMPORARY_PROBE")
                    media = session; created = true
                    session.setCallback(object : MediaSession.Callback() {
                        override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                            val key = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java) ?: return false
                            if (key.action == KeyEvent.ACTION_UP) keyUps++
                            instrumentation.sendStatus(2, Bundle().apply { putString("stream",
                                "\nSVA_MEDIA_KEY code=${key.keyCode};action=${key.action};repeat=${key.repeatCount};inputDevicePresent=${key.device != null};externalInput=${key.device?.isExternal};sourceAuthenticated=false\n") })
                            if (stopKey != null && key.keyCode == stopKey && key.action == KeyEvent.ACTION_UP && key.repeatCount == 0) {
                                cancelled = true; app.session?.cancel(); return true
                            }
                            return false // No default assumption that PLAY_PAUSE/NEXT means a double click.
                        }
                    }, app.main)
                    session.setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f)
                        .setActions(PlaybackState.ACTION_STOP).build())
                    session.isActive = true
                }
                return result
            }
            override fun abandonFocus(audio: AudioManager, request: AudioFocusRequest): Int {
                try { return super.abandonFocus(audio, request) } finally { releaseMedia() }
            }
        }
        try {
            main {
                val id = app.beginSmsEvent(SystemClock.elapsedRealtime(), SmsSource.DEBUG_SIMULATION)
                app.speak("现在进行耳机按键测试，请双击耳机。", false, fromSms = true, smsEventId = id,
                    dependencies = deps, result = { done.complete(it) })
            }
            val result = done.get(10, TimeUnit.SECONDS)
            assertTrue(result.focusReleased)
            assertNull(main { app.session })
            assertEquals(created, released)
            instrumentation.sendStatus(2, Bundle().apply { putString("stream",
                "\nSVA_MEDIA_PROBE created=$created;released=$released;keyUps=$keyUps;cancelled=$cancelled;musicContinuity=MANUAL_REQUIRED;targetIdentity=UNVERIFIED\n") })
            // No received key is a feasibility result, never a fake PASS on reliable delivery.
        } finally { main { app.session?.cancel(); releaseMedia() } }
    }
}

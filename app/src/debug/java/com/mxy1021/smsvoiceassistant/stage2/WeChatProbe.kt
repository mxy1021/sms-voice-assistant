package com.mxy1021.smsvoiceassistant.stage2

import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.ComponentName
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.PowerManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.mxy1021.smsvoiceassistant.ProbeApplication
import com.mxy1021.smsvoiceassistant.audio.PlatformSpeechDependencies
import com.mxy1021.smsvoiceassistant.audio.SpeechDependencies
import com.mxy1021.smsvoiceassistant.audio.SpeechSession
import com.mxy1021.smsvoiceassistant.audio.Stage0Log
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.flow.MutableStateFlow

/** Main-thread controller. The playback worker reads only the volatile listener reference. */
internal object WeChatProbe {
    const val WECHAT_PACKAGE = "com.tencent.mm"
    private const val TAG = "SVA_STAGE2"
    private const val FIXED_TEXT = "耳机语音测试。"
    private const val ARM_MS = 10 * 60 * 1000L

    enum class AudioSource { FIXED_UI, FIXED_NOTIFICATION, REAL_WECHAT }
    private data class Arm(val source: AudioSource, val armedWall: Long, val expiresAt: Long)
    private data class Observation(val id: Long, val lastPostTime: Long?)

    val entries = MutableStateFlow<List<String>>(emptyList())
    val bound = MutableStateFlow(false)
    val armed = MutableStateFlow<AudioSource?>(null)
    val fixedComplete = MutableStateFlow(false)
    val fixedHeard = MutableStateFlow(false)
    private var stage2Visible = false
    private var arm: Arm? = null
    private val expireArm = Runnable { clearArm(); log("ARM_EXPIRED") }
    private var activeSource: AudioSource? = null
    private var activeSession: SpeechSession? = null
    @Volatile private var listener: WeChatNotificationListener? = null
    private val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private val observations = LinkedHashMap<List<Byte>, Observation>()
    private var nextId = 0L
    private var nextCallback = 0L

    fun component(app: ProbeApplication) = ComponentName(app, WeChatNotificationListener::class.java)

    fun accessGranted(app: ProbeApplication): Boolean = runCatching {
        app.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component(app))
    }.getOrDefault(false)

    private fun allowed(app: ProbeApplication, service: WeChatNotificationListener) =
        listener === service && accessGranted(app)

    fun connected(app: ProbeApplication, service: WeChatNotificationListener) {
        val granted = accessGranted(app)
        val repeated = listener === service
        if (!repeated) stop(app)
        listener = service.takeIf { granted }
        bound.value = granted
        log("CONNECTED repeated=$repeated ${environment(app)}")
    }

    fun disconnected(app: ProbeApplication, service: WeChatNotificationListener, reason: String) {
        val current = listener === service
        if (current) {
            listener = null
            bound.value = false
            stop(app)
        }
        log("$reason current=$current ${environment(app)}")
    }

    fun requestUnbind(app: ProbeApplication) {
        val service = listener ?: return
        if (!allowed(app, service)) return
        log("UNBIND_REQUEST")
        runCatching { service.requestUnbind() }.onSuccess {
            listener = null
            bound.value = false
            stop(app)
        }.onFailure { log("UNBIND_ERROR") }
    }

    fun requestRebind(app: ProbeApplication) {
        if (listener != null || !accessGranted(app)) return
        log("REBIND_REQUEST")
        runCatching { NotificationListenerService.requestRebind(component(app)) }
            .onFailure { log("REBIND_ERROR") }
    }

    fun posted(app: ProbeApplication, service: WeChatNotificationListener, sbn: StatusBarNotification,
               wall: Long, elapsed: Long) {
        if (!allowed(app, service)) return
        val callback = ++nextCallback
        val key = digest("notification", sbn.key)
        val prior = observations[key]
        val id = prior?.id ?: ++nextId
        remember(key, Observation(id, sbn.postTime))
        val channel = sbn.notification.channelId?.let {
            val channelKey = digest("channel", it)
            observations[channelKey]?.id ?: (++nextId).also { alias ->
                remember(channelKey, Observation(alias, null))
            }
        }
        val fields = runCatching { WeChatFields(sbn.notification) }.getOrNull()
        log("POSTED source=WECHAT callback=$callback notification=$id channel=$channel " +
            "seenKey=${prior != null} samePostTime=${prior?.lastPostTime?.let { it == sbn.postTime }} " +
            "postWall=${sbn.postTime} callbackWall=$wall callbackElapsed=$elapsed " +
            "postToCallbackMs=${wall - sbn.postTime} ${environment(app)} ${fields?.shape() ?: "FIELDS_ERROR"}")
        val pending = arm ?: return
        if (SystemClock.elapsedRealtime() >= pending.expiresAt) {
            stop(app)
            log("ARM_EXPIRED")
            return
        }
        if (sbn.postTime < pending.armedWall || prior?.lastPostTime == sbn.postTime) return
        val speech = runCatching { fields?.testSpeech() }.getOrNull() ?: return
        stop(app)
        log("AUDIO_SELECTION callback=$callback fields=${speech.source}")
        start(app, pending.source, if (pending.source == AudioSource.REAL_WECHAT) speech.text else FIXED_TEXT,
            service, callback, elapsed)
    }

    fun removed(app: ProbeApplication, service: WeChatNotificationListener, sbn: StatusBarNotification, reason: Int) {
        if (!allowed(app, service)) return
        val key = digest("notification", sbn.key)
        val id = observations[key]?.id
        log("REMOVED source=WECHAT callback=${++nextCallback} notification=$id reason=$reason " +
            "postWall=${sbn.postTime} ${environment(app)}")
        observations.remove(key)
    }

    fun visibility(app: ProbeApplication, visible: Boolean) {
        stage2Visible = visible
        if (!visible && activeSource == AudioSource.FIXED_UI) activeSession?.cancel()
        log("ACTIVITY ${environment(app)}")
    }

    fun arm(app: ProbeApplication, source: AudioSource) {
        val service = listener
        if (!fixedHeard.value || service == null || !allowed(app, service) ||
            app.settings.snapshot().announcingEnabled || app.session != null) {
            log("ARM_REJECTED")
            return
        }
        stop(app)
        arm = Arm(source, System.currentTimeMillis(), SystemClock.elapsedRealtime() + ARM_MS)
        armed.value = source
        app.main.postDelayed(expireArm, ARM_MS)
        log("ARM source=$source expiresInMs=$ARM_MS")
    }

    fun fixed(app: ProbeApplication) {
        if (app.session != null) return
        stop(app)
        fixedComplete.value = false
        fixedHeard.value = false
        start(app, AudioSource.FIXED_UI, FIXED_TEXT)
    }

    fun confirmFixedHeard() {
        if (fixedComplete.value) {
            fixedHeard.value = true
            log("FIXED_UI_HEARD_BY_USER")
        }
    }

    private fun clearArm() {
        arm = null
        armed.value = null
    }

    fun stop(app: ProbeApplication) {
        clearArm()
        app.main.removeCallbacks(expireArm)
        activeSession?.cancel()
    }

    private fun start(app: ProbeApplication, source: AudioSource, text: String,
                      service: WeChatNotificationListener? = null, callback: Long? = null,
                      arrived: Long = SystemClock.elapsedRealtime()) {
        fun permitted() = !app.settings.snapshot().announcingEnabled &&
            (service == null || allowed(app, service))
        if (!permitted() || app.session != null || app.foregroundFocus != null || app.compatibilityTestActive) {
            log("AUDIO_REJECTED source=$source callback=$callback")
            return
        }
        val began = SystemClock.elapsedRealtime()
        val volume = app.getSystemService(AudioManager::class.java)
        val volumeBefore = runCatching { volume.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrNull()
        val dependencies = object : SpeechDependencies by PlatformSpeechDependencies {
            override fun permissionGranted(context: android.content.Context, permission: String) =
                permitted() && PlatformSpeechDependencies.permissionGranted(context, permission)

            override fun requestFocus(audio: AudioManager, request: AudioFocusRequest): Int {
                val state = environment(app)
                val result = try { PlatformSpeechDependencies.requestFocus(audio, request) }
                catch (failure: RuntimeException) {
                    log("AUDIO_FOCUS_EXCEPTION source=$source callback=$callback $state")
                    throw failure
                }
                log("AUDIO_FOCUS source=$source callback=$callback result=$result $state")
                return result
            }
        }
        log("AUDIO_START source=$source callback=$callback callbackToStartMs=${began - arrived} " +
            "mediaVolume=$volumeBefore ${environment(app)}")
        activeSource = source
        app.speak(text, background = !(stage2Visible || app.visible), dependencies = dependencies, result = { outcome ->
            activeSource = null
            activeSession = null
            if (source == AudioSource.FIXED_UI) fixedComplete.value =
                outcome.playbackCompleted && outcome.actualRouteConfirmed && outcome.focusReleased
            val volumeAfter = runCatching { volume.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrNull()
            log("AUDIO_END source=$source callback=$callback elapsedMs=${SystemClock.elapsedRealtime() - began} " +
                "pcmMs=${outcome.pcmDurationMs} bytesWritten=${outcome.bytesWritten} routed=${outcome.actualRouteConfirmed} " +
                "trackComplete=${outcome.playbackCompleted} focusReleased=${outcome.focusReleased} " +
                "focusChanges=${outcome.focusChanges} mediaVolume=$volumeAfter stage=${outcome.stage} reason=${outcome.reason}")
        })
        if (activeSource == source) activeSession = app.session
    }

    private fun environment(app: ProbeApplication): String {
        val memory = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(memory)
        return "mainOrStage0Visible=${app.visible} stage2Visible=$stage2Visible " +
            "interactive=${app.getSystemService(PowerManager::class.java).isInteractive} " +
            "keyguard=${app.getSystemService(KeyguardManager::class.java).isKeyguardLocked} " +
            "importance=${memory.importance} access=${accessGranted(app)} bound=${bound.value}"
    }

    private fun digest(kind: String, value: String): List<Byte> {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(kind.toByteArray())
        return digest.digest(value.toByteArray()).toList()
    }

    private fun remember(key: List<Byte>, observation: Observation) {
        observations[key] = observation
        if (observations.size > 96) observations.remove(observations.keys.first())
    }

    private fun log(detail: String) {
        val line = "run=${Stage0Log.processRun} wall=${System.currentTimeMillis()} elapsed=${SystemClock.elapsedRealtime()} $detail"
        Log.i(TAG, line)
        entries.value = (entries.value + line).takeLast(32)
    }
}

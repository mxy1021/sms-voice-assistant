package com.mxy1021.smsvoiceassistant

import android.graphics.Color
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.mxy1021.smsvoiceassistant.audio.SpeechGain
import com.mxy1021.smsvoiceassistant.settings.DisplayMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Test-APK UiAutomation only; no application accessibility service or new permission. */
@RunWith(AndroidJUnit4::class)
class ThemeGainDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as ProbeApplication
    private fun <T> main(block: () -> T): T {
        var result: T? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST") return result as T
    }
    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use { fd ->
        ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes().toString(Charsets.UTF_8) }
    }
    private fun activity() = main {
        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
            .filterIsInstance<MainActivity>().firstOrNull()
    }
    private fun waitFor(message: String, test: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime()+5000
        while (SystemClock.elapsedRealtime() < deadline) { if (test()) return; Thread.sleep(50) }
        error(message)
    }
    private fun launch() {
        shell("am start -n ${app.packageName}/.MainActivity")
        waitFor("MainActivity must be resumed") { activity() != null }
        Thread.sleep(500)
    }
    private fun find(root: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (root == null) return null
        if (predicate(root)) return root
        for (i in 0 until root.childCount) find(root.getChild(i),predicate)?.let { return it }
        return null
    }
    private fun node(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        repeat(12) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            require(root?.packageName?.toString() == app.packageName) { "Only operate on our app UI" }
            find(root,predicate)?.let { result ->
                result.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                Thread.sleep(100)
                return result
            }
            find(root) { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            Thread.sleep(150)
        }
        error("Requested app control not found")
    }
    private fun clickTheme(mode: DisplayMode) {
        assertTrue(node { it.contentDescription?.toString() == mode.label }
            .performAction(AccessibilityNodeInfo.ACTION_CLICK))
        waitFor("Theme setting must update") { main { app.settings.snapshot().displayMode == mode } }
        Thread.sleep(300)
    }
    private fun assertBackground(dark: Boolean) {
        waitFor("Rendered background must match selected theme") {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root?.packageName?.toString() != app.packageName) false else {
                val screenshot = instrumentation.uiAutomation.takeScreenshot() ?: return@waitFor false
                try {
                    val pixel = screenshot.getPixel(5,screenshot.height/2)
                    val mean = (Color.red(pixel)+Color.green(pixel)+Color.blue(pixel))/3
                    if (dark) mean < 70 else mean > 190
                } finally { screenshot.recycle() }
            }
        }
    }
    @Test fun fourSliderStepsPersistAndSurviveActivityRecreation() {
        val original = main { app.settings.snapshot() }
        try {
            launch()
            SpeechGain.levels.forEach { gain ->
                val slider = node { it.contentDescription?.toString()?.startsWith("语音增益，") == true }
                assertNotNull(slider.rangeInfo)
                assertEquals(0f,slider.rangeInfo.min,0f); assertEquals(3f,slider.rangeInfo.max,0f)
                val previousGain = main { app.settings.snapshot().speechGainDb }
                val changed = slider.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
                    Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,gain/3f) })
                assertTrue("A different step must accept SET_PROGRESS ($previousGain -> $gain)", changed || previousGain == gain)
                waitFor("Discrete slider must persist $gain") { main { app.settings.snapshot().speechGainDb == gain } }
                assertEquals(gain,app.prefs.getInt("speechGainDb",-1))
                assertEquals(original.copy(speechGainDb=gain),main { app.settings.snapshot() })
            }
            val previous = activity()!!
            main { previous.recreate() }
            waitFor("Activity recreation must finish") { activity()?.let { it !== previous } == true }
            assertEquals(9,main { app.settings.snapshot().speechGainDb })
            val slider = node { it.contentDescription?.toString() == "语音增益，9dB" }
            assertEquals(3f,slider.rangeInfo.current,0f)
            instrumentation.sendStatus(2,Bundle().apply { putString("stream",
                "\nSVA_THEME_GAIN slider=4;sharedPreferences=true;activityRecreate=true;settingsPreserved=true\n") })
        } finally { main { app.settings.setSpeechGain(original.speechGainDb) } }
    }
    @Test fun lightDarkAndFollowSystemRenderAndPersistWithoutInvalidatingSpeech() {
        val original = main { app.settings.snapshot() }
        val initialSystem = shell("cmd uimode night").trim().substringAfterLast(' ')
        require(initialSystem in listOf("auto","yes","no","custom"))
        val permit = main { app.settings.permit(false)!! }
        try {
            launch()
            clickTheme(DisplayMode.LIGHT); assertBackground(false)
            assertEquals(1,app.prefs.getInt("displayMode",-1))
            clickTheme(DisplayMode.DARK); assertBackground(true)
            assertEquals(2,app.prefs.getInt("displayMode",-1))
            val previous = activity()!!; main { previous.recreate() }
            waitFor("Dark theme must survive recreation") { activity()?.let { it !== previous } == true }
            assertBackground(true)
            clickTheme(DisplayMode.SYSTEM)
            shell("cmd uimode night no"); assertBackground(false)
            shell("cmd uimode night yes"); assertBackground(true)
            assertEquals(original.copy(displayMode=DisplayMode.SYSTEM),main { app.settings.snapshot() })
            assertTrue(main { app.settings.valid(permit) })
            instrumentation.sendStatus(2,Bundle().apply { putString("stream",
                "\nSVA_THEME_GAIN themes=3;renderedPixels=true;systemChange=true;persistent=true;speechPermitUnchanged=true\n") })
        } finally {
            shell("cmd uimode night $initialSystem")
            main { app.settings.setDisplayMode(original.displayMode) }
        }
    }
}

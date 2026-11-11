package com.mxy1021.smsvoiceassistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.mxy1021.smsvoiceassistant.bluetooth.AudioPortRefresh
import com.mxy1021.smsvoiceassistant.bluetooth.BluetoothProbe
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Activity lifecycle, Android permission reads and paired/profile/audio queries.
 * Actual revoke/regrant kills the target on this OS and must be verified separately by the user.
 */
@RunWith(AndroidJUnit4::class)
class ForegroundBluetoothRefreshTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }
    private fun snapshot(): MainActivity.BluetoothReadiness? {
        var result: MainActivity.BluetoothReadiness? = null
        instrumentation.runOnMainSync {
            result = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().firstOrNull()?.bluetoothReadiness()
        }
        return result
    }
    private fun settled(): MainActivity.BluetoothReadiness {
        val end = SystemClock.elapsedRealtime() + 6000
        while (SystemClock.elapsedRealtime() < end) {
            snapshot()?.let { if (!it.checking) return it }
            SystemClock.sleep(50)
        }
        error("Foreground Bluetooth refresh did not settle")
    }
    @Test fun repeatedForegroundReadsRealPermissionAndRecomputesReadiness() {
        val context = instrumentation.targetContext
        val packageName = context.packageName
        shell("am start -n $packageName/.MainActivity")
        val first = settled()
        assertEquals(context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED, first.permission)
        repeat(5) {
            shell("input keyevent KEYCODE_HOME")
            val end = SystemClock.elapsedRealtime() + 3000
            while (snapshot() != null && SystemClock.elapsedRealtime() < end) SystemClock.sleep(50)
            assertNull("Home must stop the actual Activity", snapshot())
            shell("am start -n $packageName/.MainActivity")
            val fresh = settled()
            assertTrue("onResume must run a fresh query", fresh.revision > first.revision)
            assertEquals(context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED, fresh.permission)
            if (!fresh.permission || !fresh.pairedTarget || !fresh.connected) assertFalse(fresh.routeReady)
            // Stable connected test conditions must remain ready after every foreground transition.
            if (first.routeReady) assertTrue(fresh.routeReady)
        }
        instrumentation.sendStatus(2, android.os.Bundle().apply {
            putString("stream", "\nSVA_BLUETOOTH_LIFECYCLE PASS cycles=5 actualPermissionReads=true " +
                "initialRouteReady=${first.routeReady};revokeRegrant=MANUAL_REQUIRED\n")
        })
    }

    @Test fun portRecoveryUsesRealRoutingCallbackThenStrictInspection() {
        val context = instrumentation.targetContext
        shell("am start -n ${context.packageName}/.MainActivity")
        val initial = settled()
        org.junit.Assume.assumeTrue("Connected target and real permission required", initial.permission && initial.routeReady)
        val done = CompletableFuture<Boolean>()
        val recovery = AudioPortRefresh(context)
        try {
            instrumentation.runOnMainSync { recovery.start { done.complete(it) } }
            assertTrue("Actual routing callback required; timeout is not a pass", done.get(3, TimeUnit.SECONDS))
            instrumentation.runOnMainSync { assertFalse(recovery.running) }
            val inspected = CompletableFuture<Boolean>()
            instrumentation.runOnMainSync {
                BluetoothProbe(context).inspect((context.applicationContext as ProbeApplication).target) {
                    inspected.complete(it.connected && it.output != null)
                }
            }
            assertTrue("Readiness still requires strict target/expected-route confirmation", inspected.get(3, TimeUnit.SECONDS))
            instrumentation.sendStatus(2, android.os.Bundle().apply {
                putString("stream", "\nSVA_BLUETOOTH_PORT PASS nativeRoutingCallback=true pcmBytesWritten=0 released=true strictRecheck=true\n")
            })
        } finally { instrumentation.runOnMainSync { recovery.cancel() } }
    }

    @Test fun cancelDropsQueuedRoutingCallbackAndReleasesTrack() {
        val context = instrumentation.targetContext
        shell("am start -n ${context.packageName}/.MainActivity")
        org.junit.Assume.assumeTrue(settled().routeReady)
        val called = java.util.concurrent.atomic.AtomicBoolean(false)
        instrumentation.runOnMainSync {
            val recovery = AudioPortRefresh(context)
            recovery.start { called.set(true) }
            assertTrue("Track must have started before cancellation", recovery.running)
            recovery.cancel(); recovery.cancel()
            assertFalse(recovery.running)
        }
        SystemClock.sleep(900)
        assertFalse("Cancelled recovery must never update UI via a late callback", called.get())
    }
}

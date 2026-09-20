package xyz.shapemachine.andsri

import android.app.AlertDialog
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class RequestLifecycleTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)

    @Test fun pausedWeatherRequestClearsBusyUiWithoutRetry() {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            instrumentation.runOnMainSync {
                val gate = field(activity, "weatherRequestGate") as RequestGate
                val adapter = field(activity, "adapter") as LauncherAdapter
                gate.tryBegin()
                adapter.updateWeather(null, refreshing = true)
                instrumentation.callActivityOnPause(activity)
                assertFalse(gate.isActive())
                assertEquals(false, field(adapter, "weatherRefreshing"))
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun stoppedLocationLookupDismissesDisabledDialog() {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as SettingsActivity
        try {
            instrumentation.runOnMainSync {
                activity.javaClass.getDeclaredMethod("editWeatherLocation").apply { isAccessible = true }.invoke(activity)
                val dialog = field(activity, "locationDialog") as AlertDialog
                val gate = field(activity, "locationRequestGate") as RequestGate
                gate.tryBegin()
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                instrumentation.callActivityOnStop(activity)
                assertFalse(dialog.isShowing)
                assertFalse(gate.isActive())
                assertNull(field(activity, "locationDialog"))
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}

package xyz.shapemachine.andsri

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class TimeZonesSettingsTest {
    @Test fun listPreservesOrderAndRestoresAfterRemovalAndRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = LauncherPreferences(context)
        val original = preferences.appearance()
        val zones = listOf(AdditionalTimeZone("Amsterdam", "Europe/Amsterdam"),
            AdditionalTimeZone("London", "Europe/London"), AdditionalTimeZone("Tokyo", "Asia/Tokyo"))
        preferences.saveAppearance(original.copy(additionalTimeZones = zones))
        val current = AtomicReference<Activity>()
        val app = context.applicationContext as Application
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { if (activity is SettingsActivity) current.set(activity) }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
            (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
        fun dialog(activity: Activity) = SettingsActivity::class.java.getDeclaredField("timeZonesDialog")
            .apply { isAccessible = true }.get(activity) as AlertDialog
        fun awaitActivity(previous: Activity? = null): Activity {
            val deadline = System.currentTimeMillis() + 15000
            while (System.currentTimeMillis() < deadline) {
                instrumentation.waitForIdleSync()
                current.get()?.takeIf { it !== previous }?.let { return it }
                Thread.sleep(50)
            }
            error("Settings did not resume")
        }
        app.registerActivityLifecycleCallbacks(callbacks)
        try {
            instrumentation.startActivitySync(Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val first = awaitActivity()
            lateinit var firstEditor: AlertDialog
            instrumentation.runOnMainSync {
                views(first.window.decorView).filterIsInstance<TextView>()
                    .single { it.text.toString().startsWith(first.getString(R.string.additional_time_zones) + "\n") }.performClick()
                val editor = dialog(first)
                firstEditor = editor
                assertTrue(editor.isShowing)
                assertEquals(View.GONE, editor.getButton(AlertDialog.BUTTON_NEUTRAL).visibility)
                views(editor.window!!.decorView).single {
                    it.contentDescription == first.getString(R.string.remove_time_zone_named, "London")
                }.performClick()
            }
            val second = awaitActivity(first)
            instrumentation.runOnMainSync {
                assertFalse("Destroyed activity must dismiss its editor", firstEditor.isShowing)
                assertEquals(listOf(zones[0], zones[2]), preferences.appearance().additionalTimeZones)
                assertTrue(dialog(second).isShowing)
                assertEquals(View.VISIBLE, dialog(second).getButton(AlertDialog.BUTTON_NEUTRAL).visibility)
                second.recreate()
            }
            val third = awaitActivity(second)
            instrumentation.runOnMainSync {
                assertTrue(dialog(third).isShowing)
                dialog(third).getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
                val search = SettingsActivity::class.java.getDeclaredField("locationDialog")
                    .apply { isAccessible = true }.get(third) as AlertDialog
                search.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
                assertTrue(dialog(third).isShowing)
            }
        } finally {
            instrumentation.runOnMainSync { current.get()?.finish() }
            app.unregisterActivityLifecycleCallbacks(callbacks)
            preferences.saveAppearance(original)
        }
    }
}

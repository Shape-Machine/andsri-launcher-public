package xyz.shapemachine.andsri.settings

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class SettingsConsistencyTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()

    @Test fun measuredChoicesStackAtLargeTextWithoutSpuriousPreferenceWrites() {
        instrumentation.runOnMainSync {
            val fonts = listOf("atkinson_hyperlegible_next_regular", "newsreader_regular", "maple_mono_regular")
            val translations = listOf(listOf("Follow system", "Light", "Dark"), listOf("Compact", "Standaard", "Comfortabel"), listOf("सिस्टम", "हल्का", "गहरा"))
            for (scale in listOf(1f, 2f)) for (width in listOf(220, 720)) for (fontName in fonts) for (labels in translations) {
                val config = android.content.res.Configuration(context.resources.configuration).apply { fontScale = scale }
                val scaled = context.createConfigurationContext(config)
                val font = context.resources.getFont(context.resources.getIdentifier(fontName, "font", context.packageName))
                var picks = 0
                val group = SettingsControls.choices(scaled, labels, labels[0], { it }, { font },
                    android.graphics.Color.BLACK, android.graphics.Color.WHITE, 1, false) { picks++ }
                group.measure(View.MeasureSpec.makeMeasureSpec(SettingsControls.dp(scaled, width), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                group.layout(0, 0, group.measuredWidth, group.measuredHeight)
                assertEquals(0, picks)
                for (i in 0 until group.childCount) {
                    val button = group.getChildAt(i) as RadioButton
                    assertNull(button.buttonDrawable)
                    assertTrue("Text baseline must remain inside the button", button.baseline in 0 until button.height)
                    assertTrue(button.measuredHeight >= SettingsControls.dp(scaled, 48))
                    assertTrue(button.right <= group.width)
                    if (group.orientation == RadioGroup.HORIZONTAL) assertTrue(button.paint.measureText(button.text.toString()) <= button.width - button.paddingLeft - button.paddingRight + 1)
                }
                if (scale == 2f && width == 220 && labels[1] == "Standaard") assertEquals(RadioGroup.VERTICAL, group.orientation)
                (group.getChildAt(1) as RadioButton).performClick()
                assertEquals(1, picks)
                assertEquals(group.getChildAt(1).id, group.checkedRadioButtonId)
            }
        }
    }

    @Test fun settingsMatchSharedControlsAndPreserveScrollAcrossRecreation() {
        val current = AtomicReference<Activity>()
        val app = context.applicationContext as Application
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { if (activity.javaClass.simpleName == "SettingsActivity") current.set(activity) }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        app.registerActivityLifecycleCallbacks(callbacks)
        fun awaitReady(previous: Activity? = null): Activity {
            val deadline = System.currentTimeMillis() + 15000
            while (System.currentTimeMillis() < deadline) {
                instrumentation.waitForIdleSync()
                var ready = false
                val activity = current.get()
                if (activity != null && activity !== previous) instrumentation.runOnMainSync {
                    ready = views(activity.window.decorView).filterIsInstance<RadioGroup>().isNotEmpty()
                }
                if (ready) return activity
                Thread.sleep(50)
            }
            error("Settings did not become ready")
        }
        try {
            instrumentation.startActivitySync(Intent().setClassName(context, "${context.packageName}.SettingsActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            var activity = awaitReady()
            for (mode in listOf("option_light", "option_dark")) {
                val labelId = context.resources.getIdentifier(mode, "string", context.packageName)
                val label = context.getString(labelId)
                var changes = false
                instrumentation.runOnMainSync {
                    val button = views(activity.window.decorView).filterIsInstance<RadioButton>().first { it.text.toString() == label }
                    changes = !button.isChecked
                    if (changes) button.performClick()
                }
                // Audiobooks updates the page in place; other apps recreate for appearance.
                if (changes && !context.packageName.endsWith("audiobook")) activity = awaitReady(activity)
                else { instrumentation.waitForIdleSync(); activity = awaitReady() }
                instrumentation.runOnMainSync {
                    val all = views(activity.window.decorView)
                    assertTrue(all.filterIsInstance<RadioButton>().all { it.buttonDrawable == null })
                    assertTrue(all.filterIsInstance<RadioButton>().first { it.text.toString() == label }.isChecked)
                    all.filterIsInstance<ScrollView>().first().scrollTo(0, 0)
                }
                instrumentation.waitForIdleSync()
                Thread.sleep(1000) // Let System UI render the updated bar contrast before capturing.
                val image = instrumentation.uiAutomation.takeScreenshot()
                val dir = File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: context.getExternalFilesDir(null)!!.absolutePath, "settings-screenshots").apply { mkdirs() }
                File(dir, "$mode.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                image.recycle()
            }
            var before = 0
            instrumentation.runOnMainSync {
                val scroll = views(activity.window.decorView).filterIsInstance<ScrollView>().first()
                scroll.scrollTo(0, scroll.getChildAt(0).height)
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                before = views(activity.window.decorView).filterIsInstance<ScrollView>().first().scrollY
                activity.recreate()
            }
            activity = awaitReady(activity)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertEquals(before, views(activity.window.decorView).filterIsInstance<ScrollView>().first().scrollY)
            }
        } finally {
            instrumentation.runOnMainSync { current.get()?.finish(); app.unregisterActivityLifecycleCallbacks(callbacks) }
        }
    }
}

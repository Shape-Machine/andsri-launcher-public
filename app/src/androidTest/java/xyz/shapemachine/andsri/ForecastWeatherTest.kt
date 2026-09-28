package xyz.shapemachine.andsri

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.LocaleList
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ForecastWeatherTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val location = WeatherLocation("Amsterdam", 52.37, 4.89)
    private fun snapshot(forecast: Boolean = true) = WeatherSnapshot(
        location.name, 18.0, 0, System.currentTimeMillis() - 5 * 60_000,
        TemperatureUnit.CELSIUS,
        if (forecast) List(12) { ForecastHour(18.0 + it % 3, 0, 0) } else emptyList(),
    )

    @Test fun legacyStylesPreserveLocationUnitAndCachedConditions() {
        val preferences = LauncherPreferences(context)
        val raw = context.getSharedPreferences("launcher_preferences", Context.MODE_PRIVATE)
        val cache = WeatherCache(context)
        try {
            val config = WeatherConfig(location, TemperatureUnit.CELSIUS)
            preferences.saveWeather(config)
            val currentOnly = snapshot(false)
            cache.save(location, currentOnly)
            for (style in listOf("COMPACT", "STANDARD", "EMPHASIZED", "FORECAST")) {
                raw.edit().putString("weather_preset", style).commit()
                assertEquals(config, preferences.weather())
                assertEquals(currentOnly, cache.load(preferences.weather()))
            }
            preferences.saveWeather(config)
            assertFalse(raw.contains("weather_preset"))
        } finally {
            preferences.saveWeather(WeatherConfig())
            cache.clear()
        }
    }

    @Test fun forecastMutesOnlyAgeAndRetainsRefreshAndErrorStates() {
        instrumentation.runOnMainSync {
            for (language in listOf("en", "nl", "hi")) {
                val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                    setLocales(LocaleList.forLanguageTags(language))
                })
                var refreshes = 0
                val adapter = LauncherAdapter(localized, {}, {}, {}, { _, _ -> }, {}, { refreshes++ }, {})
                try {
                    for (color in listOf(Color.BLACK, Color.WHITE)) {
                        adapter.submit(listOf(HomeRow.Weather), AppearanceConfig(), WeatherConfig(location), color)
                        adapter.updateWeather(snapshot(), false)
                        val row = adapter.getView(0, null, LinearLayout(localized)) as LinearLayout
                        assertEquals(3, row.childCount)
                        val primary = row.getChildAt(0) as TextView
                        val secondary = row.getChildAt(1) as TextView
                        val tertiary = row.getChildAt(2) as TextView
                        val text = primary.text as Spanned
                        val span = text.getSpans(0, text.length, ForegroundColorSpan::class.java).single()
                        val age = localized.resources.getQuantityString(R.plurals.weather_updated_minutes, 5, 5)
                        assertEquals(age, text.subSequence(text.getSpanStart(span), text.getSpanEnd(span)).toString())
                        assertEquals(text.length, text.getSpanEnd(span))
                        assertEquals(color, primary.currentTextColor)
                        assertEquals(color and 0x00ffffff, span.foregroundColor and 0x00ffffff)
                        assertTrue(Color.alpha(span.foregroundColor) in 1..254)
                        assertTrue(secondary.text.isNotBlank())
                        assertTrue(tertiary.text.isNotBlank())
                        assertTrue(row.contentDescription.contains(age))
                        capture(row, "$language-${if (color == Color.BLACK) "light" else "dark"}", color)

                        val before = refreshes
                        row.performClick()
                        assertEquals(before + 1, refreshes)
                        adapter.updateWeather(snapshot(), true)
                        assertEquals(localized.getString(R.string.weather_refreshing), secondary.text.toString())
                        row.performClick()
                        assertEquals(before + 1, refreshes)
                        val error = localized.getString(R.string.weather_refresh_failed)
                        adapter.updateWeather(snapshot(false), false, error)
                        assertEquals(error, secondary.text.toString())
                        assertEquals("", tertiary.text.toString())
                        adapter.updateWeather(snapshot(false), false)
                        assertEquals(localized.getString(R.string.weather_tap_for_forecast), secondary.text.toString())
                        adapter.updateWeather(null, false)
                        assertEquals(localized.getString(R.string.weather_tap_to_check), primary.text.toString())
                        assertFalse(primary.text is Spanned && (primary.text as Spanned)
                            .getSpans(0, primary.text.length, ForegroundColorSpan::class.java).isNotEmpty())
                    }
                } finally { adapter.close() }
            }
        }
    }

    private fun capture(row: View, name: String, foreground: Int) {
        val width = (393 * row.resources.displayMetrics.density).toInt()
        row.setBackgroundColor(if (foreground == Color.BLACK) Color.WHITE else Color.BLACK)
        row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        row.layout(0, 0, row.measuredWidth, row.measuredHeight)
        val bitmap = Bitmap.createBitmap(row.width, row.height, Bitmap.Config.ARGB_8888)
        row.draw(Canvas(bitmap))
        val dir = File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: context.getExternalFilesDir(null)!!.absolutePath, "weather-screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}

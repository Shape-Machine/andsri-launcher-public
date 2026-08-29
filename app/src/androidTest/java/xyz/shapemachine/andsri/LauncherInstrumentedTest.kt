package xyz.shapemachine.andsri

import android.graphics.Color
import android.net.TrafficStats
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test
    fun testCleanAppearanceDefaultsToArcticonsWithText() {
        val appearance = AppearanceConfig()
        assertTrue(appearance.displayMode == AppDisplayMode.ICON_TEXT)
        assertTrue(appearance.iconTheme == IconTheme.ARCTICONS)
    }

    @Test
    fun testAppearanceColorsFollowExplicitMode() {
        val context = instrumentation.targetContext

        assertEquals(Color.BLACK, AppearanceResolver.textColor(context, AppearanceConfig(appearanceMode = AppearanceMode.LIGHT)))
        assertEquals(Color.WHITE, AppearanceResolver.backgroundColor(context, AppearanceConfig(appearanceMode = AppearanceMode.LIGHT)))
        assertEquals(Color.WHITE, AppearanceResolver.textColor(context, AppearanceConfig(appearanceMode = AppearanceMode.DARK)))
        assertEquals(Color.BLACK, AppearanceResolver.backgroundColor(context, AppearanceConfig(appearanceMode = AppearanceMode.DARK)))
    }

    @Test
    fun testBundledThemesContainCoreAndroidIcons() {
        val provider = BundledIconProvider(instrumentation.targetContext)
        assertNotNull(provider.icon("com.android.settings", IconTheme.LAWNICONS, Color.WHITE))
        assertNotNull(provider.icon("com.android.settings", IconTheme.ARCTICONS, Color.WHITE))
        assertNotNull(provider.icon("com.android.settings", IconTheme.APPSTRACT, Color.WHITE))
        assertNotNull(provider.icon("com.android.settings", IconTheme.CUSCON, Color.WHITE))
        assertNotNull(provider.icon("com.android.settings", IconTheme.DELTA, Color.WHITE))
        assertNotNull(provider.icon("com.android.settings", IconTheme.DOLLPHONE, Color.WHITE))
        assertNotNull(provider.icon("com.android.settings", IconTheme.SNOW, Color.WHITE))
        assertNotNull(provider.cachedIcon("com.android.settings", IconTheme.ARCTICONS, Color.WHITE))
    }

    @Test
    fun testConfigurationPersistsAndReconcilesWithoutTouchingUserData() {
        val isolatedContext = instrumentation.context.createDeviceProtectedStorageContext()
        val preferences = LauncherPreferences(isolatedContext)
        preferences.reset()
        assertTrue(preferences.appearance().displayMode == AppDisplayMode.ICON_TEXT)
        assertTrue(preferences.appearance().iconTheme == IconTheme.ARCTICONS)
        preferences.saveFavorites(listOf("one/component", "gone/component"))
        preferences.hide("gone/component")
        preferences.saveCustomLabel("one/component", "Renamed")
        preferences.saveAppearance(AppearanceConfig(font = FontPreset.MONOSPACE, appearanceMode = AppearanceMode.DARK))
        preferences.saveWeather(WeatherConfig(WeatherLocation("Amsterdam", 52.37, 4.89), WeatherPreset.COMPACT, TemperatureUnit.CELSIUS))
        preferences.setAppsExpanded(true)
        preferences.reconcileInstalled(setOf("one/component"), preferences.snapshot())
        assertTrue(preferences.favoriteComponents() == listOf("one/component"))
        assertTrue(preferences.hiddenComponents().isEmpty())
        assertTrue(preferences.customLabels()["one/component"] == "Renamed")
        assertTrue(preferences.appearance().font == FontPreset.MONOSPACE)
        assertTrue(preferences.weather().location?.name == "Amsterdam")
        assertTrue(preferences.weather().preset == WeatherPreset.COMPACT)
        assertTrue(preferences.appsExpanded())
        preferences.reset()
    }

    @Test
    fun testShippedLocalesResolveTranslatedLauncherSettings() {
        val context = instrumentation.targetContext
        val labels = listOf("en", "nl", "hi").map { tag ->
            val configuration = android.content.res.Configuration(context.resources.configuration).apply {
                setLocales(android.os.LocaleList.forLanguageTags(tag))
            }
            context.createConfigurationContext(configuration).getString(R.string.launcher_settings)
        }
        assertTrue(labels.all { it.isNotBlank() })
        assertTrue(labels.toSet().size == 3)
    }

    @Test
    fun testFavoritesAlsoRemainInAlphabeticalApps() {
        val favorite = AppEntry(android.content.ComponentName("example.favorite", "example.favorite.Main"), "Favorite")
        val other = AppEntry(android.content.ComponentName("example.other", "example.other.Main"), "Other")
        val rows = HomeRows.build(listOf(favorite, other), listOf(favorite.component.flattenToString()), appsExpanded = true)
        assertTrue((rows.filterIsInstance<HomeRow.Favorites>().single()).apps == listOf(favorite))
        assertTrue(rows.filterIsInstance<HomeRow.App>().map { it.app } == listOf(favorite, other))
    }

    @Test
    fun testLauncherStartsWithLaunchableContent() {
        val context = instrumentation.targetContext
        val intent = android.content.Intent(context, MainActivity::class.java).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val activity = instrumentation.startActivitySync(intent)
        val list = activity.findViewById<android.widget.ListView>(MainActivity.LIST_ID)
        repeat(20) {
            instrumentation.waitForIdleSync()
            if (list.adapter.count >= 2) return@repeat
            Thread.sleep(50)
        }
        assertTrue((0 until list.adapter.count).any {
            list.adapter.getItem(it) is HomeRow.App || list.adapter.getItem(it) is HomeRow.AppsToggle
        })
        activity.finish()
    }

    @Test
    fun testSettingsStartsWithScrollableContent() {
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(
            android.content.Intent(context, SettingsActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        val root = content.getChildAt(0) as android.widget.FrameLayout
        assertTrue(root.getChildAt(0) is android.widget.ScrollView)
        instrumentation.waitForIdleSync()
        val statusBar = root.getChildAt(1)
        assertTrue(statusBar.layoutParams.height > 0)
        assertTrue((statusBar.background as android.graphics.drawable.ColorDrawable).color in setOf(Color.BLACK, Color.WHITE))
        activity.finish()
    }

    @Test
    fun testConfiguredWeatherPrecedesFavoritesAndCollapsedApps() {
        val favorite = AppEntry(android.content.ComponentName("example.favorite", "example.favorite.Main"), "Favorite")
        val rows = HomeRows.build(
            listOf(favorite),
            listOf(favorite.component.flattenToString()),
            weatherConfigured = true,
            appsExpanded = false,
        )

        assertTrue(
            rows == listOf(
                HomeRow.Header,
                HomeRow.Gap,
                HomeRow.Weather,
                HomeRow.Gap,
                HomeRow.Favorites(listOf(favorite)),
                HomeRow.AppsToggle(false),
            ),
        )
    }

    @Test
    fun testUnconfiguredLauncherPerformsNoAutomaticNetworkTraffic() {
        val context = instrumentation.targetContext
        LauncherPreferences(context).saveWeather(WeatherConfig(location = null))
        WeatherCache(context).clear()
        val uid = context.applicationInfo.uid
        val before = TrafficStats.getUidRxBytes(uid) to TrafficStats.getUidTxBytes(uid)
        val activity = instrumentation.startActivitySync(
            android.content.Intent(context, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )

        Thread.sleep(500)

        val after = TrafficStats.getUidRxBytes(uid) to TrafficStats.getUidTxBytes(uid)
        assertTrue(before == after)
        activity.finish()
    }

    @Test
    fun testAppsStayExpandedWhenThereAreNoFavorites() {
        val app = AppEntry(android.content.ComponentName("example.app", "example.app.Main"), "App")
        val rows = HomeRows.build(listOf(app), emptyList(), appsExpanded = false)

        assertTrue(rows.filterIsInstance<HomeRow.App>() == listOf(HomeRow.App(app)))
        assertTrue(rows.none { it is HomeRow.AppsToggle })
    }
}

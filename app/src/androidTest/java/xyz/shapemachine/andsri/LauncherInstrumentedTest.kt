package xyz.shapemachine.andsri

import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
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
    fun testBundledThemesContainCoreAndroidIcons() {
        val provider = BundledIconProvider(instrumentation.targetContext)
        assertNotNull(provider.icon("com.android.settings", IconTheme.LAWNICONS, Color.WHITE))
        assertNotNull(provider.icon("com.android.settings", IconTheme.ARCTICONS, Color.WHITE))
        assertNotNull(provider.icon("org.fdroid.fdroid", IconTheme.MONDSTERN, Color.WHITE))
        assertNotNull(provider.icon("com.android.settings", IconTheme.CUSCON, Color.WHITE))
        assertNotNull(provider.icon("com.android.settings", IconTheme.DELTA, Color.WHITE))
        assertNotNull(provider.icon("com.android.settings", IconTheme.DOLLPHONE, Color.WHITE))
        assertNotNull(provider.icon("com.android.settings", IconTheme.SNOW, Color.WHITE))
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
        preferences.reconcileInstalled(setOf("one/component"), preferences.snapshot())
        assertTrue(preferences.favoriteComponents() == listOf("one/component"))
        assertTrue(preferences.hiddenComponents().isEmpty())
        assertTrue(preferences.customLabels()["one/component"] == "Renamed")
        assertTrue(preferences.appearance().font == FontPreset.MONOSPACE)
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
        val rows = HomeRows.build(listOf(favorite, other), listOf(favorite.component.flattenToString()))
        assertTrue((rows.filterIsInstance<HomeRow.Favorites>().single()).apps == listOf(favorite))
        assertTrue(rows.filterIsInstance<HomeRow.App>().map { it.app } == listOf(favorite, other))
    }

    @Test
    fun testLauncherStartsAndRendersApps() {
        val context = instrumentation.targetContext
        val intent = android.content.Intent(context, MainActivity::class.java).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val activity = instrumentation.startActivitySync(intent)
        val list = activity.findViewById<android.widget.ListView>(MainActivity.LIST_ID)
        repeat(20) {
            instrumentation.waitForIdleSync()
            if (list.adapter.count > 2) return@repeat
            Thread.sleep(50)
        }
        assertTrue(list.adapter.count > 2)
        activity.finish()
    }
}

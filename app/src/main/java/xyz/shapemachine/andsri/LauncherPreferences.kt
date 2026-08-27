package xyz.shapemachine.andsri

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class LauncherState(
    val appearance: AppearanceConfig,
    val favorites: List<String>,
    val hidden: Set<String>,
    val labels: Map<String, String>,
)

class LauncherPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun hiddenComponents(): Set<String> = preferences.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().toSet()
    fun favoriteComponents(): List<String> = stringList(KEY_FAVORITES)
    fun customLabels(): Map<String, String> = stringMap(KEY_LABELS)
    fun appearance() = AppearanceConfig(
        wallpaperUri = preferences.getString(KEY_WALLPAPER, null),
        darkness = preferences.getInt(KEY_DARKNESS, 145),
        pureBlack = preferences.getBoolean(KEY_BLACK, false),
        displayMode = enumValue(KEY_MODE, AppDisplayMode.ICON_TEXT),
        font = enumValue(KEY_FONT, FontPreset.SANS),
        density = enumValue(KEY_DENSITY, DensityPreset.STANDARD),
        iconTheme = enumValue(KEY_THEME, IconTheme.ARCTICONS),
        appearanceMode = enumValue(KEY_APPEARANCE_MODE, AppearanceMode.SYSTEM),
        clockPreset = enumValue(KEY_CLOCK_PRESET, ClockPreset.STANDARD),
    )

    fun snapshot() = LauncherState(appearance(), favoriteComponents(), hiddenComponents(), customLabels())

    fun hide(component: String) = preferences.edit().putStringSet(KEY_HIDDEN, hiddenComponents() + component).apply()
    fun restoreHidden(components: Set<String>) = preferences.edit().putStringSet(KEY_HIDDEN, hiddenComponents() - components).apply()
    fun saveFavorites(components: List<String>) = preferences.edit().putString(KEY_FAVORITES, JSONArray(components).toString()).apply()
    fun toggleFavorite(component: String): Boolean {
        val favorites = favoriteComponents().toMutableList()
        val added = if (component in favorites) { favorites.remove(component); false } else { favorites.add(component); true }
        saveFavorites(favorites)
        return added
    }
    fun saveCustomLabel(component: String, label: String?) {
        val labels = customLabels().toMutableMap()
        if (label.isNullOrBlank()) labels.remove(component) else labels[component] = label.trim()
        preferences.edit().putString(KEY_LABELS, JSONObject(labels as Map<*, *>).toString()).apply()
    }

    fun saveAppearance(value: AppearanceConfig) {
        preferences.edit().putString(KEY_WALLPAPER, value.wallpaperUri)
            .putInt(KEY_DARKNESS, value.darkness.coerceIn(0, 255)).putBoolean(KEY_BLACK, value.pureBlack)
            .putString(KEY_MODE, value.displayMode.name).putString(KEY_FONT, value.font.name)
            .putString(KEY_DENSITY, value.density.name).putString(KEY_THEME, value.iconTheme.name)
            .putString(KEY_APPEARANCE_MODE, value.appearanceMode.name)
            .putString(KEY_CLOCK_PRESET, value.clockPreset.name).apply()
    }

    fun reconcileInstalled(components: Set<String>, state: LauncherState): LauncherState {
        val favorites = state.favorites.filter { it in components }
        val hidden = state.hidden.filterTo(mutableSetOf()) { it in components }
        val labels = state.labels.filterKeys { it in components }
        val reconciled = state.copy(favorites = favorites, hidden = hidden, labels = labels)
        if (reconciled == state) return state
        preferences.edit()
            .putString(KEY_FAVORITES, JSONArray(favorites).toString())
            .putStringSet(KEY_HIDDEN, hidden)
            .putString(KEY_LABELS, JSONObject(labels as Map<*, *>).toString())
            .apply()
        return reconciled
    }

    fun reset() = preferences.edit().clear().apply()

    fun registerChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        preferences.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        preferences.unregisterOnSharedPreferenceChangeListener(listener)

    private fun stringList(key: String): List<String> = runCatching {
        val array = JSONArray(preferences.getString(key, null) ?: return emptyList())
        List(array.length()) { array.getString(it) }
    }.getOrDefault(emptyList())

    private fun stringMap(key: String): Map<String, String> = runCatching {
        val value = JSONObject(preferences.getString(key, null) ?: return emptyMap())
        value.keys().asSequence().associateWith { value.getString(it) }
    }.getOrDefault(emptyMap())

    private inline fun <reified T : Enum<T>> enumValue(key: String, default: T): T =
        runCatching { enumValueOf<T>(preferences.getString(key, default.name) ?: default.name) }.getOrDefault(default)

    companion object {
        private const val FILE_NAME = "launcher_preferences"
        internal const val KEY_HIDDEN = "hidden_components"
        internal const val KEY_FAVORITES = "favorites"
        internal const val KEY_LABELS = "custom_labels"
        private const val KEY_WALLPAPER = "wallpaper_uri"
        private const val KEY_DARKNESS = "darkness"
        private const val KEY_BLACK = "pure_black"
        private const val KEY_MODE = "display_mode"
        private const val KEY_FONT = "font"
        private const val KEY_DENSITY = "density"
        private const val KEY_THEME = "icon_theme"
        private const val KEY_APPEARANCE_MODE = "appearance_mode"
        private const val KEY_CLOCK_PRESET = "clock_preset"
    }
}

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
    val weather: WeatherConfig,
    val appsExpanded: Boolean,
)

class LauncherPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun hiddenComponents(): Set<String> = preferences.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().toSet()
    fun favoriteComponents(): List<String> = stringList(KEY_FAVORITES)
    fun customLabels(): Map<String, String> = stringMap(KEY_LABELS)
    fun appearance() = AppearanceConfig(
        wallpaperUri = preferences.getString(KEY_WALLPAPER, null),
        wallpaperFade = preferences.getInt(KEY_WALLPAPER_FADE, 145),
        solidBackground = preferences.getBoolean(KEY_SOLID_BACKGROUND, false),
        displayMode = enumValue(KEY_MODE, AppDisplayMode.ICON_TEXT),
        font = enumValue(KEY_FONT, FontPreset.SANS),
        density = enumValue(KEY_DENSITY, DensityPreset.STANDARD),
        iconTheme = enumValue(KEY_THEME, IconTheme.ARCTICONS),
        appearanceMode = enumValue(KEY_APPEARANCE_MODE, AppearanceMode.SYSTEM),
        clockPreset = enumValue(KEY_CLOCK_PRESET, ClockPreset.STANDARD),
    )

    fun weather() = WeatherConfig(
        location = preferences.getString(KEY_WEATHER_NAME, null)?.let { name ->
            if (!preferences.contains(KEY_WEATHER_LATITUDE) || !preferences.contains(KEY_WEATHER_LONGITUDE)) return@let null
            val latitude = java.lang.Double.longBitsToDouble(preferences.getLong(KEY_WEATHER_LATITUDE, 0L))
            val longitude = java.lang.Double.longBitsToDouble(preferences.getLong(KEY_WEATHER_LONGITUDE, 0L))
            WeatherLocation(name, latitude, longitude).takeIf {
                name.isNotBlank() && latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
            }
        },
        preset = enumValue(KEY_WEATHER_PRESET, WeatherPreset.STANDARD),
        unit = enumValue(KEY_WEATHER_UNIT, TemperatureUnit.SYSTEM),
    )

    fun appsExpanded() = preferences.getBoolean(KEY_APPS_EXPANDED, false)

    fun snapshot() = LauncherState(
        appearance(), favoriteComponents(), hiddenComponents(), customLabels(), weather(), appsExpanded(),
    )

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

    fun saveWeather(value: WeatherConfig) {
        val editor = preferences.edit()
            .putString(KEY_WEATHER_PRESET, value.preset.name)
            .putString(KEY_WEATHER_UNIT, value.unit.name)
        value.location?.let {
            editor.putString(KEY_WEATHER_NAME, it.name)
                .putLong(KEY_WEATHER_LATITUDE, java.lang.Double.doubleToRawLongBits(it.latitude))
                .putLong(KEY_WEATHER_LONGITUDE, java.lang.Double.doubleToRawLongBits(it.longitude))
        } ?: editor.remove(KEY_WEATHER_NAME).remove(KEY_WEATHER_LATITUDE).remove(KEY_WEATHER_LONGITUDE)
        editor.apply()
    }

    fun setAppsExpanded(expanded: Boolean) = preferences.edit().putBoolean(KEY_APPS_EXPANDED, expanded).apply()

    fun saveAppearance(value: AppearanceConfig) {
        preferences.edit().putString(KEY_WALLPAPER, value.wallpaperUri)
            .putInt(KEY_WALLPAPER_FADE, value.wallpaperFade.coerceIn(0, 255))
            .putBoolean(KEY_SOLID_BACKGROUND, value.solidBackground)
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
        internal const val KEY_WEATHER_NAME = "weather_location_name"
        internal const val KEY_WEATHER_LATITUDE = "weather_location_latitude"
        internal const val KEY_WEATHER_LONGITUDE = "weather_location_longitude"
        internal const val KEY_WEATHER_PRESET = "weather_preset"
        internal const val KEY_WEATHER_UNIT = "weather_unit"
        internal const val KEY_APPS_EXPANDED = "apps_expanded"
        private const val KEY_WALLPAPER = "wallpaper_uri"
        // Keep legacy storage keys so existing installations migrate without data loss.
        private const val KEY_WALLPAPER_FADE = "darkness"
        private const val KEY_SOLID_BACKGROUND = "pure_black"
        private const val KEY_MODE = "display_mode"
        private const val KEY_FONT = "font"
        private const val KEY_DENSITY = "density"
        internal const val KEY_THEME = "icon_theme"
        private const val KEY_APPEARANCE_MODE = "appearance_mode"
        private const val KEY_CLOCK_PRESET = "clock_preset"
    }
}

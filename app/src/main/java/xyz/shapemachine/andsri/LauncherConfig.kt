package xyz.shapemachine.andsri

enum class AppDisplayMode { TEXT, ICON_TEXT }
enum class FontPreset { SANS, SERIF, MONOSPACE }
enum class DensityPreset { COMPACT, STANDARD, COMFORTABLE }
enum class IconTheme { NORMAL, LAWNICONS, ARCTICONS, APPSTRACT, CUSCON, DELTA, DOLLPHONE, SNOW }
enum class AppearanceMode { SYSTEM, LIGHT, DARK }
enum class ClockPreset { COMPACT, STANDARD, EMPHASIZED }
enum class WeatherPreset { COMPACT, STANDARD, EMPHASIZED }
enum class TemperatureUnit { SYSTEM, CELSIUS, FAHRENHEIT }

data class WeatherLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

data class WeatherConfig(
    val location: WeatherLocation? = null,
    val preset: WeatherPreset = WeatherPreset.STANDARD,
    val unit: TemperatureUnit = TemperatureUnit.SYSTEM,
)

data class AppearanceConfig(
    val wallpaperUri: String? = null,
    val wallpaperFade: Int = 145,
    val solidBackground: Boolean = false,
    val displayMode: AppDisplayMode = AppDisplayMode.ICON_TEXT,
    val font: FontPreset = FontPreset.SANS,
    val density: DensityPreset = DensityPreset.STANDARD,
    val iconTheme: IconTheme = IconTheme.ARCTICONS,
    val appearanceMode: AppearanceMode = AppearanceMode.SYSTEM,
    val clockPreset: ClockPreset = ClockPreset.STANDARD,
)

package xyz.shapemachine.andsri

enum class AppDisplayMode { TEXT, ICON_TEXT }
enum class FontPreset { SANS, SERIF, MONOSPACE }
enum class DensityPreset { COMPACT, STANDARD, COMFORTABLE }
enum class IconTheme { NORMAL, LAWNICONS, ARCTICONS, MONDSTERN, CUSCON, DELTA, DOLLPHONE, SNOW }
enum class AppearanceMode { SYSTEM, LIGHT, DARK }
enum class ClockPreset { STANDARD, COMPACT, EMPHASIZED }

data class AppearanceConfig(
    val wallpaperUri: String? = null,
    val darkness: Int = 145,
    val pureBlack: Boolean = false,
    val displayMode: AppDisplayMode = AppDisplayMode.ICON_TEXT,
    val font: FontPreset = FontPreset.SANS,
    val density: DensityPreset = DensityPreset.STANDARD,
    val iconTheme: IconTheme = IconTheme.ARCTICONS,
    val appearanceMode: AppearanceMode = AppearanceMode.SYSTEM,
    val clockPreset: ClockPreset = ClockPreset.STANDARD,
)

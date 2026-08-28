package xyz.shapemachine.andsri

sealed interface HomeRow {
    data object Header : HomeRow
    data object Weather : HomeRow
    data class Favorites(val apps: List<AppEntry>) : HomeRow
    data class AppsToggle(val expanded: Boolean) : HomeRow
    data class App(val app: AppEntry) : HomeRow
    data object Gap : HomeRow
    data object Empty : HomeRow
}

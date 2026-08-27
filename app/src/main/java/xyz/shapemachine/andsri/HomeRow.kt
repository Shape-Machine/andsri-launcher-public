package xyz.shapemachine.andsri

sealed interface HomeRow {
    data object Header : HomeRow
    data class Favorites(val apps: List<AppEntry>) : HomeRow
    data class App(val app: AppEntry) : HomeRow
    data object Spacer : HomeRow
    data object Empty : HomeRow
}

package xyz.shapemachine.andsri

object HomeRows {
    fun build(
        apps: List<AppEntry>,
        favoriteComponents: List<String>,
        weatherConfigured: Boolean = false,
        appsExpanded: Boolean = false,
    ): List<HomeRow> {
        val byComponent = apps.associateBy { it.component.flattenToString() }
        val favorites = favoriteComponents.mapNotNull(byComponent::get)
        return buildList {
            add(HomeRow.Header)
            if (weatherConfigured) {
                add(HomeRow.Gap)
                add(HomeRow.Weather)
            }
            if (favorites.isNotEmpty()) {
                add(HomeRow.Gap)
                add(HomeRow.Favorites(favorites))
            }
            if (apps.isEmpty()) add(HomeRow.Empty)
            else if (favorites.isEmpty()) {
                add(HomeRow.Gap)
                apps.forEach { add(HomeRow.App(it)) }
            } else {
                add(HomeRow.AppsToggle(appsExpanded))
                if (appsExpanded) apps.forEach { add(HomeRow.App(it)) }
            }
        }
    }
}

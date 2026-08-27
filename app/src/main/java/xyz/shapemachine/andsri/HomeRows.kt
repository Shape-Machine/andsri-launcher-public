package xyz.shapemachine.andsri

object HomeRows {
    fun build(
        apps: List<AppEntry>,
        favoriteComponents: List<String>,
    ): List<HomeRow> {
        val byComponent = apps.associateBy { it.component.flattenToString() }
        val favorites = favoriteComponents.mapNotNull(byComponent::get)
        return buildList {
            add(HomeRow.Header)
            if (apps.isEmpty()) add(HomeRow.Empty)
            if (favorites.isNotEmpty()) add(HomeRow.Favorites(favorites))
            if (favorites.isNotEmpty() && apps.isNotEmpty()) add(HomeRow.Spacer)
            apps.forEach { add(HomeRow.App(it)) }
        }
    }
}

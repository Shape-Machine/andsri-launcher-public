package xyz.shapemachine.andsri

internal object LayoutPolicy {
    fun favoriteColumnCount(availableWidthDp: Int) = if (availableWidthDp >= 440) 5 else 4

    fun shouldStackSegments(availableWidthPx: Int, labelWidthsPx: List<Float>, itemPaddingPx: Int): Boolean {
        if (labelWidthsPx.isEmpty()) return false
        return (labelWidthsPx.maxOrNull()!! + itemPaddingPx) * labelWidthsPx.size > availableWidthPx
    }

    fun reorderListHeight(availableHeightPx: Int, maximumHeightPx: Int): Int =
        minOf(availableHeightPx / 2, maximumHeightPx).coerceAtLeast(0)
}

package xyz.shapemachine.andsri

internal object LayoutPolicy {
    private const val WIDE_BREAKPOINT_DP = 840
    private const val GLANCE_WIDTH_DP = 360
    private const val MIN_APP_REGION_DP = 480

    fun isWideLayout(availableWidthDp: Int) = availableWidthDp >= WIDE_BREAKPOINT_DP

    fun glancePanelWidth(availableWidthDp: Int): Int =
        minOf(GLANCE_WIDTH_DP, availableWidthDp - MIN_APP_REGION_DP).coerceAtLeast(0)

    fun favoriteColumnCount(availableWidthDp: Int) = when {
        availableWidthDp >= 720 -> 6
        availableWidthDp >= 440 -> 5
        else -> 4
    }

    fun <T> rowMajorPairs(items: List<T>): List<List<T>> = items.chunked(2)

    fun shouldStackSegments(availableWidthPx: Int, labelWidthsPx: List<Float>, itemPaddingPx: Int): Boolean {
        if (labelWidthsPx.isEmpty()) return false
        return (labelWidthsPx.maxOrNull()!! + itemPaddingPx) * labelWidthsPx.size > availableWidthPx
    }

    fun reorderListHeight(availableHeightPx: Int, maximumHeightPx: Int): Int =
        minOf(availableHeightPx / 2, maximumHeightPx).coerceAtLeast(0)
}

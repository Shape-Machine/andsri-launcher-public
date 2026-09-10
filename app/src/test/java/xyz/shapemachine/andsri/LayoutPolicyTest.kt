package xyz.shapemachine.andsri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutPolicyTest {
    @Test
    fun narrowPhonesUseFourFavoriteColumns() {
        assertEquals(4, LayoutPolicy.favoriteColumnCount(360))
        assertEquals(4, LayoutPolicy.favoriteColumnCount(393))
        assertEquals(4, LayoutPolicy.favoriteColumnCount(439))
        assertEquals(5, LayoutPolicy.favoriteColumnCount(440))
        assertEquals(6, LayoutPolicy.favoriteColumnCount(720))
    }

    @Test
    fun wideLayoutUsesWindowWidthAndBoundsTheGlancePanel() {
        assertFalse(LayoutPolicy.isWideLayout(839))
        assertTrue(LayoutPolicy.isWideLayout(840))
        assertEquals(360, LayoutPolicy.glancePanelWidth(840))
        assertEquals(360, LayoutPolicy.glancePanelWidth(1280))
        assertEquals(listOf(listOf("A", "B"), listOf("C", "D"), listOf("E")), LayoutPolicy.rowMajorPairs(listOf("A", "B", "C", "D", "E")))
    }

    @Test
    fun segmentsStackOnlyWhenTheirLabelsNeedMoreWidth() {
        assertFalse(LayoutPolicy.shouldStackSegments(500, listOf(100f, 100f, 100f), 20))
        assertTrue(LayoutPolicy.shouldStackSegments(300, listOf(100f, 100f, 100f), 20))
        assertTrue(LayoutPolicy.shouldStackSegments(500, listOf(40f, 40f, 180f), 20))
    }

    @Test
    fun reorderListIsBoundedByWindowAndMaximumHeight() {
        assertEquals(400, LayoutPolicy.reorderListHeight(800, 600))
        assertEquals(600, LayoutPolicy.reorderListHeight(1600, 600))
    }
}

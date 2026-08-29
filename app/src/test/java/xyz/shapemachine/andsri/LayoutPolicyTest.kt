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

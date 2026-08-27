package xyz.shapemachine.andsri

import java.text.Collator
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class LabelOrderingTest {
    @Test
    fun sortsLabelsUsingLocaleCollation() {
        val collator = Collator.getInstance(Locale.ENGLISH)

        val sorted = LabelOrdering.sort(listOf("Settings", "Calendar", "Alarm"), collator)

        assertEquals(listOf("Alarm", "Calendar", "Settings"), sorted)
    }
}

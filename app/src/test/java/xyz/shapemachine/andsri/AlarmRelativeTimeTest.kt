package xyz.shapemachine.andsri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmRelativeTimeTest {
    @Test
    fun futureAlarmsUseHumanScaleUnits() {
        assertEquals(AlarmRelativeTime(1, AlarmTimeUnit.MINUTES), AlarmRelativeTime.from(0L, 1L))
        assertEquals(AlarmRelativeTime(45, AlarmTimeUnit.MINUTES), AlarmRelativeTime.from(0L, 45L * 60_000L))
        assertEquals(AlarmRelativeTime(8, AlarmTimeUnit.HOURS), AlarmRelativeTime.from(0L, 8L * 3_600_000L))
        assertEquals(AlarmRelativeTime(2, AlarmTimeUnit.DAYS), AlarmRelativeTime.from(0L, 47L * 3_600_000L))
    }

    @Test
    fun elapsedAlarmsAreHidden() {
        assertNull(AlarmRelativeTime.from(100L, 100L))
        assertNull(AlarmRelativeTime.from(101L, 100L))
    }
}

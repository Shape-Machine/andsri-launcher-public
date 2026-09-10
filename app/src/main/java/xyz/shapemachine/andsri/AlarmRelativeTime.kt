package xyz.shapemachine.andsri

internal enum class AlarmTimeUnit {
    MINUTES,
    HOURS,
    DAYS,
}

internal data class AlarmRelativeTime(
    val value: Int,
    val unit: AlarmTimeUnit,
) {
    companion object {
        private const val MINUTE_MILLIS = 60_000L
        private const val HOUR_MILLIS = 60L * MINUTE_MILLIS
        private const val DAY_MILLIS = 24L * HOUR_MILLIS

        fun from(nowMillis: Long, triggerMillis: Long): AlarmRelativeTime? {
            val remaining = triggerMillis - nowMillis
            if (remaining <= 0L) return null
            return when {
                remaining < HOUR_MILLIS -> AlarmRelativeTime(ceilUnits(remaining, MINUTE_MILLIS), AlarmTimeUnit.MINUTES)
                remaining < DAY_MILLIS -> AlarmRelativeTime(roundedUnits(remaining, HOUR_MILLIS), AlarmTimeUnit.HOURS)
                else -> AlarmRelativeTime(roundedUnits(remaining, DAY_MILLIS), AlarmTimeUnit.DAYS)
            }
        }

        private fun ceilUnits(duration: Long, unit: Long): Int =
            (1L + (duration - 1L) / unit).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        private fun roundedUnits(duration: Long, unit: Long): Int {
            val whole = duration / unit
            val rounded = whole + if (duration % unit >= unit / 2L) 1L else 0L
            return rounded.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        }
    }
}

package xyz.shapemachine.andsri

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherDataTest {
    @Test
    fun requestGateCoalescesAndRejectsStaleResults() {
        val gate = RequestGate()
        val first = gate.tryBegin()

        assertNotNull(first)
        assertNull(gate.tryBegin())
        assertTrue(gate.isActive())
        gate.invalidate()
        assertFalse(gate.finish(first!!))
        assertFalse(gate.isActive())

        val second = gate.tryBegin()
        assertNotNull(second)
        assertTrue(gate.finish(second!!))
        assertFalse(gate.isActive())
    }

    @Test(expected = IllegalArgumentException::class)
    fun responseReaderRejectsOversizedBodies() {
        OpenMeteoClient.readLimited(
            ByteArray(65 * 1024).inputStream(),
            Long.MAX_VALUE,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun responseReaderRejectsExpiredCalls() {
        OpenMeteoClient.readLimited(
            "{}".byteInputStream(),
            deadlineNanos = 10L,
            nowNanos = { 11L },
        )
    }

    @Test
    fun parsesOnlyRequiredLocationFields() {
        val locations = OpenMeteoClient.parseLocations(
            """{"results":[{"name":"Amsterdam","latitude":52.37,"longitude":4.89,"country":"Netherlands","admin1":"North Holland"}]}""",
        )

        assertEquals(
            listOf(WeatherLocation("Amsterdam, North Holland, Netherlands", 52.37, 4.89)),
            locations,
        )
    }

    @Test
    fun missingGeocodingResultsReturnsEmptyList() {
        assertEquals(emptyList<WeatherLocation>(), OpenMeteoClient.parseLocations("{}"))
    }

    @Test
    fun parsesCurrentWeather() {
        val snapshot = OpenMeteoClient.parseWeather(
            """{"current":{"temperature_2m":18.4,"weather_code":2}}""",
            "Amsterdam",
            TemperatureUnit.CELSIUS,
            1234L,
        )

        assertEquals(WeatherSnapshot("Amsterdam", 18.4, 2, 1234L, TemperatureUnit.CELSIUS), snapshot)
    }

    @Test
    fun parsesAndBoundsHourlyForecast() {
        val hourlyValues = (0 until 14).joinToString(",")
        val hourlyTimes = (0 until 14).joinToString(",") { "\"2026-09-07T${it.toString().padStart(2, '0')}:00\"" }
        val snapshot = OpenMeteoClient.parseWeather(
            """{"current":{"time":"2026-09-07T00:15","temperature_2m":18.4,"weather_code":2},"hourly":{"time":[$hourlyTimes],"temperature_2m":[$hourlyValues],"weather_code":[$hourlyValues],"precipitation_probability":[${(0 until 14).joinToString(",") { (it * 10).toString() }}]}}""",
            "Amsterdam",
            TemperatureUnit.CELSIUS,
            1234L,
        )

        assertEquals(12, snapshot.forecast.size)
        assertEquals(ForecastHour(1.0, 1, 10), snapshot.forecast.first())
        assertEquals(ForecastHour(12.0, 12, 100), snapshot.forecast.last())
    }

    @Test
    fun systemUnitFollowsLocaleMeasurementConvention() {
        assertEquals(TemperatureUnit.FAHRENHEIT, OpenMeteoClient.resolveUnit(TemperatureUnit.SYSTEM, Locale.US))
        val dutch = Locale.Builder().setLanguage("nl").setRegion("NL").build()
        assertEquals(TemperatureUnit.CELSIUS, OpenMeteoClient.resolveUnit(TemperatureUnit.SYSTEM, dutch))
        assertEquals(TemperatureUnit.FAHRENHEIT, OpenMeteoClient.resolveUnit(TemperatureUnit.FAHRENHEIT, dutch))
    }
}

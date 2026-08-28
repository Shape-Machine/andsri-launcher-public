package xyz.shapemachine.andsri

import android.content.Context
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

data class WeatherSnapshot(
    val locationName: String,
    val temperature: Double,
    val weatherCode: Int,
    val fetchedAtMillis: Long,
    val unit: TemperatureUnit,
)

class RequestGate {
    private val generation = AtomicLong()
    @Volatile private var activeToken: Long? = null

    @Synchronized fun tryBegin(): Long? {
        if (activeToken != null) return null
        return generation.incrementAndGet().also { activeToken = it }
    }

    @Synchronized fun finish(token: Long): Boolean {
        if (activeToken != token) return false
        activeToken = null
        return true
    }

    @Synchronized fun invalidate() {
        generation.incrementAndGet()
        activeToken = null
    }

    fun isActive() = activeToken != null
}

class WeatherCache(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(config: WeatherConfig): WeatherSnapshot? {
        val location = config.location ?: return null
        if (preferences.getString(KEY_LOCATION, null) != locationKey(location) ||
            preferences.getString(KEY_UNIT, null) != OpenMeteoClient.resolveUnit(config.unit).name
        ) return null
        return runCatching {
            WeatherSnapshot(
                location.name,
                java.lang.Double.longBitsToDouble(preferences.getLong(KEY_TEMPERATURE, 0L)),
                preferences.getInt(KEY_CODE, -1).also { require(it >= 0) },
                preferences.getLong(KEY_FETCHED_AT, 0L).also { require(it > 0L) },
                config.unit,
            )
        }.getOrNull()
    }

    fun save(location: WeatherLocation, snapshot: WeatherSnapshot) {
        preferences.edit()
            .putString(KEY_LOCATION, locationKey(location))
            .putString(KEY_UNIT, snapshot.unit.name)
            .putLong(KEY_TEMPERATURE, java.lang.Double.doubleToRawLongBits(snapshot.temperature))
            .putInt(KEY_CODE, snapshot.weatherCode)
            .putLong(KEY_FETCHED_AT, snapshot.fetchedAtMillis)
            .apply()
    }

    fun clear() = preferences.edit().clear().apply()

    private fun locationKey(location: WeatherLocation) = "${location.latitude},${location.longitude}"

    private companion object {
        const val FILE_NAME = "weather_cache"
        const val KEY_LOCATION = "location"
        const val KEY_UNIT = "unit"
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_CODE = "code"
        const val KEY_FETCHED_AT = "fetched_at"
    }
}

class OpenMeteoClient {
    @Volatile private var connection: HttpURLConnection? = null

    fun resolveLocation(query: String, language: String): List<WeatherLocation> {
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8)
        val json = request("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=5&language=$language&format=json")
        return parseLocations(json)
    }

    fun fetch(config: WeatherConfig): WeatherSnapshot {
        val location = requireNotNull(config.location)
        val resolvedUnit = resolveUnit(config.unit)
        val unitParameter = if (resolvedUnit == TemperatureUnit.FAHRENHEIT) "fahrenheit" else "celsius"
        val json = request(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${location.latitude}&longitude=${location.longitude}" +
                "&current=temperature_2m,weather_code&temperature_unit=$unitParameter&forecast_days=1",
        )
        return parseWeather(json, location.name, resolvedUnit, System.currentTimeMillis())
    }

    fun cancel() {
        connection?.disconnect()
        connection = null
    }

    private fun request(url: String): String {
        val active = URI(url).toURL().openConnection() as HttpURLConnection
        connection = active
        try {
            active.connectTimeout = CONNECT_TIMEOUT_MS
            active.readTimeout = READ_TIMEOUT_MS
            active.instanceFollowRedirects = false
            active.setRequestProperty("Accept", "application/json")
            active.setRequestProperty("User-Agent", "andSri Android launcher")
            val code = active.responseCode
            require(code == HttpURLConnection.HTTP_OK) { "Weather service returned HTTP $code" }
            val deadlineNanos = System.nanoTime() + MAX_CALL_ELAPSED_MS * 1_000_000L
            return active.inputStream.use {
                readLimited(it, deadlineNanos, contentLength = active.contentLengthLong)
            }
        } finally {
            active.disconnect()
            if (connection === active) connection = null
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 8_000
        private const val MAX_CALL_ELAPSED_MS = 15_000L
        private const val MAX_RESPONSE_BYTES = 64 * 1024

        internal fun readLimited(
            input: java.io.InputStream,
            deadlineNanos: Long,
            nowNanos: () -> Long = System::nanoTime,
            contentLength: Long = -1L,
        ): String {
            require(contentLength <= MAX_RESPONSE_BYTES) { "Weather response is too large" }
            val initialSize = contentLength.coerceIn(0L, MAX_RESPONSE_BYTES.toLong()).toInt()
            val output = ByteArrayOutputStream(initialSize)
            val buffer = ByteArray(4096)
            while (true) {
                require(nowNanos() <= deadlineNanos) { "Weather request timed out" }
                val count = input.read(buffer)
                if (count < 0) break
                require(nowNanos() <= deadlineNanos) { "Weather request timed out" }
                require(output.size() + count <= MAX_RESPONSE_BYTES) { "Weather response is too large" }
                output.write(buffer, 0, count)
            }
            return output.toString(StandardCharsets.UTF_8)
        }

        internal fun parseLocations(json: String): List<WeatherLocation> {
            val values = JSONObject(json).optJSONArray("results") ?: return emptyList()
            return buildList {
                for (index in 0 until values.length()) {
                    val value = values.getJSONObject(index)
                    val name = listOfNotNull(
                        value.optString("name").takeIf(String::isNotBlank),
                        value.optString("admin1").takeIf(String::isNotBlank),
                        value.optString("country").takeIf(String::isNotBlank),
                    ).distinct().joinToString(", ")
                    if (name.isNotBlank()) add(WeatherLocation(name, value.getDouble("latitude"), value.getDouble("longitude")))
                }
            }
        }

        internal fun parseWeather(
            json: String,
            locationName: String,
            unit: TemperatureUnit,
            fetchedAtMillis: Long,
        ): WeatherSnapshot {
            val current = JSONObject(json).getJSONObject("current")
            return WeatherSnapshot(
                locationName,
                current.getDouble("temperature_2m"),
                current.getInt("weather_code"),
                fetchedAtMillis,
                unit,
            )
        }

        fun resolveUnit(unit: TemperatureUnit, locale: Locale = Locale.getDefault()): TemperatureUnit = when (unit) {
            TemperatureUnit.SYSTEM -> if (locale.country in setOf("US", "LR", "MM")) TemperatureUnit.FAHRENHEIT else TemperatureUnit.CELSIUS
            else -> unit
        }

        fun roundedTemperature(snapshot: WeatherSnapshot) = snapshot.temperature.roundToInt()
    }
}

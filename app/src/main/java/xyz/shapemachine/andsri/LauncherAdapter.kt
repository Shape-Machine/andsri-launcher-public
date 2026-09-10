package xyz.shapemachine.andsri

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.GridLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.TextView
import android.util.LruCache
import android.util.TypedValue
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class LauncherAdapter(
    private val context: Context,
    private val onClockClick: () -> Unit,
    private val onDateClick: () -> Unit,
    private val onAppClick: (AppEntry) -> Unit,
    private val onAppLongClick: (View, AppEntry) -> Unit,
    private val onSettingsClick: () -> Unit,
    private val onWeatherRefresh: () -> Unit,
    private val onWeatherAttribution: () -> Unit,
    private val onAppsToggle: (Boolean) -> Unit,
) : BaseAdapter() {
    private data class CachedIcon(val state: Drawable.ConstantState, val estimatedBytes: Int)
    private data class IconKey(val component: String, val theme: IconTheme, val color: Int)
    private data class WideControlsState(val favorites: List<AppEntry>, val appsExpanded: Boolean?)

    private var sourceRows: List<HomeRow> = listOf(HomeRow.Header)
    private var rows: List<HomeRow> = sourceRows
    private var wideLayout = false
    private var wideGlanceContainer: LinearLayout? = null
    private var wideControlsContainer: LinearLayout? = null
    private var wideFavoritesGrid: GridView? = null
    private var wideAppsToggleView: View? = null
    private var wideControlsState: WideControlsState? = null
    private var wideWeatherVisible = false
    private var appearance = AppearanceConfig()
    private var timeText = ""
    private var dateText = ""
    private var additionalTimeNames: List<String> = emptyList()
    private var additionalTimeValues: Array<String> = emptyArray()
    private var nextAlarmText = ""
    private var textColor = Color.WHITE
    private var weatherConfig = WeatherConfig()
    private var weatherSnapshot: WeatherSnapshot? = null
    private var weatherRefreshing = false
    private var weatherError: String? = null
    private var boundHeaderView: LinearLayout? = null
    private var boundTimeView: TextView? = null
    private var boundDateView: TextView? = null
    private var boundAdditionalTimeView: LinearLayout? = null
    private var boundNextAlarmView: TextView? = null
    private var boundWeatherView: LinearLayout? = null
    private val iconProvider = BundledIconProvider(context)
    private val normalIconCache = object : LruCache<String, CachedIcon>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: CachedIcon) = value.estimatedBytes
    }
    private val fontCache = mutableMapOf<FontPreset, Typeface>()
    private val iconLoader = Executors.newSingleThreadExecutor()
    private val iconLock = Any()
    private val pendingIconCallbacks = mutableMapOf<IconKey, MutableList<(IconKey, Drawable) -> Unit>>()
    private val failedIcons = mutableSetOf<IconKey>()
    @Volatile private var closed = false
    private val rowFallbacks = object : LruCache<String, Drawable>(128) {}
    private val favoriteFallbacks = object : LruCache<String, Drawable>(128) {}
    private val appClickListener = View.OnClickListener { anchor ->
        (anchor.tag as? AppEntry)?.let { app ->
            anchor.performHapticFeedback(0)
            onAppClick(app)
        }
    }
    private val appLongClickListener = View.OnLongClickListener { anchor ->
        (anchor.tag as? AppEntry)?.let { app ->
            anchor.performHapticFeedback(0)
            onAppLongClick(anchor, app)
            true
        } ?: false
    }

    override fun getCount() = rows.size
    override fun getItem(position: Int) = rows[position]
    override fun getItemId(position: Int) = position.toLong()
    override fun getViewTypeCount() = 8
    override fun getItemViewType(position: Int) = when (rows[position]) {
        HomeRow.Header -> 0
        is HomeRow.App -> 1
        is HomeRow.Favorites -> 2
        HomeRow.Empty -> 3
        HomeRow.Weather -> 4
        is HomeRow.AppsToggle -> 5
        HomeRow.Gap -> 6
        is HomeRow.AppPair -> 7
    }

    fun submit(updatedRows: List<HomeRow>, updatedAppearance: AppearanceConfig, updatedWeather: WeatherConfig, updatedTextColor: Int) {
        sourceRows = updatedRows
        rows = visibleRows()
        appearance = updatedAppearance
        weatherConfig = updatedWeather
        textColor = updatedTextColor
        if (updatedWeather.location == null) {
            weatherSnapshot = null
            weatherError = null
            boundWeatherView = null
        }
        refreshWideFixedViews()
        notifyDataSetChanged()
    }

    fun updateAppearance(updatedAppearance: AppearanceConfig, updatedWeather: WeatherConfig, updatedTextColor: Int) {
        val weatherPresetChanged = updatedWeather.preset != weatherConfig.preset
        val requiresRebind = updatedTextColor != textColor ||
            updatedAppearance.displayMode != appearance.displayMode ||
            updatedAppearance.font != appearance.font ||
            updatedAppearance.density != appearance.density ||
            updatedAppearance.iconTheme != appearance.iconTheme ||
            updatedAppearance.clockPreset != appearance.clockPreset
        appearance = updatedAppearance
        weatherConfig = updatedWeather
        textColor = updatedTextColor
        if (requiresRebind) {
            refreshWideFixedViews(forceRebuild = true)
            notifyDataSetChanged()
        }
        else if (weatherPresetChanged) boundWeatherView?.let(::bindWeather)
    }

    fun configureLayout(
        wide: Boolean,
        glanceContainer: LinearLayout? = null,
        controlsContainer: LinearLayout? = null,
    ) {
        wideLayout = wide
        wideGlanceContainer = glanceContainer
        wideControlsContainer = controlsContainer
        wideFavoritesGrid = null
        wideAppsToggleView = null
        wideControlsState = null
        clearBoundFixedViews()
        rows = visibleRows()
        refreshWideFixedViews(forceRebuild = true)
        notifyDataSetChanged()
    }

    fun scrollWideFavoritesToTop() {
        wideFavoritesGrid?.setSelection(0)
    }

    fun updateWeather(snapshot: WeatherSnapshot?, refreshing: Boolean, error: String? = null) {
        weatherSnapshot = snapshot
        weatherRefreshing = refreshing
        weatherError = error
        boundWeatherView?.let(::bindWeather)
    }

    fun updateClock(
        time: String,
        date: String,
        updatedAdditionalTimeNames: List<String>,
        updatedAdditionalTimeValues: Array<String>,
        nextAlarm: String,
    ) {
        timeText = time
        dateText = date
        additionalTimeNames = updatedAdditionalTimeNames
        additionalTimeValues = updatedAdditionalTimeValues
        boundTimeView?.text = time
        boundDateView?.text = date
        boundAdditionalTimeView?.let { bindAdditionalTimes(it, applyStyle = false) }
        if (nextAlarmText != nextAlarm) {
            nextAlarmText = nextAlarm
            boundNextAlarmView?.apply {
                text = nextAlarm
                visibility = if (nextAlarm.isBlank()) View.GONE else View.VISIBLE
            }
        }
    }

    fun preloadFavoriteIcons(apps: List<AppEntry>, config: AppearanceConfig, color: Int) {
        val immediatelyVisible = if (wideLayout) apps.take(WIDE_VISIBLE_FAVORITES) else apps
        if (config.iconTheme == IconTheme.NORMAL) immediatelyVisible.forEach { normalIcon(it) }
        else iconProvider.preload(immediatelyVisible.map { it.component.packageName }, config.iconTheme, color)
    }

    fun close() {
        closed = true
        synchronized(iconLock) { pendingIconCallbacks.clear() }
        iconLoader.shutdownNow()
    }

    fun clearDynamicIcons() {
        normalIconCache.evictAll()
        synchronized(iconLock) { failedIcons.clear() }
    }

    override fun getView(position: Int, recycled: View?, parent: ViewGroup): View = when (val row = rows[position]) {
        HomeRow.Header -> headerView(recycled)
        HomeRow.Weather -> weatherView(recycled)
        is HomeRow.App -> appView(row, recycled)
        is HomeRow.AppPair -> appPairView(row.apps, recycled)
        is HomeRow.Favorites -> favoritesView(row.apps, recycled)
        is HomeRow.AppsToggle -> appsToggleView(row.expanded, recycled)
        HomeRow.Gap -> gapView(recycled)
        HomeRow.Empty -> emptyView(recycled)
    }

    private fun visibleRows(): List<HomeRow> {
        if (!wideLayout) return sourceRows
        val appRows = sourceRows.mapNotNull { (it as? HomeRow.App)?.app }
        if (appRows.isEmpty()) return sourceRows.filterIsInstance<HomeRow.Empty>()
        return LayoutPolicy.rowMajorPairs(appRows).map(HomeRow::AppPair)
    }

    private fun refreshWideFixedViews(forceRebuild: Boolean = false) {
        if (!wideLayout) return
        val glance = wideGlanceContainer ?: return
        val controls = wideControlsContainer ?: return
        val shouldShowWeather = sourceRows.any { it == HomeRow.Weather }
        if (forceRebuild || glance.childCount == 0 || shouldShowWeather != wideWeatherVisible) populateWideGlance(glance)
        else {
            boundHeaderView?.let { headerView(it) }
            boundWeatherView?.let(::bindWeather)
        }
        val controlsState = currentWideControlsState()
        val previousControlsState = wideControlsState
        when {
            forceRebuild || previousControlsState == null || controlsState.favorites != previousControlsState.favorites -> {
                populateWideControls(controls, controlsState)
            }
            controlsState.appsExpanded != previousControlsState.appsExpanded -> {
                val toggle = wideAppsToggleView
                if (toggle != null && controlsState.appsExpanded != null) {
                    appsToggleView(controlsState.appsExpanded, toggle)
                    wideControlsState = controlsState
                } else {
                    populateWideControls(controls, controlsState)
                }
            }
        }
    }

    private fun populateWideGlance(container: LinearLayout) {
        clearBoundFixedViews()
        container.removeAllViews()
        wideWeatherVisible = sourceRows.any { it == HomeRow.Weather }
        container.addView(headerView(null), LinearLayout.LayoutParams(-1, -2))
        if (wideWeatherVisible) {
            container.addView(gapView(null), LinearLayout.LayoutParams(-1, dp(SECTION_GAP_DP)))
            container.addView(weatherView(null), LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun currentWideControlsState() = WideControlsState(
        favorites = sourceRows.filterIsInstance<HomeRow.Favorites>().firstOrNull()?.apps.orEmpty(),
        appsExpanded = sourceRows.filterIsInstance<HomeRow.AppsToggle>().firstOrNull()?.expanded,
    )

    private fun populateWideControls(container: LinearLayout, state: WideControlsState) {
        container.removeAllViews()
        wideFavoritesGrid = null
        wideAppsToggleView = null
        wideControlsState = state
        if (state.favorites.isNotEmpty()) {
            val grid = AdaptiveFavoritesList(context).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                stretchMode = GridView.STRETCH_COLUMN_WIDTH
                verticalSpacing = dp(6)
                setPadding(dp(18), dp(8), dp(18), dp(8))
                clipToPadding = false
                adapter = FavoriteGridAdapter(state.favorites)
            }
            wideFavoritesGrid = grid
            container.addView(grid, LinearLayout.LayoutParams(-1, dp(FAVORITE_VIEWPORT_DP)))
        }
        state.appsExpanded?.let {
            val toggle = appsToggleView(it, null)
            wideAppsToggleView = toggle
            container.addView(toggle, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun clearBoundFixedViews() {
        boundHeaderView = null
        boundTimeView = null
        boundDateView = null
        boundAdditionalTimeView = null
        boundNextAlarmView = null
        boundWeatherView = null
    }

    private fun weatherView(recycled: View?): View {
        val container = recycled as? LinearLayout ?: LinearLayout(context).apply {
            layoutParams = AbsListView.LayoutParams(-1, -2)
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            isHapticFeedbackEnabled = true
            addView(label(28f).apply { id = WEATHER_PRIMARY_ID; gravity = Gravity.CENTER })
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER
                orientation = LinearLayout.HORIZONTAL
                addView(label(15f).apply {
                    id = WEATHER_SECONDARY_ID
                    gravity = Gravity.CENTER
                    maxLines = 2
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(label(18f).apply {
                    id = WEATHER_ATTRIBUTION_ID
                    gravity = Gravity.CENTER
                    text = "ⓘ"
                    contentDescription = context.getString(R.string.weather_attribution)
                    minWidth = dp(44)
                    minHeight = dp(44)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { onWeatherAttribution() }
                })
            })
            addView(label(15f).apply {
                id = WEATHER_TERTIARY_ID
                gravity = Gravity.CENTER
                maxLines = 2
            })
            setOnClickListener { view ->
                if (!weatherRefreshing) {
                    view.performHapticFeedback(0)
                    onWeatherRefresh()
                }
            }
        }
        boundWeatherView = container
        bindWeather(container)
        return container
    }

    private fun bindWeather(container: LinearLayout) {
        val primary = container.findViewById<TextView>(WEATHER_PRIMARY_ID)
        val secondary = container.findViewById<TextView>(WEATHER_SECONDARY_ID)
        val attribution = container.findViewById<TextView>(WEATHER_ATTRIBUTION_ID)
        val tertiary = container.findViewById<TextView>(WEATHER_TERTIARY_ID)
        val snapshot = weatherSnapshot
        primary.maxLines = 2
        primary.ellipsize = null
        secondary.maxLines = 2
        secondary.ellipsize = null
        tertiary.maxLines = 2
        tertiary.ellipsize = null
        val condition = snapshot?.let { context.getString(weatherConditionLabel(it.weatherCode)) }
        val age = snapshot?.let(::weatherAge)
        val temperature = snapshot?.let {
            val suffix = if (OpenMeteoClient.resolveUnit(it.unit) == TemperatureUnit.FAHRENHEIT) "°F" else "°C"
            "${OpenMeteoClient.roundedTemperature(it)}$suffix"
        }
        val symbol = snapshot?.let { weatherSymbol(it.weatherCode) }
        when (weatherConfig.preset) {
            WeatherPreset.COMPACT -> {
                primary.textSize = 19f
                primary.text = if (snapshot == null) context.getString(R.string.weather_tap_to_check) else "$symbol  $temperature · $condition · $age"
                secondary.visibility = View.GONE
                attribution.visibility = View.GONE
                container.setPadding(dp(24), dp(4), dp(24), dp(10))
            }
            WeatherPreset.STANDARD -> {
                primary.textSize = 28f
                primary.text = temperature?.let { "$symbol  $it" } ?: context.getString(R.string.weather_tap_to_check)
                secondary.visibility = View.VISIBLE
                attribution.visibility = View.VISIBLE
                secondary.text = snapshot?.let { "${it.locationName} · $condition · $age" }.orEmpty()
                container.setPadding(dp(24), dp(8), dp(24), dp(14))
            }
            WeatherPreset.EMPHASIZED -> {
                primary.textSize = 40f
                primary.text = temperature?.let { "$symbol  $it" } ?: context.getString(R.string.weather_tap_to_check)
                secondary.visibility = View.VISIBLE
                attribution.visibility = View.VISIBLE
                secondary.text = snapshot?.let { "${it.locationName} · $condition · $age" }.orEmpty()
                container.setPadding(dp(24), dp(12), dp(24), dp(18))
            }
            WeatherPreset.FORECAST -> {
                primary.textSize = 21f
                primary.maxLines = 1
                primary.ellipsize = TextUtils.TruncateAt.END
                secondary.maxLines = 1
                secondary.ellipsize = TextUtils.TruncateAt.END
                tertiary.maxLines = 1
                tertiary.ellipsize = TextUtils.TruncateAt.END
                primary.text = if (snapshot == null) {
                    context.getString(R.string.weather_tap_to_check)
                } else {
                    "$symbol  $temperature · $condition · $age"
                }
                secondary.visibility = View.VISIBLE
                attribution.visibility = View.GONE
                tertiary.visibility = View.VISIBLE
                if (snapshot != null && snapshot.forecast.size >= 12) {
                    secondary.text = context.getString(
                        R.string.weather_next_four_hours,
                        snapshot.forecast.take(4).joinToString("  ") {
                            "${weatherSymbol(it.weatherCode)} ${it.temperature.roundToInt()}°"
                        },
                    )
                    tertiary.text = forecastSummary(snapshot.forecast)
                } else {
                    secondary.text = context.getString(R.string.weather_tap_for_forecast)
                    tertiary.text = ""
                }
                container.setPadding(dp(24), dp(8), dp(24), dp(14))
            }
        }
        if (weatherConfig.preset != WeatherPreset.FORECAST) tertiary.visibility = View.GONE
        if (weatherRefreshing) {
            if (weatherConfig.preset == WeatherPreset.COMPACT) primary.text = context.getString(R.string.weather_refreshing)
            else secondary.apply { visibility = View.VISIBLE; text = context.getString(R.string.weather_refreshing) }
        } else weatherError?.let {
            if (weatherConfig.preset == WeatherPreset.COMPACT) primary.text = it
            else secondary.apply { visibility = View.VISIBLE; text = it }
        }
        listOf(primary, secondary, tertiary, attribution).forEach { it.setTextColor(textColor); it.typeface = font() }
        attribution.alpha = 0.7f
        container.contentDescription = listOfNotNull(
            primary.text,
            secondary.text.takeIf { secondary.visibility == View.VISIBLE },
            tertiary.text.takeIf { tertiary.visibility == View.VISIBLE && it.isNotBlank() },
        ).joinToString(". ")
    }

    private fun appsToggleView(expanded: Boolean, recycled: View?): View = (recycled as? TextView ?: label(22f)).apply {
        text = if (expanded) "⌃" else "⌄"
        contentDescription = context.getString(if (expanded) R.string.hide_apps else R.string.show_apps)
        gravity = Gravity.CENTER
        setTextColor(textColor)
        typeface = font()
        setPadding(dp(24), dp(16), dp(24), dp(16))
        isHapticFeedbackEnabled = true
        setOnClickListener { view -> view.performHapticFeedback(0); onAppsToggle(!expanded) }
    }

    private fun gapView(recycled: View?): View = (recycled ?: View(context)).apply {
        layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(SECTION_GAP_DP))
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun weatherConditionLabel(code: Int) = when (code) {
        0 -> R.string.weather_clear
        1, 2 -> R.string.weather_partly_cloudy
        3 -> R.string.weather_cloudy
        45, 48 -> R.string.weather_fog
        51, 53, 55, 56, 57 -> R.string.weather_drizzle
        61, 63, 65, 66, 67, 80, 81, 82 -> R.string.weather_rain
        71, 73, 75, 77, 85, 86 -> R.string.weather_snow
        95, 96, 99 -> R.string.weather_thunderstorm
        else -> R.string.weather_unknown
    }

    private fun weatherSymbol(code: Int) = when (code) {
        0 -> "☀"
        1, 2 -> "⛅"
        3 -> "☁"
        45, 48 -> "≋"
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "☂"
        71, 73, 75, 77, 85, 86 -> "❄"
        95, 96, 99 -> "ϟ"
        else -> "·"
    }

    private fun forecastSummary(forecast: List<ForecastHour>): String {
        val low = forecast.minOf { it.temperature }.roundToInt()
        val high = forecast.maxOf { it.temperature }.roundToInt()
        val wetIndex = forecast.indexOfFirst {
            it.precipitationProbability >= 50 || it.weatherCode in PRECIPITATION_CODES
        }
        if (wetIndex >= 0) {
            val condition = context.getString(
                when (forecast[wetIndex].weatherCode) {
                    in SNOW_CODES -> R.string.weather_snow
                    in THUNDER_CODES -> R.string.weather_thunderstorm
                    in RAIN_CODES -> R.string.weather_rain
                    else -> R.string.weather_precipitation
                },
            )
            val timing = context.resources.getQuantityString(
                R.plurals.weather_hours_from_now,
                wetIndex + 1,
                wetIndex + 1,
            )
            return context.getString(
                R.string.weather_forecast_wet,
                low,
                high,
                condition,
                timing,
                forecast.maxOf { it.precipitationProbability },
            )
        }
        val clearingIndex = forecast.indexOfFirst { it.weatherCode in 0..2 }
            .takeIf { forecast.first().weatherCode !in 0..2 && it > 0 }
        if (clearingIndex != null) {
            val timing = context.resources.getQuantityString(
                R.plurals.weather_hours_from_now,
                clearingIndex + 1,
                clearingIndex + 1,
            )
            return context.getString(R.string.weather_forecast_clearing, low, high, timing)
        }
        return context.getString(R.string.weather_forecast_dry, low, high)
    }

    private fun weatherAge(snapshot: WeatherSnapshot): String {
        val minutes = ((System.currentTimeMillis() - snapshot.fetchedAtMillis).coerceAtLeast(0L) / 60_000L).toInt()
        return when {
            minutes < 1 -> context.getString(R.string.weather_updated_just_now)
            minutes < 60 -> context.resources.getQuantityString(R.plurals.weather_updated_minutes, minutes, minutes)
            minutes < 24 * 60 -> (minutes / 60).let { context.resources.getQuantityString(R.plurals.weather_updated_hours, it, it) }
            else -> (minutes / (24 * 60)).let { context.resources.getQuantityString(R.plurals.weather_updated_days, it, it) }
        }
    }

    private fun headerView(recycled: View?): View {
        val container = recycled as? LinearLayout ?: LinearLayout(context).apply {
            layoutParams = AbsListView.LayoutParams(-1, -2)
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(56), dp(24), 0)
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(-1, -2)
                addView(View(context), LinearLayout.LayoutParams(dp(48), dp(48)))
                addView(label(42f).apply {
                    id = TIME_ID
                    gravity = Gravity.CENTER
                    maxLines = 1
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(ImageButton(context).apply {
                    id = SETTINGS_ID
                    setImageResource(android.R.drawable.ic_menu_preferences)
                    background = null
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    contentDescription = context.getString(R.string.launcher_settings)
                    isHapticFeedbackEnabled = true
                    setOnClickListener { anchor -> anchor.performHapticFeedback(0); onSettingsClick() }
                }, LinearLayout.LayoutParams(dp(48), dp(48)))
            })
            addView(label(17f).apply { id = DATE_ID; gravity = Gravity.CENTER; maxLines = 2; layoutParams = LinearLayout.LayoutParams(-1, -2); setPadding(0, dp(6), 0, 0) })
            addView(LinearLayout(context).apply {
                id = SECONDARY_TIME_ID
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(-1, -2)
                setPadding(0, dp(4), 0, 0)
            })
            addView(label(14f).apply {
                id = NEXT_ALARM_ID
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(4), 0, 0)
            })
        }
        boundHeaderView = container
        container.setPadding(dp(24), dp(if (wideLayout) 20 else 56), dp(24), 0)
        val sizes = when (appearance.clockPreset) {
            ClockPreset.COMPACT -> 34f to 15f
            ClockPreset.STANDARD -> 42f to 17f
            ClockPreset.EMPHASIZED -> 52f to 18f
        }
        container.findViewById<TextView>(TIME_ID).apply {
            boundTimeView = this
            text = timeText
            setAutoSizeTextTypeUniformWithConfiguration(24, sizes.first.toInt(), 1, TypedValue.COMPLEX_UNIT_SP)
            setTextColor(textColor)
            typeface = font()
            setOnClickListener { onClockClick() }
        }
        container.findViewById<ImageButton>(SETTINGS_ID).drawable?.setTint(textColor)
        container.findViewById<TextView>(DATE_ID).apply { boundDateView = this; text = dateText; textSize = sizes.second; setTextColor(textColor); typeface = font(); setOnClickListener { onDateClick() } }
        container.findViewById<LinearLayout>(SECONDARY_TIME_ID).apply {
            boundAdditionalTimeView = this
            bindAdditionalTimes(this, applyStyle = true)
        }
        container.findViewById<TextView>(NEXT_ALARM_ID).apply {
            boundNextAlarmView = this
            text = nextAlarmText
            visibility = if (nextAlarmText.isBlank()) View.GONE else View.VISIBLE
            setTextColor(textColor)
            typeface = font()
            setOnClickListener { onClockClick() }
        }
        return container
    }

    private fun bindAdditionalTimes(container: LinearLayout, applyStyle: Boolean) {
        container.visibility = if (additionalTimeNames.isEmpty()) View.GONE else View.VISIBLE
        while (container.childCount > additionalTimeNames.size) {
            container.removeViewAt(container.childCount - 1)
        }
        while (container.childCount < additionalTimeNames.size) {
            container.addView(additionalTimeCell(), LinearLayout.LayoutParams(0, -2, 1f))
        }
        val currentFont = if (applyStyle) font() else null
        additionalTimeNames.forEachIndexed { index, name ->
            val cell = container.getChildAt(index) as LinearLayout
            (cell.getChildAt(0) as TextView).apply {
                if (text.toString() != name) text = name
                if (applyStyle) {
                    setTextColor(textColor)
                    typeface = currentFont
                }
            }
            (cell.getChildAt(1) as TextView).apply {
                val value = additionalTimeValues[index]
                if (text.toString() != value) text = value
                if (applyStyle) {
                    setTextColor(textColor)
                    typeface = currentFont
                }
            }
        }
    }

    private fun additionalTimeCell() = LinearLayout(context).apply {
        val cellFont = font()
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(label(13f).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            typeface = cellFont
        })
        addView(label(15f).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            typeface = cellFont
        })
    }

    private fun appView(row: HomeRow.App, recycled: View?): View {
        val view = recycled as? TextView ?: label(20f).apply { gravity = Gravity.CENTER_VERTICAL; isHapticFeedbackEnabled = true }
        bindAppView(view, row.app)
        return view
    }

    private fun appPairView(apps: List<AppEntry>, recycled: View?): View {
        val row = recycled as? LinearLayout ?: LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = AbsListView.LayoutParams(-1, -2)
            repeat(2) {
                addView(label(20f).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    isHapticFeedbackEnabled = true
                }, LinearLayout.LayoutParams(0, -2, 1f))
            }
        }
        repeat(2) { index ->
            val cell = row.getChildAt(index) as TextView
            val app = apps.getOrNull(index)
            if (app == null) {
                cell.visibility = View.INVISIBLE
                cell.tag = null
                cell.setCompoundDrawables(null, null, null, null)
                cell.setOnClickListener(null)
                cell.setOnLongClickListener(null)
            } else {
                cell.visibility = View.VISIBLE
                bindAppView(cell, app)
            }
        }
        return row
    }

    private fun bindAppView(view: TextView, app: AppEntry) {
        val vertical = when (appearance.density) { DensityPreset.COMPACT -> 11; DensityPreset.STANDARD -> 17; DensityPreset.COMFORTABLE -> 23 }
        view.setPadding(dp(28), dp(vertical), dp(28), dp(vertical))
        view.minHeight = dp(44)
        view.typeface = font()
        view.setTextColor(textColor)
        view.text = app.label
        view.contentDescription = app.label
        view.tag = app
        if (appearance.displayMode != AppDisplayMode.TEXT) {
            bindIcon(app, favorite = false) { key, icon ->
                if (view.tag == app && appearance.displayMode != AppDisplayMode.TEXT && iconKey(app) == key) {
                    icon.setBounds(0, 0, dp(34), dp(34))
                    view.setCompoundDrawables(icon, null, null, null)
                }
            }
            view.compoundDrawablePadding = dp(14)
        } else view.setCompoundDrawables(null, null, null, null)
        view.setOnClickListener(appClickListener)
        view.setOnLongClickListener(appLongClickListener)
    }

    private fun favoritesView(apps: List<AppEntry>, recycled: View?): View {
        val grid = recycled as? AdaptiveFavoritesGrid ?: AdaptiveFavoritesGrid(context).apply {
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        while (grid.childCount > apps.size) grid.removeViewAt(grid.childCount - 1)
        apps.forEachIndexed { index, app ->
            val icon = (grid.getChildAt(index) as? ImageView) ?: favoriteIcon().apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(76)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(4), dp(3), dp(4), dp(3))
                }
            }.also(grid::addView)
            bindFavoriteIcon(icon, app)
        }
        return grid
    }

    private fun favoriteIcon() = ImageView(context).apply {
        setPadding(dp(9), dp(9), dp(9), dp(9))
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        isHapticFeedbackEnabled = true
    }

    private fun bindFavoriteIcon(icon: ImageView, app: AppEntry) {
        icon.tag = app
        bindIcon(app, favorite = true) { key, drawable ->
            if (icon.tag == app && iconKey(app) == key) icon.setImageDrawable(drawable)
        }
        icon.contentDescription = app.label
        icon.tooltipText = app.label
        icon.setOnClickListener(appClickListener)
        icon.setOnLongClickListener(appLongClickListener)
    }

    private fun emptyView(recycled: View?): View = (recycled as? TextView ?: label(18f)).apply {
        text = context.getString(R.string.no_apps); setTextColor(textColor); gravity = Gravity.CENTER
        setPadding(dp(28), dp(32), dp(28), dp(32))
    }

    private fun label(size: Float) = TextView(context).apply { setTextColor(textColor); textSize = size }
    private fun font() = fontCache.getOrPut(appearance.font) {
        when (appearance.font) {
            FontPreset.SANS -> context.resources.getFont(R.font.atkinson_hyperlegible_next_regular)
            FontPreset.SERIF -> context.resources.getFont(R.font.newsreader_regular)
            FontPreset.MONOSPACE -> context.resources.getFont(R.font.maple_mono_regular)
        }
    }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    private fun normalIcon(app: AppEntry): Drawable? {
        val key = app.component.flattenToString()
        return normalIconCache.get(key)?.state?.newDrawable(context.resources) ?: runCatching {
            context.packageManager.getActivityIcon(app.component)
        }.getOrNull()?.also { drawable ->
            drawable.constantState?.let { state ->
                val cacheDimension = dp(64)
                val width = drawable.intrinsicWidth.coerceAtLeast(cacheDimension)
                val height = drawable.intrinsicHeight.coerceAtLeast(cacheDimension)
                normalIconCache.put(key, CachedIcon(state, width * height * 4))
            }
        }
    }

    private fun iconFor(app: AppEntry): Drawable = if (appearance.iconTheme == IconTheme.NORMAL) {
        normalIcon(app) ?: LetterTileDrawable(app.label, IconTheme.LAWNICONS, textColor)
    } else iconProvider.icon(app.component.packageName, appearance.iconTheme, textColor)
        ?: LetterTileDrawable(app.label, appearance.iconTheme, textColor)

    private fun iconKey(app: AppEntry) = IconKey(app.component.flattenToString(), appearance.iconTheme, textColor)

    private fun bindIcon(app: AppEntry, favorite: Boolean, onReady: (IconKey, Drawable) -> Unit) {
        val key = iconKey(app)
        val cached = if (key.theme == IconTheme.NORMAL) {
            normalIconCache.get(key.component)?.state?.newDrawable(context.resources)
        } else {
            iconProvider.cachedIcon(app.component.packageName, key.theme, key.color)
        }
        if (cached != null) {
            onReady(key, cached)
            return
        }
        val fallbackKey = "${app.label}:${key.theme}:${key.color}"
        val fallbacks = if (favorite) favoriteFallbacks else rowFallbacks
        onReady(key, fallbacks.get(fallbackKey) ?: LetterTileDrawable(app.label, key.theme, key.color).also {
            fallbacks.put(fallbackKey, it)
        })
        if (key.theme != IconTheme.NORMAL && !iconProvider.supports(app.component.packageName, key.theme)) return
        requestIcon(key, app, onReady)
    }

    private fun requestIcon(key: IconKey, app: AppEntry, onReady: (IconKey, Drawable) -> Unit) {
        val shouldLoad = synchronized(iconLock) {
            if (closed || key in failedIcons) return
            pendingIconCallbacks.getOrPut(key) { mutableListOf() }.let { callbacks ->
                callbacks.add(onReady)
                callbacks.size == 1
            }
        }
        if (!shouldLoad) return
        val task = Runnable {
            val loaded = runCatching {
                if (key.theme == IconTheme.NORMAL) normalIcon(app)
                else iconProvider.icon(app.component.packageName, key.theme, key.color)
            }.getOrNull()
            val callbacks = synchronized(iconLock) {
                if (loaded == null) failedIcons += key
                pendingIconCallbacks.remove(key).orEmpty()
            }
            if (loaded != null && !closed) context.mainExecutor.execute {
                if (!closed) callbacks.forEach { it(key, loaded.constantState?.newDrawable(context.resources) ?: loaded) }
            }
        }
        runCatching { iconLoader.execute(task) }.onFailure {
            synchronized(iconLock) { pendingIconCallbacks.remove(key) }
        }
    }

    companion object {
        private const val TIME_ID = 1001
        private const val DATE_ID = 1002
        private const val SETTINGS_ID = 1003
        private const val WEATHER_PRIMARY_ID = 1004
        private const val WEATHER_SECONDARY_ID = 1005
        private const val WEATHER_ATTRIBUTION_ID = 1006
        private const val WEATHER_TERTIARY_ID = 1007
        private const val SECONDARY_TIME_ID = 1008
        private const val NEXT_ALARM_ID = 1009
        private const val SECTION_GAP_DP = 24
        private const val FAVORITE_VIEWPORT_DP = 176
        private const val WIDE_VISIBLE_FAVORITES = 12
        private val RAIN_CODES = setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82)
        private val SNOW_CODES = setOf(71, 73, 75, 77, 85, 86)
        private val THUNDER_CODES = setOf(95, 96, 99)
        private val PRECIPITATION_CODES = RAIN_CODES + SNOW_CODES + THUNDER_CODES
    }

    private class AdaptiveFavoritesGrid(context: Context) : GridLayout(context) {
        private var measuredColumns = -1

        override fun onMeasure(widthSpec: Int, heightSpec: Int) {
            val available = MeasureSpec.getSize(widthSpec) - paddingLeft - paddingRight
            val columns = LayoutPolicy.favoriteColumnCount((available / resources.displayMetrics.density).toInt())
            if (columns != measuredColumns) {
                measuredColumns = columns
                columnCount = columns
            }
            super.onMeasure(widthSpec, heightSpec)
        }
    }

    private inner class FavoriteGridAdapter(private val apps: List<AppEntry>) : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int) = apps[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, recycled: View?, parent: ViewGroup): View {
            val icon = recycled as? ImageView ?: favoriteIcon().apply {
                layoutParams = AbsListView.LayoutParams(-1, dp(76))
            }
            bindFavoriteIcon(icon, apps[position])
            return icon
        }
    }

    private class AdaptiveFavoritesList(context: Context) : GridView(context) {
        private var measuredColumns = -1

        override fun onMeasure(widthSpec: Int, heightSpec: Int) {
            val available = MeasureSpec.getSize(widthSpec) - paddingLeft - paddingRight
            val columns = LayoutPolicy.favoriteColumnCount((available / resources.displayMetrics.density).toInt())
            if (columns != measuredColumns) {
                measuredColumns = columns
                numColumns = columns
            }
            super.onMeasure(widthSpec, heightSpec)
        }
    }

}

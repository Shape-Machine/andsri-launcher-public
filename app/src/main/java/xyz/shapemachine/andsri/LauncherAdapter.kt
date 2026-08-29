package xyz.shapemachine.andsri

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.TextView
import android.util.LruCache
import android.util.TypedValue
import java.util.concurrent.Executors

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

    private var rows: List<HomeRow> = listOf(HomeRow.Header)
    private var appearance = AppearanceConfig()
    private var timeText = ""
    private var dateText = ""
    private var textColor = Color.WHITE
    private var weatherConfig = WeatherConfig()
    private var weatherSnapshot: WeatherSnapshot? = null
    private var weatherRefreshing = false
    private var weatherError: String? = null
    private var boundTimeView: TextView? = null
    private var boundDateView: TextView? = null
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
    override fun getViewTypeCount() = 7
    override fun getItemViewType(position: Int) = when (rows[position]) {
        HomeRow.Header -> 0
        is HomeRow.App -> 1
        is HomeRow.Favorites -> 2
        HomeRow.Empty -> 3
        HomeRow.Weather -> 4
        is HomeRow.AppsToggle -> 5
        HomeRow.Gap -> 6
    }

    fun submit(updatedRows: List<HomeRow>, updatedAppearance: AppearanceConfig, updatedWeather: WeatherConfig, updatedTextColor: Int) {
        rows = updatedRows
        appearance = updatedAppearance
        weatherConfig = updatedWeather
        textColor = updatedTextColor
        if (updatedWeather.location == null) {
            weatherSnapshot = null
            weatherError = null
            boundWeatherView = null
        }
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
        if (requiresRebind) notifyDataSetChanged()
        else if (weatherPresetChanged) boundWeatherView?.let(::bindWeather)
    }

    fun updateWeather(snapshot: WeatherSnapshot?, refreshing: Boolean, error: String? = null) {
        weatherSnapshot = snapshot
        weatherRefreshing = refreshing
        weatherError = error
        boundWeatherView?.let(::bindWeather)
    }

    fun updateClock(time: String, date: String) {
        timeText = time
        dateText = date
        boundTimeView?.text = time
        boundDateView?.text = date
    }

    fun preloadFavoriteIcons(apps: List<AppEntry>, config: AppearanceConfig, color: Int) {
        if (config.iconTheme == IconTheme.NORMAL) apps.forEach { normalIcon(it) }
        else iconProvider.preload(apps.map { it.component.packageName }, config.iconTheme, color)
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
        is HomeRow.Favorites -> favoritesView(row.apps, recycled)
        is HomeRow.AppsToggle -> appsToggleView(row.expanded, recycled)
        HomeRow.Gap -> gapView(recycled)
        HomeRow.Empty -> emptyView(recycled)
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
        val snapshot = weatherSnapshot
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
        }
        if (weatherRefreshing) {
            if (weatherConfig.preset == WeatherPreset.COMPACT) primary.text = context.getString(R.string.weather_refreshing)
            else secondary.apply { visibility = View.VISIBLE; text = context.getString(R.string.weather_refreshing) }
        } else weatherError?.let {
            if (weatherConfig.preset == WeatherPreset.COMPACT) primary.text = it
            else secondary.apply { visibility = View.VISIBLE; text = it }
        }
        listOf(primary, secondary, attribution).forEach { it.setTextColor(textColor); it.typeface = font() }
        attribution.alpha = 0.7f
        container.contentDescription = listOfNotNull(primary.text, secondary.text.takeIf { secondary.visibility == View.VISIBLE }).joinToString(". ")
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
        }
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
        return container
    }

    private fun appView(row: HomeRow.App, recycled: View?): View {
        val app = row.app
        val view = recycled as? TextView ?: label(20f).apply { gravity = Gravity.CENTER_VERTICAL; isHapticFeedbackEnabled = true }
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
        return view
    }

    private fun favoritesView(apps: List<AppEntry>, recycled: View?): View {
        val grid = recycled as? AdaptiveFavoritesGrid ?: AdaptiveFavoritesGrid(context).apply {
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        while (grid.childCount > apps.size) grid.removeViewAt(grid.childCount - 1)
        apps.forEachIndexed { index, app ->
            val icon = (grid.getChildAt(index) as? ImageView) ?: ImageView(context).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(76)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(4), dp(3), dp(4), dp(3))
                }
                setPadding(dp(9), dp(9), dp(9), dp(9))
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                isHapticFeedbackEnabled = true
            }.also(grid::addView)
            icon.tag = app
            bindIcon(app, favorite = true) { key, drawable ->
                if (icon.tag == app && iconKey(app) == key) icon.setImageDrawable(drawable)
            }
            icon.contentDescription = app.label
            icon.tooltipText = app.label
            icon.setOnClickListener(appClickListener)
            icon.setOnLongClickListener(appLongClickListener)
        }
        return grid
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
        private const val SECTION_GAP_DP = 24
    }

    private class AdaptiveFavoritesGrid(context: Context) : GridLayout(context) {
        override fun onMeasure(widthSpec: Int, heightSpec: Int) {
            val available = MeasureSpec.getSize(widthSpec) - paddingLeft - paddingRight
            columnCount = LayoutPolicy.favoriteColumnCount((available / resources.displayMetrics.density).toInt())
            super.onMeasure(widthSpec, heightSpec)
        }
    }

}

package xyz.shapemachine.andsri

import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.provider.AlarmClock
import android.view.Menu
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.EditText
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.Toast
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var adapter: LauncherAdapter
    private lateinit var appRepository: AppRepository
    private lateinit var preferences: LauncherPreferences
    private lateinit var listView: ListView
    private lateinit var root: FrameLayout
    private lateinit var customWallpaper: ImageView
    private lateinit var weatherCache: WeatherCache
    private val weatherClient = OpenMeteoClient()
    private val weatherRequestGate = RequestGate()
    private val loader = Executors.newSingleThreadExecutor()
    private val reloadLock = Any()
    private var reloadRunning = false
    private var refreshDirty = true
    private var packagesDirty = true
    private var rowsDirty = true
    private var favoritePreloadDirty = true
    private var cachedAllApps: List<AppEntry> = emptyList()
    private var cachedVisibleApps: List<AppEntry> = emptyList()
    @Volatile private var isActive = false
    private var systemBarTextColor = Color.WHITE
    private var appliedAppearance: AppearanceConfig? = null
    private var appliedTextColor: Int? = null
    @Volatile private var displayedWallpaper: String? = null
    private lateinit var timeFormatter: DateFormat
    private lateinit var dateFormatter: DateFormat
    private val clockCalendar = Calendar.getInstance()
    private var displayedDay = Int.MIN_VALUE
    private var displayedDate = ""
    private val handler = Handler(Looper.getMainLooper())
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == LauncherPreferences.KEY_WEATHER_NAME ||
            key == LauncherPreferences.KEY_WEATHER_LATITUDE || key == LauncherPreferences.KEY_WEATHER_LONGITUDE ||
            key == LauncherPreferences.KEY_WEATHER_UNIT
        ) {
            weatherRequestGate.invalidate()
            weatherClient.cancel()
        }
        requestReload(
            packagesChanged = key == null || key == LauncherPreferences.KEY_LABELS,
            rowsChanged = key == null || key == LauncherPreferences.KEY_LABELS ||
                key == LauncherPreferences.KEY_FAVORITES || key == LauncherPreferences.KEY_HIDDEN ||
                key == LauncherPreferences.KEY_WEATHER_NAME || key == LauncherPreferences.KEY_WEATHER_LATITUDE ||
                key == LauncherPreferences.KEY_WEATHER_LONGITUDE || key == LauncherPreferences.KEY_APPS_EXPANDED,
            preloadFavorites = key == null || key == LauncherPreferences.KEY_FAVORITES ||
                key == LauncherPreferences.KEY_LABELS || key == LauncherPreferences.KEY_THEME,
        )
    }
    private val clockTick = object : Runnable {
        override fun run() {
            updateClock()
            scheduleNextMinute()
        }
    }
    private val launcherCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: android.os.UserHandle) = requestReload(packagesChanged = true, rowsChanged = true)
        override fun onPackageAdded(packageName: String, user: android.os.UserHandle) = requestReload(packagesChanged = true, rowsChanged = true)
        override fun onPackageChanged(packageName: String, user: android.os.UserHandle) = requestReload(packagesChanged = true, rowsChanged = true)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: android.os.UserHandle, replacing: Boolean) = requestReload(packagesChanged = true, rowsChanged = true)
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: android.os.UserHandle, replacing: Boolean) = requestReload(packagesChanged = true, rowsChanged = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureWindow()
        preferences = LauncherPreferences(this)
        weatherCache = WeatherCache(this)
        appRepository = AppRepository(this)
        adapter = LauncherAdapter(
            context = this,
            onClockClick = ::openClock,
            onDateClick = ::openCalendar,
            onAppClick = ::launchApp,
            onAppLongClick = ::showAppMenu,
            onSettingsClick = { startActivity(Intent(this, SettingsActivity::class.java)) },
            onWeatherRefresh = ::refreshWeather,
            onWeatherAttribution = {
                startActivityIfResolvable(Intent(Intent.ACTION_VIEW, Uri.parse("https://open-meteo.com/")))
            },
            onAppsToggle = preferences::setAppsExpanded,
        )
        listView = ListView(this).apply {
            id = LIST_ID
            setAdapter(this@MainActivity.adapter)
            divider = null
            isFastScrollEnabled = false
            isVerticalScrollBarEnabled = false
            cacheColorHint = Color.TRANSPARENT
            setBackgroundColor(Color.TRANSPARENT)
            clipToPadding = false
        }
        customWallpaper = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        root = FrameLayout(this).apply {
            addView(customWallpaper, FrameLayout.LayoutParams(-1, -1))
            addView(View(this@MainActivity).apply { id = OVERLAY_ID }, FrameLayout.LayoutParams(-1, -1))
            addView(listView, FrameLayout.LayoutParams(-1, -1))
            addView(View(this@MainActivity).apply { id = STATUS_BAR_ID }, FrameLayout.LayoutParams(-1, 0, Gravity.TOP))
        }
        setContentView(root)
        preferences.registerChangeListener(preferenceListener)
        getSystemService(LauncherApps::class.java).registerCallback(launcherCallback, handler)
        requestHomeRoleIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        isActive = true
        refreshClockFormatters()
        startReloadIfNeeded()
        clockTick.run()
    }

    override fun onPause() {
        isActive = false
        handler.removeCallbacks(clockTick)
        super.onPause()
    }

    override fun onDestroy() {
        preferences.unregisterChangeListener(preferenceListener)
        getSystemService(LauncherApps::class.java).unregisterCallback(launcherCallback)
        loader.shutdownNow()
        weatherRequestGate.invalidate()
        weatherClient.cancel()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        listView.setSelection(0)
    }

    private fun configureWindow() {
        window.isNavigationBarContrastEnforced = false
        window.decorView.setOnApplyWindowInsetsListener { view, insets ->
            val controller = window.insetsController
            val lightFlags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            controller?.setSystemBarsAppearance(
                if (systemBarTextColor == Color.BLACK) lightFlags else 0,
                lightFlags,
            )
            val statusHeight = insets.getInsets(WindowInsets.Type.statusBars()).top
            view.findViewById<View>(STATUS_BAR_ID)?.apply {
                layoutParams = (layoutParams as FrameLayout.LayoutParams).apply { height = statusHeight }
                setBackgroundColor(systemBarBackground())
            }
            view.setPadding(0, 0, 0, insets.getInsets(WindowInsets.Type.navigationBars()).bottom)
            insets
        }
    }

    private fun requestHomeRoleIfNeeded() {
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME) && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
            Toast.makeText(this, R.string.choose_home, Toast.LENGTH_LONG).show()
            startActivity(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
        }
    }

    private fun startReloadIfNeeded() {
        val work = synchronized(reloadLock) {
            if (!isActive || reloadRunning || !refreshDirty) return
            reloadRunning = true
            refreshDirty = false
            ReloadWork(packagesDirty, rowsDirty, favoritePreloadDirty).also {
                packagesDirty = false
                rowsDirty = false
                favoritePreloadDirty = false
            }
        }
        loader.execute {
            val result = runCatching { prepareReload(work) }
            mainExecutor.execute {
                result.getOrNull()?.takeUnless { isDestroyed }?.let { update ->
                    if (update.rows == null) adapter.updateAppearance(update.appearance, update.weather, update.textColor)
                    else adapter.submit(update.rows, update.appearance, update.weather, update.textColor)
                    adapter.updateWeather(update.weatherSnapshot, weatherRequestGate.isActive())
                    applyAppearance(update.appearance, update.textColor, update.wallpaper)
                }
                val rerun = synchronized(reloadLock) {
                    reloadRunning = false
                    refreshDirty && isActive
                }
                if (rerun) startReloadIfNeeded()
            }
        }
    }

    private fun prepareReload(work: ReloadWork): ReloadUpdate {
        var state = preferences.snapshot()
        if (work.scanPackages || cachedAllApps.isEmpty()) {
            cachedAllApps = appRepository.allApps(state.labels)
            state = preferences.reconcileInstalled(
                cachedAllApps.mapTo(mutableSetOf()) { it.component.flattenToString() },
                state,
            )
        }
        val rebuildRows = work.rebuildRows || work.scanPackages
        val rows = if (rebuildRows) {
            cachedVisibleApps = cachedAllApps.filterNot { it.component.flattenToString() in state.hidden }
            HomeRows.build(
                cachedVisibleApps,
                state.favorites,
                weatherConfigured = state.weather.location != null,
                appsExpanded = state.appsExpanded,
            )
        } else null
        val textColor = AppearanceResolver.textColor(this, state.appearance)
        if (work.preloadFavorites) {
            val favoriteLookup = cachedVisibleApps.associateBy { it.component.flattenToString() }
            val favoriteApps = state.favorites.mapNotNull(favoriteLookup::get)
            adapter.preloadFavoriteIcons(favoriteApps, state.appearance, textColor)
        }
        return ReloadUpdate(
            rows,
            state.appearance,
            state.weather,
            weatherCache.load(state.weather),
            textColor,
            loadWallpaperUpdate(state.appearance),
        )
    }

    private fun refreshWeather() {
        val config = preferences.weather()
        val location = config.location ?: return
        val requestToken = weatherRequestGate.tryBegin() ?: return
        adapter.updateWeather(weatherCache.load(config), refreshing = true)
        Thread({
            val result = runCatching { weatherClient.fetch(config) }
            mainExecutor.execute {
                if (isDestroyed || !weatherRequestGate.finish(requestToken) || preferences.weather() != config) return@execute
                result.getOrNull()?.let { weatherCache.save(location, it) }
                val snapshot = result.getOrNull() ?: weatherCache.load(config)
                adapter.updateWeather(
                    snapshot,
                    refreshing = false,
                    error = result.exceptionOrNull()?.let { getString(R.string.weather_refresh_failed) },
                )
            }
        }, "andSri-weather-refresh").start()
    }

    private fun requestReload(
        packagesChanged: Boolean = false,
        rowsChanged: Boolean = false,
        preloadFavorites: Boolean = packagesChanged,
    ) {
        synchronized(reloadLock) {
            refreshDirty = true
            packagesDirty = packagesDirty || packagesChanged
            rowsDirty = rowsDirty || rowsChanged
            favoritePreloadDirty = favoritePreloadDirty || preloadFavorites
        }
        if (isActive) startReloadIfNeeded()
    }

    private fun applyAppearance(config: AppearanceConfig, textColor: Int, wallpaperUpdate: WallpaperUpdate?) {
        if (config == appliedAppearance && textColor == appliedTextColor && wallpaperUpdate == null) return
        appliedAppearance = config
        appliedTextColor = textColor
        val backgroundColor = AppearanceResolver.backgroundColor(this, config)
        val showsWallpaper = !config.solidBackground && config.wallpaperFade < 255
        val usesSystemWallpaper = showsWallpaper && config.wallpaperUri == null
        if (usesSystemWallpaper) window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        systemBarTextColor = textColor
        root.findViewById<View>(STATUS_BAR_ID).setBackgroundColor(systemBarBackground())
        window.decorView.requestApplyInsets()
        root.setBackgroundColor(if (showsWallpaper) Color.TRANSPARENT else backgroundColor)
        customWallpaper.visibility = if (!showsWallpaper || config.wallpaperUri == null) View.GONE else View.VISIBLE
        if (customWallpaper.visibility == View.GONE && displayedWallpaper != null) {
            customWallpaper.setImageDrawable(null)
            displayedWallpaper = null
        }
        if (customWallpaper.visibility == View.VISIBLE && wallpaperUpdate != null) {
            if (wallpaperUpdate.bitmap != null) {
                customWallpaper.setImageBitmap(wallpaperUpdate.bitmap)
                displayedWallpaper = wallpaperUpdate.uri
            } else {
                customWallpaper.setImageDrawable(null)
                displayedWallpaper = null
                customWallpaper.visibility = View.GONE
                root.setBackgroundColor(backgroundColor)
            }
        }
        val overlay = root.findViewById<View>(OVERLAY_ID)
        when {
            !showsWallpaper -> {
                overlay.visibility = View.GONE
                customWallpaper.clearColorFilter()
            }
            customWallpaper.visibility == View.VISIBLE -> {
                overlay.visibility = View.GONE
                customWallpaper.colorFilter = BlendModeColorFilter(
                    Color.argb(config.wallpaperFade, Color.red(backgroundColor), Color.green(backgroundColor), Color.blue(backgroundColor)),
                    BlendMode.SRC_OVER,
                )
            }
            config.wallpaperUri == null -> {
                customWallpaper.clearColorFilter()
                overlay.visibility = View.VISIBLE
                overlay.setBackgroundColor(
                    Color.argb(config.wallpaperFade, Color.red(backgroundColor), Color.green(backgroundColor), Color.blue(backgroundColor)),
                )
            }
            else -> {
                customWallpaper.clearColorFilter()
                overlay.visibility = View.GONE
            }
        }
    }

    private fun systemBarBackground() = if (systemBarTextColor == Color.BLACK) Color.WHITE else Color.BLACK

    private fun loadWallpaperUpdate(config: AppearanceConfig): WallpaperUpdate? {
        val uri = config.wallpaperUri ?: return null
        if (config.solidBackground || config.wallpaperFade >= 255 || uri == displayedWallpaper) return null
        val bitmap = runCatching {
            val width = resources.displayMetrics.widthPixels
            val height = resources.displayMetrics.heightPixels
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, Uri.parse(uri))) { decoder, info, _ ->
                val sourceWidth = info.size.width
                val sourceHeight = info.size.height
                val targetAspect = width.toDouble() / height
                val sourceAspect = sourceWidth.toDouble() / sourceHeight
                val crop = if (sourceAspect > targetAspect) {
                    val cropWidth = (sourceHeight * targetAspect).toInt().coerceIn(1, sourceWidth)
                    val left = (sourceWidth - cropWidth) / 2
                    Rect(left, 0, left + cropWidth, sourceHeight)
                } else {
                    val cropHeight = (sourceWidth / targetAspect).toInt().coerceIn(1, sourceHeight)
                    val top = (sourceHeight - cropHeight) / 2
                    Rect(0, top, sourceWidth, top + cropHeight)
                }
                decoder.setCrop(crop)
                decoder.setTargetSize(width, height)
                decoder.allocator = ImageDecoder.ALLOCATOR_HARDWARE
            }
        }.getOrNull()
        return WallpaperUpdate(uri, bitmap)
    }

    private fun updateClock() {
        val now = Date()
        clockCalendar.time = now
        val day = clockCalendar.get(Calendar.YEAR) * 400 + clockCalendar.get(Calendar.DAY_OF_YEAR)
        if (day != displayedDay) {
            displayedDay = day
            displayedDate = dateFormatter.format(now)
        }
        adapter.updateClock(
            timeFormatter.format(now),
            displayedDate,
        )
    }

    private fun refreshClockFormatters() {
        timeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT)
        dateFormatter = DateFormat.getDateInstance(DateFormat.FULL)
        clockCalendar.timeZone = timeFormatter.timeZone
        displayedDay = Int.MIN_VALUE
    }

    private fun scheduleNextMinute() {
        handler.removeCallbacks(clockTick)
        val delay = 60_000L - (System.currentTimeMillis() % 60_000L) + 20L
        handler.postDelayed(clockTick, delay)
    }

    private fun launchApp(app: AppEntry) {
        getSystemService(LauncherApps::class.java)
            .startMainActivity(app.component, android.os.Process.myUserHandle(), null, null)
    }

    private fun showAppMenu(anchor: View, app: AppEntry) {
        val componentKey = app.component.flattenToString()
        val isFavorite = componentKey in preferences.favoriteComponents()
        PopupMenu(this, anchor).apply {
            menu.add(Menu.NONE, ACTION_FAVORITE, Menu.NONE, if (isFavorite) R.string.remove_favorite else R.string.add_favorite)
            menu.add(Menu.NONE, ACTION_RENAME, Menu.NONE, R.string.rename)
            menu.add(Menu.NONE, ACTION_INFO, Menu.NONE, R.string.app_info)
            menu.add(Menu.NONE, ACTION_HIDE, Menu.NONE, R.string.hide)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    ACTION_FAVORITE -> preferences.toggleFavorite(componentKey)
                    ACTION_RENAME -> renameApp(app)
                    ACTION_INFO -> openAppInfo(app.component)
                    ACTION_HIDE -> confirmHide(app)
                }
                true
            }
            show()
        }
    }

    private fun openAppInfo(component: ComponentName) {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${component.packageName}")))
    }

    private fun renameApp(app: AppEntry) {
        val input = EditText(this).apply { setText(app.label); selectAll() }
        AlertDialog.Builder(this)
            .setTitle(R.string.rename)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.restore_default) { _, _ ->
                preferences.saveCustomLabel(app.component.flattenToString(), null)
            }
            .setPositiveButton(R.string.save) { _, _ ->
                preferences.saveCustomLabel(app.component.flattenToString(), input.text.toString())
            }
            .show()
    }

    private fun confirmHide(app: AppEntry) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.hide_title, app.label))
            .setMessage(R.string.hide_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm_hide) { _, _ ->
                preferences.hide(app.component.flattenToString())
            }
            .show()
    }

    private fun openClock() {
        startActivityIfResolvable(Intent(AlarmClock.ACTION_SHOW_ALARMS))
    }

    private fun openCalendar() {
        startActivityIfResolvable(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR))
    }

    private fun startActivityIfResolvable(intent: Intent) {
        if (intent.resolveActivity(packageManager) != null) startActivity(intent)
        else Toast.makeText(this, R.string.action_unavailable, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val ACTION_INFO = 1
        private const val ACTION_HIDE = 3
        private const val ACTION_FAVORITE = 4
        private const val ACTION_RENAME = 5
        private const val OVERLAY_ID = 2001
        const val LIST_ID = 2002
        private const val STATUS_BAR_ID = 2003
    }

    private data class WallpaperUpdate(val uri: String, val bitmap: Bitmap?)
    private data class ReloadUpdate(
        val rows: List<HomeRow>?,
        val appearance: AppearanceConfig,
        val weather: WeatherConfig,
        val weatherSnapshot: WeatherSnapshot?,
        val textColor: Int,
        val wallpaper: WallpaperUpdate?,
    )
    private data class ReloadWork(
        val scanPackages: Boolean,
        val rebuildRows: Boolean,
        val preloadFavorites: Boolean,
    )
}

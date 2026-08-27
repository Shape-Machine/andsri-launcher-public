package xyz.shapemachine.andsri

import android.app.Activity
import android.app.AlertDialog
import android.app.LocaleManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.Bundle
import android.os.LocaleList
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {
    private lateinit var preferences: LauncherPreferences
    private lateinit var repository: AppRepository
    private val loader = java.util.concurrent.Executors.newSingleThreadExecutor()
    @Volatile private var installedApps: List<AppEntry> = emptyList()
    private val apps get() = installedApps
    private var appsLoaded = false
    private var appsLoading = false
    private var pendingAppsAction: (() -> Unit)? = null
    private var hiddenPromptCancellation: android.os.CancellationSignal? = null
    private var hiddenDialog: AlertDialog? = null
    private val appearance by lazy { preferences.appearance() }
    private val settingsTypeface: Typeface by lazy {
        resources.getFont(when (appearance.font) {
            FontPreset.SANS -> R.font.atkinson_hyperlegible_next_regular
            FontPreset.SERIF -> R.font.newsreader_regular
            FontPreset.MONOSPACE -> R.font.maple_mono_regular
        })
    }
    private val foregroundColor by lazy {
        val appearance = preferences.appearance()
        val systemDark = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        when {
            appearance.pureBlack -> Color.WHITE
            appearance.appearanceMode == AppearanceMode.LIGHT -> Color.BLACK
            appearance.appearanceMode == AppearanceMode.DARK || systemDark -> Color.WHITE
            else -> Color.BLACK
        }
    }
    private val backgroundColor get() = if (foregroundColor == Color.WHITE) Color.rgb(18, 18, 18) else Color.rgb(246, 246, 246)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = LauncherPreferences(this)
        repository = AppRepository(this)
        render()
    }

    private fun render() {
        val padding = dp(when (appearance.density) {
            DensityPreset.COMPACT -> 20
            DensityPreset.STANDARD -> 24
            DensityPreset.COMFORTABLE -> 28
        })
        val topPadding = dp(when (appearance.density) {
            DensityPreset.COMPACT -> 40
            DensityPreset.STANDARD -> 48
            DensityPreset.COMFORTABLE -> 56
        })
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, topPadding, padding, padding)
            setBackgroundColor(backgroundColor)
            addView(title(getString(R.string.settings_title), 30f))
            section(R.string.organize)
            action(R.string.edit_favorites, ::editFavorites)
            action(R.string.reorder_favorites, ::reorderFavorites)
            action(R.string.hidden_apps, ::authenticateHiddenApps)
            section(R.string.appearance)
            action(R.string.choose_wallpaper, ::chooseWallpaper)
            darknessControl()
            switchControl(R.string.pure_black, appearance.pureBlack) {
                preferences.saveAppearance(preferences.appearance().copy(pureBlack = it)); recreate()
            }
            enumControl(R.string.appearance_mode, AppearanceMode.entries, appearance.appearanceMode) {
                preferences.saveAppearance(preferences.appearance().copy(appearanceMode = it)); recreate()
            }
            enumControl(R.string.display_mode, AppDisplayMode.entries, appearance.displayMode) {
                preferences.saveAppearance(preferences.appearance().copy(displayMode = it))
            }
            iconThemeControl()
            enumControl(R.string.font_preset, FontPreset.entries, appearance.font, vertical = true) {
                preferences.saveAppearance(preferences.appearance().copy(font = it)); recreate()
            }
            enumControl(R.string.clock_preset, ClockPreset.entries, appearance.clockPreset) {
                preferences.saveAppearance(preferences.appearance().copy(clockPreset = it))
            }
            enumControl(R.string.list_density, DensityPreset.entries, appearance.density) {
                preferences.saveAppearance(preferences.appearance().copy(density = it)); recreate()
            }
            section(R.string.general)
            action(R.string.language, ::setLanguage)
            action(R.string.default_home, ::openHomeSettings)
            action(R.string.open_source_licenses, ::showLicenses)
            section(R.string.danger_zone)
            action(R.string.reset_launcher, ::confirmReset)
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(backgroundColor); addView(content) })
    }

    private fun LinearLayout.section(label: Int) = addView(title(getString(label), 15f).apply {
        val (top, bottom) = when (appearance.density) {
            DensityPreset.COMPACT -> 24 to 6
            DensityPreset.STANDARD -> 30 to 8
            DensityPreset.COMFORTABLE -> 36 to 10
        }
        alpha = 0.7f; setPadding(0, dp(top), 0, dp(bottom))
    })

    private fun LinearLayout.action(label: Int, action: () -> Unit, value: String? = null) = addView(TextView(context).apply {
        text = if (value == null) getString(label) else "${getString(label)}\n$value"
        textSize = 18f; setTextColor(foregroundColor); gravity = Gravity.CENTER_VERTICAL
        typeface = settingsTypeface
        val vertical = when (appearance.density) {
            DensityPreset.COMPACT -> 12
            DensityPreset.STANDARD -> 17
            DensityPreset.COMFORTABLE -> 22
        }
        setPadding(dp(4), dp(vertical), dp(4), dp(vertical)); isHapticFeedbackEnabled = true
        setOnClickListener { it.performHapticFeedback(0); action() }
    })

    private fun <T : Enum<T>> LinearLayout.enumControl(
        label: Int,
        values: List<T>,
        selected: T,
        vertical: Boolean = false,
        onPick: (T) -> Unit,
    ) {
        addView(title(getString(label), 16f).apply { setPadding(dp(4), dp(14), dp(4), dp(4)) })
        val ids = mutableMapOf<Int, T>()
        addView(RadioGroup(context).apply {
            orientation = if (vertical) RadioGroup.VERTICAL else RadioGroup.HORIZONTAL
            values.forEachIndexed { index, value ->
                val id = View.generateViewId()
                ids[id] = value
                addView(RadioButton(context).apply {
                    this.id = id
                    text = enumLabel(value)
                    textSize = 13f
                    typeface = if (value is FontPreset) fontTypeface(value) else settingsTypeface
                    buttonDrawable = null
                    background = segmentBackground(index, values.lastIndex, vertical)
                    setTextColor(ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                        intArrayOf(backgroundColor, foregroundColor),
                    ))
                    gravity = Gravity.CENTER
                    minHeight = dp(44)
                    isChecked = value == selected
                    setPadding(dp(6), dp(8), dp(6), dp(8))
                }, if (vertical) {
                    RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                } else {
                    RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
            }
            setOnCheckedChangeListener { _, checkedId -> ids[checkedId]?.let(onPick) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun fontTypeface(font: FontPreset): Typeface = resources.getFont(when (font) {
        FontPreset.SANS -> R.font.atkinson_hyperlegible_next_regular
        FontPreset.SERIF -> R.font.newsreader_regular
        FontPreset.MONOSPACE -> R.font.maple_mono_regular
    })

    private fun segmentBackground(index: Int, lastIndex: Int, vertical: Boolean): StateListDrawable {
        val radius = dp(10).toFloat()
        val isRtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val atStart = if (vertical || !isRtl) index == 0 else index == lastIndex
        val atEnd = if (vertical || !isRtl) index == lastIndex else index == 0
        val radii = if (vertical) {
            floatArrayOf(
                if (atStart) radius else 0f, if (atStart) radius else 0f,
                if (atStart) radius else 0f, if (atStart) radius else 0f,
                if (atEnd) radius else 0f, if (atEnd) radius else 0f,
                if (atEnd) radius else 0f, if (atEnd) radius else 0f,
            )
        } else {
            floatArrayOf(
                if (atStart) radius else 0f, if (atStart) radius else 0f,
                if (atEnd) radius else 0f, if (atEnd) radius else 0f,
                if (atEnd) radius else 0f, if (atEnd) radius else 0f,
                if (atStart) radius else 0f, if (atStart) radius else 0f,
            )
        }
        fun shape(fill: Int) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            color = ColorStateList.valueOf(fill)
            cornerRadii = radii
            setStroke(dp(1), Color.argb(110, Color.red(foregroundColor), Color.green(foregroundColor), Color.blue(foregroundColor)))
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_checked), shape(foregroundColor))
            addState(intArrayOf(), shape(Color.TRANSPARENT))
        }
    }

    private fun LinearLayout.darknessControl() {
        addView(title(getString(R.string.wallpaper_darkness), 16f).apply { setPadding(dp(4), dp(14), dp(4), 0) })
        addView(SeekBar(context).apply {
            max = 255
            progress = appearance.darkness
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = Unit
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    preferences.saveAppearance(preferences.appearance().copy(darkness = progress))
                }
            })
        })
    }

    private fun LinearLayout.iconThemeControl() {
        addView(title(getString(R.string.icon_theme), 16f).apply { setPadding(dp(4), dp(14), dp(4), dp(4)) })
        val provider = BundledIconProvider(context)
        val tiles = mutableMapOf<IconTheme, Pair<LinearLayout, TextView>>()
        val grid = GridLayout(context).apply { columnCount = 2 }

        fun updateSelection(selected: IconTheme) = tiles.forEach { (theme, views) ->
            val (tile, label) = views
            val checked = theme == selected
            tile.background = iconTileBackground(checked)
            tile.isSelected = checked
            label.text = if (checked) "✓  ${enumLabel(theme)}" else enumLabel(theme)
        }

        IconTheme.entries.forEach { theme ->
            val label = TextView(context).apply {
                text = enumLabel(theme)
                textSize = 14f
                typeface = settingsTypeface
                setTextColor(foregroundColor)
                gravity = Gravity.CENTER
                maxLines = 1
            }
            val tile = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(12), dp(8), dp(10))
                isClickable = true
                isFocusable = true
                isHapticFeedbackEnabled = true
                contentDescription = enumLabel(theme)
                addView(ImageView(context).apply {
                    setImageDrawable(provider.preview(theme, foregroundColor))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }, LinearLayout.LayoutParams(dp(48), dp(48)))
                addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)))
                setOnClickListener { view ->
                    view.performHapticFeedback(0)
                    preferences.saveAppearance(preferences.appearance().copy(iconTheme = theme))
                    updateSelection(theme)
                }
            }
            tiles[theme] = tile to label
            grid.addView(tile, GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            })
        }
        updateSelection(appearance.iconTheme)
        addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun iconTileBackground(selected: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(10).toFloat()
        setColor(if (selected) Color.argb(24, Color.red(foregroundColor), Color.green(foregroundColor), Color.blue(foregroundColor)) else Color.TRANSPARENT)
        setStroke(
            dp(if (selected) 2 else 1),
            Color.argb(if (selected) 220 else 70, Color.red(foregroundColor), Color.green(foregroundColor), Color.blue(foregroundColor)),
        )
    }

    @Suppress("DEPRECATION")
    private fun LinearLayout.switchControl(label: Int, selected: Boolean, onPick: (Boolean) -> Unit) =
        addView(Switch(context).apply {
            text = getString(label)
            textSize = 18f
            typeface = settingsTypeface
            setTextColor(foregroundColor)
            isChecked = selected
            setPadding(dp(4), dp(12), dp(4), dp(12))
            setOnCheckedChangeListener { _, checked -> onPick(checked) }
        })

    private fun withApps(action: () -> Unit) {
        if (appsLoaded) return action()
        pendingAppsAction = action
        if (appsLoading) return
        appsLoading = true
        loader.execute {
            val loaded = runCatching { repository.allApps(preferences.customLabels()) }.getOrDefault(emptyList())
            mainExecutor.execute {
                if (isDestroyed) return@execute
                installedApps = loaded
                appsLoaded = true
                appsLoading = false
                pendingAppsAction.also { pendingAppsAction = null }?.invoke()
            }
        }
    }

    private fun editFavorites() = withApps(::showEditFavorites)

    private fun showEditFavorites() {
        if (apps.isEmpty()) return Toast.makeText(this, R.string.no_apps, Toast.LENGTH_SHORT).show()
        val current = preferences.favoriteComponents()
        pickApps(R.string.edit_favorites, current.toSet()) { selected ->
            preferences.saveFavorites(current.filter { it in selected } + selected.filterNot { it in current })
        }
    }

    private fun reorderFavorites() = withApps(::showReorderFavorites)

    private fun showReorderFavorites() {
        if (apps.isEmpty()) return Toast.makeText(this, R.string.no_apps, Toast.LENGTH_SHORT).show()
        val components = preferences.favoriteComponents()
        if (components.isEmpty()) return Toast.makeText(this, R.string.no_favorites, Toast.LENGTH_SHORT).show()
        val labels = apps.associateBy { it.component.flattenToString() }
        val ordered = components.toMutableList()
        val list = ListView(this)
        val reorderAdapter = object : BaseAdapter() {
            override fun getCount() = ordered.size
            override fun getItem(position: Int) = ordered[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, recycled: View?, parent: android.view.ViewGroup): View =
                (recycled as? TextView ?: TextView(this@SettingsActivity).apply {
                    textSize = 18f
                    typeface = settingsTypeface
                    setTextColor(foregroundColor)
                    setPadding(dp(16), dp(16), dp(12), dp(16))
                }).apply {
                    text = "☰  ${labels[ordered[position]]?.label ?: ordered[position]}"
                }
        }
        list.adapter = reorderAdapter
        var dragged = -1
        list.setOnTouchListener { _, event ->
            val position = list.pointToPosition(event.x.toInt(), event.y.toInt())
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val handleTouched = if (list.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                        event.x >= list.width - dp(56)
                    } else event.x <= dp(56)
                    dragged = if (handleTouched && position >= 0) position else -1
                    dragged >= 0
                }
                MotionEvent.ACTION_MOVE -> if (dragged >= 0 && position >= 0 && position != dragged) {
                    ordered.add(position, ordered.removeAt(dragged))
                    dragged = position
                    reorderAdapter.notifyDataSetChanged()
                    true
                } else dragged >= 0
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val wasDragging = dragged >= 0
                    dragged = -1
                    wasDragging
                }
                else -> dragged >= 0
            }
        }
        val reorderContent = LinearLayout(this).apply {
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, resources.displayMetrics.heightPixels / 2))
        }
        AlertDialog.Builder(this).setTitle(R.string.reorder_favorites).setMessage(R.string.drag_reorder_hint)
            .setView(reorderContent).setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ -> preferences.saveFavorites(ordered) }.show()
    }

    private fun authenticateHiddenApps() = withApps(::showHiddenAuthentication)

    private fun showHiddenAuthentication() {
        if (apps.isEmpty()) return Toast.makeText(this, R.string.no_apps, Toast.LENGTH_SHORT).show()
        runCatching {
            val prompt = BiometricPrompt.Builder(this).setTitle(getString(R.string.hidden_apps))
                .setSubtitle(getString(R.string.authenticate_hidden))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()
            hiddenPromptCancellation?.cancel()
            hiddenPromptCancellation = android.os.CancellationSignal()
            prompt.authenticate(hiddenPromptCancellation!!, mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = showHiddenApps()
            })
        }.onFailure { Toast.makeText(this, R.string.authentication_unavailable, Toast.LENGTH_LONG).show() }
    }

    private fun showHiddenApps() {
        val hidden = preferences.hiddenComponents()
        val values = apps.filter { it.component.flattenToString() in hidden }
        if (values.isEmpty()) return AlertDialog.Builder(this).setMessage(R.string.no_hidden_apps).setPositiveButton(android.R.string.ok, null).show().also { hiddenDialog = it }.let { }
        val checked = BooleanArray(values.size)
        AlertDialog.Builder(this).setTitle(R.string.restore_hidden).setMultiChoiceItems(values.map { it.label }.toTypedArray(), checked) { _, i, value -> checked[i] = value }
            .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.restore) { _, _ ->
                preferences.restoreHidden(values.indices.filter { checked[it] }.mapTo(mutableSetOf()) { values[it].component.flattenToString() })
            }.show().also { hiddenDialog = it }
    }

    private fun chooseWallpaper() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) }, WALLPAPER_REQUEST)
    }

    @Deprecated("Activity result retained to avoid an AndroidX dependency")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == WALLPAPER_REQUEST && resultCode == RESULT_OK) data?.data?.let { uri ->
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                .onSuccess { preferences.saveAppearance(preferences.appearance().copy(wallpaperUri = uri.toString(), pureBlack = false)) }
                .onFailure { Toast.makeText(this, R.string.wallpaper_error, Toast.LENGTH_LONG).show() }
        }
    }

    private fun enumLabel(value: Enum<*>) = getString(when (value) {
        AppearanceMode.SYSTEM -> R.string.option_system
        AppearanceMode.LIGHT -> R.string.option_light
        AppearanceMode.DARK -> R.string.option_dark
        AppDisplayMode.TEXT -> R.string.option_text
        AppDisplayMode.ICON_TEXT -> R.string.option_icon_text
        IconTheme.NORMAL -> R.string.option_normal
        IconTheme.LAWNICONS -> R.string.option_lawnicons
        IconTheme.ARCTICONS -> R.string.option_arcticons
        IconTheme.MONDSTERN -> R.string.option_mondstern
        IconTheme.CUSCON -> R.string.option_cuscon
        IconTheme.DELTA -> R.string.option_delta
        IconTheme.DOLLPHONE -> R.string.option_dollphone
        IconTheme.SNOW -> R.string.option_snow
        FontPreset.SANS -> R.string.option_sans
        FontPreset.SERIF -> R.string.option_serif
        FontPreset.MONOSPACE -> R.string.option_monospace
        DensityPreset.COMPACT -> R.string.option_compact
        DensityPreset.STANDARD -> R.string.option_standard
        DensityPreset.COMFORTABLE -> R.string.option_comfortable
        ClockPreset.STANDARD -> R.string.option_standard
        ClockPreset.COMPACT -> R.string.option_compact
        ClockPreset.EMPHASIZED -> R.string.option_emphasized
        else -> error("Missing label for $value")
    })

    private fun setLanguage() {
        val tags = arrayOf("", "en", "nl", "hi")
        val current = getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        val selected = tags.indexOfFirst { it == current }.coerceAtLeast(0)
        AlertDialog.Builder(this).setTitle(R.string.language)
            .setSingleChoiceItems(arrayOf(getString(R.string.option_system), "English", "Nederlands", "हिन्दी"), selected) { dialog, index ->
            getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tags[index])
            dialog.dismiss()
        }.show()
    }

    private fun openHomeSettings() = startActivity(Intent(Settings.ACTION_HOME_SETTINGS))

    private fun showLicenses() {
        val files = assets.list("licenses").orEmpty().sorted()
        val names = files.map { it.substringBeforeLast('.').replace('-', ' ').lowercase().replaceFirstChar(Char::titlecase) }
        AlertDialog.Builder(this).setTitle(R.string.open_source_licenses).setItems(names.toTypedArray()) { _, index ->
            val text = assets.open("licenses/${files[index]}").bufferedReader().use { it.readText() }
            val content = TextView(this).apply { this.text = text; setTextColor(foregroundColor); textSize = 12f; typeface = settingsTypeface; setPadding(dp(20), dp(16), dp(20), dp(16)) }
            AlertDialog.Builder(this).setTitle(names[index]).setView(ScrollView(this).apply { addView(content) })
                .setPositiveButton(android.R.string.ok, null).show()
        }.show()
    }
    private fun confirmReset() = AlertDialog.Builder(this).setTitle(R.string.reset_launcher).setMessage(R.string.reset_message)
        .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.reset) { _, _ -> preferences.reset(); finish() }.show().let { }

    private fun pickApps(title: Int, selected: Set<String>, onSave: (List<String>) -> Unit) {
        val values = apps
        val checked = BooleanArray(values.size) { values[it].component.flattenToString() in selected }
        AlertDialog.Builder(this).setTitle(title).setMultiChoiceItems(values.map { it.label }.toTypedArray(), checked) { _, i, value -> checked[i] = value }
            .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save) { _, _ -> onSave(values.indices.filter { checked[it] }.map { values[it].component.flattenToString() }) }.show()
    }

    override fun onStop() {
        hiddenPromptCancellation?.cancel(); hiddenPromptCancellation = null
        hiddenDialog?.dismiss(); hiddenDialog = null
        super.onStop()
    }

    override fun onDestroy() {
        loader.shutdownNow()
        super.onDestroy()
    }

    private fun title(text: String, size: Float) = TextView(this).apply { this.text = text; textSize = size; typeface = settingsTypeface; setTextColor(foregroundColor); gravity = Gravity.START }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    companion object { private const val WALLPAPER_REQUEST = 41 }
}

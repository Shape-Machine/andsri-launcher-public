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

class LauncherAdapter(
    private val context: Context,
    private val onClockClick: () -> Unit,
    private val onDateClick: () -> Unit,
    private val onAppClick: (AppEntry) -> Unit,
    private val onAppLongClick: (View, AppEntry) -> Unit,
    private val onSettingsClick: () -> Unit,
) : BaseAdapter() {
    private data class CachedIcon(val state: Drawable.ConstantState, val estimatedBytes: Int)

    private var rows: List<HomeRow> = listOf(HomeRow.Header)
    private var appearance = AppearanceConfig()
    private var timeText = ""
    private var dateText = ""
    private var textColor = Color.WHITE
    private var boundTimeView: TextView? = null
    private var boundDateView: TextView? = null
    private val iconProvider = BundledIconProvider(context)
    private val normalIconCache = object : LruCache<String, CachedIcon>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: CachedIcon) = value.estimatedBytes
    }
    private val fontCache = mutableMapOf<FontPreset, Typeface>()

    override fun getCount() = rows.size
    override fun getItem(position: Int) = rows[position]
    override fun getItemId(position: Int) = position.toLong()
    override fun getViewTypeCount() = 5
    override fun getItemViewType(position: Int) = when (rows[position]) {
        HomeRow.Header -> 0
        is HomeRow.App -> 1
        is HomeRow.Favorites -> 2
        HomeRow.Spacer -> 3
        HomeRow.Empty -> 4
    }

    fun submit(updatedRows: List<HomeRow>, updatedAppearance: AppearanceConfig, updatedTextColor: Int) {
        rows = updatedRows
        appearance = updatedAppearance
        textColor = updatedTextColor
        notifyDataSetChanged()
    }

    fun updateAppearance(updatedAppearance: AppearanceConfig, updatedTextColor: Int) {
        val requiresRebind = updatedTextColor != textColor ||
            updatedAppearance.displayMode != appearance.displayMode ||
            updatedAppearance.font != appearance.font ||
            updatedAppearance.density != appearance.density ||
            updatedAppearance.iconTheme != appearance.iconTheme ||
            updatedAppearance.clockPreset != appearance.clockPreset
        appearance = updatedAppearance
        textColor = updatedTextColor
        if (requiresRebind) notifyDataSetChanged()
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

    override fun getView(position: Int, recycled: View?, parent: ViewGroup): View = when (val row = rows[position]) {
        HomeRow.Header -> headerView(recycled)
        is HomeRow.App -> appView(row, recycled)
        is HomeRow.Favorites -> favoritesView(row.apps, recycled)
        HomeRow.Spacer -> spacerView(recycled)
        HomeRow.Empty -> emptyView(recycled)
    }

    private fun headerView(recycled: View?): View {
        val container = recycled as? LinearLayout ?: LinearLayout(context).apply {
            layoutParams = AbsListView.LayoutParams(-1, -2)
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(56), dp(24), dp(40))
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(-1, -2)
                addView(View(context), LinearLayout.LayoutParams(dp(48), dp(48)))
                addView(label(42f).apply { id = TIME_ID; gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, -2, 1f))
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
            addView(label(17f).apply { id = DATE_ID; gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(-1, -2); setPadding(0, dp(6), 0, 0) })
        }
        val sizes = when (appearance.clockPreset) {
            ClockPreset.COMPACT -> 34f to 15f
            ClockPreset.STANDARD -> 42f to 17f
            ClockPreset.EMPHASIZED -> 52f to 18f
        }
        container.findViewById<TextView>(TIME_ID).apply { boundTimeView = this; text = timeText; textSize = sizes.first; setTextColor(textColor); typeface = font(); setOnClickListener { onClockClick() } }
        container.findViewById<ImageButton>(SETTINGS_ID).drawable?.setTint(textColor)
        container.findViewById<TextView>(DATE_ID).apply { boundDateView = this; text = dateText; textSize = sizes.second; setTextColor(textColor); typeface = font(); setOnClickListener { onDateClick() } }
        return container
    }

    private fun appView(row: HomeRow.App, recycled: View?): View {
        val app = row.app
        val view = recycled as? TextView ?: label(20f).apply { gravity = Gravity.CENTER_VERTICAL; isHapticFeedbackEnabled = true }
        val vertical = when (appearance.density) { DensityPreset.COMPACT -> 11; DensityPreset.STANDARD -> 17; DensityPreset.COMFORTABLE -> 23 }
        view.setPadding(dp(28), dp(vertical), dp(28), dp(vertical))
        view.typeface = font()
        view.setTextColor(textColor)
        view.text = app.label
        view.contentDescription = app.label
        if (appearance.displayMode != AppDisplayMode.TEXT) {
            val icon = iconFor(app)
            icon.setBounds(0, 0, dp(34), dp(34))
            view.setCompoundDrawables(icon, null, null, null)
            view.compoundDrawablePadding = dp(14)
        } else view.setCompoundDrawables(null, null, null, null)
        view.setOnClickListener { anchor -> anchor.performHapticFeedback(0); onAppClick(app) }
        view.setOnLongClickListener { anchor -> anchor.performHapticFeedback(0); onAppLongClick(anchor, app); true }
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
            icon.setImageDrawable(iconFor(app))
            icon.contentDescription = app.label
            icon.tooltipText = app.label
            icon.setOnClickListener { anchor -> anchor.performHapticFeedback(0); onAppClick(app) }
            icon.setOnLongClickListener { anchor -> anchor.performHapticFeedback(0); onAppLongClick(anchor, app); true }
        }
        return grid
    }

    private fun emptyView(recycled: View?): View = (recycled as? TextView ?: label(18f)).apply {
        text = context.getString(R.string.no_apps); setTextColor(textColor); gravity = Gravity.CENTER
        setPadding(dp(28), dp(32), dp(28), dp(32))
    }

    private fun spacerView(recycled: View?): View = (recycled ?: View(context)).apply {
        layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34))
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
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

    companion object {
        private const val TIME_ID = 1001
        private const val DATE_ID = 1002
        private const val SETTINGS_ID = 1003
    }

    private class AdaptiveFavoritesGrid(context: Context) : GridLayout(context) {
        override fun onMeasure(widthSpec: Int, heightSpec: Int) {
            val available = MeasureSpec.getSize(widthSpec) - paddingLeft - paddingRight
            columnCount = if (available >= (440 * resources.displayMetrics.density).toInt()) 5 else 4
            super.onMeasure(widthSpec, heightSpec)
        }
    }

}

package xyz.shapemachine.andsri.settings

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView

/** andSri settings controls v1. Canonical copy: launcher private repository. */
object SettingsControls {
    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
    fun sectionTop(density: Int) = 24 + density * 6
    fun sectionBottom(density: Int) = 6 + density * 2
    fun rowPadding(density: Int) = 12 + density * 5
    fun pageTop(density: Int) = 40 + density * 8

    fun language(context: Context, systemLabel: String): String {
        val locale = context.getSystemService(LocaleManager::class.java).applicationLocales
        return if (locale.isEmpty) systemLabel else when (locale[0]?.language) {
            "en" -> "English"
            "nl" -> "Nederlands"
            "hi" -> "हिन्दी"
            else -> locale[0]?.getDisplayName(locale[0]).orEmpty()
        }
    }

    fun <T> choices(context: Context, values: List<T>, selected: T, label: (T) -> String,
        font: (T) -> Typeface, foreground: Int, background: Int, density: Int,
        vertical: Boolean = false, onPick: (T) -> Unit): RadioGroup {
        val group = object : RadioGroup(context) {
            private var stacked: Boolean? = null
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
                val widest = (0 until childCount).maxOfOrNull { index ->
                    val button = getChildAt(index) as RadioButton
                    button.paint.measureText(button.text.toString()) + button.paddingLeft + button.paddingRight
                } ?: 0f
                val stack = vertical || maxOf(widest, dp(context, 48).toFloat()) * childCount > available
                if (stacked != stack) {
                    stacked = stack
                    orientation = if (stack) VERTICAL else HORIZONTAL
                    for (i in 0 until childCount) {
                        val button = getChildAt(i) as RadioButton
                        button.layoutParams = if (stack) LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                            else LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                        button.background = segment(context, i, childCount - 1, stack, foreground)
                    }
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }
        group.isBaselineAligned = false
        values.forEach { value ->
            group.addView(RadioButton(context).apply {
                id = View.generateViewId()
                text = label(value)
                textSize = 13f
                typeface = font(value)
                buttonDrawable = null
                setTextColor(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(background, foreground)))
                gravity = Gravity.CENTER
                minWidth = 0
                minHeight = dp(context, 48)
                setPadding(dp(context, 8), dp(context, 8 + density * 2), dp(context, 8), dp(context, 8 + density * 2))
                isChecked = value == selected
                tag = value
                setOnClickListener { performHapticFeedback(0) }
            })
        }
        group.setOnCheckedChangeListener { _, id ->
            val index = (0 until group.childCount).firstOrNull { group.getChildAt(it).id == id }
            if (index != null) onPick(values[index])
        }
        return group
    }

    private fun segment(context: Context, index: Int, last: Int, vertical: Boolean, foreground: Int): StateListDrawable {
        val radius = dp(context, 10).toFloat()
        val rtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val start = if (vertical || !rtl) index == 0 else index == last
        val end = if (vertical || !rtl) index == last else index == 0
        val corners = if (vertical) floatArrayOf(if(start) radius else 0f, if(start) radius else 0f, if(start) radius else 0f, if(start) radius else 0f, if(end) radius else 0f, if(end) radius else 0f, if(end) radius else 0f, if(end) radius else 0f)
            else floatArrayOf(if(start) radius else 0f, if(start) radius else 0f, if(end) radius else 0f, if(end) radius else 0f, if(end) radius else 0f, if(end) radius else 0f, if(start) radius else 0f, if(start) radius else 0f)
        fun shape(fill: Int) = GradientDrawable().apply {
            color = ColorStateList.valueOf(fill)
            cornerRadii = corners
            setStroke(dp(context, 1), Color.argb(110, Color.red(foreground), Color.green(foreground), Color.blue(foreground)))
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_checked), shape(foreground))
            addState(intArrayOf(), shape(Color.TRANSPARENT))
        }
    }
}

/** Preserve position across preference recreation and data-only settings updates. */
open class SettingsPageActivity : Activity() {
    private var settingsScroll: ScrollView? = null
    private var restoredScroll = 0
    private var settingsDark = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoredScroll = savedInstanceState?.getInt("andsri.settings.scroll") ?: 0
    }
    protected fun trackSettingsScroll(scroll: ScrollView) {
        val position = settingsScroll?.scrollY ?: restoredScroll
        settingsScroll = scroll
        scroll.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                scroll.viewTreeObserver.removeOnGlobalLayoutListener(this)
                scroll.scrollTo(0, position)
            }
        })
    }
    protected fun applySettingsTheme(dark: Boolean) {
        settingsDark = dark
        window.setDecorFitsSystemWindows(false)
        window.decorView.setBackgroundColor(if (dark) Color.BLACK else Color.WHITE)
        window.isNavigationBarContrastEnforced = false
        window.decorView.setOnApplyWindowInsetsListener { view, insets ->
            val flags = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (dark) 0 else flags, flags)
            view.setPadding(0, insets.getInsets(android.view.WindowInsets.Type.statusBars()).top, 0,
                insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom)
            insets
        }
        setTheme(if (dark) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val flags = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (settingsDark) 0 else flags, flags)
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("andsri.settings.scroll", settingsScroll?.scrollY ?: restoredScroll)
        super.onSaveInstanceState(outState)
    }
}

package xyz.shapemachine.andsri

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

object AppearanceResolver {
    fun isDark(context: Context, config: AppearanceConfig): Boolean = when (config.appearanceMode) {
        AppearanceMode.DARK -> true
        AppearanceMode.LIGHT -> false
        AppearanceMode.SYSTEM -> context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    fun textColor(context: Context, config: AppearanceConfig) =
        if (isDark(context, config)) Color.WHITE else Color.BLACK

    fun backgroundColor(context: Context, config: AppearanceConfig) =
        if (isDark(context, config)) Color.BLACK else Color.WHITE
}

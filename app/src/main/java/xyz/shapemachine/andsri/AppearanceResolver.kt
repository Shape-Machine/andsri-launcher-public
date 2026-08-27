package xyz.shapemachine.andsri

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.app.WallpaperManager
import android.util.LruCache

object AppearanceResolver {
    private val wallpaperLuminanceCache = LruCache<String, Float>(4)

    fun textColor(context: Context, config: AppearanceConfig): Int {
        if (config.pureBlack || config.appearanceMode == AppearanceMode.DARK) return Color.WHITE
        if (config.appearanceMode == AppearanceMode.LIGHT) return Color.BLACK
        val wallpaperLuminance = config.wallpaperUri?.let { sampledLuminance(context, it) }
            ?: systemWallpaperLuminance(context)
        if (wallpaperLuminance != null) {
            val visibleLuminance = wallpaperLuminance * (1f - config.darkness / 255f)
            return if (visibleLuminance > 0.52f) Color.BLACK else Color.WHITE
        }
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        return if (night || config.darkness >= 110) Color.WHITE else Color.BLACK
    }

    private fun sampledLuminance(context: Context, value: String): Float? {
        wallpaperLuminanceCache.get(value)?.let { return it }
        return runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, Uri.parse(value))
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val scale = maxOf(info.size.width, info.size.height) / 24f
            decoder.setTargetSize(maxOf(1, (info.size.width / scale).toInt()), maxOf(1, (info.size.height / scale).toInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        var total = 0.0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val color = bitmap.getPixel(x, y)
            total += (0.2126 * Color.red(color) + 0.7152 * Color.green(color) + 0.0722 * Color.blue(color)) / 255.0
        }
            (total / (bitmap.width * bitmap.height)).toFloat()
        }.getOrNull()?.also { wallpaperLuminanceCache.put(value, it) }
    }

    private fun systemWallpaperLuminance(context: Context): Float? = runCatching {
        WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            ?.primaryColor?.toArgb()?.let(::luminance)
    }.getOrNull()

    private fun luminance(color: Int) =
        ((0.2126 * Color.red(color) + 0.7152 * Color.green(color) + 0.0722 * Color.blue(color)) / 255.0).toFloat()
}

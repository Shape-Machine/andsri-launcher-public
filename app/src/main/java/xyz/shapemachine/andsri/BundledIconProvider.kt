package xyz.shapemachine.andsri

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import android.util.LruCache

class BundledIconProvider(private val context: Context) {
    private data class CachedIcon(val state: Drawable.ConstantState, val estimatedBytes: Int)

    private val cache = object : LruCache<String, CachedIcon>(ICON_CACHE_BYTES) {
        override fun sizeOf(key: String, value: CachedIcon) = value.estimatedBytes
    }
    private val mappings = mutableMapOf<IconTheme, Map<String, String>>()

    private fun mapping(theme: IconTheme): Map<String, String> = synchronized(mappings) {
        mappings.getOrPut(theme) {
            context.assets.open("icons/${theme.folder}/mapping.tsv").bufferedReader().useLines { lines ->
                lines.mapNotNull { line -> line.split('\t', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            }
        }
    }

    fun icon(packageName: String, theme: IconTheme, color: Int): Drawable? {
        if (theme == IconTheme.NORMAL) return null
        val file = mapping(theme)[packageName] ?: return null
        return themedDrawable(theme, file, color)
    }

    fun cachedIcon(packageName: String, theme: IconTheme, color: Int): Drawable? {
        if (theme == IconTheme.NORMAL) return null
        val file = mapping(theme)[packageName] ?: return null
        return cache.get("${theme.name}:$file")?.state?.newDrawable(context.resources)?.mutate()?.apply {
            if (theme == IconTheme.LAWNICONS || theme == IconTheme.ARCTICONS || theme == IconTheme.SNOW) setTint(color)
        }
    }

    fun supports(packageName: String, theme: IconTheme) =
        theme != IconTheme.NORMAL && mapping(theme).containsKey(packageName)

    fun preview(theme: IconTheme, color: Int): Drawable? {
        if (theme == IconTheme.NORMAL) return runCatching {
            context.packageManager.getApplicationIcon("com.android.settings")
        }.getOrNull()
        val files = mapping(theme)
        val file = files["com.android.settings"] ?: files.values.firstOrNull() ?: return null
        return themedDrawable(theme, file, color)
    }

    private fun themedDrawable(theme: IconTheme, file: String, color: Int): Drawable? {
        val key = "${theme.name}:$file"
        val drawable = cache.get(key)?.state?.newDrawable(context.resources) ?: decode(theme, file)?.also { drawable ->
            drawable.constantState?.let { state ->
                val width = drawable.intrinsicWidth.coerceAtLeast(1)
                val height = drawable.intrinsicHeight.coerceAtLeast(1)
                cache.put(key, CachedIcon(state, width * height * 4))
            }
        }
        return drawable?.mutate()?.apply {
            if (theme == IconTheme.LAWNICONS || theme == IconTheme.ARCTICONS || theme == IconTheme.SNOW) setTint(color)
        }
    }

    private fun decode(theme: IconTheme, file: String): Drawable? = runCatching {
        ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.assets, "icons/${theme.folder}/$file")) { decoder, info, _ ->
            val target = (ICON_SIZE_DP * context.resources.displayMetrics.density).toInt()
            val scale = minOf(1f, target.toFloat() / maxOf(info.size.width, info.size.height))
            decoder.setTargetSize(
                maxOf(1, (info.size.width * scale).toInt()),
                maxOf(1, (info.size.height * scale).toInt()),
            )
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }.getOrNull()

    fun preload(packageNames: Collection<String>, theme: IconTheme, color: Int) {
        if (theme != IconTheme.NORMAL) mapping(theme)
        packageNames.forEach { icon(it, theme, color) }
    }

    private val IconTheme.folder get() = name.lowercase()

    private companion object {
        const val ICON_SIZE_DP = 64
        const val ICON_CACHE_BYTES = 6 * 1024 * 1024
    }
}

package xyz.shapemachine.andsri

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

class LetterTileDrawable(label: String, private val theme: IconTheme, private val foreground: Int = Color.WHITE) : Drawable() {
    private val letter = label.firstOrNull()?.uppercase() ?: "?"
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD }

    override fun draw(canvas: Canvas) {
        val radius = minOf(bounds.width(), bounds.height()) / 2f
        paint.style = Paint.Style.FILL
        val outlined = theme == IconTheme.ARCTICONS || theme == IconTheme.SNOW
        paint.color = if (outlined) Color.TRANSPARENT else foreground
        canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), radius - 1, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = maxOf(2f, radius / 10f)
        paint.color = foreground
        canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), radius - paint.strokeWidth, paint)
        paint.style = Paint.Style.FILL
        paint.color = if (outlined) foreground else contrasting(foreground)
        paint.textSize = radius
        val baseline = bounds.exactCenterY() - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(letter, bounds.exactCenterX(), baseline, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated by Android") override fun getOpacity() = PixelFormat.TRANSLUCENT

    private fun contrasting(color: Int) = if (Color.red(color) + Color.green(color) + Color.blue(color) > 382) Color.BLACK else Color.WHITE
}

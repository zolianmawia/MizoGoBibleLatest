package com.zoliana.khampat.mizobible.ui.transform

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.widget.TextView

/**
 * Custom background drawable for Bible verses that renders YouVersion-style highlights:
 * - On EVERY line ("tlar tin"), the background color extends only up to where the text on that line ends.
 * - Each line's right end curves with smooth rounded corners rather than extending flat to the screen edge.
 * - Single-line verses wrap the line of text like a rounded capsule.
 * - Consecutive verses with the same highlight color seamlessly connect without gaps.
 */
class VerseHighlightDrawable(
    private val textView: TextView,
    private val hasPin: () -> Boolean,
    var color: Int,
    var isPrevSame: Boolean,
    var isNextSame: Boolean,
    val density: Float,
    val isHeading: Boolean = false
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = this@VerseHighlightDrawable.color
    }
    private val combinedPath = Path()
    private val tempLinePath = Path()

    fun setColorValue(newColor: Int) {
        if (color != newColor) {
            color = newColor
            paint.color = newColor
            invalidateSelf()
        }
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        if (color == Color.TRANSPARENT || Color.alpha(color) == 0) return

        val b = bounds
        val left = b.left.toFloat()
        val top = b.top.toFloat()
        val right = b.right.toFloat()
        val bottom = b.bottom.toFloat()
        val totalWidth = right - left
        val totalHeight = bottom - top
        if (totalWidth <= 0f || totalHeight <= 0f) return

        val r = 7f * density
        val tl = if (isPrevSame) 0f else r
        val tr = if (isPrevSame) 0f else r
        val bl = if (isNextSame) 0f else r
        val br = if (isNextSame) 0f else r

        val layout = textView.layout
        if (layout == null || layout.lineCount <= 0) {
            combinedPath.reset()
            val effectiveTl = minOf(tl, totalWidth / 2f, totalHeight / 2f)
            val effectiveTr = minOf(tr, totalWidth / 2f, totalHeight / 2f)
            val effectiveBr = minOf(br, totalWidth / 2f, totalHeight / 2f)
            val effectiveBl = minOf(bl, totalWidth / 2f, totalHeight / 2f)
            combinedPath.addRoundRect(
                left, top, right, bottom,
                floatArrayOf(
                    effectiveTl, effectiveTl,
                    effectiveTr, effectiveTr,
                    effectiveBr, effectiveBr,
                    effectiveBl, effectiveBl
                ),
                Path.Direction.CW
            )
            canvas.drawPath(combinedPath, paint)
            return
        }

        val lineCount = layout.lineCount
        val textLeft = textView.left.toFloat()
        val textTop = textView.top.toFloat()
        val textPaddingLeft = textView.totalPaddingLeft.toFloat()
        val textPaddingTop = textView.totalPaddingTop.toFloat()

        val padH = 6f * density
        val overlap = 1.5f * density

        // Determine common left edge for all lines (or individual if heading/indented)
        var minLineLeft = Float.MAX_VALUE
        for (i in 0 until lineCount) {
            if (layout.getLineWidth(i) > 0f) {
                val l = textLeft + textPaddingLeft + layout.getLineLeft(i).toFloat()
                if (l < minLineLeft) minLineLeft = l
            }
        }
        if (minLineLeft == Float.MAX_VALUE) minLineLeft = textLeft + textPaddingLeft
        val commonStartLeft = (minLineLeft - padH).coerceAtLeast(left)

        combinedPath.reset()
        var hasCombined = false

        for (i in 0 until lineCount) {
            val lineWidth = layout.getLineWidth(i).toFloat()
            if (lineWidth <= 0f) continue

            val lineStartX = if (isHeading) {
                (textLeft + textPaddingLeft + layout.getLineLeft(i).toFloat() - padH).coerceAtLeast(left)
            } else {
                commonStartLeft
            }
            val lineEndX = textLeft + textPaddingLeft + layout.getLineLeft(i).toFloat() + lineWidth
            val xEnd = (lineEndX + padH).coerceAtMost(right)
            if (xEnd <= lineStartX) continue

            val lineTop = textTop + textPaddingTop + layout.getLineTop(i).toFloat()
            val lineBottom = textTop + textPaddingTop + layout.getLineBottom(i).toFloat()

            val yTop = if (i == 0) {
                (lineTop - if (!isPrevSame) 2f * density else 0f).coerceAtLeast(top)
            } else {
                lineTop - overlap
            }

            val yBottom = if (i == lineCount - 1) {
                (lineBottom + if (!isNextSame) 2f * density else 0f).coerceAtMost(bottom)
            } else {
                lineBottom + overlap
            }

            val curTl = if (i == 0 && !isPrevSame) r else 0f
            val curBl = if (i == lineCount - 1 && !isNextSame) r else 0f
            val curTr = r
            val curBr = r

            val effTl = minOf(curTl, (xEnd - lineStartX) / 2f, (yBottom - yTop) / 2f)
            val effTr = minOf(curTr, (xEnd - lineStartX) / 2f, (yBottom - yTop) / 2f)
            val effBr = minOf(curBr, (xEnd - lineStartX) / 2f, (yBottom - yTop) / 2f)
            val effBl = minOf(curBl, (xEnd - lineStartX) / 2f, (yBottom - yTop) / 2f)

            tempLinePath.reset()
            tempLinePath.addRoundRect(
                lineStartX, yTop, xEnd, yBottom,
                floatArrayOf(
                    effTl, effTl,
                    effTr, effTr,
                    effBr, effBr,
                    effBl, effBl
                ),
                Path.Direction.CW
            )

            if (!hasCombined) {
                combinedPath.set(tempLinePath)
                hasCombined = true
            } else {
                try {
                    val success = combinedPath.op(tempLinePath, Path.Op.UNION)
                    if (!success) {
                        combinedPath.addPath(tempLinePath)
                    }
                } catch (e: Exception) {
                    combinedPath.addPath(tempLinePath)
                }
            }
        }

        if (hasCombined) {
            canvas.drawPath(combinedPath, paint)
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

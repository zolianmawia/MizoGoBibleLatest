package com.zoliana.khampat.mizobible.ui.transform

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.text.style.LineBackgroundSpan
import androidx.core.graphics.ColorUtils

class WavyUnderlineSpan(private val color: Int) : LineBackgroundSpan {
    override fun drawBackground(
        canvas: Canvas, paint: Paint,
        left: Int, right: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, lineNumber: Int
    ) {
        val oldColor = paint.color
        val oldStyle = paint.style
        val oldStrokeWidth = paint.strokeWidth

        // SIAMLAM: Rawng hi tlem kan ti 'dal' (70% opacity) ang a
        // Chuan a line pawh kan ti 'sin' (2f) zawk ang.
        paint.color = ColorUtils.setAlphaComponent(color, 180) // 255 kha a chiang ber, 180 chu a dal deuh
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f 
        paint.isAntiAlias = true

        val path = Path()
        val waveLength = 16f // Tlem kan ti khat deuh ang
        val waveHeight = 4f  // A san lam (height) kan ti hniam ang, a lang sin zawk nan
        val y = bottom.toFloat() - 2f

        path.moveTo(left.toFloat(), y)
        
        var x = left.toFloat()
        while (x < right) {
            path.quadTo(x + waveLength / 4, y - waveHeight, x + waveLength / 2, y)
            path.quadTo(x + waveLength * 3 / 4, y + waveHeight, x + waveLength, y)
            x += waveLength
        }

        canvas.drawPath(path, paint)

        paint.color = oldColor
        paint.style = oldStyle
        paint.strokeWidth = oldStrokeWidth
    }
}
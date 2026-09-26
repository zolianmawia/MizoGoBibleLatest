package com.zoliana.khampat.mizobible.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.zoliana.khampat.mizobible.utils.ThemeHelper

class TreeConnectorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    init {
        updateThemeColors()
    }

    enum class NodeType {
        ROOT,
        CHILD
    }

    var nodeType: NodeType = NodeType.ROOT
        set(value) {
            field = value
            invalidate()
        }

    // For ROOT node
    var isFirstRoot: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    var isLastRoot: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    var isExpanded: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    var hasChildren: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    // For CHILD node
    var isFirstChild: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    var isLastChild: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    var parentHasBelowRoot: Boolean = true
        set(value) {
            field = value
            invalidate()
        }
    var childDotColor: Int = Color.parseColor("#88B4FC")
        set(value) {
            field = value
            invalidate()
        }

    var customDotY: Float = -1f
        set(value) {
            field = value
            invalidate()
        }

    var treeLineColor: Int = Color.parseColor("#DAC89C")
        set(value) {
            field = value
            linePaint.color = value
            invalidate()
        }

    var rootDotColor: Int = Color.parseColor("#1B365D")
        set(value) {
            field = value
            rootDotPaint.color = value
            invalidate()
        }

    fun updateThemeColors() {
        try {
            val themeMode = ThemeHelper.getCurrentThemeMode(context)
            val bgColor = ThemeHelper.getEffectiveBackgroundColor(context)
            val isDark = ThemeHelper.isColorDark(bgColor)
            val primaryColor = ThemeHelper.APP_COLORS.find {
                it.id.equals(ThemeHelper.getSelectedAppColor(context), ignoreCase = true)
            }?.colorInt ?: Color.parseColor("#1976D2")

            if (isDark) {
                treeLineColor = Color.parseColor("#5A5C75")
                rootDotColor = primaryColor
                childDotStrokePaint.color = Color.parseColor("#44FFFFFF")
            } else if (themeMode == ThemeHelper.ThemeMode.SEPIA) {
                treeLineColor = Color.parseColor("#C8B996")
                rootDotColor = primaryColor
                childDotStrokePaint.color = Color.parseColor("#9E8E6A")
            } else {
                treeLineColor = Color.parseColor("#DAC89C")
                rootDotColor = primaryColor
                childDotStrokePaint.color = Color.parseColor("#9E8E6A")
            }
        } catch (_: Exception) {}
        invalidate()
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DAC89C") // Golden tan wood tree line
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.8f)
        strokeCap = Paint.Cap.ROUND
    }

    private val rootDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1B365D") // Dark navy root dot
        style = Paint.Style.FILL
    }

    private val childDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val childDotStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9E8E6A")
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.2f)
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val trunkX = dpToPx(24f)
        val branchX = dpToPx(42f)
        val childDotX = dpToPx(62f)
        val h = height.toFloat()

        if (nodeType == NodeType.ROOT) {
            val centerY = if (customDotY >= 0) customDotY else h / 2f
            val rootDotRadius = dpToPx(5.5f)

            // Vertical line above dot
            if (!isFirstRoot) {
                canvas.drawLine(trunkX, 0f, trunkX, centerY - rootDotRadius, linePaint)
            }

            // Vertical line below dot
            if (!isLastRoot || (isExpanded && hasChildren)) {
                canvas.drawLine(trunkX, centerY + rootDotRadius, trunkX, h, linePaint)
            }

            // Draw root dot
            canvas.drawCircle(trunkX, centerY, rootDotRadius, rootDotPaint)

        } else if (nodeType == NodeType.CHILD) {
            val dotY = if (customDotY >= 0) customDotY else dpToPx(16f)
            val childDotRadius = dpToPx(6f)

            // 1. Trunk line on the left (at trunkX = 24dp)
            if (parentHasBelowRoot) {
                canvas.drawLine(trunkX, 0f, trunkX, h, linePaint)
            }

            // 2. Branch line (at branchX = 42dp)
            if (isFirstChild && isLastChild) {
                canvas.drawLine(branchX, dotY, childDotX - childDotRadius, dotY, linePaint)
            } else if (isFirstChild) {
                // Top corner of bracket: horizontal branch and downward line
                canvas.drawLine(branchX, dotY, childDotX - childDotRadius, dotY, linePaint)
                canvas.drawLine(branchX, dotY, branchX, h, linePaint)
            } else if (isLastChild) {
                // Bottom corner of bracket: upward line coming from above and horizontal branch
                canvas.drawLine(branchX, 0f, branchX, dotY, linePaint)
                canvas.drawLine(branchX, dotY, childDotX - childDotRadius, dotY, linePaint)
            } else {
                // Middle child of bracket: vertical line from top to bottom, plus horizontal branch
                canvas.drawLine(branchX, 0f, branchX, h, linePaint)
                canvas.drawLine(branchX, dotY, childDotX - childDotRadius, dotY, linePaint)
            }

            // 3. Child Dot with prominent color
            childDotPaint.color = childDotColor
            canvas.drawCircle(childDotX, dotY, childDotRadius, childDotPaint)
            canvas.drawCircle(childDotX, dotY, childDotRadius, childDotStrokePaint)
        }
    }
}

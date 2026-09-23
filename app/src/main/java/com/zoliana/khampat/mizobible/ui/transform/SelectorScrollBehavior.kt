package com.zoliana.khampat.mizobible.ui.transform

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat

class SelectorScrollBehavior(context: Context, attrs: AttributeSet) :
    CoordinatorLayout.Behavior<View>(context, attrs) {

    private var dyDirectionSum = 0

    override fun onStartNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: View,
        directTargetChild: View,
        target: View,
        axes: Int,
        type: Int
    ): Boolean {
        return axes == ViewCompat.SCROLL_AXIS_VERTICAL
    }

    override fun onNestedPreScroll(
        coordinatorLayout: CoordinatorLayout,
        child: View,
        target: View,
        dx: Int,
        dy: Int,
        consumed: IntArray,
        type: Int
    ) {
        if (dy > 0 && dyDirectionSum < 0 || dy < 0 && dyDirectionSum > 0) {
            dyDirectionSum = 0
        }
        dyDirectionSum += dy

        val translationY = if (dyDirectionSum > 0) {
            // Scroll down: Selector kha Bottom Nav (56dp) hmunah a thla ang
            // 56dp converted to pixels (approx 150px)
            56f * coordinatorLayout.context.resources.displayMetrics.density
        } else {
            // Scroll up: A hmun pangngaiah a let leh ang
            0f
        }

        child.animate().translationY(translationY).setDuration(200).start()
    }
}
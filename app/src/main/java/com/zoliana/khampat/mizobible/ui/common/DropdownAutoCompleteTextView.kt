package com.zoliana.khampat.mizobible.ui.common

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import com.google.android.material.textfield.MaterialAutoCompleteTextView

class DropdownAutoCompleteTextView : MaterialAutoCompleteTextView {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        setOnClickListener {
            showAllSuggestions()
        }
    }

    override fun enoughToFilter(): Boolean = true

    fun showAllSuggestions() {
        val a = adapter
        if (a is TitleSuggestionAdapter) {
            a.filter.filter(null) {
                if (isAttachedToWindow) {
                    try {
                        showDropDown()
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
        } else if (a != null && a.count > 0) {
            if (isAttachedToWindow) {
                try {
                    showDropDown()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        if (focused) {
            post {
                if (isAttachedToWindow && hasFocus()) {
                    showAllSuggestions()
                }
            }
        }
    }
}

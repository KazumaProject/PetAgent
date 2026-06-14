package com.kazumaproject.petagent.overlay

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.TextView
import kotlin.math.roundToInt

class CareItemFlyView(context: Context, itemText: String) : TextView(context) {
    init {
        text = itemText
        textSize = 28f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        isFocusable = false
        isClickable = false
        alpha = 1f
        scaleX = 1f
        scaleY = 1f
        minWidth = dp(SIZE_DP)
        minHeight = dp(SIZE_DP)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }

    companion object {
        const val SIZE_DP = 44
        const val DURATION_MS = 450L
    }
}

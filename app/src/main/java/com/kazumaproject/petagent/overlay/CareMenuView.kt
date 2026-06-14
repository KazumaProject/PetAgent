package com.kazumaproject.petagent.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

class CareMenuView(
    context: Context,
    onFoodClicked: () -> Unit,
    onWaterClicked: () -> Unit,
    onPlayClicked: () -> Unit,
) : LinearLayout(context) {
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(8), dp(8), dp(8))
        background = roundedBackground(Color.argb(214, 35, 38, 44), dp(24).toFloat())
        elevation = dp(8).toFloat()
        isClickable = true
        isFocusable = false

        addView(careButton("🍎", "Food", onFoodClicked))
        addView(careButton("💧", "Water", onWaterClicked))
        addView(careButton("🎾", "Play", onPlayClicked))
    }

    private fun careButton(text: String, label: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            contentDescription = label
            isClickable = true
            isFocusable = false
            background = roundedBackground(Color.argb(230, 255, 255, 255), dp(18).toFloat())
            setOnClickListener { onClick() }
            layoutParams = LayoutParams(dp(BUTTON_SIZE_DP), dp(BUTTON_SIZE_DP)).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            }
        }
    }

    private fun roundedBackground(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }

    private companion object {
        const val BUTTON_SIZE_DP = 44
    }
}

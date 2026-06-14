package com.kazumaproject.petagent.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

class PetBreakStatusPanelView(
    context: Context,
    private val onSettingsClicked: () -> Unit,
    private val onTalkClicked: () -> Unit,
    private val onCloseClicked: () -> Unit,
) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(Color.rgb(32, 37, 43))
            setStroke(dp(1), Color.argb(55, 255, 255, 255))
        }
        elevation = dp(8).toFloat()

        addView(
            TextView(context).apply {
                text = "Pet"
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            },
            matchWrapParams(),
        )
        addView(actionButton("Talk", onTalkClicked))
        addView(actionButton("設定を開く", onSettingsClicked))
        addView(actionButton("Close", onCloseClicked))
    }

    private fun actionButton(label: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            text = label
            textSize = 13f
            isAllCaps = false
            gravity = Gravity.CENTER
            setOnClickListener { onClick() }
        }
    }

    private fun matchWrapParams(): LayoutParams {
        return LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }
}

package com.kazumaproject.petagent.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.TextView
import com.kazumaproject.petagent.game.PrimaryNeed
import kotlin.math.roundToInt

class NeedBubbleView(
    context: Context,
    initialNeed: PrimaryNeed,
    private val onNeedClicked: (PrimaryNeed) -> Unit,
) : TextView(context) {
    private var currentNeed: PrimaryNeed = initialNeed

    init {
        textSize = 22f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        background = roundedBackground()
        elevation = dp(8).toFloat()
        isClickable = true
        isFocusable = false
        minWidth = dp(SIZE_DP)
        minHeight = dp(SIZE_DP)
        updateNeed(initialNeed)
        setOnClickListener { onNeedClicked(currentNeed) }
    }

    fun updateNeed(need: PrimaryNeed) {
        currentNeed = need
        text = when (need) {
            PrimaryNeed.Water -> "💧"
            PrimaryNeed.Food -> "🍎"
            PrimaryNeed.Play -> "🎾"
            PrimaryNeed.Sleep -> "💤"
        }
        contentDescription = when (need) {
            PrimaryNeed.Water -> "Needs water"
            PrimaryNeed.Food -> "Needs food"
            PrimaryNeed.Play -> "Wants to play"
            PrimaryNeed.Sleep -> "Sleepy"
        }
    }

    private fun roundedBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(232, 255, 255, 255))
            setStroke(dp(1), Color.argb(150, 35, 38, 44))
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }

    companion object {
        const val SIZE_DP = 40
    }
}

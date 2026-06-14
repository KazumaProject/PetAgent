package com.kazumaproject.petagent.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.kazumaproject.petagent.breakreminder.BreakReminderTone
import kotlin.math.roundToInt

class PetBreakReminderBubbleView(
    context: Context,
    message: String,
    activeMinutes: Int,
    tone: BreakReminderTone,
    private val onAcceptClicked: () -> Unit,
    private val onSnoozeClicked: () -> Unit,
    private val onDismissTodayClicked: () -> Unit,
) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(backgroundColor(tone))
            setStroke(dp(1), Color.argb(70, 255, 255, 255))
        }
        elevation = dp(8).toFloat()

        addView(
            TextView(context).apply {
                text = message
                textSize = 15f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            },
            matchWrapParams(),
        )
        addView(
            TextView(context).apply {
                text = "${activeMinutes}分が経ちました"
                textSize = 13f
                setTextColor(Color.argb(220, 255, 255, 255))
                gravity = Gravity.CENTER
                setPadding(0, dp(3), 0, dp(8))
            },
            matchWrapParams(),
        )

        val actionRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
        }
        actionRow.addView(button("休憩する", onAcceptClicked), weightedButtonParams())
        actionRow.addView(button("あとで", onSnoozeClicked), weightedButtonParams())
        addView(actionRow, matchWrapParams())
        addView(button("今日は表示しない", onDismissTodayClicked), matchWrapParams())
    }

    private fun button(label: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener {
                onClick()
            }
        }
    }

    private fun backgroundColor(tone: BreakReminderTone): Int {
        return when (tone) {
            BreakReminderTone.SOFT -> Color.rgb(54, 105, 102)
            BreakReminderTone.NORMAL -> Color.rgb(40, 74, 112)
            BreakReminderTone.ASSERTIVE -> Color.rgb(116, 76, 45)
        }
    }

    private fun matchWrapParams(): LayoutParams {
        return LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    private fun weightedButtonParams(): LayoutParams {
        return LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(2)
            marginEnd = dp(2)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }
}

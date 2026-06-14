package com.kazumaproject.petagent.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.kazumaproject.petagent.breakreminder.BreakStatus
import kotlin.math.roundToInt

class PetBreakStatusPanelView(
    context: Context,
    status: BreakStatus,
    private val onBreakNowClicked: () -> Unit,
    private val onSnoozeClicked: () -> Unit,
    private val onSettingsClicked: () -> Unit,
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
                text = "Break Status"
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            },
            matchWrapParams(),
        )
        addView(statusLine("次の休憩まで: ${formatRemaining(status.remainingMs)}"))
        addView(statusLine("現在の設定: ${status.intervalMinutes}分ごと"))
        addView(statusLine("今日の休憩: ${status.todayBreakCount}回"))
        addView(actionButton("今すぐ休憩", onBreakNowClicked))
        addView(actionButton("あとで通知", onSnoozeClicked))
        addView(actionButton("設定を開く", onSettingsClicked))
    }

    private fun statusLine(label: String): TextView {
        return TextView(context).apply {
            text = label
            textSize = 14f
            setTextColor(Color.argb(225, 255, 255, 255))
            setPadding(0, dp(4), 0, dp(2))
        }
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

    private fun formatRemaining(remainingMs: Long): String {
        if (remainingMs <= 0L) return "今"
        val minutes = ((remainingMs + 59_999L) / 60_000L).coerceAtLeast(1L)
        return "${minutes}分"
    }

    private fun matchWrapParams(): LayoutParams {
        return LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }
}

package com.kazumaproject.petagent.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.kazumaproject.petagent.llm.LocalModelMetadata
import com.kazumaproject.petagent.llm.LocalModelState
import kotlin.math.roundToInt

class PetConversationBubbleView(context: Context) : LinearLayout(context) {
    private var inputView: EditText? = null

    init {
        orientation = VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(Color.rgb(28, 34, 40))
            setStroke(dp(1), Color.argb(60, 255, 255, 255))
        }
        elevation = dp(8).toFloat()
    }

    fun showModelSetup(
        metadata: LocalModelMetadata,
        state: LocalModelState,
        onDownloadWifiClicked: () -> Unit,
        onDownloadMobileClicked: () -> Unit,
        onCloseClicked: () -> Unit,
    ) {
        inputView = null
        removeAllViews()
        addView(title("Talk"))
        addView(bodyText("モデルをダウンロードしてローカルで会話しますか？"))
        addView(bodyText(modelStatusText(metadata, state)))

        if (state is LocalModelState.Downloading) {
            addView(actionButton("閉じる", onCloseClicked), matchWrapParams())
            return
        }

        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
        }
        row.addView(actionButton("Wi-Fiでダウンロード", onDownloadWifiClicked), weightedButtonParams())
        row.addView(actionButton("モバイルでも許可", onDownloadMobileClicked), weightedButtonParams())
        addView(row, matchWrapParams())
        addView(actionButton("閉じる", onCloseClicked), matchWrapParams())
    }

    fun showInput(
        onSendClicked: (String) -> Unit,
        onCloseClicked: () -> Unit,
    ) {
        removeAllViews()
        addView(title("Talk"))

        val editText = EditText(context).apply {
            hint = "メッセージ"
            textSize = 14f
            minLines = 1
            maxLines = 3
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_SEND
            setSingleLine(false)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendFromInput(this, onSendClicked)
                    true
                } else {
                    false
                }
            }
        }
        inputView = editText
        addView(editText, matchWrapParams())

        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
        }
        row.addView(actionButton("送信") { sendFromInput(editText, onSendClicked) }, weightedButtonParams())
        row.addView(actionButton("閉じる", onCloseClicked), weightedButtonParams())
        addView(row, matchWrapParams())
    }

    fun showResponse(
        text: String,
        isWorking: Boolean,
        isError: Boolean,
        onNewMessageClicked: () -> Unit,
        onCloseClicked: () -> Unit,
    ) {
        inputView = null
        removeAllViews()
        addView(title(if (isError) "Error" else "Talk"))
        addView(
            bodyText(
                text.ifBlank {
                    if (isWorking) "考え中..." else "..."
                },
                color = if (isError) Color.rgb(255, 205, 210) else Color.WHITE,
            ),
        )

        if (!isWorking) {
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
            }
            row.addView(actionButton("もう一度", onNewMessageClicked), weightedButtonParams())
            row.addView(actionButton("閉じる", onCloseClicked), weightedButtonParams())
            addView(row, matchWrapParams())
        }
    }

    fun focusInput() {
        inputView?.requestFocus()
    }

    private fun sendFromInput(editText: EditText, onSendClicked: (String) -> Unit) {
        val message = editText.text?.toString()?.trim().orEmpty()
        if (message.isBlank()) return
        editText.setText("")
        onSendClicked(message)
    }

    private fun modelStatusText(metadata: LocalModelMetadata, state: LocalModelState): String {
        return when (state) {
            LocalModelState.NotDownloaded -> metadata.displayName
            is LocalModelState.Downloading -> "${metadata.displayName}: ${state.progress}%"
            is LocalModelState.Downloaded -> "ダウンロード済み"
            LocalModelState.Loading -> "読み込み中..."
            LocalModelState.Ready -> "準備できました"
            is LocalModelState.Failed -> state.message
        }
    }

    private fun title(text: String): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
    }

    private fun bodyText(text: String, color: Int = Color.argb(230, 255, 255, 255)): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 14f
            setTextColor(color)
            setPadding(0, dp(6), 0, dp(6))
        }
    }

    private fun actionButton(label: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener { onClick() }
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

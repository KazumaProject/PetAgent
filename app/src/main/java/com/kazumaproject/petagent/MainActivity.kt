package com.kazumaproject.petagent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var overlayStatus: TextView
    private lateinit var notificationStatus: TextView
    private lateinit var overlayButton: Button
    private lateinit var notificationButton: Button
    private lateinit var startButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.app_name)
        setContentView(buildContentView())
    }

    override fun onResume() {
        super.onResume()
        updatePermissionState()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) {
            updatePermissionState()
        }
    }

    private fun buildContentView(): View {
        val scrollView = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(28), dp(24), dp(28))
        }

        val title = TextView(this).apply {
            text = "African Scops Owl"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "Floating AI pet MVP"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(22))
        }

        overlayStatus = statusText()
        notificationStatus = statusText()

        overlayButton = actionButton("Open Overlay Settings") {
            openOverlaySettings()
        }
        notificationButton = actionButton("Allow Notifications") {
            requestNotificationPermission()
        }
        startButton = actionButton("Start Owl") {
            startPetIfReady()
        }
        val stopButton = actionButton("Stop Owl") {
            PetForegroundService.stop(this)
        }

        root.addView(title, matchWrapParams())
        root.addView(subtitle, matchWrapParams())
        root.addView(overlayStatus, matchWrapParams())
        root.addView(notificationStatus, matchWrapParams())
        root.addView(overlayButton, buttonParams())
        root.addView(notificationButton, buttonParams())
        root.addView(startButton, buttonParams())
        root.addView(stopButton, buttonParams())
        scrollView.addView(root)
        return scrollView
    }

    private fun startPetIfReady() {
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings()
            return
        }
        if (needsNotificationPermission()) {
            requestNotificationPermission()
            return
        }
        PetForegroundService.start(this)
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        startActivity(intent)
    }

    private fun requestNotificationPermission() {
        if (!needsNotificationPermission()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private fun updatePermissionState() {
        val overlayGranted = Settings.canDrawOverlays(this)
        val notificationGranted = !needsNotificationPermission()

        overlayStatus.text = "Overlay: ${if (overlayGranted) "granted" else "required"}"
        notificationStatus.text = "Notifications: ${if (notificationGranted) "granted" else "required"}"
        overlayButton.isEnabled = !overlayGranted
        notificationButton.visibility = if (notificationGranted) View.GONE else View.VISIBLE
        startButton.isEnabled = overlayGranted
    }

    private fun needsNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    }

    private fun statusText(): TextView {
        return TextView(this).apply {
            textSize = 16f
            setPadding(0, dp(8), 0, dp(8))
        }
    }

    private fun actionButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
        }
    }

    private fun matchWrapParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun buttonParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(10)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }

    private companion object {
        const val REQUEST_NOTIFICATIONS = 201
    }
}

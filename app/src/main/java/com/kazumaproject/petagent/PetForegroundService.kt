package com.kazumaproject.petagent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kazumaproject.petagent.agent.StubAgentCore
import com.kazumaproject.petagent.overlay.PetOverlayController
import com.kazumaproject.petagent.petpack.PetPackLoader
import com.kazumaproject.petagent.runtime.PetRuntime

class PetForegroundService : Service(), PetOverlayController.Listener {
    private var overlayController: PetOverlayController? = null
    private var runtime: PetRuntime? = null
    private var agentCore: StubAgentCore? = null
    private var isMinimized = false

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_STOP -> {
                stopPetAndService()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PEEK -> {
                overlayController?.toggleMinimized()
                updateNotification()
                return START_STICKY
            }
            ACTION_START -> startPet()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        clearPetRuntime()
        super.onDestroy()
    }

    override fun onPetTapped() {
        runtime?.onPetTapped()
    }

    override fun onDragStarted() {
        runtime?.onDragStarted()
    }

    override fun onDragEnded() {
        runtime?.onDragEnded()
    }

    override fun onMinimizedChanged(minimized: Boolean) {
        isMinimized = minimized
        runtime?.onMinimizedChanged(minimized)
        updateNotification()
    }

    private fun startPet() {
        promoteToForeground()
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission is missing; stopping pet service.")
            stopPetAndService()
            return
        }

        if (runtime != null) return

        try {
            val petPack = PetPackLoader(this).load()
            val overlay = PetOverlayController(this, petPack, this)
            overlay.show()
            val view = overlay.view ?: error("Overlay view was not created.")
            val agent = StubAgentCore()
            val petRuntime = PetRuntime(view, agent)

            overlayController = overlay
            agentCore = agent
            runtime = petRuntime
            petRuntime.start()
        } catch (error: Throwable) {
            Log.e(TAG, "Unable to start pet overlay.", error)
            stopPetAndService()
        }
    }

    private fun stopPetAndService() {
        clearPetRuntime()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun clearPetRuntime() {
        runtime?.stop()
        runtime = null
        agentCore?.unload()
        agentCore = null
        overlayController?.remove()
        overlayController = null
        isMinimized = false
    }

    private fun promoteToForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, PetForegroundService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this,
            REQUEST_STOP,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val peekIntent = Intent(this, PetForegroundService::class.java).setAction(ACTION_TOGGLE_PEEK)
        val peekPendingIntent = PendingIntent.getService(
            this,
            REQUEST_TOGGLE_PEEK,
            peekIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val peekLabel = if (isMinimized) "Restore" else "Peek"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("African Scops Owl is awake")
            .setContentText("Drag, tap, or long-press the tiny owl companion.")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_view, peekLabel, peekPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Floating pet",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps the African scops owl overlay running."
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "PetForegroundService"
        private const val CHANNEL_ID = "floating_pet"
        private const val NOTIFICATION_ID = 4127
        private const val REQUEST_STOP = 1001
        private const val REQUEST_TOGGLE_PEEK = 1002
        private const val ACTION_START = "com.kazumaproject.petagent.action.START"
        private const val ACTION_STOP = "com.kazumaproject.petagent.action.STOP"
        private const val ACTION_TOGGLE_PEEK = "com.kazumaproject.petagent.action.TOGGLE_PEEK"

        fun start(context: Context) {
            val intent = Intent(context, PetForegroundService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, PetForegroundService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}

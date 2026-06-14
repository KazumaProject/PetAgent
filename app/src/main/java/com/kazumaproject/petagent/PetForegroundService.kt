package com.kazumaproject.petagent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kazumaproject.petagent.behavior.PetBehaviorSettingsRepository
import com.kazumaproject.petagent.behavior.PetBrain
import com.kazumaproject.petagent.behavior.SpeciesBehaviorProfile
import com.kazumaproject.petagent.breakreminder.BreakReminderEngine
import com.kazumaproject.petagent.breakreminder.BreakReminderRepository
import com.kazumaproject.petagent.breakreminder.BreakReminderSettingsRepository
import com.kazumaproject.petagent.data.PetAgentDatabase
import com.kazumaproject.petagent.data.behavior.PetBehaviorRepository
import com.kazumaproject.petagent.data.memory.PetMemoryRepository
import com.kazumaproject.petagent.motion.PetMotionController
import com.kazumaproject.petagent.motion.PetMotionPlanner
import com.kazumaproject.petagent.overlay.PetBreakReminderBubbleController
import com.kazumaproject.petagent.overlay.PetBreakStatusPanelController
import com.kazumaproject.petagent.overlay.PetOverlayController
import com.kazumaproject.petagent.petpack.PetCatalogLoader
import com.kazumaproject.petagent.petpack.PetPack
import com.kazumaproject.petagent.petpack.PetPackLoader
import com.kazumaproject.petagent.runtime.PetRuntime
import com.kazumaproject.petagent.runtime.PetUiRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PetForegroundService : Service(), PetOverlayController.Listener {
    private var overlayController: PetOverlayController? = null
    private var breakReminderBubbleController: PetBreakReminderBubbleController? = null
    private var breakStatusPanelController: PetBreakStatusPanelController? = null
    private var runtime: PetRuntime? = null
    private var serviceScope: CoroutineScope? = null
    private var currentPetPack: PetPack? = null
    private var latestPetBounds: Rect? = null
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
            ACTION_UPDATE_SETTINGS -> {
                return if (updatePetSettings(startId)) START_STICKY else START_NOT_STICKY
            }
            ACTION_TEST_REMINDER -> {
                runtime?.showTestBreakReminder() ?: stopSelf(startId)
                return if (runtime != null) START_STICKY else START_NOT_STICKY
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

    override fun onPetTapped(petBounds: Rect) {
        latestPetBounds = petBounds
        runtime?.onPetTapped()
    }

    override fun onDragStarted(petBounds: Rect) {
        latestPetBounds = petBounds
        breakReminderBubbleController?.removeAll()
        breakStatusPanelController?.removeAll()
        runtime?.onDragStarted()
    }

    override fun onPetMoved(petBounds: Rect) {
        latestPetBounds = petBounds
    }

    override fun onDragEnded(petBounds: Rect) {
        latestPetBounds = petBounds
        runtime?.onDragEnded()
    }

    override fun onMinimizedChanged(minimized: Boolean, petBounds: Rect) {
        isMinimized = minimized
        latestPetBounds = petBounds
        if (minimized) {
            breakReminderBubbleController?.removeAll()
            breakStatusPanelController?.removeAll()
        }
        runtime?.onMinimizedChanged(minimized)
        updateNotification()
    }

    private fun startPet(selectedPetSettings: SelectedPetSettings? = null) {
        promoteToForeground()
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission is missing; stopping pet service.")
            stopPetAndService()
            return
        }

        if (runtime != null) return

        try {
            val settings = selectedPetSettings ?: loadSelectedPetSettings()
            val overlay = PetOverlayController(this, settings.petPack, this, settings.sizeDp)
            overlay.show()
            val view = overlay.view ?: error("Overlay view was not created.")
            val windowManager = getSystemService(WindowManager::class.java)
            val database = PetAgentDatabase.getInstance(this)
            val breakReminderRepository = BreakReminderRepository(database.breakReminderDao())
            val petMemoryRepository = PetMemoryRepository(database.petMemoryDao())
            val petBehaviorRepository = PetBehaviorRepository(database.petBehaviorDao())
            val breakReminderSettingsRepository = BreakReminderSettingsRepository(this)
            val petBehaviorSettingsRepository = PetBehaviorSettingsRepository(this)
            val profile = SpeciesBehaviorProfile.fromManifest(
                manifest = settings.petPack.manifest,
                availableAnimationKeys = settings.petPack.animations.keys,
            )
            val motionPlanner = PetMotionPlanner(settings.petPack)
            val breakReminderEngine = BreakReminderEngine(breakReminderRepository)
            val brain = PetBrain(
                behaviorProfile = profile,
                motionPlanner = motionPlanner,
                breakReminderEngine = breakReminderEngine,
                breakReminderRepository = breakReminderRepository,
                petMemoryRepository = petMemoryRepository,
                petBehaviorRepository = petBehaviorRepository,
            )
            val motionController = PetMotionController(overlay)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            val bubbleController = PetBreakReminderBubbleController(
                context = this,
                windowManager = windowManager,
                onAcceptClicked = { runtime?.acceptBreak() },
                onSnoozeClicked = { runtime?.snoozeBreak() },
                onDismissTodayClicked = { runtime?.dismissBreakToday() },
            )
            val statusPanelController = PetBreakStatusPanelController(
                context = this,
                windowManager = windowManager,
                onBreakNowClicked = { runtime?.acceptBreak() },
                onSnoozeClicked = { runtime?.snoozeBreak() },
                onSettingsClicked = { openSettingsActivity() },
            )
            val petRuntime = PetRuntime(
                petView = view,
                petPack = settings.petPack,
                behaviorProfile = profile,
                petBrain = brain,
                motionPlanner = motionPlanner,
                breakReminderEngine = breakReminderEngine,
                breakReminderRepository = breakReminderRepository,
                breakReminderSettingsRepository = breakReminderSettingsRepository,
                petMemoryRepository = petMemoryRepository,
                petBehaviorRepository = petBehaviorRepository,
                petBehaviorSettingsRepository = petBehaviorSettingsRepository,
                petMotionController = motionController,
                coroutineScope = scope,
                onUiRequest = { request -> handleUiRequest(request) },
            )

            overlayController = overlay
            breakReminderBubbleController = bubbleController
            breakStatusPanelController = statusPanelController
            serviceScope = scope
            currentPetPack = settings.petPack
            latestPetBounds = overlay.currentPetBounds()
            runtime = petRuntime
            petRuntime.start()
        } catch (error: Throwable) {
            Log.e(TAG, "Unable to start pet overlay.", error)
            stopPetAndService()
        }
    }

    private fun handleUiRequest(request: PetUiRequest) {
        when (request) {
            PetUiRequest.OpenBreakStatus -> showBreakStatusPanel()
            is PetUiRequest.ShowBreakReminder -> {
                if (isMinimized) return
                val bounds = latestPetBounds ?: overlayController?.currentPetBounds() ?: return
                breakStatusPanelController?.removeAll()
                breakReminderBubbleController?.showNearPet(
                    petBounds = bounds,
                    message = request.message,
                    activeMinutes = request.activeMinutes,
                    tone = request.tone,
                )
            }
        }
    }

    private fun showBreakStatusPanel() {
        if (isMinimized) return
        val petRuntime = runtime ?: return
        val bounds = latestPetBounds ?: overlayController?.currentPetBounds() ?: return
        serviceScope?.launch {
            val status = petRuntime.currentBreakStatus()
            breakReminderBubbleController?.removeAll()
            breakStatusPanelController?.showNearPet(bounds, status)
        }
    }

    private fun openSettingsActivity() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun updatePetSettings(startId: Int): Boolean {
        val overlay = overlayController
        if (overlay == null || runtime == null) {
            stopSelf(startId)
            return false
        }

        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission is missing; stopping pet service.")
            stopPetAndService()
            return false
        }

        try {
            val selectedSettings = loadSelectedPetSettings()
            if (overlay.getPetId() != selectedSettings.petPack.manifest.petId) {
                clearPetRuntime()
                startPet(selectedSettings)
            } else {
                currentPetPack = selectedSettings.petPack
                overlay.updateSize(selectedSettings.sizeDp)
                latestPetBounds = overlay.currentPetBounds()
                updateNotification()
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Unable to update pet settings.", error)
        }
        return true
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
        overlayController?.remove()
        overlayController = null
        breakReminderBubbleController?.removeAll()
        breakReminderBubbleController = null
        breakStatusPanelController?.removeAll()
        breakStatusPanelController = null
        serviceScope?.cancel()
        serviceScope = null
        currentPetPack = null
        latestPetBounds = null
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
            .setContentTitle("Floating pet is awake")
            .setContentText("Your pet can move around and suggest healthy breaks.")
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
            description = "Keeps the floating pet overlay running."
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun loadSelectedPetSettings(): SelectedPetSettings {
        val prefs = PetPreferences.prefs(this)
        val selectedPetId = prefs.getString(
            PetPreferences.KEY_SELECTED_PET_ID,
            PetPreferences.DEFAULT_PET_ID,
        ) ?: PetPreferences.DEFAULT_PET_ID
        val selectedBasePath = prefs.getString(
            PetPreferences.KEY_SELECTED_PET_BASE_PATH,
            PetPreferences.DEFAULT_PET_BASE_PATH,
        ) ?: PetPreferences.DEFAULT_PET_BASE_PATH
        val catalog = PetCatalogLoader(this).load()
        val entry = catalog.pets.firstOrNull { it.petId == selectedPetId }
            ?: catalog.pets.firstOrNull { it.basePath == selectedBasePath }
            ?: catalog.pets.firstOrNull { it.petId == PetPreferences.DEFAULT_PET_ID }
            ?: catalog.pets.first()
        val petPack = PetPackLoader(this, entry.basePath).load()
        val sizeDp = prefs.getInt(
            PetPreferences.petSizeKey(petPack.manifest.petId),
            petPack.manifest.defaultSizeDp,
        ).coerceIn(petPack.manifest.minSizeDp, petPack.manifest.maxSizeDp)

        return SelectedPetSettings(
            petPack = petPack,
            sizeDp = sizeDp,
        )
    }

    private data class SelectedPetSettings(
        val petPack: PetPack,
        val sizeDp: Int,
    )

    companion object {
        private const val TAG = "PetForegroundService"
        private const val CHANNEL_ID = "floating_pet"
        private const val NOTIFICATION_ID = 4127
        private const val REQUEST_STOP = 1001
        private const val REQUEST_TOGGLE_PEEK = 1002
        private const val ACTION_START = "com.kazumaproject.petagent.action.START"
        private const val ACTION_STOP = "com.kazumaproject.petagent.action.STOP"
        private const val ACTION_TOGGLE_PEEK = "com.kazumaproject.petagent.action.TOGGLE_PEEK"
        private const val ACTION_UPDATE_SETTINGS = "com.kazumaproject.petagent.action.UPDATE_SETTINGS"
        private const val ACTION_TEST_REMINDER = "com.kazumaproject.petagent.action.TEST_REMINDER"

        fun start(context: Context) {
            val intent = Intent(context, PetForegroundService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, PetForegroundService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }

        fun updateSettings(context: Context) {
            val intent = Intent(context, PetForegroundService::class.java)
                .setAction(ACTION_UPDATE_SETTINGS)
            context.startService(intent)
        }

        fun showTestReminder(context: Context) {
            val intent = Intent(context, PetForegroundService::class.java)
                .setAction(ACTION_TEST_REMINDER)
            context.startService(intent)
        }
    }
}

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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.kazumaproject.petagent.petpack.PetCatalogEntry
import com.kazumaproject.petagent.petpack.PetCatalogLoader
import com.kazumaproject.petagent.petpack.PetPack
import com.kazumaproject.petagent.petpack.PetPackLoader
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var overlayStatus: TextView
    private lateinit var notificationStatus: TextView
    private lateinit var overlayButton: Button
    private lateinit var notificationButton: Button
    private lateinit var startButton: Button
    private lateinit var petSelector: Spinner
    private lateinit var petSizeLabel: TextView
    private lateinit var petSizeSeekBar: SeekBar
    private val preferences by lazy { PetPreferences.prefs(this) }
    private var catalogPets: List<PetCatalogEntry> = emptyList()
    private var selectedPetPack: PetPack? = null
    private var suppressSizeCallback = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        catalogPets = PetCatalogLoader(this).load().pets
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
            text = "Floating Pet Agent"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "自律行動と休憩リマインダー"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(22))
        }

        petSelector = buildPetSelector()
        petSizeLabel = statusText()
        petSizeSeekBar = buildSizeSeekBar()
        overlayStatus = statusText()
        notificationStatus = statusText()
        overlayButton = actionButton("Open Overlay Settings") { openOverlaySettings() }
        notificationButton = actionButton("Allow Notifications") { requestNotificationPermission() }
        startButton = actionButton("Start Pet") { startPetIfReady() }
        val stopButton = actionButton("Stop Pet") { PetForegroundService.stop(this) }

        root.addView(title, matchWrapParams())
        root.addView(subtitle, matchWrapParams())

        root.addView(sectionLabel("Pet"), matchWrapParams())
        root.addView(petSelector, spinnerParams())
        root.addView(petSizeLabel, matchWrapParams())
        root.addView(petSizeSeekBar, matchWrapParams())

        root.addView(sectionLabel("Break Reminder"), matchWrapParams())
        root.addView(
            switchRow(
                label = "休憩リマインダー",
                key = PetPreferences.KEY_BREAK_REMINDER_ENABLED,
                defaultValue = PetPreferences.DEFAULT_BREAK_REMINDER_ENABLED,
            ),
            matchWrapParams(),
        )
        root.addView(settingLabel("提案する間隔"), matchWrapParams())
        root.addView(
            intSpinner(
                values = listOf(15, 25, 30, 45, 60),
                current = preferences.getInt(
                    PetPreferences.KEY_BREAK_REMINDER_INTERVAL_MINUTES,
                    PetPreferences.DEFAULT_BREAK_REMINDER_INTERVAL_MINUTES,
                ),
                suffix = "分",
            ) { value ->
                saveIntSetting(PetPreferences.KEY_BREAK_REMINDER_INTERVAL_MINUTES, value)
            },
            spinnerParams(),
        )
        root.addView(settingLabel("あとで通知"), matchWrapParams())
        root.addView(
            intSpinner(
                values = listOf(5, 10, 15, 30),
                current = preferences.getInt(
                    PetPreferences.KEY_BREAK_REMINDER_SNOOZE_MINUTES,
                    PetPreferences.DEFAULT_BREAK_REMINDER_SNOOZE_MINUTES,
                ),
                suffix = "分",
            ) { value ->
                saveIntSetting(PetPreferences.KEY_BREAK_REMINDER_SNOOZE_MINUTES, value)
            },
            spinnerParams(),
        )
        root.addView(
            switchRow(
                label = "静かにする時間帯",
                key = PetPreferences.KEY_BREAK_REMINDER_QUIET_HOURS_ENABLED,
                defaultValue = PetPreferences.DEFAULT_BREAK_REMINDER_QUIET_HOURS_ENABLED,
            ),
            matchWrapParams(),
        )
        root.addView(settingLabel("静かにする開始時刻"), matchWrapParams())
        root.addView(
            intSpinner(
                values = (0..23).toList(),
                current = preferences.getInt(
                    PetPreferences.KEY_BREAK_REMINDER_QUIET_START_HOUR,
                    PetPreferences.DEFAULT_BREAK_REMINDER_QUIET_START_HOUR,
                ),
                suffix = "時",
            ) { value ->
                saveIntSetting(PetPreferences.KEY_BREAK_REMINDER_QUIET_START_HOUR, value)
            },
            spinnerParams(),
        )
        root.addView(settingLabel("静かにする終了時刻"), matchWrapParams())
        root.addView(
            intSpinner(
                values = (0..23).toList(),
                current = preferences.getInt(
                    PetPreferences.KEY_BREAK_REMINDER_QUIET_END_HOUR,
                    PetPreferences.DEFAULT_BREAK_REMINDER_QUIET_END_HOUR,
                ),
                suffix = "時",
            ) { value ->
                saveIntSetting(PetPreferences.KEY_BREAK_REMINDER_QUIET_END_HOUR, value)
            },
            spinnerParams(),
        )
        root.addView(actionButton("テスト表示") { PetForegroundService.showTestReminder(this) }, buttonParams())

        root.addView(sectionLabel("Pet Behavior"), matchWrapParams())
        root.addView(
            switchRow(
                label = "自律行動",
                key = PetPreferences.KEY_AUTONOMOUS_BEHAVIOR_ENABLED,
                defaultValue = PetPreferences.DEFAULT_AUTONOMOUS_BEHAVIOR_ENABLED,
            ),
            matchWrapParams(),
        )
        root.addView(
            switchRow(
                label = "自律移動",
                key = PetPreferences.KEY_AUTONOMOUS_MOVE_ENABLED,
                defaultValue = PetPreferences.DEFAULT_AUTONOMOUS_MOVE_ENABLED,
            ),
            matchWrapParams(),
        )
        root.addView(settingLabel("動きの頻度"), matchWrapParams())
        root.addView(
            stringSpinner(
                entries = listOf(
                    "low" to "少ない",
                    "normal" to "普通",
                    "high" to "多い",
                ),
                current = preferences.getString(
                    PetPreferences.KEY_AUTONOMOUS_BEHAVIOR_FREQUENCY,
                    PetPreferences.DEFAULT_AUTONOMOUS_BEHAVIOR_FREQUENCY,
                ) ?: PetPreferences.DEFAULT_AUTONOMOUS_BEHAVIOR_FREQUENCY,
            ) { value ->
                saveStringSetting(PetPreferences.KEY_AUTONOMOUS_BEHAVIOR_FREQUENCY, value)
            },
            spinnerParams(),
        )
        root.addView(
            switchRow(
                label = "休憩前に近づく",
                key = PetPreferences.KEY_APPROACH_BEFORE_BREAK_ENABLED,
                defaultValue = PetPreferences.DEFAULT_APPROACH_BEFORE_BREAK_ENABLED,
            ),
            matchWrapParams(),
        )

        root.addView(sectionLabel("Permission / Service"), matchWrapParams())
        root.addView(overlayStatus, matchWrapParams())
        root.addView(notificationStatus, matchWrapParams())
        root.addView(overlayButton, buttonParams())
        root.addView(notificationButton, buttonParams())
        root.addView(startButton, buttonParams())
        root.addView(stopButton, buttonParams())
        scrollView.addView(root)

        bindSelectedPet(catalogPets[selectedPetIndex()], notifyService = false, saveSelection = false)
        attachPetSelectorListener()
        return scrollView
    }

    private fun buildPetSelector(): Spinner {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            catalogPets.map { it.displayName },
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        return Spinner(this).apply {
            this.adapter = adapter
            setSelection(selectedPetIndex(), false)
        }
    }

    private fun attachPetSelectorListener() {
        petSelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long,
            ) {
                val entry = catalogPets[position]
                if (isCurrentPersistedSelection(entry) && selectedPetPack?.manifest?.petId == entry.petId) {
                    return
                }

                bindSelectedPet(entry, notifyService = true, saveSelection = true)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun isCurrentPersistedSelection(entry: PetCatalogEntry): Boolean {
        val selectedPetId = preferences.getString(
            PetPreferences.KEY_SELECTED_PET_ID,
            PetPreferences.DEFAULT_PET_ID,
        ) ?: PetPreferences.DEFAULT_PET_ID
        val selectedBasePath = preferences.getString(
            PetPreferences.KEY_SELECTED_PET_BASE_PATH,
            PetPreferences.DEFAULT_PET_BASE_PATH,
        ) ?: PetPreferences.DEFAULT_PET_BASE_PATH

        return entry.petId == selectedPetId && entry.basePath == selectedBasePath
    }

    private fun buildSizeSeekBar(): SeekBar {
        return SeekBar(this).apply {
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val petPack = selectedPetPack ?: return
                    val sizeDp = petPack.manifest.minSizeDp + progress
                    petSizeLabel.text = "Pet size: $sizeDp dp"
                    if (fromUser && !suppressSizeCallback) {
                        savePetSize(petPack, sizeDp)
                        PetForegroundService.updateSettings(this@MainActivity)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
    }

    private fun bindSelectedPet(
        entry: PetCatalogEntry,
        notifyService: Boolean,
        saveSelection: Boolean,
    ) {
        val petPack = PetPackLoader(this, entry.basePath).load()
        selectedPetPack = petPack

        if (saveSelection) {
            preferences.edit()
                .putString(PetPreferences.KEY_SELECTED_PET_ID, entry.petId)
                .putString(PetPreferences.KEY_SELECTED_PET_BASE_PATH, entry.basePath)
                .apply()
        }

        bindPetSize(petPack)

        if (notifyService) {
            PetForegroundService.updateSettings(this)
        }
    }

    private fun bindPetSize(petPack: PetPack) {
        val manifest = petPack.manifest
        val savedSizeDp = savedPetSize(petPack)
        suppressSizeCallback = true
        petSizeSeekBar.max = (manifest.maxSizeDp - manifest.minSizeDp).coerceAtLeast(0)
        petSizeSeekBar.progress = savedSizeDp - manifest.minSizeDp
        petSizeSeekBar.isEnabled = manifest.maxSizeDp > manifest.minSizeDp
        petSizeLabel.text = "Pet size: $savedSizeDp dp"
        suppressSizeCallback = false
    }

    private fun switchRow(
        label: String,
        key: String,
        defaultValue: Boolean,
    ): View {
        return SwitchCompat(this).apply {
            text = label
            textSize = 16f
            isChecked = preferences.getBoolean(key, defaultValue)
            setPadding(0, dp(6), 0, dp(6))
            setOnCheckedChangeListener { _, isChecked ->
                preferences.edit().putBoolean(key, isChecked).apply()
                PetForegroundService.updateSettings(this@MainActivity)
            }
        }
    }

    private fun intSpinner(
        values: List<Int>,
        current: Int,
        suffix: String,
        onSelected: (Int) -> Unit,
    ): Spinner {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            values.map { "$it$suffix" },
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        return Spinner(this).apply {
            this.adapter = adapter
            setSelection(values.indexOf(current).takeIf { it >= 0 } ?: 0, false)
            var initialized = false
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (!initialized) {
                        initialized = true
                        return
                    }
                    onSelected(values[position])
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
    }

    private fun stringSpinner(
        entries: List<Pair<String, String>>,
        current: String,
        onSelected: (String) -> Unit,
    ): Spinner {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            entries.map { it.second },
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        return Spinner(this).apply {
            this.adapter = adapter
            setSelection(entries.indexOfFirst { it.first == current }.takeIf { it >= 0 } ?: 0, false)
            var initialized = false
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (!initialized) {
                        initialized = true
                        return
                    }
                    onSelected(entries[position].first)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
    }

    private fun saveIntSetting(key: String, value: Int) {
        preferences.edit().putInt(key, value).apply()
        PetForegroundService.updateSettings(this)
    }

    private fun saveStringSetting(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
        PetForegroundService.updateSettings(this)
    }

    private fun selectedPetIndex(): Int {
        val selectedPetId = preferences.getString(
            PetPreferences.KEY_SELECTED_PET_ID,
            PetPreferences.DEFAULT_PET_ID,
        ) ?: PetPreferences.DEFAULT_PET_ID
        val selectedBasePath = preferences.getString(
            PetPreferences.KEY_SELECTED_PET_BASE_PATH,
            PetPreferences.DEFAULT_PET_BASE_PATH,
        ) ?: PetPreferences.DEFAULT_PET_BASE_PATH

        return catalogPets.indexOfFirst { it.petId == selectedPetId }
            .takeIf { it >= 0 }
            ?: catalogPets.indexOfFirst { it.basePath == selectedBasePath }
                .takeIf { it >= 0 }
            ?: catalogPets.indexOfFirst { it.petId == PetPreferences.DEFAULT_PET_ID }
                .takeIf { it >= 0 }
            ?: 0
    }

    private fun savedPetSize(petPack: PetPack): Int {
        val manifest = petPack.manifest
        return preferences.getInt(
            PetPreferences.petSizeKey(manifest.petId),
            manifest.defaultSizeDp,
        ).coerceIn(manifest.minSizeDp, manifest.maxSizeDp)
    }

    private fun savePetSize(petPack: PetPack, sizeDp: Int) {
        val manifest = petPack.manifest
        preferences.edit()
            .putInt(
                PetPreferences.petSizeKey(manifest.petId),
                sizeDp.coerceIn(manifest.minSizeDp, manifest.maxSizeDp),
            )
            .apply()
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

    private fun sectionLabel(label: String): TextView {
        return TextView(this).apply {
            text = label
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(18), 0, dp(4))
        }
    }

    private fun settingLabel(label: String): TextView {
        return TextView(this).apply {
            text = label
            textSize = 15f
            setPadding(0, dp(8), 0, dp(2))
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

    private fun spinnerParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            bottomMargin = dp(8)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }

    private companion object {
        const val REQUEST_NOTIFICATIONS = 201
    }
}

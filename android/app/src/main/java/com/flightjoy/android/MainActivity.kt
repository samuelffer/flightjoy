package com.flightjoy.android

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.flightjoy.android.config.AppPreferences
import com.flightjoy.android.config.FlightClientConfig
import com.flightjoy.android.config.PhoneButtonConfig
import com.flightjoy.android.control.ControlState
import com.flightjoy.android.logging.AppLogger
import com.flightjoy.android.network.ClientConnectionState
import com.flightjoy.android.network.ClientStatusSnapshot
import com.flightjoy.android.network.UdpTelemetryClient
import com.flightjoy.android.sensor.NormalizedOrientation
import com.flightjoy.android.sensor.SensorController
import com.flightjoy.android.ui.ThickSliderView
import com.flightjoy.android.ui.VirtualStickView
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences
    private lateinit var config: FlightClientConfig
    private val logger = AppLogger()
    private val controlState = ControlState()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var sensorController: SensorController
    private lateinit var telemetryClient: UdpTelemetryClient

    private var isReverseThrottleMode: Boolean = false
    private var isEditControlsMode: Boolean = false

    // Cockpit Views
    private lateinit var btnMenu: Button
    private lateinit var btnCalibrate: Button
    private lateinit var txtConnectionStatus: TextView
    private lateinit var virtualStickView: VirtualStickView
    private lateinit var txtOrientationValues: TextView

    private lateinit var txtThrottleTitle: TextView
    private lateinit var sliderThrottle: ThickSliderView
    private lateinit var txtThrottleValue: TextView
    private lateinit var btnReverseSwitch: AppCompatButton

    private lateinit var sliderBrake: ThickSliderView
    private lateinit var txtBrakeValue: TextView

    private lateinit var sliderRudder: ThickSliderView
    private lateinit var txtRudderValue: TextView
    private lateinit var btnRudderMode: Button

    // Edit Controls Mode Banner
    private lateinit var editModeBar: LinearLayout
    private lateinit var btnResetButtons: Button
    private lateinit var btnExitEditMode: Button

    private val cockpitButtons = mutableMapOf<Int, Button>()

    // 1. Menu Hub Overlay (3 Square Cards)
    private lateinit var hubOverlay: FrameLayout
    private lateinit var btnCloseHub: Button
    private lateinit var cardMenuConnection: LinearLayout
    private lateinit var cardMenuSettings: LinearLayout
    private lateinit var cardMenuEditControls: LinearLayout

    // 2. Portrait Connection Screen
    private lateinit var connectionOverlay: FrameLayout
    private lateinit var btnCloseConnection: Button
    private lateinit var txtConnectionOverlayStatus: TextView
    private lateinit var edtServerIp: EditText
    private lateinit var edtServerPort: EditText
    private lateinit var btnSearchPc: Button
    private lateinit var btnConnect: Button

    // 3. Portrait Settings Screen
    private lateinit var settingsOverlay: FrameLayout
    private lateinit var btnCloseSettings: Button
    private lateinit var spnMapPitch: Spinner
    private lateinit var spnMapRoll: Spinner
    private lateinit var spnMapRudder: Spinner
    private lateinit var spnMapThrottle: Spinner
    private lateinit var spnMapReverse: Spinner
    private lateinit var spnMapBrake: Spinner

    private lateinit var txtSensitivityLabel: TextView
    private lateinit var seekSensitivity: SeekBar
    private lateinit var txtDeadzoneLabel: TextView
    private lateinit var seekDeadzone: SeekBar
    private lateinit var txtMaxAngleLabel: TextView
    private lateinit var seekMaxAngle: SeekBar

    private lateinit var chkInvertPitch: CheckBox
    private lateinit var chkInvertRoll: CheckBox
    private lateinit var chkInvertYaw: CheckBox
    private lateinit var chkEnableYaw: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveFullscreen()
        setContentView(R.layout.activity_main)

        prefs = AppPreferences(this)
        config = prefs.loadConfig()
        val deviceId = prefs.getOrCreateDeviceId()

        bindViews()
        setupThickSlidersAndReverseSwitch()
        setupGyroCenterLock()
        setupFlightButtons()
        setupMenuHubAndPortraitScreens()

        sensorController = SensorController(
            context = this,
            controlState = controlState,
            logger = logger,
            onOrientationUpdated = { orientation ->
                runOnUiThread {
                    renderOrientation(orientation)
                }
            }
        ).apply {
            currentConfig = config
        }

        telemetryClient = UdpTelemetryClient(
            deviceId = deviceId,
            controlState = controlState,
            logger = logger,
            onStatusChanged = { snapshot ->
                runOnUiThread {
                    renderConnectionStatus(snapshot)
                }
            }
        ).apply {
            activeConfig = config
        }

        btnCalibrate.setOnClickListener {
            sensorController.calibrateCenter()
            Toast.makeText(this, "Gyroscope calibrated!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveFullscreen()
        sensorController.currentConfig = config
        sensorController.start()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableImmersiveFullscreen()
        }
    }

    override fun onPause() {
        super.onPause()
        sensorController.stop()
    }

    override fun onDestroy() {
        telemetryClient.disconnect(sendPacket = true)
        super.onDestroy()
    }

    private fun enableImmersiveFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun bindViews() {
        btnMenu = findViewById(R.id.btnMenu)
        btnCalibrate = findViewById(R.id.btnCalibrate)
        txtConnectionStatus = findViewById(R.id.txtConnectionStatus)
        virtualStickView = findViewById(R.id.virtualStickView)
        txtOrientationValues = findViewById(R.id.txtOrientationValues)

        txtThrottleTitle = findViewById(R.id.txtThrottleTitle)
        sliderThrottle = findViewById(R.id.sliderThrottle)
        txtThrottleValue = findViewById(R.id.txtThrottleValue)
        btnReverseSwitch = findViewById(R.id.btnReverseSwitch)

        sliderBrake = findViewById(R.id.sliderBrake)
        txtBrakeValue = findViewById(R.id.txtBrakeValue)

        sliderRudder = findViewById(R.id.sliderRudder)
        txtRudderValue = findViewById(R.id.txtRudderValue)
        btnRudderMode = findViewById(R.id.btnRudderMode)

        editModeBar = findViewById(R.id.editModeBar)
        btnResetButtons = findViewById(R.id.btnResetButtons)
        btnExitEditMode = findViewById(R.id.btnExitEditMode)

        cockpitButtons[1] = findViewById(R.id.btnB1)
        cockpitButtons[2] = findViewById(R.id.btnB2)
        cockpitButtons[3] = findViewById(R.id.btnB3)
        cockpitButtons[4] = findViewById(R.id.btnB4)
        cockpitButtons[5] = findViewById(R.id.btnB5)
        cockpitButtons[6] = findViewById(R.id.btnB6)
        cockpitButtons[7] = findViewById(R.id.btnB7)
        cockpitButtons[8] = findViewById(R.id.btnB8)

        hubOverlay = findViewById(R.id.hubOverlay)
        btnCloseHub = findViewById(R.id.btnCloseHub)
        cardMenuConnection = findViewById(R.id.cardMenuConnection)
        cardMenuSettings = findViewById(R.id.cardMenuSettings)
        cardMenuEditControls = findViewById(R.id.cardMenuEditControls)

        connectionOverlay = findViewById(R.id.connectionOverlay)
        btnCloseConnection = findViewById(R.id.btnCloseConnection)
        txtConnectionOverlayStatus = findViewById(R.id.txtConnectionOverlayStatus)
        edtServerIp = findViewById(R.id.edtServerIp)
        edtServerPort = findViewById(R.id.edtServerPort)
        btnSearchPc = findViewById(R.id.btnSearchPc)
        btnConnect = findViewById(R.id.btnConnect)

        settingsOverlay = findViewById(R.id.settingsOverlay)
        btnCloseSettings = findViewById(R.id.btnCloseSettings)
        spnMapPitch = findViewById(R.id.spnMapPitch)
        spnMapRoll = findViewById(R.id.spnMapRoll)
        spnMapRudder = findViewById(R.id.spnMapRudder)
        spnMapThrottle = findViewById(R.id.spnMapThrottle)
        spnMapReverse = findViewById(R.id.spnMapReverse)
        spnMapBrake = findViewById(R.id.spnMapBrake)

        txtSensitivityLabel = findViewById(R.id.txtSensitivityLabel)
        seekSensitivity = findViewById(R.id.seekSensitivity)
        txtDeadzoneLabel = findViewById(R.id.txtDeadzoneLabel)
        seekDeadzone = findViewById(R.id.seekDeadzone)
        txtMaxAngleLabel = findViewById(R.id.txtMaxAngleLabel)
        seekMaxAngle = findViewById(R.id.seekMaxAngle)

        chkInvertPitch = findViewById(R.id.chkInvertPitch)
        chkInvertRoll = findViewById(R.id.chkInvertRoll)
        chkInvertYaw = findViewById(R.id.chkInvertYaw)
        chkEnableYaw = findViewById(R.id.chkEnableYaw)
    }

    private fun setupThickSlidersAndReverseSwitch() {
        // Left Slider: Throttle / Reverse (Vertical, 1:1 tracking on the square handle)
        sliderThrottle.apply {
            isVertical = true
            isBipolarCenter = false
            isInvertedVertical = false
            returnToCenterOnRelease = false
            onValueChanged = { normalized ->
                val pct = (((normalized + 1.0f) * 0.5f) * 100.0f).roundToInt()
                if (isReverseThrottleMode) {
                    controlState.throttle = -1.0f
                    controlState.reverseThrottle = normalized
                    txtThrottleValue.text = "REV $pct%"
                } else {
                    controlState.reverseThrottle = -1.0f
                    controlState.throttle = normalized
                    txtThrottleValue.text = "$pct%"
                }
            }
            setNormalizedValueImmediate(-1.0f)
        }

        // Reverse Switch Button (top-to-bottom 0% -> 100% when active)
        updateReverseSwitchVisual()
        btnReverseSwitch.setOnClickListener {
            isReverseThrottleMode = !isReverseThrottleMode
            sliderThrottle.isInvertedVertical = isReverseThrottleMode
            controlState.throttle = -1.0f
            controlState.reverseThrottle = -1.0f
            sliderThrottle.setNormalizedValueImmediate(-1.0f)
            updateReverseSwitchVisual()
        }

        // Right Small Vertical Progressive Brake Slider (Starts at top 0%, pull down freely to 100%)
        sliderBrake.apply {
            isVertical = true
            isBipolarCenter = false
            isInvertedVertical = true
            returnToCenterOnRelease = false
            onValueChanged = { normalized ->
                controlState.brakeSlider = normalized
                val pct = (((normalized + 1.0f) * 0.5f) * 100.0f).roundToInt()
                txtBrakeValue.text = "$pct%"
            }
            setNormalizedValueImmediate(-1.0f)
        }

        // Bottom-Right Horizontal Rudder Slider
        sliderRudder.apply {
            isVertical = false
            isBipolarCenter = true
            returnToCenterOnRelease = config.rudderAutoCenter
            onValueChanged = { normalized ->
                controlState.rudderSlider = normalized
                val pct = (normalized * 100.0f).roundToInt()
                txtRudderValue.text = String.format(Locale.US, "%+d%%", pct)
            }
            setNormalizedValueImmediate(0.0f)
        }

        updateRudderModeButtonVisual()
        btnRudderMode.setOnClickListener {
            config.rudderAutoCenter = !config.rudderAutoCenter
            sliderRudder.returnToCenterOnRelease = config.rudderAutoCenter
            if (config.rudderAutoCenter) {
                sliderRudder.setNormalizedValueImmediate(0.0f)
            }
            prefs.saveConfig(config)
            updateRudderModeButtonVisual()
        }
    }

    private fun updateReverseSwitchVisual() {
        if (isReverseThrottleMode) {
            btnReverseSwitch.setBackgroundResource(R.drawable.bg_reverse_switch_on)
            btnReverseSwitch.text = "REV\nON"
            btnReverseSwitch.setTextColor(Color.parseColor("#0A0A0A"))
            txtThrottleTitle.text = "REVERSE"
            txtThrottleTitle.setTextColor(Color.parseColor("#FFFFFF"))
        } else {
            btnReverseSwitch.setBackgroundResource(R.drawable.bg_reverse_switch_off)
            btnReverseSwitch.text = "REV\nOFF"
            btnReverseSwitch.setTextColor(Color.parseColor("#9E9E9E"))
            txtThrottleTitle.text = "THROTTLE"
            txtThrottleTitle.setTextColor(Color.parseColor("#9E9E9E"))
        }
    }

    private fun updateRudderModeButtonVisual() {
        if (config.rudderAutoCenter) {
            btnRudderMode.text = "Auto-Center: ON"
            btnRudderMode.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E0E0E0"))
            btnRudderMode.setTextColor(Color.parseColor("#0A0A0A"))
        } else {
            btnRudderMode.text = "Mode: Free"
            btnRudderMode.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#262626"))
            btnRudderMode.setTextColor(Color.parseColor("#BDBDBD"))
        }
    }

    private fun setupGyroCenterLock() {
        virtualStickView.onCenterLockChanged = { locked ->
            sensorController.isGyroLocked = locked
            if (locked) {
                txtOrientationValues.text = "GYROSCOPE LOCKED (0%)"
                txtOrientationValues.setTextColor(Color.parseColor("#FFFFFF"))
            } else {
                txtOrientationValues.setTextColor(Color.parseColor("#9E9E9E"))
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFlightButtons() {
        refreshAllButtonLabelsAndColors()

        for ((slotIndex, btnView) in cockpitButtons) {
            btnView.setOnTouchListener { _, event ->
                val btnCfg = getButtonConfig(slotIndex)

                // In Edit Controls Mode: tapping any button opens the Edit Button dialog
                if (isEditControlsMode) {
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        showEditButtonDialog(slotIndex)
                    }
                    return@setOnTouchListener true
                }

                // Normal Flight Mode:
                // - Single Click (isHoldMode == false): normal tap sends a clean click pulse
                // - Hold (isHoldMode == true): keeps pressed while holding finger down
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        if (btnCfg.isHoldMode) {
                            controlState.setButtonPressed(btnCfg.targetVJoyButton, true)
                            updateSingleButtonVisual(slotIndex)
                        } else {
                            // Single Click: pulse press for 120ms
                            val targetBtn = btnCfg.targetVJoyButton
                            controlState.setButtonPressed(targetBtn, true)
                            updateSingleButtonVisual(slotIndex)
                            mainHandler.postDelayed({
                                controlState.setButtonPressed(targetBtn, false)
                                updateSingleButtonVisual(slotIndex)
                            }, 120L)
                        }
                        true
                    }

                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        if (btnCfg.isHoldMode) {
                            controlState.setButtonPressed(btnCfg.targetVJoyButton, false)
                            updateSingleButtonVisual(slotIndex)
                        }
                        true
                    }

                    else -> false
                }
            }
        }

        btnExitEditMode.setOnClickListener {
            setEditControlsMode(false)
        }

        btnResetButtons.setOnClickListener {
            controlState.clearAllButtons()
            config.buttons = FlightClientConfig.createDefaultButtons()
            prefs.saveConfig(config)
            refreshAllButtonLabelsAndColors()
            Toast.makeText(this, "Buttons reset to default.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setEditControlsMode(enabled: Boolean) {
        isEditControlsMode = enabled
        controlState.clearAllButtons()
        editModeBar.visibility = if (enabled) View.VISIBLE else View.GONE
        refreshAllButtonLabelsAndColors()
    }

    private fun getButtonConfig(slotIndex: Int): PhoneButtonConfig {
        return config.buttons.firstOrNull { it.slotIndex == slotIndex }
            ?: PhoneButtonConfig(slotIndex, "B$slotIndex", slotIndex, false)
    }

    private fun refreshAllButtonLabelsAndColors() {
        for (slotIndex in 1..8) {
            updateSingleButtonVisual(slotIndex)
        }
    }

    private fun updateSingleButtonVisual(slotIndex: Int) {
        val btnView = cockpitButtons[slotIndex] ?: return
        val cfg = getButtonConfig(slotIndex)
        val isActive = controlState.isButtonPressed(cfg.targetVJoyButton)

        if (isEditControlsMode) {
            val modeTag = if (cfg.isHoldMode) "HOLD" else "CLICK"
            btnView.text = "${cfg.label}\n[B${cfg.targetVJoyButton} • $modeTag]"
            btnView.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#333333"))
            btnView.setTextColor(Color.parseColor("#FFFFFF"))
        } else {
            btnView.text = if (cfg.isHoldMode) {
                "${cfg.label}\n[HOLD • B${cfg.targetVJoyButton}]"
            } else {
                "${cfg.label}\n[vJoy ${cfg.targetVJoyButton}]"
            }
            if (isActive) {
                btnView.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFFFFF"))
                btnView.setTextColor(Color.parseColor("#0A0A0A"))
            } else {
                btnView.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#222222"))
                btnView.setTextColor(Color.parseColor("#FFFFFF"))
            }
        }
    }

    private fun showEditButtonDialog(slotIndex: Int) {
        val cfg = getButtonConfig(slotIndex)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_button, null)

        val txtTitle = view.findViewById<TextView>(R.id.txtEditButtonTitle)
        val edtLabel = view.findViewById<EditText>(R.id.edtButtonLabel)
        val spnVJoy = view.findViewById<Spinner>(R.id.spnTargetVJoyButton)
        val rbSingleClick = view.findViewById<RadioButton>(R.id.rbSingleClick)
        val rbHoldMode = view.findViewById<RadioButton>(R.id.rbHoldMode)

        txtTitle.text = "EDIT BUTTON $slotIndex"
        edtLabel.setText(cfg.label)
        if (cfg.isHoldMode) {
            rbHoldMode.isChecked = true
        } else {
            rbSingleClick.isChecked = true
        }

        val vjoyOptions = (1..32).map { "vJoy Button $it" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, vjoyOptions)
        spnVJoy.adapter = adapter
        spnVJoy.setSelection((cfg.targetVJoyButton - 1).coerceIn(0, 31))

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                controlState.setButtonPressed(cfg.targetVJoyButton, false)
                cfg.label = edtLabel.text.toString().trim().ifEmpty { "B$slotIndex" }
                cfg.targetVJoyButton = (spnVJoy.selectedItemPosition + 1).coerceIn(1, 32)
                cfg.isHoldMode = rbHoldMode.isChecked
                prefs.saveConfig(config)
                telemetryClient.activeConfig = config
                refreshAllButtonLabelsAndColors()
                enableImmersiveFullscreen()
            }
            .setNegativeButton("Cancel") { _, _ ->
                enableImmersiveFullscreen()
            }
            .create()

        dialog.setOnDismissListener {
            enableImmersiveFullscreen()
        }
        dialog.show()
    }

    private fun setupMenuHubAndPortraitScreens() {
        val axesList = FlightClientConfig.AVAILABLE_VJOY_AXES
        val axisAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, axesList)
        spnMapPitch.adapter = axisAdapter
        spnMapRoll.adapter = axisAdapter
        spnMapRudder.adapter = axisAdapter
        spnMapThrottle.adapter = axisAdapter
        spnMapReverse.adapter = axisAdapter
        spnMapBrake.adapter = axisAdapter

        populateMenuFromConfig()

        // Open ☰ Menu Hub
        btnMenu.setOnClickListener {
            setEditControlsMode(false)
            hubOverlay.visibility = View.VISIBLE
        }

        btnCloseHub.setOnClickListener {
            hubOverlay.visibility = View.GONE
            enableImmersiveFullscreen()
        }

        // Square 1: Connection -> Switch screen to Portrait!
        cardMenuConnection.setOnClickListener {
            populateMenuFromConfig()
            hubOverlay.visibility = View.GONE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            connectionOverlay.visibility = View.VISIBLE
            enableImmersiveFullscreen()
        }

        // Square 2: Settings -> Switch screen to Portrait!
        cardMenuSettings.setOnClickListener {
            populateMenuFromConfig()
            hubOverlay.visibility = View.GONE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            settingsOverlay.visibility = View.VISIBLE
            enableImmersiveFullscreen()
        }

        // Square 3: Edit Controls -> Close Hub and enter interactive Edit Controls mode in Landscape!
        cardMenuEditControls.setOnClickListener {
            hubOverlay.visibility = View.GONE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            setEditControlsMode(true)
            Toast.makeText(this, "Tap any button to edit its name or Click/Hold mode.", Toast.LENGTH_SHORT).show()
            enableImmersiveFullscreen()
        }

        // Close Portrait Connection Screen -> Return to Landscape!
        btnCloseConnection.setOnClickListener {
            saveMenuIntoConfig()
            connectionOverlay.visibility = View.GONE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            enableImmersiveFullscreen()
        }

        // Close Portrait Settings Screen -> Return to Landscape!
        btnCloseSettings.setOnClickListener {
            saveMenuIntoConfig()
            settingsOverlay.visibility = View.GONE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            enableImmersiveFullscreen()
        }

        // Fast LAN PC Discovery (Magnifying Glass 🔍)
        btnSearchPc.setOnClickListener {
            val port = edtServerPort.text.toString().toIntOrNull()?.coerceIn(1024, 65535) ?: 49152
            btnSearchPc.isEnabled = false
            Toast.makeText(this, "Searching for PC on Wi-Fi...", Toast.LENGTH_SHORT).show()

            telemetryClient.discoverPcOnLan(port) { discovered ->
                runOnUiThread {
                    btnSearchPc.isEnabled = true
                    if (discovered != null) {
                        edtServerIp.setText(discovered.ipAddress)
                        edtServerPort.setText(discovered.port.toString())
                        saveMenuIntoConfig()
                        Toast.makeText(
                            this,
                            "Found PC: ${discovered.pcName} (${discovered.ipAddress})",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            this,
                            "No PC found on Wi-Fi (make sure FlightJoy is open on your PC).",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        btnConnect.setOnClickListener {
            if (telemetryClient.isConnectedOrPairing) {
                telemetryClient.disconnect(sendPacket = true)
            } else {
                saveMenuIntoConfig()
                telemetryClient.connect(config)
                connectionOverlay.visibility = View.GONE
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                enableImmersiveFullscreen()
            }
        }

        seekSensitivity.setOnSeekBarChangeListener(simpleSeekListener { p ->
            val v = p.coerceAtLeast(25) / 100.0f
            txtSensitivityLabel.text = String.format(Locale.US, "Gyro Sensitivity: %.2fx", v)
        })

        seekDeadzone.setOnSeekBarChangeListener(simpleSeekListener { p ->
            txtDeadzoneLabel.text = "Center Deadzone: $p%"
        })

        seekMaxAngle.setOnSeekBarChangeListener(simpleSeekListener { p ->
            val v = p.coerceAtLeast(20)
            txtMaxAngleLabel.text = "Max Tilt Angle: ${v}°"
        })
    }

    private fun populateMenuFromConfig() {
        edtServerIp.setText(config.serverIp)
        edtServerPort.setText(config.serverPort.toString())

        val axes = FlightClientConfig.AVAILABLE_VJOY_AXES
        spnMapPitch.setSelection(axes.indexOf(config.mapPitchAxis).coerceAtLeast(0))
        spnMapRoll.setSelection(axes.indexOf(config.mapRollAxis).coerceAtLeast(0))
        spnMapRudder.setSelection(axes.indexOf(config.mapRudderAxis).coerceAtLeast(0))
        spnMapThrottle.setSelection(axes.indexOf(config.mapThrottleAxis).coerceAtLeast(0))
        spnMapReverse.setSelection(axes.indexOf(config.mapReverseAxis).coerceAtLeast(0))
        spnMapBrake.setSelection(axes.indexOf(config.mapBrakeAxis).coerceAtLeast(0))

        seekSensitivity.progress = (config.sensitivity * 100).roundToInt().coerceIn(25, 250)
        txtSensitivityLabel.text = String.format(Locale.US, "Gyro Sensitivity: %.2fx", config.sensitivity)

        seekDeadzone.progress = (config.deadzone * 100).roundToInt().coerceIn(0, 30)
        txtDeadzoneLabel.text = "Center Deadzone: ${seekDeadzone.progress}%"

        seekMaxAngle.progress = config.maxDeflectionDeg.roundToInt().coerceIn(20, 80)
        txtMaxAngleLabel.text = "Max Tilt Angle: ${seekMaxAngle.progress}°"

        chkInvertPitch.isChecked = config.invertPitch
        chkInvertRoll.isChecked = config.invertRoll
        chkInvertYaw.isChecked = config.invertYaw
        chkEnableYaw.isChecked = config.enableYaw
    }

    private fun saveMenuIntoConfig() {
        config.serverIp = edtServerIp.text.toString().trim().ifEmpty { "192.168.1.100" }
        config.serverPort = edtServerPort.text.toString().toIntOrNull()?.coerceIn(1024, 65535) ?: 49152

        val axes = FlightClientConfig.AVAILABLE_VJOY_AXES
        config.mapPitchAxis = axes.getOrElse(spnMapPitch.selectedItemPosition) { "Y" }
        config.mapRollAxis = axes.getOrElse(spnMapRoll.selectedItemPosition) { "X" }
        config.mapRudderAxis = axes.getOrElse(spnMapRudder.selectedItemPosition) { "RZ" }
        config.mapThrottleAxis = axes.getOrElse(spnMapThrottle.selectedItemPosition) { "Z" }
        config.mapReverseAxis = axes.getOrElse(spnMapReverse.selectedItemPosition) { "SL0" }
        config.mapBrakeAxis = axes.getOrElse(spnMapBrake.selectedItemPosition) { "SL1" }

        config.sensitivity = seekSensitivity.progress.coerceAtLeast(25) / 100.0f
        config.deadzone = seekDeadzone.progress / 100.0f
        config.maxDeflectionDeg = seekMaxAngle.progress.coerceAtLeast(20).toFloat()

        config.invertPitch = chkInvertPitch.isChecked
        config.invertRoll = chkInvertRoll.isChecked
        config.invertYaw = chkInvertYaw.isChecked
        config.enableYaw = chkEnableYaw.isChecked

        prefs.saveConfig(config)
        sensorController.currentConfig = config
        telemetryClient.activeConfig = config
    }

    private fun renderOrientation(orientation: NormalizedOrientation) {
        virtualStickView.updateAxes(
            pitchNorm = orientation.pitch,
            rollNorm = orientation.roll,
            dz = config.deadzone
        )

        if (virtualStickView.isLocked) {
            txtOrientationValues.text = "GYROSCOPE LOCKED (0%)"
            return
        }

        val pPct = (orientation.pitch * 100.0f).roundToInt()
        val rPct = (orientation.roll * 100.0f).roundToInt()
        txtOrientationValues.text = String.format(
            Locale.US,
            "PITCH %+d%%   •   ROLL %+d%%",
            pPct,
            rPct
        )
    }

    private fun renderConnectionStatus(snapshot: ClientStatusSnapshot) {
        txtConnectionStatus.text = snapshot.message
        txtConnectionOverlayStatus.text = snapshot.message
        when (snapshot.state) {
            ClientConnectionState.CONNECTED -> {
                txtConnectionStatus.setTextColor(Color.parseColor("#FFFFFF"))
                txtConnectionOverlayStatus.setTextColor(Color.parseColor("#34D399"))
                btnConnect.text = "Disconnect"
                btnConnect.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#424242"))
                btnConnect.setTextColor(Color.parseColor("#FFFFFF"))
            }
            ClientConnectionState.PAIRING -> {
                txtConnectionStatus.setTextColor(Color.parseColor("#BDBDBD"))
                txtConnectionOverlayStatus.setTextColor(Color.parseColor("#BDBDBD"))
                btnConnect.text = "Cancel"
                btnConnect.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#424242"))
                btnConnect.setTextColor(Color.parseColor("#FFFFFF"))
            }
            ClientConnectionState.ERROR_OR_TIMEOUT,
            ClientConnectionState.DISCONNECTED -> {
                txtConnectionStatus.setTextColor(Color.parseColor("#9E9E9E"))
                txtConnectionOverlayStatus.setTextColor(Color.parseColor("#9E9E9E"))
                btnConnect.text = "Connect"
                btnConnect.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFFFFF"))
                btnConnect.setTextColor(Color.parseColor("#0A0A0A"))
            }
        }
    }

    private fun simpleSeekListener(onChanged: (Int) -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                onChanged(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
    }
}

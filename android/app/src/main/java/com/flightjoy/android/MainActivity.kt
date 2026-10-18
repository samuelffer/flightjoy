package com.flightjoy.android

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
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
import com.flightjoy.android.config.ButtonBehaviorMode
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

// Main Cockpit Activity (Landscape flight controls + Portrait Settings/Connection)
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences
    private lateinit var config: FlightClientConfig
    private val logger = AppLogger()
    private val controlState = ControlState()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val easeInterpolator = DecelerateInterpolator(1.6f)

    private lateinit var sensorController: SensorController
    private lateinit var telemetryClient: UdpTelemetryClient

    private var isReverseThrottleMode: Boolean = false
    private var isEditControlsMode: Boolean = false
    private var lastConnectionState: ClientConnectionState = ClientConnectionState.DISCONNECTED

    // Cockpit Views
    private lateinit var btnMenu: Button
    private lateinit var btnCalibrate: Button
    private lateinit var txtConnectionStatus: TextView
    private lateinit var virtualStickView: VirtualStickView
    private lateinit var txtOrientationValues: TextView

    private lateinit var sliderThrottle: ThickSliderView
    private lateinit var txtThrottleValue: TextView
    private lateinit var btnReverseSwitch: AppCompatButton

    private lateinit var brakeSliderContainer: LinearLayout
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

    // 1. Menu Hub Overlay (4 Rounded Square Cards)
    private lateinit var hubOverlay: FrameLayout
    private lateinit var hubModalCard: LinearLayout
    private lateinit var btnCloseHub: Button
    private lateinit var cardMenuQuickConnect: LinearLayout
    private lateinit var txtQuickConnectIcon: TextView
    private lateinit var txtQuickConnectTitle: TextView
    private lateinit var txtQuickConnectSub: TextView
    private lateinit var cardMenuConnection: LinearLayout
    private lateinit var cardMenuSettings: LinearLayout
    private lateinit var cardMenuEditControls: LinearLayout

    // 2. Portrait Connection Screen
    private lateinit var connectionOverlay: FrameLayout
    private lateinit var connectionContentCard: LinearLayout
    private lateinit var btnCloseConnection: Button
    private lateinit var txtConnectionOverlayStatus: TextView
    private lateinit var edtServerIp: EditText
    private lateinit var edtServerPort: EditText
    private lateinit var btnSearchPc: Button
    private lateinit var btnConnect: Button

    // 3. Portrait Settings Screen
    private lateinit var settingsOverlay: FrameLayout
    private lateinit var settingsContentCard: LinearLayout
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

    private lateinit var chkShowBrakeSlider: CheckBox
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
            animatePress(btnCalibrate)
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

    private fun animateShowOverlay(overlay: View, card: View) {
        overlay.alpha = 0f
        overlay.visibility = View.VISIBLE
        card.scaleX = 0.92f
        card.scaleY = 0.92f
        overlay.animate()
            .alpha(1f)
            .setDuration(170L)
            .setInterpolator(easeInterpolator)
            .start()
        card.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(190L)
            .setInterpolator(easeInterpolator)
            .start()
    }

    private fun animateHideOverlay(overlay: View, card: View, onEnd: (() -> Unit)? = null) {
        card.animate()
            .scaleX(0.94f)
            .scaleY(0.94f)
            .setDuration(130L)
            .setInterpolator(easeInterpolator)
            .start()
        overlay.animate()
            .alpha(0f)
            .setDuration(140L)
            .setInterpolator(easeInterpolator)
            .withEndAction {
                overlay.visibility = View.GONE
                overlay.alpha = 1f
                card.scaleX = 1f
                card.scaleY = 1f
                onEnd?.invoke()
            }
            .start()
    }

    private fun animatePress(view: View) {
        view.animate()
            .scaleX(0.94f)
            .scaleY(0.94f)
            .setDuration(65L)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(110L)
                    .setInterpolator(easeInterpolator)
                    .start()
            }
            .start()
    }

    private fun bindViews() {
        btnMenu = findViewById(R.id.btnMenu)
        btnCalibrate = findViewById(R.id.btnCalibrate)
        txtConnectionStatus = findViewById(R.id.txtConnectionStatus)
        virtualStickView = findViewById(R.id.virtualStickView)
        txtOrientationValues = findViewById(R.id.txtOrientationValues)

        sliderThrottle = findViewById(R.id.sliderThrottle)
        txtThrottleValue = findViewById(R.id.txtThrottleValue)
        btnReverseSwitch = findViewById(R.id.btnReverseSwitch)

        brakeSliderContainer = findViewById(R.id.brakeSliderContainer)
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
        hubModalCard = findViewById(R.id.hubModalCard)
        btnCloseHub = findViewById(R.id.btnCloseHub)
        cardMenuQuickConnect = findViewById(R.id.cardMenuQuickConnect)
        txtQuickConnectIcon = findViewById(R.id.txtQuickConnectIcon)
        txtQuickConnectTitle = findViewById(R.id.txtQuickConnectTitle)
        txtQuickConnectSub = findViewById(R.id.txtQuickConnectSub)
        cardMenuConnection = findViewById(R.id.cardMenuConnection)
        cardMenuSettings = findViewById(R.id.cardMenuSettings)
        cardMenuEditControls = findViewById(R.id.cardMenuEditControls)

        connectionOverlay = findViewById(R.id.connectionOverlay)
        connectionContentCard = findViewById(R.id.connectionContentCard)
        btnCloseConnection = findViewById(R.id.btnCloseConnection)
        txtConnectionOverlayStatus = findViewById(R.id.txtConnectionOverlayStatus)
        edtServerIp = findViewById(R.id.edtServerIp)
        edtServerPort = findViewById(R.id.edtServerPort)
        btnSearchPc = findViewById(R.id.btnSearchPc)
        btnConnect = findViewById(R.id.btnConnect)

        settingsOverlay = findViewById(R.id.settingsOverlay)
        settingsContentCard = findViewById(R.id.settingsContentCard)
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

        chkShowBrakeSlider = findViewById(R.id.chkShowBrakeSlider)
        chkInvertPitch = findViewById(R.id.chkInvertPitch)
        chkInvertRoll = findViewById(R.id.chkInvertRoll)
        chkInvertYaw = findViewById(R.id.chkInvertYaw)
        chkEnableYaw = findViewById(R.id.chkEnableYaw)
    }

    private fun setupThickSlidersAndReverseSwitch() {
        // Left Slider (Vertical, 1:1 tracking on the square handle, percentage only)
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
            animatePress(btnReverseSwitch)
            isReverseThrottleMode = !isReverseThrottleMode
            sliderThrottle.isInvertedVertical = isReverseThrottleMode
            controlState.throttle = -1.0f
            controlState.reverseThrottle = -1.0f
            sliderThrottle.setNormalizedValueImmediate(-1.0f)
            updateReverseSwitchVisual()
        }

        // Optional Vertical Progressive Brake Slider (Starts at top 0%, pull down freely to 100%)
        updateBrakeSliderVisibility()
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

        // Bottom-Right Horizontal Slider
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
            animatePress(btnRudderMode)
            config.rudderAutoCenter = !config.rudderAutoCenter
            sliderRudder.returnToCenterOnRelease = config.rudderAutoCenter
            if (config.rudderAutoCenter) {
                sliderRudder.setNormalizedValueImmediate(0.0f)
            }
            prefs.saveConfig(config)
            updateRudderModeButtonVisual()
        }
    }

    private fun updateBrakeSliderVisibility() {
        if (config.showBrakeSlider) {
            brakeSliderContainer.visibility = View.VISIBLE
        } else {
            brakeSliderContainer.visibility = View.GONE
            controlState.brakeSlider = -1.0f
        }
    }

    private fun updateReverseSwitchVisual() {
        if (isReverseThrottleMode) {
            btnReverseSwitch.setBackgroundResource(R.drawable.bg_reverse_switch_on)
            btnReverseSwitch.text = "REV\nON"
            btnReverseSwitch.setTextColor(Color.parseColor("#0A0A0A"))
        } else {
            btnReverseSwitch.setBackgroundResource(R.drawable.bg_reverse_switch_off)
            btnReverseSwitch.text = "REV\nOFF"
            btnReverseSwitch.setTextColor(Color.parseColor("#9E9E9E"))
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
                        animatePress(btnView)
                        showEditButtonDialog(slotIndex)
                    }
                    return@setOnTouchListener true
                }

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        animatePress(btnView)
                        when (btnCfg.behaviorMode) {
                            ButtonBehaviorMode.HOLD -> {
                                controlState.setButtonPressed(btnCfg.targetVJoyButton, true)
                                updateSingleButtonVisual(slotIndex)
                            }
                            ButtonBehaviorMode.TOGGLE -> {
                                val nextState = !controlState.isButtonPressed(btnCfg.targetVJoyButton)
                                controlState.setButtonPressed(btnCfg.targetVJoyButton, nextState)
                                updateSingleButtonVisual(slotIndex)
                            }
                            ButtonBehaviorMode.SINGLE_CLICK -> {
                                val targetBtn = btnCfg.targetVJoyButton
                                controlState.setButtonPressed(targetBtn, true)
                                updateSingleButtonVisual(slotIndex)
                                mainHandler.postDelayed({
                                    controlState.setButtonPressed(targetBtn, false)
                                    updateSingleButtonVisual(slotIndex)
                                }, 120L)
                            }
                        }
                        true
                    }

                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        if (btnCfg.behaviorMode == ButtonBehaviorMode.HOLD) {
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
            animatePress(btnExitEditMode)
            setEditControlsMode(false)
        }

        btnResetButtons.setOnClickListener {
            animatePress(btnResetButtons)
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
            ?: PhoneButtonConfig(slotIndex, "B$slotIndex", slotIndex, ButtonBehaviorMode.SINGLE_CLICK)
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

        val modeTag = when (cfg.behaviorMode) {
            ButtonBehaviorMode.HOLD -> "HOLD"
            ButtonBehaviorMode.TOGGLE -> "STAY"
            ButtonBehaviorMode.SINGLE_CLICK -> "CLICK"
        }

        if (isEditControlsMode) {
            btnView.text = "${cfg.label}\n[B${cfg.targetVJoyButton} • $modeTag]"
            btnView.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#333333"))
            btnView.setTextColor(Color.parseColor("#FFFFFF"))
        } else {
            btnView.text = if (cfg.behaviorMode == ButtonBehaviorMode.SINGLE_CLICK) {
                "${cfg.label}\n[vJoy ${cfg.targetVJoyButton}]"
            } else {
                "${cfg.label}\n[$modeTag • B${cfg.targetVJoyButton}]"
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
        val btnModeSingleClick = view.findViewById<Button>(R.id.btnModeSingleClick)
        val btnModeHold = view.findViewById<Button>(R.id.btnModeHold)
        val btnModeToggle = view.findViewById<Button>(R.id.btnModeToggle)
        val btnCancel = view.findViewById<Button>(R.id.btnCancelEditButton)
        val btnSave = view.findViewById<Button>(R.id.btnSaveEditButton)

        txtTitle.text = "EDIT BUTTON $slotIndex"
        edtLabel.setText(cfg.label)

        var selectedMode = cfg.behaviorMode

        fun refreshModeButtons() {
            val activeBg = ColorStateList.valueOf(Color.parseColor("#FFFFFF"))
            val inactiveBg = ColorStateList.valueOf(Color.parseColor("#222222"))
            val activeFg = Color.parseColor("#0A0A0A")
            val inactiveFg = Color.parseColor("#FFFFFF")

            btnModeSingleClick.backgroundTintList =
                if (selectedMode == ButtonBehaviorMode.SINGLE_CLICK) activeBg else inactiveBg
            btnModeSingleClick.setTextColor(
                if (selectedMode == ButtonBehaviorMode.SINGLE_CLICK) activeFg else inactiveFg
            )

            btnModeHold.backgroundTintList =
                if (selectedMode == ButtonBehaviorMode.HOLD) activeBg else inactiveBg
            btnModeHold.setTextColor(
                if (selectedMode == ButtonBehaviorMode.HOLD) activeFg else inactiveFg
            )

            btnModeToggle.backgroundTintList =
                if (selectedMode == ButtonBehaviorMode.TOGGLE) activeBg else inactiveBg
            btnModeToggle.setTextColor(
                if (selectedMode == ButtonBehaviorMode.TOGGLE) activeFg else inactiveFg
            )
        }

        refreshModeButtons()

        btnModeSingleClick.setOnClickListener {
            selectedMode = ButtonBehaviorMode.SINGLE_CLICK
            refreshModeButtons()
        }
        btnModeHold.setOnClickListener {
            selectedMode = ButtonBehaviorMode.HOLD
            refreshModeButtons()
        }
        btnModeToggle.setOnClickListener {
            selectedMode = ButtonBehaviorMode.TOGGLE
            refreshModeButtons()
        }

        val vjoyOptions = (1..32).map { "vJoy Button $it" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, vjoyOptions)
        spnVJoy.adapter = adapter
        spnVJoy.setSelection((cfg.targetVJoyButton - 1).coerceIn(0, 31))

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnSave.setOnClickListener {
            controlState.setButtonPressed(cfg.targetVJoyButton, false)
            cfg.label = edtLabel.text.toString().trim().ifEmpty { "B$slotIndex" }
            cfg.targetVJoyButton = (spnVJoy.selectedItemPosition + 1).coerceIn(1, 32)
            cfg.behaviorMode = selectedMode
            prefs.saveConfig(config)
            telemetryClient.activeConfig = config
            refreshAllButtonLabelsAndColors()
            dialog.dismiss()
        }

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

        // Open ☰ Menu Hub with Smooth Animation
        btnMenu.setOnClickListener {
            animatePress(btnMenu)
            setEditControlsMode(false)
            updateQuickConnectTileVisual()
            animateShowOverlay(hubOverlay, hubModalCard)
        }

        btnCloseHub.setOnClickListener {
            animateHideOverlay(hubOverlay, hubModalCard) {
                enableImmersiveFullscreen()
            }
        }

        // Square 1: 1-Tap Quick Connect / Disconnect
        cardMenuQuickConnect.setOnClickListener {
            animatePress(cardMenuQuickConnect)
            animateHideOverlay(hubOverlay, hubModalCard) {
                enableImmersiveFullscreen()
                if (telemetryClient.isConnectedOrPairing) {
                    telemetryClient.disconnect(sendPacket = true)
                    Toast.makeText(this, "Disconnected.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Connecting to ${config.serverIp}:${config.serverPort}...", Toast.LENGTH_SHORT).show()
                    telemetryClient.connect(config)
                }
            }
        }

        // Square 2: Connection -> Switch screen to Portrait!
        cardMenuConnection.setOnClickListener {
            animatePress(cardMenuConnection)
            populateMenuFromConfig()
            hubOverlay.visibility = View.GONE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            animateShowOverlay(connectionOverlay, connectionContentCard)
            enableImmersiveFullscreen()
        }

        // Square 3: Settings -> Switch screen to Portrait!
        cardMenuSettings.setOnClickListener {
            animatePress(cardMenuSettings)
            populateMenuFromConfig()
            hubOverlay.visibility = View.GONE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            animateShowOverlay(settingsOverlay, settingsContentCard)
            enableImmersiveFullscreen()
        }

        // Square 4: Edit Controls -> Close Hub and enter interactive Edit Controls mode in Landscape!
        cardMenuEditControls.setOnClickListener {
            animatePress(cardMenuEditControls)
            animateHideOverlay(hubOverlay, hubModalCard) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                setEditControlsMode(true)
                Toast.makeText(this, "Tap any button to edit its name or behavior.", Toast.LENGTH_SHORT).show()
                enableImmersiveFullscreen()
            }
        }

        // Close Portrait Connection Screen -> Return to Landscape!
        btnCloseConnection.setOnClickListener {
            saveMenuIntoConfig()
            animateHideOverlay(connectionOverlay, connectionContentCard) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                enableImmersiveFullscreen()
            }
        }

        // Close Portrait Settings Screen -> Return to Landscape!
        btnCloseSettings.setOnClickListener {
            saveMenuIntoConfig()
            updateBrakeSliderVisibility()
            animateHideOverlay(settingsOverlay, settingsContentCard) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                enableImmersiveFullscreen()
            }
        }

        // Fast LAN PC Discovery (Magnifying Glass 🔍)
        btnSearchPc.setOnClickListener {
            animatePress(btnSearchPc)
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
            animatePress(btnConnect)
            if (telemetryClient.isConnectedOrPairing) {
                telemetryClient.disconnect(sendPacket = true)
            } else {
                saveMenuIntoConfig()
                telemetryClient.connect(config)
                animateHideOverlay(connectionOverlay, connectionContentCard) {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    enableImmersiveFullscreen()
                }
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
            val v = p.coerceAtLeast(15)
            txtMaxAngleLabel.text = "Max Tilt Angle: ${v}°"
        })
    }

    private fun updateQuickConnectTileVisual() {
        if (telemetryClient.isConnectedOrPairing) {
            txtQuickConnectIcon.text = "✕"
            txtQuickConnectIcon.setBackgroundColor(Color.parseColor("#383838"))
            txtQuickConnectIcon.setTextColor(Color.parseColor("#FFFFFF"))
            txtQuickConnectTitle.text = "Disconnect"
            txtQuickConnectSub.text = "Active Session"
        } else {
            txtQuickConnectIcon.text = "⚡"
            txtQuickConnectIcon.setBackgroundColor(Color.parseColor("#FFFFFF"))
            txtQuickConnectIcon.setTextColor(Color.parseColor("#0A0A0A"))
            txtQuickConnectTitle.text = "Connect"
            txtQuickConnectSub.text = "${config.serverIp}"
        }
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

        seekMaxAngle.progress = config.maxDeflectionDeg.roundToInt().coerceIn(15, 80)
        txtMaxAngleLabel.text = "Max Tilt Angle: ${seekMaxAngle.progress}°"

        chkShowBrakeSlider.isChecked = config.showBrakeSlider
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
        config.maxDeflectionDeg = seekMaxAngle.progress.coerceAtLeast(15).toFloat()

        config.showBrakeSlider = chkShowBrakeSlider.isChecked
        config.invertPitch = chkInvertPitch.isChecked
        config.invertRoll = chkInvertRoll.isChecked
        config.invertYaw = chkInvertYaw.isChecked
        config.enableYaw = chkEnableYaw.isChecked

        prefs.saveConfig(config)
        sensorController.currentConfig = config
        telemetryClient.activeConfig = config
        updateBrakeSliderVisibility()
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
        val prevState = lastConnectionState
        lastConnectionState = snapshot.state

        txtConnectionStatus.text = snapshot.message
        txtConnectionOverlayStatus.text = snapshot.message
        updateQuickConnectTileVisual()

        if (snapshot.state == ClientConnectionState.ERROR_OR_TIMEOUT &&
            prevState != ClientConnectionState.ERROR_OR_TIMEOUT
        ) {
            Toast.makeText(
                this,
                "Connection failed. Please check Connection settings.",
                Toast.LENGTH_LONG
            ).show()
        }

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

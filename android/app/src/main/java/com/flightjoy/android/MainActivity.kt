package com.flightjoy.android

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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

    // Cockpit Views
    private lateinit var btnMenu: Button
    private lateinit var btnCalibrate: Button
    private lateinit var txtConnectionStatus: TextView
    private lateinit var virtualStickView: VirtualStickView
    private lateinit var txtOrientationValues: TextView

    private lateinit var sliderThrottle: ThickSliderView
    private lateinit var txtThrottleValue: TextView

    private lateinit var sliderRudder: ThickSliderView
    private lateinit var txtRudderValue: TextView
    private lateinit var btnRudderCenter: Button

    private val cockpitButtons = mutableMapOf<Int, Button>()

    // Janela Modal na Tela (Menu ☰)
    private lateinit var menuOverlay: FrameLayout
    private lateinit var btnCloseMenu: Button
    private lateinit var edtServerIp: EditText
    private lateinit var edtServerPort: EditText
    private lateinit var btnSearchPc: Button
    private lateinit var btnConnect: Button

    private lateinit var spnMapPitch: Spinner
    private lateinit var spnMapRoll: Spinner
    private lateinit var spnMapRudder: Spinner
    private lateinit var spnMapThrottle: Spinner

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
    private lateinit var btnResetButtons: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = AppPreferences(this)
        config = prefs.loadConfig()
        val deviceId = prefs.getOrCreateDeviceId()

        bindViews()
        setupThickSliders()
        setupFlightButtons()
        setupMenuOverlay()

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

        btnMenu.setOnClickListener {
            populateMenuFromConfig()
            menuOverlay.visibility = View.VISIBLE
        }

        btnCloseMenu.setOnClickListener {
            saveMenuIntoConfig()
            menuOverlay.visibility = View.GONE
        }

        btnCalibrate.setOnClickListener {
            sensorController.calibrateCenter()
            Toast.makeText(this, "Giroscópio calibrado!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        sensorController.currentConfig = config
        sensorController.start()
    }

    override fun onPause() {
        super.onPause()
        sensorController.stop()
    }

    override fun onDestroy() {
        telemetryClient.disconnect(sendPacket = true)
        super.onDestroy()
    }

    private fun bindViews() {
        btnMenu = findViewById(R.id.btnMenu)
        btnCalibrate = findViewById(R.id.btnCalibrate)
        txtConnectionStatus = findViewById(R.id.txtConnectionStatus)
        virtualStickView = findViewById(R.id.virtualStickView)
        txtOrientationValues = findViewById(R.id.txtOrientationValues)

        sliderThrottle = findViewById(R.id.sliderThrottle)
        txtThrottleValue = findViewById(R.id.txtThrottleValue)

        sliderRudder = findViewById(R.id.sliderRudder)
        txtRudderValue = findViewById(R.id.txtRudderValue)
        btnRudderCenter = findViewById(R.id.btnRudderCenter)

        cockpitButtons[1] = findViewById(R.id.btnB1)
        cockpitButtons[2] = findViewById(R.id.btnB2)
        cockpitButtons[3] = findViewById(R.id.btnB3)
        cockpitButtons[4] = findViewById(R.id.btnB4)
        cockpitButtons[5] = findViewById(R.id.btnB5)
        cockpitButtons[6] = findViewById(R.id.btnB6)
        cockpitButtons[7] = findViewById(R.id.btnB7)
        cockpitButtons[8] = findViewById(R.id.btnB8)

        menuOverlay = findViewById(R.id.menuOverlay)
        btnCloseMenu = findViewById(R.id.btnCloseMenu)
        edtServerIp = findViewById(R.id.edtServerIp)
        edtServerPort = findViewById(R.id.edtServerPort)
        btnSearchPc = findViewById(R.id.btnSearchPc)
        btnConnect = findViewById(R.id.btnConnect)

        spnMapPitch = findViewById(R.id.spnMapPitch)
        spnMapRoll = findViewById(R.id.spnMapRoll)
        spnMapRudder = findViewById(R.id.spnMapRudder)
        spnMapThrottle = findViewById(R.id.spnMapThrottle)

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
        btnResetButtons = findViewById(R.id.btnResetButtons)
    }

    private fun setupThickSliders() {
        // Slider Esquerdo: Potência (Vertical, suave, só move pelo quadradinho)
        sliderThrottle.apply {
            isVertical = true
            isBipolarCenter = false
            enableSmoothDamping = true
            dragSensitivityFactor = 0.85f
            onValueChanged = { normalized ->
                controlState.throttle = normalized
                val pct = (((normalized + 1.0f) * 0.5f) * 100.0f).roundToInt()
                txtThrottleValue.text = "$pct%"
            }
            setNormalizedValueImmediate(-1.0f)
        }

        // Slider Direito Inferior: Rudder / Pedais (Horizontal, centrado em 0%, só move pelo quadradinho)
        sliderRudder.apply {
            isVertical = false
            isBipolarCenter = true
            enableSmoothDamping = false
            dragSensitivityFactor = 0.90f
            onValueChanged = { normalized ->
                controlState.rudderSlider = normalized
                val pct = (normalized * 100.0f).roundToInt()
                txtRudderValue.text = String.format(Locale.US, "%+d%%", pct)
            }
            setNormalizedValueImmediate(0.0f)
        }

        btnRudderCenter.setOnClickListener {
            sliderRudder.setNormalizedValueImmediate(0.0f)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFlightButtons() {
        refreshAllButtonLabelsAndColors()

        for ((slotIndex, btnView) in cockpitButtons) {
            var longPressTriggered = false
            val longPressRunnable = Runnable {
                longPressTriggered = true
                val btnCfg = getButtonConfig(slotIndex)
                controlState.setButtonPressed(btnCfg.targetVJoyButton, false)
                updateSingleButtonVisual(slotIndex)
                showEditButtonDialog(slotIndex)
            }

            btnView.setOnTouchListener { _, event ->
                val btnCfg = getButtonConfig(slotIndex)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        longPressTriggered = false
                        mainHandler.postDelayed(longPressRunnable, 550L)

                        if (!btnCfg.isToggleMode) {
                            controlState.setButtonPressed(btnCfg.targetVJoyButton, true)
                            updateSingleButtonVisual(slotIndex)
                        }
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        mainHandler.removeCallbacks(longPressRunnable)
                        if (!longPressTriggered) {
                            if (btnCfg.isToggleMode) {
                                val nextState = !controlState.isButtonPressed(btnCfg.targetVJoyButton)
                                controlState.setButtonPressed(btnCfg.targetVJoyButton, nextState)
                            } else {
                                controlState.setButtonPressed(btnCfg.targetVJoyButton, false)
                            }
                            updateSingleButtonVisual(slotIndex)
                        }
                        true
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        mainHandler.removeCallbacks(longPressRunnable)
                        if (!btnCfg.isToggleMode) {
                            controlState.setButtonPressed(btnCfg.targetVJoyButton, false)
                            updateSingleButtonVisual(slotIndex)
                        }
                        true
                    }

                    else -> false
                }
            }
        }
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

        btnView.text = "${cfg.label}\n[vJoy ${cfg.targetVJoyButton}]"
        if (isActive) {
            btnView.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFFFFF"))
            btnView.setTextColor(Color.parseColor("#0A0A0A"))
        } else {
            btnView.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#222222"))
            btnView.setTextColor(Color.parseColor("#FFFFFF"))
        }
    }

    private fun showEditButtonDialog(slotIndex: Int) {
        val cfg = getButtonConfig(slotIndex)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_button, null)

        val txtTitle = view.findViewById<TextView>(R.id.txtEditButtonTitle)
        val edtLabel = view.findViewById<EditText>(R.id.edtButtonLabel)
        val spnVJoy = view.findViewById<Spinner>(R.id.spnTargetVJoyButton)
        val chkToggle = view.findViewById<CheckBox>(R.id.chkButtonToggleMode)

        txtTitle.text = "EDITAR BOTÃO $slotIndex"
        edtLabel.setText(cfg.label)
        chkToggle.isChecked = cfg.isToggleMode

        val vjoyOptions = (1..32).map { "Botão vJoy $it" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, vjoyOptions)
        spnVJoy.adapter = adapter
        spnVJoy.setSelection((cfg.targetVJoyButton - 1).coerceIn(0, 31))

        AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("Salvar") { _, _ ->
                controlState.setButtonPressed(cfg.targetVJoyButton, false)
                cfg.label = edtLabel.text.toString().trim().ifEmpty { "B$slotIndex" }
                cfg.targetVJoyButton = (spnVJoy.selectedItemPosition + 1).coerceIn(1, 32)
                cfg.isToggleMode = chkToggle.isChecked
                prefs.saveConfig(config)
                telemetryClient.activeConfig = config
                refreshAllButtonLabelsAndColors()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun setupMenuOverlay() {
        val axesList = FlightClientConfig.AVAILABLE_VJOY_AXES
        val axisAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, axesList)
        spnMapPitch.adapter = axisAdapter
        spnMapRoll.adapter = axisAdapter
        spnMapRudder.adapter = axisAdapter
        spnMapThrottle.adapter = axisAdapter

        populateMenuFromConfig()

        // Botão de Lupa: Busca Rápida de PC na Rede Local
        btnSearchPc.setOnClickListener {
            val port = edtServerPort.text.toString().toIntOrNull()?.coerceIn(1024, 65535) ?: 49152
            btnSearchPc.isEnabled = false
            Toast.makeText(this, "Buscando PC na rede Wi-Fi...", Toast.LENGTH_SHORT).show()

            telemetryClient.discoverPcOnLan(port) { discovered ->
                runOnUiThread {
                    btnSearchPc.isEnabled = true
                    if (discovered != null) {
                        edtServerIp.setText(discovered.ipAddress)
                        edtServerPort.setText(discovered.port.toString())
                        saveMenuIntoConfig()
                        Toast.makeText(
                            this,
                            "PC encontrado: ${discovered.pcName} (${discovered.ipAddress})",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            this,
                            "Nenhum PC encontrado na rede (verifique se o FlightJoy está aberto no PC).",
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
                menuOverlay.visibility = View.GONE
            }
        }

        seekSensitivity.setOnSeekBarChangeListener(simpleSeekListener { p ->
            val v = p.coerceAtLeast(25) / 100.0f
            txtSensitivityLabel.text = String.format(Locale.US, "Sensibilidade do Giroscópio: %.2fx", v)
        })

        seekDeadzone.setOnSeekBarChangeListener(simpleSeekListener { p ->
            txtDeadzoneLabel.text = "Zona Morta Central: $p%"
        })

        seekMaxAngle.setOnSeekBarChangeListener(simpleSeekListener { p ->
            val v = p.coerceAtLeast(20)
            txtMaxAngleLabel.text = "Ângulo Máximo de Inclinação: ${v}°"
        })

        btnResetButtons.setOnClickListener {
            controlState.clearAllButtons()
            config.buttons = FlightClientConfig.createDefaultButtons()
            prefs.saveConfig(config)
            refreshAllButtonLabelsAndColors()
            Toast.makeText(this, "Botões restaurados para o padrão.", Toast.LENGTH_SHORT).show()
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

        seekSensitivity.progress = (config.sensitivity * 100).roundToInt().coerceIn(25, 250)
        txtSensitivityLabel.text = String.format(Locale.US, "Sensibilidade do Giroscópio: %.2fx", config.sensitivity)

        seekDeadzone.progress = (config.deadzone * 100).roundToInt().coerceIn(0, 30)
        txtDeadzoneLabel.text = "Zona Morta Central: ${seekDeadzone.progress}%"

        seekMaxAngle.progress = config.maxDeflectionDeg.roundToInt().coerceIn(20, 80)
        txtMaxAngleLabel.text = "Ângulo Máximo de Inclinação: ${seekMaxAngle.progress}°"

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
        when (snapshot.state) {
            ClientConnectionState.CONNECTED -> {
                txtConnectionStatus.setTextColor(Color.parseColor("#FFFFFF"))
                btnConnect.text = "Desconectar"
                btnConnect.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#424242"))
                btnConnect.setTextColor(Color.parseColor("#FFFFFF"))
            }
            ClientConnectionState.PAIRING -> {
                txtConnectionStatus.setTextColor(Color.parseColor("#BDBDBD"))
                btnConnect.text = "Cancelar"
                btnConnect.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#424242"))
                btnConnect.setTextColor(Color.parseColor("#FFFFFF"))
            }
            ClientConnectionState.ERROR_OR_TIMEOUT,
            ClientConnectionState.DISCONNECTED -> {
                txtConnectionStatus.setTextColor(Color.parseColor("#9E9E9E"))
                btnConnect.text = "Conectar"
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

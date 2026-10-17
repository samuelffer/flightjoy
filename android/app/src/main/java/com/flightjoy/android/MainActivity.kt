package com.flightjoy.android

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.flightjoy.android.config.AppPreferences
import com.flightjoy.android.config.FlightClientConfig
import com.flightjoy.android.control.ControlState
import com.flightjoy.android.logging.AppLogger
import com.flightjoy.android.network.ClientConnectionState
import com.flightjoy.android.network.ClientStatusSnapshot
import com.flightjoy.android.network.UdpTelemetryClient
import com.flightjoy.android.sensor.NormalizedOrientation
import com.flightjoy.android.sensor.SensorController
import com.flightjoy.android.ui.VirtualStickView
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences
    private lateinit var config: FlightClientConfig
    private val logger = AppLogger()
    private val controlState = ControlState()

    private lateinit var sensorController: SensorController
    private lateinit var telemetryClient: UdpTelemetryClient

    private lateinit var edtServerIp: EditText
    private lateinit var edtServerPort: EditText
    private lateinit var edtPairingPin: EditText
    private lateinit var btnConnect: Button
    private lateinit var btnCalibrate: Button
    private lateinit var btnSettings: Button
    private lateinit var txtConnectionStatus: TextView

    private lateinit var seekThrottle: SeekBar
    private lateinit var txtThrottleValue: TextView
    private lateinit var btnThrottleIdle: Button

    private lateinit var seekAux: SeekBar
    private lateinit var txtAuxValue: TextView
    private lateinit var btnAuxCenter: Button

    private lateinit var virtualStickView: VirtualStickView
    private lateinit var txtOrientationValues: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = AppPreferences(this)
        config = prefs.loadConfig()
        val deviceId = prefs.getOrCreateDeviceId()

        bindViews()
        populateHeaderFields()
        setupSliders()
        setupFlightButtons()

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
        )

        btnConnect.setOnClickListener {
            if (telemetryClient.isConnectedOrPairing) {
                telemetryClient.disconnect(sendPacket = true)
            } else {
                saveHeaderIntoConfig()
                telemetryClient.connect(config)
            }
        }

        btnCalibrate.setOnClickListener {
            sensorController.calibrateCenter()
            Toast.makeText(this, "Centro do giroscópio calibrado!", Toast.LENGTH_SHORT).show()
        }

        btnSettings.setOnClickListener {
            showSettingsDialog()
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
        controlState.clearAllButtons()
    }

    override fun onDestroy() {
        telemetryClient.disconnect(sendPacket = true)
        super.onDestroy()
    }

    private fun bindViews() {
        edtServerIp = findViewById(R.id.edtServerIp)
        edtServerPort = findViewById(R.id.edtServerPort)
        edtPairingPin = findViewById(R.id.edtPairingPin)
        btnConnect = findViewById(R.id.btnConnect)
        btnCalibrate = findViewById(R.id.btnCalibrate)
        btnSettings = findViewById(R.id.btnSettings)
        txtConnectionStatus = findViewById(R.id.txtConnectionStatus)

        seekThrottle = findViewById(R.id.seekThrottle)
        txtThrottleValue = findViewById(R.id.txtThrottleValue)
        btnThrottleIdle = findViewById(R.id.btnThrottleIdle)

        seekAux = findViewById(R.id.seekAux)
        txtAuxValue = findViewById(R.id.txtAuxValue)
        btnAuxCenter = findViewById(R.id.btnAuxCenter)

        virtualStickView = findViewById(R.id.virtualStickView)
        txtOrientationValues = findViewById(R.id.txtOrientationValues)
    }

    private fun populateHeaderFields() {
        edtServerIp.setText(config.serverIp)
        edtServerPort.setText(config.serverPort.toString())
        edtPairingPin.setText(config.pairingPin)
    }

    private fun saveHeaderIntoConfig() {
        config.serverIp = edtServerIp.text.toString().trim().ifEmpty { "192.168.1.100" }
        config.serverPort = edtServerPort.text.toString().toIntOrNull()?.coerceIn(1024, 65535) ?: 49152
        config.pairingPin = edtPairingPin.text.toString().trim().ifEmpty { "482910" }
        prefs.saveConfig(config)
    }

    private fun setupSliders() {
        // Throttle: progress 0..1000 -> normalizado -1.0..+1.0 (exibido 0%..100%)
        seekThrottle.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val pct = (progress / 10.0f).roundToInt()
                val normalized = (progress / 500.0f) - 1.0f
                controlState.throttle = normalized.coerceIn(-1.0f, 1.0f)
                txtThrottleValue.text = "THR (Potência): $pct%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnThrottleIdle.setOnClickListener {
            seekThrottle.progress = 0
        }

        // Aux / Flaps: progress 0..1000 (centro 500) -> normalizado -1.0..+1.0 (exibido -100%..+100%)
        seekAux.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val normalized = ((progress - 500) / 500.0f).coerceIn(-1.0f, 1.0f)
                val pct = (normalized * 100.0f).roundToInt()
                controlState.aux = normalized
                txtAuxValue.text = "AUX / TRIM: $pct%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnAuxCenter.setOnClickListener {
            seekAux.progress = 500
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFlightButtons() {
        val buttonIds = listOf(
            R.id.btnB1 to 1,
            R.id.btnB2 to 2,
            R.id.btnB3 to 3,
            R.id.btnB4 to 4,
            R.id.btnB5 to 5,
            R.id.btnB6 to 6,
            R.id.btnB7 to 7,
            R.id.btnB8 to 8
        )

        val defaultColor = ColorStateList.valueOf(Color.parseColor("#334155"))
        val triggerDefaultColor = ColorStateList.valueOf(Color.parseColor("#0284C7"))
        val pressedColor = ColorStateList.valueOf(Color.parseColor("#22C55E"))

        for ((viewId, btnNumber) in buttonIds) {
            val btn = findViewById<Button>(viewId)
            val restColor = if (btnNumber == 1) triggerDefaultColor else defaultColor
            btn.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                        controlState.setButtonPressed(btnNumber, true)
                        v.backgroundTintList = pressedColor
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                        controlState.setButtonPressed(btnNumber, false)
                        v.backgroundTintList = restColor
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun renderOrientation(orientation: NormalizedOrientation) {
        virtualStickView.updateAxes(
            pitchNorm = orientation.pitch,
            rollNorm = orientation.roll,
            yawNorm = orientation.yaw,
            dz = config.deadzone
        )

        val pPct = orientation.pitch * 100.0f
        val rPct = orientation.roll * 100.0f
        val yPct = orientation.yaw * 100.0f
        txtOrientationValues.text = String.format(
            Locale.US,
            "Pitch: %+05.1f%%  |  Roll: %+05.1f%%  |  Yaw: %+05.1f%%",
            pPct,
            rPct,
            yPct
        )
    }

    private fun renderConnectionStatus(snapshot: ClientStatusSnapshot) {
        txtConnectionStatus.text = snapshot.message
        when (snapshot.state) {
            ClientConnectionState.CONNECTED -> {
                txtConnectionStatus.setTextColor(Color.parseColor("#22C55E"))
                btnConnect.text = "Desconectar"
                btnConnect.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EF4444"))
            }
            ClientConnectionState.PAIRING -> {
                txtConnectionStatus.setTextColor(Color.parseColor("#F59E0B"))
                btnConnect.text = "Cancelar"
                btnConnect.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F59E0B"))
            }
            ClientConnectionState.ERROR_OR_TIMEOUT -> {
                txtConnectionStatus.setTextColor(Color.parseColor("#EF4444"))
                btnConnect.text = "Conectar"
                btnConnect.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
            }
            ClientConnectionState.DISCONNECTED -> {
                txtConnectionStatus.setTextColor(Color.parseColor("#94A3B8"))
                btnConnect.text = "Conectar"
                btnConnect.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
            }
        }
    }

    private fun showSettingsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null)

        val txtRateLabel = dialogView.findViewById<TextView>(R.id.txtRateLabel)
        val seekRateHz = dialogView.findViewById<SeekBar>(R.id.seekRateHz)
        val txtMaxAngleLabel = dialogView.findViewById<TextView>(R.id.txtMaxAngleLabel)
        val seekMaxAngle = dialogView.findViewById<SeekBar>(R.id.seekMaxAngle)
        val txtSensitivityLabel = dialogView.findViewById<TextView>(R.id.txtSensitivityLabel)
        val seekSensitivity = dialogView.findViewById<SeekBar>(R.id.seekSensitivity)
        val txtDeadzoneLabel = dialogView.findViewById<TextView>(R.id.txtDeadzoneLabel)
        val seekDeadzone = dialogView.findViewById<SeekBar>(R.id.seekDeadzone)
        val txtExpoLabel = dialogView.findViewById<TextView>(R.id.txtExpoLabel)
        val seekExpo = dialogView.findViewById<SeekBar>(R.id.seekExpo)

        val chkInvertPitch = dialogView.findViewById<CheckBox>(R.id.chkInvertPitch)
        val chkInvertRoll = dialogView.findViewById<CheckBox>(R.id.chkInvertRoll)
        val chkInvertYaw = dialogView.findViewById<CheckBox>(R.id.chkInvertYaw)
        val chkEnableYaw = dialogView.findViewById<CheckBox>(R.id.chkEnableYaw)
        val chkLandscapeMode = dialogView.findViewById<CheckBox>(R.id.chkLandscapeMode)
        val txtLogsView = dialogView.findViewById<TextView>(R.id.txtLogsView)

        seekRateHz.progress = config.sendRateHz.coerceIn(20, 100)
        txtRateLabel.text = "Taxa de Envio UDP: ${seekRateHz.progress} Hz"

        seekMaxAngle.progress = config.maxDeflectionDeg.roundToInt().coerceIn(20, 80)
        txtMaxAngleLabel.text = "Ângulo Máximo de Inclinação: ${seekMaxAngle.progress}°"

        seekSensitivity.progress = (config.sensitivity * 100).roundToInt().coerceIn(25, 250)
        txtSensitivityLabel.text = String.format(Locale.US, "Sensibilidade Geral: %.2fx", seekSensitivity.progress / 100.0f)

        seekDeadzone.progress = (config.deadzone * 100).roundToInt().coerceIn(0, 30)
        txtDeadzoneLabel.text = "Zona Morta Central: ${seekDeadzone.progress}%"

        seekExpo.progress = (config.exponential * 100).roundToInt().coerceIn(0, 90)
        txtExpoLabel.text = "Curva Exponencial (Suavidade Centro): ${seekExpo.progress}%"

        chkInvertPitch.isChecked = config.invertPitch
        chkInvertRoll.isChecked = config.invertRoll
        chkInvertYaw.isChecked = config.invertYaw
        chkEnableYaw.isChecked = config.enableYaw
        chkLandscapeMode.isChecked = config.landscapeYokeMode
        txtLogsView.text = logger.getAllLinesText().ifBlank { "Sem eventos registrados ainda." }

        seekRateHz.setOnSeekBarChangeListener(simpleSeekListener { p ->
            val v = p.coerceAtLeast(20)
            txtRateLabel.text = "Taxa de Envio UDP: $v Hz"
        })
        seekMaxAngle.setOnSeekBarChangeListener(simpleSeekListener { p ->
            val v = p.coerceAtLeast(20)
            txtMaxAngleLabel.text = "Ângulo Máximo de Inclinação: ${v}°"
        })
        seekSensitivity.setOnSeekBarChangeListener(simpleSeekListener { p ->
            val v = p.coerceAtLeast(25) / 100.0f
            txtSensitivityLabel.text = String.format(Locale.US, "Sensibilidade Geral: %.2fx", v)
        })
        seekDeadzone.setOnSeekBarChangeListener(simpleSeekListener { p ->
            txtDeadzoneLabel.text = "Zona Morta Central: $p%"
        })
        seekExpo.setOnSeekBarChangeListener(simpleSeekListener { p ->
            txtExpoLabel.text = "Curva Exponencial (Suavidade Centro): $p%"
        })

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Salvar") { _, _ ->
                config.sendRateHz = seekRateHz.progress.coerceAtLeast(20)
                config.maxDeflectionDeg = seekMaxAngle.progress.coerceAtLeast(20).toFloat()
                config.sensitivity = seekSensitivity.progress.coerceAtLeast(25) / 100.0f
                config.deadzone = seekDeadzone.progress / 100.0f
                config.exponential = seekExpo.progress / 100.0f
                config.invertPitch = chkInvertPitch.isChecked
                config.invertRoll = chkInvertRoll.isChecked
                config.invertYaw = chkInvertYaw.isChecked
                config.enableYaw = chkEnableYaw.isChecked
                config.landscapeYokeMode = chkLandscapeMode.isChecked

                prefs.saveConfig(config)
                sensorController.currentConfig = config
                Toast.makeText(this, "Ajustes salvos!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Fechar", null)
            .show()
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

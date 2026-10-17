package com.flightjoy.android.control

import java.util.concurrent.atomic.AtomicInteger

/**
 * Mantém o estado atual dos controles do celular de forma thread-safe.
 */
class ControlState {
    @Volatile
    var pitch: Float = 0.0f

    @Volatile
    var roll: Float = 0.0f

    @Volatile
    var yaw: Float = 0.0f

    @Volatile
    var throttle: Float = -1.0f // -1.0f = 0% acelerador; +1.0f = 100% acelerador

    @Volatile
    var aux: Float = 0.0f // 0.0f = neutro

    private val buttonsMask = AtomicInteger(0)

    fun setButtonPressed(buttonIndexOneBased: Int, pressed: Boolean) {
        if (buttonIndexOneBased !in 1..16) return
        val bit = 1 shl (buttonIndexOneBased - 1)
        while (true) {
            val current = buttonsMask.get()
            val updated = if (pressed) (current or bit) else (current and bit.inv())
            if (buttonsMask.compareAndSet(current, updated)) {
                break
            }
        }
    }

    fun getButtonsMask(): Int = buttonsMask.get()

    fun clearAllButtons() {
        buttonsMask.set(0)
    }
}

package com.flightjoy.android.control

import java.util.concurrent.atomic.AtomicInteger

/**
 * Thread-safe holder for all current mobile flight control states.
 */
class ControlState {
    @Volatile
    var pitch: Float = 0.0f

    @Volatile
    var roll: Float = 0.0f

    @Volatile
    var yaw: Float = 0.0f

    @Volatile
    var rudderSlider: Float = 0.0f

    @Volatile
    var throttle: Float = -1.0f // -1.0f = 0% throttle; +1.0f = 100% throttle

    @Volatile
    var reverseThrottle: Float = -1.0f // -1.0f = 0% reverse; +1.0f = 100% reverse

    @Volatile
    var brakeSlider: Float = -1.0f // -1.0f = 0% brake; +1.0f = 100% brake

    val combinedRudder: Float
        get() = (rudderSlider + yaw).coerceIn(-1.0f, 1.0f)

    private val buttonsMask = AtomicInteger(0)

    /**
     * Sets or clears a target vJoy button (1..32).
     */
    fun setButtonPressed(vjoyButtonOneBased: Int, pressed: Boolean) {
        if (vjoyButtonOneBased !in 1..32) return
        val bit = 1 shl (vjoyButtonOneBased - 1)
        while (true) {
            val current = buttonsMask.get()
            val updated = if (pressed) (current or bit) else (current and bit.inv())
            if (buttonsMask.compareAndSet(current, updated)) {
                break
            }
        }
    }

    fun isButtonPressed(vjoyButtonOneBased: Int): Boolean {
        if (vjoyButtonOneBased !in 1..32) return false
        val bit = 1 shl (vjoyButtonOneBased - 1)
        return (buttonsMask.get() and bit) != 0
    }

    fun getButtonsMask(): Int = buttonsMask.get()

    fun clearAllButtons() {
        buttonsMask.set(0)
    }
}

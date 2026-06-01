package com.beackers.procon

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

/**
 * IME surface for controller-driven text input.
 *
 * The input view intentionally stays small so it behaves like an overlay above
 * the system navigation bar while hardware controller events are routed through
 * the selected input method. The overlay reports the current button state and
 * leaves actual text-entry mapping to the controller input layer.
 */
class ProconInputMethodService : InputMethodService(), SensorEventListener {
    private val pressedButtons = linkedSetOf<String>()
    private val joystickStates = linkedMapOf<Joystick, JoystickState>()
    private var gyroState: GyroState? = null
    private var registeredGyroManager: SensorManager? = null
    private var registeredGyroDeviceId: Int? = null
    private var statusText: TextView? = null

    override fun onCreateInputView(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(getColor(R.color.overlay_background))

            addView(
                TextView(context).also { label ->
                    statusText = label
                    label.gravity = Gravity.CENTER
                    label.setTextColor(getColor(R.color.overlay_text))
                    label.textSize = 20f
                    label.text = getString(R.string.overlay_idle)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        pressedButtons.clear()
        joystickStates.clear()
        gyroState = null
        updateOverlay()
    }

    override fun onFinishInput() {
        pressedButtons.clear()
        joystickStates.clear()
        gyroState = null
        updateOverlay()
        super.onFinishInput()
    }

    override fun onDestroy() {
        unregisterGyroscope()
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        event?.device?.let(::registerGyroscopeIfAvailable)

        val buttonLabel = keyCode.toControllerButtonLabel()
        if (buttonLabel != null) {
            pressedButtons.add(buttonLabel)
            updateOverlay()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        event?.device?.let(::registerGyroscopeIfAvailable)

        val buttonLabel = keyCode.toControllerButtonLabel()
        if (buttonLabel != null) {
            pressedButtons.remove(buttonLabel)
            updateOverlay()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isJoystickEvent()) {
            event.device?.let(::registerGyroscopeIfAvailable)
            updateJoystickState(event)
            updateOverlay()
            return true
        }

        return super.onGenericMotionEvent(event)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GYROSCOPE || event.values.size < GYRO_AXIS_COUNT) {
            return
        }

        gyroState = GyroState(
            x = event.values[0],
            y = event.values[1],
            z = event.values[2],
        )
        updateOverlay()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun updateJoystickState(event: MotionEvent) {
        val device = event.device ?: return
        val leftX = event.centeredAxisValue(device, MotionEvent.AXIS_X)
        val leftY = event.centeredAxisValue(device, MotionEvent.AXIS_Y)
        val rightX = event.centeredAxisValue(device, MotionEvent.AXIS_Z)
        val rightY = event.centeredAxisValue(device, MotionEvent.AXIS_RZ)

        joystickStates.update(Joystick.LEFT, leftX, leftY)
        joystickStates.update(Joystick.RIGHT, rightX, rightY)
    }

    private fun LinkedHashMap<Joystick, JoystickState>.update(joystick: Joystick, x: Float, y: Float) {
        val magnitude = hypot(x, y).coerceAtMost(1f)
        if (magnitude <= JOYSTICK_IDLE_THRESHOLD) {
            remove(joystick)
            return
        }

        put(
            joystick,
            JoystickState(
                direction = joystickDirection(x, y),
                magnitude = magnitude,
            ),
        )
    }

    private fun registerGyroscopeIfAvailable(device: InputDevice) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || device.id == registeredGyroDeviceId) {
            return
        }

        unregisterGyroscope()

        val sensorManager = device.sensorManager
        val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) ?: return
        if (sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME)) {
            registeredGyroManager = sensorManager
            registeredGyroDeviceId = device.id
        }
    }

    private fun unregisterGyroscope() {
        registeredGyroManager?.unregisterListener(this)
        registeredGyroManager = null
        registeredGyroDeviceId = null
    }

    private fun updateOverlay() {
        val lines = buildList {
            if (pressedButtons.isNotEmpty()) {
                add(getString(R.string.overlay_pressed, pressedButtons.joinToString(separator = " + ")))
            }

            joystickStates.forEach { (joystick, state) ->
                add(
                    getString(
                        R.string.overlay_joystick_state,
                        joystick.label,
                        state.direction,
                        state.magnitude.asPercent(),
                    ),
                )
            }

            gyroState?.let { state ->
                add(
                    getString(
                        R.string.overlay_gyro_state,
                        state.x,
                        state.y,
                        state.z,
                    ),
                )
            }
        }

        statusText?.text = lines.takeIf { it.isNotEmpty() }?.joinToString(separator = "\n")
            ?: getString(R.string.overlay_idle)
    }

    private fun MotionEvent.isJoystickEvent(): Boolean {
        return (source and InputDevice.SOURCE_CLASS_JOYSTICK) == InputDevice.SOURCE_CLASS_JOYSTICK
    }

    private fun MotionEvent.centeredAxisValue(device: InputDevice, axis: Int): Float {
        val range = device.getMotionRange(axis, source) ?: return 0f
        val flat = max(range.flat, JOYSTICK_IDLE_THRESHOLD)
        val value = getAxisValue(axis)
        return if (abs(value) > flat) value else 0f
    }

    private fun joystickDirection(x: Float, y: Float): String {
        val angle = Math.toDegrees(atan2(-y.toDouble(), x.toDouble()))
        val normalizedAngle = (angle + FULL_CIRCLE_DEGREES) % FULL_CIRCLE_DEGREES
        val directionIndex = ((normalizedAngle + HALF_DIRECTION_SLICE_DEGREES) / DIRECTION_SLICE_DEGREES).toInt() %
            JOYSTICK_DIRECTIONS.size
        return JOYSTICK_DIRECTIONS[directionIndex]
    }

    private fun Float.asPercent(): String = "${(this * 100).toInt()}%"

    private fun Int.toControllerButtonLabel(): String? = when (this) {
        KeyEvent.KEYCODE_BUTTON_A -> "A"
        KeyEvent.KEYCODE_BUTTON_B -> "B"
        KeyEvent.KEYCODE_BUTTON_X -> "X"
        KeyEvent.KEYCODE_BUTTON_Y -> "Y"
        KeyEvent.KEYCODE_BUTTON_L1 -> "L"
        KeyEvent.KEYCODE_BUTTON_R1 -> "R"
        KeyEvent.KEYCODE_BUTTON_L2 -> "ZL"
        KeyEvent.KEYCODE_BUTTON_R2 -> "ZR"
        KeyEvent.KEYCODE_BUTTON_THUMBL -> "Left Stick"
        KeyEvent.KEYCODE_BUTTON_THUMBR -> "Right Stick"
        KeyEvent.KEYCODE_BUTTON_START -> "+"
        KeyEvent.KEYCODE_BUTTON_SELECT -> "-"
        KeyEvent.KEYCODE_BUTTON_MODE -> "Home"
        KeyEvent.KEYCODE_DPAD_UP -> "D-Pad Up"
        KeyEvent.KEYCODE_DPAD_DOWN -> "D-Pad Down"
        KeyEvent.KEYCODE_DPAD_LEFT -> "D-Pad Left"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "D-Pad Right"
        KeyEvent.KEYCODE_DPAD_CENTER -> "D-Pad Center"
        else -> null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private enum class Joystick(val label: String) {
        LEFT("Left stick"),
        RIGHT("Right stick"),
    }

    private data class JoystickState(
        val direction: String,
        val magnitude: Float,
    )

    private data class GyroState(
        val x: Float,
        val y: Float,
        val z: Float,
    )

    private companion object {
        const val GYRO_AXIS_COUNT = 3
        const val JOYSTICK_IDLE_THRESHOLD = 0.05f
        const val FULL_CIRCLE_DEGREES = 360.0
        const val DIRECTION_SLICE_DEGREES = 45.0
        const val HALF_DIRECTION_SLICE_DEGREES = DIRECTION_SLICE_DEGREES / 2

        val JOYSTICK_DIRECTIONS = listOf(
            "Right",
            "Up-right",
            "Up",
            "Up-left",
            "Left",
            "Down-left",
            "Down",
            "Down-right",
        )
    }
}

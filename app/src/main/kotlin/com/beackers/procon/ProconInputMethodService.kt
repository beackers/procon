package com.beackers.procon

import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView

/**
 * IME surface for controller-driven text input.
 *
 * The input view intentionally stays small so it behaves like an overlay above
 * the system navigation bar while hardware controller events are routed through
 * the selected input method. The overlay reports the current button state and
 * leaves actual text-entry mapping to the controller input layer.
 */
class ProconInputMethodService : InputMethodService() {
    private val pressedButtons = linkedSetOf<String>()
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
        updateOverlay()
    }

    override fun onFinishInput() {
        pressedButtons.clear()
        updateOverlay()
        super.onFinishInput()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val buttonLabel = keyCode.toControllerButtonLabel()
        if (buttonLabel != null) {
            pressedButtons.add(buttonLabel)
            updateOverlay()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val buttonLabel = keyCode.toControllerButtonLabel()
        if (buttonLabel != null) {
            pressedButtons.remove(buttonLabel)
            updateOverlay()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun updateOverlay() {
        statusText?.text = if (pressedButtons.isEmpty()) {
            getString(R.string.overlay_idle)
        } else {
            getString(R.string.overlay_pressed, pressedButtons.joinToString(separator = " + "))
        }
    }

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
}

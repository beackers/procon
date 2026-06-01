package com.beackers.procon

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.inputmethodservice.InputMethodService
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * IME surface for controller-driven text input.
 *
 * The input view stays transparent and non-visual while the candidate view acts
 * as a lightweight radial selector. Holding a mapped controller button chooses
 * a letter group, tilting the left stick highlights a sector in that group, and
 * releasing the stick commits the highlighted lowercase letter.
 */
class ProconInputMethodService : InputMethodService() {
    private val pressedLetterButtons = linkedSetOf<ControllerButton>()
    private var activeButton: ControllerButton? = null
    private var leftStickSelection: StickSelection? = null
    private var letterOverlay: LetterSectorOverlayView? = null

    override fun onCreateInputView(): View {
        return SpaceView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            minimumHeight = 0
        }
    }

    override fun onCreateCandidatesView(): View {
        return LetterSectorOverlayView(this).also { overlay ->
            letterOverlay = overlay
            overlay.visibility = View.GONE
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        clearInputState()
    }

    override fun onFinishInput() {
        clearInputState()
        super.onFinishInput()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val button = keyCode.toLetterButton() ?: return super.onKeyDown(keyCode, event)
        if (pressedLetterButtons.add(button)) {
            setActiveButton(button)
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val button = keyCode.toLetterButton() ?: return super.onKeyUp(keyCode, event)
        if (pressedLetterButtons.remove(button)) {
            if (activeButton == button) {
                setActiveButton(pressedLetterButtons.lastOrNull())
            } else {
                updateOverlay()
            }
        }
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!event.isJoystickEvent()) {
            return super.onGenericMotionEvent(event)
        }

        updateLeftStickSelection(event)
        return true
    }

    private fun clearInputState() {
        pressedLetterButtons.clear()
        activeButton = null
        leftStickSelection = null
        updateOverlay()
    }

    private fun setActiveButton(button: ControllerButton?) {
        activeButton = button
        recalculateSelectionForActiveButton()
        updateOverlay()
    }

    private fun recalculateSelectionForActiveButton() {
        val letters = activeButton?.letters
        val selection = leftStickSelection
        if (letters.isNullOrEmpty() || selection == null) {
            return
        }

        leftStickSelection = selection.copy(
            letterIndex = letterIndexForAngle(selection.angleDegrees, letters.size),
        )
    }

    private fun updateLeftStickSelection(event: MotionEvent) {
        val device = event.device ?: return
        val x = event.centeredAxisValue(device, MotionEvent.AXIS_X)
        val y = event.centeredAxisValue(device, MotionEvent.AXIS_Y)
        val magnitude = hypot(x, y).coerceAtMost(1f)

        if (magnitude <= JOYSTICK_IDLE_THRESHOLD) {
            commitCurrentSelection()
            leftStickSelection = null
            updateOverlay()
            return
        }

        val letters = activeButton?.letters
        leftStickSelection = if (letters.isNullOrEmpty()) {
            null
        } else {
            val angleDegrees = stickAngleDegrees(x, y)
            StickSelection(
                angleDegrees = angleDegrees,
                magnitude = magnitude,
                letterIndex = letterIndexForAngle(angleDegrees, letters.size),
            )
        }
        updateOverlay()
    }

    private fun commitCurrentSelection() {
        val button = activeButton ?: return
        val selection = leftStickSelection ?: return
        val letter = button.letters.getOrNull(selection.letterIndex) ?: return
        currentInputConnection?.commitText(letter.toString(), 1)
    }

    private fun updateOverlay() {
        val letters = activeButton?.letters.orEmpty()
        val shouldShow = leftStickSelection != null && letters.isNotEmpty()
        setCandidatesViewShown(shouldShow)
        letterOverlay?.updateState(
            letters = letters,
            selection = leftStickSelection,
        )
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

    private fun stickAngleDegrees(x: Float, y: Float): Float {
        val angle = Math.toDegrees(atan2(-y.toDouble(), x.toDouble()))
        return ((angle + FULL_CIRCLE_DEGREES) % FULL_CIRCLE_DEGREES).toFloat()
    }

    private fun letterIndexForAngle(angleDegrees: Float, letterCount: Int): Int {
        val sectorSize = FULL_CIRCLE_DEGREES / letterCount
        return ((angleDegrees + sectorSize / 2) / sectorSize).toInt() % letterCount
    }

    private fun Int.toLetterButton(): ControllerButton? = when (this) {
        KeyEvent.KEYCODE_BUTTON_A -> ControllerButton.A
        KeyEvent.KEYCODE_BUTTON_B -> ControllerButton.B
        KeyEvent.KEYCODE_BUTTON_X -> ControllerButton.X
        KeyEvent.KEYCODE_BUTTON_Y -> ControllerButton.Y
        else -> null
    }

    private enum class ControllerButton(val letters: List<Char>) {
        A(('a'..'f').toList()),
        B(('g'..'m').toList()),
        X(('n'..'s').toList()),
        Y(('t'..'z').toList()),
    }

    private data class StickSelection(
        val angleDegrees: Float,
        val magnitude: Float,
        val letterIndex: Int,
    )

    private class SpaceView(context: Context) : View(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), 0)
        }
    }

    private class LetterSectorOverlayView(context: Context) : View(context) {
        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(178, 32, 33, 36)
            style = Paint.Style.FILL
        }
        private val sectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(84, 255, 255, 255)
            style = Paint.Style.FILL
        }
        private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(130, 255, 255, 255)
            strokeWidth = dp(1).toFloat()
            style = Paint.Style.STROKE
        }
        private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = dp(4).toFloat()
            style = Paint.Style.STROKE
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = dp(18).toFloat()
        }
        private val selectedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = dp(24).toFloat()
        }
        private val textBounds = Rect()
        private val arrowHeadPath = Path()

        private var letters: List<Char> = emptyList()
        private var selection: StickSelection? = null

        init {
            setWillNotDraw(false)
            setBackgroundColor(Color.TRANSPARENT)
        }

        fun updateState(letters: List<Char>, selection: StickSelection?) {
            this.letters = letters
            this.selection = selection
            visibility = if (selection != null && letters.isNotEmpty()) View.VISIBLE else View.GONE
            invalidate()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val desiredHeight = dp(188)
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = resolveSize(desiredHeight, heightMeasureSpec)
            setMeasuredDimension(width, height)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val currentSelection = selection ?: return
            if (letters.isEmpty()) return

            val centerX = width / 2f
            val centerY = height / 2f
            val radius = min(width, height).toFloat() / 2f - dp(12)
            val sectorAngle = 360f / letters.size
            val selectedIndex = currentSelection.letterIndex.coerceIn(0, letters.lastIndex)

            canvas.drawCircle(centerX, centerY, radius, backgroundPaint)
            drawSelectedSector(canvas, centerX, centerY, radius, selectedIndex, sectorAngle)
            drawDividers(canvas, centerX, centerY, radius, sectorAngle)
            drawLetters(canvas, centerX, centerY, radius, selectedIndex, sectorAngle)
            drawArrow(canvas, centerX, centerY, radius, currentSelection)
        }

        private fun drawSelectedSector(
            canvas: Canvas,
            centerX: Float,
            centerY: Float,
            radius: Float,
            selectedIndex: Int,
            sectorAngle: Float,
        ) {
            val startAngle = selectedIndex * sectorAngle - sectorAngle / 2f
            canvas.drawArc(
                centerX - radius,
                centerY - radius,
                centerX + radius,
                centerY + radius,
                -startAngle,
                -sectorAngle,
                true,
                sectorPaint,
            )
        }

        private fun drawDividers(
            canvas: Canvas,
            centerX: Float,
            centerY: Float,
            radius: Float,
            sectorAngle: Float,
        ) {
            letters.indices.forEach { index ->
                val boundaryAngle = index * sectorAngle - sectorAngle / 2f
                val radians = boundaryAngle.toRadians()
                canvas.drawLine(
                    centerX,
                    centerY,
                    centerX + cos(radians).toFloat() * radius,
                    centerY - sin(radians).toFloat() * radius,
                    dividerPaint,
                )
            }
            canvas.drawCircle(centerX, centerY, radius, dividerPaint)
        }

        private fun drawLetters(
            canvas: Canvas,
            centerX: Float,
            centerY: Float,
            radius: Float,
            selectedIndex: Int,
            sectorAngle: Float,
        ) {
            letters.forEachIndexed { index, letter ->
                val radians = (index * sectorAngle).toRadians()
                val paint = if (index == selectedIndex) selectedTextPaint else textPaint
                val label = letter.toString()
                paint.getTextBounds(label, 0, label.length, textBounds)
                val labelRadius = radius * 0.72f
                val x = centerX + cos(radians).toFloat() * labelRadius
                val y = centerY - sin(radians).toFloat() * labelRadius - textBounds.exactCenterY()
                canvas.drawText(label, x, y, paint)
            }
        }

        private fun drawArrow(
            canvas: Canvas,
            centerX: Float,
            centerY: Float,
            radius: Float,
            selection: StickSelection,
        ) {
            val radians = selection.angleDegrees.toRadians()
            val length = radius * (0.25f + 0.45f * selection.magnitude.coerceIn(0f, 1f))
            val endX = centerX + cos(radians).toFloat() * length
            val endY = centerY - sin(radians).toFloat() * length
            canvas.drawLine(centerX, centerY, endX, endY, arrowPaint)

            val arrowHeadSize = dp(10).toFloat()
            val left = (selection.angleDegrees + 150f).toRadians()
            val right = (selection.angleDegrees - 150f).toRadians()
            arrowHeadPath.reset()
            arrowHeadPath.moveTo(endX, endY)
            arrowHeadPath.lineTo(
                endX + cos(left).toFloat() * arrowHeadSize,
                endY - sin(left).toFloat() * arrowHeadSize,
            )
            arrowHeadPath.moveTo(endX, endY)
            arrowHeadPath.lineTo(
                endX + cos(right).toFloat() * arrowHeadSize,
                endY - sin(right).toFloat() * arrowHeadSize,
            )
            canvas.drawPath(arrowHeadPath, arrowPaint)
        }

        private fun Float.toRadians(): Double = this * PI / 180.0

        private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    }

    private companion object {
        const val JOYSTICK_IDLE_THRESHOLD = 0.05f
        const val FULL_CIRCLE_DEGREES = 360f
    }
}

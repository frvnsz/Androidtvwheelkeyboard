package com.example.wheelkeyboard.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import com.example.wheelkeyboard.model.WheelKeyboardItem
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class WheelKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    enum class KeyboardMode { LETTERS, SYMBOLS }

    var wheelIndex: Int = 0
        private set

    private var mode: KeyboardMode = KeyboardMode.LETTERS
    private val items: List<WheelKeyboardItem>
        get() = when (mode) {
            KeyboardMode.LETTERS -> WheelKeyboardItem.letterOrder
            KeyboardMode.SYMBOLS -> WheelKeyboardItem.symbolOrder
        }

    private val outerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 30f; color = Color.argb(46, 118, 214, 255) }
    private val ringShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 28f; color = Color.rgb(12, 17, 28) }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 18f; color = Color.rgb(55, 68, 92); strokeCap = Paint.Cap.ROUND }
    private val innerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.argb(160, 154, 174, 205) }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.argb(95, 172, 190, 218) }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 31f; color = Color.rgb(117, 214, 255); strokeCap = Paint.Cap.ROUND }
    private val selectedHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 44f; color = Color.argb(72, 117, 214, 255); strokeCap = Paint.Cap.ROUND }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 25f; isFakeBoldText = true }
    private val smallTextPaint = Paint(textPaint).apply { color = Color.rgb(190, 202, 219); textSize = 14f; isFakeBoldText = false }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val centerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.rgb(93, 112, 148) }
    private val centerHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.argb(150, 178, 231, 255) }

    init {
        isFocusable = true
        isFocusableInTouchMode = false
        importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO
        minimumHeight = 300
    }

    fun rotate(direction: Int) {
        setWheelIndex((wheelIndex + direction).floorMod(items.size))
        playSoundEffect(SoundEffectConstants.CLICK)
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun setWheelIndex(index: Int) {
        wheelIndex = index.floorMod(items.size)
        invalidate()
    }

    fun toggleMode() {
        mode = when (mode) {
            KeyboardMode.LETTERS -> KeyboardMode.SYMBOLS
            KeyboardMode.SYMBOLS -> KeyboardMode.LETTERS
        }
        wheelIndex = 0
        invalidate()
    }

    fun setModeForCharacter(char: Char) {
        val preferredMode = if (WheelKeyboardItem.indexOfCharacter(char, WheelKeyboardItem.symbolOrder) != null) {
            KeyboardMode.SYMBOLS
        } else {
            KeyboardMode.LETTERS
        }
        mode = preferredMode
        WheelKeyboardItem.indexOfCharacter(char, items)?.let { wheelIndex = it }
        invalidate()
    }

    fun selectedItem(): WheelKeyboardItem = items[wheelIndex]

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = 320
        setMeasuredDimension(width, resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width * 0.36f, height * 0.39f)
        val centerRadius = radius * 0.52f
        centerPaint.shader = RadialGradient(
            cx,
            cy - centerRadius * 0.28f,
            centerRadius,
            intArrayOf(Color.rgb(55, 68, 93), Color.rgb(26, 33, 47)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )

        val sweep = 360f / items.size
        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawCircle(cx, cy, radius + 5f, outerGlowPaint)
        canvas.drawCircle(cx, cy, radius, ringShadowPaint)
        canvas.drawCircle(cx, cy, radius, ringPaint)
        drawTicks(canvas, cx, cy, radius, sweep)
        canvas.drawCircle(cx, cy, radius * 0.78f, innerRingPaint)
        canvas.drawArc(oval, -90f - sweep / 2f, sweep, false, selectedHaloPaint)
        canvas.drawArc(oval, -90f - sweep / 2f, sweep, false, selectedPaint)
        canvas.drawCircle(cx, cy, centerRadius, centerPaint)
        canvas.drawCircle(cx, cy, centerRadius, centerStrokePaint)
        canvas.drawCircle(cx, cy - centerRadius * 0.08f, centerRadius * 0.82f, centerHighlightPaint)

        items.forEachIndexed { index, item ->
            val angle = Math.toRadians((index * sweep - 90.0) - (wheelIndex * sweep))
            val labelRadius = radius
            val x = cx + cos(angle).toFloat() * labelRadius
            val y = cy + sin(angle).toFloat() * labelRadius + textPaint.textSize / 3f
            val paint = if (index == wheelIndex) textPaint else smallTextPaint
            canvas.drawText(shortLabel(item), x, y, paint)
        }
        canvas.drawText(items[wheelIndex].label, cx, cy + textPaint.textSize / 3f, textPaint)
    }

    private fun drawTicks(canvas: Canvas, cx: Float, cy: Float, radius: Float, sweep: Float) {
        items.indices.forEach { index ->
            val angle = Math.toRadians((index * sweep - 90.0) - (wheelIndex * sweep))
            val startRadius = radius - 18f
            val endRadius = radius - 7f
            canvas.drawLine(
                cx + cos(angle).toFloat() * startRadius,
                cy + sin(angle).toFloat() * startRadius,
                cx + cos(angle).toFloat() * endRadius,
                cy + sin(angle).toFloat() * endRadius,
                tickPaint
            )
        }
    }

    private fun shortLabel(item: WheelKeyboardItem): String = when (item) {
        is WheelKeyboardItem.Letter -> item.value.toString()
        is WheelKeyboardItem.Digit -> item.value.toString()
        is WheelKeyboardItem.Punctuation -> item.value.toString()
        WheelKeyboardItem.Space -> "␠"
        WheelKeyboardItem.Delete -> "⌫"
        WheelKeyboardItem.Search -> "⌕"
        WheelKeyboardItem.VoiceSearch -> "🎙"
        WheelKeyboardItem.Done -> "✓"
        WheelKeyboardItem.ClearText -> "Clear"
        WheelKeyboardItem.ToggleSymbols -> "&123"
        WheelKeyboardItem.ToggleLetters -> "ABC"
    }

    private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus
}

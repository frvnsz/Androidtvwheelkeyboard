package com.example.wheelkeyboard.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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
    var wheelIndex: Int = 0
        private set

    private val items = WheelKeyboardItem.fixedOrder
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 18f; color = Color.rgb(42, 51, 68) }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 26f; color = Color.rgb(117, 214, 255); strokeCap = Paint.Cap.ROUND }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 24f }
    private val smallTextPaint = Paint(textPaint).apply { color = Color.rgb(184, 193, 204); textSize = 14f }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(31, 38, 51) }

    init {
        isFocusable = true
        isFocusableInTouchMode = false
        importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO
        minimumHeight = 280
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

    fun selectedItem(): WheelKeyboardItem = items[wheelIndex]

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = 300
        setMeasuredDimension(width, resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width * 0.38f, height * 0.42f)
        canvas.drawCircle(cx, cy, radius, ringPaint)
        canvas.drawCircle(cx, cy, radius * 0.52f, centerPaint)

        val sweep = 360f / items.size
        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(oval, -90f - sweep / 2f, sweep, false, selectedPaint)

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

    private fun shortLabel(item: WheelKeyboardItem): String = when (item) {
        is WheelKeyboardItem.Letter -> item.value.toString()
        is WheelKeyboardItem.Digit -> item.value.toString()
        WheelKeyboardItem.Space -> "␠"
        WheelKeyboardItem.Delete -> "⌫"
        WheelKeyboardItem.Search -> "⌕"
        WheelKeyboardItem.VoiceSearch -> "🎙"
        WheelKeyboardItem.Done -> "✓"
        WheelKeyboardItem.ClearText -> "Clear"
    }

    private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus
}

package org.anandram.xwordapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class CrosswordKeyboardView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface Listener {
        fun onKeyPress(ch: Char)
        fun onBackspace()
        fun onDirectionToggle()
    }

    var listener: Listener? = null

    private val rows = listOf(
            listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
            listOf(DIRECTION_SYMBOL, "Z", "X", "C", "V", "B", "N", "M", BACKSPACE_SYMBOL)
    )

    private val keyGap = dp(4f)
    private val keyRadius = dp(6f)
    private val keyHeight = dp(48f)
    private val topPadding = dp(6f)
    private val bottomPadding = dp(6f)

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3D3D3D.toInt() }
    private val keyPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF666666.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        textSize = dp(20f)
    }
    private val specialTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFAAAAAA.toInt()
        textAlign = Paint.Align.CENTER
        textSize = dp(20f)
    }

    private data class Key(val label: String, val rect: RectF)

    private val keys = mutableListOf<Key>()
    private var pressedKey: Key? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = (keyHeight * rows.size + keyGap * (rows.size - 1)
                + topPadding + bottomPadding).toInt()
        setMeasuredDimension(
                resolveSize(suggestedMinimumWidth, widthMeasureSpec),
                height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutKeys(w, h)
    }

    private fun layoutKeys(width: Int, height: Int) {
        keys.clear()
        val usableWidth = width - keyGap * 2

        rows.forEachIndexed { row, labels ->
            val keyWidth = (usableWidth - keyGap * (labels.size - 1)) / labels.size
            val rowWidth = keyWidth * labels.size + keyGap * (labels.size - 1)
            val startX = keyGap + (usableWidth - rowWidth) / 2
            val top = topPadding + row * (keyHeight + keyGap)

            labels.forEachIndexed { index, label ->
                val left = startX + index * (keyWidth + keyGap)
                keys += Key(label, RectF(left, top, left + keyWidth, top + keyHeight))
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (key in keys) {
            val paint = if (key == pressedKey) keyPressedPaint else keyPaint
            canvas.drawRoundRect(key.rect, keyRadius, keyRadius, paint)

            val textPaint = if (isSpecialKey(key.label)) specialTextPaint else this.textPaint
            val centerX = key.rect.centerX()
            val centerY = key.rect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2
            canvas.drawText(key.label, centerX, centerY, textPaint)
        }
    }

    private fun isSpecialKey(label: String): Boolean =
            label == DIRECTION_SYMBOL || label == BACKSPACE_SYMBOL

    private fun keyAt(x: Float, y: Float): Key? =
            keys.firstOrNull { it.rect.contains(x, y) }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedKey = keyAt(event.x, event.y)
                invalidate()
                return pressedKey != null
            }
            MotionEvent.ACTION_MOVE -> {
                val newKey = keyAt(event.x, event.y)
                if (newKey != pressedKey) {
                    pressedKey = newKey
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val key = pressedKey
                pressedKey = null
                invalidate()
                if (key != null && key == keyAt(event.x, event.y)) {
                    when (key.label) {
                        DIRECTION_SYMBOL -> listener?.onDirectionToggle()
                        BACKSPACE_SYMBOL -> listener?.onBackspace()
                        else -> key.label.firstOrNull()?.let { listener?.onKeyPress(it) }
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedKey = null
                invalidate()
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    companion object {
        private const val DIRECTION_SYMBOL = "\u2194"
        private const val BACKSPACE_SYMBOL = "\u232B"
    }
}
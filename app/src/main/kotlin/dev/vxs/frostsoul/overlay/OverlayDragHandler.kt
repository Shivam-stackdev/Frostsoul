package dev.vxs.frostsoul.overlay

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs

/**
 * Handles drag gestures for the floating lyrics overlay.
 * Supports both locked (no-drag) and unlocked (draggable) modes.
 */
class OverlayDragHandler(
    private val windowManager: WindowManager,
    private val params: WindowManager.LayoutParams,
    private val onPositionChanged: (Int, Int) -> Unit
) : View.OnTouchListener {

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private val touchSlop = 16f // pixels

    var isLocked = false
        set(value) {
            field = value
            // When locked, make touch-through
            updateTouchThrough(value)
        }

    private fun updateTouchThrough(locked: Boolean) {
        if (locked) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        try {
            windowManager.updateViewLayout(params.token as? View ?: return, params)
        } catch (_: Exception) {}
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (isLocked) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY

                if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    isDragging = true
                }

                if (isDragging) {
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    windowManager.updateViewLayout(v, params)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    onPositionChanged(params.x, params.y)
                }
                return true
            }
        }
        return false
    }
}

package dev.vxs.frostsoul.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the overlay window using Android's WindowManager.
 */
@Singleton
class OverlayWindowManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var composeView: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var dragHandler: OverlayDragHandler? = null

    val isShowing: Boolean get() = composeView != null

    fun show(
        content: ComposeView,
        x: Int = 0,
        y: Int = 200,
        locked: Boolean = false,
        touchThrough: Boolean = false
    ) {
        if (composeView != null) return // Already showing

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    if (touchThrough && locked) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }

        params = layoutParams
        composeView = content

        dragHandler = OverlayDragHandler(windowManager, layoutParams) { newX, newY ->
            // Position saved via callback
        }.apply {
            isLocked = locked
        }

        content.setOnTouchListener(dragHandler)
        windowManager.addView(content, layoutParams)
    }

    fun hide() {
        composeView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        composeView = null
        params = null
        dragHandler = null
    }

    fun updatePosition(x: Int, y: Int) {
        params?.let { p ->
            p.x = x
            p.y = y
            composeView?.let {
                try {
                    windowManager.updateViewLayout(it, p)
                } catch (_: Exception) {}
            }
        }
    }

    fun updateLockedState(locked: Boolean, touchThrough: Boolean) {
        dragHandler?.isLocked = locked
        params?.let { p ->
            if (locked && touchThrough) {
                p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }
            composeView?.let {
                try {
                    windowManager.updateViewLayout(it, p)
                } catch (_: Exception) {}
            }
        }
    }
}

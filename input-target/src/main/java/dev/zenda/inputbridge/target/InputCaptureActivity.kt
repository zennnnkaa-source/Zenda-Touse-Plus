package dev.zenda.inputbridge.target

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/** Shows actual events delivered to this Activity; never fabricates event rows. */
class InputCaptureActivity : Activity() {
    private lateinit var eventText: TextView
    private lateinit var captureSurface: InputCaptureSurface
    private val rows = ArrayDeque<String>()
    private val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "InputBridge Test Target"
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(Color.rgb(12, 16, 26))
        }
        root.addView(TextView(this).apply {
            text = "InputBridge Test Target"
            textSize = 22f
            setTextColor(Color.rgb(235, 242, 255))
        })
        root.addView(TextView(this).apply {
            text = "Only events delivered to this test app are logged. No input is simulated by this screen."
            textSize = 13f
            setTextColor(Color.rgb(162, 177, 203))
            setPadding(0, dp(4), 0, dp(10))
        })
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply {
            text = "Capture pointer"
            setOnClickListener {
                captureSurface.requestFocus()
                if (android.os.Build.VERSION.SDK_INT >= 26) captureSurface.requestPointerCapture()
                appendEvent("Requested pointer capture.")
            }
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        buttons.addView(Button(this).apply {
            text = "Release"
            setOnClickListener {
                if (android.os.Build.VERSION.SDK_INT >= 26) captureSurface.releasePointerCapture()
                appendEvent("Requested pointer-capture release.")
            }
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        buttons.addView(Button(this).apply {
            text = "Clear"
            setOnClickListener { rows.clear(); renderRows() }
        }, LinearLayout.LayoutParams(0, dp(48), 0.7f))
        root.addView(buttons)

        captureSurface = InputCaptureSurface(this) { message -> appendEvent(message) }
        root.addView(captureSurface, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply { topMargin = dp(10); bottomMargin = dp(10) })

        val scroll = ScrollView(this)
        eventText = TextView(this).apply {
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.rgb(196, 218, 244))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            text = "Waiting for real input events…"
        }
        scroll.addView(eventText)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(190)
        ))
        setContentView(root)
        appendEvent("Target ready. Connect a physical keyboard, mouse or controller, or run a Lab probe.")
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val action = when (event.action) {
            KeyEvent.ACTION_DOWN -> "DOWN"
            KeyEvent.ACTION_UP -> "UP"
            KeyEvent.ACTION_MULTIPLE -> "MULTIPLE"
            else -> event.action.toString()
        }
        appendEvent("KEY $action keyCode=${event.keyCode} scan=${event.scanCode} repeat=${event.repeatCount} meta=${event.metaState} device=${event.deviceId} source=0x${event.source.toString(16)} eventTime=${event.eventTime}")
        return super.dispatchKeyEvent(event)
    }

    private fun appendEvent(message: String) {
        if (!::eventText.isInitialized) return
        val uptime = SystemClock.uptimeMillis()
        rows.addFirst("${formatter.format(Date())} +${uptime}ms  $message")
        while (rows.size > 120) rows.removeLast()
        renderRows()
    }

    private fun renderRows() {
        if (::eventText.isInitialized) eventText.text = rows.joinToString("\n")
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private class InputCaptureSurface(context: android.content.Context, private val log: (String) -> Unit) : View(context) {
        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(20, 28, 43) }
        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(42, 58, 82); strokeWidth = 1.5f }
        private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(103, 211, 193); strokeWidth = 3f }
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(192, 208, 234); textSize = 34f }

        init {
            isFocusable = true
            isFocusableInTouchMode = true
            contentDescription = "Input event capture test surface"
            if (android.os.Build.VERSION.SDK_INT >= 24) pointerIcon = PointerIcon.getSystemIcon(context, PointerIcon.TYPE_CROSSHAIR)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 24f, 24f, backgroundPaint)
            val step = (32 * resources.displayMetrics.density).coerceAtLeast(24f)
            var x = step
            while (x < width) { canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint); x += step }
            var y = step
            while (y < height) { canvas.drawLine(0f, y, width.toFloat(), y, gridPaint); y += step }
            canvas.drawLine(width / 2f - 22, height / 2f, width / 2f + 22, height / 2f, crossPaint)
            canvas.drawLine(width / 2f, height / 2f - 22, width / 2f, height / 2f + 22, crossPaint)
            canvas.drawText("Tap / drag here", 20f, 44f, labelPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val action = when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> "DOWN"
                MotionEvent.ACTION_POINTER_DOWN -> "POINTER_DOWN"
                MotionEvent.ACTION_MOVE -> "MOVE"
                MotionEvent.ACTION_POINTER_UP -> "POINTER_UP"
                MotionEvent.ACTION_UP -> "UP"
                MotionEvent.ACTION_CANCEL -> "CANCEL"
                else -> "ACTION_${event.actionMasked}"
            }
            val pointers = (0 until event.pointerCount).joinToString(prefix = "[", postfix = "]") { index ->
                "id=${event.getPointerId(index)}@${event.getX(index).toInt()},${event.getY(index).toInt()}"
            }
            log("TOUCH $action pointers=${event.pointerCount} coords=$pointers source=0x${event.source.toString(16)} device=${event.deviceId} buttons=${event.buttonState} pressure=${"%.2f".format(Locale.US, event.pressure)} eventTime=${event.eventTime}")
            return true
        }

        override fun onGenericMotionEvent(event: MotionEvent): Boolean {
            if ((event.source and android.view.InputDevice.SOURCE_MOUSE) == android.view.InputDevice.SOURCE_MOUSE || event.action == MotionEvent.ACTION_SCROLL) {
                logMouseMotion(event, "GENERIC")
                return true
            }
            return super.onGenericMotionEvent(event)
        }

        override fun onCapturedPointerEvent(event: MotionEvent): Boolean {
            logMouseMotion(event, "CAPTURED")
            return true
        }

        private fun logMouseMotion(event: MotionEvent, delivery: String) {
            val dx = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
            val dy = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
            val wheelV = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            val wheelH = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
            log("MOTION delivery=$delivery action=${event.action} x=${event.x.toInt()} y=${event.y.toInt()} rel=(${"%.2f".format(Locale.US, dx)},${"%.2f".format(Locale.US, dy)}) scroll=(${"%.2f".format(Locale.US, wheelH)},${"%.2f".format(Locale.US, wheelV)}) source=0x${event.source.toString(16)} device=${event.deviceId} buttons=${event.buttonState} eventTime=${event.eventTime}")
        }

        override fun onPointerCaptureChange(hasCapture: Boolean) {
            super.onPointerCaptureChange(hasCapture)
            log("Pointer capture ${if (hasCapture) "ACQUIRED" else "RELEASED"} by Android.")
        }

    }
}

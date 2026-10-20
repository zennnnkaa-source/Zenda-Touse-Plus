package dev.zenda.inputbridge.target

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
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
    private lateinit var eventCountText: TextView
    private lateinit var captureSurface: InputCaptureSurface
    private val rows = ArrayDeque<String>()
    private val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Zenda Touse Plus Test Target"
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.rgb(10, 15, 28)
        window.navigationBarColor = Color.rgb(10, 15, 28)
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            setBackgroundColor(Color.rgb(10, 15, 28))
        }
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(28, 49, 82), Color.rgb(19, 32, 55), Color.rgb(17, 29, 44))
            ).apply { cornerRadius = dp(22).toFloat(); setStroke(dp(1), Color.rgb(53, 80, 117)) }
        }
        hero.addView(TextView(this).apply {
            text = "ZENDA  /  INPUT LAB"
            textSize = 10f
            letterSpacing = 0.18f
            setTextColor(Color.rgb(112, 224, 210))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        hero.addView(TextView(this).apply {
            text = "Zenda Touse Plus"
            textSize = 25f
            setTextColor(Color.rgb(245, 248, 255))
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            setPadding(0, dp(4), 0, 0)
        })
        hero.addView(TextView(this).apply {
            text = "Input verification target  ·  Live event monitor"
            textSize = 12f
            setTextColor(Color.rgb(178, 197, 223))
            setPadding(0, dp(3), 0, 0)
        })
        root.addView(hero, LinearLayout.LayoutParams(-1, -2))

        root.addView(TextView(this).apply {
            text = "TEST SURFACE"
            textSize = 10f
            letterSpacing = 0.14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(127, 151, 183))
            setPadding(dp(2), dp(15), 0, dp(6))
        })
        root.addView(TextView(this).apply {
            text = "Tap, drag, or connect a physical keyboard, mouse, or controller. Only events actually delivered to this app appear below."
            textSize = 12f
            setTextColor(Color.rgb(171, 188, 213))
            setPadding(dp(2), 0, dp(2), dp(10))
        })

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(makeButton("Capture pointer", Color.rgb(34, 179, 161), Color.rgb(8, 28, 36)) {
            captureSurface.requestFocus()
            if (android.os.Build.VERSION.SDK_INT >= 26) captureSurface.requestPointerCapture()
            appendEvent("Requested pointer capture.")
        }, LinearLayout.LayoutParams(0, dp(46), 1.25f).apply { marginEnd = dp(7) })
        buttons.addView(makeButton("Release", Color.rgb(36, 49, 72), Color.rgb(224, 234, 250)) {
            if (android.os.Build.VERSION.SDK_INT >= 26) captureSurface.releasePointerCapture()
            appendEvent("Requested pointer-capture release.")
        }, LinearLayout.LayoutParams(0, dp(46), 0.8f).apply { marginEnd = dp(7) })
        buttons.addView(makeButton("Clear log", Color.rgb(36, 49, 72), Color.rgb(224, 234, 250)) {
            rows.clear()
            renderRows()
        }, LinearLayout.LayoutParams(0, dp(46), 0.8f))
        root.addView(buttons)

        captureSurface = InputCaptureSurface(this) { message -> appendEvent(message) }
        captureSurface.background = roundedPanel(Color.rgb(17, 27, 44), Color.rgb(47, 67, 96), 20)
        root.addView(captureSurface, LinearLayout.LayoutParams(-1, dp(220)).apply {
            topMargin = dp(12)
            bottomMargin = dp(12)
        })

        val logHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(8))
        }
        logHeader.addView(TextView(this).apply {
            text = "LIVE EVENT STREAM"
            textSize = 10f
            letterSpacing = 0.12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(127, 151, 183))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        eventCountText = TextView(this).apply {
            text = "0 EVENTS"
            textSize = 10f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(112, 224, 210))
            setPadding(dp(9), dp(5), dp(9), dp(5))
            background = roundedPanel(Color.rgb(19, 53, 58), Color.rgb(40, 104, 104), 20)
        }
        logHeader.addView(eventCountText)
        root.addView(logHeader)

        val logPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedPanel(Color.rgb(14, 22, 37), Color.rgb(39, 55, 79), 18)
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        eventText = TextView(this).apply {
            textSize = 11f
            typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.NORMAL)
            setTextColor(Color.rgb(194, 215, 240))
            setLineSpacing(dp(3).toFloat(), 1f)
            text = "Waiting for real input events…"
        }
        scroll.addView(eventText)
        logPanel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(logPanel, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        appendEvent("Target ready. Waiting for real input events.")
    }

    private fun makeButton(label: String, backgroundColor: Int, textColor: Int, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            background = roundedPanel(backgroundColor, backgroundColor, 14)
            stateListAnimator = null
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(7), 0, dp(7), 0)
            setOnClickListener { action() }
        }

    private fun roundedPanel(fill: Int, stroke: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(1), stroke)
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
        if (::eventText.isInitialized) {
            eventText.text = if (rows.isEmpty()) "No events yet. Interact with the test surface or a connected input device." else rows.joinToString("\n")
        }
        if (::eventCountText.isInitialized) eventCountText.text = "${rows.size} EVENTS"
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

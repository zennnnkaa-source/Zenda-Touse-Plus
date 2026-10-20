package dev.zenda.inputbridge.lab.diagnostics

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Activity-scoped observer for input devices and events delivered to the app's own window.
 * It does not claim system-wide capture and never synthesizes diagnostic events.
 */
class InputDiagnosticsController(context: Context) : InputManager.InputDeviceListener {
    private val appContext = context.applicationContext
    private val inputManager = appContext.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val mutableState = MutableStateFlow(InputDiagnosticsSnapshot())
    val state: StateFlow<InputDiagnosticsSnapshot> = mutableState.asStateFlow()
    private var registered = false
    private var sensitiveTextEntryActive = false

    fun start() {
        synchronized(lock) {
            if (registered) return
            registered = true
            inputManager.registerInputDeviceListener(this, mainHandler)
            mutableState.value = mutableState.value.copy(
                devices = readDevices(),
                isMonitoring = true
            )
            appendEventLocked(
                category = "SYSTEM",
                summary = "Device monitoring started; events are captured only when delivered to this app.",
                deviceId = UNKNOWN_DEVICE_ID,
                source = 0,
                action = 0
            )
        }
    }

    fun stop() {
        synchronized(lock) {
            if (!registered) return
            registered = false
            inputManager.unregisterInputDeviceListener(this)
            mutableState.value = mutableState.value.copy(
                pressedKeys = DiagnosticsPolicy.releaseAllKeys(mutableState.value.pressedKeys),
                isMonitoring = false
            )
            appendEventLocked(
                category = "SYSTEM",
                summary = "Monitoring stopped; held-key state was reset to avoid stale keys.",
                deviceId = UNKNOWN_DEVICE_ID,
                source = 0,
                action = 0
            )
        }
    }

    fun setSensitiveTextEntryActive(active: Boolean) {
        synchronized(lock) {
            if (sensitiveTextEntryActive == active) return
            sensitiveTextEntryActive = active
            if (active) {
                mutableState.value = mutableState.value.copy(
                    pressedKeys = DiagnosticsPolicy.releaseAllKeys(mutableState.value.pressedKeys)
                )
            }
            if (registered) {
                appendEventLocked(
                    category = "PRIVACY",
                    summary = if (active) "Key-event recording paused while the pairing-code field is focused." else "Key-event recording resumed after leaving the pairing-code field.",
                    deviceId = UNKNOWN_DEVICE_ID,
                    source = 0,
                    action = 0
                )
            }
        }
    }

    fun recordKeyEvent(event: KeyEvent) {
        synchronized(lock) {
            if (!DiagnosticsPolicy.shouldRecordKeyEvent(registered, sensitiveTextEntryActive)) return
            val label = KeyEvent.keyCodeToString(event.keyCode)
            val actionLabel = when (event.action) {
                KeyEvent.ACTION_DOWN -> if (event.repeatCount > 0) "DOWN repeat" else "DOWN"
                KeyEvent.ACTION_UP -> "UP"
                KeyEvent.ACTION_MULTIPLE -> "MULTIPLE"
                else -> "UNKNOWN(${event.action})"
            }
            val deviceName = if (event.deviceId == UNKNOWN_DEVICE_ID) "virtual/unknown" else deviceName(event.deviceId)
            appendEventLocked(
                category = "KEY",
                summary = "$actionLabel · $label · device=$deviceName · repeat=${event.repeatCount} · meta=${event.metaState}",
                deviceId = event.deviceId,
                source = event.source,
                action = event.action
            )
            val key = PressedKey(event.deviceId, event.keyCode, label, event.repeatCount, event.metaState)
            val current = mutableState.value.pressedKeys
            val updated = when (event.action) {
                KeyEvent.ACTION_DOWN -> DiagnosticsPolicy.upsertPressedKey(current, key)
                KeyEvent.ACTION_UP -> DiagnosticsPolicy.releasePressedKey(current, event.deviceId, event.keyCode)
                else -> current
            }
            mutableState.value = mutableState.value.copy(pressedKeys = updated)
        }
    }

    fun recordMotionEvent(event: MotionEvent) {
        synchronized(lock) {
            if (!registered) return
            val axes = motionAxisValues(event)
            val deviceName = if (event.deviceId == UNKNOWN_DEVICE_ID) "virtual/unknown" else deviceName(event.deviceId)
            val actionName = MotionEvent.actionToString(event.action)
            val buttons = "buttons=0x${event.buttonState.toString(16)}"
            val summary = buildString {
                append(actionName)
                append(" · ").append(sourceName(event.source))
                append(" · device=").append(deviceName)
                append(" · pointers=").append(event.pointerCount)
                if (axes.isNotEmpty()) append(" · axes[p0]=").append(axes.joinToString(", "))
                append(" · ").append(buttons)
            }
            appendEventLocked(
                category = "MOTION",
                summary = summary.take(MAX_SUMMARY_LENGTH),
                deviceId = event.deviceId,
                source = event.source,
                action = event.action
            )
        }
    }

    override fun onInputDeviceAdded(deviceId: Int) = refreshDevices("Device attached: ${deviceName(deviceId)}", deviceId)

    override fun onInputDeviceRemoved(deviceId: Int) {
        synchronized(lock) {
            if (!registered) return
            mutableState.value = mutableState.value.copy(
                devices = readDevices(),
                pressedKeys = DiagnosticsPolicy.releaseDeviceKeys(mutableState.value.pressedKeys, deviceId)
            )
            appendEventLocked("DEVICE", "Device detached: id=$deviceId; held keys for this device were released.", deviceId, 0, 0)
        }
    }

    override fun onInputDeviceChanged(deviceId: Int) = refreshDevices("Device changed: ${deviceName(deviceId)}", deviceId)

    private fun refreshDevices(message: String, deviceId: Int) {
        synchronized(lock) {
            if (!registered) return
            mutableState.value = mutableState.value.copy(devices = readDevices())
            appendEventLocked("DEVICE", message, deviceId, 0, 0)
        }
    }

    private fun readDevices(): List<InputDeviceDescriptor> = InputDevice.getDeviceIds().mapNotNull { id ->
        val device = InputDevice.getDevice(id) ?: return@mapNotNull null
        val ranges = device.motionRanges.map { range ->
            InputAxisDescriptor(
                axis = range.axis,
                axisName = MotionEvent.axisToString(range.axis),
                source = range.source,
                sourceName = sourceName(range.source),
                min = range.min,
                max = range.max,
                flat = range.flat,
                fuzz = range.fuzz,
                resolution = range.resolution
            )
        }
        InputDeviceDescriptor(
            id = device.id,
            name = device.name,
            descriptor = device.descriptor,
            sources = device.sources,
            vendorId = device.vendorId,
            productId = device.productId,
            keyboardType = device.keyboardType,
            isVirtual = device.isVirtual,
            motionRanges = ranges
        )
    }.sortedBy { it.name.lowercase() }

    private fun motionAxisValues(event: MotionEvent): List<String> {
        val device = InputDevice.getDevice(event.deviceId)
        val reportedAxes = device?.motionRanges
            ?.asSequence()
            ?.filter { it.source == event.source || (it.source and event.source) == it.source }
            ?.map { it.axis }
            ?.distinct()
            ?.toList()
            .orEmpty()
        val priorityAxes = listOf(
            MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_RELATIVE_X,
            MotionEvent.AXIS_RELATIVE_Y, MotionEvent.AXIS_VSCROLL, MotionEvent.AXIS_HSCROLL,
            MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y, MotionEvent.AXIS_RX,
            MotionEvent.AXIS_RY, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ
        )
        val selectedAxes = (priorityAxes.filter { it in reportedAxes } + reportedAxes.filterNot { it in priorityAxes })
            .distinct()
            .take(DiagnosticsPolicy.MAX_AXES_PER_EVENT)
            .ifEmpty { priorityAxes.take(8) }
        return selectedAxes.mapNotNull { axis ->
            val value = event.getAxisValue(axis)
            if (!value.isFinite()) null else "${MotionEvent.axisToString(axis)}=${format(value)}"
        }.take(DiagnosticsPolicy.MAX_AXES_PER_EVENT)
    }

    private fun appendEventLocked(category: String, summary: String, deviceId: Int, source: Int, action: Int) {
        val current = mutableState.value
        val event = InputDiagnosticEvent(
            timestampMillis = System.currentTimeMillis(),
            category = category,
            summary = summary.take(MAX_SUMMARY_LENGTH),
            deviceId = deviceId,
            source = source,
            action = action
        )
        val events = DiagnosticsPolicy.appendBounded(current.events, event)
        val dropped = if (current.events.size >= DiagnosticsPolicy.MAX_EVENTS) current.droppedEventCount + 1 else current.droppedEventCount
        mutableState.value = mutableState.value.copy(events = events, droppedEventCount = dropped)
    }

    private fun deviceName(deviceId: Int): String = InputDevice.getDevice(deviceId)?.name ?: "device#$deviceId"

    private fun sourceName(source: Int): String = when {
        (source and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD -> "KEYBOARD"
        (source and InputDevice.SOURCE_MOUSE_RELATIVE) == InputDevice.SOURCE_MOUSE_RELATIVE -> "MOUSE_RELATIVE"
        (source and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE -> "MOUSE"
        (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK -> "JOYSTICK"
        (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD -> "GAMEPAD"
        (source and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD -> "DPAD"
        (source and InputDevice.SOURCE_TRACKBALL) == InputDevice.SOURCE_TRACKBALL -> "TRACKBALL"
        (source and InputDevice.SOURCE_TOUCHSCREEN) == InputDevice.SOURCE_TOUCHSCREEN -> "TOUCHSCREEN"
        source == 0 -> "UNKNOWN"
        else -> "SOURCE_0x${source.toString(16)}"
    }

    private fun format(value: Float): String = "%.3f".format(java.util.Locale.US, value)

    companion object {
        private const val MAX_SUMMARY_LENGTH = 500
        private const val UNKNOWN_DEVICE_ID = -1
    }
}

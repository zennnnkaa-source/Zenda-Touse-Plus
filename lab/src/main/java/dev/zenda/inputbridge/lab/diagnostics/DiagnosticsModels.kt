package dev.zenda.inputbridge.lab.diagnostics

/** A public-API-only snapshot of an input device. Values are observations, not capability promises. */
data class InputDeviceDescriptor(
    val id: Int,
    val name: String,
    val descriptor: String,
    val sources: Int,
    val vendorId: Int,
    val productId: Int,
    val keyboardType: Int,
    val isVirtual: Boolean,
    val motionRanges: List<InputAxisDescriptor>
)

data class InputAxisDescriptor(
    val axis: Int,
    val axisName: String,
    val source: Int,
    val sourceName: String,
    val min: Float,
    val max: Float,
    val flat: Float,
    val fuzz: Float,
    val resolution: Float
)

data class InputDiagnosticEvent(
    val timestampMillis: Long,
    val category: String,
    val summary: String,
    val deviceId: Int,
    val source: Int,
    val action: Int
)

data class PressedKey(
    val deviceId: Int,
    val keyCode: Int,
    val label: String,
    val repeatCount: Int,
    val metaState: Int
)

data class InputDiagnosticsSnapshot(
    val devices: List<InputDeviceDescriptor> = emptyList(),
    val events: List<InputDiagnosticEvent> = emptyList(),
    val pressedKeys: List<PressedKey> = emptyList(),
    val isMonitoring: Boolean = false,
    val droppedEventCount: Long = 0
)

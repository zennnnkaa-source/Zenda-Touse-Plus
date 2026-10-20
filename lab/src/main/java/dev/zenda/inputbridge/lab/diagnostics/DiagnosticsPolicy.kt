package dev.zenda.inputbridge.lab.diagnostics

import kotlin.math.abs

/** Pure state and numeric policy kept independent of Android classes for deterministic tests. */
object DiagnosticsPolicy {
    const val MAX_EVENTS = 200
    const val MAX_AXES_PER_EVENT = 12

    fun shouldRecordKeyEvent(monitoring: Boolean, sensitiveTextEntryActive: Boolean): Boolean =
        monitoring && !sensitiveTextEntryActive

    fun appendBounded(
        current: List<InputDiagnosticEvent>,
        event: InputDiagnosticEvent,
        maxEvents: Int = MAX_EVENTS
    ): List<InputDiagnosticEvent> {
        require(maxEvents > 0)
        return (current + event).takeLast(maxEvents)
    }

    fun upsertPressedKey(current: List<PressedKey>, key: PressedKey): List<PressedKey> {
        val withoutSameKey = current.filterNot { it.deviceId == key.deviceId && it.keyCode == key.keyCode }
        return withoutSameKey + key
    }

    fun releasePressedKey(current: List<PressedKey>, deviceId: Int, keyCode: Int): List<PressedKey> =
        current.filterNot { it.deviceId == deviceId && it.keyCode == keyCode }

    fun releaseDeviceKeys(current: List<PressedKey>, deviceId: Int): List<PressedKey> =
        current.filterNot { it.deviceId == deviceId }

    fun releaseAllKeys(current: List<PressedKey>): List<PressedKey> = current.takeIf { it.isEmpty() } ?: emptyList()

    /** Normalize a centered axis into [-1, 1], accounting for asymmetric min/max ranges. */
    fun normalizeCentered(
        value: Float,
        minValue: Float,
        maxValue: Float,
        deadZone: Float = 0f,
        invert: Boolean = false,
        sensitivity: Float = 1f
    ): Float {
        if (!value.isFinite() || !minValue.isFinite() || !maxValue.isFinite() || maxValue <= minValue) return 0f
        val dead = safeDeadZone(deadZone)
        val range = maxValue - minValue
        if (!range.isFinite()) return 0f
        val center = minValue + range / 2f
        val halfRange = if (value >= center) maxValue - center else center - minValue
        if (halfRange <= 0f) return 0f
        var normalized = ((value - center) / halfRange).coerceIn(-1f, 1f)
        if (abs(normalized) <= dead) return 0f
        normalized = (normalized.signPreservingDeadZone(dead) / (1f - dead)).coerceIn(-1f, 1f)
        if (invert) normalized = -normalized
        return (normalized * safeSensitivity(sensitivity)).coerceIn(-1f, 1f)
    }

    /** Normalize an unipolar control (e.g. a trigger) into [0, 1]. */
    fun normalizeUnipolar(
        value: Float,
        minValue: Float,
        maxValue: Float,
        deadZone: Float = 0f,
        invert: Boolean = false,
        sensitivity: Float = 1f
    ): Float {
        if (!value.isFinite() || !minValue.isFinite() || !maxValue.isFinite() || maxValue <= minValue) return 0f
        val range = maxValue - minValue
        if (!range.isFinite()) return 0f
        val dead = safeDeadZone(deadZone)
        var normalized = ((value - minValue) / range).coerceIn(0f, 1f)
        normalized = if (normalized <= dead) 0f else ((normalized - dead) / (1f - dead)).coerceIn(0f, 1f)
        if (invert) normalized = 1f - normalized
        return (normalized * safeSensitivity(sensitivity)).coerceIn(0f, 1f)
    }

    fun transformPair(
        x: Float,
        y: Float,
        swapAxes: Boolean = false,
        invertX: Boolean = false,
        invertY: Boolean = false,
        sensitivity: Float = 1f,
        deadZone: Float = 0f
    ): Pair<Float, Float> {
        val inputX = if (swapAxes) y else x
        val inputY = if (swapAxes) x else y
        val outX = normalizeCentered(inputX, -1f, 1f, deadZone, invertX, sensitivity)
        val outY = normalizeCentered(inputY, -1f, 1f, deadZone, invertY, sensitivity)
        return outX to outY
    }

    private fun safeDeadZone(value: Float): Float = if (value.isFinite()) value.coerceIn(0f, 0.95f) else 0f

    private fun safeSensitivity(value: Float): Float = if (value.isFinite()) value.coerceIn(0f, 4f) else 1f

    private fun Float.signPreservingDeadZone(deadZone: Float): Float =
        if (this < 0f) this + deadZone else this - deadZone
}

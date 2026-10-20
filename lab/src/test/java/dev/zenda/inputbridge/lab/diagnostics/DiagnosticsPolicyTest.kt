package dev.zenda.inputbridge.lab.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsPolicyTest {
    @Test
    fun keyCaptureIsPausedForSensitivePairingCodeEntry() {
        assertTrue(DiagnosticsPolicy.shouldRecordKeyEvent(monitoring = true, sensitiveTextEntryActive = false))
        assertFalse(DiagnosticsPolicy.shouldRecordKeyEvent(monitoring = true, sensitiveTextEntryActive = true))
        assertFalse(DiagnosticsPolicy.shouldRecordKeyEvent(monitoring = false, sensitiveTextEntryActive = false))
    }

    @Test
    fun eventLogIsBoundedAndKeepsNewestEvents() {
        val events = (1..5).map { event(it.toLong()) }
        val result = events.fold(emptyList<InputDiagnosticEvent>()) { acc, item ->
            DiagnosticsPolicy.appendBounded(acc, item, maxEvents = 3)
        }
        assertEquals(listOf(3L, 4L, 5L), result.map { it.timestampMillis })
        assertEquals(3, result.size)
    }

    @Test
    fun keyDownRepeatUpAndDeviceRemovalMaintainCorrectPressedState() {
        var pressed = emptyList<PressedKey>()
        pressed = DiagnosticsPolicy.upsertPressedKey(pressed, key(deviceId = 4, code = 29, repeat = 0))
        pressed = DiagnosticsPolicy.upsertPressedKey(pressed, key(deviceId = 4, code = 29, repeat = 2))
        pressed = DiagnosticsPolicy.upsertPressedKey(pressed, key(deviceId = 7, code = 29, repeat = 0))
        assertEquals(2, pressed.size)
        assertEquals(2, pressed.first { it.deviceId == 4 }.repeatCount)
        pressed = DiagnosticsPolicy.releasePressedKey(pressed, deviceId = 4, keyCode = 29)
        assertEquals(listOf(7), pressed.map { it.deviceId })
        pressed = DiagnosticsPolicy.releaseDeviceKeys(pressed, deviceId = 7)
        assertTrue(pressed.isEmpty())
    }

    @Test
    fun releaseAllClearsStaleKeys() {
        assertTrue(DiagnosticsPolicy.releaseAllKeys(listOf(key(1, 29), key(1, 30))).isEmpty())
        assertTrue(DiagnosticsPolicy.releaseAllKeys(emptyList()).isEmpty())
    }

    @Test
    fun centeredAxisNormalizesAsymmetricRangeAndClamps() {
        assertEquals(-1f, DiagnosticsPolicy.normalizeCentered(-32768f, -32768f, 32767f), 0.001f)
        assertEquals(1f, DiagnosticsPolicy.normalizeCentered(32767f, -32768f, 32767f), 0.001f)
        assertEquals(0f, DiagnosticsPolicy.normalizeCentered(Float.NaN, -1f, 1f), 0f)
        assertEquals(0f, DiagnosticsPolicy.normalizeCentered(2f, 1f, 1f), 0f)
        assertTrue(DiagnosticsPolicy.normalizeCentered(0.5f, -1f, 1f, deadZone = Float.NaN).isFinite())
        assertTrue(DiagnosticsPolicy.normalizeCentered(0.5f, -1f, 1f, sensitivity = Float.NaN).isFinite())
    }

    @Test
    fun centeredAxisSupportsDeadZoneInversionAndSensitivity() {
        assertEquals(0f, DiagnosticsPolicy.normalizeCentered(0.08f, -1f, 1f, deadZone = 0.1f), 0.001f)
        assertEquals(-0.5f, DiagnosticsPolicy.normalizeCentered(0.5f, -1f, 1f, invert = true), 0.001f)
        assertEquals(1f, DiagnosticsPolicy.normalizeCentered(0.8f, -1f, 1f, sensitivity = 4f), 0.001f)
        assertTrue(DiagnosticsPolicy.normalizeCentered(-0.8f, -1f, 1f, sensitivity = 4f) >= -1f)
    }

    @Test
    fun unipolarAxisMapsRangeAndHandlesInversion() {
        assertEquals(0f, DiagnosticsPolicy.normalizeUnipolar(0f, 0f, 1f), 0.001f)
        assertEquals(0.5f, DiagnosticsPolicy.normalizeUnipolar(0.5f, 0f, 1f), 0.001f)
        assertEquals(1f, DiagnosticsPolicy.normalizeUnipolar(1f, 0f, 1f), 0.001f)
        assertEquals(0.75f, DiagnosticsPolicy.normalizeUnipolar(0.25f, 0f, 1f, invert = true), 0.001f)
        assertEquals(1f, DiagnosticsPolicy.normalizeUnipolar(0f, 0f, 1f, invert = true), 0.001f)
        assertFalse(DiagnosticsPolicy.normalizeUnipolar(Float.POSITIVE_INFINITY, 0f, 1f) > 0f)
    }

    @Test
    fun pairTransformCanSwapAndInvertAxes() {
        val (x, y) = DiagnosticsPolicy.transformPair(0.25f, -0.75f, swapAxes = true, invertX = true)
        assertEquals(0.75f, x, 0.001f)
        assertEquals(0.25f, y, 0.001f)
    }

    private fun event(timestamp: Long) = InputDiagnosticEvent(timestamp, "KEY", "test", 1, 0, 0)
    private fun key(deviceId: Int, code: Int, repeat: Int = 0) = PressedKey(deviceId, code, "KEYCODE_$code", repeat, 0)
}

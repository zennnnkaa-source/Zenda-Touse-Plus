package dev.zenda.inputbridge.lab.adb

import android.system.Os
import java.util.concurrent.TimeUnit

/** Shared, deliberately small privileged operation set used by both Phase 1 entry paths. */
object InputProbeCommandExecutor {
    fun identity(): String = "UID ${Os.getuid()}"

    fun tap(x: Int, y: Int): String {
        require(x in 0..12000 && y in 0..12000) { "Coordinates are outside the safety range." }
        return executeInput(listOf("tap", x.toString(), y.toString()))
    }

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): String {
        require(listOf(x1, y1, x2, y2).all { it in 0..12000 }) { "Coordinates are outside the safety range." }
        require(durationMs in 50..2000) { "Swipe duration must be 50–2000 ms." }
        return executeInput(listOf("swipe", x1.toString(), y1.toString(), x2.toString(), y2.toString(), durationMs.toString()))
    }

    private fun executeInput(arguments: List<String>): String {
        val process = ProcessBuilder(listOf("/system/bin/input") + arguments)
            .redirectErrorStream(true)
            .start()
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return "TIMEOUT"
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim().take(240)
        return "exit=${process.exitValue()}${if (output.isNotBlank()) " output=$output" else ""}"
    }
}

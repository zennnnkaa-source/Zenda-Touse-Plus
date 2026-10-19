package dev.zenda.inputbridge.lab.adb

/** Validation boundary for values, shell strings, and local helper requests. */
object ProbeInputPolicy {
    fun isValidPort(port: Int): Boolean = port in 1..65535

    fun isValidHelperPort(port: Int): Boolean = port in 30000..59999

    fun isValidPairingCode(code: String): Boolean = code.matches(Regex("\\d{6}"))

    fun isAllowedCommand(command: String): Boolean =
        command == "id" ||
            command == "pm path dev.zenda.inputbridge.lab" ||
            command.matches(Regex("CLASSPATH=/data/app/[A-Za-z0-9_./~=-]+/base\\.apk /system/bin/app_process /system/bin dev\\.zenda\\.inputbridge\\.lab\\.adb\\.AdbBootstrapHelperMain (?:3[0-9]{4}|4[0-9]{4}|5[0-9]{4}) [0-9a-f]{64} </dev/null >/dev/null 2>&1 &"))

    /** A strict, bounded protocol. Only numeric single gestures can be requested. */
    fun isAllowedHelperRequest(command: String): Boolean {
        if (command == "UID" || command == "STOP") return true
        val parts = command.split(' ')
        if (parts.any { it.isEmpty() || !it.matches(Regex("[0-9]+")) && it !in setOf("TAP", "SWIPE") }) return false
        return when {
            parts.size == 3 && parts[0] == "TAP" -> {
                val x = parts[1].toIntOrNull() ?: return false
                val y = parts[2].toIntOrNull() ?: return false
                x in 0..12000 && y in 0..12000
            }
            parts.size == 6 && parts[0] == "SWIPE" -> {
                val coordinates = parts.subList(1, 5).map { it.toIntOrNull() ?: return false }
                val duration = parts[5].toIntOrNull() ?: return false
                coordinates.all { it in 0..12000 } && duration in 50..2000
            }
            else -> false
        }
    }
}

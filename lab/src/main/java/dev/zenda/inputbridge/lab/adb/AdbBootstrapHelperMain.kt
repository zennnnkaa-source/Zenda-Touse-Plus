package dev.zenda.inputbridge.lab.adb

/** Entry point for the on-device app_process helper bootstrapped over ADB. */
object AdbBootstrapHelperMain {
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size != 2) return
        val port = args[0].toIntOrNull() ?: return
        val token = args[1]
        if (!ProbeInputPolicy.isValidHelperPort(port) || !token.matches(Regex("[0-9a-f]{64}"))) return
        try {
            LocalHelperServer(port, token).apply {
                start()
                awaitTermination()
            }
        } catch (_: Exception) {
            // Parent app sees failure through a bounded local-socket connection timeout.
            // Never log the ephemeral token or command arguments.
        }
    }
}

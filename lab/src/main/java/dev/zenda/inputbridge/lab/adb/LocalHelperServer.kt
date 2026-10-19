package dev.zenda.inputbridge.lab.adb

import android.os.SystemClock
import android.system.Os
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest

/**
 * Shared helper runtime for both activation paths. Shizuku starts this runtime
 * inside its UserService; direct Wireless debugging starts the same class via
 * app_process. Only fixed commands cross the loopback socket.
 */
class LocalHelperServer(private val port: Int, private val token: String) {
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var serverThread: Thread? = null
    @Volatile private var lastResult: String = "No input command has run."
    @Volatile private var stopRequested = false

    @Synchronized
    fun start() {
        require(ProbeInputPolicy.isValidHelperPort(port)) { "Helper port outside allowed range." }
        require(token.matches(Regex("[0-9a-f]{64}"))) { "Invalid helper session token." }
        check(serverSocket == null) { "Helper server already started." }
        val server = ServerSocket()
        server.reuseAddress = false
        server.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 4)
        server.soTimeout = 1000
        serverSocket = server
        val thread = Thread({ serve(server) }, "inputbridge-shared-helper-$port").apply { isDaemon = true }
        serverThread = thread
        thread.start()
    }

    fun getLastResult(): String = lastResult

    fun awaitTermination() {
        serverThread?.join()
    }

    @Synchronized
    fun stop() {
        stopRequested = true
        try { serverSocket?.close() } catch (_: Exception) { }
        serverSocket = null
    }

    private fun serve(server: ServerSocket) {
        val deadline = SystemClock.elapsedRealtime() + 5 * 60 * 1000L
        try {
            while (!stopRequested && SystemClock.elapsedRealtime() < deadline) {
                val client = try { server.accept() } catch (_: SocketTimeoutException) { continue }
                client.use clientUse@{ socket: Socket ->
                    socket.soTimeout = 7000
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                    val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
                    val receivedToken = reader.readLine() ?: return@clientUse
                    if (!constantTimeEquals(token, receivedToken)) {
                        writer.write("ERROR|authentication\n")
                        writer.flush()
                        return@clientUse
                    }
                    val command = reader.readLine() ?: return@clientUse
                    val response = when {
                        !ProbeInputPolicy.isAllowedHelperRequest(command) -> "ERROR|unsupported-command"
                        command == "UID" -> "UID|${Os.getuid()}"
                        command == "STOP" -> {
                            lastResult = "Helper stop requested."
                            "OK|stopping"
                        }
                        command.startsWith("TAP ") -> {
                            val parts = command.split(' ')
                            try {
                                InputProbeCommandExecutor.tap(parts[1].toInt(), parts[2].toInt())
                                    .also { lastResult = it }.let { "TAP|$it" }
                            } catch (e: Exception) {
                                "ERROR|${e.javaClass.simpleName}".also { lastResult = it }
                            }
                        }
                        command.startsWith("SWIPE ") -> {
                            val parts = command.split(' ')
                            try {
                                InputProbeCommandExecutor.swipe(
                                    parts[1].toInt(), parts[2].toInt(), parts[3].toInt(),
                                    parts[4].toInt(), parts[5].toInt()
                                ).also { lastResult = it }.let { "SWIPE|$it" }
                            } catch (e: Exception) {
                                "ERROR|${e.javaClass.simpleName}".also { lastResult = it }
                            }
                        }
                        else -> "ERROR|unsupported-command".also { lastResult = it }
                    }
                    writer.write(response)
                    writer.write("\n")
                    writer.flush()
                    if (command == "STOP") stopRequested = true
                }
            }
        } catch (_: Exception) {
            if (!stopRequested) lastResult = "Helper socket stopped after an unexpected I/O or lifecycle error."
        } finally {
            try { server.close() } catch (_: Exception) { }
            synchronized(this) { if (serverSocket === server) serverSocket = null }
        }
    }

    private fun constantTimeEquals(expected: String, supplied: String): Boolean =
        MessageDigest.isEqual(
            expected.toByteArray(Charsets.US_ASCII),
            supplied.toByteArray(Charsets.US_ASCII)
        )
}

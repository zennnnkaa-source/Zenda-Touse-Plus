package dev.zenda.inputbridge.lab.adb

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

/** Client for the short-lived helper created by the experimental ADB bootstrap. */
object LocalHelperClient {
    fun request(port: Int, token: String, command: String): String {
        require(port in 30000..59999)
        require(token.matches(Regex("[0-9a-f]{64}")))
        require(ProbeInputPolicy.isAllowedHelperRequest(command))
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 1200)
            socket.soTimeout = 7000
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            writer.write(token)
            writer.write("\n")
            writer.write(command)
            writer.write("\n")
            writer.flush()
            return reader.readLine() ?: throw IllegalStateException("Helper closed without a response")
        }
    }
}

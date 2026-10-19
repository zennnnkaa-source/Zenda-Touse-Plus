package dev.zenda.inputbridge.lab.shizuku

import android.system.Os
import dev.zenda.inputbridge.lab.IProbeService
import dev.zenda.inputbridge.lab.adb.LocalHelperServer
import dev.zenda.inputbridge.lab.adb.ProbeInputPolicy

/** Phase 1 Shizuku UserService: reports its real UID and runs the shared helper runtime. */
class ProbeUserService : IProbeService.Stub() {
    @Volatile private var socketHelper: LocalHelperServer? = null
    @Volatile private var lastResult: String = "No input command has run."

    override fun getIdentity(): String {
        val uid = Os.getuid()
        return when (uid) {
            0 -> "UID 0 (root) — reported by the helper process"
            2000 -> "UID 2000 (shell/ADB) — reported by the helper process"
            else -> "UID $uid — reported by the helper process"
        }
    }

    override fun getProcessUid(): Int = Os.getuid()

    @Synchronized
    override fun startSocketHelper(port: Int, sessionToken: String): String {
        if (!ProbeInputPolicy.isValidHelperPort(port)) return "ERROR|helper-port"
        if (!sessionToken.matches(Regex("[0-9a-f]{64}"))) return "ERROR|session-token"
        return try {
            socketHelper?.stop()
            socketHelper = LocalHelperServer(port, sessionToken).also { it.start() }
            lastResult = "Shared local helper started; UID ${Os.getuid()}."
            "READY|${Os.getuid()}"
        } catch (e: Exception) {
            socketHelper = null
            "ERROR|${e.javaClass.simpleName}"
        }
    }

    override fun getLastInputResult(): String = socketHelper?.getLastResult() ?: lastResult

    @Synchronized
    override fun stopSocketHelper() {
        socketHelper?.stop()
        socketHelper = null
        lastResult = "Shared helper stop requested."
    }
}

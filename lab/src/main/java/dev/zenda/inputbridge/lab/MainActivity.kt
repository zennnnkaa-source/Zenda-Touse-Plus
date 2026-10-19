package dev.zenda.inputbridge.lab

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Point
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import dev.zenda.inputbridge.lab.adb.AdbConnectionManager
import dev.zenda.inputbridge.lab.adb.LocalHelperClient
import dev.zenda.inputbridge.lab.data.local.ProbeAction
import dev.zenda.inputbridge.lab.data.local.ProbeHistoryRepository
import dev.zenda.inputbridge.lab.data.local.ProbeOutcome
import dev.zenda.inputbridge.lab.data.local.ProbeProvider
import dev.zenda.inputbridge.lab.data.local.ProbeRunEntity
import dev.zenda.inputbridge.lab.data.preferences.UiPreferencesRepository
import dev.zenda.inputbridge.lab.shizuku.ProbeUserService
import dev.zenda.inputbridge.lab.ui.LabScreen
import dev.zenda.inputbridge.lab.ui.theme.InputBridgeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "inputbridge-lab-worker").apply { isDaemon = true }
    }
    private val shizukuStatus = mutableStateOf("Checking Shizuku binder…")
    private val helperStatus = mutableStateOf("Helper not bound")
    private val adbStatus = mutableStateOf("Not connected")
    private val darkThemeState = mutableStateOf(true)
    private val recentRuns = mutableStateOf<List<ProbeRunEntity>>(emptyList())
    private val uiPreferences by lazy { UiPreferencesRepository(applicationContext) }
    private val historyRepository by lazy { ProbeHistoryRepository(applicationContext) }
    @Volatile private var probeService: IProbeService? = null
    @Volatile private var adbManager: AdbConnectionManager? = null
    @Volatile private var connectedEndpoint: String? = null
    @Volatile private var directHelperPort: Int? = null
    @Volatile private var directHelperToken: String? = null
    @Volatile private var directHelperStarting = false
    @Volatile private var shizukuHelperPort: Int? = null
    @Volatile private var shizukuHelperToken: String? = null
    private var args: Shizuku.UserServiceArgs? = null

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_REQUEST_CODE) runOnUiThread {
            refreshShizukuStatus()
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            recordProbe(ProbeProvider.SHIZUKU, ProbeAction.PERMISSION_REQUEST, if (granted) ProbeOutcome.SUCCEEDED else ProbeOutcome.FAILED)
            helperStatus.value = if (granted) {
                "Permission granted; bind the probe helper next."
            } else "Permission denied by Shizuku."
        }
    }
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        runOnUiThread { refreshShizukuStatus() }
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        runOnUiThread {
            probeService = null
            shizukuHelperPort = null
            shizukuHelperToken = null
            helperStatus.value = "Shizuku binder died — bind again after restarting Shizuku."
            refreshShizukuStatus()
        }
    }
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            probeService = IProbeService.Stub.asInterface(service)
            recordProbe(ProbeProvider.SHIZUKU, ProbeAction.HELPER_BIND, ProbeOutcome.SUCCEEDED)
            worker.execute {
                val report = try {
                    val remote = probeService ?: throw IllegalStateException("Binder was empty")
                    "Bound helper reports ${remote.getIdentity()}"
                } catch (e: Exception) {
                    "Helper bound but identity probe failed: ${e.javaClass.simpleName}"
                }
                runOnUiThread { helperStatus.value = report }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            probeService = null
            shizukuHelperPort = null
            shizukuHelperToken = null
            recordProbe(ProbeProvider.SHIZUKU, ProbeAction.HELPER_BIND, ProbeOutcome.FAILED)
            runOnUiThread { helperStatus.value = "Helper disconnected. Bind again after checking Shizuku." }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        args = Shizuku.UserServiceArgs(ComponentName(this, ProbeUserService::class.java))
            .daemon(false).tag("inputbridge-phase1-probe").version(1)
        refreshShizukuStatus()
        lifecycleScope.launch {
            uiPreferences.darkThemeEnabled.collect { enabled -> darkThemeState.value = enabled }
        }
        lifecycleScope.launch {
            historyRepository.observeRecent(20).collect { records -> recentRuns.value = records }
        }
        setContent {
            InputBridgeTheme(darkTheme = darkThemeState.value) {
                LabScreen(
                    shizukuStatus = shizukuStatus.value,
                    helperStatus = helperStatus.value,
                    adbStatus = adbStatus.value,
                    recentRuns = recentRuns.value,
                    darkTheme = darkThemeState.value,
                    onToggleTheme = {
                        lifecycleScope.launch(Dispatchers.IO) { uiPreferences.toggleDarkTheme() }
                    },
                    onRefreshShizuku = { refreshShizukuStatus() },
                    onRequestShizukuPermission = { requestShizukuPermission() },
                    onBindHelper = { bindProbeHelper() },
                    onUnbindHelper = { unbindProbeHelper() },
                    onOpenTarget = { openTarget() },
                    onGetHelperResult = { refreshHelperResult() },
                    onShizukuTapProbe = { runShizukuTapProbe() },
                    onShizukuSwipeProbe = { runShizukuSwipeProbe() },
                    onPair = { host, port, code -> pairDirectly(host, port, code) },
                    onConnect = { host, port -> connectDirectly(host, port) },
                    onStartAdbHelper = { startDirectHelper() },
                    onStopAdbHelper = { stopDirectHelper() },
                    onDisconnect = { disconnectDirectly() },
                    onAdbTapProbe = { runAdbTapProbe() },
                    onAdbSwipeProbe = { runAdbSwipeProbe() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshShizukuStatus()
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        try { unbindProbeHelper() } catch (_: Exception) { }
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun refreshShizukuStatus() {
        val report = try {
            if (!Shizuku.pingBinder()) {
                "Not running / binder unavailable. Start Shizuku, then check again."
            } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                "Binder connected; Shizuku app permission required."
            } else {
                val uid = Shizuku.getUid()
                val identity = when (uid) {
                    0 -> "root UID 0"
                    2000 -> "shell/ADB UID 2000"
                    else -> "UID $uid"
                }
                "Binder connected; permission granted; backend reports $identity. Input injection is not yet verified."
            }
        } catch (e: Exception) {
            "Status check failed: ${e.javaClass.simpleName}. Retry after checking Shizuku."
        }
        shizukuStatus.value = report
    }

    private fun requestShizukuPermission() {
        try {
            if (!Shizuku.pingBinder()) {
                shizukuStatus.value = "Shizuku binder unavailable; start Shizuku first."
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                refreshShizukuStatus()
            } else {
                recordProbe(ProbeProvider.SHIZUKU, ProbeAction.PERMISSION_REQUEST, ProbeOutcome.STARTED)
                Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
            }
        } catch (e: Exception) {
            shizukuStatus.value = "Permission request failed: ${e.javaClass.simpleName}"
        }
    }

    private fun bindProbeHelper() {
        if (!Shizuku.pingBinder()) {
            helperStatus.value = "Cannot bind: Shizuku binder is unavailable."
            return
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            helperStatus.value = "Cannot bind: grant Shizuku app permission first."
            return
        }
        try {
            val userServiceArgs = args ?: throw IllegalStateException("UserService configuration missing")
            recordProbe(ProbeProvider.SHIZUKU, ProbeAction.HELPER_BIND, ProbeOutcome.STARTED)
            Shizuku.bindUserService(userServiceArgs, serviceConnection)
            helperStatus.value = "Bind requested; waiting for helper callback…"
        } catch (e: Exception) {
            recordProbe(ProbeProvider.SHIZUKU, ProbeAction.HELPER_BIND, ProbeOutcome.FAILED)
            helperStatus.value = "Bind failed: ${e.javaClass.simpleName}: ${e.message ?: "no details"}"
        }
    }

    private fun unbindProbeHelper() {
        val serviceArgs = args ?: return
        try { probeService?.stopSocketHelper() } catch (_: Exception) { }
        shizukuHelperPort = null
        shizukuHelperToken = null
        try { Shizuku.unbindUserService(serviceArgs, serviceConnection, true) } catch (_: Exception) { }
        probeService = null
        recordProbe(ProbeProvider.SHIZUKU, ProbeAction.HELPER_UNBIND, ProbeOutcome.SUCCEEDED)
        helperStatus.value = "Helper unbound and local helper stop requested."
    }

    private fun refreshHelperResult() {
        worker.execute {
            val message = try {
                probeService?.getLastInputResult() ?: "Helper is not bound."
            } catch (e: Exception) {
                "Helper result unavailable: ${e.javaClass.simpleName}"
            }
            runOnUiThread { helperStatus.value = message }
        }
    }

    private fun openTarget() {
        try {
            startActivity(Intent().setClassName(
                "dev.zenda.inputbridge.target",
                "dev.zenda.inputbridge.target.InputCaptureActivity"
            ))
            recordProbe(ProbeProvider.TEST_TARGET, ProbeAction.OPEN_TARGET, ProbeOutcome.SUCCEEDED)
        } catch (e: Exception) {
            recordProbe(ProbeProvider.TEST_TARGET, ProbeAction.OPEN_TARGET, ProbeOutcome.FAILED)
            adbStatus.value = "Target could not open. Install InputBridge Test Target APK beside the Lab."
        }
    }

    private fun centerCoordinates(): Pair<Int, Int> {
        val point = Point()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealSize(point)
        return (point.x / 2).coerceIn(0, 12000) to (point.y / 2).coerceIn(0, 12000)
    }

    private fun runShizukuTapProbe() {
        val point = centerCoordinates()
        runShizukuInputProbe("TAP ${point.first} ${point.second}", "one tap")
    }

    private fun runShizukuSwipeProbe() {
        val (x1, y, x2) = horizontalSwipeCoordinates()
        runShizukuInputProbe("SWIPE $x1 $y $x2 $y 500", "one 500 ms horizontal swipe")
    }

    private fun runShizukuInputProbe(command: String, description: String) {
        val remote = probeService
        if (remote == null) {
            helperStatus.value = "Helper is not bound. Complete Shizuku permission and bind first."
            return
        }
        val port = 30000 + SecureRandom().nextInt(30000)
        val tokenBytes = ByteArray(32)
        SecureRandom().nextBytes(tokenBytes)
        val token = tokenBytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        tokenBytes.fill(0)
        worker.execute {
            try {
                val started = remote.startSocketHelper(port, token)
                if (!started.startsWith("READY|")) {
                    throw IllegalStateException("Shizuku helper start failed: ${started.take(100)}")
                }
                val identity = LocalHelperClient.request(port, token, "UID")
                if (!identity.matches(Regex("UID\\|[0-9]+"))) {
                    throw IllegalStateException("Shared helper did not return a valid UID.")
                }
                shizukuHelperPort = port
                shizukuHelperToken = token
                runOnUiThread {
                    helperStatus.value = "Shared helper responds as ${identity.substringAfter('|')}; opening target for $description."
                    openTarget()
                }
                Thread.sleep(1700)
                val requestStart = SystemClock.elapsedRealtime()
                val result = LocalHelperClient.request(port, token, command)
                val roundTripMs = SystemClock.elapsedRealtime() - requestStart
                recordProbe(ProbeProvider.SHIZUKU, if (command.startsWith("TAP ")) ProbeAction.TAP_PROBE else ProbeAction.SWIPE_PROBE, ProbeOutcome.UNVERIFIED)
                runOnUiThread {
                    helperStatus.value = "$result · helper round-trip ${roundTripMs}ms (not end-to-end latency). Inspect the target event log; command completion alone is not an input-delivery PASS."
                }
            } catch (e: Exception) {
                shizukuHelperPort = null
                shizukuHelperToken = null
                recordProbe(ProbeProvider.SHIZUKU, if (command.startsWith("TAP ")) ProbeAction.TAP_PROBE else ProbeAction.SWIPE_PROBE, ProbeOutcome.FAILED)
                runOnUiThread {
                    helperStatus.value = "Shizuku helper probe failed: ${e.javaClass.simpleName}. Binder, local socket and target event delivery remain separate checks."
                }
            }
        }
    }

    private fun horizontalSwipeCoordinates(): Triple<Int, Int, Int> {
        val point = Point()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealSize(point)
        val centerX = point.x / 2
        val y = (point.y / 2).coerceIn(0, 12000)
        val offset = (point.x / 4).coerceAtLeast(1)
        val startX = (centerX - offset).coerceIn(0, 12000)
        val endX = (centerX + offset).coerceIn(0, 12000)
        return Triple(startX, y, endX)
    }

    private fun manager(): AdbConnectionManager {
        synchronized(this) {
            val existing = adbManager
            if (existing != null) return existing
            return AdbConnectionManager(applicationContext).also { adbManager = it }
        }
    }

    private fun pairDirectly(host: String, port: Int, code: String) {
        if (!code.matches(Regex("\\d{6}"))) {
            recordProbe(ProbeProvider.DIRECT_ADB, ProbeAction.PAIR, ProbeOutcome.FAILED)
            adbStatus.value = "Pairing failed: enter exactly six digits. Code was cleared from the form."
            return
        }
        worker.execute {
            val report = try {
                val ok = manager().pairNow(host.trim(), port, code)
                recordProbe(ProbeProvider.DIRECT_ADB, ProbeAction.PAIR, if (ok) ProbeOutcome.SUCCEEDED else ProbeOutcome.FAILED)
                if (ok) "Pairing succeeded; this does not prove a shell connection or input injection. Enter the current connection port next."
                else "Pairing failed. Confirm host, pairing port, code, and Wireless debugging pairing screen."
            } catch (e: Exception) {
                recordProbe(ProbeProvider.DIRECT_ADB, ProbeAction.PAIR, ProbeOutcome.FAILED)
                "Pairing failed: ${e.javaClass.simpleName}. Recheck host, pairing port and the temporary code."
            }
            runOnUiThread { adbStatus.value = report }
        }
    }

    private fun connectDirectly(host: String, port: Int) {
        worker.execute {
            val report = try {
                val client = manager()
                val requestedEndpoint = "${host.trim()}:$port"
                val currentEndpoint = connectedEndpoint
                if (client.isConnected && currentEndpoint != null && currentEndpoint != requestedEndpoint) {
                    "Already connected to $currentEndpoint. Disconnect before connecting to a different host/port."
                } else {
                    val connected = client.isConnected || client.connectNow(host.trim(), port)
                    if (!connected) "Connection failed. Use the current connection port (not the pairing port)."
                    else {
                        connectedEndpoint = requestedEndpoint
                        val output = client.runFixedProbeCommand("id")
                        if (output.isBlank()) "ADB connected but shell identity output was empty; injection is not verified."
                        else "ADB shell probe returned: ${output.take(300)}. Injection is not verified until the test target receives an event."
                    }
                }
            } catch (e: Exception) {
                "ADB connection/shell probe failed: ${e.javaClass.simpleName}. Check Wi‑Fi, current connection port and pairing state."
            }
            recordProbe(ProbeProvider.DIRECT_ADB, ProbeAction.CONNECT, if (report.startsWith("ADB shell probe returned:")) ProbeOutcome.SUCCEEDED else ProbeOutcome.FAILED)
            runOnUiThread { adbStatus.value = report }
        }
    }

    private fun disconnectDirectly() {
        worker.execute {
            val helperPort = directHelperPort
            val helperToken = directHelperToken
            directHelperPort = null
            directHelperToken = null
            if (helperPort != null && helperToken != null) {
                try { LocalHelperClient.request(helperPort, helperToken, "STOP") } catch (_: Exception) { }
            }
            val old = synchronized(this) { adbManager.also { adbManager = null } }
            try { old?.close() } catch (_: Exception) { }
            connectedEndpoint = null
            recordProbe(ProbeProvider.DIRECT_ADB, ProbeAction.DISCONNECT, ProbeOutcome.SUCCEEDED)
            runOnUiThread { adbStatus.value = "Disconnected; ADB session closed and helper stop attempted." }
        }
    }

    private fun startDirectHelper() {
        val client = adbManager
        if (client == null || !client.isConnected) {
            adbStatus.value = "Cannot bootstrap helper: establish the direct ADB connection first."
            return
        }
        if (directHelperStarting || (directHelperPort != null && directHelperToken != null)) {
            adbStatus.value = "An experimental helper session is starting or already running. Stop it before starting another."
            return
        }
        directHelperStarting = true
        val port = 30000 + SecureRandom().nextInt(30000)
        val tokenBytes = ByteArray(32)
        SecureRandom().nextBytes(tokenBytes)
        val token = tokenBytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        tokenBytes.fill(0)
        worker.execute {
            val report = try {
                client.bootstrapHelper(port, token)
                var response: String? = null
                var lastError: Exception? = null
                for (attempt in 0 until 12) {
                    try {
                        response = LocalHelperClient.request(port, token, "UID")
                        break
                    } catch (e: Exception) {
                        lastError = e
                        Thread.sleep(250)
                    }
                }
                if (response != null && response!!.matches(Regex("UID\\|[0-9]+"))) {
                    directHelperPort = port
                    directHelperToken = token
                    recordProbe(ProbeProvider.DIRECT_ADB, ProbeAction.HELPER_START, ProbeOutcome.SUCCEEDED)
                    val uid = response!!.substringAfter('|')
                    "Experimental app_process helper responded and reports UID $uid. Verify a tap in the target before treating this path as working."
                } else {
                    recordProbe(ProbeProvider.DIRECT_ADB, ProbeAction.HELPER_START, ProbeOutcome.FAILED)
                    "Bootstrap command was sent, but the helper socket did not return a valid UID (${lastError?.javaClass?.simpleName ?: "no response"}). A failed helper may remain idle for up to five minutes."
                }
            } catch (e: Exception) {
                recordProbe(ProbeProvider.DIRECT_ADB, ProbeAction.HELPER_START, ProbeOutcome.FAILED)
                "Helper bootstrap failed: ${e.javaClass.simpleName}. Pair/connect may work even if app_process or loopback IPC is blocked."
            } finally {
                directHelperStarting = false
            }
            runOnUiThread { adbStatus.value = report }
        }
    }

    private fun stopDirectHelper() {
        if (directHelperStarting) {
            adbStatus.value = "Helper bootstrap is still running; wait for its result before stopping it."
            return
        }
        val port = directHelperPort
        val token = directHelperToken
        directHelperPort = null
        directHelperToken = null
        if (port == null || token == null) {
            adbStatus.value = "No direct helper session is registered in this app process."
            return
        }
        worker.execute {
            val report = try {
                LocalHelperClient.request(port, token, "STOP")
                recordProbe(ProbeProvider.DIRECT_ADB, ProbeAction.HELPER_STOP, ProbeOutcome.SUCCEEDED)
                "Experimental helper stop requested; its local session token was cleared from app state."
            } catch (e: Exception) {
                recordProbe(ProbeProvider.DIRECT_ADB, ProbeAction.HELPER_STOP, ProbeOutcome.UNVERIFIED)
                "Could not confirm helper stop (${e.javaClass.simpleName}); its idle lifetime is bounded to five minutes."
            }
            runOnUiThread { adbStatus.value = report }
        }
    }

    private fun runAdbTapProbe() {
        val (x, y) = centerCoordinates()
        runAdbInputProbe("TAP $x $y", "one tap")
    }

    private fun runAdbSwipeProbe() {
        val (x1, y, x2) = horizontalSwipeCoordinates()
        runAdbInputProbe("SWIPE $x1 $y $x2 $y 500", "one 500 ms horizontal swipe")
    }

    private fun runAdbInputProbe(command: String, description: String) {
        val port = directHelperPort
        val token = directHelperToken
        if (port == null || token == null) {
            adbStatus.value = "Start the direct helper and verify its UID before testing $description."
            return
        }
        openTarget()
        worker.execute {
            val report = try {
                TimeUnit.MILLISECONDS.sleep(1800)
                val requestStart = SystemClock.elapsedRealtime()
                val output = LocalHelperClient.request(port, token, command)
                val roundTripMs = SystemClock.elapsedRealtime() - requestStart
                recordProbe(ProbeProvider.DIRECT_ADB, if (command.startsWith("TAP ")) ProbeAction.TAP_PROBE else ProbeAction.SWIPE_PROBE, ProbeOutcome.UNVERIFIED)
                "Local helper replied to $description: ${output.take(240)}; helper round-trip ${roundTripMs}ms (not end-to-end latency). Inspect the target event log; a response is not an automatic input-delivery PASS."
            } catch (e: Exception) {
                recordProbe(ProbeProvider.DIRECT_ADB, if (command.startsWith("TAP ")) ProbeAction.TAP_PROBE else ProbeAction.SWIPE_PROBE, ProbeOutcome.FAILED)
                "Direct helper $description failed: ${e.javaClass.simpleName}. Inspect app_process/loopback policy; input delivery remains unverified."
            }
            runOnUiThread { adbStatus.value = report }
        }
    }


    private fun recordProbe(provider: ProbeProvider, action: ProbeAction, outcome: ProbeOutcome) {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { historyRepository.record(provider, action, outcome) }
        }
    }

    companion object { private const val SHIZUKU_REQUEST_CODE = 7421 }
}


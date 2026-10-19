package dev.zenda.inputbridge.lab

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Point
import android.os.Bundle
import android.os.SystemClock
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import rikka.shizuku.Shizuku
import dev.zenda.inputbridge.lab.adb.AdbConnectionManager
import dev.zenda.inputbridge.lab.adb.LocalHelperClient
import dev.zenda.inputbridge.lab.shizuku.ProbeUserService
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
            helperStatus.value = if (grantResult == PackageManager.PERMISSION_GRANTED) {
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
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF78DCC8),
                secondary = Color(0xFF9EB7FF),
                background = Color(0xFF0B1020),
                surface = Color(0xFF141C2D),
                onBackground = Color(0xFFEAF0FF),
                onSurface = Color(0xFFEAF0FF)
            )) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LabScreen(
                        shizukuStatus = shizukuStatus.value,
                        helperStatus = helperStatus.value,
                        adbStatus = adbStatus.value,
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
            Shizuku.bindUserService(userServiceArgs, serviceConnection)
            helperStatus.value = "Bind requested; waiting for helper callback…"
        } catch (e: Exception) {
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
        } catch (e: Exception) {
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
                runOnUiThread {
                    helperStatus.value = "$result · helper round-trip ${roundTripMs}ms (not end-to-end latency). Inspect the target event log; command completion alone is not an input-delivery PASS."
                }
            } catch (e: Exception) {
                shizukuHelperPort = null
                shizukuHelperToken = null
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
            adbStatus.value = "Pairing failed: enter exactly six digits. Code was cleared from the form."
            return
        }
        worker.execute {
            val report = try {
                val ok = manager().pairNow(host.trim(), port, code)
                if (ok) "Pairing succeeded; this does not prove a shell connection or input injection. Enter the current connection port next."
                else "Pairing failed. Confirm host, pairing port, code, and Wireless debugging pairing screen."
            } catch (e: Exception) {
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
                    val uid = response!!.substringAfter('|')
                    "Experimental app_process helper responded and reports UID $uid. Verify a tap in the target before treating this path as working."
                } else {
                    "Bootstrap command was sent, but the helper socket did not return a valid UID (${lastError?.javaClass?.simpleName ?: "no response"}). A failed helper may remain idle for up to five minutes."
                }
            } catch (e: Exception) {
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
                "Experimental helper stop requested; its local session token was cleared from app state."
            } catch (e: Exception) {
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
                "Local helper replied to $description: ${output.take(240)}; helper round-trip ${roundTripMs}ms (not end-to-end latency). Inspect the target event log; a response is not an automatic input-delivery PASS."
            } catch (e: Exception) {
                "Direct helper $description failed: ${e.javaClass.simpleName}. Inspect app_process/loopback policy; input delivery remains unverified."
            }
            runOnUiThread { adbStatus.value = report }
        }
    }

    companion object { private const val SHIZUKU_REQUEST_CODE = 7421 }
}

@Composable
private fun LabScreen(
    shizukuStatus: String,
    helperStatus: String,
    adbStatus: String,
    onRefreshShizuku: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onBindHelper: () -> Unit,
    onUnbindHelper: () -> Unit,
    onOpenTarget: () -> Unit,
    onGetHelperResult: () -> Unit,
    onShizukuTapProbe: () -> Unit,
    onShizukuSwipeProbe: () -> Unit,
    onPair: (String, Int, String) -> Unit,
    onConnect: (String, Int) -> Unit,
    onStartAdbHelper: () -> Unit,
    onStopAdbHelper: () -> Unit,
    onDisconnect: () -> Unit,
    onAdbTapProbe: () -> Unit,
    onAdbSwipeProbe: () -> Unit
) {
    var host by remember { mutableStateOf("127.0.0.1") }
    var pairingPort by remember { mutableStateOf("") }
    var connectionPort by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("InputBridge", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFF78DCC8))
        Text("Zenda Touse Plus · Phase 1 Feasibility Lab", style = MaterialTheme.typography.titleMedium)
        Text("POC only. Pairing, shell access and input delivery are separate results; no game compatibility is implied.", color = Color(0xFFA8B5CD), style = MaterialTheme.typography.bodyMedium)

        LabCard("Option A · Shizuku (Primary)", shizukuStatus) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRefreshShizuku, modifier = Modifier.weight(1f)) { Text("Check status") }
                Button(onClick = onRequestShizukuPermission, modifier = Modifier.weight(1f)) { Text("Request permission") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onBindHelper, modifier = Modifier.weight(1f)) { Text("Bind helper") }
                Button(onClick = onUnbindHelper, modifier = Modifier.weight(1f)) { Text("Unbind") }
            }
            Text("Helper: $helperStatus", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB8C9E5))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onShizukuTapProbe, modifier = Modifier.weight(1f)) { Text("Test one tap") }
                Button(onClick = onShizukuSwipeProbe, modifier = Modifier.weight(1f)) { Text("Test one swipe") }
            }
            Button(onClick = onGetHelperResult, modifier = Modifier.fillMaxWidth()) { Text("Read helper result") }
        }

        LabCard("Option B · Direct Wireless debugging", adbStatus) {
            Text("Experimental. Keep both this app and Android's Pair device screen accessible. Use the current pair and connect ports; they are different and can change.", color = Color(0xFFA8B5CD), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value = host, onValueChange = { host = it.take(253) }, label = { Text("Device host / IP") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = pairingPort, onValueChange = { pairingPort = it.filter(Char::isDigit).take(5) }, label = { Text("Pairing port") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = connectionPort, onValueChange = { connectionPort = it.filter(Char::isDigit).take(5) }, label = { Text("Connection port") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(value = pairingCode, onValueChange = { pairingCode = it.filter(Char::isDigit).take(6) }, label = { Text("Temporary 6-digit pairing code") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                val code = pairingCode
                pairingCode = ""
                val port = pairingPort.toIntOrNull() ?: -1
                onPair(host, port, code)
            }, modifier = Modifier.fillMaxWidth(), enabled = pairingCode.length == 6 && (pairingPort.toIntOrNull()?.let { it in 1..65535 } == true)) { Text("Pair device") }
            Button(onClick = { onConnect(host, connectionPort.toIntOrNull() ?: -1) }, modifier = Modifier.fillMaxWidth(), enabled = connectionPort.toIntOrNull()?.let { it in 1..65535 } == true) { Text("Connect + verify shell id") }
            Button(onClick = onStartAdbHelper, modifier = Modifier.fillMaxWidth()) { Text("Bootstrap helper + verify UID") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAdbTapProbe, modifier = Modifier.weight(1f)) { Text("Test one tap") }
                Button(onClick = onAdbSwipeProbe, modifier = Modifier.weight(1f)) { Text("Test one swipe") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStopAdbHelper, modifier = Modifier.weight(1f)) { Text("Stop helper") }
                Button(onClick = onDisconnect, modifier = Modifier.weight(1f)) { Text("Disconnect") }
            }
        }

        LabCard("Test target", "Use this separate app to inspect events actually delivered to an Activity.") {
            Button(onClick = onOpenTarget, modifier = Modifier.fillMaxWidth()) { Text("Open InputBridge Test Target") }
        }
        Spacer(Modifier.height(12.dp))
        Text("Safety: never share the temporary pairing code. The Lab does not log the code, does not persist it, and only allows fixed shell probes and bounded numeric tap/swipe requests.", color = Color(0xFFA8B5CD), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LabCard(title: String, status: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141C2D))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Text(status, style = MaterialTheme.typography.bodyMedium)
            content()
        }
    }
}

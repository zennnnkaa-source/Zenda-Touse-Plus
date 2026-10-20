package dev.zenda.inputbridge.lab

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import dev.zenda.inputbridge.lab.adb.AdbConnectionManager
import dev.zenda.inputbridge.lab.diagnostics.InputDiagnosticsController
import dev.zenda.inputbridge.lab.diagnostics.InputDiagnosticsSnapshot
import dev.zenda.inputbridge.lab.adb.AdbPairingNotificationService
import dev.zenda.inputbridge.lab.adb.AdbDiscoveredEndpoint
import dev.zenda.inputbridge.lab.adb.AdbServiceDiscovery
import dev.zenda.inputbridge.lab.adb.AdbServiceKind
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
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "inputbridge-lab-worker").apply { isDaemon = true }
    }
    private val shizukuStatus = mutableStateOf("Checking Shizuku binder…")
    private val helperStatus = mutableStateOf("Helper not bound")
    private val adbStatus = mutableStateOf("Not connected")
    private val darkThemeState = mutableStateOf(true)
    private val recentRuns = mutableStateOf<List<ProbeRunEntity>>(emptyList())
    private val inputDiagnosticsSnapshot = mutableStateOf(InputDiagnosticsSnapshot())
    private val inputDiagnosticsController by lazy { InputDiagnosticsController(applicationContext) }
    private var originalInputDiagnosticsWindowCallback: Window.Callback? = null
    private var inputDiagnosticsWindowCallback: Window.Callback? = null
    private val directStage = mutableStateOf("idle")
    private val pairingEndpoint = mutableStateOf<AdbDiscoveredEndpoint?>(null)
    private val connectionServiceEndpoint = mutableStateOf<AdbDiscoveredEndpoint?>(null)
    private val openDirectTabRequest = mutableStateOf(false)
    private val directConnectAttempted = AtomicBoolean(false)
    private var directPairingSucceeded = false
    private var directDiscovery: AdbServiceDiscovery? = null
    private var directSettingsLaunched = false
    private var directSettingsWasPaused = false
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var localNetworkPermissionLauncher: ActivityResultLauncher<String>
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
    private var pairingResultReceiverRegistered = false
    private val pairingResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            val received = intent ?: return
            if (received.action !in setOf(AdbPairingNotificationService.ACTION_RESULT, AdbPairingNotificationService.ACTION_STATE)) return
            val success = received.action == AdbPairingNotificationService.ACTION_RESULT && received.getBooleanExtra(AdbPairingNotificationService.EXTRA_SUCCESS, false)
            val cancelled = received.action == AdbPairingNotificationService.ACTION_RESULT && received.getBooleanExtra(AdbPairingNotificationService.EXTRA_CANCELLED, false)
            val pairingAttempted = received.action == AdbPairingNotificationService.ACTION_RESULT && received.getBooleanExtra(AdbPairingNotificationService.EXTRA_PAIRING_ATTEMPTED, false)
            val message = received.getStringExtra(AdbPairingNotificationService.EXTRA_MESSAGE).orEmpty()
            runOnUiThread {
                adbStatus.value = message
                if (received.action == AdbPairingNotificationService.ACTION_STATE) {
                    directStage.value = "pairing"
                } else if (cancelled) {
                    directStage.value = "ready_for_code"
                } else if (success) {
                    directPairingSucceeded = true
                    directStage.value = "paired_waiting_connection"
                    if (pairingAttempted) recordProbe(ProbeProvider.DIRECT_ADB, ProbeAction.PAIR, ProbeOutcome.SUCCEEDED)
                    // Do not restart foreground Activity discovery while the system pairing Settings screen is still open.
                    if (!(directSettingsLaunched && directSettingsWasPaused)) {
                        ensureDirectDiscovery().start()
                        attemptDirectConnectionIfReady()
                    }
                } else {
                    directPairingSucceeded = false
                    directStage.value = "ready_for_code"
                    if (pairingAttempted) recordProbe(ProbeProvider.DIRECT_ADB, ProbeAction.PAIR, ProbeOutcome.FAILED)
                    if (!(directSettingsLaunched && directSettingsWasPaused)) ensureDirectDiscovery().restart()
                }
            }
        }
    }

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
        openDirectTabRequest.value = intent?.getBooleanExtra(EXTRA_OPEN_DIRECT_TAB, false) == true
        notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // Inline notification input is optional; the in-app code field remains the fallback.
            if (!granted) {
                adbStatus.value = "Notification permission denied. Use the in-app pairing field; notification input is unavailable."
            }
            requestLocalNetworkPermissionThenContinue()
        }
        localNetworkPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted && Build.VERSION.SDK_INT >= 36) {
                adbStatus.value = "Nearby devices permission was denied. Android 16 local-network restrictions may block mDNS/ADB; retry with permission enabled if discovery or connection fails."
            }
            continueDirectActivationAfterPermissions()
        }
        val pairingFilter = IntentFilter().apply {
            addAction(AdbPairingNotificationService.ACTION_RESULT)
            addAction(AdbPairingNotificationService.ACTION_STATE)
        }
        ContextCompat.registerReceiver(
            this,
            pairingResultReceiver,
            pairingFilter,
            AdbPairingNotificationService.internalBroadcastPermission(this),
            null,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        pairingResultReceiverRegistered = true
        ensureDirectNotificationChannel()
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        args = Shizuku.UserServiceArgs(ComponentName(this, ProbeUserService::class.java))
            .daemon(false).tag("inputbridge-probe").version(1)
        refreshShizukuStatus()
        lifecycleScope.launch {
            uiPreferences.darkThemeEnabled.collect { enabled -> darkThemeState.value = enabled }
        }
        lifecycleScope.launch {
            historyRepository.observeRecent(20).collect { records -> recentRuns.value = records }
        }
        lifecycleScope.launch {
            inputDiagnosticsController.state.collect { snapshot -> inputDiagnosticsSnapshot.value = snapshot }
        }
        setContent {
            InputBridgeTheme(darkTheme = darkThemeState.value) {
                LabScreen(
                    shizukuStatus = shizukuStatus.value,
                    helperStatus = helperStatus.value,
                    adbStatus = adbStatus.value,
                    recentRuns = recentRuns.value,
                    inputDiagnostics = inputDiagnosticsSnapshot.value,
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
                    onAdbSwipeProbe = { runAdbSwipeProbe() },
                    directStage = directStage.value,
                    pairingEndpointLabel = pairingEndpoint.value?.displayAddress.orEmpty(),
                    connectionEndpointLabel = connectionServiceEndpoint.value?.displayAddress.orEmpty(),
                    openDirectTabRequest = openDirectTabRequest.value,
                    onDirectTabRequestConsumed = { openDirectTabRequest.value = false },
                    onStartDirectActivation = { startDirectActivation() },
                    onRetryDirectDiscovery = { retryDirectDiscovery() },
                    onRetryDirectConnection = { retryDirectConnectionDiscovery() },
                    onPairDetectedCode = { code -> pairWithDiscoveredCode(code) },
                    onPairingCodeFocusChanged = { focused -> inputDiagnosticsController.setSensitiveTextEntryActive(focused) }
                )
            }
        }
        installInputDiagnosticsWindowCallback()
    }

    override fun onStart() {
        super.onStart()
        inputDiagnosticsController.start()
    }

    override fun onStop() {
        inputDiagnosticsController.stop()
        super.onStop()
    }

    /**
     * Observe events through the public Window.Callback interface instead of overriding
     * ComponentActivity's restricted dispatch methods. Delegation preserves the original
     * Activity callback and normal Android/Compose event routing.
     */
    private fun installInputDiagnosticsWindowCallback() {
        if (inputDiagnosticsWindowCallback != null) return
        val original = window.callback ?: return
        val observer = object : Window.Callback by original {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                inputDiagnosticsController.recordKeyEvent(event)
                return original.dispatchKeyEvent(event)
            }

            override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
                inputDiagnosticsController.recordMotionEvent(event)
                return original.dispatchGenericMotionEvent(event)
            }

            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                // Mouse button presses may use the touch dispatch path. Ignore ordinary
                // touchscreen gestures here to avoid polluting mouse/controller diagnostics.
                if ((event.source and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE) {
                    inputDiagnosticsController.recordMotionEvent(event)
                }
                return original.dispatchTouchEvent(event)
            }
        }
        originalInputDiagnosticsWindowCallback = original
        inputDiagnosticsWindowCallback = observer
        window.callback = observer
    }

    private fun restoreInputDiagnosticsWindowCallback() {
        val observer = inputDiagnosticsWindowCallback ?: return
        val original = originalInputDiagnosticsWindowCallback
        if (original != null && window.callback === observer) {
            window.callback = original
        }
        inputDiagnosticsWindowCallback = null
        originalInputDiagnosticsWindowCallback = null
    }

    override fun onResume() {
        super.onResume()
        refreshShizukuStatus()
        if (directSettingsLaunched && directSettingsWasPaused) {
            directSettingsLaunched = false
            directSettingsWasPaused = false
            // Do not overwrite a pairing state received from the notification while Settings was foregrounded.
            if (directStage.value in setOf("waiting_settings", "ready_for_code", "waiting_for_pairing")) {
                directStage.value = "ready_for_code"
                adbStatus.value = if (pairingEndpoint.value != null) {
                    "Pairing service detected. The Android code can be submitted from the notification or the in-app fallback."
                } else {
                    "Waiting for pairing details. Keep Android Wireless debugging → Pair device with pairing code open."
                }
            }
            ensureDirectDiscovery().start()
            if (pairingEndpoint.value != null && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                adbStatus.value = "Notifications are disabled; use the in-app six-digit code field."
            }
        }
    }

    override fun onPause() {
        if (directSettingsLaunched) {
            directSettingsWasPaused = true
            // The foreground pairing service owns background discovery; release the Activity's
            // duplicate browser/multicast lock while the system Settings screen is open.
            directDiscovery?.stopDiscovery()
        }
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_DIRECT_TAB, false)) {
            openDirectTabRequest.value = true
        }
    }

    override fun onDestroy() {
        restoreInputDiagnosticsWindowCallback()
        directDiscovery?.close()
        directDiscovery = null
        if (pairingResultReceiverRegistered) {
            try { unregisterReceiver(pairingResultReceiver) } catch (_: Exception) { }
            pairingResultReceiverRegistered = false
        }
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
            adbStatus.value = "Target could not open. Install the Zenda Touse Plus Test Target APK beside the main app."
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

    private fun startDirectActivation() {
        if (directStage.value in setOf("waiting_settings", "ready_for_code", "waiting_for_pairing")) {
            pairingEndpoint.value = null
            connectionServiceEndpoint.value = null
            directPairingSucceeded = false
            directConnectAttempted.set(false)
            requestLocalNetworkPermissionThenContinue()
            return
        }
        if (directStage.value in setOf("pairing", "paired_waiting_connection", "connecting", "connected", "helper_ready")) {
            ensureDirectDiscovery().start()
            return
        }
        pairingEndpoint.value = null
        connectionServiceEndpoint.value = null
        directPairingSucceeded = false
        directConnectAttempted.set(false)
        directStage.value = "waiting_settings"
        adbStatus.value = "Waiting for Android Wireless debugging pairing. Developer options will open next."
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestLocalNetworkPermissionThenContinue()
        }
    }

    /** Ask for Android 16's opt-in local-network compatibility permission before starting discovery. */
    private fun requestLocalNetworkPermissionThenContinue() {
        if (Build.VERSION.SDK_INT >= 36 &&
            checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED
        ) {
            localNetworkPermissionLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
            return
        }
        continueDirectActivationAfterPermissions()
    }

    private fun continueDirectActivationAfterPermissions() {
        if (Build.VERSION.SDK_INT >= 36 &&
            checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED
        ) {
            adbStatus.value = "Nearby devices permission is not granted. Android 16 local-network restrictions may block mDNS/ADB; enable the permission if discovery or connection fails."
        }
        // Re-start discovery after the permission decision so callbacks use the current access state.
        ensureDirectDiscovery().restart()
        startPairingNotificationServiceIfAllowed()
        launchDeveloperOptions()
    }

    private fun startPairingNotificationServiceIfAllowed() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            adbStatus.value = "Notification input is unavailable because notification permission is denied. Enter the code in the app instead."
            return
        }
        if (!AdbPairingNotificationService.areNotificationsAvailable(this)) {
            adbStatus.value = "InputBridge notifications or the pairing channel are disabled. Use the in-app six-digit code field or enable notifications in system Settings."
            return
        }
        try {
            AdbPairingNotificationService.start(this)
        } catch (error: Exception) {
            adbStatus.value = "Could not start notification pairing (${error.javaClass.simpleName}). Use the in-app code field instead."
        }
    }

    private fun launchDeveloperOptions() {
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        try {
            directSettingsLaunched = true
            directSettingsWasPaused = false
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            try {
                directSettingsLaunched = true
                directSettingsWasPaused = false
                startActivity(Intent(Settings.ACTION_SETTINGS))
                adbStatus.value = "Opened system Settings. Find Developer options → Wireless debugging → Pair device with pairing code."
            } catch (error: Exception) {
                directSettingsLaunched = false
                adbStatus.value = "Could not open Settings automatically (${error.javaClass.simpleName}). Open Developer options manually, then retry discovery."
                directStage.value = "ready_for_code"
            }
        } catch (error: Exception) {
            directSettingsLaunched = false
            adbStatus.value = "Could not open Developer options (${error.javaClass.simpleName}). Open it manually, then retry discovery."
            directStage.value = "ready_for_code"
        }
    }

    private fun retryDirectDiscovery() {
        pairingEndpoint.value = null
        connectionServiceEndpoint.value = null
        directStage.value = "ready_for_code"
        adbStatus.value = "Searching again for Android Wireless debugging pairing and connection services."
        ensureDirectDiscovery().restart()
    }

    private fun retryDirectConnectionDiscovery() {
        connectionServiceEndpoint.value = null
        directConnectAttempted.set(false)
        directStage.value = "paired_waiting_connection"
        adbStatus.value = "Pairing is already confirmed. Searching again for the current ADB connection service."
        ensureDirectDiscovery().restart()
    }

    private fun ensureDirectDiscovery(): AdbServiceDiscovery {
        val existing = directDiscovery
        if (existing != null) return existing
        return AdbServiceDiscovery(
            applicationContext,
            onEndpoint = { endpoint ->
                runOnUiThread {
                    when (endpoint.kind) {
                        AdbServiceKind.PAIRING -> {
                            pairingEndpoint.value = endpoint
                            if (directStage.value !in setOf("pairing", "paired_waiting_connection", "connecting", "connected", "helper_ready")) {
                                directStage.value = "ready_for_code"
                                adbStatus.value = "Pairing screen detected (${endpoint.displayAddress}). Enter the temporary six-digit code shown in Android Settings."
                                showDirectNotification("Pairing screen detected", "Return to InputBridge and enter the six-digit code from Android Settings.")
                            }
                        }
                        AdbServiceKind.CONNECTION -> {
                            connectionServiceEndpoint.value = endpoint
                            if (directPairingSucceeded) attemptDirectConnectionIfReady()
                        }
                    }
                }
            },
            onStatus = { message ->
                runOnUiThread {
                    if (directStage.value in setOf("waiting_settings", "waiting_for_pairing", "ready_for_code", "paired_waiting_connection")) {
                        adbStatus.value = message
                    }
                }
            },
            onServiceLost = { kind, serviceName ->
                runOnUiThread {
                    when (kind) {
                        AdbServiceKind.PAIRING -> {
                            val endpoint = pairingEndpoint.value
                            if (endpoint != null && (serviceName.isBlank() || endpoint.serviceName == serviceName)) {
                                pairingEndpoint.value = null
                                if (directStage.value in setOf("waiting_settings", "waiting_for_pairing", "ready_for_code")) {
                                    adbStatus.value = "The temporary pairing service disappeared. Reopen the Android pairing dialog and retry discovery."
                                }
                            }
                        }
                        AdbServiceKind.CONNECTION -> {
                            val endpoint = connectionServiceEndpoint.value
                            if (endpoint != null && (serviceName.isBlank() || endpoint.serviceName == serviceName)) {
                                connectionServiceEndpoint.value = null
                                if (directPairingSucceeded && directStage.value == "paired_waiting_connection") {
                                    directConnectAttempted.set(false)
                                    adbStatus.value = "The ADB connection endpoint changed or disappeared. Searching for the current port again."
                                }
                            }
                        }
                    }
                }
            }
        ).also { directDiscovery = it }
    }

    private fun pairWithDiscoveredCode(code: String) {
        if (directStage.value == "pairing" || AdbPairingNotificationService.isPairingSubmissionInProgress()) {
            adbStatus.value = "A pairing request is already running. Wait for its result before submitting another code."
            return
        }
        if (!code.matches(Regex("\\d{6}"))) {
            adbStatus.value = "Enter exactly six digits from the Android pairing dialog. The code was not saved."
            directStage.value = "ready_for_code"
            return
        }
        val endpoint = pairingEndpoint.value
        if (endpoint == null) {
            adbStatus.value = "Pairing details are not available yet. Keep the Android pairing dialog open and retry discovery."
            directStage.value = "ready_for_code"
            retryDirectDiscovery()
            return
        }
        AdbPairingNotificationService.stop(this)
        directStage.value = "pairing"
        adbStatus.value = "Pairing with the discovered endpoint. The code is used only for this request."
        worker.execute {
            val paired = try {
                manager().pairNow(endpoint.host, endpoint.port, code)
            } catch (error: Exception) {
                recordProbe(ProbeProvider.DIRECT_ADB, ProbeAction.PAIR, ProbeOutcome.FAILED)
                runOnUiThread {
                    pairingEndpoint.value = null
                    directStage.value = "ready_for_code"
                    adbStatus.value = "Pairing failed (${error.javaClass.simpleName}). Reopen ‘Pair device with pairing code’ and use the new code."
                    ensureDirectDiscovery().restart()
                    showDirectWaitingNotification()
                }
                return@execute
            }
            if (paired) {
                recordProbe(ProbeProvider.DIRECT_ADB, ProbeAction.PAIR, ProbeOutcome.SUCCEEDED)
                runOnUiThread {
                    directPairingSucceeded = true
                    pairingEndpoint.value = null
                    directStage.value = "paired_waiting_connection"
                    adbStatus.value = "Pairing accepted. Finding the current ADB connection endpoint and checking shell identity."
                    showDirectNotification("Pairing accepted", "InputBridge is connecting to the current Wireless debugging endpoint.")
                    attemptDirectConnectionIfReady()
                }
            } else {
                recordProbe(ProbeProvider.DIRECT_ADB, ProbeAction.PAIR, ProbeOutcome.FAILED)
                runOnUiThread {
                    pairingEndpoint.value = null
                    directStage.value = "ready_for_code"
                    adbStatus.value = "Pairing was not accepted. Open a fresh Android pairing dialog and enter the new six-digit code."
                    ensureDirectDiscovery().restart()
                    showDirectWaitingNotification()
                }
            }
        }
    }

    private fun attemptDirectConnectionIfReady() {
        if (!directPairingSucceeded) return
        val endpoint = connectionServiceEndpoint.value
        if (endpoint == null) {
            directStage.value = "paired_waiting_connection"
            adbStatus.value = "Pairing succeeded. Waiting for Android to advertise its current ADB connection endpoint."
            ensureDirectDiscovery().start()
            return
        }
        if (!directConnectAttempted.compareAndSet(false, true)) return
        directStage.value = "connecting"
        connectDirectly(endpoint.host, endpoint.port, autoStartHelper = true)
    }

    private fun ensureDirectNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(DIRECT_NOTIFICATION_CHANNEL, "Direct activation", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows when InputBridge is waiting for Wireless debugging pairing."
                }
            )
        }
    }

    private fun showDirectWaitingNotification() {
        showDirectNotification(
            "Waiting for pairing",
            "Open Wireless debugging → Pair device with pairing code, then return to InputBridge."
        )
    }

    private fun showDirectNotification(title: String, message: String) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val openIntent = Intent(this, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_DIRECT_TAB, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, DIRECT_NOTIFICATION_ID, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, DIRECT_NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(DIRECT_NOTIFICATION_ID, notification)
    }

    private fun clearDirectNotification() {
        getSystemService(NotificationManager::class.java).cancel(DIRECT_NOTIFICATION_ID)
    }

    private fun manager(): AdbConnectionManager {
        synchronized(this) {
            val existing = adbManager
            if (existing != null) return existing
            return AdbConnectionManager.shared(applicationContext).also { adbManager = it }
        }
    }

    private fun pairDirectly(host: String, port: Int, code: String) {
        if (directStage.value == "pairing" || AdbPairingNotificationService.isPairingSubmissionInProgress()) {
            adbStatus.value = "A pairing request is already running. Wait for its result before submitting another code."
            return
        }
        if (!code.matches(Regex("\\d{6}"))) {
            recordProbe(ProbeProvider.DIRECT_ADB, ProbeAction.PAIR, ProbeOutcome.FAILED)
            adbStatus.value = "Pairing failed: enter exactly six digits. Code was cleared from the form."
            return
        }
        AdbPairingNotificationService.stop(this)
        directStage.value = "pairing"
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
            runOnUiThread {
                adbStatus.value = report
                if (directStage.value == "pairing") directStage.value = "ready_for_code"
            }
        }
    }

    private fun connectDirectly(host: String, port: Int, autoStartHelper: Boolean = false) {
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
            val connected = report.startsWith("ADB shell probe returned:")
            recordProbe(ProbeProvider.DIRECT_ADB, ProbeAction.CONNECT, if (connected) ProbeOutcome.SUCCEEDED else ProbeOutcome.FAILED)
            runOnUiThread {
                adbStatus.value = report
                if (autoStartHelper) {
                    if (connected) {
                        directStage.value = "connected"
                        clearDirectNotification()
                        startDirectHelper()
                    } else {
                        directConnectAttempted.set(false)
                        directStage.value = "paired_waiting_connection"
                        ensureDirectDiscovery().start()
                    }
                }
            }
        }
    }

    private fun disconnectDirectly() {
        if (directStage.value == "pairing" || AdbPairingNotificationService.isPairingSubmissionInProgress()) {
            adbStatus.value = "Pairing is still in progress. Wait for the actual pairing result before disconnecting."
            return
        }
        AdbPairingNotificationService.stop(this)
        worker.execute {
            val helperPort = directHelperPort
            val helperToken = directHelperToken
            directHelperPort = null
            directHelperToken = null
            if (helperPort != null && helperToken != null) {
                try { LocalHelperClient.request(helperPort, helperToken, "STOP") } catch (_: Exception) { }
            }
            val activityManager = synchronized(this) { adbManager.also { adbManager = null } }
            val sharedManager = AdbConnectionManager.releaseShared()
            val old = activityManager ?: sharedManager
            if (activityManager != null && sharedManager != null && activityManager !== sharedManager) {
                try { sharedManager.close() } catch (_: Exception) { }
            }
            try { old?.close() } catch (_: Exception) { }
            connectedEndpoint = null
            directPairingSucceeded = false
            directConnectAttempted.set(false)
            recordProbe(ProbeProvider.DIRECT_ADB, ProbeAction.DISCONNECT, ProbeOutcome.SUCCEEDED)
            runOnUiThread {
                adbStatus.value = "Disconnected; ADB session closed and helper stop attempted."
                directStage.value = "idle"
                pairingEndpoint.value = null
                connectionServiceEndpoint.value = null
                clearDirectNotification()
            }
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
            runOnUiThread {
                adbStatus.value = report
                if (report.startsWith("Experimental app_process helper responded and reports UID")) {
                    directStage.value = "helper_ready"
                    clearDirectNotification()
                } else if (directStage.value == "connecting") {
                    directStage.value = "connected"
                }
            }
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

    companion object {
        private const val SHIZUKU_REQUEST_CODE = 7421
        private const val DIRECT_NOTIFICATION_ID = 7422
        private const val DIRECT_NOTIFICATION_CHANNEL = "direct_activation"
        const val EXTRA_OPEN_DIRECT_TAB = "dev.zenda.inputbridge.OPEN_DIRECT_TAB"
    }
}


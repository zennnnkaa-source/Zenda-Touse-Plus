package dev.zenda.inputbridge.lab.adb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import dev.zenda.inputbridge.lab.MainActivity
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Notification-driven lifecycle for real Android Wireless debugging ADB pairing. */
class AdbPairingNotificationService : Service() {
    companion object {
        const val ACTION_START = "dev.zenda.inputbridge.lab.adb.action.START_PAIRING"
        const val ACTION_SUBMIT_CODE = "dev.zenda.inputbridge.lab.adb.action.SUBMIT_CODE"
        const val ACTION_RETRY = "dev.zenda.inputbridge.lab.adb.action.RETRY"
        const val ACTION_STOP = "dev.zenda.inputbridge.lab.adb.action.STOP"
        const val ACTION_RESULT = "dev.zenda.inputbridge.lab.adb.action.PAIR_RESULT"
        const val ACTION_STATE = "dev.zenda.inputbridge.lab.adb.action.PAIR_STATE"
        const val EXTRA_SUCCESS = "pair_success"
        const val EXTRA_MESSAGE = "pair_message"
        const val EXTRA_HOST = "pair_host"
        const val EXTRA_PORT = "pair_port"
        const val EXTRA_CANCELLED = "pair_cancelled"
        const val EXTRA_PAIRING_ATTEMPTED = "pairing_attempted"
        private const val CHANNEL_ID = "wireless_adb_pairing"
        private const val NOTIFICATION_ID = 7301
        private const val REMOTE_INPUT_KEY = "wireless_adb_pairing_code"
        private const val EXTRA_SESSION = "pair_session"
        private const val SEARCH_TIMEOUT_MS = 180_000L
        private const val ENDPOINT_IDLE_TIMEOUT_MS = 180_000L
        @Volatile private var activePairingSubmission = false

        fun isPairingSubmissionInProgress(): Boolean = activePairingSubmission

        fun start(context: Context) {
            val intent = Intent(context, AdbPairingNotificationService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }

        fun stop(context: Context) {
            // Never tear down the worker while the ADB pairing protocol is running.
            if (activePairingSubmission) return
            context.stopService(Intent(context, AdbPairingNotificationService::class.java))
        }

        fun internalBroadcastPermission(context: Context): String =
            "${context.packageName}.permission.INTERNAL_PAIRING_BROADCAST"

        fun areNotificationsAvailable(context: Context): Boolean {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return false
            if (!manager.areNotificationsEnabled()) return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = manager.getNotificationChannel(CHANNEL_ID)
                if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) return false
            }
            return true
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "zenda-adb-pairing").apply { isDaemon = true } }
    private val submissionInProgress = AtomicBoolean(false)
    @Volatile private var currentEndpoint: AdbDiscoveredEndpoint? = null
    @Volatile private var discovery: AdbServiceDiscovery? = null
    @Volatile private var sessionId = 0L
    @Volatile private var foregroundStarted = false
    @Volatile private var destroyed = false
    @Volatile private var flowState = PairingNotificationState.IDLE

    private val timeoutRunnable = Runnable {
        if (!destroyed && currentEndpoint == null && !submissionInProgress.get()) {
            sessionId = maxOf(SystemClock.elapsedRealtime(), sessionId + 1L).coerceAtLeast(1L)
            flowState = PairingNotificationStateMachine.reduce(flowState, PairingNotificationEvent.TIMEOUT)
            closeDiscoveryOnly()
            postNotification(
                "Pairing service not found",
                "No Wireless debugging pairing service was discovered within 3 minutes. Reopen ‘Pair device with pairing code’ and retry.",
                retry = true
            )
            broadcastResult(false, "Pairing service not discovered. Confirm Wireless debugging is enabled and retry.")
        }
    }

    private val endpointExpiryRunnable = Runnable {
        if (!destroyed && currentEndpoint != null && !submissionInProgress.get()) {
            currentEndpoint = null
            sessionId = maxOf(SystemClock.elapsedRealtime(), sessionId + 1L).coerceAtLeast(1L)
            closeDiscoveryOnly()
            flowState = PairingNotificationStateMachine.reduce(flowState, PairingNotificationEvent.SERVICE_LOST)
            postNotification(
                "Pairing request expired",
                "The discovered pairing endpoint was idle for 3 minutes. Reopen ‘Pair device with pairing code’ to refresh it.",
                retry = true
            )
            restartDiscovery()
        }
    }

    private val restartDiscoveryRunnable = Runnable {
        if (!destroyed && !submissionInProgress.get()) beginDiscovery()
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action != ACTION_STOP && !foregroundStarted) {
            val initialNotification = if (action == ACTION_SUBMIT_CODE) {
                baseBuilder("Checking pairing request", "Validating the current Wireless debugging session…").build()
            } else {
                searchingNotification()
            }
            if (!startForegroundCompat(initialNotification)) return START_NOT_STICKY
        }

        when (action) {
            ACTION_START, ACTION_RETRY, null -> {
                if (submissionInProgress.get()) return START_NOT_STICKY
                if (!startForegroundCompat(searchingNotification())) return START_NOT_STICKY
                beginDiscovery()
            }
            ACTION_SUBMIT_CODE -> handleSubmittedCode(intent)
            ACTION_STOP -> {
                if (submissionInProgress.get()) {
                    broadcastState("Pairing is already being verified. Wait for its result before stopping.")
                    return START_NOT_STICKY
                }
                flowState = PairingNotificationStateMachine.reduce(flowState, PairingNotificationEvent.STOP)
                stopDiscovery()
                broadcastResult(false, "Pairing discovery was stopped.", cancelled = true)
                stopForegroundCompat(remove = true)
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun handleSubmittedCode(intent: Intent) {
        val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(REMOTE_INPUT_KEY)?.toString().orEmpty()
        val host = intent.getStringExtra(EXTRA_HOST).orEmpty()
        val port = intent.getIntExtra(EXTRA_PORT, -1)
        val submittedSession = intent.getLongExtra(EXTRA_SESSION, -1L)
        val endpoint = currentEndpoint
        if (!PairingNotificationPolicy.isCurrentPairingRequest(submittedSession, sessionId, host, port, endpoint)) {
            postNotification(
                "Pairing request expired",
                "The pairing service or notification request is no longer current. Discover the service again and submit a fresh code.",
                retry = true
            )
            restartDiscovery()
            return
        }
        if (!PairingNotificationPolicy.isValidCode(code)) {
            postNotification(
                "Invalid pairing code",
                "Enter the six ASCII digits shown by Android Wireless debugging.",
                endpoint = endpoint,
                retry = true
            )
            return
        }
        if (!submissionInProgress.compareAndSet(false, true)) return
        activePairingSubmission = true

        mainHandler.removeCallbacks(endpointExpiryRunnable)
        mainHandler.removeCallbacks(timeoutRunnable)
        flowState = PairingNotificationStateMachine.reduce(flowState, PairingNotificationEvent.SUBMIT)
        broadcastState("Pairing in progress. Waiting for the ADB pairing protocol result.")
        if (!startForegroundCompat(baseBuilder("Pairing in progress", "Authenticating with Android Wireless debugging…").build())) {
            submissionInProgress.set(false)
            activePairingSubmission = false
            return
        }

        try {
            worker.execute {
                val result = try {
                    val paired = AdbConnectionManager.shared(applicationContext).pairNow(host, port, code)
                    if (paired) true to "Android Wireless debugging accepted the pairing code."
                    else false to "Pairing was not accepted. The code may be wrong or expired."
                } catch (error: Exception) {
                    val message = when (error) {
                        is java.net.ConnectException -> "Cannot connect to the discovered pairing port. Reopen the Android pairing dialog and retry."
                        is java.net.SocketTimeoutException -> "Pairing timed out. Reopen the Android pairing dialog and use a fresh code."
                        else -> "Pairing failed (${error.javaClass.simpleName}). Reopen the Android pairing dialog and retry."
                    }
                    false to message
                }
                mainHandler.post {
                    submissionInProgress.set(false)
                    activePairingSubmission = false
                    if (destroyed) return@post
                    if (result.first) {
                        flowState = PairingNotificationStateMachine.reduce(flowState, PairingNotificationEvent.SUCCESS)
                        stopDiscovery()
                        currentEndpoint = null
                        sessionId = maxOf(SystemClock.elapsedRealtime(), sessionId + 1L).coerceAtLeast(1L)
                        broadcastResult(true, result.second, host, port, pairingAttempted = true)
                        postNotification(
                            "Wireless debugging paired",
                            "Pairing succeeded. ADB connection and input delivery are separate checks.",
                            ongoing = false
                        )
                        stopForegroundCompat(remove = false)
                        stopSelf()
                    } else {
                        flowState = PairingNotificationStateMachine.reduce(flowState, PairingNotificationEvent.FAILURE)
                        // A failed/expired temporary code must never remain attached to an old endpoint.
                        currentEndpoint = null
                        sessionId = maxOf(SystemClock.elapsedRealtime(), sessionId + 1L).coerceAtLeast(1L)
                        mainHandler.removeCallbacks(endpointExpiryRunnable)
                        closeDiscoveryOnly()
                        broadcastResult(false, result.second, host, port, pairingAttempted = true)
                        postNotification("Pairing failed", result.second, retry = true)
                        restartDiscovery()
                    }
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            submissionInProgress.set(false)
            activePairingSubmission = false
            flowState = PairingNotificationStateMachine.reduce(flowState, PairingNotificationEvent.FAILURE)
            broadcastResult(false, "The pairing worker is unavailable. Reopen the app and retry.", host, port)
            postNotification("Pairing unavailable", "The pairing worker is unavailable. Reopen the app and retry.", retry = true)
            restartDiscovery()
        }
    }

    private fun beginDiscovery() {
        if (destroyed || submissionInProgress.get()) return
        mainHandler.removeCallbacks(restartDiscoveryRunnable)
        stopDiscovery()
        sessionId = maxOf(SystemClock.elapsedRealtime(), sessionId + 1L).coerceAtLeast(1L)
        val discoverySession = sessionId
        currentEndpoint = null
        mainHandler.removeCallbacks(endpointExpiryRunnable)
        flowState = PairingNotificationStateMachine.reduce(flowState, PairingNotificationEvent.START)
        mainHandler.postDelayed(timeoutRunnable, SEARCH_TIMEOUT_MS)

        val activeDiscovery = AdbServiceDiscovery(
            context = applicationContext,
            onEndpoint = { endpoint ->
                if (endpoint.kind == AdbServiceKind.PAIRING) mainHandler.post {
                    if (destroyed || discoverySession != sessionId) return@post
                    val previousEndpoint = currentEndpoint
                    currentEndpoint = endpoint
                    flowState = PairingNotificationStateMachine.reduce(flowState, PairingNotificationEvent.SERVICE_FOUND)
                    mainHandler.removeCallbacks(timeoutRunnable)
                    if (previousEndpoint != endpoint) {
                        mainHandler.removeCallbacks(endpointExpiryRunnable)
                        mainHandler.postDelayed(endpointExpiryRunnable, ENDPOINT_IDLE_TIMEOUT_MS)
                    }
                    postNotification(
                        "Pairing service found",
                        "Enter the six-digit code shown by Android in this notification.",
                        endpoint = endpoint
                    )
                }
            },
            onStatus = { status ->
                mainHandler.post {
                    if (!destroyed && discoverySession == sessionId && currentEndpoint == null && !submissionInProgress.get()) {
                        postNotification("Searching for pairing service…", status, retry = true)
                    }
                }
            },
            onServiceLost = { kind, serviceName ->
                if (kind == AdbServiceKind.PAIRING) mainHandler.post {
                    if (destroyed || discoverySession != sessionId || submissionInProgress.get()) return@post
                    val endpoint = currentEndpoint
                    if (endpoint != null && (serviceName.isBlank() || endpoint.serviceName == serviceName)) {
                        currentEndpoint = null
                        sessionId = maxOf(SystemClock.elapsedRealtime(), sessionId + 1L).coerceAtLeast(1L)
                        flowState = PairingNotificationStateMachine.reduce(flowState, PairingNotificationEvent.SERVICE_LOST)
                        mainHandler.removeCallbacks(endpointExpiryRunnable)
                        mainHandler.removeCallbacks(timeoutRunnable)
                        // Restart under a fresh session so any PendingIntent for the lost endpoint stays stale.
                        closeDiscoveryOnly()
                        postNotification(
                            "Pairing service unavailable",
                            "The Android pairing dialog may have closed or the endpoint changed. Reopen it and wait for discovery.",
                            retry = true
                        )
                        restartDiscovery()
                    }
                }
            }
        )
        discovery = activeDiscovery
        try {
            activeDiscovery.start()
        } catch (error: Exception) {
            mainHandler.post {
                if (!destroyed && discoverySession == sessionId) {
                    postNotification("Discovery failed", "Could not start Wireless debugging discovery (${error.javaClass.simpleName}). Retry or use the in-app code field.", retry = true)
                }
            }
        }
    }

    private fun restartDiscovery() {
        mainHandler.removeCallbacks(restartDiscoveryRunnable)
        mainHandler.postDelayed(restartDiscoveryRunnable, 1_200L)
    }

    private fun stopDiscovery() {
        mainHandler.removeCallbacks(timeoutRunnable)
        mainHandler.removeCallbacks(endpointExpiryRunnable)
        mainHandler.removeCallbacks(restartDiscoveryRunnable)
        closeDiscoveryOnly()
    }

    private fun closeDiscoveryOnly() {
        val previous = discovery
        discovery = null
        try {
            previous?.close()
        } catch (_: Exception) {
            // Discovery may already have stopped due to an Android NSD callback.
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Wireless ADB pairing", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Search status and inline pairing-code entry for Android Wireless debugging."
                    setSound(null, null)
                    setShowBadge(false)
                }
            )
        }
    }

    private fun startForegroundCompat(notification: Notification): Boolean {
        if (foregroundStarted) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
            return true
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            foregroundStarted = true
            true
        } catch (error: Exception) {
            foregroundStarted = false
            broadcastResult(false, "Could not keep the pairing notification active (${error.javaClass.simpleName}). Keep InputBridge open and use the in-app code field.")
            stopSelf()
            false
        }
    }

    private fun stopForegroundCompat(remove: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(if (remove) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(remove)
        }
        foregroundStarted = false
    }

    private fun searchingNotification(): Notification = baseBuilder(
        "Searching for pairing service…",
        "Open Android Wireless debugging and tap ‘Pair device with pairing code’."
    ).addAction(action("Stop", ACTION_STOP)).build()

    private fun postNotification(
        title: String,
        message: String,
        endpoint: AdbDiscoveredEndpoint? = null,
        retry: Boolean = false,
        ongoing: Boolean = true
    ) {
        val builder = baseBuilder(title, message).setOngoing(ongoing)
        if (endpoint != null && endpoint == currentEndpoint && !submissionInProgress.get()) {
            val remoteInput = RemoteInput.Builder(REMOTE_INPUT_KEY).setLabel("6-digit pairing code").build()
            val submitIntent = Intent(this, AdbPairingNotificationService::class.java)
                .setAction(ACTION_SUBMIT_CODE)
                .setData(Uri.parse("zenda://wireless-adb-pairing/$sessionId"))
                .putExtra(EXTRA_HOST, endpoint.host)
                .putExtra(EXTRA_PORT, endpoint.port)
                .putExtra(EXTRA_SESSION, sessionId)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getForegroundService(
                this,
                (sessionId and 0x7fffffffL).toInt(),
                submitIntent,
                flags
            )
            builder.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_edit),
                    "Enter pairing code",
                    pending
                )
                    .addRemoteInput(remoteInput)
                    .setAllowGeneratedReplies(false)
                    .build()
            )
        }
        if (retry) builder.addAction(action("Retry discovery", ACTION_RETRY))
        if (ongoing) builder.addAction(action("Stop", ACTION_STOP))
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, builder.build())
    }

    private fun baseBuilder(title: String, message: String): Notification.Builder {
        val openIntent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_DIRECT_TAB, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentPending = PendingIntent.getActivity(
            this,
            7303,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setContentIntent(contentPending)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
    }

    private fun action(label: String, action: String): Notification.Action {
        val requestCode = when (action) {
            ACTION_RETRY -> 7304
            ACTION_STOP -> 7305
            else -> 7306
        }
        val intent = Intent(this, AdbPairingNotificationService::class.java).setAction(action)
        val pending = PendingIntent.getForegroundService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val iconResource = when (action) {
            ACTION_RETRY -> android.R.drawable.ic_popup_sync
            ACTION_STOP -> android.R.drawable.ic_menu_close_clear_cancel
            else -> android.R.drawable.ic_menu_manage
        }
        return Notification.Action.Builder(Icon.createWithResource(this, iconResource), label, pending).build()
    }

    private fun broadcastState(message: String) {
        sendBroadcast(
            Intent(ACTION_STATE).setPackage(packageName).putExtra(EXTRA_MESSAGE, message),
            internalBroadcastPermission(this)
        )
    }

    private fun broadcastResult(
        success: Boolean,
        message: String,
        host: String = "",
        port: Int = -1,
        cancelled: Boolean = false,
        pairingAttempted: Boolean = false
    ) {
        sendBroadcast(
            Intent(ACTION_RESULT).setPackage(packageName)
                .putExtra(EXTRA_SUCCESS, success)
                .putExtra(EXTRA_MESSAGE, message)
                .putExtra(EXTRA_HOST, host)
                .putExtra(EXTRA_PORT, port)
                .putExtra(EXTRA_CANCELLED, cancelled)
                .putExtra(EXTRA_PAIRING_ATTEMPTED, pairingAttempted),
            internalBroadcastPermission(this)
        )
    }

    override fun onDestroy() {
        destroyed = true
        stopDiscovery()
        worker.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

package dev.zenda.inputbridge.lab.adb

import android.content.Context
import android.net.wifi.WifiManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.ext.SdkExtensions
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

/** A service endpoint discovered from Android Wireless debugging's mDNS advertisements. */
data class AdbDiscoveredEndpoint(
    val host: String,
    val port: Int,
    val serviceName: String,
    val kind: AdbServiceKind
) {
    val displayAddress: String
        get() = if (host.contains(':') && !host.startsWith('[')) "[$host]:$port" else "$host:$port"
}

enum class AdbServiceKind(val serviceType: String) {
    PAIRING("_adb-tls-pairing._tcp."),
    CONNECTION("_adb-tls-connect._tcp.");

    companion object {
        fun fromServiceType(serviceType: String?): AdbServiceKind? {
            val normalized = serviceType.orEmpty().lowercase()
            return entries.firstOrNull { normalized.contains(it.serviceType.removeSuffix(".").lowercase()) }
        }
    }
}

/**
 * Discovers current Wireless debugging pairing and connection endpoints without hardcoding ports.
 * This only discovers endpoints; it does not establish an ADB session or imply input capability.
 */
class AdbServiceDiscovery(
    context: Context,
    private val onEndpoint: (AdbDiscoveredEndpoint) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onServiceLost: (AdbServiceKind, String) -> Unit = { _, _ -> }
) : Closeable {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val multicastLock: WifiManager.MulticastLock? = try {
        wifiManager?.createMulticastLock("ZendaTousePlus:AdbServiceDiscovery")?.apply {
            setReferenceCounted(false)
        }
    } catch (_: Exception) {
        null
    }
    private val listeners = mutableMapOf<AdbServiceKind, NsdManager.DiscoveryListener>()
    private val resolving = ConcurrentHashMap.newKeySet<String>()
    private val lostServices = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var closed = false
    @Volatile private var generation = 0L

    @Synchronized
    fun start() {
        if (closed) return
        if (listeners.isEmpty()) generation += 1L
        val listenerGeneration = generation
        acquireMulticastLockIfRequired()
        AdbServiceKind.entries.forEach { kind ->
            if (listeners.containsKey(kind)) return@forEach
            val listener = createListener(kind, listenerGeneration)
            listeners[kind] = listener
            try {
                manager.discoverServices(kind.serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (error: Exception) {
                listeners.remove(kind)
                onStatus("Could not start ${kind.name.lowercase()} service discovery (${error.javaClass.simpleName}).")
            }
        }
        if (listeners.isEmpty()) releaseMulticastLock()
    }

    @Synchronized
    fun restart() {
        stopDiscovery()
        start()
    }

    @Synchronized
    fun stopDiscovery() {
        generation += 1L
        val snapshot = listeners.toMap()
        listeners.clear()
        snapshot.values.forEach { listener ->
            try {
                manager.stopServiceDiscovery(listener)
            } catch (_: Exception) {
                // The service may already have stopped or failed to start.
            }
        }
        resolving.clear()
        lostServices.clear()
        releaseMulticastLock()
    }

    /** Android 12 and earlier, plus Android 13 without T extension 7, need this lock for mDNS. */
    private fun requiresMulticastLock(): Boolean = when {
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 -> true
        Build.VERSION.SDK_INT == Build.VERSION_CODES.TIRAMISU ->
            SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) < 7
        else -> false
    }

    @Synchronized
    private fun acquireMulticastLockIfRequired() {
        if (!requiresMulticastLock()) return
        val lock = multicastLock
        if (lock == null) {
            onStatus("Wi-Fi multicast lock is unavailable. mDNS discovery may not receive service advertisements on this Android version.")
            return
        }
        try {
            if (!lock.isHeld) lock.acquire()
        } catch (error: Exception) {
            onStatus("Could not acquire the temporary Wi-Fi multicast lock (${error.javaClass.simpleName}); mDNS discovery may be limited.")
        }
    }

    @Synchronized
    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) multicastLock.release()
        } catch (_: Exception) {
            // Release can race with a platform-side lock reset; do not block discovery cleanup.
        }
    }

    private fun serviceKey(generation: Long, serviceInfo: NsdServiceInfo): String =
        "$generation|${serviceInfo.serviceType.orEmpty().lowercase()}|${serviceInfo.serviceName.orEmpty()}"

    private fun createListener(kind: AdbServiceKind, listenerGeneration: Long) = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            if (!closed && listenerGeneration == generation) {
                onStatus("Searching for Wireless debugging ${kind.name.lowercase()} service.")
            }
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            if (closed || listenerGeneration != generation) return
            val foundKind = AdbServiceKind.fromServiceType(serviceInfo.serviceType) ?: kind
            if (foundKind != kind) return
            val key = serviceKey(listenerGeneration, serviceInfo)
            lostServices.remove(key)
            if (!resolving.add(key)) return
            try {
                manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        resolving.remove(key)
                        if (!closed && listenerGeneration == generation) {
                            onStatus("Could not resolve Wireless debugging service (Android error $errorCode). Retry discovery or use manual endpoint details.")
                        }
                    }

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        resolving.remove(key)
                        if (closed || listenerGeneration != generation || key in lostServices) return
                        val host = info.host?.hostAddress
                        val port = info.port
                        if (!host.isNullOrBlank() && port in 1..65535) {
                            onEndpoint(
                                AdbDiscoveredEndpoint(
                                    host = host,
                                    port = port,
                                    serviceName = info.serviceName.orEmpty(),
                                    kind = foundKind
                                )
                            )
                        } else {
                            onStatus("Wireless debugging service was found but did not expose a usable endpoint. Retry discovery or use manual endpoint details.")
                        }
                    }
                })
            } catch (error: Exception) {
                resolving.remove(key)
                if (!closed && listenerGeneration == generation) {
                    onStatus("Could not resolve Wireless debugging service (${error.javaClass.simpleName}).")
                }
            }
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            if (closed || listenerGeneration != generation) return
            lostServices.add(serviceKey(listenerGeneration, serviceInfo))
            val lostKind = AdbServiceKind.fromServiceType(serviceInfo.serviceType) ?: kind
            onServiceLost(lostKind, serviceInfo.serviceName.orEmpty())
        }

        override fun onDiscoveryStopped(serviceType: String) = Unit

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            synchronized(this@AdbServiceDiscovery) {
                if (listeners[kind] === this) listeners.remove(kind)
                if (listeners.isEmpty() && listenerGeneration == generation) releaseMulticastLock()
            }
            if (!closed && listenerGeneration == generation) {
                onStatus("Wireless debugging discovery failed (Android error $errorCode). Retry or use manual endpoint details.")
            }
            try { manager.stopServiceDiscovery(this) } catch (_: Exception) { }
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            try { manager.stopServiceDiscovery(this) } catch (_: Exception) { }
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        stopDiscovery()
    }
}

package dev.zenda.inputbridge.lab.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
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
    private val onStatus: (String) -> Unit
) : Closeable {
    private val manager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val listeners = mutableMapOf<AdbServiceKind, NsdManager.DiscoveryListener>()
    private val resolving = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var closed = false

    @Synchronized
    fun start() {
        if (closed) return
        AdbServiceKind.entries.forEach { kind ->
            if (listeners.containsKey(kind)) return@forEach
            val listener = createListener(kind)
            listeners[kind] = listener
            try {
                manager.discoverServices(kind.serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (error: Exception) {
                listeners.remove(kind)
                onStatus("Could not start ${kind.name.lowercase()} service discovery (${error.javaClass.simpleName}).")
            }
        }
    }

    @Synchronized
    fun restart() {
        stopDiscovery()
        start()
    }

    @Synchronized
    fun stopDiscovery() {
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
    }

    private fun createListener(kind: AdbServiceKind) = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            onStatus("Searching for Wireless debugging ${kind.name.lowercase()} service.")
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            val foundKind = AdbServiceKind.fromServiceType(serviceInfo.serviceType) ?: kind
            if (foundKind != kind) return
            val key = "${serviceInfo.serviceType}|${serviceInfo.serviceName}"
            if (!resolving.add(key)) return
            try {
                manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        resolving.remove(key)
                        if (!closed) onStatus("Could not resolve Wireless debugging service (Android error $errorCode). Retry discovery or use manual endpoint details.")
                    }

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        resolving.remove(key)
                        if (closed) return
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
                if (!closed) onStatus("Could not resolve Wireless debugging service (${error.javaClass.simpleName}).")
            }
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

        override fun onDiscoveryStopped(serviceType: String) = Unit

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            synchronized(this@AdbServiceDiscovery) { listeners.remove(kind) }
            if (!closed) onStatus("Wireless debugging discovery failed (Android error $errorCode). Retry or use manual endpoint details.")
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

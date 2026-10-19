package dev.zenda.inputbridge.lab

import dev.zenda.inputbridge.lab.adb.AdbDiscoveredEndpoint
import dev.zenda.inputbridge.lab.adb.AdbServiceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdbServiceDiscoveryPolicyTest {
    @Test
    fun mapsWirelessDebuggingServiceTypesToSeparateEndpoints() {
        assertEquals(AdbServiceKind.PAIRING, AdbServiceKind.fromServiceType("_adb-tls-pairing._tcp.local."))
        assertEquals(AdbServiceKind.CONNECTION, AdbServiceKind.fromServiceType("_adb-tls-connect._tcp.local."))
        assertNull(AdbServiceKind.fromServiceType("_http._tcp.local."))
    }

    @Test
    fun formatsIpv4AndIpv6EndpointsWithoutConfusingThePort() {
        assertEquals("192.168.1.8:37123", AdbDiscoveredEndpoint("192.168.1.8", 37123, "pair", AdbServiceKind.PAIRING).displayAddress)
        assertEquals("[fe80::1234%wlan0]:37123", AdbDiscoveredEndpoint("fe80::1234%wlan0", 37123, "pair", AdbServiceKind.PAIRING).displayAddress)
    }
}

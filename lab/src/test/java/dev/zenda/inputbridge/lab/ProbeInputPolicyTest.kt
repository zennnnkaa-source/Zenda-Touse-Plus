package dev.zenda.inputbridge.lab

import dev.zenda.inputbridge.lab.adb.ProbeInputPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbeInputPolicyTest {
    @Test fun portsMustBeInTcpRange() {
        assertTrue(ProbeInputPolicy.isValidPort(1))
        assertTrue(ProbeInputPolicy.isValidPort(65535))
        assertFalse(ProbeInputPolicy.isValidPort(0))
        assertFalse(ProbeInputPolicy.isValidPort(65536))
    }

    @Test fun pairingCodeMustBeSixDigits() {
        assertTrue(ProbeInputPolicy.isValidPairingCode("012345"))
        assertFalse(ProbeInputPolicy.isValidPairingCode("12345"))
        assertFalse(ProbeInputPolicy.isValidPairingCode("12a456"))
        assertFalse(ProbeInputPolicy.isValidPairingCode("1234567"))
    }

    @Test fun onlyFixedProbeCommandsAreAllowed() {
        assertTrue(ProbeInputPolicy.isAllowedCommand("id"))
        assertTrue(ProbeInputPolicy.isAllowedCommand("pm path dev.zenda.inputbridge.lab"))
        val apk = "/data/app/~~safe/dev.zenda.inputbridge.lab-abc/base.apk"
        val token = "a".repeat(64)
        assertTrue(ProbeInputPolicy.isAllowedCommand("CLASSPATH=$apk /system/bin/app_process /system/bin dev.zenda.inputbridge.lab.adb.AdbBootstrapHelperMain 34567 $token </dev/null >/dev/null 2>&1 &"))
        assertFalse(ProbeInputPolicy.isAllowedCommand("input tap 540 1200"))
        assertFalse(ProbeInputPolicy.isAllowedCommand("pm path dev.zenda.inputbridge.target; reboot"))
        assertFalse(ProbeInputPolicy.isAllowedCommand("rm -rf /"))
        assertFalse(ProbeInputPolicy.isAllowedCommand("CLASSPATH=$apk /system/bin/app_process /system/bin dev.zenda.inputbridge.lab.adb.AdbBootstrapHelperMain 1234 $token </dev/null >/dev/null 2>&1 &"))
    }

    @Test fun helperRequestsAreBounded() {
        assertTrue(ProbeInputPolicy.isAllowedHelperRequest("UID"))
        assertTrue(ProbeInputPolicy.isAllowedHelperRequest("STOP"))
        assertTrue(ProbeInputPolicy.isAllowedHelperRequest("TAP 0 12000"))
        assertTrue(ProbeInputPolicy.isAllowedHelperRequest("SWIPE 100 200 900 200 500"))
        assertFalse(ProbeInputPolicy.isAllowedHelperRequest("TAP 12001 20"))
        assertFalse(ProbeInputPolicy.isAllowedHelperRequest("SWIPE 10 10 20 20 10"))
        assertFalse(ProbeInputPolicy.isAllowedHelperRequest("SWIPE 10 10 20 20 5000"))
        assertFalse(ProbeInputPolicy.isAllowedHelperRequest("TAP 10 20; reboot"))
        assertFalse(ProbeInputPolicy.isAllowedHelperRequest("SHELL id"))
    }

}

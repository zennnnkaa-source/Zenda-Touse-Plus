package dev.zenda.inputbridge.lab

import dev.zenda.inputbridge.lab.adb.AdbDiscoveredEndpoint
import dev.zenda.inputbridge.lab.adb.AdbServiceKind
import dev.zenda.inputbridge.lab.adb.PairingNotificationEvent
import dev.zenda.inputbridge.lab.adb.PairingNotificationPolicy
import dev.zenda.inputbridge.lab.adb.PairingNotificationState
import dev.zenda.inputbridge.lab.adb.PairingNotificationStateMachine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingNotificationPolicyTest {
    @Test fun acceptsOnlySixAsciiDigits() {
        assertTrue(PairingNotificationPolicy.isValidCode("012345"))
        assertFalse(PairingNotificationPolicy.isValidCode("12345"))
        assertFalse(PairingNotificationPolicy.isValidCode("1234567"))
        assertFalse(PairingNotificationPolicy.isValidCode("１２３４５６"))
        assertFalse(PairingNotificationPolicy.isValidCode("12 345"))
        assertFalse(PairingNotificationPolicy.isValidCode(null))
    }

    @Test fun endpointValidationRejectsMalformedValues() {
        assertTrue(PairingNotificationPolicy.isValidEndpoint("192.168.1.4", 37123))
        assertTrue(PairingNotificationPolicy.isValidEndpoint("fe80::1234", 65535))
        assertFalse(PairingNotificationPolicy.isValidEndpoint("bad host", 37123))
        assertFalse(PairingNotificationPolicy.isValidEndpoint("", 37123))
        assertFalse(PairingNotificationPolicy.isValidEndpoint("192.168.1.4", 0))
        assertFalse(PairingNotificationPolicy.isValidEndpoint("192.168.1.4", 65536))
    }

    @Test fun notificationSubmissionMustMatchCurrentSessionAndPairingEndpoint() {
        val endpoint = AdbDiscoveredEndpoint(
            host = "192.168.1.4",
            port = 37123,
            serviceName = "adb-pairing-test",
            kind = AdbServiceKind.PAIRING
        )
        assertTrue(PairingNotificationPolicy.isCurrentPairingRequest(8, 8, "192.168.1.4", 37123, endpoint))
        assertFalse(PairingNotificationPolicy.isCurrentPairingRequest(7, 8, "192.168.1.4", 37123, endpoint))
        assertFalse(PairingNotificationPolicy.isCurrentPairingRequest(8, 8, "192.168.1.5", 37123, endpoint))
        assertFalse(PairingNotificationPolicy.isCurrentPairingRequest(8, 8, "192.168.1.4", 37124, endpoint))
        assertFalse(PairingNotificationPolicy.isCurrentPairingRequest(8, 8, "192.168.1.4", 37123, null))
        val connectionEndpoint = endpoint.copy(kind = AdbServiceKind.CONNECTION)
        assertFalse(PairingNotificationPolicy.isCurrentPairingRequest(8, 8, "192.168.1.4", 37123, connectionEndpoint))
    }

    @Test fun stateTransitionsDoNotReportSuccessBeforeProtocolResult() {
        var state = PairingNotificationState.IDLE
        assertEquals(PairingNotificationState.IDLE, PairingNotificationStateMachine.reduce(state, PairingNotificationEvent.SUCCESS))
        state = PairingNotificationStateMachine.reduce(state, PairingNotificationEvent.START)
        assertEquals(PairingNotificationState.SEARCHING, state)
        state = PairingNotificationStateMachine.reduce(state, PairingNotificationEvent.SERVICE_FOUND)
        assertEquals(PairingNotificationState.SERVICE_FOUND, state)
        state = PairingNotificationStateMachine.reduce(state, PairingNotificationEvent.SUBMIT)
        assertEquals(PairingNotificationState.SUBMITTING, state)
        assertEquals(PairingNotificationState.SUBMITTING, PairingNotificationStateMachine.reduce(state, PairingNotificationEvent.SERVICE_FOUND))
        state = PairingNotificationStateMachine.reduce(state, PairingNotificationEvent.SUCCESS)
        assertEquals(PairingNotificationState.PAIRED, state)
    }

    @Test fun serviceLossReturnsToSearchAndTimeoutCanRetry() {
        var state = PairingNotificationStateMachine.reduce(PairingNotificationState.IDLE, PairingNotificationEvent.START)
        state = PairingNotificationStateMachine.reduce(state, PairingNotificationEvent.SERVICE_FOUND)
        state = PairingNotificationStateMachine.reduce(state, PairingNotificationEvent.SERVICE_LOST)
        assertEquals(PairingNotificationState.SEARCHING, state)
        state = PairingNotificationStateMachine.reduce(state, PairingNotificationEvent.TIMEOUT)
        assertEquals(PairingNotificationState.TIMED_OUT, state)
        state = PairingNotificationStateMachine.reduce(state, PairingNotificationEvent.RETRY)
        assertEquals(PairingNotificationState.SEARCHING, state)
    }

    @Test fun failedPairingCanReturnToDiscovery() {
        var state = PairingNotificationStateMachine.reduce(PairingNotificationState.IDLE, PairingNotificationEvent.START)
        state = PairingNotificationStateMachine.reduce(state, PairingNotificationEvent.SERVICE_FOUND)
        state = PairingNotificationStateMachine.reduce(state, PairingNotificationEvent.SUBMIT)
        state = PairingNotificationStateMachine.reduce(state, PairingNotificationEvent.FAILURE)
        assertEquals(PairingNotificationState.FAILED, state)
        state = PairingNotificationStateMachine.reduce(state, PairingNotificationEvent.RETRY)
        assertEquals(PairingNotificationState.SEARCHING, state)
    }
}

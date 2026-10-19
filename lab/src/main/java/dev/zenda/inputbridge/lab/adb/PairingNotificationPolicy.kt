package dev.zenda.inputbridge.lab.adb

/** Pure policy used by notification input handling; pairing codes are never persisted. */
object PairingNotificationPolicy {
    fun isValidCode(code: CharSequence?): Boolean =
        code != null && code.length == 6 && code.all { it in '0'..'9' }

    fun isValidEndpoint(host: String?, port: Int): Boolean =
        !host.isNullOrBlank() && host.length <= 253 && !host.any(Char::isWhitespace) && port in 1..65535

    fun isCurrentSession(submittedSession: Long, currentSession: Long): Boolean =
        submittedSession > 0L && submittedSession == currentSession

    /** Reject modified extras, expired sessions, and connection (not pairing) endpoints. */
    fun isCurrentPairingRequest(
        submittedSession: Long,
        currentSession: Long,
        submittedHost: String?,
        submittedPort: Int,
        currentEndpoint: AdbDiscoveredEndpoint?
    ): Boolean {
        if (!isCurrentSession(submittedSession, currentSession)) return false
        if (!isValidEndpoint(submittedHost, submittedPort)) return false
        val endpoint = currentEndpoint ?: return false
        return endpoint.kind == AdbServiceKind.PAIRING &&
            endpoint.host == submittedHost && endpoint.port == submittedPort
    }
}

enum class PairingNotificationState { IDLE, SEARCHING, SERVICE_FOUND, SUBMITTING, PAIRED, FAILED, TIMED_OUT, STOPPED }
enum class PairingNotificationEvent { START, SERVICE_FOUND, SERVICE_LOST, SUBMIT, SUCCESS, FAILURE, TIMEOUT, RETRY, STOP }

/** Deterministic state reducer shared by notification flow tests and service lifecycle. */
object PairingNotificationStateMachine {
    fun reduce(state: PairingNotificationState, event: PairingNotificationEvent): PairingNotificationState = when (event) {
        PairingNotificationEvent.START, PairingNotificationEvent.RETRY -> PairingNotificationState.SEARCHING
        PairingNotificationEvent.SERVICE_FOUND -> if (state == PairingNotificationState.SEARCHING || state == PairingNotificationState.FAILED || state == PairingNotificationState.TIMED_OUT) PairingNotificationState.SERVICE_FOUND else state
        PairingNotificationEvent.SERVICE_LOST -> if (state == PairingNotificationState.SERVICE_FOUND || state == PairingNotificationState.FAILED) PairingNotificationState.SEARCHING else state
        PairingNotificationEvent.SUBMIT -> if (state == PairingNotificationState.SERVICE_FOUND || state == PairingNotificationState.FAILED) PairingNotificationState.SUBMITTING else state
        PairingNotificationEvent.SUCCESS -> if (state == PairingNotificationState.SUBMITTING) PairingNotificationState.PAIRED else state
        PairingNotificationEvent.FAILURE -> if (state == PairingNotificationState.SUBMITTING || state == PairingNotificationState.SERVICE_FOUND) PairingNotificationState.FAILED else state
        PairingNotificationEvent.TIMEOUT -> if (state == PairingNotificationState.SEARCHING) PairingNotificationState.TIMED_OUT else state
        PairingNotificationEvent.STOP -> PairingNotificationState.STOPPED
    }
}

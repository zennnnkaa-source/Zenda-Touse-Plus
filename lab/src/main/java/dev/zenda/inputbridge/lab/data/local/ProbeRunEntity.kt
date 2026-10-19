package dev.zenda.inputbridge.lab.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "probe_run_history",
    indices = [Index(value = ["occurredAtMillis"])]
)
data class ProbeRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val occurredAtMillis: Long,
    val provider: String,
    val action: String,
    val outcome: String
)

enum class ProbeProvider { SHIZUKU, DIRECT_ADB, TEST_TARGET }
enum class ProbeAction { PERMISSION_REQUEST, HELPER_BIND, HELPER_UNBIND, TAP_PROBE, SWIPE_PROBE, PAIR, CONNECT, HELPER_START, HELPER_STOP, DISCONNECT, OPEN_TARGET }
enum class ProbeOutcome { STARTED, SUCCEEDED, FAILED, UNVERIFIED }

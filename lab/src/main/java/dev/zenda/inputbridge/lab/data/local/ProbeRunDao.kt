package dev.zenda.inputbridge.lab.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProbeRunDao {
    @Insert
    suspend fun insert(record: ProbeRunEntity)

    @Query("SELECT * FROM probe_run_history ORDER BY occurredAtMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ProbeRunEntity>>

    @Query("DELETE FROM probe_run_history WHERE id NOT IN (SELECT id FROM probe_run_history ORDER BY occurredAtMillis DESC LIMIT 100)")
    suspend fun keepLatestHundred()
}

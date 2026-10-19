package dev.zenda.inputbridge.lab.data.local

import android.content.Context
import kotlinx.coroutines.flow.Flow

class ProbeHistoryRepository(context: Context) {
    private val dao = ProbeRunDatabase.getInstance(context).probeRunDao()

    fun observeRecent(limit: Int = 20): Flow<List<ProbeRunEntity>> =
        dao.observeRecent(limit.coerceIn(1, 100))

    suspend fun record(provider: ProbeProvider, action: ProbeAction, outcome: ProbeOutcome) {
        dao.insert(
            ProbeRunEntity(
                occurredAtMillis = System.currentTimeMillis(),
                provider = provider.name,
                action = action.name,
                outcome = outcome.name
            )
        )
        dao.keepLatestHundred()
    }
}

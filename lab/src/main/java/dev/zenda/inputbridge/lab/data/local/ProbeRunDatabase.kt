package dev.zenda.inputbridge.lab.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProbeRunEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ProbeRunDatabase : RoomDatabase() {
    abstract fun probeRunDao(): ProbeRunDao

    companion object {
        @Volatile private var instance: ProbeRunDatabase? = null

        fun getInstance(context: Context): ProbeRunDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ProbeRunDatabase::class.java,
                    "inputbridge_probe_history.db"
                ).build().also { instance = it }
            }
    }
}

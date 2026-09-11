package com.jarvis.app.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Database(entities = [VaultMemory::class], version = 1, exportSchema = false)
abstract class StarkVaultDb : RoomDatabase() {
    abstract fun vaultDao(): VaultDao

    companion object {
        @Volatile private var inst: StarkVaultDb? = null

        fun get(ctx: Context): StarkVaultDb {
            return inst ?: synchronized(this) {
                inst ?: Room.databaseBuilder(
                    ctx.applicationContext, StarkVaultDb::class.java, "stark_vault.db"
                ).build().also { inst = it }
            }
        }

        private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Mirror a memory into the privacy vault (fire-and-forget). */
        fun remember(ctx: Context, category: String, content: String) {
            val c = content.trim().take(2000)
            if (c.isEmpty()) return
            try {
                val db = get(ctx)
                io.launch {
                    try {
                        db.vaultDao().insertMemory(VaultMemory(category = category, content = c))
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }
        }

        /** Purge vault rows older than keepDays (fire-and-forget, runs at startup). */
        fun purgeExpired(ctx: Context, keepDays: Int = 90) {
            try {
                val db = get(ctx)
                val cutoff = starkVaultCutoff(System.currentTimeMillis(), keepDays)
                io.launch {
                    try {
                        db.vaultDao().purgeExpiredMemories(cutoff)
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }
        }
    }
}

package com.jarvis.app.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "stark_vault")
data class VaultMemory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface VaultDao {
    @Query("SELECT * FROM stark_vault ORDER BY timestamp DESC")
    suspend fun getAllMemories(): List<VaultMemory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: VaultMemory)

    @Query("DELETE FROM stark_vault WHERE timestamp < :expiryThreshold")
    suspend fun purgeExpiredMemories(expiryThreshold: Long)
}

/** 90-day TTL cutoff for the Stark privacy vault. Pure, tested. */
fun starkVaultCutoff(nowMs: Long, keepDays: Int = 90): Long = nowMs - keepDays * 24 * 3600 * 1000L

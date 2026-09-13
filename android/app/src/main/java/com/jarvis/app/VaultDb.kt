package com.jarvis.app

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

/**
 * Self-healing memory vault (Protocol Epsilon): user facts live in a local
 * Room database (stark_vault) instead of SharedPreferences. Every app start
 * purges records older than 30 days, so nothing accumulates forever.
 */
@Entity(tableName = "facts")
data class VaultFact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val ts: Long
)

@Dao
interface VaultDao {
    @Query("SELECT * FROM facts ORDER BY id DESC LIMIT 200")
    fun all(): List<VaultFact>

    @Insert
    fun insert(f: VaultFact)

    @Query("DELETE FROM facts WHERE text = :t")
    fun deleteText(t: String)

    @Query("DELETE FROM facts")
    fun clear()

    @Query("DELETE FROM facts WHERE ts < :cutoff")
    fun purgeBefore(cutoff: Long): Int

    @Query("DELETE FROM facts WHERE id NOT IN (SELECT id FROM facts ORDER BY id DESC LIMIT :keep)")
    fun trimTo(keep: Int)
}

@Database(entities = [VaultFact::class], version = 1, exportSchema = false)
abstract class VaultDb : RoomDatabase() {
    abstract fun dao(): VaultDao
}

/** 30-day TTL cutoff: vault rows older than this die on startup. Pure, tested. */
fun vaultCutoff(nowMs: Long): Long = nowMs - 30L * 24 * 3600 * 1000

/** Convert legacy SharedPrefs facts into vault rows (fresh timestamps). Pure, tested. */
fun migrateLegacyFacts(legacy: Set<String>, nowMs: Long): List<VaultFact> =
    legacy.map { VaultFact(text = it.take(500), ts = nowMs) }

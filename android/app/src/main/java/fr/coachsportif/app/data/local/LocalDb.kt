package fr.coachsportif.app.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Dernière réponse serveur connue (jour, semaine…) : affichage hors ligne avec date de synchro. */
@Entity(tableName = "cache")
data class CachedPayload(
    @PrimaryKey val key: String,          // "day:2026-11-03", "week:2026-11-02", "summary:2026-11-02"
    val json: String,
    val fetchedAtMs: Long,
    val planVersion: Int?,
)

/**
 * File d'attente des écritures (terminer une séance, corriger…).
 * La clé d'idempotence est créée UNE fois à la mise en file : un renvoi après coupure ne crée pas de doublon.
 */
@Entity(tableName = "outbox")
data class OutboxOp(
    @PrimaryKey val idempotencyKey: String,
    val fn: String,
    val argsJson: String,
    val label: String,                    // « Dos/Biceps du 03/11 »
    val createdAtMs: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
    val state: String = "pending",        // pending | failed (erreur métier, intervention requise)
)

/** Saisie en cours, sauvegardée à chaque frappe (fermeture d'app / batterie vide → rien n'est perdu). */
@Entity(tableName = "entry_draft")
data class EntryDraft(
    @PrimaryKey val sessionKey: String,
    val json: String,
    val updatedAtMs: Long,
)

@Dao
interface CacheDao {
    @Query("select * from cache where `key` = :key")
    fun observe(key: String): Flow<CachedPayload?>

    @Upsert
    suspend fun put(p: CachedPayload)

    @Query("delete from cache")
    suspend fun clear()
}

@Dao
interface OutboxDao {
    @Query("select * from outbox order by createdAtMs")
    fun observeAll(): Flow<List<OutboxOp>>

    @Query("select * from outbox where state = 'pending' order by createdAtMs")
    suspend fun pending(): List<OutboxOp>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(op: OutboxOp)

    @Query("delete from outbox where idempotencyKey = :key")
    suspend fun remove(key: String)

    @Query("update outbox set attempts = attempts + 1, lastError = :error, state = :state where idempotencyKey = :key")
    suspend fun markAttempt(key: String, error: String?, state: String)

    @Query("delete from outbox")
    suspend fun clear()
}

@Dao
interface DraftDao {
    @Query("select * from entry_draft where sessionKey = :key")
    suspend fun get(key: String): EntryDraft?

    @Upsert
    suspend fun put(d: EntryDraft)

    @Query("delete from entry_draft where sessionKey = :key")
    suspend fun remove(key: String)

    @Query("delete from entry_draft")
    suspend fun clear()
}

@Database(entities = [CachedPayload::class, OutboxOp::class, EntryDraft::class], version = 1, exportSchema = true)
abstract class LocalDb : RoomDatabase() {
    abstract fun cache(): CacheDao
    abstract fun outbox(): OutboxDao
    abstract fun drafts(): DraftDao

    companion object {
        fun create(context: Context): LocalDb =
            Room.databaseBuilder(context, LocalDb::class.java, "coach.db").build()
    }
}

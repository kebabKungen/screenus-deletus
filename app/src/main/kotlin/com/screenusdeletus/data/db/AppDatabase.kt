package com.screenusdeletus.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY createdAt")
    fun observeAll(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules ORDER BY createdAt")
    suspend fun getAll(): List<RuleEntity>

    @Query("SELECT * FROM rules WHERE id = :id")
    suspend fun get(id: String): RuleEntity?

    @Upsert
    suspend fun upsert(rule: RuleEntity)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface DayResultDao {
    @Query("SELECT * FROM day_results WHERE epochDay >= :fromDay ORDER BY epochDay")
    suspend fun since(fromDay: Long): List<DayResultEntity>

    @Query("SELECT * FROM day_results WHERE epochDay >= :fromDay ORDER BY epochDay")
    fun observeSince(fromDay: Long): Flow<List<DayResultEntity>>

    @Query("SELECT * FROM day_results ORDER BY epochDay")
    suspend fun all(): List<DayResultEntity>

    @Upsert
    suspend fun upsert(day: DayResultEntity)
}

@Dao
interface TriggerLogDao {
    @Insert
    suspend fun insert(log: TriggerLogEntity): Long

    @Query("SELECT * FROM trigger_logs WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun since(since: Long): List<TriggerLogEntity>

    @Query("SELECT * FROM trigger_logs WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun observeSince(since: Long): Flow<List<TriggerLogEntity>>
}

@Dao
interface TamperEventDao {
    @Insert
    suspend fun insert(event: TamperEventEntity): Long

    @Query("SELECT * FROM tamper_events WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun since(since: Long): List<TamperEventEntity>

    @Query("SELECT * FROM tamper_events ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TamperEventEntity>>

    @Query("SELECT * FROM tamper_events WHERE reportedToGuardian = 0 ORDER BY timestamp")
    suspend fun unreported(): List<TamperEventEntity>

    @Query("UPDATE tamper_events SET reportedToGuardian = 1 WHERE id IN (:ids)")
    suspend fun markReported(ids: List<Long>)
}

@Dao
interface UnlockDao {
    @Upsert
    suspend fun upsert(request: UnlockRequestEntity)

    @Query("SELECT * FROM unlock_requests WHERE grantedAt IS NOT NULL AND grantedAt >= :since ORDER BY grantedAt")
    suspend fun grantedSince(since: Long): List<UnlockRequestEntity>

    @Query("SELECT * FROM unlock_requests ORDER BY requestedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<UnlockRequestEntity>>

    @Query("SELECT * FROM unlock_requests WHERE grantedAt IS NOT NULL AND grantedAt >= :since")
    fun observeGrantedSince(since: Long): Flow<List<UnlockRequestEntity>>
}

@Dao
interface EnforcementEventDao {
    @Insert
    suspend fun insert(event: EnforcementEventEntity)

    @Query("SELECT COUNT(*) FROM enforcement_events WHERE ruleId = :ruleId AND action = :action AND timestamp >= :since")
    suspend fun count(ruleId: String, action: String, since: Long): Int

    @Query("SELECT COUNT(*) FROM enforcement_events WHERE action IN ('BLOCK','INTERSTITIAL') AND timestamp >= :since")
    fun observeBlocksSince(since: Long): Flow<Int>

    @Query("DELETE FROM enforcement_events WHERE timestamp < :before")
    suspend fun prune(before: Long)
}

@Dao
interface ViolationDao {
    @Insert
    suspend fun insert(v: ViolationEntity)

    @Query("SELECT COUNT(*) FROM violations WHERE epochDay = :day AND ruleId = :ruleId AND kind = :kind")
    suspend fun count(day: Long, ruleId: String, kind: String): Int

    @Query("SELECT COUNT(*) FROM violations WHERE epochDay = :day")
    suspend fun countForDay(day: Long): Int

    @Query("SELECT * FROM violations ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ViolationEntity>>
}

@Dao
interface CategoryOverrideDao {
    @Query("SELECT * FROM category_overrides")
    suspend fun all(): List<CategoryOverrideEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(o: CategoryOverrideEntity)
}

@Dao
interface GuardianOutboxDao {
    @Insert
    suspend fun insert(m: GuardianMessageEntity): Long

    @Query("SELECT * FROM guardian_outbox WHERE sent = 0 ORDER BY createdAt")
    fun observePending(): Flow<List<GuardianMessageEntity>>

    @Query("SELECT * FROM guardian_outbox WHERE sent = 0 ORDER BY createdAt")
    suspend fun pending(): List<GuardianMessageEntity>

    @Query("UPDATE guardian_outbox SET sent = 1 WHERE id IN (:ids)")
    suspend fun markSent(ids: List<Long>)
}

@Database(
    entities = [
        RuleEntity::class, DayResultEntity::class, TriggerLogEntity::class, TamperEventEntity::class,
        UnlockRequestEntity::class, EnforcementEventEntity::class, ViolationEntity::class,
        CategoryOverrideEntity::class, GuardianMessageEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rules(): RuleDao
    abstract fun days(): DayResultDao
    abstract fun triggers(): TriggerLogDao
    abstract fun tamper(): TamperEventDao
    abstract fun unlocks(): UnlockDao
    abstract fun enforcement(): EnforcementEventDao
    abstract fun violations(): ViolationDao
    abstract fun categories(): CategoryOverrideDao
    abstract fun outbox(): GuardianOutboxDao

    companion object {
        /** Opens the SQLCipher-encrypted database. The passphrase lives in [com.screenusdeletus.security.SecurePrefs]. */
        fun open(context: Context, passphrase: ByteArray): AppDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "screenusdeletus.db")
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .build()
        }
    }
}

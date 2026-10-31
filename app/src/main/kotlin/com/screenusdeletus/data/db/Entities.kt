package com.screenusdeletus.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Comma separated package names. */
    val packages: String,
    /** Comma separated [com.screenusdeletus.core.AppCategory] names. */
    val categories: String,
    val dailyLimitMs: Long,
    /** `start-end:days;start-end:days` in minutes, days as ISO digits. */
    val blockedHours: String,
    val escalationLevel: String,
    val enabled: Boolean,
    val createdAt: Long,
    val integrityTag: String,
    /** A weakened version of this rule waiting out its cooling-off period, encoded like the row. */
    val pendingEncoded: String? = null,
    val pendingAppliesAt: Long? = null,
)

@Entity(tableName = "day_results")
data class DayResultEntity(
    @PrimaryKey val epochDay: Long,
    val usageMs: Long,
    val screenOnMs: Long,
    val goalMs: Long,
    val violations: Int,
    val emergencyUnlocks: Int,
    val pickups: Int,
    /** `package=ms,package=ms` */
    val perApp: String,
    /** 24 comma separated hourly totals. */
    val hourly: String,
)

@Entity(tableName = "trigger_logs", indices = [Index("timestamp")])
data class TriggerLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val appOpened: String,
    val hourOfDay: Int,
    val dayOfWeek: Int,
    val userReportedTrigger: String?,
    val moodScore: Int,
    val stressLevel: Int,
    val proceeded: Boolean,
    val note: String? = null,
)

@Entity(tableName = "tamper_events", indices = [Index("timestamp")])
data class TamperEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: String,
    val detail: String?,
    val reportedToGuardian: Boolean = false,
)

@Entity(tableName = "unlock_requests", indices = [Index("requestedAt")])
data class UnlockRequestEntity(
    @PrimaryKey val requestId: String,
    val requestedAt: Long,
    /** null while pending / when denied. */
    val grantedAt: Long?,
    val durationMs: Long,
    val userExplanation: String,
    /** "self" or "guardian". */
    val approvedBy: String?,
    val guardianResponse: String?,
    val status: String,
)

@Entity(tableName = "enforcement_events", indices = [Index("timestamp")])
data class EnforcementEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val packageName: String,
    val ruleId: String?,
    /** ALLOW_PASS, WARN, INTERSTITIAL, INTERSTITIAL_PROCEEDED, BLOCK, SETTINGS_BLOCKED */
    val action: String,
    val detail: String?,
)

@Entity(tableName = "violations", indices = [Index(value = ["epochDay", "ruleId", "kind"])])
data class ViolationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val timestamp: Long,
    val ruleId: String?,
    /** LIMIT_EXCEEDED, INTERSTITIAL_PROCEEDED, EMERGENCY_UNLOCK */
    val kind: String,
    val penalty: Double,
)

@Entity(tableName = "category_overrides")
data class CategoryOverrideEntity(
    @PrimaryKey val packageName: String,
    val category: String,
)

/** Messages for the guardian that could not be sent automatically. */
@Entity(tableName = "guardian_outbox")
data class GuardianMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val subject: String,
    val body: String,
    val sent: Boolean = false,
)

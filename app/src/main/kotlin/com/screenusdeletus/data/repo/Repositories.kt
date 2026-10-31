package com.screenusdeletus.data.repo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.screenusdeletus.core.CommitmentDevice
import com.screenusdeletus.core.CommitmentLedger
import com.screenusdeletus.core.DayResult
import com.screenusdeletus.core.InsightInput
import com.screenusdeletus.core.InsightsEngine
import com.screenusdeletus.core.ProtectionLevel
import com.screenusdeletus.core.StreakCalculator
import com.screenusdeletus.core.StreakData
import com.screenusdeletus.core.TriggerLog
import com.screenusdeletus.core.UnlockEligibility
import com.screenusdeletus.core.UnlockPolicy
import com.screenusdeletus.core.UnlockSettings
import com.screenusdeletus.core.formatDuration
import com.screenusdeletus.data.db.AppDatabase
import com.screenusdeletus.data.db.DayResultEntity
import com.screenusdeletus.data.db.GuardianMessageEntity
import com.screenusdeletus.data.db.TriggerLogEntity
import com.screenusdeletus.data.db.UnlockRequestEntity
import com.screenusdeletus.data.db.ViolationEntity
import com.screenusdeletus.security.Keys
import com.screenusdeletus.security.SecurePrefs
import com.screenusdeletus.util.Notifications
import com.screenusdeletus.util.Time
import org.json.JSONObject
import java.util.UUID

class ProfileRepository(private val prefs: SecurePrefs) {
    var profile: BehaviorProfile
        get() = BehaviorProfile.fromJson(prefs.string(Keys.PROFILE_JSON))
        set(v) = prefs.put(Keys.PROFILE_JSON, v.toJson())

    var dailyGoalMs: Long
        get() = prefs.long(Keys.DAILY_GOAL_MS, profile.goalDailyMinutes * 60_000L)
        set(v) = prefs.put(Keys.DAILY_GOAL_MS, v)

    var onboardingComplete: Boolean
        get() = prefs.bool(Keys.ONBOARDING_COMPLETE)
        set(v) = prefs.put(Keys.ONBOARDING_COMPLETE, v)
}

/**
 * The trusted guardian: approves unlocks with their own PIN and receives alerts. Without a
 * backend, alerts go out by SMS (if enabled and permitted) or wait in an outbox that the user is
 * nagged to send by email.
 */
class GuardianRepository(
    private val context: Context,
    private val prefs: SecurePrefs,
    private val db: AppDatabase,
) {
    var guardian: TrustedGuardian?
        get() = TrustedGuardian.fromJson(prefs.string(Keys.GUARDIAN_JSON))
        set(v) = prefs.put(Keys.GUARDIAN_JSON, v?.toJson())

    val isConfigured: Boolean get() = guardian != null && prefs.string(Keys.GUARDIAN_PIN_HASH) != null

    fun observeOutbox() = db.outbox().observePending()

    fun canSendSms(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

    suspend fun notify(subject: String, body: String) {
        val g = guardian ?: return
        if (g.smsAlerts && g.phone.isNotBlank() && canSendSms() && sendSms(g.phone, "Screenus Deletus: $subject\n$body")) return
        db.outbox().insert(GuardianMessageEntity(createdAt = Time.now(), subject = subject, body = body))
        Notifications.alert(
            context, Notifications.ID_GUARDIAN_OUTBOX,
            "Message for ${g.name}",
            "You promised to keep ${g.name} informed: \"$subject\". Tap to send it.",
            route = "guardian",
        )
    }

    suspend fun pending() = db.outbox().pending()

    suspend fun markSent(ids: List<Long>) = db.outbox().markSent(ids)

    @Suppress("DEPRECATION")
    private fun sendSms(phone: String, text: String): Boolean = try {
        val sms = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
        sms.sendMultipartTextMessage(phone, null, sms.divideMessage(text), null, null)
        true
    } catch (e: Exception) {
        Log.w("Guardian", "SMS failed", e)
        false
    }
}

class CommitmentRepository(private val prefs: SecurePrefs) {
    var commitment: CommitmentDevice?
        get() = commitmentFromJson(prefs.string(Keys.COMMITMENT_JSON))
        set(v) = prefs.put(Keys.COMMITMENT_JSON, v?.toJson())

    /** @return the penalty added, 0 if no active commitment */
    fun recordViolation(): Double {
        val c = commitment ?: return 0.0
        val (updated, penalty) = CommitmentLedger.recordViolation(c)
        commitment = updated
        return penalty
    }

    fun settle(amount: Double) {
        commitment = commitment?.let { CommitmentLedger.settle(it, amount) }
    }

    fun newWeek() {
        commitment = commitment?.let(CommitmentLedger::newWeek)
    }
}

class TriggerRepository(private val db: AppDatabase) {
    suspend fun log(t: TriggerLog, note: String? = null) {
        db.triggers().insert(
            TriggerLogEntity(
                timestamp = t.timestamp, appOpened = t.appOpened, hourOfDay = t.hourOfDay, dayOfWeek = t.isoDayOfWeek,
                userReportedTrigger = t.userReportedTrigger, moodScore = t.moodScore, stressLevel = t.stressLevel,
                proceeded = t.proceeded, note = note,
            ),
        )
    }

    suspend fun since(since: Long): List<TriggerLog> = db.triggers().since(since).map { it.toModel() }

    fun observeSince(since: Long) = db.triggers().observeSince(since)

    /** Trigger → frequency */
    suspend fun analyze(since: Long): Map<String, Int> =
        since(since).mapNotNull { it.userReportedTrigger }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.associate { it.key to it.value }
}

fun TriggerLogEntity.toModel() = TriggerLog(timestamp, appOpened, hourOfDay, dayOfWeek, userReportedTrigger, moodScore, stressLevel, proceeded)

/**
 * Records rule violations once per rule/kind/day (unlocks every time) and charges the commitment
 * device. Violations break streaks.
 */
class ViolationRecorder(
    private val db: AppDatabase,
    private val commitment: CommitmentRepository,
    private val guardian: GuardianRepository,
    private val context: Context,
) {
    suspend fun record(ruleId: String?, kind: String, oncePerDay: Boolean = true) {
        val day = Time.epochDay()
        if (oncePerDay && ruleId != null && db.violations().count(day, ruleId, kind) > 0) return
        val penalty = commitment.recordViolation()
        db.violations().insert(ViolationEntity(epochDay = day, timestamp = Time.now(), ruleId = ruleId, kind = kind, penalty = penalty))
        if (penalty > 0) {
            val c = commitment.commitment
            Notifications.alert(
                context, Notifications.ID_COMMITMENT,
                "Commitment: %.2f %s owed".format(penalty, c?.currency ?: ""),
                "That's %.2f %s outstanding for ${c?.charityName}. Settle it in Screenus Deletus.".format(c?.outstanding ?: 0.0, c?.currency ?: ""),
                route = "commitment",
            )
            if (guardian.guardian != null) {
                guardian.notify("Commitment penalty", "A rule was broken ($kind). Penalty: %.2f %s.".format(penalty, c?.currency ?: ""))
            }
        }
    }

    fun observeRecent() = db.violations().observeRecent(50)

    companion object {
        const val LIMIT_EXCEEDED = "LIMIT_EXCEEDED"
        const val INTERSTITIAL_PROCEEDED = "INTERSTITIAL_PROCEEDED"
        const val EMERGENCY_UNLOCK = "EMERGENCY_UNLOCK"
    }
}

class UnlockRepository(
    private val db: AppDatabase,
    private val prefs: SecurePrefs,
) {
    /** Settings in force. Loosening changes wait for the next week. */
    var settings: UnlockSettings
        get() {
            applyPendingIfDue()
            return unlockSettingsFromJson(prefs.string(Keys.UNLOCK_SETTINGS_JSON))
        }
        private set(v) = prefs.put(Keys.UNLOCK_SETTINGS_JSON, v.toJson())

    val pendingSettings: Pair<UnlockSettings, Long>?
        get() = prefs.string(Keys.UNLOCK_SETTINGS_PENDING_JSON)?.let { s ->
            val o = JSONObject(s)
            unlockSettingsFromJson(o.getString("settings")) to o.getLong("at")
        }

    /** @return the time the change takes effect (now for tightening changes). */
    fun updateSettings(new: UnlockSettings, bypass: Boolean = false): Long {
        val current = settings
        if (bypass || !current.isLoosenedBy(new)) {
            settings = new
            prefs.remove(Keys.UNLOCK_SETTINGS_PENDING_JSON)
            return Time.now()
        }
        val at = Time.weekStart() + 7 * 24 * 3_600_000L
        prefs.put(Keys.UNLOCK_SETTINGS_PENDING_JSON, JSONObject().put("settings", new.toJson()).put("at", at).toString())
        return at
    }

    private fun applyPendingIfDue() {
        val p = prefs.string(Keys.UNLOCK_SETTINGS_PENDING_JSON) ?: return
        val o = JSONObject(p)
        if (o.getLong("at") <= Time.now()) {
            prefs.put(Keys.UNLOCK_SETTINGS_JSON, o.getString("settings"))
            prefs.remove(Keys.UNLOCK_SETTINGS_PENDING_JSON)
        }
    }

    val unlockedUntil: Long get() = prefs.long(Keys.UNLOCKED_UNTIL)

    suspend fun eligibility(guardianConfigured: Boolean, protection: ProtectionLevel): UnlockEligibility {
        val now = Time.now()
        val granted = db.unlocks().grantedSince(now - 14 * 24 * 3_600_000L).mapNotNull { it.grantedAt }
        return UnlockPolicy.eligibility(settings, granted, now, Time.weekStart(now), guardianConfigured, protection)
    }

    suspend fun unlocksThisWeek(): Int = db.unlocks().grantedSince(Time.weekStart()).size

    fun observeGrantedThisWeek() = db.unlocks().observeGrantedSince(Time.weekStart())

    fun observeHistory() = db.unlocks().observeRecent(30)

    suspend fun grant(reason: String, approvedBy: String): Long {
        val now = Time.now()
        val duration = settings.unlockDurationMs
        db.unlocks().upsert(
            UnlockRequestEntity(
                requestId = UUID.randomUUID().toString(), requestedAt = now, grantedAt = now, durationMs = duration,
                userExplanation = reason, approvedBy = approvedBy, guardianResponse = null, status = "GRANTED",
            ),
        )
        prefs.putNow(Keys.UNLOCKED_UNTIL, now + duration)
        return now + duration
    }

    suspend fun logAbandoned(reason: String) {
        val now = Time.now()
        db.unlocks().upsert(
            UnlockRequestEntity(UUID.randomUUID().toString(), now, null, 0, reason, null, null, "ABANDONED"),
        )
    }

    fun endUnlockNow() = prefs.putNow(Keys.UNLOCKED_UNTIL, 0)

    // ---- interstitial passes -------------------------------------------------------------------

    fun interstitialPasses(): Map<String, Long> {
        val s = prefs.string(Keys.INTERSTITIAL_PASSES_JSON) ?: return emptyMap()
        val o = JSONObject(s)
        val now = Time.now()
        return o.keys().asSequence().associateWith { o.getLong(it) }.filterValues { it > now }
    }

    fun grantPass(pkg: String, durationMs: Long) {
        val passes = interstitialPasses().toMutableMap()
        passes[pkg] = Time.now() + durationMs
        prefs.put(Keys.INTERSTITIAL_PASSES_JSON, JSONObject(passes as Map<*, *>).toString())
    }
}

data class DashboardData(
    val todayUsageMs: Long,
    val screenOnMs: Long,
    val pickups: Int,
    val goalMs: Long,
    val weeklyAverageMs: Long,
    val last30Days: List<DayResultEntity>,
    val appBreakdown: List<Pair<String, Long>>,
    val categoryBreakdown: Map<String, Long>,
    val hourlyToday: LongArray,
    /** [day 0..6 (Mon..Sun)][hour] averaged over the last 4 weeks. */
    val heatMap: Array<LongArray>,
    val triggerAnalysis: Map<String, Int>,
    val streak: StreakData,
    val unlocksThisWeek: Int,
    val maxUnlocksPerWeek: Int,
    val complianceScore: Double,
    val insights: List<String>,
)

class StatsRepository(
    private val db: AppDatabase,
    private val usage: UsageRepository,
    private val catalog: AppCatalog,
    private val rules: RulesRepository,
    private val profile: ProfileRepository,
    private val triggers: TriggerRepository,
    private val unlocks: UnlockRepository,
) {
    /** Writes results for every completed day not yet recorded (usage events are kept ~a week). */
    suspend fun rollOverCompletedDays(): List<Long> {
        val today = Time.epochDay()
        val recorded = db.days().since(today - 8).map { it.epochDay }.toSet()
        val written = mutableListOf<Long>()
        for (day in (today - 7) until today) {
            if (day in recorded) continue
            val s = usage.summaryForDay(day)
            if (s.perPackageMs.isEmpty() && s.screenOnMs == 0L) continue // no data (before install / no access)
            val ruleList = rules.activeRules()
            var violations = db.violations().countForDay(day)
            // Limits exceeded on SOFT/MEDIUM rules count as violations even without an unlock.
            if (violations == 0) {
                violations = ruleList.count { r -> r.dailyLimitMs >= 0 && usage.usageForRule(r, s) > r.dailyLimitMs }
            }
            val unlocksThatDay = db.unlocks().grantedSince(Time.dayStart(day)).count { (it.grantedAt ?: 0) < Time.dayEnd(day) }
            db.days().upsert(
                DayResultEntity(
                    epochDay = day, usageMs = s.totalAppMs, screenOnMs = s.screenOnMs, goalMs = profile.dailyGoalMs,
                    violations = violations, emergencyUnlocks = unlocksThatDay, pickups = s.pickups,
                    perApp = s.perPackageMs.entries.joinToString(",") { "${it.key}=${it.value}" },
                    hourly = s.hourlyMs.joinToString(","),
                ),
            )
            written += day
        }
        return written
    }

    suspend fun streak(): StreakData {
        val days = db.days().all().map { it.toResult() }
        return StreakCalculator.compute(days, Time.epochDay())
    }

    suspend fun dashboard(): DashboardData {
        val today = usage.today()
        val todayDay = Time.epochDay()
        val history = db.days().since(todayDay - 30)
        val last7 = history.filter { it.epochDay >= todayDay - 7 }
        val streak = StreakCalculator.compute(db.days().all().map { it.toResult() }, todayDay)

        val heat = Array(7) { LongArray(24) }
        val weeksCounted = IntArray(7)
        history.filter { it.epochDay >= todayDay - 28 }.forEach { d ->
            val dow = java.time.LocalDate.ofEpochDay(d.epochDay).dayOfWeek.value - 1
            weeksCounted[dow]++
            d.hourly.split(',').mapNotNull { it.toLongOrNull() }.forEachIndexed { h, ms -> if (h < 24) heat[dow][h] += ms }
        }
        for (d in 0 until 7) if (weeksCounted[d] > 0) for (h in 0 until 24) heat[d][h] /= weeksCounted[d]

        val hourly7 = LongArray(24)
        last7.forEach { d -> d.hourly.split(',').mapNotNull { it.toLongOrNull() }.forEachIndexed { h, ms -> if (h < 24) hourly7[h] += ms } }
        today.hourlyMs.forEachIndexed { h, ms -> hourly7[h] += ms }

        val apps = today.perPackageMs.entries.sortedByDescending { it.value }.map { it.key to it.value }
        val categories = today.perPackageMs.entries.groupBy { catalog.categoryOf(it.key).label }
            .mapValues { (_, v) -> v.sumOf { it.value } }
            .entries.sortedByDescending { it.value }.associate { it.key to it.value }

        val weekAgo = Time.now() - 7 * 24 * 3_600_000L
        val triggerList = triggers.since(weekAgo)
        val unlocksThisWeek = unlocks.unlocksThisWeek()
        val maxUnlocks = unlocks.settings.maxUnlocksPerWeek

        val insights = InsightsEngine.generate(
            InsightInput(
                hourlyMsLast7Days = hourly7,
                dailyTotalsMs = last7.map { it.usageMs },
                goalMs = profile.dailyGoalMs,
                triggers = triggerList,
                streak = streak,
                unlocksThisWeek = unlocksThisWeek,
                maxUnlocksPerWeek = maxUnlocks,
                topApps = apps.take(10),
                pickupsToday = today.pickups,
            ),
            appLabel = catalog::label,
        )

        return DashboardData(
            todayUsageMs = today.totalAppMs,
            screenOnMs = today.screenOnMs,
            pickups = today.pickups,
            goalMs = profile.dailyGoalMs,
            weeklyAverageMs = if (last7.isEmpty()) today.totalAppMs else last7.map { it.usageMs }.average().toLong(),
            last30Days = history,
            appBreakdown = apps,
            categoryBreakdown = categories,
            hourlyToday = today.hourlyMs,
            heatMap = heat,
            triggerAnalysis = triggerList.mapNotNull { it.userReportedTrigger }.groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }.associate { it.key to it.value },
            streak = streak,
            unlocksThisWeek = unlocksThisWeek,
            maxUnlocksPerWeek = maxUnlocks,
            complianceScore = StreakCalculator.complianceScore(history.map { it.toResult() }),
            insights = insights,
        )
    }

    suspend fun weeklyReport(): String {
        val d = dashboard()
        val week = d.last30Days.filter { it.epochDay >= Time.epochDay() - 7 }
        return buildString {
            appendLine("Screenus Deletus weekly report")
            appendLine("Average per day: ${formatDuration(d.weeklyAverageMs)} (goal ${formatDuration(d.goalMs)})")
            appendLine("Days on target: ${week.count { it.toResult().successful }}/${week.size}")
            appendLine("Current streak: ${d.streak.currentStreak} days (best ${d.streak.longestStreak})")
            appendLine("Emergency unlocks this week: ${d.unlocksThisWeek}/${d.maxUnlocksPerWeek}")
            appendLine("Compliance (30 days): ${d.complianceScore.toInt()}%")
            if (d.triggerAnalysis.isNotEmpty()) {
                appendLine("Top triggers: " + d.triggerAnalysis.entries.take(3).joinToString { "${it.key} (${it.value})" })
            }
        }
    }
}

fun DayResultEntity.toResult() = DayResult(epochDay, usageMs, goalMs, violations, emergencyUnlocks)

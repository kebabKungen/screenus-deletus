package com.timeguard.enforcement

import android.content.Context
import android.content.Intent
import android.util.Log
import com.timeguard.core.Decision
import com.timeguard.core.EnforcementContext
import com.timeguard.core.EscalationLevel
import com.timeguard.core.ProtectionLevel
import com.timeguard.core.RuleEngine
import com.timeguard.data.db.AppDatabase
import com.timeguard.data.db.EnforcementEventEntity
import com.timeguard.data.repo.AppCatalog
import com.timeguard.data.repo.ProfileRepository
import com.timeguard.data.repo.RulesRepository
import com.timeguard.data.repo.UnlockRepository
import com.timeguard.data.repo.UsageRepository
import com.timeguard.data.repo.ViolationRecorder
import com.timeguard.security.TamperMonitor
import com.timeguard.util.Notifications
import com.timeguard.util.Time
import java.util.concurrent.ConcurrentHashMap

/**
 * Glue between "this package is now in the foreground" and the [RuleEngine]'s decision: shows
 * warnings, interstitials and block screens, and records what happened.
 */
class EnforcementController(
    private val context: Context,
    private val db: AppDatabase,
    private val rules: RulesRepository,
    private val usage: UsageRepository,
    private val catalog: AppCatalog,
    private val unlocks: UnlockRepository,
    private val tamper: TamperMonitor,
    private val profile: ProfileRepository,
    private val violations: ViolationRecorder,
) {
    private val engine = RuleEngine()
    private val lastBlockShown = ConcurrentHashMap<String, Long>()
    private val warned = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    var currentForeground: String? = null
        private set

    /** What happened, so callers (the accessibility service) can e.g. press HOME. */
    enum class Outcome { NONE, WARNED, INTERSTITIAL, BLOCKED }

    suspend fun onForeground(pkg: String): Outcome {
        currentForeground = pkg
        if (!profile.onboardingComplete) return Outcome.NONE
        if (pkg == context.packageName || pkg in catalog.launcherPackages) return Outcome.NONE

        val ruleList = rules.activeRules()
        if (ruleList.isEmpty()) return Outcome.NONE

        val now = Time.now()
        val ctx = EnforcementContext(
            nowMillis = now,
            minuteOfDay = Time.minuteOfDay(now),
            isoDayOfWeek = Time.isoDayOfWeek(now),
            protectionLevel = tamper.protectionLevel,
            deviceCompromised = tamper.deviceCompromised,
            unlockedUntil = unlocks.unlockedUntil,
            interstitialPasses = unlocks.interstitialPasses(),
        )
        val today = usage.today()
        val dayStart = Time.startOfToday()
        val category = catalog.categoryOf(pkg)
        val interstitialCounts = HashMap<String, Int>()
        for (r in ruleList) {
            if (r.appliesTo(pkg, category) && r.escalationLevel != EscalationLevel.HARD) {
                interstitialCounts[r.id] = db.enforcement().count(r.id, ACTION_INTERSTITIAL, dayStart)
            }
        }

        val decision = engine.evaluate(
            packageName = pkg,
            category = category,
            rules = ruleList,
            ctx = ctx,
            usageForRule = { r -> today.perPackageMs.entries.sumOf { (p, ms) -> if (r.appliesTo(p, catalog.categoryOf(p))) ms else 0L } },
            interstitialsToday = { r -> interstitialCounts[r.id] ?: 0 },
        )

        return when (decision) {
            Decision.Allow -> Outcome.NONE
            is Decision.Warn -> {
                val key = "${Time.epochDay()}:${decision.ruleId}:${if (decision.remainingMs <= 0) "over" else "near"}"
                if (warned.add(key)) {
                    Notifications.warn(context, decision.ruleId, if (decision.remainingMs <= 0) "Limit reached" else "Almost at your limit", decision.message)
                    log(pkg, decision.ruleId, "WARN", decision.message)
                    if (decision.remainingMs <= 0) violations.record(decision.ruleId, ViolationRecorder.LIMIT_EXCEEDED)
                }
                Outcome.WARNED
            }
            is Decision.Interstitial -> {
                if (showScreen(pkg, BlockingActivity.MODE_INTERSTITIAL, decision.reason, decision.ruleId, decision.delaySeconds)) {
                    log(pkg, decision.ruleId, ACTION_INTERSTITIAL, decision.reason)
                }
                Outcome.INTERSTITIAL
            }
            is Decision.Block -> {
                if (showScreen(pkg, BlockingActivity.MODE_BLOCK, decision.reason, decision.ruleId, 0)) {
                    log(pkg, decision.ruleId, "BLOCK", decision.reason)
                }
                Outcome.BLOCKED
            }
        }
    }

    /** User sat through an interstitial and chose to continue. */
    suspend fun onInterstitialProceeded(pkg: String, ruleId: String?) {
        unlocks.grantPass(pkg, PASS_DURATION_MS)
        log(pkg, ruleId, "INTERSTITIAL_PROCEEDED", null)
        violations.record(ruleId, ViolationRecorder.INTERSTITIAL_PROCEEDED)
    }

    private fun showScreen(pkg: String, mode: String, reason: String, ruleId: String?, delay: Int): Boolean {
        val now = Time.now()
        val last = lastBlockShown[pkg] ?: 0L
        if (now - last < 1_500) return false // the screen is already up
        lastBlockShown[pkg] = now
        try {
            context.startActivity(
                Intent(context, BlockingActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    .putExtra(BlockingActivity.EXTRA_PACKAGE, pkg)
                    .putExtra(BlockingActivity.EXTRA_MODE, mode)
                    .putExtra(BlockingActivity.EXTRA_REASON, reason)
                    .putExtra(BlockingActivity.EXTRA_RULE_ID, ruleId)
                    .putExtra(BlockingActivity.EXTRA_DELAY, delay)
                    .putExtra(BlockingActivity.EXTRA_LOCKDOWN, tamper.protectionLevel == ProtectionLevel.LOCKDOWN),
            )
        } catch (e: Exception) {
            Log.e("Enforcement", "Could not show block screen", e)
            return false
        }
        return true
    }

    private suspend fun log(pkg: String, ruleId: String?, action: String, detail: String?) {
        db.enforcement().insert(EnforcementEventEntity(timestamp = Time.now(), packageName = pkg, ruleId = ruleId, action = action, detail = detail))
    }

    fun observeBlocksToday() = db.enforcement().observeBlocksSince(Time.startOfToday())

    companion object {
        const val ACTION_INTERSTITIAL = "INTERSTITIAL"
        const val PASS_DURATION_MS = 5 * 60 * 1000L
    }
}

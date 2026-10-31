package com.screenusdeletus.security

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import com.screenusdeletus.core.ProtectionLevel
import com.screenusdeletus.core.TamperEvent
import com.screenusdeletus.core.TamperPolicy
import com.screenusdeletus.core.TamperType
import com.screenusdeletus.data.db.AppDatabase
import com.screenusdeletus.data.db.TamperEventEntity
import com.screenusdeletus.data.repo.GuardianRepository
import com.screenusdeletus.data.repo.UsageRepository
import com.screenusdeletus.monitoring.AppMonitoringService
import com.screenusdeletus.util.Notifications
import com.screenusdeletus.util.Time
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ProtectionStatus(
    val deviceAdminActive: Boolean,
    val accessibilityEnabled: Boolean,
    val usageAccess: Boolean,
    val overlayPermission: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val level: ProtectionLevel,
    val levelUntil: Long?,
    val tamperScore: Int,
    val integrity: IntegrityChecker.Report?,
    val disarmedUntil: Long,
    val disarmRequestedAt: Long,
) {
    val allProtectionsOn: Boolean get() = deviceAdminActive && accessibilityEnabled && usageAccess
}

/**
 * Watches every protection Screenus Deletus relies on, records tamper events on state transitions and
 * derives the current [ProtectionLevel] (progressive lockdown) from the tamper history.
 *
 * Also owns the "protection pause" (disarm) flow: the only sanctioned way to turn protections
 * off is to request a pause and wait out a delay, or have the guardian approve it.
 */
class TamperMonitor(
    private val context: Context,
    private val prefs: SecurePrefs,
    private val db: AppDatabase,
    private val integrity: IntegrityChecker,
    private val usage: UsageRepository,
    private val guardian: GuardianRepository,
) {
    private val mutex = Mutex()
    private val _status = MutableStateFlow<ProtectionStatus?>(null)
    val status: StateFlow<ProtectionStatus?> = _status.asStateFlow()

    @Volatile
    private var lastIntegrityReport: IntegrityChecker.Report? = null
    private var lastIntegrityAt = 0L

    val adminComponent = ComponentName(context, GuardDeviceAdminReceiver::class.java)

    val protectionLevel: ProtectionLevel
        get() {
            val until = prefs.long(Keys.PROTECTION_UNTIL)
            val level = runCatching { ProtectionLevel.valueOf(prefs.string(Keys.PROTECTION_LEVEL) ?: "NORMAL") }
                .getOrDefault(ProtectionLevel.NORMAL)
            // A timed lockdown that has expired is treated as over even before the next check runs.
            return if (level != ProtectionLevel.NORMAL && until > 0 && until <= Time.now()) ProtectionLevel.RESTRICTED else level
        }

    val deviceCompromised: Boolean get() = prefs.bool(Keys.LAST_KNOWN_ROOT)

    // ---- protection pause ----------------------------------------------------------------------

    val disarmDelayMs: Long get() = prefs.long(Keys.DISARM_DELAY_MS, DEFAULT_DISARM_DELAY)
    val isDisarmed: Boolean get() = prefs.long(Keys.DISARMED_UNTIL) > Time.now()
    fun setDisarmDelay(ms: Long) {
        if (ms > disarmDelayMs || isDisarmed) prefs.put(Keys.DISARM_DELAY_MS, ms)
    }

    suspend fun requestDisarm() {
        if (prefs.long(Keys.DISARM_REQUESTED_AT) == 0L) {
            prefs.putNow(Keys.DISARM_REQUESTED_AT, Time.now())
            guardian.notify(
                "Protection pause requested",
                "A pause of Screenus Deletus's protections was requested. It starts in ${disarmDelayMs / 60_000} minutes.",
            )
        }
        refreshDisarm()
    }

    fun cancelDisarm() {
        prefs.putNow(Keys.DISARM_REQUESTED_AT, 0)
        prefs.putNow(Keys.DISARMED_UNTIL, 0)
    }

    /** Guardian approved: pause now. */
    fun disarmNow() {
        prefs.putNow(Keys.DISARM_REQUESTED_AT, 0)
        prefs.putNow(Keys.DISARMED_UNTIL, Time.now() + DISARM_WINDOW_MS)
    }

    /** Moves a pending disarm request into the paused state once its delay has elapsed. */
    fun refreshDisarm() {
        val requested = prefs.long(Keys.DISARM_REQUESTED_AT)
        if (requested > 0 && Time.now() >= requested + disarmDelayMs) disarmNow()
    }

    // ---- checks --------------------------------------------------------------------------------

    fun isDeviceAdminActive(): Boolean =
        context.getSystemService(DevicePolicyManager::class.java).isAdminActive(adminComponent)

    fun isAccessibilityEnabled(): Boolean {
        if (AppMonitoringService.running) return true
        val ours = ComponentName(context, AppMonitoringService::class.java)
        // The secure setting is the source of truth for "switched on"; it accepts both the full
        // and the short (".monitoring.X") component forms.
        val setting = android.provider.Settings.Secure.getString(
            context.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        val listed = setting.split(':').any { entry ->
            ComponentName.unflattenFromString(entry.trim())?.let { it == ours } == true
        }
        if (listed) return true
        val am = context.getSystemService(AccessibilityManager::class.java)
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
            it.resolveInfo?.serviceInfo?.let { si -> ComponentName(si.packageName, si.name) } == ours
        }
    }

    fun hasOverlayPermission(): Boolean = android.provider.Settings.canDrawOverlays(context)

    fun isIgnoringBatteryOptimizations(): Boolean =
        context.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /**
     * Runs all checks. Cheap checks run every time; the APK hash runs at most hourly unless
     * [forceIntegrity] is set.
     */
    suspend fun check(forceIntegrity: Boolean = false): ProtectionStatus = withContext(Dispatchers.IO) { mutex.withLock {
        refreshDisarm()
        val setupDone = prefs.bool(Keys.ONBOARDING_COMPLETE)
        val now = Time.now()

        val admin = isDeviceAdminActive()
        val access = isAccessibilityEnabled()
        val usageOk = usage.hasUsageAccess()

        if (setupDone) {
            transition(Keys.LAST_KNOWN_ADMIN, admin, TamperType.DEVICE_ADMIN_DISABLED, "Device admin is not active")
            transition(Keys.LAST_KNOWN_ACCESSIBILITY, access, TamperType.ACCESSIBILITY_DISABLED, "Accessibility service is off")
            transition(Keys.LAST_KNOWN_USAGE_ACCESS, usageOk, TamperType.USAGE_ACCESS_REVOKED, "Usage access is not granted")
        } else {
            prefs.put(Keys.LAST_KNOWN_ADMIN, admin)
            prefs.put(Keys.LAST_KNOWN_ACCESSIBILITY, access)
            prefs.put(Keys.LAST_KNOWN_USAGE_ACCESS, usageOk)
        }

        if (forceIntegrity || now - lastIntegrityAt > INTEGRITY_INTERVAL_MS || lastIntegrityReport == null) {
            val report = integrity.runAll()
            lastIntegrityReport = report
            lastIntegrityAt = now
            if (setupDone) {
                if (!report.signatureValid) record(TamperType.SIGNATURE_MISMATCH, "Signing certificate changed")
                if (!report.apkIntact) record(TamperType.FILE_INTEGRITY_FAILED, "APK hash changed without an update")
                if (report.debuggerAttached) record(TamperType.FILE_INTEGRITY_FAILED, "Debugger attached to a release build")
            }
            val compromised = report.deviceCompromised
            if (compromised && !prefs.bool(Keys.LAST_KNOWN_ROOT) && setupDone) {
                record(TamperType.ROOT_DETECTED, (report.rootIndicators + report.hookingIndicators).joinToString("; "))
            }
            prefs.put(Keys.LAST_KNOWN_ROOT, compromised)
            if (report.adbEnabled && !prefs.bool(Keys.LAST_KNOWN_ADB) && setupDone) {
                record(TamperType.ADB_ENABLED, "USB debugging was turned on")
            }
            prefs.put(Keys.LAST_KNOWN_ADB, report.adbEnabled)
        }

        if (setupDone) checkProcessExits()
        checkClock()

        val disabled = setupDone && !isDisarmed && !(admin && access && usageOk)
        val verdict = recompute(disabled)
        val status = ProtectionStatus(
            deviceAdminActive = admin,
            accessibilityEnabled = access,
            usageAccess = usageOk,
            overlayPermission = hasOverlayPermission(),
            ignoringBatteryOptimizations = isIgnoringBatteryOptimizations(),
            level = verdict.level,
            levelUntil = verdict.until,
            tamperScore = verdict.score,
            integrity = lastIntegrityReport,
            disarmedUntil = prefs.long(Keys.DISARMED_UNTIL),
            disarmRequestedAt = prefs.long(Keys.DISARM_REQUESTED_AT),
        )
        _status.value = status
        status
    } }

    private suspend fun transition(key: String, nowOk: Boolean, type: TamperType, detail: String) {
        val wasOk = prefs.bool(key, true)
        if (wasOk && !nowOk) {
            record(type, detail)
            Notifications.alert(
                context, Notifications.ID_PROTECTION, "Screenus Deletus protection disabled",
                "$detail. Restricted mode is on until you re-enable it.",
            )
        }
        prefs.put(key, nowOk)
    }

    /** Android 11+ tells us exactly when the user force-stopped the app. */
    private suspend fun checkProcessExits() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val am = context.getSystemService(ActivityManager::class.java)
        val lastChecked = prefs.long(Keys.LAST_HEARTBEAT)
        if (lastChecked == 0L) {
            prefs.put(Keys.LAST_HEARTBEAT, Time.now())
            return
        }
        val exits = runCatching { am.getHistoricalProcessExitReasons(context.packageName, 0, 10) }.getOrDefault(emptyList())
        exits.filter { it.timestamp > lastChecked && it.reason == ApplicationExitInfo.REASON_USER_REQUESTED }
            .forEach { record(TamperType.FORCE_STOPPED, "Force-stopped at ${Time.formatDateTime(it.timestamp)}", it.timestamp) }
        prefs.put(Keys.LAST_HEARTBEAT, Time.now())
    }

    /**
     * Detects manual clock changes (a classic way to reset daily limits) by comparing the wall
     * clock against the monotonic clock. Reboots reset the monotonic clock, so a reboot simply
     * re-baselines.
     */
    private suspend fun checkClock() {
        val elapsed = SystemClock.elapsedRealtime()
        val delta = System.currentTimeMillis() - elapsed
        val previousDelta = prefs.long(Keys.LAST_WALL_ELAPSED_DELTA, Long.MIN_VALUE)
        val previousElapsed = prefs.long(Keys.LAST_ELAPSED, Long.MAX_VALUE)
        prefs.put(Keys.LAST_WALL_ELAPSED_DELTA, delta)
        prefs.put(Keys.LAST_ELAPSED, elapsed)
        // First run, or the device rebooted (monotonic clock restarted): just re-baseline.
        if (previousDelta == Long.MIN_VALUE || elapsed < previousElapsed) return
        // Network time sync corrects by seconds; a jump of more than 5 minutes is a manual change.
        val drift = delta - previousDelta
        if (kotlin.math.abs(drift) > CLOCK_TOLERANCE_MS && prefs.bool(Keys.ONBOARDING_COMPLETE)) {
            record(TamperType.CLOCK_CHANGED, "Clock moved by ${drift / 60_000} minutes")
        }
    }

    fun onBoot() {
        prefs.put(Keys.LAST_WALL_ELAPSED_DELTA, System.currentTimeMillis() - SystemClock.elapsedRealtime())
        prefs.put(Keys.LAST_ELAPSED, SystemClock.elapsedRealtime())
    }

    /**
     * Records a tamper event (de-duplicated per type for 10 minutes) and recomputes the
     * protection level. Severe events are forwarded to the guardian.
     */
    suspend fun record(type: TamperType, detail: String?, at: Long = Time.now()) {
        if (isDisarmed && type in PAUSABLE) {
            guardian.notify("Protection change during pause", "${type.label}. ${detail.orEmpty()}")
            return
        }
        val recent = db.tamper().since(at - DEDUPE_MS)
        if (type != TamperType.WRONG_PIN && recent.any { it.type == type.name }) return
        db.tamper().insert(TamperEventEntity(timestamp = at, type = type.name, detail = detail))
        if (type.severity >= 2) {
            guardian.notify("Tamper alert", "${type.label}. ${detail.orEmpty()} (${Time.formatDateTime(at)})")
        }
        recompute(protectionsDisabled = prefs.bool(Keys.ONBOARDING_COMPLETE) && !isDisarmed &&
            !(prefs.bool(Keys.LAST_KNOWN_ADMIN, true) && prefs.bool(Keys.LAST_KNOWN_ACCESSIBILITY, true) && prefs.bool(Keys.LAST_KNOWN_USAGE_ACCESS, true)))
    }

    private suspend fun recompute(protectionsDisabled: Boolean): com.screenusdeletus.core.ProtectionVerdict {
        val now = Time.now()
        val events = db.tamper().since(now - TamperPolicy.WINDOW_MS).mapNotNull { e ->
            runCatching { TamperEvent(e.timestamp, TamperType.valueOf(e.type)) }.getOrNull()
        }
        val verdict = TamperPolicy.verdict(events, now, protectionsDisabled)
        val previous = protectionLevel
        prefs.put(Keys.PROTECTION_LEVEL, verdict.level.name)
        prefs.put(Keys.PROTECTION_UNTIL, verdict.until ?: 0L)
        if (verdict.level == ProtectionLevel.LOCKDOWN && previous != ProtectionLevel.LOCKDOWN) {
            Notifications.alert(
                context, Notifications.ID_PROTECTION, "Lockdown mode",
                "Repeated tampering detected. Every rule is now enforced as a hard block" +
                    (verdict.until?.let { " until ${Time.formatClock(it)}" } ?: " until protections are restored") + ".",
            )
        }
        return verdict
    }

    fun observeEvents() = db.tamper().observeRecent(100)

    companion object {
        const val DEFAULT_DISARM_DELAY = 30 * 60 * 1000L
        const val DISARM_WINDOW_MS = 15 * 60 * 1000L
        private const val INTEGRITY_INTERVAL_MS = 60 * 60 * 1000L
        private const val DEDUPE_MS = 10 * 60 * 1000L
        private const val CLOCK_TOLERANCE_MS = 5 * 60 * 1000L
        private val PAUSABLE = setOf(
            TamperType.DEVICE_ADMIN_DISABLE_REQUESTED, TamperType.DEVICE_ADMIN_DISABLED,
            TamperType.ACCESSIBILITY_DISABLED, TamperType.USAGE_ACCESS_REVOKED,
            TamperType.FORCE_STOPPED,
        )
    }
}

package com.screenusdeletus.data.repo

import com.screenusdeletus.core.CommitmentDevice
import com.screenusdeletus.core.UnlockSettings
import org.json.JSONArray
import org.json.JSONObject

/** Answers from the onboarding questionnaire; used to personalise defaults and suggestions. */
data class BehaviorProfile(
    val estimatedDailyMinutes: Int = 240,
    val goalDailyMinutes: Int = 120,
    val problemCategories: List<String> = emptyList(),
    val worstTimes: List<String> = emptyList(),
    val preferredAlternatives: List<String> = emptyList(),
    val motivation: String = "",
) {
    fun toJson(): String = JSONObject()
        .put("estimated", estimatedDailyMinutes)
        .put("goal", goalDailyMinutes)
        .put("categories", JSONArray(problemCategories))
        .put("times", JSONArray(worstTimes))
        .put("alternatives", JSONArray(preferredAlternatives))
        .put("motivation", motivation)
        .toString()

    companion object {
        fun fromJson(s: String?): BehaviorProfile {
            if (s.isNullOrBlank()) return BehaviorProfile()
            val o = JSONObject(s)
            return BehaviorProfile(
                o.optInt("estimated", 240),
                o.optInt("goal", 120),
                o.optJSONArray("categories").toStringList(),
                o.optJSONArray("times").toStringList(),
                o.optJSONArray("alternatives").toStringList(),
                o.optString("motivation", ""),
            )
        }
    }
}

data class TrustedGuardian(
    val name: String,
    val email: String,
    val phone: String,
    /** Friend, Family, Partner, Therapist, Coach… */
    val relationshipType: String,
    /** When true, emergency unlocks require the guardian's PIN. */
    val canApproveUnlocks: Boolean = true,
    /** When true, the guardian's PIN lets a weakening rule change skip the cooling-off period. */
    val canModifyRules: Boolean = true,
    /** Text the guardian automatically (needs SMS permission). */
    val smsAlerts: Boolean = false,
    val weeklyReportDelivery: Boolean = true,
) {
    fun toJson(): String = JSONObject()
        .put("name", name).put("email", email).put("phone", phone).put("relationship", relationshipType)
        .put("approve", canApproveUnlocks).put("modify", canModifyRules)
        .put("sms", smsAlerts).put("weekly", weeklyReportDelivery)
        .toString()

    companion object {
        fun fromJson(s: String?): TrustedGuardian? {
            if (s.isNullOrBlank()) return null
            val o = JSONObject(s)
            return TrustedGuardian(
                o.optString("name"), o.optString("email"), o.optString("phone"), o.optString("relationship"),
                o.optBoolean("approve", true), o.optBoolean("modify", true),
                o.optBoolean("sms", false), o.optBoolean("weekly", true),
            )
        }
    }
}

fun UnlockSettings.toJson(): String = JSONObject()
    .put("max", maxUnlocksPerWeek).put("cooldown", cooldownMs).put("wait", baseWaitMs)
    .put("mult", waitMultiplier).put("maxWait", maxWaitMs).put("duration", unlockDurationMs)
    .put("challenge", requiresChallenge).put("guardian", requireGuardianApproval)
    .toString()

fun unlockSettingsFromJson(s: String?): UnlockSettings {
    if (s.isNullOrBlank()) return UnlockSettings()
    val o = JSONObject(s)
    val d = UnlockSettings()
    return UnlockSettings(
        o.optInt("max", d.maxUnlocksPerWeek), o.optLong("cooldown", d.cooldownMs), o.optLong("wait", d.baseWaitMs),
        o.optDouble("mult", d.waitMultiplier), o.optLong("maxWait", d.maxWaitMs), o.optLong("duration", d.unlockDurationMs),
        o.optBoolean("challenge", d.requiresChallenge), o.optBoolean("guardian", d.requireGuardianApproval),
    )
}

/** True when [new] is more permissive than [old]. */
fun UnlockSettings.isLoosenedBy(new: UnlockSettings): Boolean =
    new.maxUnlocksPerWeek > maxUnlocksPerWeek || new.cooldownMs < cooldownMs || new.baseWaitMs < baseWaitMs ||
        new.waitMultiplier < waitMultiplier || new.unlockDurationMs > unlockDurationMs ||
        (requiresChallenge && !new.requiresChallenge) || (requireGuardianApproval && !new.requireGuardianApproval)

fun CommitmentDevice.toJson(): String = JSONObject()
    .put("pledged", pledgedAmount).put("currency", currency).put("base", basePenalty)
    .put("mult", escalationMultiplier).put("charity", charityName).put("url", charityUrl)
    .put("week", violationsThisWeek).put("total", totalViolations).put("owed", totalOwed)
    .put("settled", totalSettled).put("active", active)
    .toString()

fun commitmentFromJson(s: String?): CommitmentDevice? {
    if (s.isNullOrBlank()) return null
    val o = JSONObject(s)
    return CommitmentDevice(
        pledgedAmount = o.optDouble("pledged", 0.0),
        currency = o.optString("currency", "USD"),
        basePenalty = o.optDouble("base", 1.0),
        escalationMultiplier = o.optDouble("mult", 1.5),
        charityName = o.optString("charity"),
        charityUrl = o.optString("url"),
        violationsThisWeek = o.optInt("week"),
        totalViolations = o.optInt("total"),
        totalOwed = o.optDouble("owed", 0.0),
        totalSettled = o.optDouble("settled", 0.0),
        active = o.optBoolean("active", true),
    )
}

fun JSONArray?.toStringList(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { getString(it) }

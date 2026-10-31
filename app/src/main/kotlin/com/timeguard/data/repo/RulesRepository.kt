package com.timeguard.data.repo

import com.timeguard.core.AppCategory
import com.timeguard.core.EscalationLevel
import com.timeguard.core.ScreenTimeRule
import com.timeguard.core.TamperType
import com.timeguard.core.TimeRange
import com.timeguard.data.db.RuleDao
import com.timeguard.data.db.RuleEntity
import com.timeguard.security.CryptoManager
import com.timeguard.security.Keys
import com.timeguard.security.SecurePrefs
import com.timeguard.util.Time
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class StoredRule(
    val rule: ScreenTimeRule,
    /** A weakened version (or deletion, when [pendingDelete]) waiting for its cooling-off period. */
    val pending: ScreenTimeRule?,
    val pendingDelete: Boolean,
    val pendingAppliesAt: Long?,
    val integrityOk: Boolean,
)

sealed interface SaveResult {
    data object Applied : SaveResult
    data class Scheduled(val appliesAt: Long) : SaveResult
}

/**
 * Stores rules with an integrity MAC and enforces a cooling-off period on any change that would
 * loosen restrictions. Tightening is always immediate.
 */
class RulesRepository(
    private val dao: RuleDao,
    private val crypto: CryptoManager,
    private val prefs: SecurePrefs,
    private val onTamper: suspend (TamperType, String) -> Unit,
) {
    private val mutex = Mutex()

    @Volatile
    private var cache: List<ScreenTimeRule>? = null

    val weakeningDelayMs: Long get() = prefs.long(Keys.WEAKENING_DELAY_MS, DEFAULT_WEAKENING_DELAY)

    fun setWeakeningDelay(ms: Long) {
        // Shortening the delay is itself a weakening change; only allow increases here.
        if (ms > weakeningDelayMs) prefs.put(Keys.WEAKENING_DELAY_MS, ms)
    }

    fun observe(): Flow<List<StoredRule>> = dao.observeAll().map { list -> list.map(::toStored) }

    /** Rules currently in force. Cached; promotes pending changes whose cooling-off has elapsed. */
    suspend fun activeRules(): List<ScreenTimeRule> {
        cache?.let { return it }
        return mutex.withLock {
            cache ?: load().also { cache = it }
        }
    }

    private suspend fun load(): List<ScreenTimeRule> {
        val now = Time.now()
        val rows = dao.getAll()
        verifyRuleSet(rows.map { it.id })
        var changed = false
        val result = mutableListOf<ScreenTimeRule>()
        for (row in rows) {
            val stored = toStored(row)
            if (stored.pendingAppliesAt != null && stored.pendingAppliesAt <= now) {
                changed = true
                if (stored.pendingDelete || stored.pending == null) {
                    dao.delete(row.id)
                    continue
                }
                val promoted = stored.pending.let { it.copy(integrityTag = crypto.sign(it.canonicalForm())) }
                dao.upsert(toEntity(promoted, null, null))
                result += promoted
                continue
            }
            if (!stored.integrityOk) {
                onTamper(TamperType.RULE_INTEGRITY_FAILED, "Rule '${row.name}' failed its integrity check")
                // Still enforce it — as strictly as possible.
                result += stored.rule.copy(escalationLevel = EscalationLevel.HARD)
            } else {
                result += stored.rule
            }
        }
        if (changed) commitDigest()
        return result
    }

    /** Detects rows inserted or deleted behind TimeGuard's back. */
    private suspend fun verifyRuleSet(ids: List<String>) {
        val digestInput = ids.sorted().joinToString(",")
        val stored = prefs.string(Keys.RULES_DIGEST)
        if (stored == null || !crypto.verify(digestInput, stored)) {
            if (stored != null) {
                onTamper(TamperType.RULE_INTEGRITY_FAILED, "The set of stored rules changed outside TimeGuard")
            }
            prefs.put(Keys.RULES_DIGEST, crypto.sign(digestInput))
        }
    }

    private suspend fun commitDigest() {
        prefs.put(Keys.RULES_DIGEST, crypto.sign(dao.getAll().map { it.id }.sorted().joinToString(",")))
    }

    fun invalidate() {
        cache = null
    }

    /**
     * @param bypassCoolingOff true when the guardian approved the change with their PIN.
     */
    suspend fun save(rule: ScreenTimeRule, bypassCoolingOff: Boolean = false): SaveResult = mutex.withLock {
        val existing = dao.get(rule.id)?.let(::toStored)
        val now = Time.now()
        val result = if (existing == null || bypassCoolingOff || !existing.rule.isWeakenedBy(rule)) {
            val signed = rule.copy(integrityTag = crypto.sign(rule.copy(integrityTag = "").canonicalForm()))
            dao.upsert(toEntity(signed, null, null))
            SaveResult.Applied
        } else {
            val appliesAt = now + weakeningDelayMs
            dao.upsert(toEntity(existing.rule, encodeRule(rule), appliesAt))
            SaveResult.Scheduled(appliesAt)
        }
        commitDigest()
        cache = null
        result
    }

    suspend fun delete(id: String, bypassCoolingOff: Boolean = false): SaveResult = mutex.withLock {
        val existing = dao.get(id)?.let(::toStored) ?: return@withLock SaveResult.Applied
        val result = if (bypassCoolingOff || !existing.rule.enabled) {
            dao.delete(id)
            SaveResult.Applied
        } else {
            val appliesAt = Time.now() + weakeningDelayMs
            dao.upsert(toEntity(existing.rule, DELETE_MARKER, appliesAt))
            SaveResult.Scheduled(appliesAt)
        }
        commitDigest()
        cache = null
        result
    }

    suspend fun cancelPending(id: String) = mutex.withLock {
        val existing = dao.get(id)?.let(::toStored) ?: return@withLock
        dao.upsert(toEntity(existing.rule, null, null))
        cache = null
    }

    // ---- mapping -------------------------------------------------------------------------------

    private fun toStored(e: RuleEntity): StoredRule {
        val rule = ScreenTimeRule(
            id = e.id,
            name = e.name,
            targetPackages = splitSet(e.packages),
            targetCategories = splitSet(e.categories).mapNotNull { runCatching { AppCategory.valueOf(it) }.getOrNull() }.toSet(),
            dailyLimitMs = e.dailyLimitMs,
            blockedHours = decodeRanges(e.blockedHours),
            escalationLevel = runCatching { EscalationLevel.valueOf(e.escalationLevel) }.getOrDefault(EscalationLevel.HARD),
            enabled = e.enabled,
            createdAt = e.createdAt,
            integrityTag = e.integrityTag,
        )
        val ok = crypto.verify(rule.copy(integrityTag = "").canonicalForm(), e.integrityTag)
        val pendingDelete = e.pendingEncoded == DELETE_MARKER
        val pending = if (pendingDelete) null else e.pendingEncoded?.let { decodeRule(it, e.id, e.createdAt) }
        return StoredRule(rule, pending, pendingDelete, e.pendingAppliesAt, ok)
    }

    private fun toEntity(rule: ScreenTimeRule, pendingEncoded: String?, pendingAppliesAt: Long?) = RuleEntity(
        id = rule.id,
        name = rule.name,
        packages = rule.targetPackages.sorted().joinToString(","),
        categories = rule.targetCategories.map { it.name }.sorted().joinToString(","),
        dailyLimitMs = rule.dailyLimitMs,
        blockedHours = encodeRanges(rule.blockedHours),
        escalationLevel = rule.escalationLevel.name,
        enabled = rule.enabled,
        createdAt = rule.createdAt,
        integrityTag = rule.integrityTag,
        pendingEncoded = pendingEncoded,
        pendingAppliesAt = pendingAppliesAt,
    )

    private fun encodeRule(r: ScreenTimeRule): String = listOf(
        r.name.replace("\u001f", " "),
        r.targetPackages.sorted().joinToString(","),
        r.targetCategories.map { it.name }.sorted().joinToString(","),
        r.dailyLimitMs.toString(),
        encodeRanges(r.blockedHours),
        r.escalationLevel.name,
        r.enabled.toString(),
    ).joinToString("\u001f")

    private fun decodeRule(s: String, id: String, createdAt: Long): ScreenTimeRule? = runCatching {
        val p = s.split("\u001f")
        ScreenTimeRule(
            id = id,
            name = p[0],
            targetPackages = splitSet(p[1]),
            targetCategories = splitSet(p[2]).map { AppCategory.valueOf(it) }.toSet(),
            dailyLimitMs = p[3].toLong(),
            blockedHours = decodeRanges(p[4]),
            escalationLevel = EscalationLevel.valueOf(p[5]),
            enabled = p[6].toBoolean(),
            createdAt = createdAt,
        )
    }.getOrNull()

    companion object {
        const val DEFAULT_WEAKENING_DELAY = 24 * 60 * 60 * 1000L
        private const val DELETE_MARKER = "__DELETE__"

        fun newId(): String = UUID.randomUUID().toString()

        private fun splitSet(s: String): Set<String> = s.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

        fun encodeRanges(ranges: List<TimeRange>): String =
            ranges.joinToString(";") { "${it.startMinute}-${it.endMinute}:${it.days.sorted().joinToString("")}" }

        fun decodeRanges(s: String): List<TimeRange> = s.split(';').filter { it.isNotBlank() }.mapNotNull { part ->
            runCatching {
                val (range, days) = part.split(':').let { it[0] to it.getOrElse(1) { "" } }
                val (a, b) = range.split('-').map(String::toInt)
                TimeRange(a, b, days.map { it.digitToInt() }.toSet())
            }.getOrNull()
        }
    }
}

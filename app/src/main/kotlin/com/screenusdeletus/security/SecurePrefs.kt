package com.screenusdeletus.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Encrypted key/value storage (AES-256-GCM values, AES-256-SIV keys, master key in the Android
 * Keystore). Holds settings, PIN hashes, the database passphrase and protection state.
 */
class SecurePrefs(context: Context) {

    val prefs: SharedPreferences = open(context.applicationContext)

    /** Bumped whenever a value changes so UI can observe settings without a listener per key. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> _version.value++ }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun string(key: String, default: String? = null): String? = prefs.getString(key, default)
    fun long(key: String, default: Long = 0L): Long = prefs.getLong(key, default)
    fun int(key: String, default: Int = 0): Int = prefs.getInt(key, default)
    fun bool(key: String, default: Boolean = false): Boolean = prefs.getBoolean(key, default)

    fun put(key: String, value: String?) = prefs.edit { putString(key, value) }
    fun put(key: String, value: Long) = prefs.edit { putLong(key, value) }
    fun put(key: String, value: Int) = prefs.edit { putInt(key, value) }
    fun put(key: String, value: Boolean) = prefs.edit { putBoolean(key, value) }
    fun remove(key: String) = prefs.edit { remove(key) }

    /** Synchronous write for values that must survive an immediate process kill. */
    fun putNow(key: String, value: Long) = prefs.edit(commit = true) { putLong(key, value) }

    companion object {
        private const val FILE = "screenusdeletus_secure"

        private fun open(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return try {
                create(context, masterKey)
            } catch (e: Exception) {
                // The keyset can become unreadable (e.g. Keystore reset). Starting over is the only
                // option; the tamper monitor notices the missing setup and re-runs onboarding.
                Log.e("SecurePrefs", "Encrypted prefs unreadable, resetting", e)
                context.deleteSharedPreferences(FILE)
                create(context, masterKey)
            }
        }

        private fun create(context: Context, masterKey: MasterKey) = EncryptedSharedPreferences.create(
            context,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}

/** Every key used in [SecurePrefs], in one place. */
object Keys {
    const val ONBOARDING_COMPLETE = "onboarding_complete"
    const val PIN_HASH = "pin_hash"
    const val PIN_SALT = "pin_salt"
    const val PIN_FAILURES = "pin_failures"
    const val PIN_LOCKED_UNTIL = "pin_locked_until"
    const val BIOMETRIC_ENABLED = "biometric_enabled"

    const val GUARDIAN_PIN_HASH = "guardian_pin_hash"
    const val GUARDIAN_PIN_SALT = "guardian_pin_salt"
    const val GUARDIAN_JSON = "guardian_json"

    const val DB_PASSPHRASE = "db_passphrase"

    const val PROFILE_JSON = "profile_json"
    const val DAILY_GOAL_MS = "daily_goal_ms"
    const val WEAKENING_DELAY_MS = "weakening_delay_ms"

    const val UNLOCK_SETTINGS_JSON = "unlock_settings_json"
    const val UNLOCK_SETTINGS_PENDING_JSON = "unlock_settings_pending_json"
    const val UNLOCKED_UNTIL = "unlocked_until"
    const val INTERSTITIAL_PASSES_JSON = "interstitial_passes_json"

    const val COMMITMENT_JSON = "commitment_json"

    const val PROTECTION_LEVEL = "protection_level"
    const val PROTECTION_UNTIL = "protection_until"
    const val DISARM_REQUESTED_AT = "disarm_requested_at"
    const val DISARM_DELAY_MS = "disarm_delay_ms"
    const val DISARMED_UNTIL = "disarmed_until"

    const val PINNED_CERT_SHA256 = "pinned_cert_sha256"
    const val APK_HASH = "apk_hash"
    const val APK_HASH_VERSION = "apk_hash_version"
    const val RULES_DIGEST = "rules_digest"

    const val LAST_KNOWN_ADMIN = "last_known_admin"
    const val LAST_KNOWN_ACCESSIBILITY = "last_known_accessibility"
    const val LAST_KNOWN_USAGE_ACCESS = "last_known_usage_access"
    const val LAST_KNOWN_ROOT = "last_known_root"
    const val LAST_KNOWN_ADB = "last_known_adb"
    const val LAST_HEARTBEAT = "last_heartbeat"
    const val LAST_WALL_ELAPSED_DELTA = "last_wall_elapsed_delta"
    const val LAST_ELAPSED = "last_elapsed"

    const val LAST_ROLLED_DAY = "last_rolled_day"
    const val LAST_WEEKLY_REPORT_WEEK = "last_weekly_report_week"
    const val RECENT_SUGGESTIONS = "recent_suggestions"
}

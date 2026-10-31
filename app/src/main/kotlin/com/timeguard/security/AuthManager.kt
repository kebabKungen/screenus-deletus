package com.timeguard.security

import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PIN + biometric authentication for the user, plus a separate guardian PIN that only the
 * guardian knows. PINs are stored as salted PBKDF2-HMAC-SHA256 hashes. Wrong guesses trigger an
 * exponential lockout and are logged as tamper events.
 */
class AuthManager(
    private val prefs: SecurePrefs,
    private val onWrongPin: () -> Unit,
) {
    sealed interface PinResult {
        data object Ok : PinResult
        data class Wrong(val attemptsBeforeLockout: Int) : PinResult
        data class LockedOut(val until: Long) : PinResult
    }

    val hasPin: Boolean get() = prefs.string(Keys.PIN_HASH) != null
    val hasGuardianPin: Boolean get() = prefs.string(Keys.GUARDIAN_PIN_HASH) != null
    var biometricEnabled: Boolean
        get() = prefs.bool(Keys.BIOMETRIC_ENABLED)
        set(v) = prefs.put(Keys.BIOMETRIC_ENABLED, v)

    fun setPin(pin: String) = store(pin, Keys.PIN_HASH, Keys.PIN_SALT)
    fun setGuardianPin(pin: String) = store(pin, Keys.GUARDIAN_PIN_HASH, Keys.GUARDIAN_PIN_SALT)
    fun clearGuardianPin() {
        prefs.remove(Keys.GUARDIAN_PIN_HASH)
        prefs.remove(Keys.GUARDIAN_PIN_SALT)
    }

    fun verifyPin(pin: String, now: Long = System.currentTimeMillis()): PinResult =
        verify(pin, Keys.PIN_HASH, Keys.PIN_SALT, now)

    fun verifyGuardianPin(pin: String, now: Long = System.currentTimeMillis()): PinResult =
        verify(pin, Keys.GUARDIAN_PIN_HASH, Keys.GUARDIAN_PIN_SALT, now)

    private fun store(pin: String, hashKey: String, saltKey: String) {
        require(pin.length >= MIN_PIN_LENGTH) { "PIN must have at least $MIN_PIN_LENGTH digits" }
        val salt = CryptoManager.randomBytes(16)
        prefs.put(saltKey, Base64.encodeToString(salt, Base64.NO_WRAP))
        prefs.put(hashKey, hash(pin, salt))
    }

    private fun verify(pin: String, hashKey: String, saltKey: String, now: Long): PinResult {
        val lockedUntil = prefs.long(Keys.PIN_LOCKED_UNTIL)
        if (lockedUntil > now) return PinResult.LockedOut(lockedUntil)

        val stored = prefs.string(hashKey) ?: return PinResult.Wrong(0)
        val salt = Base64.decode(prefs.string(saltKey) ?: return PinResult.Wrong(0), Base64.NO_WRAP)
        if (MessageDigest.isEqual(stored.toByteArray(), hash(pin, salt).toByteArray())) {
            prefs.put(Keys.PIN_FAILURES, 0)
            return PinResult.Ok
        }

        onWrongPin()
        val failures = prefs.int(Keys.PIN_FAILURES) + 1
        prefs.put(Keys.PIN_FAILURES, failures)
        if (failures >= FREE_ATTEMPTS) {
            // 30s, 60s, 120s … capped at 1h
            val shift = (failures - FREE_ATTEMPTS).coerceAtMost(7)
            val lockout = (30_000L shl shift).coerceAtMost(3_600_000L)
            prefs.put(Keys.PIN_LOCKED_UNTIL, now + lockout)
            return PinResult.LockedOut(now + lockout)
        }
        return PinResult.Wrong(FREE_ATTEMPTS - failures)
    }

    private fun hash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun canUseBiometrics(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun promptBiometric(
        activity: FragmentActivity,
        title: String,
        onSuccess: () -> Unit,
        onFallback: () -> Unit,
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFallback()
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Unlock TimeGuard")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }

    companion object {
        const val MIN_PIN_LENGTH = 4
        private const val FREE_ATTEMPTS = 5
        private const val ITERATIONS = 120_000
    }
}

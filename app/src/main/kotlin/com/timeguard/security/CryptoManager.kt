package com.timeguard.security

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.Mac
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.mac.MacConfig
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Tink-backed integrity protection. Every stored rule carries an HMAC-SHA256 tag computed with a
 * key that is itself wrapped by a non-exportable Android Keystore key. Editing the database
 * directly (e.g. on a rooted phone) invalidates the tags, which the tamper monitor detects.
 */
class CryptoManager(context: Context) {

    private val mac: Mac by lazy {
        MacConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context.applicationContext, KEYSET_NAME, KEYSET_PREFS)
            .withKeyTemplate(KeyTemplates.get("HMAC_SHA256_256BITTAG"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(Mac::class.java)
    }

    fun sign(data: String): String =
        Base64.encodeToString(mac.computeMac(data.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)

    fun verify(data: String, tag: String): Boolean = try {
        mac.verifyMac(Base64.decode(tag, Base64.NO_WRAP), data.toByteArray(Charsets.UTF_8))
        true
    } catch (e: Exception) {
        false
    }

    companion object {
        private const val KEYSET_NAME = "tg_integrity_keyset"
        private const val KEYSET_PREFS = "tg_integrity_keyset_prefs"
        private const val MASTER_KEY_URI = "android-keystore://tg_integrity_master_key"

        fun randomBytes(n: Int): ByteArray = ByteArray(n).also { SecureRandom().nextBytes(it) }

        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

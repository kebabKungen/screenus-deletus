package com.timeguard.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.provider.Settings
import com.timeguard.BuildConfig
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Local anti-tamper checks. None of these are unbeatable on a device the user fully controls —
 * their job is to make bypassing TimeGuard slow and deliberate, and to make sure it never goes
 * unnoticed.
 */
class IntegrityChecker(private val context: Context, private val prefs: SecurePrefs) {

    data class Report(
        val signatureValid: Boolean,
        val apkIntact: Boolean,
        val rootIndicators: List<String>,
        val hookingIndicators: List<String>,
        val adbEnabled: Boolean,
        val developerOptionsEnabled: Boolean,
        val debuggerAttached: Boolean,
    ) {
        val deviceCompromised: Boolean get() = rootIndicators.isNotEmpty() || hookingIndicators.isNotEmpty()
    }

    fun runAll(): Report = Report(
        signatureValid = verifyApkSignature(),
        apkIntact = validateCriticalFiles(),
        rootIndicators = detectRoot(),
        hookingIndicators = detectHookingFrameworks(),
        adbEnabled = isUsbDebuggingEnabled(),
        developerOptionsEnabled = isDeveloperOptionsEnabled(),
        debuggerAttached = !BuildConfig.DEBUG && (Debug.isDebuggerConnected() || isDebuggable()),
    )

    // ---- 1. Signature verification ----------------------------------------------------------

    fun signingCertSha256(): List<String> {
        val pm = context.packageManager
        val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val signing = info.signingInfo ?: return emptyList()
        val sigs = if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        return sigs.orEmpty().map { CryptoManager.sha256Hex(it.toByteArray()) }
    }

    /**
     * Compares the signing certificate against the value baked in at build time, or — if none was
     * configured — the certificate pinned on first launch. A re-signed (patched) APK fails.
     */
    fun verifyApkSignature(): Boolean {
        val current = runCatching { signingCertSha256() }.getOrDefault(emptyList())
        if (current.isEmpty()) return false
        val expected = BuildConfig.EXPECTED_CERT_SHA256.lowercase().replace(":", "")
        if (expected.isNotEmpty()) return expected in current

        val pinned = prefs.string(Keys.PINNED_CERT_SHA256)
        if (pinned == null) {
            prefs.put(Keys.PINNED_CERT_SHA256, current.first())
            return true
        }
        return pinned in current
    }

    // ---- 6. File integrity -------------------------------------------------------------------

    /**
     * SHA-256 of the installed APK(s). The baseline is recorded per version code; a different hash
     * for the same version means the APK was modified in place.
     */
    fun validateCriticalFiles(): Boolean {
        val versionCode = currentVersionCode()
        val hash = runCatching { apkHash() }.getOrNull() ?: return false
        val storedVersion = prefs.long(Keys.APK_HASH_VERSION, -1)
        val storedHash = prefs.string(Keys.APK_HASH)
        if (storedHash == null || storedVersion != versionCode) {
            // First run or a legitimate update — only re-baseline when the signature still matches.
            if (!verifyApkSignature()) return false
            prefs.put(Keys.APK_HASH, hash)
            prefs.put(Keys.APK_HASH_VERSION, versionCode)
            return true
        }
        return storedHash == hash
    }

    private fun apkHash(): String {
        val ai = context.applicationInfo
        val files = listOf(ai.sourceDir) + ai.splitSourceDirs.orEmpty()
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        for (path in files.sorted()) {
            FileInputStream(path).use { input ->
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun currentVersionCode(): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    // ---- 4. Root & hooking detection ---------------------------------------------------------

    fun detectRoot(): List<String> {
        val found = mutableListOf<String>()
        val suPaths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/su", "/system/bin/.ext/su",
            "/system/usr/we-need-root/su", "/data/local/su", "/data/local/bin/su", "/data/local/xbin/su",
            "/su/bin/su", "/system/sbin/su", "/vendor/bin/su", "/cache/su", "/dev/su",
        )
        suPaths.firstOrNull { File(it).exists() }?.let { found += "su binary at $it" }

        listOf("/sbin/.magisk", "/data/adb/magisk", "/data/adb/modules", "/cache/.disable_magisk", "/data/adb/ksu")
            .firstOrNull { File(it).exists() }?.let { found += "Magisk/KernelSU files ($it)" }

        if (Build.TAGS?.contains("test-keys") == true) found += "Build signed with test-keys"

        val rootPackages = listOf(
            "com.topjohnwu.magisk", "io.github.vvb2060.magisk", "me.weishu.kernelsu",
            "eu.chainfire.supersu", "com.koushikdutta.superuser", "com.noshufou.android.su",
            "com.thirdparty.superuser", "com.yellowes.su", "com.kingroot.kinguser", "com.zachspong.temprootremovejb",
        )
        rootPackages.filter { isInstalled(it) }.forEach { found += "Root manager installed ($it)" }

        if (readProp("ro.debuggable") == "1" && readProp("ro.secure") == "0") found += "Insecure system build (ro.secure=0)"

        if (runCatching { Runtime.getRuntime().exec(arrayOf("which", "su")).inputStream.bufferedReader().readText().isNotBlank() }.getOrDefault(false)) {
            found += "su found on PATH"
        }
        return found.distinct()
    }

    fun detectHookingFrameworks(): List<String> {
        val found = mutableListOf<String>()
        val hookPackages = listOf(
            "de.robv.android.xposed.installer", "org.meowcat.edxposed.manager", "org.lsposed.manager",
            "io.github.lsposed.manager", "com.saurik.substrate", "io.va.exposed", "com.android.vending.billing.InAppBillingService.LOCK",
        )
        hookPackages.filter { isInstalled(it) }.forEach { found += "Hooking framework installed ($it)" }

        val hookClasses = listOf("de.robv.android.xposed.XposedBridge", "com.saurik.substrate.MS")
        hookClasses.filter { runCatching { Class.forName(it); true }.getOrDefault(false) }
            .forEach { found += "Hooking framework loaded in process ($it)" }

        // Stack traces containing Xposed frames mean our process is being hooked.
        val stack = Throwable().stackTrace.joinToString { it.className }
        if (stack.contains("xposed", ignoreCase = true) || stack.contains("lsposed", ignoreCase = true)) {
            found += "Xposed frames on the call stack"
        }

        // Frida commonly maps an agent library into the target process.
        runCatching {
            File("/proc/self/maps").useLines { lines ->
                if (lines.any { it.contains("frida", ignoreCase = true) }) {
                    found += "Frida agent mapped into process"
                }
            }
        }
        return found.distinct()
    }

    // ---- 5. USB debugging --------------------------------------------------------------------

    fun isUsbDebuggingEnabled(): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1

    fun isDeveloperOptionsEnabled(): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1

    private fun isDebuggable(): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun isInstalled(pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private fun readProp(name: String): String? = runCatching {
        Runtime.getRuntime().exec(arrayOf("getprop", name)).inputStream.bufferedReader().readText().trim()
    }.getOrNull()
}

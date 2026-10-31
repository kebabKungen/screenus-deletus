package com.screenusdeletus

import android.content.Context
import android.util.Base64
import com.screenusdeletus.core.TamperType
import com.screenusdeletus.data.db.AppDatabase
import com.screenusdeletus.data.repo.AppCatalog
import com.screenusdeletus.data.repo.CommitmentRepository
import com.screenusdeletus.data.repo.GuardianRepository
import com.screenusdeletus.data.repo.ProfileRepository
import com.screenusdeletus.data.repo.RulesRepository
import com.screenusdeletus.data.repo.StatsRepository
import com.screenusdeletus.data.repo.TriggerRepository
import com.screenusdeletus.data.repo.UnlockRepository
import com.screenusdeletus.data.repo.UsageRepository
import com.screenusdeletus.data.repo.ViolationRecorder
import com.screenusdeletus.enforcement.EnforcementController
import com.screenusdeletus.security.AuthManager
import com.screenusdeletus.security.CryptoManager
import com.screenusdeletus.security.IntegrityChecker
import com.screenusdeletus.security.Keys
import com.screenusdeletus.security.SecurePrefs
import com.screenusdeletus.security.TamperMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Hand-rolled dependency container; everything is created lazily on first use. */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val prefs by lazy { SecurePrefs(app) }
    val crypto by lazy { CryptoManager(app) }

    val db: AppDatabase by lazy {
        val stored = prefs.string(Keys.DB_PASSPHRASE)
        val passphrase = if (stored != null) {
            Base64.decode(stored, Base64.NO_WRAP)
        } else {
            CryptoManager.randomBytes(32).also { prefs.put(Keys.DB_PASSPHRASE, Base64.encodeToString(it, Base64.NO_WRAP)) }
        }
        AppDatabase.open(app, passphrase)
    }

    val auth by lazy { AuthManager(prefs) { scope.launch { tamper.record(TamperType.WRONG_PIN, "Wrong PIN") } } }
    val catalog by lazy { AppCatalog(app, db.categories()) }
    val usage by lazy { UsageRepository(app, catalog) }
    val rules by lazy { RulesRepository(db.rules(), crypto, prefs) { type, detail -> tamper.record(type, detail) } }
    val profile by lazy { ProfileRepository(prefs) }
    val guardian by lazy { GuardianRepository(app, prefs, db) }
    val commitment by lazy { CommitmentRepository(prefs) }
    val triggers by lazy { TriggerRepository(db) }
    val unlocks by lazy { UnlockRepository(db, prefs) }
    val violations by lazy { ViolationRecorder(db, commitment, guardian, app) }
    val integrity by lazy { IntegrityChecker(app, prefs) }
    val tamper by lazy { TamperMonitor(app, prefs, db, integrity, usage, guardian) }
    val stats by lazy { StatsRepository(db, usage, catalog, rules, profile, triggers, unlocks) }
    val enforcement by lazy { EnforcementController(app, db, rules, usage, catalog, unlocks, tamper, profile, violations) }
}

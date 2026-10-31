package com.screenusdeletus.data.repo

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.screenusdeletus.core.AppCategory
import com.screenusdeletus.data.db.CategoryOverrideDao
import com.screenusdeletus.data.db.CategoryOverrideEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class InstalledApp(
    val packageName: String,
    val label: String,
    val category: AppCategory,
)

/** Installed launchable apps, their labels and their categories. */
class AppCatalog(
    private val context: Context,
    private val overridesDao: CategoryOverrideDao,
) {
    private val pm: PackageManager get() = context.packageManager
    private val labelCache = ConcurrentHashMap<String, String>()
    private val categoryCache = ConcurrentHashMap<String, AppCategory>()

    @Volatile
    private var overrides: Map<String, AppCategory>? = null

    suspend fun launchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        loadOverrides()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .map { InstalledApp(it, label(it), categoryOf(it)) }
            .sortedBy { it.label.lowercase() }
    }

    /** Home screen apps; their foreground time isn't counted and they are never blocked. */
    val launcherPackages: Set<String> by lazy {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).map { it.activityInfo.packageName }.toSet()
    }

    fun label(pkg: String): String = labelCache.getOrPut(pkg) {
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull() ?: prettyName(pkg)
    }

    fun icon(pkg: String): Drawable? = runCatching { pm.getApplicationIcon(pkg) }.getOrNull()

    fun categoryOf(pkg: String): AppCategory {
        overrides?.get(pkg)?.let { return it }
        return categoryCache.getOrPut(pkg) { KNOWN[pkg] ?: systemCategory(pkg) }
    }

    suspend fun setCategory(pkg: String, category: AppCategory) {
        overridesDao.set(CategoryOverrideEntity(pkg, category.name))
        overrides = null
        loadOverrides()
    }

    suspend fun loadOverrides() {
        if (overrides != null) return
        overrides = overridesDao.all().mapNotNull { o ->
            runCatching { o.packageName to AppCategory.valueOf(o.category) }.getOrNull()
        }.toMap()
    }

    private fun systemCategory(pkg: String): AppCategory {
        val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return AppCategory.OTHER
        return when (info.category) {
            ApplicationInfo.CATEGORY_GAME -> AppCategory.GAMES
            ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SOCIAL_MEDIA
            ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_IMAGE -> AppCategory.ENTERTAINMENT
            ApplicationInfo.CATEGORY_NEWS -> AppCategory.NEWS
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.PRODUCTIVITY
            ApplicationInfo.CATEGORY_MAPS -> AppCategory.UTILITIES
            else -> if ((info.flags and ApplicationInfo.FLAG_SYSTEM) != 0) AppCategory.UTILITIES else AppCategory.OTHER
        }
    }

    companion object {
        /** "com.google.android.youtube" → "Youtube" when the real label isn't visible to us. */
        fun prettyName(pkg: String): String {
            val skip = setOf("com", "org", "net", "android", "app", "apps", "google", "mobile", "client")
            val part = pkg.split('.').lastOrNull { it.lowercase() !in skip && it.length > 1 } ?: pkg
            return part.replaceFirstChar { it.uppercase() }
        }

        /** Apps whose category metadata is missing or unhelpful. */
        val KNOWN: Map<String, AppCategory> = buildMap {
            listOf(
                "com.instagram.android", "com.instagram.barcelona", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
                "com.facebook.katana", "com.facebook.lite", "com.twitter.android", "com.snapchat.android",
                "com.reddit.frontpage", "com.pinterest", "com.linkedin.android", "com.tumblr", "xyz.blueskyweb.app",
                "org.joinmastodon.android", "com.bereal.ft", "com.vkontakte.android",
            ).forEach { put(it, AppCategory.SOCIAL_MEDIA) }
            listOf(
                "com.google.android.youtube", "com.netflix.mediaclient", "tv.twitch.android.app", "com.disney.disneyplus",
                "com.amazon.avod.thirdpartyclient", "com.hbo.hbonow", "com.wbd.stream", "com.spotify.music",
                "com.google.android.apps.youtube.music", "com.crunchyroll.crunchyroid", "com.plexapp.android",
            ).forEach { put(it, AppCategory.ENTERTAINMENT) }
            listOf(
                "com.whatsapp", "org.telegram.messenger", "com.discord", "com.facebook.orca", "org.thoughtcrime.securesms",
                "com.google.android.apps.messaging", "com.Slack", "com.microsoft.teams", "com.viber.voip",
            ).forEach { put(it, AppCategory.COMMUNICATION) }
            listOf(
                "com.amazon.mShop.android.shopping", "com.einnovation.temu", "com.zzkko", "com.alibaba.aliexpresshd",
                "com.ebay.mobile", "com.etsy.android", "com.contextlogic.wish",
            ).forEach { put(it, AppCategory.SHOPPING) }
            listOf(
                "com.google.android.apps.magazines", "flipboard.app", "com.nytimes.android", "bbc.mobile.news.ww",
                "com.cnn.mobile.android.phone", "com.particlenews.newsbreak",
            ).forEach { put(it, AppCategory.NEWS) }
            listOf(
                "com.google.android.gm", "com.google.android.apps.docs", "com.google.android.calendar",
                "com.microsoft.office.outlook", "com.todoist", "com.notion.id", "md.obsidian",
            ).forEach { put(it, AppCategory.PRODUCTIVITY) }
            listOf("com.duolingo", "org.khanacademy.android", "com.anki.android", "com.ichi2.anki")
                .forEach { put(it, AppCategory.EDUCATION) }
            listOf("com.calm.android", "com.getsomeheadspace.android", "com.strava", "com.google.android.apps.fitness")
                .forEach { put(it, AppCategory.HEALTH) }
        }
    }
}

package com.screenusdeletus.core

import kotlin.random.Random

data class TriggerLog(
    val timestamp: Long,
    val appOpened: String,
    val hourOfDay: Int,
    val isoDayOfWeek: Int,
    val userReportedTrigger: String?,
    /** 1–10, self reported. 0 = not answered. */
    val moodScore: Int,
    /** 1–10, self reported. 0 = not answered. */
    val stressLevel: Int,
    /** Whether the user went ahead with the app after reflecting. */
    val proceeded: Boolean,
)

object ReflectionPrompts {
    val prompts = listOf(
        "Why did you try to open this app?",
        "What are you feeling right now?",
        "Is there something else you could do instead?",
        "How will you feel in 10 minutes if you continue?",
        "What's your goal for today?",
        "What were you doing right before you reached for your phone?",
        "Is this a need, or a habit?",
    )

    /** Common triggers offered as one-tap answers; free text is always allowed too. */
    val commonTriggers = listOf(
        "Bored", "Stressed", "Lonely", "Tired", "Procrastinating",
        "Anxious", "Habit / autopilot", "Notification", "Avoiding a task", "Actually need it",
    )

    fun pick(random: Random = Random.Default): String = prompts.random(random)
}

data class AlternativeActivity(
    val title: String,
    val description: String,
    val minutes: Int,
    val tag: String,
    /** Optional app that supports this activity (opened only if installed). */
    val suggestedPackage: String? = null,
)

object HabitSuggester {
    val catalog = listOf(
        AlternativeActivity("Box breathing", "4 seconds in, hold 4, out 4, hold 4. Repeat.", 2, "mindfulness"),
        AlternativeActivity("Guided meditation", "A short guided session.", 10, "mindfulness", "com.calm.android"),
        AlternativeActivity("Stretch", "Neck, shoulders, back — stand up and stretch.", 5, "exercise"),
        AlternativeActivity("Walk around the block", "Leave the phone behind.", 15, "exercise"),
        AlternativeActivity("Bodyweight set", "10 squats, 10 push-ups, 30s plank.", 5, "exercise"),
        AlternativeActivity("Read a chapter", "Pick up the book you've been meaning to finish.", 20, "reading"),
        AlternativeActivity("Audiobook", "Listen while you tidy up.", 20, "reading", "com.audible.application"),
        AlternativeActivity("Music, eyes closed", "One full album side, no scrolling.", 15, "music", "com.spotify.music"),
        AlternativeActivity("Sketch", "Draw whatever is in front of you.", 10, "creative"),
        AlternativeActivity("Journal", "Write three things on your mind.", 5, "creative"),
        AlternativeActivity("Message a friend", "A real conversation instead of a feed.", 5, "social"),
        AlternativeActivity("Glass of water", "You're probably a little dehydrated.", 1, "health"),
        AlternativeActivity("Tidy one surface", "Desk, counter or nightstand.", 5, "home"),
        AlternativeActivity("Do the next tiny step", "Two minutes on the task you're avoiding.", 2, "productivity"),
    )

    val tags: List<String> get() = catalog.map { it.tag }.distinct()

    /**
     * Ranks activities that fit the time available, preferring the user's tags and avoiding the
     * ones suggested most recently.
     */
    fun suggest(
        availableMinutes: Int,
        preferences: Collection<String>,
        recentlySuggested: Collection<String> = emptyList(),
        random: Random = Random.Default,
    ): AlternativeActivity {
        val fitting = catalog.filter { it.minutes <= availableMinutes.coerceAtLeast(1) }
            .ifEmpty { catalog.filter { it.minutes <= 2 } }
        return fitting
            .map { a ->
                var score = random.nextDouble() * 0.5
                if (a.tag in preferences) score += 2.0
                if (a.title in recentlySuggested) score -= 1.5
                score += a.minutes.toDouble() / availableMinutes.coerceAtLeast(1) // use the time well
                a to score
            }
            .maxBy { it.second }
            .first
    }
}

data class InsightInput(
    val hourlyMsLast7Days: LongArray,
    val dailyTotalsMs: List<Long>,
    val goalMs: Long,
    val triggers: List<TriggerLog>,
    val streak: StreakData,
    val unlocksThisWeek: Int,
    val maxUnlocksPerWeek: Int,
    val topApps: List<Pair<String, Long>>,
    val pickupsToday: Int,
)

/**
 * Generates short, actionable insights from local data using transparent heuristics.
 * Nothing leaves the device.
 */
object InsightsEngine {
    fun generate(input: InsightInput, appLabel: (String) -> String = { it }): List<String> {
        val out = mutableListOf<String>()
        val days = input.dailyTotalsMs

        if (input.hourlyMsLast7Days.size == 24 && input.hourlyMsLast7Days.sum() > 0) {
            val peak = input.hourlyMsLast7Days.indices.maxBy { input.hourlyMsLast7Days[it] }
            val share = input.hourlyMsLast7Days[peak].toDouble() / input.hourlyMsLast7Days.sum()
            if (share > 0.12) {
                out += "Your heaviest hour is ${TimeRange.formatMinute(peak * 60)}–${TimeRange.formatMinute((peak + 1) * 60)} " +
                    "(${(share * 100).toInt()}% of your screen time). Consider a focus block then."
            }
            val lateNight = (22..23).sumOf { input.hourlyMsLast7Days[it] } + (0..5).sumOf { input.hourlyMsLast7Days[it] }
            if (lateNight.toDouble() / input.hourlyMsLast7Days.sum() > 0.2) {
                out += "Over a fifth of your use happens late at night. A 22:00–07:00 block can protect your sleep."
            }
        }

        if (days.size >= 4) {
            val recent = days.takeLast(3).average()
            val earlier = days.dropLast(3).average()
            if (earlier > 0) {
                val change = (recent - earlier) / earlier
                if (change <= -0.1) out += "Down ${(-change * 100).toInt()}% over the last 3 days. Keep going."
                else if (change >= 0.15) out += "Up ${(change * 100).toInt()}% over the last 3 days. Is something stressing you?"
            }
        }

        if (input.goalMs > 0 && days.isNotEmpty()) {
            val hit = days.count { it <= input.goalMs }
            out += "You hit your daily goal on $hit of the last ${days.size} days."
        }

        val reported = input.triggers.mapNotNull { it.userReportedTrigger?.takeIf(String::isNotBlank) }
        if (reported.size >= 3) {
            val (top, count) = reported.groupingBy { it }.eachCount().maxBy { it.value }
            out += "\"$top\" is your most common trigger ($count times). " + triggerAdvice(top)
        }
        val stressed = input.triggers.filter { it.stressLevel >= 7 }
        if (stressed.size >= 3 && stressed.size * 2 >= input.triggers.count { it.stressLevel > 0 }) {
            out += "Most of your blocked attempts happen when you're stressed. Try a 2-minute breathing exercise first."
        }
        val resisted = input.triggers.count { !it.proceeded }
        if (input.triggers.isNotEmpty() && resisted > 0) {
            out += "You walked away $resisted of ${input.triggers.size} times after reflecting."
        }

        if (input.streak.currentStreak >= 3) {
            out += "${input.streak.currentStreak}-day streak! Your best is ${input.streak.longestStreak}."
        } else if (input.streak.longestStreak > input.streak.currentStreak && input.streak.longestStreak >= 3) {
            out += "Your record is ${input.streak.longestStreak} days. One good day starts the next streak."
        }

        if (input.maxUnlocksPerWeek > 0 && input.unlocksThisWeek >= input.maxUnlocksPerWeek - 1 && input.unlocksThisWeek > 0) {
            out += "You've used ${input.unlocksThisWeek}/${input.maxUnlocksPerWeek} emergency unlocks this week."
        }

        input.topApps.firstOrNull()?.let { (pkg, ms) ->
            val total = input.topApps.sumOf { it.second }
            if (total > 0 && ms.toDouble() / total > 0.4) {
                out += "${appLabel(pkg)} accounts for ${(ms * 100 / total)}% of your app time."
            }
        }
        if (input.pickupsToday >= 80) {
            out += "${input.pickupsToday} pickups today. Try leaving your phone in another room for an hour."
        }
        return out
    }

    private fun triggerAdvice(trigger: String): String = when (trigger.lowercase()) {
        "bored" -> "Keep a short list of offline things you enjoy."
        "stressed", "anxious" -> "A walk or breathing exercise helps more than a feed."
        "tired" -> "Scrolling when tired usually means you need rest, not content."
        "procrastinating", "avoiding a task" -> "Commit to just two minutes on the task you're avoiding."
        "notification" -> "Turn off non-essential notifications for your blocked apps."
        "lonely" -> "Message or call one person directly instead."
        else -> "Plan an alternative for that moment in advance."
    }
}

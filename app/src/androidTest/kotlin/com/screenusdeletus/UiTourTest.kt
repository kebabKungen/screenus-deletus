package com.screenusdeletus

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.screenusdeletus.enforcement.BlockingActivity
import com.screenusdeletus.ui.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Walks through onboarding and every screen, taking a screenshot of each. CI grants usage access,
 * the accessibility service and device admin over adb before running this (scripts/ui-tour.sh).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class UiTourTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val outDir: File by lazy {
        File(instrumentation.targetContext.getExternalFilesDir(null), "screens").apply { mkdirs() }
    }
    private val prefix: String by lazy {
        val night = (instrumentation.targetContext.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        if (night) "dark_" else "light_"
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        Thread.sleep(600)
        val bmp = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(outDir, "$prefix$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun click(text: String) {
        rule.waitUntilAtLeastOneExists(hasText(text), 10_000)
        val node = rule.onAllNodesWithText(text)[0]
        try {
            node.performScrollTo()
        } catch (e: AssertionError) {
            // Not inside a scrollable container (e.g. the navigation bar).
        }
        node.performClick()
    }

    @Test
    fun tour() {
        // ---- onboarding ----
        rule.waitUntilAtLeastOneExists(hasText("Screenus Deletus"), 15_000)
        shot("01_welcome")
        click("Continue")
        shot("02_habits")
        click("Continue")
        rule.onNodeWithText("PIN (at least 4 digits)").performTextInput("2468")
        rule.onNodeWithText("Repeat PIN").performTextInput("2468")
        shot("03_pin")
        click("Continue")
        rule.waitUntilAtLeastOneExists(hasText("Permissions"), 30_000)
        shot("04_permissions")
        click("Continue")
        shot("05_first_rule")
        click("Continue")
        shot("06_done")
        click("Start")

        // ---- main tabs ----
        rule.waitUntilAtLeastOneExists(hasText("Top apps today") or hasText("Streak"), 30_000)
        shot("10_today")
        click("Rules")
        shot("11_rules")
        click("Add rule")
        shot("12_rule_editor")
        click("Cancel")
        click("Insights")
        rule.waitUntilAtLeastOneExists(hasText("Last 7 days"), 30_000)
        shot("13_insights")
        click("Security")
        rule.waitUntilAtLeastOneExists(hasText("Protections"), 30_000)
        shot("14_security")
        click("More")
        shot("15_more")
        click("Guardian")
        shot("16_guardian")
        click("More")
        click("Commitment pledge")
        shot("17_commitment")
        click("More")
        click("Settings")
        shot("18_settings")
        click("More")
        click("Emergency unlock")
        shot("19_unlock")

        // ---- block screens ----
        val ctx = instrumentation.targetContext
        fun blockIntent(mode: String, delay: Int) = Intent(ctx, BlockingActivity::class.java)
            .putExtra(BlockingActivity.EXTRA_PACKAGE, "com.android.chrome")
            .putExtra(BlockingActivity.EXTRA_MODE, mode)
            .putExtra(BlockingActivity.EXTRA_REASON, "Daily limit of 30m reached for Distracting apps")
            .putExtra(BlockingActivity.EXTRA_DELAY, delay)
        ActivityScenario.launch<BlockingActivity>(blockIntent(BlockingActivity.MODE_BLOCK, 0)).use {
            Thread.sleep(1_500)
            shot("20_block")
        }
        ActivityScenario.launch<BlockingActivity>(blockIntent(BlockingActivity.MODE_INTERSTITIAL, 25)).use {
            Thread.sleep(1_500)
            shot("21_interstitial")
        }
    }
}

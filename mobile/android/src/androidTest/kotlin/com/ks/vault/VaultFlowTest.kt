package com.ks.vault

import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ks.vault.auth.VaultSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Setup, a note, lock, a wrong and a right unlock: the first version's main path, on a device. */
@RunWith(AndroidJUnit4::class)
class VaultFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as VaultApp

    @Before fun freshVault() = runBlocking {
        if (app.session.state.value !is VaultSession.State.NeedsSetup) app.session.eraseEverything()
    }

    private fun waitForTag(tag: String) = rule.waitUntil(60_000) { rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    private fun waitForText(text: String) = rule.waitUntil(60_000) { rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    private fun enterPassword(p: String) {
        waitForTag("password")
        rule.onNodeWithTag("password").performTextInput(p)
        rule.onNodeWithTag("submit").performClick()
    }

    @Test fun windowIsHardened() {
        rule.activity.runOnUiThread {
            val window = rule.activity.window
            val decor = window.decorView
            assertTrue(window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            assertTrue(decor.filterTouchesWhenObscured)
            assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS, decor.importantForAutofill)
            if (Build.VERSION.SDK_INT >= 34) {
                assertTrue(decor.isAccessibilityDataSensitive)
            }
        }
    }

    @Test fun setUpAddANoteLockAndUnlock() {
        val res = rule.activity.resources
        // Too short is refused.
        enterPassword("12345")
        waitForText(res.getString(R.string.lock_password_too_short, "6"))
        enterPassword("vault-pass")
        enterPassword("vault-pass")

        waitForTag("add")
        rule.onNodeWithTag("add").performClick()
        rule.onNodeWithTag("add_NOTE").performClick()
        waitForTag("title")
        rule.onNodeWithTag("title").performTextInput("Locker code")
        rule.onNodeWithTag("notesField").performTextInput("Left locker, 4471")
        rule.onNodeWithTag("save").performClick()
        waitForTag("notes")
        rule.onNodeWithText("Left locker, 4471").assertIsDisplayed()

        rule.activity.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        waitForTag("lock")
        rule.onNodeWithTag("lock").performClick()

        enterPassword("wrong-pass")
        waitForTag("lockMessage")
        enterPassword("vault-pass")
        waitForTag("search")
        rule.onNodeWithTag("search").performTextInput("4471")
        waitForText("Locker code")
    }
}

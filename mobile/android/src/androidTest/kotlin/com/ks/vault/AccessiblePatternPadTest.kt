package com.ks.vault

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ks.vault.ui.AccessiblePatternPad
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The button pad for TalkBack users gives the same dots as drawing the pattern. */
@RunWith(AndroidJUnit4::class)
class AccessiblePatternPadTest {
    @get:Rule val rule = createComposeRule()

    @Test fun tapsGiveTheDrawnPattern() {
        var result: List<Int>? = null
        rule.setContent { AccessiblePatternPad(onDone = { result = it }) }
        // An L: 0, then 6 (passes 3), then 8 (passes 7).
        listOf(0, 6, 8).forEach { rule.onNodeWithTag("dot$it").performClick() }
        rule.onNodeWithTag("patternDone").performClick()
        rule.waitForIdle()
        assertEquals(listOf(0, 3, 6, 7, 8), result)
    }
}

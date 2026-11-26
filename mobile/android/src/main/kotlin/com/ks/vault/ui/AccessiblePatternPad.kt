package com.ks.vault.ui

import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ks.vault.R
import com.ks.vault.auth.Secrets

/** True while TalkBack or another explore-by-touch service is on; follows changes. */
@Composable
fun rememberTouchExploration(): Boolean {
    val context = LocalContext.current
    val am = remember { context.getSystemService(AccessibilityManager::class.java) }
    var on by remember { mutableStateOf(am?.isTouchExplorationEnabled == true) }
    DisposableEffect(am) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { on = it }
        am?.addTouchExplorationStateChangeListener(listener)
        onDispose { am?.removeTouchExplorationStateChangeListener(listener) }
    }
    return on
}

/**
 * The pattern pad for screen-reader users: nine buttons in the same 3 x 3 order. Each double tap adds
 * that dot; the button says which place it has in the pattern. Done hands the pattern over.
 */
@Composable
fun AccessiblePatternPad(onDone: (List<Int>) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val dots = remember { mutableStateListOf<Int>() }
    Column(modifier.widthIn(max = 360.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in 0..2) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0..2) {
                    val dot = row * 3 + col
                    val place = dots.indexOf(dot)
                    val label = if (place >= 0) stringResource(R.string.lock_dot_chosen, (dot + 1).toString(), (place + 1).toString())
                    else stringResource(R.string.lock_dot, (dot + 1).toString())
                    val content: @Composable () -> Unit = { Text(if (place >= 0) (place + 1).toString() else "•") }
                    val m = Modifier.weight(1f).aspectRatio(1f).testTag("dot$dot")
                        .semantics { contentDescription = label; selected = place >= 0 }
                    if (place >= 0) {
                        Button(onClick = {}, enabled = enabled, modifier = m) { content() }
                    } else {
                        OutlinedButton(onClick = { Secrets.addPatternDot(dots, dot) }, enabled = enabled, modifier = m) { content() }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { dots.clear() }, enabled = enabled && dots.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.lock_pattern_clear))
            }
            Button(
                onClick = {
                    val result = dots.toList()
                    dots.clear()
                    onDone(result)
                },
                enabled = enabled && dots.isNotEmpty(),
                modifier = Modifier.weight(1f).testTag("patternDone"),
            ) { Text(stringResource(R.string.common_done)) }
        }
    }
}

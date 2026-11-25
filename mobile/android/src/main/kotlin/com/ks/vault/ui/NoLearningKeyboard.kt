package com.ks.vault.ui

import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest

/**
 * Asks the keyboard not to learn anything typed inside [content] (IME_FLAG_NO_PERSONALIZED_LEARNING),
 * so ID numbers, notes and the secret do not end up in its dictionary or suggestions.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NoLearningKeyboard(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(
        interceptor = { request, nextHandler ->
            val noLearning = PlatformTextInputMethodRequest { outAttributes ->
                request.createInputConnection(outAttributes).also {
                    outAttributes.imeOptions = outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                }
            }
            nextHandler.startInputMethod(noLearning)
        },
        content = content,
    )
}

package com.ks.vault

import android.os.Build
import android.view.View

/**
 * Android-specific protection for the vault's window, besides FLAG_SECURE (specs/plan-per-platform.md):
 * - touches are ignored while another app draws over the window (tapjacking);
 * - autofill services are never offered the vault's fields;
 * - on Android 14 and later, accessibility services that are not real accessibility tools
 *   (TalkBack and the like still work) cannot read the screen.
 */
object WindowHardening {
    fun apply(decor: View) {
        decor.filterTouchesWhenObscured = true
        decor.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        if (Build.VERSION.SDK_INT >= 34) {
            decor.setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES)
        }
    }
}

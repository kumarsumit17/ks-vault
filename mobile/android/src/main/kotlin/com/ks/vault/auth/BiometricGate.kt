package com.ks.vault.auth

/**
 * When fingerprint or face unlock may be offered (specs/plan.md): it is turned on, the phone has not
 * restarted since the secret was last used (the boot count is the same), and no lockout wait is running.
 * After a restart the password or pattern is needed once again.
 */
object BiometricGate {
    fun allowed(enabled: Boolean, bootCountNow: Int?, bootCountAtSecretUnlock: Int?, lockoutWaitMillis: Long): Boolean =
        enabled && bootCountNow != null && bootCountNow == bootCountAtSecretUnlock && lockoutWaitMillis == 0L
}

package com.ks.vault.auth

/** Where the failure count is kept. It is not secret; it only has to survive a restart. */
interface LockoutStore {
    var failures: Int
    var lastFailureAt: Long
}

/**
 * Escalating wait after wrong secrets: 5 free tries, then 30 s, 1 min, 5 min, 15 min, then 1 hour
 * before each further try. Success resets it.
 */
class Lockout(private val store: LockoutStore, private val now: () -> Long) {

    /** Milliseconds until the next try is allowed; 0 when it is allowed now. */
    fun waitMillis(): Long {
        val delay = delayAfter(store.failures)
        if (delay == 0L) return 0
        val elapsed = now() - store.lastFailureAt
        if (elapsed < 0) {
            // The clock was set back: count the wait again from now, never for ever.
            store.lastFailureAt = now()
            return delay
        }
        return (delay - elapsed).coerceAtLeast(0)
    }

    fun recordFailure() {
        store.failures = store.failures + 1
        store.lastFailureAt = now()
    }

    fun recordSuccess() {
        store.failures = 0
        store.lastFailureAt = 0
    }

    val failures: Int get() = store.failures

    companion object {
        const val FREE_TRIES = 5
        private val STEPS = longArrayOf(30_000, 60_000, 5 * 60_000, 15 * 60_000)
        private const val MAX = 60 * 60_000L

        /** The wait after [failures] wrong tries in a row. */
        fun delayAfter(failures: Int): Long = when {
            failures < FREE_TRIES + 1 -> 0
            failures - FREE_TRIES - 1 < STEPS.size -> STEPS[failures - FREE_TRIES - 1]
            else -> MAX
        }
    }
}

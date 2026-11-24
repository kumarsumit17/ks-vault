package com.ks.vault

import com.ks.vault.auth.Lockout
import com.ks.vault.auth.LockoutStore
import com.ks.vault.auth.Secrets
import com.ks.vault.data.Search
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthTest {
    private class MemoryStore : LockoutStore {
        override var failures = 0
        override var lastFailureAt = 0L
    }

    @Test fun lockoutSchedule() {
        assertEquals(0, Lockout.delayAfter(0))
        assertEquals(0, Lockout.delayAfter(5))
        assertEquals(30_000, Lockout.delayAfter(6))
        assertEquals(60_000, Lockout.delayAfter(7))
        assertEquals(5 * 60_000, Lockout.delayAfter(8))
        assertEquals(15 * 60_000, Lockout.delayAfter(9))
        assertEquals(60 * 60_000, Lockout.delayAfter(10))
        assertEquals(60 * 60_000, Lockout.delayAfter(100))
    }

    @Test fun waitCountsDownAndSuccessResets() {
        var now = 1_000_000L
        val lockout = Lockout(MemoryStore()) { now }
        repeat(5) { lockout.recordFailure() }
        assertEquals(0, lockout.waitMillis())
        lockout.recordFailure()
        assertEquals(30_000, lockout.waitMillis())
        now += 10_000
        assertEquals(20_000, lockout.waitMillis())
        now += 20_000
        assertEquals(0, lockout.waitMillis())
        lockout.recordSuccess()
        lockout.recordFailure()
        assertEquals(0, lockout.waitMillis())
    }

    @Test fun clockSetBackDoesNotLockForEver() {
        var now = 10_000_000L
        val store = MemoryStore()
        val lockout = Lockout(store) { now }
        repeat(6) { lockout.recordFailure() }
        now -= 5_000_000
        assertEquals(30_000, lockout.waitMillis())
        now += 30_000
        assertEquals(0, lockout.waitMillis())
    }

    @Test fun lockoutSurvivesARestart() {
        val store = MemoryStore()
        val now = 5_000L
        repeat(7) { Lockout(store) { now }.recordFailure() }
        assertEquals(60_000, Lockout(store) { now }.waitMillis())
    }

    @Test fun minimums() {
        assertFalse(Secrets.passwordLongEnough("12345".toCharArray()))
        assertTrue(Secrets.passwordLongEnough("123456".toCharArray()))
        assertFalse(Secrets.patternLongEnough(listOf(0, 1, 2)))
        assertTrue(Secrets.patternLongEnough(listOf(0, 1, 2, 5)))
    }

    @Test fun patternChars() {
        assertArrayEquals("1259".toCharArray(), Secrets.patternChars(listOf(0, 1, 4, 8)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun patternWithARepeatedDotIsRejected() {
        Secrets.patternChars(listOf(0, 1, 0))
    }

    @Test fun searchPattern() {
        assertNull(Search.likePattern("  "))
        assertEquals("%pnr%", Search.likePattern(" pnr "))
        assertEquals("%50\\%\\_off\\\\%", Search.likePattern("50%_off\\"))
    }
}

package com.ks.vault

import com.ks.vault.reminders.ExpiryReminders
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(date: LocalDate, hour: Int = 9) = ZonedDateTime.of(date.atTime(hour, 0), zone).toInstant().toEpochMilli()

    @Test fun thirtyAndSevenDaysBeforeAtNine() {
        val expiry = LocalDate.of(2027, 3, 31)
        val now = at(LocalDate.of(2026, 11, 26), 12)
        assertEquals(
            listOf(at(LocalDate.of(2027, 3, 1)), at(LocalDate.of(2027, 3, 24))),
            ExpiryReminders.times(listOf(expiry.toEpochDay()), now, zone),
        )
    }

    @Test fun pastTimesAndDuplicatesAreDropped() {
        val now = at(LocalDate.of(2026, 12, 20), 12)
        val soon = LocalDate.of(2026, 12, 25).toEpochDay()   // 30 days before is past; 7 days before is past too (Dec 18)
        val later = LocalDate.of(2027, 1, 10).toEpochDay()   // Dec 11 is past; Jan 3 is ahead
        assertEquals(listOf(at(LocalDate.of(2027, 1, 3))), ExpiryReminders.times(listOf(soon, later, later), now, zone))
    }
}

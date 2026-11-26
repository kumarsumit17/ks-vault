---
type: Specification
title: ID card expiry reminders
description: The local notification before an ID card expires, what is kept while the vault is locked, and why it never names the card.
tags: [reminders, notifications, alarms, privacy]
---

# ID card expiry reminders

- **Off until turned on** in Settings, Reminders; turning it on asks for the notification permission (Android 13+).
- **When:** 30 days and 7 days before an ID card's "Valid until" date, at 9:00 local time.
- **How, while the vault is locked:** the dates are encrypted, so the times are worked out while the vault is open (each unlock and each change to an ID card) and only the list of times is kept, in plain app storage (`reminders` preferences): not which card, not its number. Android's `AlarmManager` wakes the app for the next time (inexact, so no exact-alarm permission); after a restart `BOOT_COMPLETED` sets it again and shows one that was missed.
- **The notification** says "An ID card expires soon" and "Unlock KS Vault to see which one". Tapping it opens the app; nothing from the vault is in it.
- **What it reveals:** someone who can read the app's private storage could see roughly when some ID card expires. That is the only trade-off, and it exists only while reminders are on. Turning them off or erasing the vault clears the times and the alarm.
- No network: a local alarm and a local notification only.

Tests: `ReminderTest` (times 30 and 7 days before at 9:00, past times and duplicates dropped) and `ReminderDeviceTest` (a due time posts one notification that does not name the card, is used up, and the next one stays).

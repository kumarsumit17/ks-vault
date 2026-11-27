---
type: Plan
title: Google Play readiness
description: What KS Vault needs to be published on Google Play, what is built in the repository, and the steps only the owner can do in the Play Console.
tags: [google-play, release, privacy, store-listing, plan]
status: built; owner steps open
---

# Google Play readiness

KS Vault keeps releasing on GitHub as it does now. Google Play is a second way to get the same app: same package (`com.ks.vault`), same version numbers, same suite signing key, so an install from either place updates from the other.

## Built in this repository

| Item | Where | Status |
|---|---|---|
| A `play` build type, the source of the AAB: the same app without the GitHub "Get the new version" button (Play forbids pointing users to updates outside Play); Settings says updates come through Google Play | `mobile/android/build.gradle.kts` (`BuildConfig.PLAY_BUILD`), `settings/SettingsScreen.kt`; release workflow input `play-build-type: "play"` | built |
| Privacy policy, English and Hindi: no data collected, nothing sent anywhere, no network permission, what stays on the phone, how to delete it | `PRIVACY.md` (public URL `https://github.com/kumasumit/ks-vault/blob/main/PRIVACY.md`) | built |
| Store listing text, English and Hindi: title, short description (80 characters), full description (4,000) | `mobile/android/play/listing/en-US/`, `mobile/android/play/listing/hi-IN/` | built |
| Phone screenshots (list, ID card, lock screen) and the 1024 x 500 feature graphic | `mobile/android/play/listing/en-US/images/`, made by the test `PlayScreenshots` (`-e screenshots true`): the real screens with sample data in a test window, because the app's own window is `FLAG_SECURE`; never from a real vault | built |
| 512 x 512 icon | `images/icon.png`, from `ic_launcher_foreground.xml` on the icon colour | built |
| Target API: Play requires a recent `targetSdk`; KS Vault targets 37 | — | ready |
| 16 KB memory pages (Android 15+): native libraries aligned | — | ready (checked) |

## Play Console answers (for the owner to enter)

- **Data safety:** no data collected, no data shared. Data is encrypted and stays on the device; the user can delete it (Settings, or "Forgot it?" erases the vault, or uninstalling).
- **Ads:** no ads. **In-app purchases:** none.
- **App access:** no account. A reviewer opens the app and creates their own password or pattern on first start.
- **Permissions:** camera (photos taken inside the app go straight into the vault, encrypted), notifications and start at boot (only for the optional ID card expiry reminders), biometrics (optional fingerprint or face unlock). None needs a special declaration form.
- **Content rating:** a utility with no user-generated sharing, no violence, no gambling: rated for everyone.
- **Category:** Productivity. **Contact:** the owner's developer e-mail.

## Steps only the owner can do

1. A Google Play developer account (one-time fee and identity check).
2. Create the app `com.ks.vault`, with the store listing and privacy-policy URL above.
3. **Play App Signing with the existing suite key** (alias `ks-release`), so Play installs and GitHub installs are signed alike and update each other: in the console, choose to use an existing key and upload it with Google's encryption tool. Claude never handles the keystore or its passwords.
4. Upload the AAB from the newest GitHub release (`ks-vault-vX.Y.Z.aab`, the `play` build) to the internal testing track.
5. A new personal developer account must run a **closed test with at least 12 testers for 14 days** before it can publish to production (Play policy for new personal accounts).
6. Then production.

Uploading to Play from the release workflow (a service-account key as a repository secret) is a later step, after the first manual release, and needs the owner's decision.

---
type: Plan
title: Per-platform optimization plan
description: How KS Vault's one build, the Android phone app, is made the best it can be on Android, following the suite rule that each platform build is optimized for its own OS.
tags: [plan, platforms, android, security, performance, accessibility]
status: approved
---

# Per-platform optimization plan

**Status: approved by the owner on 2026-11-25:** all five hardening items, 32-bit `x86` dropped, baseline profile only after measuring, and step 1 before stage 2 of the build plan. Suite rule (owner, 2026-11-25): each platform build is its own app, tuned to its OS, with as few dependencies as possible ([platform folders](https://github.com/kumasumit/ks-suite/blob/dev/specs/platform-folders.md#each-platform-build-is-optimized-for-its-os)). Model: [ks-jaiplayer's plan](https://github.com/kumasumit/ks-jaiplayer/blob/dev/specs/plan-per-platform.md).

KS Vault has **one platform: the Android phone** (`mobile/android/`). There is no Google TV, macOS, Windows or iOS build, and none is planned: a vault of IDs needs the phone's secure hardware, and typing on a TV remote is no place for a password. So this plan has one section; there is no `shared/` folder and nothing to share. Releases are tagged `android-vX.Y.Z` (suite rule [Platform tags](https://github.com/kumasumit/ks-suite/blob/dev/specs/versioning-signing.md), one-platform apps use their prefix too); the release workflow is ks-utility's `v0.14.0`. KS Vault has no update checker, so it needs no bridge release.

The [Build plan](plan.md)'s stages still decide the features (backup, biometrics, camera, share-in). This plan decides **how each is done the Android way**, plus what the first version should tighten now.

## 1. Audit: the Android build today (stage 1, 2026-11-25)

| Area | Today | Gap |
|---|---|---|
| Look | Compose, Material 3, dynamic colour on Android 12+, edge-to-edge, themed (monochrome) icon, dark theme | No predictive back animation (manifest flag not set) |
| Input | Touch; password field with the password keyboard, no autocorrect; 3 x 3 pattern pad with drag | The keyboard may still learn typed text in other fields (notes, numbers); the pattern pad is unusable with TalkBack |
| Language | Per-app language (`locales_config.xml`, AppCompat), English and Hindi | — |
| Security, screen | `FLAG_SECURE` (screenshots and recents blank, checked) | Other apps' accessibility services (not screen readers) can read the screen's text; touches are accepted under an overlay |
| Security, keys | Keystore AES-GCM key, StrongBox when present, PBKDF2 600,000 rounds | The Keystore key can be used while the phone is locked (`setUnlockedDeviceRequired` not set) |
| Security, data | SQLCipher, encrypted attachments, backup and device transfer off, sensitive clipboard cleared after 60 s | Autofill services may be offered the vault's fields (and could keep what is typed) |
| Performance | R8 and resource shrinking on; unlock takes a few seconds on the emulator | Cold start and unlock time not measured on a real phone; no baseline profile |
| Packaging | APK 9.6 MB, AAB built by the release workflow; native libraries 16 KB page-aligned (Android 15+, checked); four ABIs | `libsqlcipher.so` is 2.2 MB for 32-bit `x86`, which only old emulators use |
| OS integrations | Photo picker and file picker without permissions, locked on leaving | Biometrics, share-in and shortcuts are stage 2 and 3 (below) |
| Accessibility | Compose semantics; labels on icon buttons | The Add button's label is missing in the accessibility tree (seen in testing); font scale 200% not checked |

## 2. The plan, Android phone (`mobile/android/`)

**Security, the Android way** (each is a change to `auth/`, `crypto/` or the window, so the threat comes first):

- **Keys usable only while the phone is unlocked**: `setUnlockedDeviceRequired(true)` (Android 9+) on the Keystore key. Threat: malware running while the phone sits locked in a pocket could otherwise use the key. New vaults get it; there is no released vault to migrate.
- **Hidden from non-screen-reader accessibility services**: `View.setAccessibilityDataSensitive(YES)` on the window (Android 14+). Threat: an app that misuses an accessibility service to read the screen. TalkBack and other real accessibility tools still work.
- **No touches under an overlay**: `filterTouchesWhenObscured` on the window. Threat: tapjacking (another app draws over the unlock screen and catches the pattern or taps).
- **Autofill off for the whole vault**: `importantForAutofill = NO_EXCLUDE_DESCENDANTS` on the window (Android 8+). Threat: an autofill service offering to save ID numbers or the vault's secret.
- **Keyboard does not learn**: `IME_FLAG_NO_PERSONALIZED_LEARNING` on every text field, so the keyboard keeps no PAN numbers or notes in its dictionary.
- Biometrics (stage 2) with `BiometricPrompt`, Class 3 only, bound to a Keystore key through a `CryptoObject` and invalidated on new enrolment, as the build plan says.

**Look and input**

- Predictive back (`android:enableOnBackInvokedCallback="true"`), so Back previews the screen it returns to.
- Pattern pad with TalkBack: each dot is announced and can be chosen by exploring and double tapping, as Android's own lock screen does; the password stays the easier choice there and setup says so.
- Window-size aware layout for foldables and large phones in landscape: list and detail side by side when the width allows (Compose `WindowSizeClass` arithmetic by hand, no new library).

**Performance and startup**

- Measure on the phone emulator and a real phone: cold start to the unlock screen (target under 700 ms), unlock time (target under 1.5 s on a mid-range phone; PBKDF2 runs in Android's native crypto), memory while viewing a 20 MB photo.
- A baseline profile only if cold start misses the target (decision 3).

**Packaging and size**

- Ship `arm64-v8a`, `armeabi-v7a` and `x86_64` only; drop 32-bit `x86` (decision 2). The AAB lets Google Play deliver one ABI per phone later.
- Report APK and AAB size with each release; keep the 16 KB alignment check in the release notes' test list.

**OS integrations** (stage 2 and 3 of the build plan, done with Android's own parts)

- "Save to KS Vault" from the share sheet (`ACTION_SEND` for images and PDFs), unlocking first.
- In-app camera with CameraX, so no camera app writes a plain photo.
- App shortcut "Search the vault" (static shortcut, no library).

**Accessibility**

- Labels for every control, the Add button included; the lockout countdown announced politely, not every second.
- Font scale up to 200% and display size largest without clipping; touch targets at least 48 dp.
- Checked with TalkBack in English and Hindi.

## Step 1 status (2026-11-25)

Built on `dev`:

- Keystore key with `setUnlockedDeviceRequired(true)` (Android 9+). Checked that it is created and used with and without a screen PIN; using it while the phone is locked was not tested by hand (the vault locks when the screen goes off anyway).
- Window: `filterTouchesWhenObscured`, autofill excluded for the whole window, accessibility data sensitive on Android 14+ (`WindowHardening`); a device test checks all three and `FLAG_SECURE`.
- Every text field asks the keyboard not to learn (`NoLearningKeyboard`, `IME_FLAG_NO_PERSONALIZED_LEARNING` through Compose's text-input interceptor).
- Predictive back declared (`enableOnBackInvokedCallback`).
- ABIs `arm64-v8a`, `armeabi-v7a`, `x86_64`: release APK 9.6 MB → 7.3 MB. Native libraries stay 16 KB page-aligned.
- The Add button is a plain FAB with icon and text, so its label reaches TalkBack (it was reported as unlabeled before).

Measured on the Android 16 phone emulator (Apple silicon), release build:

- Cold start to the first screen: 0.7–1.2 s for the first starts after install, about 0.2 s once Android has compiled the app. The first starts miss the 700 ms target, so a baseline profile is the question for decision 3.
- Key derivation (600,000 PBKDF2 rounds): 1.4–2.4 s. To be measured on a real phone before changing anything.

## 3. Decisions for the owner

1. **Security hardening** (section 2, first group): all five as listed? Recommended: yes. Trade-off: with autofill off, a password manager cannot fill the vault's secret, which is intended.
2. **Drop the 32-bit `x86` ABI**: about 2.2 MB smaller APK; only very old emulators lose it. Recommended: yes.
3. **Baseline profile**: add the build-time `androidx.baselineprofile` plugin and test-only Macrobenchmark only if cold start misses 700 ms. Recommended: measure first, decide then.
4. **Order**: do this plan's step 1 (hardening, size, labels) before stage 2 of the build plan. Recommended: yes, it is small and makes the first release safer.

## 4. Build order and testing

| Step | What | Tested on |
|---|---|---|
| 1 | Hardening (unlocked-only key, accessibility-sensitive, overlay filter, autofill off, no keyboard learning), predictive back, ABI list, missing labels | Unit and device tests; phone emulator; Keystore behaviour with the screen locked |
| 2 | Measurements (cold start, unlock, memory); baseline profile if decision 3 says so | Phone emulator and a real phone |
| 3 | TalkBack pattern pad, font scale, large-screen layout | Phone emulator with TalkBack, a resizable emulator |
| 4 | Build plan stage 2 the Android way: backup file, BiometricPrompt, CameraX, share-in | Phone emulator; biometrics with the emulator's fingerprint |

Each step is built on `dev`, tested on the phone emulator (`scripts/emulators.sh start phone`, with `ANDROID_SERIAL` set), and recorded in [log](log.md).

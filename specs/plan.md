---
type: Plan
title: Build plan
description: What KS Vault does, how it keeps data safe, the libraries it uses and the stages it is built in, starting with a minimal first version.
tags: [plan, roadmap, security, features, libraries]
status: approved
---

# Build plan

KS Vault is an Android phone app that keeps notes, ID cards and tickets on the phone, encrypted, behind a pattern or password. It never uses the network. Every rule in [Constraints](constraints.md) holds for every stage.

## Features

**Items.** Everything in the vault is an *item* of one of three kinds. Each has a title, free notes, a pinned flag and any number of attachments.

| Kind | Fields besides title and notes |
|---|---|
| Note | none (the notes are the body) |
| ID card | document type (Aadhaar, PAN, passport, driving licence, voter ID, other), number, name on card, issued on, expires on |
| Ticket | kind (train, flight, bus, event, other), from, to, date and time, booking reference (PNR), seat |

- **Attachments:** photos and files on an item (an ID's front and back, a ticket's PDF). They are encrypted the moment they are copied in and shown from memory only.
- **List and search:** one list, newest first, pinned items on top, with a filter by kind. Search looks in titles, notes and fields as you type.
- **Copy a field** (a PAN number, a PNR) with one tap. The clipboard copy is marked sensitive (`EXTRA_IS_SENSITIVE`, so Android does not preview it) and cleared after 60 seconds.

**The lock.**

- **Set up on first start:** choose a password (at least 6 characters) or a pattern (at least 4 dots on a 3 x 3 grid), entered twice. The app warns plainly: there is no recovery; a forgotten secret means the data is gone, unless there is a backup file.
- **Unlock** with the same secret. Wrong attempts lock out for longer each time: 5 free tries, then 30 s, 1 min, 5 min, 15 min, then 1 hour per try. The count survives restarts.
- **Biometrics (optional, off by default):** a fingerprint or face unlock the user turns on in Settings after unlocking with the secret. The secret is still needed after a restart and after 5 failed biometric tries.
- **Auto-lock:** locks when the app goes to the background (with a short grace only while a picker the app itself opened is showing) and after an idle time (1, 2, 5 or 10 minutes; default 2). Locking closes the database and wipes the keys from memory.
- **Change the secret** in Settings (needs the current one).

**Settings:** lock type and change secret, biometrics, auto-lock time, language (Same as the system, English, Hindi), backup and restore, App updates ([App updates](app-updates.md)).

## Security design

One random **vault key** (256 bits) encrypts everything. It is never stored as it is:

```
secret (pattern or password)
   │  PBKDF2-HMAC-SHA256, 600,000 rounds, per-install random salt (16 bytes)
   ▼
secret key ──AES-256-GCM──▶ wraps the vault key   (inner layer: needs the secret)
                              │
Keystore key (AES-256-GCM, non-exportable, StrongBox when the phone has it)
                              ▼
                       wraps it again          (outer layer: needs this phone)
```

- **Both layers are needed** to open the vault: the secret, and the Keystore key that never leaves this phone's secure hardware. A copy of the app's files is useless on another device, and a pattern's small number of possibilities cannot be tried on a computer.
- **No secret is stored, not even a hash.** A wrong secret simply fails to open the inner layer (the GCM tag check, constant time). The lockout counter is stored next to it.
- **Database:** Room on SQLCipher (AES-256), opened with the vault key after unlock and closed on lock.
- **Attachments:** each is a file in app-private storage (`filesDir/attachments/`), encrypted with AES-256-GCM under its own random file key, which is kept in the encrypted database. They are decrypted into memory to show them, never to disk or the cache. Limit 20 MB per file.
- **Biometrics** (stage 2): a second Keystore key that needs a strong biometric for each use and is invalidated when a new fingerprint or face is enrolled, wraps a second copy of the vault key.
- **No network:** no `INTERNET` or `ACCESS_NETWORK_STATE` permission. A build check fails the build if the merged release manifest has either, so no library can add them unnoticed. The only outward step is **Get the new version**, which opens the GitHub releases page in the user's browser ([App updates](app-updates.md)).
- **No cloud backup:** `allowBackup="false"` and `dataExtractionRules` that exclude everything, for both cloud backup and device-to-device transfer.
- **Screen:** `FLAG_SECURE` on every window (no screenshots, no recording, blank thumbnail in recents). Secrets typed are not kept by the keyboard (`KeyboardType.Password`, no suggestions).
- **No logs** in `auth/` and `crypto/`; R8 removes all `android.util.Log` calls from release builds.
- **Kotlin strings** cannot be wiped, so the secret is read as a `CharArray` and the keys as `ByteArray`s, which are filled with zeros once used and on lock.

Threats this does **not** cover, said plainly in the spec and the app's About text: a rooted or infected phone, someone watching the pattern being drawn, and someone who knows the secret.

## Storage, backup and restore

- Everything stays in app-private storage: the database file and the encrypted attachment files.
- **Backup to a file** (stage 2): the user chooses where to save (Android's file picker, so a USB drive, the Downloads folder or any file app). The file (`KS-Vault-YYYY-MM-DD.ksvault`) holds every item and attachment, encrypted with AES-256-GCM under a key derived from a **backup password** the user types for this file (PBKDF2, 600,000 rounds, its own salt). It does not use the Keystore, so it can be restored on a new phone.
- **Restore** reads such a file with its backup password and either replaces the vault or adds to it.
- Backups are always started by hand. Nothing is copied anywhere automatically.

## Inbound sharing and outbound sharing

- **Inbound** (stage 2): KS Vault appears in the share sheet for images and PDFs ("Save to KS Vault"). It asks for the unlock first, then creates a ticket or ID with the file attached.
- **Outbound:** sharing an attachment or exporting an item to another app asks for confirmation first ("This will leave the vault unencrypted"). Stage 3.

## English and Hindi

Through ks-utility, as every suite app ([languages](https://github.com/kumasumit/ks-utility/blob/main/specs/languages.md)):

- All interface text is in `mobile/android/src/main/lang/en.json` and `hi.json`, with the shared catalogue (`common.*`). The ks-utility tool turns them into Android string resources before each build and checks them as part of `check`.
- Language: Same as the system, English or Hindi, through `AppCompatDelegate.setApplicationLocales` with `en` and `hi` in `locales_config.xml`.
- Hindi follows the [Hindi style guide](https://github.com/kumasumit/ks-utility/blob/main/specs/hindi-style.md); dates and numbers use Latin digits.
- ks-utility moves to `v0.6.0`.

## Code layout

- The Gradle build goes in `mobile/android/` (Gradle path `:mobile:android`); the root `app/` skeleton is removed. No `shared/` folder (one target).
- Packages under `com.ks.vault`: `auth/` (setup, unlock, lockout, auto-lock), `crypto/` (key derivation, Keystore, file encryption), `data/` (Room database, entities), `notes/` (the item list, detail and editor screens, for every kind), `documents/` (attachments: import, thumbnails, viewer), `settings/`, `ui/` (theme, pattern pad, navigation, shared pieces).
- One activity, Jetpack Compose screens, simple state holders without a dependency-injection library.

## Libraries and licences

Every one is checked against the merged manifest for network permissions when it is added ([Dependencies](dependencies.md)).

| Library | For | Licence | Stage |
|---|---|---|---|
| Kotlin standard library, coroutines | language | Apache-2.0 | 1 |
| AndroidX Activity Compose, Compose UI, Foundation, Material 3, Material Icons (core) | interface | Apache-2.0 | 1 |
| AndroidX AppCompat | per-app language before Android 13 | Apache-2.0 | 1 |
| AndroidX Lifecycle (process, runtime-compose) | auto-lock on background | Apache-2.0 | 1 |
| AndroidX Room (runtime; compiler through KSP at build time) | database | Apache-2.0 | 1 |
| SQLCipher for Android (`net.zetetic:sqlcipher-android`) | database encryption | BSD-3-Clause (Zetetic community licence) | 1 |
| ks-utility | language files, version text, release workflow | MIT (first-party) | 1 |
| AndroidX Biometric | biometric unlock | Apache-2.0 | 2 |
| CameraX (camera-core, camera-camera2, camera-lifecycle) | photos taken inside the app, so no plain copy is ever written by a camera app | Apache-2.0 | 2 |
| JUnit 4 (tests only) | unit tests | EPL-1.0 | 1 |
| AndroidX Test, Compose UI Test (tests only) | device tests | Apache-2.0 | 1 |

Not used: no Argon2 library (PBKDF2 is built into Android; the Keystore layer already stops off-phone guessing), no image-loading or JSON library (bitmaps are decoded from memory; the backup format is written by hand), no dependency injection.

## Roadmap

**Stage 1 — first version** (the minimal app to approve and test). **Built and tested on `dev`, 2026-11-24** ([status](#stage-1-status)):

1. Gradle build in `mobile/android`, ks-utility `v0.6.0`, language files, the no-network build check, FLAG_SECURE, backups off, R8 log stripping.
2. `crypto/` and `auth/`: set up with password or pattern, unlock, escalating lockout, auto-lock (background and idle), change secret.
3. Encrypted database; notes, ID cards and tickets: add, edit, delete, pin; list with kind filter and search; copy a field.
4. Photo and PDF attachments picked from the phone (Android's photo picker and file picker, no permission needed), encrypted; photos shown in the app. A PDF's pages are drawn into images once, while it is read from the picker (`PdfRenderer` needs a real file, and this way no plain copy is ever made); the original PDF and its page images are stored encrypted, and the pages are shown from memory.
5. Settings: lock, auto-lock time, language, App updates.
6. Tests: crypto and lockout unit tests; device tests for the Keystore, setup and unlock, and an item round trip; the merged-manifest check. Checked by hand on the phone emulator.

**Stage 2:** backup to an encrypted file and restore; biometric unlock; camera inside the app; "Save to KS Vault" from the share sheet.

**Stage 3:** sharing out with confirmation; an expiry reminder for ID cards (a local notification, no network); a home-screen shortcut to unlock straight into search.

**Later, planned separately:** Google Play readiness.

## Stage 1 status

Built on `dev` on 2026-11-24 and tested:

- **Unit tests** (`./gradlew testDebugUnitTest`, 22): PBKDF2 against the RFC 7914 vector, AES-GCM round trip and tampering, the key file (create, unlock, wrong secret, change, another phone's Keystore, a changed header, erase), attachment files (no plain bytes on disk, swapped files, path names), the lockout schedule (restart, clock set back), minimums, pattern encoding, search escaping.
- **Device tests** (`./gradlew connectedDebugAndroidTest`, 5, Android 16 phone emulator): the real Keystore key; the SQLCipher file has no plain text and a wrong key does not open it; attachments and PDF pages are deleted with their item; setup, a note, lock, a wrong and a right unlock, and search through the interface.
- **Checks** (`./gradlew check`): lint with no errors, the language files, and the no-network check on the debug and release manifests. The release APK's only permission is AndroidX's internal `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
- **By hand**, on the R8 release build: pattern setup and unlock, a wrong pattern counted, an ID card and a ticket with fields, search by number, a PDF and a photo added through the pickers without locking, the PDF shown as pages, screenshots and the recents thumbnail blank, locking on leaving the app and after 1 minute idle, Hindi and back to the system language.

Notes from the build:

- Compose is at 1.12.1 and Material 3 at 1.4.0: Compose 1.13 needs compile SDK 37.1, and this machine has 37.0.
- Unlocking takes a few seconds on the emulator (600,000 PBKDF2 rounds); expect about a second on a recent phone.
- New Hindi words, for the owner's review and then ks-utility's term list (`specs/hindi-style.md`, a change in ks-utility, not here): वॉल्ट (vault), पैटर्न (pattern), लॉक / अनलॉक करें (lock / unlock), शीर्षक (title), एन्क्रिप्ट (encrypt), बिंदु (dot of the pattern).

## Release

- `.github/workflows/release.yml` calls ks-utility's `kotlin-app-release.yml@v0.10.1` (releases tagged `android-vX.Y.Z`) with `android-module: ":mobile:android"` and no `tv-module` (phone only).
- The repository's four signing secrets are set by the owner and checked before the first release; a rehearsal must pass before the first tag. Tested work is built, committed, pushed and released without waiting (owner, 2026-11-26): no approval before a tag, and the owner's hand checks on a real phone come later and never block a release. A feature raises MINOR, a fix raises PATCH; nothing is released while a test fails.
- `ks-suite.json` (the landing-page card) stays in step with what the app does.

## Decisions (owner, 2026-11-24)

The plan was approved with these choices:

1. **Key derivation:** PBKDF2-HMAC-SHA256 with 600,000 rounds (built in, no library), not Argon2id: the Keystore layer keeps guessing on the phone.
2. **First version:** stage 1 as above; backup to a file comes in stage 2.
3. **Minimums:** a pattern of at least 4 dots, a password of at least 6 characters.
4. **Debug build:** the same package `com.ks.vault`, so a debug build replaces an installed release.

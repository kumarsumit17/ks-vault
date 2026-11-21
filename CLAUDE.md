# ks-vault

Private, fully offline vault and notekeeper for notes, government IDs, and tickets, protected by pattern or password authentication.

## Specs
- All specs for this project live in `specs/` in the repository root, as an Open Knowledge Format (OKF) v0.2 bundle. Never create spec files anywhere else. Start at `specs/index.md`.
- One topic per file. Every file except `index.md` and `log.md` starts with YAML frontmatter: `type` (required: `Specification`, `Policy`, `Plan`, `Playbook` or `Reference`), `title`, `description` (one sentence), `tags`.
- When a spec is added: list it in `specs/index.md` with its description. When a spec is added or changed: add a line under today's date (`# YYYY-MM-DD`, newest first) in `specs/log.md`.
- Link between specs with relative markdown links (`[Dependencies](dependencies.md)`), so they also work on GitHub.

## Stack
- Kotlin, Jetpack Compose, Room + SQLCipher, Android Keystore, BiometricPrompt (optional unlock)
- Package: `com.ks.vault`. Features: `auth/`, `crypto/`, `notes/`, `documents/`
- minSdk 26 · targetSdk latest stable

## Security constraints (non-negotiable)
- **No network, ever.** The manifest must not declare `INTERNET` or `ACCESS_NETWORK_STATE`, and no dependency may pull those permissions in. Check the merged manifest after adding any library.
- **Encryption at rest.** The Room database uses SQLCipher. Its key is random, wrapped by a non-exportable AES key in Android Keystore, and unwrapped only after a successful unlock.
- **Credentials are never stored.** Derive a key from the pattern or password (Argon2id, or PBKDF2-HMAC-SHA256 with at least 310k iterations), using a per-install salt. Compare in constant time. Add an escalating lockout after failed attempts.
- **Attachments** (ID scans, ticket images) are encrypted with the same key hierarchy in app-private storage. Never write them to shared storage or the cache in plaintext.
- **Screen protection.** Set `FLAG_SECURE` on all windows, which also blanks the recents thumbnail. Lock on `ON_STOP` and after an idle timeout. Clear decrypted data from memory when locking.
- **Backups off.** Set `android:allowBackup="false"` and add `dataExtractionRules` that exclude everything. Export only as an encrypted file the user starts by hand.
- **No logging of secrets.** No `Log.*` calls in `auth/` or `crypto/`. Release builds strip logs through R8.
- Inbound share intents (for example, saving a ticket from another app) are allowed. Outbound sharing of vault content needs an explicit confirmation from the user.
- Before changing anything in `auth/` or `crypto/`, explain the change and the threat it addresses.

## Commands
```bash
./gradlew assembleDebug
./gradlew installDebug
./gradlew testDebugUnitTest             # crypto + auth unit tests must pass
./gradlew connectedDebugAndroidTest     # Keystore behaviour needs a real device / emulator
./gradlew lintDebug
./gradlew :app:processReleaseManifest && grep -i "permission" app/build/intermediates/merged_manifests/release/*/AndroidManifest.xml   # verify no INTERNET
```

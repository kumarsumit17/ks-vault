---
type: Policy
title: Constraints
description: The security constraints every change to KS Vault must keep.
tags: [constraints, policy]
status: draft
---

# Constraints

- **No network, ever.** The manifest must not declare `INTERNET` or `ACCESS_NETWORK_STATE`, and no dependency may pull those permissions in. Check the merged manifest after adding any library.
- **Encryption at rest.** The Room database uses SQLCipher. Its key is random, wrapped by a non-exportable AES key in Android Keystore, and unwrapped only after a successful unlock.
- **Credentials are never stored.** Derive a key from the pattern or password (Argon2id, or PBKDF2-HMAC-SHA256 with at least 310k iterations), using a per-install salt. Compare in constant time. Add an escalating lockout after failed attempts.
- **Attachments** (ID scans, ticket images) are encrypted with the same key hierarchy in app-private storage. Never write them to shared storage or the cache in plaintext.
- **Screen protection.** Set `FLAG_SECURE` on all windows, which also blanks the recents thumbnail. Lock on `ON_STOP` and after an idle timeout. Clear decrypted data from memory when locking.
- **Backups off.** Set `android:allowBackup="false"` and add `dataExtractionRules` that exclude everything. Export only as an encrypted file the user starts by hand.
- **No logging of secrets.** No `Log.*` calls in `auth/` or `crypto/`. Release builds strip logs through R8.
- Inbound share intents (for example, saving a ticket from another app) are allowed. Outbound sharing of vault content needs an explicit confirmation from the user.
- Before changing anything in `auth/` or `crypto/`, explain the change and the threat it addresses.

These rules are also in `CLAUDE.md`, where they are loaded into every AI-assisted session; change both together.

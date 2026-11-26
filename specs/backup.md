---
type: Specification
title: Backup file
description: The encrypted backup file KS Vault writes and restores, its format, and how restoring keeps the vault safe from a wrong password or a damaged file.
tags: [backup, restore, encryption, format]
---

# Backup file

A backup is started by hand from Settings and saved where the user chooses (Android's file picker). Nothing is ever copied anywhere by itself ([Constraints](constraints.md)).

## Password

The user types a **backup password** twice (at least 8 characters). It is not the vault's secret and is not stored. The file uses no Keystore key, so it can be restored on another phone.

## Format (`.ksvault`, format 1)

```
"KSVB" || format (1 byte) || PBKDF2 rounds (4 bytes) || salt (16 bytes) || chunked stream
```

- Key: PBKDF2-HMAC-SHA256(backup password, salt, 600,000 rounds), 256 bits.
- The stream is cut into 64 KiB chunks; each is `last flag (1) || length (4) || AES-256-GCM sealed chunk`, with `header || chunk number || last flag` as associated data. A changed, reordered, swapped or cut-off chunk fails, and so does data after the last chunk (`crypto/ChunkedStreams.kt`).
- Inside: records of items (every field), then attachments with their bytes (a PDF before its page images), then an end mark (`backup/BackupFormat.kt`). Ids are the old vault's; restoring gives new ones.

## Restoring

- The user chooses the file, types its backup password, and picks **Add to the vault** or **Replace the vault** (replace asks to confirm).
- Everything happens in one database transaction. A wrong password fails on the first chunk before anything is written; a damaged file rolls the transaction back and deletes the attachment files written so far. In both cases the vault is as it was. Replaced attachments' files are deleted only after the transaction succeeds.
- Each restored attachment is encrypted again under a new random file key.

## Tests

Unit tests (`BackupTest`): several chunks, an empty stream, a cut-off stream, another header or key, a full backup round trip with Hindi text and a PDF with a page, a wrong password, a changed or cut-off file and a file that is not a backup. Device test (`backupRestoresIntoAnotherVault`): backup from one vault, restore into another with a wrong password (nothing changes), add, and replace (no stray files).

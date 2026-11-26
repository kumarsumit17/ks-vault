---
type: Specification
title: Sharing in and out
description: How photos and PDFs come into KS Vault from other apps, and how an item or a file leaves it, always after a confirmation and never through a plain file on disk.
tags: [sharing, intents, provider, security]
---

# Sharing in and out

## In: "Save to KS Vault"

- KS Vault is in the share sheet for photos and PDFs (`ACTION_SEND`, `ACTION_SEND_MULTIPLE`; `image/*`, `application/pdf`). Inbound sharing is allowed by [Constraints](constraints.md).
- The vault unlocks first (password, pattern, fingerprint or face). Then the user picks ticket, ID card or note and a title (the first file's name), and the files are copied in and encrypted (`documents/SaveSharedScreen.kt`, the same import as Add file).
- If no file can be read, no empty item is left behind; the error stays on the save screen.

## Out: always confirmed

- **An item's text** (title, filled-in fields, notes): Share on the item, a confirmation ("leaves the vault and is no longer encrypted"), then the share sheet with `text/plain`.
- **A file**: Share in the viewer, a confirmation, then the share sheet with `content://com.ks.vault.share/<token>/<name>` and a one-time read grant (`documents/ShareOut.kt`).
  - The provider is not exported. A token is random, kept in memory only, and dropped when the vault locks.
  - Reading it gets the decrypted bytes in an anonymous in-memory file (`memfd`, Android 11+), which is seekable, so PDF viewers work; on Android 10 and older, a pipe. Nothing is written to disk or the cache (checked: the app's cache stays empty).
  - So that the receiving app can read it, the vault stays open for up to 3 minutes while KS Vault is in the background. The grace ends as soon as KS Vault's screen comes back, so leaving it again locks at once.

## Tests

On the phone emulator: a real share from the Files app into KS Vault (unlock, title from the file name, PDF with its pages); an item's text in the share sheet; a PDF shared out and read back in through the provider (pages rendered from the in-memory file); leaving the app after a share locks it.

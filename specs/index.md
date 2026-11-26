---
okf_version: "0.2"
---

# Product specifications

* [Overview](overview.md) - What KS Vault is for, the platforms it runs on and its tech stack.
* [Backup file](backup.md) - The encrypted backup file KS Vault writes and restores, its format, and how restoring keeps the vault safe from a wrong password or a damaged file.
* [App updates](app-updates.md) - How KS Vault tells the user how to get a newer version without any network access, following the suite's app-update behaviour within its offline rule.
* [Per-platform optimization plan](plan-per-platform.md) - How KS Vault's one build, the Android phone app, is made the best it can be on Android, following the suite rule that each platform build is optimized for its own OS.
* [Build plan](plan.md) - What KS Vault does, how it keeps data safe, the libraries it uses and the stages it is built in, starting with a minimal first version.

# Policies

* [Constraints](constraints.md) - The security constraints every change to KS Vault must keep.
* [Dependencies](dependencies.md) - Third-party dependencies are kept to the minimum; the order of preference and what must be reported for each new one.

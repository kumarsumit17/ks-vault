# 2026-11-25

* **Update**: Releases are tagged `android-vX.Y.Z` (suite rule "Platform tags"): `.github/workflows/release.yml` on `kotlin-app-release.yml@v0.10.1`, triggered by `android-v*.*.*` (and plain `v*.*.*`); [Per-platform optimization plan](plan-per-platform.md), [Build plan](plan.md) and the README updated. No bridge release: KS Vault has no update checker.
* **Update**: `ks-utility` moved from `v0.9.0` to `v0.10.1`: KS Vault's new Hindi terms are in its term list (owner review); the new release-list helpers are not used here (no network). Still no network code in ks-utility.
* **Update**: [Per-platform optimization plan](plan-per-platform.md) step 1 built: unlocked-only Keystore key, window hardening (overlay filter, autofill off, accessibility data sensitive), keyboard without learning, predictive back, `x86` dropped (APK 9.6 → 7.3 MB), labelled Add button; cold start and key-derivation times recorded.
* **Update**: [Per-platform optimization plan](plan-per-platform.md) approved: all five hardening items, `x86` dropped, baseline profile after measuring, step 1 before build-plan stage 2.
* **Creation**: [Per-platform optimization plan](plan-per-platform.md) added (proposed), for the suite rule that each platform build is optimized for its OS: an audit of the Android build and the Android-specific changes; waiting for the owner's approval.
* **Update**: `ks-utility` moved from `v0.6.0` to `v0.9.0` (more languages, the translation tools; [ks-utility translation tools](https://github.com/kumarsumit17/ks-utility/blob/main/specs/translating.md)).

# 2026-11-24

* **Update**: Stage 1 built on `dev`: Gradle build in `mobile/android` (`:mobile:android`), the root `app/` skeleton removed; `ks-utility` moved to `v0.6.0`; release workflow on `kotlin-app-release.yml@v0.6.0` with `android-module: ":mobile:android"` and no TV module. [Build plan](plan.md) records the test results.
* **Update**: [Dependencies](dependencies.md) added for stage 1 (all Apache-2.0 unless noted): AndroidX Activity Compose 1.13.0, AppCompat 1.8.0 (per-app language), Compose UI and Foundation 1.12.1, Material 3 1.4.0, Material Icons core 1.7.8, Lifecycle process and runtime-compose 2.11.0 (auto-lock), Room 2.8.5 with its KSP compiler (build time), AndroidX SQLite 2.7.1, SQLCipher for Android 4.20.0 (BSD-3-Clause, Zetetic); tests: JUnit 4.13.2 (EPL-1.0), AndroidX Test runner 1.7.0 and ext-junit 1.3.0, Compose UI Test 1.12.1. None adds a permission; the build fails if the merged manifest asks for network access.
* **Update**: [Build plan](plan.md) approved by the owner: PBKDF2 (600,000 rounds), stage 1 as the first version, minimums 4 dots and 6 characters, debug build on the same package.
* **Creation**: [Build plan](plan.md) added (proposed): features, security design, backup, languages, libraries with licences and a staged roadmap; waiting for the owner's approval.

# 2026-11-23

* **Update**: Release: `.github/workflows/release.yml` now calls ks-utility's shared release workflow (`kotlin-app-release.yml@v0.4.0`, [spec](https://github.com/kumarsumit17/ks-utility/blob/main/specs/release-workflow.md)): one workflow for every Kotlin app, with a rehearsal input, pinned actions, signature checks, build-provenance attestations and `gh` publishing.
* **Creation**: [App updates](app-updates.md) added: the App updates section shows the version and a Get the new version link to the GitHub releases page; no update check or download, because of the offline constraint.
* **Update**: `ks-utility` moved to `v0.3.0`.

# 2026-11-21

* **Update**: [Dependencies](dependencies.md): `ks-utility` added as a git submodule for shared generic utilities.
* **Initialization**: `specs/` created as an Open Knowledge Format (OKF) v0.2 bundle with this log and `index.md`.
* **Creation**: [Overview](overview.md), [Constraints](constraints.md) added as drafts, from `README.md` and `CLAUDE.md`.
* **Creation**: [Dependencies](dependencies.md) added: third-party dependencies are kept to the minimum.

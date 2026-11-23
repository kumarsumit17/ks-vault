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

Emulators (from the KS-Suite workspace; suite spec `specs/testing.md`): `../scripts/emulators.sh create` once, then `../scripts/emulators.sh start phone` boots the phone AVD (`ks-phone-36`, API 36). Then:
```bash
export ANDROID_SERIAL=$(../scripts/emulators.sh serial phone)
./gradlew installDebug connectedDebugAndroidTest
adb shell monkey -p com.ks.vault -c android.intent.category.LAUNCHER 1   # open it (use the debug package if it has a suffix)
```

## Platform folders
- Every KS-Suite app repo has the same top-level platform folders: `tv/google-tv/`, `desktop/mac/`, `desktop/windows/`, `mobile/android/` and `mobile/ios/`. All five exist in every app repo, whether or not the app supports that platform yet.
- This app targets `mobile/android`. The other folders are empty on purpose (a `.gitkeep` only): they mark where that platform's code would go, not missing work. Do not add code for a platform that is not a target without the owner asking.
- Three layers, narrowest first: a platform folder holds code for one platform of this app; `shared/` in the repository root holds code used by two or more platforms of this app, written once and never copied between platform folders; `ks-utility` holds generic code used by several apps. This app has one target, so it has no `shared/` folder.
- The skeleton still has `app/` in the repository root. When the Gradle build is created, the app module goes in `mobile/android/`.
- Never rename, merge or remove these folders, and never add another top-level platform folder; the layout is a suite-wide rule.

## Shared code (`ks-utility`)
- Generic utility code shared across the KS-Suite apps lives in the `ks-utility` repository. It is a dependency of this app: a git submodule at `ks-utility/`, pinned to a commit. After cloning, run `git submodule update --init`.
- Before writing a generic helper (one that knows nothing about this app), look in `ks-utility/kotlin` and use it from there. If it is missing and another app would need it too, add it in the `ks-utility` repository, not here. Application-specific logic stays in this repo.
- Never edit files under `ks-utility/` from this repo. Change them in the `ks-utility` repository, then move the pin: `git -C ks-utility fetch && git -C ks-utility checkout <commit>`, and commit the changed `ks-utility` entry.
- Gradle wiring, to add when this repo's Gradle build is created: `includeBuild("ks-utility/kotlin")` in `settings.gradle.kts`, `ks-utility = { module = "com.ks:utility" }` in `gradle/libs.versions.toml`, and `implementation(libs.ks.utility)` in the module that uses it. It is a plain Kotlin/JVM library with no Android SDK.
- `ks-utility` has no network code and no third-party dependencies, so it keeps the security constraints above. Check that again whenever the pin moves.
- CI checks out with `submodules: true` (`.github/workflows/release.yml`).

## Working efficiently (keep token use low)
- Use the knowledge graph first: follow the rules in the `graphify` section at the end of this file. [graphify](https://github.com/Graphify-Labs/graphify) is a development tool only; it is never a dependency of the code and is never bundled or shipped.
- One-time setup on a new machine (owner): `uv tool install graphifyy` (the package name has two y's), then `/graphify .` in Claude Code. The setup in `.claude/` is committed; `graphify-out/` is generated and git-ignored.
- Read only the specs the task needs, and do not re-read a file that has not changed in this session.
- Never read generated files: `build/`, `.gradle/`, `graphify-out/graph.json`.
- Keep command output short (show only failures), and keep summaries brief.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- When graphify-out/graph.json exists, run `graphify query "<question>"` before your first grep, search or source-file read for a codebase question, and put this rule in every subagent prompt that explores code. Read raw files only for lines the graph or an earlier read already located. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. When the answer is under a Markdown heading, `graphify section "<heading>"` prints that section's text, so the .md need not be opened. These return a scoped subgraph, usually much smaller than GRAPH_REPORT
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
- `graphify-out/` is generated and git-ignored: never commit it.

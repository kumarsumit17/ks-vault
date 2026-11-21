---
type: Policy
title: Dependencies
description: Third-party dependencies are kept to the minimum; the order of preference and what must be reported for each new one.
tags: [dependencies, gradle, policy]
---

# Dependencies

Third-party dependencies are kept to the minimum.

- Add a dependency only when writing it ourselves would be unreasonable or unsafe. Order of preference: the Kotlin and Android standard libraries or a small amount of our own code; then a library already in `gradle/libs.versions.toml` (AndroidX and Compose bring many); a new one last.
- Always use a maintained library for TLS, cryptography and other security-critical code. Never hand-write those.
- Declare every dependency and its version in the version catalog `gradle/libs.versions.toml`; never write a version in a `build.gradle.kts`. Take only the artifacts used, not a whole BOM family for convenience.
- No dependency for convenience alone (dependency injection, networking wrappers, test helpers, small utilities): write those by hand.
- No dependency may pull in `INTERNET` or `ACCESS_NETWORK_STATE`: check the merged manifest after adding one ([Constraints](constraints.md)).
- No analytics, crash-reporting or ad SDKs, ever.
- Libraries named in the other specs are candidates, not commitments. Justify each at the stage that first needs it.
- Every new dependency is reported with the reason it is needed and what it adds to the dependency tree (`./gradlew :app:dependencies`), and recorded in `specs/log.md` in the same commit.
- `ks-utility`, the suite's own generic utilities, is a dependency of this app: a git submodule at `ks-utility/`, pinned to a commit and included as a Gradle build (`com.ks:utility`). It is first-party and has no third-party dependencies. Generic helpers go there; application-specific code stays here. How it is wired: `ks-utility/specs/usage.md`.

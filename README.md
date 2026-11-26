# KS Vault

> Part of the [KS-Suite](https://github.com/kumasumit/KS-Suite).

A private, fully offline vault and notekeeper protected by pattern/password authentication. Stores notes, government IDs, and tickets encrypted on-device with no network access.

| | |
|---|---|
| **Platform** | Android |
| **Tech stack** | Kotlin, Jetpack Compose, Room + SQLCipher, Android Keystore |

## Getting started

```bash
git clone --recurse-submodules <repo-url> ks-vault
cd ks-vault
./gradlew assembleDebug          # mobile/android/build/outputs/apk/debug/
./gradlew check                  # unit tests, lint, language files, no-network check
```

Needs JDK 17, the Android SDK (compile SDK 37) and Node 23.6+ (for the ks-utility language tool). The plan and the security design are in [specs/plan.md](specs/plan.md).

## Release

Releases are cut by pushing an Android platform tag (`git tag -a android-v1.2.3 -m android-v1.2.3 && git push origin android-v1.2.3`).
See the suite-level [CI/CD guide](https://github.com/kumasumit/KS-Suite#-cicd-semantic-versioning--automated-signing).

## License

MIT. See [LICENSE](LICENSE).

# KS Vault

> Part of the [KS-Suite](https://github.com/kumarsumit17/KS-Suite).

A private, fully offline vault and notekeeper protected by pattern/password authentication. Stores notes, government IDs, and tickets encrypted on-device with no network access.

| | |
|---|---|
| **Platform** | Android |
| **Tech stack** | Kotlin, Jetpack Compose, Room + SQLCipher, Android Keystore, BiometricPrompt |

## Getting started

```bash
git clone --recurse-submodules <repo-url> ks-vault
cd ks-vault
./gradlew assembleDebug
```

## Release

Releases are cut by pushing a semantic version tag (`git tag v1.2.3 && git push --tags`).
See the suite-level [CI/CD guide](https://github.com/kumarsumit17/KS-Suite#-cicd-semantic-versioning--automated-signing).

## License

MIT. See [LICENSE](LICENSE).

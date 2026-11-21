---
type: Specification
title: Overview
description: What KS Vault is for, the platforms it runs on and its tech stack.
tags: [overview, platform, stack]
status: draft
---

# Overview

A private, fully offline vault and notekeeper protected by pattern/password authentication. Stores notes, government IDs, and tickets encrypted on-device with no network access.

| | |
|---|---|
| **Platform** | Android |
| **Tech stack** | Kotlin, Jetpack Compose, Room + SQLCipher, Android Keystore, BiometricPrompt |

## Stack

- Kotlin, Jetpack Compose, Room + SQLCipher, Android Keystore, BiometricPrompt (optional unlock)
- Package: `com.ks.vault`. Features: `auth/`, `crypto/`, `notes/`, `documents/`
- minSdk 26 · targetSdk latest stable

The app is planned: this repository holds the project structure and its rules, with app code still to come.

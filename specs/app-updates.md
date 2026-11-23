---
type: Specification
title: App updates
description: How KS Vault tells the user how to get a newer version without any network access, following the suite's app-update behaviour within its offline rule.
tags: [update, github, release, settings, android, offline]
status: draft
---

# App updates

Every KS-Suite app has an **App updates** section ([suite behaviour](https://github.com/kumarsumit17/ks-utility/blob/main/specs/app-update.md)). KS Vault keeps its constraint (no network, ever), so it **never checks for updates by itself** and has no download or install step: the app has no `INTERNET` permission, and nothing in this section may add one.

- **Settings, App updates section:** "This is version X." (`common.appUpdate.current`; for a `0.0.0` build `common.appUpdate.dev`) and a **Get the new version** button (`common.appUpdate.getButton`) that opens `https://github.com/kumarsumit17/ks-vault/releases/latest` (`GitHubRepo.latestReleasePageUrl`, ks-utility `v0.3.0`) in the user's browser with an `ACTION_VIEW` intent. The browser makes the request, not this app; the app sends nothing and learns nothing.
- No "Check for updates automatically" switch and no **Check for updates** button, since both need the network.
- The user installs the new APK by hand. Android accepts it as an update only when it is signed with the same key, which the suite's release signing keeps.
- Changing this (for example, allowing the one daily request the other apps make) is a change to [Constraints](constraints.md) and needs the owner's decision first.

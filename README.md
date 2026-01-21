# RepoSync

RepoSync is an Android app that synchronizes a user-selected folder with a Git repository using a secure mirror architecture. It uses the Storage Access Framework (SAF) for folder access, maintains an internal Git working tree, and mirrors changes both ways with conservative defaults.

## Features

- SAF-based folder selection (no all-files access by default)
- Internal Git working tree using JGit
- Bidirectional sync with safe defaults (no deletes unless enabled)
- GitHub OAuth (preferred), HTTPS token, and SSH auth
- Host key verification with SHA256 fingerprints
- WorkManager-ready background sync (manual by default)
- Encrypted credential storage via Jetpack Security Crypto

## Build & Run

1. Open this repo in Android Studio.
2. Sync Gradle.
3. Run the `app` configuration on a device or emulator (API 24+).

### GitHub OAuth (Local Dev)

RepoSync uses GitHub OAuth for browser-based login. For local builds you must provide a client secret via `local.properties` (not committed to git):

```
github.client.secret=YOUR_SECRET
```

For production, do **not** ship a client secret in the app. Use a backend to exchange the code for tokens and return only the access token to the app.

## Setup a Profile

1. Tap **+** to create a profile.
2. Pick a target folder using SAF.
3. Enter remote URL and branch.
4. Configure auth:
   - **GitHub OAuth (recommended):** login with GitHub and select a repository.
   - **HTTPS token (advanced):** enter username (often `token` or your Git provider username) and token.
   - **SSH key (advanced):** generate a key and add the public key to your Git provider.
5. Tap **Test connection** to verify and trust SSH host keys if needed.
6. Choose **Clone remote** or **Import target folder**, then **Setup & Sync**.
7. Optional: enable periodic sync and set constraints under Background sync.
8. Optional: apply an ignore preset (Generic or Obsidian) under Ignore presets.

## HTTPS Token Setup

- **GitHub:** use a Personal Access Token with repo scope.
- **GitLab:** use a Personal Access Token with read/write repository.
- **Generic:** use provider-specific token.

## SSH Setup

- Generate an ed25519 key in the profile screen.
- Copy the public key to your Git provider.
- Test connection to trust the host key (fingerprint displayed as SHA256).

## Mirror Strategy

- The internal Git working directory is canonical.
- The target folder is mirrored from the internal working tree after Git operations.
- Changes in the target folder are mirrored into the internal working tree before Git operations.
- Mirroring uses temp files with size/hash verification to avoid partial writes.

Trade-offs:
- Git operates on a real filesystem path for reliability.
- SAF remains the user-granted access boundary.

## Security Notes

- Credentials are stored in encrypted preferences backed by Android Keystore.
- SSH private keys are stored encrypted and written to temporary internal files only during Git operations.
- TLS verification is enforced; SSL verification cannot be disabled.
- Logs are plain text with secret redaction.

## Threat Model (Summary)

- **Threat:** credential leakage → **Mitigation:** encrypted storage, redacted logs.
- **Threat:** MITM on Git remote → **Mitigation:** TLS verification and SSH host key verification.
- **Threat:** accidental deletes → **Mitigation:** propagate deletes is opt-in per profile.

## Tests

Run unit tests:

```
./gradlew test
```

Run instrumentation tests:

```
./gradlew connectedAndroidTest
```

## Manual QA Checklist

- Create profile, pick target folder, set remote and branch.
- Clone remote and sync; verify files appear in target folder.
- Modify target folder; sync and confirm commit + push.
- Modify remote from another device; sync and verify updates.
- Confirm conflicts are reported and no auto-merge occurs.
- Verify logs redact secrets.

## Quick Settings Tile

Add the RepoSync tile from Quick Settings. If exactly one profile exists, the tile triggers a one-time sync. If multiple profiles exist, the app opens so you can pick a profile.

## License

See LICENSE and THIRD_PARTY_NOTICES.

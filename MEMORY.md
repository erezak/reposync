# Repository Memory

- 2026-01-17 — Decision: Use mirror-mode as the default sync architecture (internal Git working tree is canonical; SAF target is mirrored).
  - Rationale: Git requires filesystem paths and SAF is document-based, so mirroring keeps Git reliable while honoring SAF access.
  - Impact: All sync logic must treat the internal repo as canonical and always mirror to/from SAF.

- 2026-01-17 — Decision: Do not propagate deletes unless the user explicitly enables “propagate deletes” per profile.
  - Rationale: Conservative deletion reduces accidental data loss.
  - Impact: Sync engine must skip deletes by default and only apply them when the profile toggle is enabled.

- 2026-01-17 — Decision: Enforce normal TLS verification; do not allow disabling SSL/TLS validation.
  - Rationale: Prevents insecure connections and MITM risks.
  - Impact: Network stack must not expose any option to disable TLS verification.

- 2026-01-17 — Decision: Support both HTTPS token and SSH authentication; SSH uses host key verification with SHA256 fingerprints.
  - Rationale: Covers common Git workflows while keeping SSH verification user-trustable and modern.
  - Impact: Implement SSH known_hosts trust flow, store fingerprints, and never auto-accept host keys.

- 2026-01-17 — Decision: Conflict handling is guidance-only in v1 (no auto-merge; no in-app merge helper).
  - Rationale: Avoids unsafe automatic resolutions and reduces complexity in initial release.
  - Impact: UI must surface conflicts clearly and direct users to resolve via desktop Git tools.

- 2026-01-17 — Decision: Sync logs are plain text with redaction of secrets.
  - Rationale: Simple export and privacy-first handling.
  - Impact: Logging must redact tokens/private keys/credentialed URLs and export as text only.

- 2026-01-17 — Decision: Direct Git Mode is deferred and not implemented in v1.
  - Rationale: Prioritize stable mirror-mode behavior and SAF compliance.
  - Impact: No in-place Git operations; future work may add an explicit opt-in toggle.

- 2026-01-17 — Decision: Store SSH keys encrypted (PKCS#8 base64) and write to temporary internal files only for JGit operations.
  - Rationale: Keep keys at rest encrypted while still supporting JGit file-based key loading.
  - Impact: Never persist plaintext keys beyond temp working files; always delete temp SSH dirs after use.

- 2026-01-17 — Decision: Maintain a single internal known_hosts file and require explicit host trust (SHA256 fingerprint) before SSH operations.
  - Rationale: Enforces strict host key verification and avoids silent trust.
  - Impact: SSH sync must fail until a host key is trusted via the UI flow.

- 2026-01-17 — Decision: Apply profile ignore patterns to Git staging via .git/info/exclude.
  - Rationale: Keeps ignore rules effective without modifying user-controlled .gitignore.
  - Impact: Sync must update .git/info/exclude before staging changes.

- 2026-01-17 — Decision: Quick Settings tile triggers a one-time sync only when exactly one profile exists; otherwise it opens the app.
  - Rationale: Avoids ambiguous profile selection from a tile without UI.
  - Impact: Tile service must enumerate profiles and branch behavior accordingly.

- 2026-01-17 — Decision: Harden mirroring with temp writes and size/hash verification (hash fallback when mtime is missing).
  - Rationale: Reduce partial writes and improve change detection when SAF timestamps are unreliable.
  - Impact: Mirror engine must use temp files and verify writes; fingerprints include hashes when needed.

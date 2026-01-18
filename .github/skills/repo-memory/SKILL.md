---
name: repo-memory
description: Create and maintain repo-local MEMORY.md entries for durable architectural decisions. Use when making decisions that constrain future implementation (data model, boundaries, schema strategy, performance, security, dependency policy, tooling) or when the user explictly asks you to remember something.
license: See repository LICENSE.
metadata:
  version: "1.0"
---

# Repository memory (MEMORY.md)

## Purpose
Maintain a durable, repo-local memory for future coding sessions.
Only record important, lasting architectural decisions, constraints, and conventions.

Do NOT record:
- General knowledge
- Transient notes
- Speculative ideas
- Personal data
- Secrets
- Anything risky in a public repo

## Creation rule
If `MEMORY.md` does not exist at the repo root, create it automatically the first time you need to record an entry.

## When to write an entry
Write an entry when you make a decision that changes or constrains future implementation, such as:
- Data model and invariants (vault is canonical; DB is cache; link syntax)
- Module boundaries and repo structure
- Storage choices (SQLite schema approach, rebuild strategy)
- Performance strategy (indexing approach, workers, debouncing rules)
- Security and privacy constraints (no telemetry, offline-only)
- Dependency policy (why a dependency was added, what it replaces)
- Build and tooling conventions (pnpm-only, formatting/lint commands)

## When NOT to write an entry
Do not write entries for:
- Minor refactors
- Small bug fixes
- Routine implementation details
- Anything likely to change next week

## Format rules
- Keep entries short and factual.
- Use reverse chronological order (newest first).
- Each entry must include:
  - Date (YYYY-MM-DD)
  - Decision summary (one line)
  - Rationale (one short sentence)
  - Impact (one short sentence describing what future work should do differently)

## Template
- YYYY-MM-DD — Decision: <summary>
  - Rationale: <why>
  - Impact: <what to follow going forward>

## Examples
- 2026-01-13 — Decision: Use pnpm for all JS tooling and installs.
  - Rationale: Project standardization and faster installs.
  - Impact: Do not use npm or yarn; keep only `pnpm-lock.yaml` in the repo.

- 2026-01-13 — Decision: Markdown files are canonical; SQLite is a rebuildable index/cache.
  - Rationale: Local-first transparency and easy portability.
  - Impact: Any schema change must support rebuild; never treat DB as source of truth.

# DCAM Coding Conventions

## Logging

Operational events use `Logger`:

- `logger.debug(...)` for diagnostic detail.
- `logger.info(...)` for normal lifecycle and state events.
- `logger.warn(...)` for recoverable failures, with `Throwable` when available.
- `logger.error(...)` for failed operations and crash events, with `Throwable` when available.

Feature and application code that cannot depend on platform code must depend on `Logger`.
Do not call `android.util.Log`, `System.out`, `System.err`, or `printStackTrace()` outside
`platform.logging` infrastructure.

Process boundaries and file roles live in `ARCHITECTURE.md`. Agent-enforced repository rules
live in the root `AGENTS.md`.

Logs must remain useful offline, exclude passwords, tokens, secrets, and sensitive payloads,
and never block capture or storage when remote delivery is unavailable.
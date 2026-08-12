# Project Editing Rules

## UTF-8 Text Editing

- Write Vietnamese and other human-readable non-ASCII text as literal UTF-8. Numeric XML/HTML entities are forbidden unless an external protocol or generated output requires them.
- Before exchanging non-ASCII text with native programs, configure `[Console]::InputEncoding`, `[Console]::OutputEncoding`, and `$OutputEncoding` to UTF-8 without BOM, then run `chcp.com 65001 > $null`.
- Use `apply_patch` only when exposed as a first-class tool. Shell commands such as `apply_patch.bat` or `apply_patch.cmd` are wrappers, not the native tool.
- If native `apply_patch` is absent or one transport attempt fails, switch immediately to strict UTF-8 .NET editing. Do not inspect or retry wrappers, alternate shells, quoting, stdin, temporary files, or argument transport.
- Before editing, read with strict `[System.IO.File]::ReadAllText`, detect CRLF/LF/mixed endings, and assert each target match count. Write with `[System.IO.File]::WriteAllText` using UTF-8 without BOM.
- Preserve untouched line endings exactly. For mixed files, use adjacent line endings. Never normalize a whole file unless requested.
- Never use `Set-Content`, `Out-File`, `Add-Content`, `>`, or `>>` for repository text files.
- After non-ASCII edits, run path-scoped `git diff --check -- <paths>` and inspect touched hunks only. Scan for `�`, `(?:Ã|Â|Ä|Æ)[^\x00-\x7F]|â€|ðŸ`, and newly introduced numeric entities; standalone Vietnamese letters such as `Â` are not corruption. Ignore pre-existing matches.
- If current edits alter line endings or introduce corruption, revert only those edits and reapply safely.

## Evidence-First Debugging

- When cause is unclear, do not claim root cause or change behavior from assumptions.
- Add focused diagnostics, reproduce, and collect evidence before fixing behavior.
- Rerun reproduction or tests after the fix. Remove temporary diagnostics unless operationally useful.

## Behavior Contracts

- Preserve confirmed business and user-visible behavior, phase boundaries, fallback order, defaults, and lifecycle semantics unless the user explicitly requests a change.
- If requirements, confirmed tests, source, or runtime evidence conflict, stop and ask before editing affected behavior. Do not infer UI behavior from backend rejection, architecture, screenshots, conventions, or review preferences.
- Direct user instructions and confirmed tests apply only to the exact behavior they cover. Never weaken contract tests to make changed behavior pass; add a focused regression test for confirmed fixes.
- Keep workflow phases bound to their confirmed inputs and decision rules. Do not mix state across phases unless the contract allows it.
- Create `BLOCKER.md` only when an unresolved behavior question blocks progress across turns; remove it after resolution unless the user asks to keep it.

## Durable Task Journal

- For every non-trivial task, create one task journal under `docs/local-dev/plans/` before substantive inspection, debugging, design, editing, or state-changing commands. Name it `YYYY-MM-DD-<task-slug>.md`. A more deeply scoped `AGENTS.md` may specify another development-document location.
- Skip the journal only for a small, obvious, single-step change with no behavior ambiguity, no debugging, no multi-file work, and no meaningful validation beyond a focused diff.
- Treat the journal as durable working memory, not authority to change behavior and not a parallel product backlog. It must not override user instructions, confirmed tests, `Behavior Contracts`, or canonical project plans.
- Start the journal with: status, objective, contract (`Must` and `Must not`), evidence (`Facts` and `Hypotheses`), phased plan with per-phase status, considered solutions, decisions with rationale, changes made, validation results, and either active remaining risks or next step, or completion notes once complete.
- Use at least these phases: inspect, consider solutions, implement, and confirm. Add reproduce, diagnose, review, rollback, migration, or device-validation phases when the task needs them.
- Update the journal before starting each phase and after completing it. Record the result of every phase. Also update it after new evidence, a user correction, a plan change, a decision, an edit batch, a validation result, an operationally relevant failed attempt, or a rollback.
- Record failed attempts only when they changed repository or runtime state, affected validation evidence, exposed a task-relevant constraint, or influenced a decision or next step. Do not record harmless command syntax, quoting, text-matching, or journal-edit failures that made no changes and provide no durable task evidence.
- Keep enough exact paths, commands, evidence, decisions, and results that work can resume from the journal alone after context loss. Separate facts from hypotheses and mark invalidated conclusions.
- When a user correction invalidates a premise, record the correction, list premise-derived changes, revert only those changes, and update the plan before continuing.
- At completion, mark the journal complete only after required validation passes. Record exact checks and results, genuine unresolved risks, and intentionally skipped work. If any required next step remains, status must not be complete. When none remains, replace `Remaining Risks / Next Step` with `Completion Notes`, state `Required work: None`, and remove stale, speculative, or optional follow-up items. If blocked, record the blocker in the journal; create `BLOCKER.md` only under the rule in `Behavior Contracts`.
- Maintain one journal per task. Link canonical plans instead of copying their backlog, and do not create parallel action checklists.

## Logging
- Application and feature code must use the project logger at `core/.../logging/application/port/Logger`.
- Do not use console printing, `System.out`, `System.err`, `printStackTrace`, direct Android `Log`, or another logger outside `platform/logging` infrastructure.
- Every log entry must describe a meaningful event and remain understandable without reading the source code. State the subject, action, and outcome in plain language, then add only useful diagnostic context.
- Do not emit opaque event labels followed by raw key-value dumps, for example: `camera_fast_probe cameraId=1 pipeline=a-camera2-native-surface-sharing-v1 codec=h264 stage=complete result=complete apiLevel=31 maxSharedSurfaceCount=4 vfProfileCount=4 imageProfileCount=4 tupleProfileCount=16 attemptCount=16 elapsedMs=758 detail=matrix_complete`.
- Prefer self-explanatory messages, for example: `Verify camera 0 capture profile success: FHD:1920x1088@30 + MAX:2320x1740. Elapsed: 592 ms. Remaining: 4407 ms. Pipeline: camera2-native-surface-sharing-v1. Codec: h264.`
- Do not repeat facts already stated by the message. `Verify camera 0 capture profile success` makes `stage=complete result=complete` redundant. Add technical fields only when they provide new diagnostic value.

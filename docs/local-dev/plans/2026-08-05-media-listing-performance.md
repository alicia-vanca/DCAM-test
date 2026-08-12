# Media Listing Performance

- Status: Complete
- Objective: Find and fix excessive delay while listing media files and counts.

## Contract

### Must
- Preserve confirmed media listing contents, ordering, counts, filters, and lifecycle behavior.
- Collect evidence before changing performance-sensitive behavior.
- Keep fix focused and validate affected flow.

### Must not
- Guess root cause from architecture alone.
- Change user-visible listing semantics or weaken tests.
- Add dependencies unless existing platform and project code cannot solve issue.

## Evidence

### Facts
- User reports media file listing/count takes too long.
- User correction: async rendering removes UI blocking but exact count wait remains noticeable; time-to-first-row alone is insufficient.
- User correction: warm cache still flashes `Audio` / `Image` / `IMP` / `Video` without counts before a second state update.
- User correction: folder counts are now fast, but opening a folder with actual media files still leaves `Loading` visible too long.
- Exact device timing and dataset size are not yet known.
- Prior fix remains valid for responsiveness but does not reduce first recursive count cost enough.
- `LocalMediaRepository.list("Internal")` recursively counts every file under all managed media folders before returning rows.
- Listing a media directory sorts raw `File` objects with `File.isFile()` inside the comparator, causing metadata calls across O(n log n) comparisons.
- Recursive counting checks symbolic-link, directory, and file state separately, causing multiple metadata calls per descendant.
- Media work already runs off the main thread, so delay is repository I/O rather than direct UI-thread blocking.

### Hypotheses
- Repeated storage scans or duplicate count/list queries may dominate latency.
- Per-file metadata work may run serially or on UI-critical path.
- Query scope, sorting, or cache invalidation may force unnecessary full enumeration.

## Phased Plan

- Inspect: Complete. Production media uses type/date/file layout; tests also require exact nested recursion. Root listing provides valid storage paths for opportunistic prewarm.
  - Actual-file listing performs one `BasicFileAttributes` read per child, then `MediaBrowserRenderer` clears and inflates every row into a `ScrollView` / `LinearLayout` before Android can draw the completed state.
- Reproduce: Complete. Baseline `LocalMediaRepositoryTest` passed; code inspection confirms recursive counts block each result and sorting repeats filesystem metadata calls.
- Consider solutions: Complete. Generalize session cache from leaves to validated directory snapshots so aggregate counts can appear in the first warm listing.
  - Actual-file follow-up: use native `ListView` recycling so only visible rows inflate. Defer staged metadata because file classification and exact size currently share one attribute read, and weakening that contract is unsupported.
- Implement: Complete. Replaced eager `ScrollView` row inflation with native `ListView` recycling; preserved row content, ordering, clicks, and spacing.
- Confirm: Complete. Production build, focused renderer regression, full unit suite, and final byte/diff audits passed.
  - Focused repository and ViewModel tests passed: warm aggregate counts are present in the first listing, exact second query is skipped, and nested changes invalidate parent counts.
  - Full suite passed 850 tests with zero failures and zero errors.
  - Final diff/corruption checks passed. One read-only encoding report had a PowerShell pipeline parse error before execution; no files changed. Rerun with collected results.
  - First two focused cache runs exposed nullable conditional unboxing; explicit branching fixed it.
  - Final focused repository and ViewModel media tests passed.
  - Earlier full run had two isolated failures in active camera/fragmented-MP4 work; final full run passed all 848 tests.
  - Two strict .NET/read-only PowerShell commands failed before write; recorded and retried safely with no corruption.

## Considered Solutions

- Remove counts: rejected because current tests and UI expose exact descendant counts.
- Persist an index: deferred because it adds migration and invalidation complexity.
- Snapshot entry metadata once: selected; preserves visible metadata while removing comparator-driven filesystem calls.
- Progressive exact counts: selected; rows appear before recursive traversal completes.
- Cancel stale count work: selected; navigation must not wait behind obsolete scans.
- `Files.walkFileTree`: not needed; focused attribute reads keep current recursion and failure behavior closer.

## Decisions

- Use evidence-first tracing before behavior changes.
- Retain progressive rendering and stale-request cancellation; extend the same executor with best-effort root prewarm.
- Keep count reuse inside `LocalMediaRepository`; expose only the existing count-free repository path needed by the UI flow.
- Previous leaf-only cache caused the visible split-second aggregate-count flash. Replace it with dependency-validated directory snapshots.
- Native `ListView` selected over a new dependency: platform recycling removes O(n) main-thread row inflation while keeping repository and entry semantics unchanged.
- Replace the initial device-only renderer regression with a focused JVM structural check so this slice remains runnable despite unrelated broken instrumentation sources.
- Failed journal replacement attempt matched stale wording and wrote nothing; reopened from current journal text.
- Failed read-only inspection command for `DcamFileName` had an unterminated PowerShell quote; command did not execute and changed no files.
- Failed journal edit used PowerShell string splitting for an exact-match assertion; assertion failed before write and changed no files.
- Two read-only inspection batches referenced stale paths for `DcamAudioFileName` and `MediaEntry`; valid files still read, missing-path errors changed no files.
- Failed focused-validation journal update used an unsupported inline PowerShell `if` expression; command failed before write and changed no files.
- Retry used a stale journal anchor; assertion failed before write and changed no files.
- Parallel validation and journal edits raced once, dropping two newly added validation lines; restored them from command output and stopped parallel journal writes.
- Final byte audit found `MediaBrowserRenderer.java` lacked a trailing newline; audit stopped before remaining paths, then the file was corrected with its existing CRLF style.
- Re-run found the new `MediaBrowserRendererTest.java` also lacked a trailing newline; corrected it with LF matching adjacent unit tests.
- Complete audit then showed the previous append created one CRLF inside the otherwise LF test and the journal lacked its final LF; normalized the new test to adjacent CRLF style and restored the journal final LF.

## Changes Made

- Created this task journal.
- Added count-free media listing through `MediaRepository` and `BrowseMediaUseCase`.
- Updated `LocalMediaRepository` to snapshot visible metadata once, defer recursive counts, and stop interrupted scans.
- Updated `MainViewModel` to render fast rows, refresh exact counts, and cancel obsolete work.
- Hid folder count UI until exact count is available.
- Added repository tests for unknown versus exact counts.
- Added ViewModel tests for progressive listing and obsolete scan cancellation.
- Added exact session cache for directory counts, invalidated by directory identity, size, modification time, and child snapshot validity.
- Count-free listings now reuse valid cached subtree counts, including `Audio` / `Image` / `IMP` / `Video` aggregates.
- Cached directory snapshots now store direct-file counts and child-directory dependencies; warm aggregate validation uses directory metadata only and does not enumerate media files.
- Root media screen now prewarms storage counts on the existing cancelable media executor.
- Added tests for cache reuse, nested invalidation, warm aggregate counts, and root prewarm.
- Added ViewModel regression proving warm folder counts skip the exact second query and therefore avoid the count flash.
- Replaced media explorer `ScrollView` / `LinearLayout` with native `ListView` and a reusable `BaseAdapter`; render now updates data in O(n) references but inflates only visible rows.
- Added focused JVM `MediaBrowserRendererTest`: generated binding must expose `ListView`, and renderer adapter must extend `BaseAdapter`.

## Validation Results

- Baseline: `LocalMediaRepositoryTest` passed before edits.
- Focused final: `:app:testDebugUnitTest --tests com.dvid.dcam.platform.storage.LocalMediaRepositoryTest --tests com.dvid.dcam.app.ui.MainViewModelMediaListingTest` passed.
- Full suite: 850 tests ran; 850 passed with zero failures and zero errors.
- `git diff --check` passed for tracked touched files and no-index checks passed for new test/journal.
- Strict UTF-8 reads, BOM checks, line-ending checks, and corruption/entity scans passed.
- Actual-file follow-up production Java compiled successfully.
- Focused `MediaBrowserRendererTest` passed.
- Actual-file follow-up full suite: 856 tests passed with zero failures, errors, or skips.

- Full Android-test source compilation is blocked by 11 unrelated errors in existing camera device tests (`SosHandoff`, changed factory signatures, and changed preview/event interfaces); changed production files compiled before that failure.
- Device `BODYCAMERA4HHITK` is connected, but instrumentation remains blocked by unrelated existing Android-test compile errors; completed production/UI slice does not depend on those tests.

## Completion Notes

- Required work: None. Task complete.
- Result: actual-file screens keep exact off-main-thread enumeration but inflate only visible rows, so `Loading` can clear without building every off-screen row.
- Intentionally skipped: staged metadata, persistent indexing, and file-entry caching because no measured repository bottleneck requires their invalidation complexity.
- No required next step remains for this task.

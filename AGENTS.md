# Project Editing Rules

## Vietnamese UTF-8

- Write Vietnamese and other human-readable non-ASCII text as literal UTF-8 characters.
- Do not write XML/HTML numeric entities such as `B&#7897; nh&#7899;` or `&#x1ED9;` in source files.
- Preserve XML/HTML entities only when external protocol or generated output requires them.
- After non-ASCII edits, run `git diff --check` and scan changed files for `�|Ã|Â|â€|ðŸ` and numeric entities.
## Safe Text Editing Workflow

1. Before commands that exchange Vietnamese or other non-ASCII text, configure each PowerShell process:

   ```powershell
   $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
   [Console]::InputEncoding = $utf8NoBom
   [Console]::OutputEncoding = $utf8NoBom
   $OutputEncoding = $utf8NoBom
   chcp.com 65001 > $null
   ```

2. Use the native `apply_patch` tool directly when it is exposed as a tool. Do not invoke an `apply_patch` wrapper through PowerShell, `cmd.exe`, batch files, pipelines, here-strings, shell interpolation, or dynamically built multiline arguments.
3. If native `apply_patch` is unavailable, or the first patch transport attempt fails, do not retry through another shell or quoting form. Use the strict UTF-8 .NET fallback immediately.
4. Do not use `Set-Content`, `Out-File`, `Add-Content`, `>`, or `>>` for repository text files.
5. For the fallback, read and write with strict UTF-8 through .NET:

   ```powershell
   $strictUtf8 = [System.Text.UTF8Encoding]::new($false, $true)
   $text = [System.IO.File]::ReadAllText($path, $strictUtf8)
   [System.IO.File]::WriteAllText($path, $text, [System.Text.UTF8Encoding]::new($false))
   ```

6. After editing, run `git diff --check`, inspect `git diff --word-diff`, and scan changed files for mojibake and numeric entities.
7. If corruption appears, stop. Restore only the damaged lines from Git when safe, then reapply the intended change with the native `apply_patch` tool or strict UTF-8 fallback.
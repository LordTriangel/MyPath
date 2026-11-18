# C8 – App update


Part of the core features (`docs/core-features.md`): every core feature has JVM regression tests; a field bug first gets a failing test, then the fix.


| # | Core feature | Field bugs guarded against | Regression tests |
| --- | --- | --- | --- |
| C8 | App update from GitHub (Settings → Updates) – resumes after a broken connection | download failed with repeated "connection abort" | `UpdateDownloadTest`, `AppUpdaterTest` |

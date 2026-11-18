# C11 – Backup, restore, recycle bin


Part of the core features (`docs/core-features.md`): every core feature has JVM regression tests; a field bug first gets a failing test, then the fix.


| # | Core feature | Field bugs guarded against | Regression tests |
| --- | --- | --- | --- |
| C11 | **Tours & plans are never lost: backup, restore, recycle bin** – a GPX per finished tour / saved plan / import in Documents/OnPath (or a chosen folder), restore after a reinstall / on a new phone, delete = 30-day recycle bin, Export all (ZIP), Android backup of the DB; map updates / Clean up / Clear all never touch tours | – (owner decision); field bug 2026-11-08 (S10 right after a restart: "Volume external_primary not found" → red box at start) | `BackupTest`, `BackupNotReadyTest`, `MapsNeverTouchToursTest`, screenshots `recycleBin*`, `settingsBackup`, `restoreOffer`; emulator `CoreBackupE2ETest` |

C11 details (owner, `backup/`): file `YYYY-MM-DD Name.gpx` (sanitised, unique; rename / re-save overwrite the
same file via `files/backup-index.json`), GPX 1.1 with `<metadata><extensions><onpath:backup>` = JSON (uuid, type
tour|plan|imported, name, activity, SAC, dates, stats, stops with names, pinned POIs, deleted, `favourite` (true only, the ★ of My routes – owner 2026-11-13; a restore puts it back, the recycle bin keeps it); a recorded tour's followed plan as `plan` – only in the record, never a second `<trk>` / `<rte>`: GPX readers, OnPath's restore too, would take it for part of the tour). Written on one background
thread (`Backups`) after: tour end (RecordingService.finish), "Save" of an unfinished tour, plan save / overwrite,
rename (My routes or Save's dialog; also the automatic place name), GPX import, delete → `Deleted/`, restore from the bin; at app start the bin is
emptied of entries older than 30 days (files too) and tours without a file (or not yet moved to `Deleted/`) are written (e.g. a new folder). Failures → Problems WARN + message "Backup to … failed – reason" until OK (kept across restarts). Storage not ready yet ("Volume … not found", just after a restart; `BackupVolumes`) is no failure: the tour stays queued (prefs, across restarts), retried after 30 s / 2 min / 10 min / 30 min, at the next backup and the next start, only an INFO; the message comes only when still not ready after 10 min and on a second occasion (start / backup) – "Back up all now" and real failures (full, no access) at once. MediaStore volume: `external_primary` when listed, else `external`. No app-private fallback file: the tour itself stays in the DB.
Android 10+: MediaStore (Files, RELATIVE_PATH Documents/OnPath, no permission); Android 8–9: the folder with
WRITE_EXTERNAL_STORAGE (Settings → Backup → Allow); chosen folder: SAF tree with persistable permission. Restore:
folder picker (opens at Documents/OnPath), imports only files with the OnPath record, newest per uuid, skips uuids on
the phone and the bin. First start with no tours asks once (prefs `backup` → `restoreOffered`). Android Auto Backup:
`res/xml/backup_rules.xml` + `data_extraction_rules.xml` – tours DB, settings, tour/plan times only.

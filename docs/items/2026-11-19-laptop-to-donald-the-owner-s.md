# → [donald]

- → [donald] (laptop 2026-11-19 night, from the 7-63-42 fix): the owner's 15 log lines came from the app asking again on every trigger – on `failed` `MapCells.apply` drops the package from the queue and the next trigger asks the server again (harmless for the server: it records, rebuilds only on a new BUILD_REV). Suggestion: remember a failed package for the session (or until the app updates) and log it once. Your call for the release. Delete the tag when read.

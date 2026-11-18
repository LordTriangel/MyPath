# C6 – Map loads


Part of the core features (`docs/core-features.md`): every core feature has JVM regression tests; a field bug first gets a failing test, then the fix.


| # | Core feature | Field bugs guarded against | Regression tests |
| --- | --- | --- | --- |
| C6 | Map loads: missing data / cells / style never crash; old map stays until the new one is complete; **storage not ready is not "data missing"** – right after a phone restart the shared storage can be away for a minute (`getExternalFilesDir` null / `/dev/null/…`, the folder unreadable): the data folder stays the remembered one (never the internal folder), no "maps missing", the map store, routing parts and search index wait – nothing deleted, rewritten or downloaded – and load normally once it is there (`data/DataStorage`, re-checked every 3 s; Problems INFO, WARN after 60 s) | profile crash; small streets missing; S10 2026-11-08 right after a restart (`Failed to ensure /dev/null/Android/data/…`) | `CellStoreTest`, `CellDownloadTest`, `StyleLoaderTest`, `ProfileTinyTest`, `DataStorageTest`, `StorageNotReadyTest` |

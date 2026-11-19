# Rebuild phase 3: core off the JVM ([umbau], 2026-11-19)

> **Decisions (2026-11-19).** The owner chose the **small version** („Small version“, asked in [umbau]'s chat): rules, routes, plans, tours and texts become platform-free; `cells/`, net, backup and the rd5 files stay JVM-only for now (the minimal phase 3 of this document, ≈ 3 nights). Module path: staged, as recommended here. Still to decide before the pieces that need it start: texts (T0 recommended), JSON (own port of Android's `org.json` needs a licence notice = pull request; or kotlinx.serialization-json), kotlinx-datetime as a new dependency, the emulator run for the format fixtures (F1), 5b as its own `:engine` module. This document is a helper's draft reviewed only in outline by the lead – the numbers come with the commands that produced them; §6 lists what is not verified.

Working paper of phase 3 of `architecture-plan-2026-11.md` §5. Design only – no app code changed. Measured on main
@ c11e4b071 by script, by three throw-away compiler probes in `/tmp` (not in the repo, §7) and by reading. The per-file
table (583 rows: every Kotlin file of `core/` and `state/`, 25 categories, the native compiler's error count) is
**`umbau-phase-3-files.tsv`**. Deleted when phase 3 is tagged (`umbau-phase-3`). "Not verified" marks what I assumed
(§6 lists all of it). Decisions are recommendations – the lead decides.

## 0. In one table

| Question | Answer |
| --- | --- |
| How far is `:core` from a non-JVM build? | Compiled for `linuxX64` as it is: **2,856 errors in 113 of 264 files**; 109 more files only fail because they use one of the 113; **42 files compile today**. `:state`: 136 errors in 10 of 59 files |
| Where is the work? | 15 files hold 67 % of the errors (`CellStore` 483, `MapCells` 205, `PmTiles` 152, `NavSession` 141, `BackupCodec` 117 …); `cells/` alone 39 %. 52 files have ≤ 5 errors. Three hubs block almost everything: `debug/Problems` + `EventLog` + `data/AtomicFiles` (172 files depend on them), `routing/RoutingContract` (158), `data/Geo` (122) – the hub files themselves are small work (`Geo` 12 errors, `RoutingContract` 5; `Problems` 61, `EventLog` 87, `AtomicFiles` 82), so they go first (§4) |
| Module path | **Staged.** A: make the code common-clean in place (module stays JVM), guarded by a real compiler check `tools/common-check` (linuxX64) with a shrinking list `core/jvm-only.txt`. B: flip `:core` / `:state` to Kotlin Multiplatform at the end. **Sources stay in `core/src/main/kotlin`** (declared as `commonMain`'s folder) – no path changes for the planner |
| The surprise | **`:strings` cannot get a `linuxX64` target**: Compose `components-resources:1.7.3` publishes android, desktop, iOS, macOS, js, wasm – no Linux (checked in the Gradle cache). 47 core files use the texts and ≈ 181 of 264 depend on them. Stage A is not affected (the check build uses a generated texts stand-in, as the planner does); the flip (B) needs a decision (§3.3) |
| Libraries | **JSON: own Kotlin port of Android's `org.json`** (not kotlinx.serialization) · **bytes / gzip: okio** · **files: own `Files` port**, on Android today's `java.io` code behind it · **dates: kotlinx-datetime for instant + zone, all text by own code** · **locks: atomicfu `SynchronizedObject`** · **numbers → text: own `Fmt`** · **SHA-256: `Hasher` port** (§2) |
| TeaVM (planner) | Probed with TeaVM 0.16 wasm-gc: kotlinx.serialization-json tree API, okio `Buffer` / gzip, kotlinx-datetime `Instant` **compile**; okio `sha256()` and `okio.FileSystem` **do not** (§2.3). No `expect` in common code – the planner compiles the same folder as plain Kotlin/JVM |
| Stored formats | 40 found (§2.2). 12 have a frozen-bytes test today; **the 14 `org.json` formats have none and cannot get one on the JVM today**: the tests run JSON-java, the phone runs Android's `org.json` – two libraries |
| Pieces | 7 tool / fixture pieces (F0–F6) + the ports (P1) + 23 conversion + 5 flip = **36**, §4. Nights: **6–7** with two to three lanes (the plan said 3–4) |
| Minimal phase 3 | foundation + hubs + everything outside `cells/`, `net`, GPX parse, `NetReader`: ≈ 3 nights, 0 stored-format risk in `cells/`; done-rule by the check build (§5.3) |
| Does 5b need it? | Not strictly: the ported engine is self-contained (spike: 74 files + a 62-line platform edge). Recommendation: 5b builds its own multiplatform module `:engine`; it needs phase 3 only for the `Files` port and the `Geo` / `RoutingContract` hubs (pieces H1, F2) |

## 1. Inventory

### 1.1 Method
1. `inventory.py` (appendix A): regex per category over every `.kt` file, comment lines skipped, import lines not
   counted as uses. Good for "where", not exact (a regex cannot see `Map.forEach { k, v -> }` or `toSortedMap()`).
2. **The compiler** (appendix B): a throw-away Kotlin Multiplatform build with only `linuxX64`, source folder =
   `core/src/main/kotlin` (+ `state/src/main/kotlin` in a second run), coroutines 1.9.0, Compose runtime 1.7.3, and a
   generated stand-in for the texts (`Res.string.*`, `Txt`). Kotlin 2.2.10, Kotlin/Native from `~/.konan`. One run =
   7–14 s (front end only – it stops at the errors). Then repeated, excluding every file with an error, until it
   compiles: 5 rounds → the files that are common-clean **today**.
   Errors cascade (one unresolved type gives several messages), so "errors" overstates the edits; "files" is exact.

### 1.2 Size
| Source set | Files | Lines | `@Test` |
| --- | ---: | ---: | ---: |
| `core/src/main/kotlin` | 264 | 34,910 | |
| `core/src/test/kotlin` | 199 | 24,989 | 1,748 |
| `core/src/testkit/kotlin` (shared with `:app`, `:state`) | 16 | 1,378 | |
| `state/src/main/kotlin` | 59 | 8,437 | |
| `state/src/test/kotlin` | 45 | 18,281 | 843 |

### 1.3 What the native compiler says (`core` main)
| Fact | Number |
| --- | ---: |
| Errors | 2,856, of them 1,966 "unresolved reference" |
| Files with own errors | 113 (≤ 5 errors: 52 · 6–20: 31 · 21–60: 15 · > 60: 15) |
| Files that only fail through others | 109 (round 2: 95, round 3: 13, round 4: 1) |
| Files that compile today | 42 |
| Top unresolved names | `File` 220, `java.` 195, `JSONObject` 100, `Math` 96, `put` 78, `synchronized` 70, `JSONArray` 48, `IOException` 47, `System` 30, `Charsets` 28, `Synchronized` 24, `Volatile` 24, `format` 22, `FileOutputStream` 15, `btools` 12, `HttpURLConnection` 11, `Locale` 9, `StackTraceElement` 9 |
| Hubs (name-based graph, files that depend on it directly or through others) | `Problems` / `EventLog` / `AtomicFiles` 172 · `RoutingContract` / `Junctions` 158 · `Geo` 122 · `Format` 70 · `Rides` 63 · `PaceModel` 60 · `CellMath` 44 |

| Package | Files | Compile today | Own errors (files) | Errors | Files with texts | Biggest files (errors) |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| `cells` | 13 | 1 | 10 | **1,110** | 3 | `CellStore` 483, `MapCells` 205, `PmTiles` 152, `PoiTiles` 94, `CellApi` 90, `GeoJsonMerge` 64, `CellManifest` 18 |
| `data` | 19 | 4 | 11 | 359 | 5 | `AtomicFiles` 82, `MultiDay` 71, `TourPlan` 49, `DataImporter` 36, `TourJson` 30, `LegFiles` 30, `FollowFile` 27, `Geo` 12 |
| `debug` | 8 | 0 | 6 | 187 | 0 | `EventLog` 87, `Problems` 61, `MainStall` 20, `MapJank` 12 |
| `routing` | 32 | 1 | 13 | 184 | 10 | `Rd5Parts` 89, `TrackToPlan` 25, `RouterClassHash` 14, `LegCache` 14, `PlanCode` 12, `Planner` 9, `RoutingContract` 5 |
| `navigation` | 26 | 2 | 10 | 175 | 4 | `NavSession` 141, `JogRule` 10, `VoiceGuide` 7, `FollowStore` 6, `NavText` 5 |
| `suggest` | 26 | 4 | 19 | 173 | 2 | `NetReader` 103 (BRouter classes), `SuggestGeo` 12, `SuggestService` 10, 16 files ≤ 7 |
| `backup` | 4 | 0 | 3 | 144 | 1 | `BackupCodec` 117, `TourBackup` 19, `BackupQueue` 8 |
| `time` | 10 | 0 | 6 | 142 | 3 | `BreakDetector` 41, `TripPace` 32, `PlanTimesStore` 31, `TourTimesStore` 26, `TimeEstimate` 9 |
| `plansync` | 13 | 1 | 5 | 103 | 0 | `HttpMailbox` 53, `SyncFiles` 34, `SyncRouting` 11 |
| `net` | 1 | 0 | 1 | 71 | 0 | `HttpDownload` 71 |
| `search` | 12 | 0 | 6 | 63 | 3 | `BaseMapPlaces` 32, `SharedPlace` 14, `SharedPlaceText` 6, `SearchText` 6 |
| `gpx` | 5 | 0 | 3 | 48 | 3 | `GpxParser` 36, `GpxWriter` 10 |
| `rides` | 4 | 0 | 3 | 39 | 2 | `Rides` 24, `RideView` 8, `RideApply` 7 |
| `domain` | 49 | 12 | 11 | 31 | 7 | `WaitProgress` 9, `MapReload` 7, `RoutesTab` 4 |
| `recording` | 23 | 10 | 1 | 18 | 1 | `LatestFileWriter` 18 |
| `sync` | 9 | 3 | 2 | 4 | 0 | `SyncItem` 2, `SaveRule` 2 |
| `setup`, `poi`, `map` | 8 | 2 | 3 | 5 | 2 | `VoiceSetup` 3 |
| `ui`, `update` | 2 | 2 | 0 | 0 | 1 | – |
| **`state` (all `ui/` + `debug/`)** | 59 | – | 10 | 136 | 18 | `MultiDayFlow` 83, `PlanRouteSlice` 30, `SettingsSlice` 7, `RoutesState` 4 |

Clean already (no own error): `ui`, `update`. Nearly clean (≤ 5 errors in the whole package): `sync` (own SHA-256,
AES-GCM, Base64 – no `java.security`), `poi`, `map`, `setup`. `recording` has one JVM file. `domain` has 31 errors in
49 files.

### 1.4 JVM-only use by category (`core` main; regex counts; `state` main in brackets)
| Category | Files | Uses | Top files | Note |
| --- | ---: | ---: | --- | --- |
| `java.io` (File, streams, `RandomAccessFile`, `IOException`) | 33 (2) | 339 (3) | `CellStore` 104, `PmTiles` 36, `Rd5Parts` 23, `AtomicFiles` 23, `MapCells` 19, `SyncFiles` 17, `EventLog` 17 | `File` is in 78 public signatures of 24 files; 9 classes take a `File` in the constructor. `:app` passes them (32 main files, 94 test files import `java.io.File`) |
| `kotlin.io` file extensions (`readText`, `listFiles`, `deleteRecursively` …) | 26 | 180 | `CellStore` 60, `MapCells` 17, `LegFiles` 11 | JVM-only although they look like Kotlin |
| `java.nio` | 4 | 18 | `AtomicFiles` 6 (atomic move), `Rd5Parts` 5, `PmTiles` 5 (`ByteBuffer`, both byte orders) | |
| `java.time` | 5 | 18 | `BackupCodec` 6, `RideView` 4, `GpxParser` 4, `GpxWriter` 2, `TourBackup` 2 | |
| `java.text` | 6 | 8 | `SimpleDateFormat` 4 (`EventLog`, `Problems`, `BreakDetector`, `RideApply`), `Normalizer` 2 (`SearchText`, `RoutesTab`) | no `DecimalFormat`, no `Collator` |
| `Locale` / `Date` / `Calendar` | 18 (2) | 31 (2) | `TimeEstimate`, `BreakDetector`, `Format` 3 each | |
| zip / gzip / CRC | 5 | 7 | `PmTiles` (GZIP in + out), `SuggestFile` (`Inflater`), `BaseMapPlaces` (GZIP in), `DataImporter` (`ZipInputStream`), `Rd5Parts` (`CRC32`) | GPX export uses none |
| `java.util.concurrent` (+ atomic) | 9 (3) | 25 (7) | `EventLog` 8, `CellStore` 5, `LatestFileWriter` 4 | `ConcurrentHashMap` 8 uses / 6 files, `Atomic*` 7 / 3, `Executors` 2 (`Problems`, `EventLog`) + 4 in `state/PlanRouteSlice` |
| threads and locks | 24 (2) | 129 (3) | `MapCells` 55, `Problems` 9, `BackupQueue` 8, `LegCache` 7, `CellStore` 7 | `synchronized(` 70 in 10 files, `@Synchronized` 24 in 9, `@Volatile` 24 in 9, `Thread` 2, `Runnable` 8 (`BackupQueue`), `ThreadLocal` 1 (`RouterRules`) |
| `@JvmStatic` / `@JvmField` / `@Throws` | 2 | 5 | `AtomicFiles` 4 | available in common code – nothing to do (the compiler did not complain) |
| other `java.util` (`Arrays`, `Collections`, `TreeMap`, `IdentityHashMap`) | 8 | 10 | `PmTiles`, `MapCells` 2 each | no `UUID`, `Base64`, `java.util.Random`, `Pattern` (0) |
| `java.security` | 3 | 5 | `CellStore` 3, `RouterClassHash`, `HttpDownload` – all SHA-256 | no `javax.crypto` in main (own AES-GCM) |
| `java.net` | 5 | 25 | `CellApi` 8, `HttpDownload` 6, `SharedPlace` 4, `SharedPlaceText` 4 (`URI`, `URLDecoder`), `HttpMailbox` 3 | |
| `org.json` | 19 (1) | 148 (22) | `NavSession` 29, `CellStore` 17, `BreakDetector` 14, `Problems` 11, `BackupCodec` 10; state: `MultiDayFlow` | compile-only JSON-java jar; on the phone Android's own |
| gson | 2 | 9 | `GeoJsonMerge` 6, `PoiTiles` 3 | its text output is **hashed** (§2.2 row 16) |
| XML (SAX) | 1 | 4 | `GpxParser` | |
| BRouter classes (`btools.*`) | 1 | 22 | `NetReader` | waits for 5b |
| `String.format` / `"…".format` | 13 (2) | 22 (2) | `Format` 3, `CellStore` 3 | 24 calls: `Locale.ROOT` 12, `Locale.US` 2, `Format.locale()` 2, **no locale 8** (§2.1) |
| `System.*` | 13 (2) | 25 (5) | `EventLog` 5, `NetReader` 3, `CellStore` 3 | `currentTimeMillis`, `nanoTime`, `arraycopy`, `getProperty` |
| `java.lang` helpers | 55 | 160 | `PoiTiles` 19, `PmTiles` 12, `Geo` 11, `MainStall` 9 | `Math.*` 96 (`round` 34, `toRadians` 24, `abs` 7, `floor` 6, `toDegrees` 5, `floorMod` 5, `floorDiv` 5, `signum` 4), `Charsets` 28 (UTF_8 23, US_ASCII 3, ISO_8859_1 2), `toByteArray()` 16, raw-bits calls 4, `StackTraceElement` (`MainStall`) |
| reflection | 0 | 0 | | 3 `javaClass` (log text only) |
| `Dispatchers.IO` | 1 (1) | 1 (4) | `SharedPlaceResolve`; `PlanRouteSlice` | coroutines themselves are multiplatform |

### 1.5 Tests and test kit
| | `core/src/test` (199 files) | `core/src/testkit` (16) | `state/src/test` (45) |
| --- | --- | --- | --- |
| JUnit 4 | all 199; 6,136 assert calls, **1,266 with the message as first argument** (kotlin.test has it last); 19 files with `TemporaryFolder` | `ProblemsRule` | 42 |
| Nothing JVM but JUnit | **106 files** | 2 | 15 |
| `java.io` / `kotlin.io` files | 46 / 52 files | `Sources`, `CellFixtures`, `FakeDoorman` | 17 / 15 |
| threads / `java.util.concurrent` | 12 / 9 files | `ManualExecutor`, `FakeRoutingEngine`, `FakeDoorman`, `VirtualDelayDispatcher` | 14 / 11 |
| JDBC (SQLite) | – | `JdbcTours` | 6 files (through `JdbcTours`) |
| `org.json` | 13 files | 2 | – |
| read main source text (`sourceFile(…)`) | 3 | `Sources.kt` (the helper) | 7 |
| crypto (`javax.crypto`, `MessageDigest` as the reference) | 4 | – | – |

Stay JVM-only for good: everything on `JdbcTours` (SQLite is a platform edge), the source-reading tests, tests that
compare with a JDK reference (`SyncCryptoTest`, `PlanCodeTest.matchesTheJdkBase64`, the new parity tests of §2), tests
with real threads, `NetReaderTest` (BRouter jars).

## 2. Replacement per category

### 2.1 One choice each
| Category | Choice | Why | Stays JVM-only / trap |
| --- | --- | --- | --- |
| Files, directories | **Own port `Files`** (interface in `core/…/io/Files.kt`: exists / isFile / size / lastModified / list / mkdirs / delete / deleteTree / move / `writeAtomic(path, sync) { sink }` / `source(path)` / `appendSink(path)` / `open(path): RandomAccess`), paths as `okio.Path`. **JVM implementation `JvmFiles` = today's code moved** (`FileOutputStream` + `fd.sync()` + `Files.move(ATOMIC_MOVE)` from `AtomicFiles`), native implementation over `okio.FileSystem.SYSTEM` | "never lose a track": on Android not one line of the write path changes. `okio.FileSystem` itself cannot be the port: the class does not compile under TeaVM (§2.3) | Old signatures stay as thin factories in JVM code (`fun NavSessionStore(file: File) = NavSessionStore(JvmFiles, file.toOkioPath())`), so `:app`, `:state` and 140 test files compile unchanged. Trap: `File.listFiles()` order, `lastModified` resolution, `createTempFile` names – keep in `JvmFiles` |
| Streams, byte buffers, `RandomAccessFile` | **okio** `Buffer` / `BufferedSource` / `BufferedSink` / `ByteString`; own 5-line `RandomAccess { read(pos, into, off, len); size }` | one library for bytes on every target; replaces `ByteBuffer` (both byte orders: `readIntLe`, `readLong`) | byte order per call site – `PmTiles` little-endian, `Rd5Parts` and the tile hash big-endian |
| gzip / inflate / CRC / zip | **okio** `GzipSource` / `GzipSink` / `InflaterSource`; CRC32 as 15 lines of own code (BRouter's CRC, `Rd5PartsTest.crcIsBRoutersCrc` exists); `DataImporter`'s `ZipInputStream` stays JVM | gzip bytes written need not be identical (`PmTiles`: copied tiles byte for byte, own output not hashed) | okio's zlib classes are JVM + native only – enough here; not verified on native |
| SHA-256 | **`Hasher` port** (`update(bytes, off, len)`, `digest()`): JVM = `MessageDigest` as today, native = okio. The existing own `sync/Sha256` stays for small inputs | downloads of hundreds of MB are hashed – own Kotlin SHA-256 would be slower on the phone; okio's `sha256()` does not compile under TeaVM | `TileHashTest` gets a frozen hex value first (F5) |
| JSON (`org.json`, 20 files) | **Own Kotlin port of Android's `org.json`** (AOSP `JSONObject`, `JSONArray`, `JSONTokener`, `JSONStringer`, Apache-2.0) as `com.onpath.app.json`, same class and method names → a converted file changes **its import lines only** | (1) same algorithm as the phone today: key order (insertion), numbers (`1000.0` → `1000`), `\/`, lenient reading, `opt*` coercions – byte-identical by construction; (2) JVM tests finally run the phone's JSON code (today: JSON-java); (3) no new dependency, compiles everywhere incl. TeaVM | Fallback if a port is not wanted: kotlinx.serialization-json tree API (compiles under TeaVM, §2.3) behind an own `JsonObj` facade with the `opt*` coercions and an own writer – more code to prove (`1.0` vs `1`, `NaN` printed instead of refused, strict parser). A licence notice is a pull request (CLAUDE.md) |
| gson (2 files) | **stays JVM-only in phase 3** | `CellStore.poiHashes` hashes gson's `JsonElement.toString()`; a different serialiser = every installed area downloads again | later: own streaming reader with a frozen POI-hash fixture |
| Dates | **kotlinx-datetime 0.6.x** for `Instant`, `Clock.System`, `TimeZone.currentSystemDefault()`, `toLocalDateTime`; **every stored text by own code** in `time/TimeText.kt`: ISO instant (`2024-06-01T08:00:00Z`, `.500Z` only with milliseconds), `yyyy-MM-dd`, `HH:mm[:ss.SSS]`, the three GPX parse forms | stored texts stay byte-identical because no library formats them; the library only gives the zone offset | The localised tour name of `RideApply` (`d MMM yyyy` with month names) goes behind `Platform.dateText(pattern, ms)` – JVM = `SimpleDateFormat` as today. `GpxParser` accepts what `OffsetDateTime.parse` accepts (e.g. no seconds) – table test first (F4) |
| Numbers → text (`String.format`, 24 calls) | **Own `Fmt`** in `data/Fmt.kt`: `fixed(v, decimals)`, `pad(n, width)`, `hex(bytes)`; decimal separator for shown numbers from `Platform.decimalSeparator()` | 14 calls pass ROOT / US; **8 pass no locale**: `Format.clock`, `TimeEstimate` ×2 (shown), `CellStore` folder name `g%06d` ×2 (**stored**; a device with non-ASCII digits writes a folder the `GEN` regex does not find – a latent bug, keep as it is and note it), `%02x` ×3 | Java's `%.nf` rounds half-up on the exact decimal value of the double – `Fmt.fixed` must match: parity test on 100,000 random + edge values against `String.format(Locale.ROOT)` first (F3) |
| `Math.*` (96) | `kotlin.math`, by a fixed table: `Math.round(x)` → `x.roundToLong()` / `roundToInt()` (**never `kotlin.math.round`** – that is half-even), `floorDiv` / `floorMod` → `floorDiv` / `mod`, `toRadians(x)` → `x * (PI / 180.0)`, `toDegrees(x)` → `x * (180.0 / PI)`, `signum` → `sign` | on the JVM `kotlin.math.sin` etc. are the same intrinsics – no bit changes | `x * (PI / 180.0)` equals JDK 17's `toRadians` bit for bit (all frozen fixtures were made there); what the phone's own `Math.toRadians` gives is not verified. Bits fixture for `Geo` first (F2) |
| `Charsets`, `String(bytes)`, `toByteArray()` | `encodeToByteArray()` / `decodeToString()`; ISO-8859-1 and ASCII by a 3-line loop | common API | malformed UTF-8 → U+FFFD in both – same |
| Locks (`synchronized`, `@Synchronized`) | **atomicfu** `SynchronizedObject` + `synchronized(lock)` | already on the classpath (coroutines brings `atomicfu` 1.x); on the JVM it is a type alias of `Any` and the same monitor – same bytecode, no timing change | `synchronized(queue)` on a map and `@Synchronized` on a method need an own lock object: **every** site of that monitor must move together, including sites in `:app` / `:state` (grep per lock). Not verified under TeaVM (inline → `monitorenter`, so expected fine) |
| `@Volatile` | `kotlin.concurrent.Volatile` (import only) | common since Kotlin 1.9 | – |
| `ConcurrentHashMap`, `Atomic*` | plain map under the owner's lock; atomicfu `atomic(…)` | 8 + 7 uses | `AtomicFiles.locks` (per-file write locks): keep in `JvmFiles` |
| Own threads (`Executors` in `Problems`, `EventLog`; `Runnable` in `BackupQueue`; 4 dispatchers in `PlanRouteSlice`) | passed in: `fun interface Runner { fun run(block: () -> Unit) }`, a `CoroutineDispatcher` for the slices; the JVM / app side creates the same single threads as today | no scheduling change | `ThreadLocal` in `RouterRules` (1): stays JVM with the routing engine |
| `System.currentTimeMillis` / `nanoTime` | `Platform.nowMs()`; `kotlin.time.TimeSource.Monotonic` | 30 uses | |
| HTTP (`HttpURLConnection`, 4 files) | **port `Http`** (plan §3): `HttpDownload`, the transport of `CellApi`, `HttpMailbox`, `SharedPlace.Net` stay JVM files; their pure parts (status parsing, redirect rules, retry) move out | iOS will use its own HTTP anyway | `URI` / `URLDecoder` in `SharedPlaceText`: own 30 lines + table test from `SharedPlaceTest` |
| XML (SAX, `GpxParser`) | **stays JVM-only in phase 3**; later one own pull parser for app and planner (the planner already has a 217-line SAX stand-in, `web/engine/src/main/java/onpathweb/…/SAXParser.java`) | GPX import is a core feature (C7); replacing Android's parser changes what odd files do | `BackupCodec.decode` calls it → restore stays JVM-only too (split `BackupCodec` into encode = common, decode = JVM) |
| `Normalizer` (search folding) | `Platform.stripMarks(text)` – JVM = `Normalizer` NFD as today | a table of our own would change which names match | the planner has a stand-in (`TNormalizer`) |
| BRouter classes (`NetReader`) | stays JVM-only until 5b | | `WayNet`, `RouterProfile`, `TourSuggester`, `SuggestService` name it → `NetReader` behind a small interface in H2, or they wait too |

**No `expect` / `actual` in common code** (rule of the phase): platform things come in as interfaces – `Files`, `Http`,
`Hasher`, and one `Platform` object (`nowMs`, `zoneOffset` if kotlinx-datetime is not wanted, `decimalSeparator`,
`dateText`, `stripMarks`, `language`) set once at start. Reason: the planner compiles the same folder with the plain
Kotlin/JVM compiler (TeaVM), where `expect` does not exist; with interfaces it implements the port instead of
replacing files.

JVM-only at the end of phase 3, by design (≈ 16 files): `JvmFiles`, `JvmPlatform`, `net/HttpDownload`,
`plansync/HttpMailbox`, `cells/CellApi` (transport half), `search/SharedPlace` (network half), `cells/PoiTiles`,
`cells/GeoJsonMerge`, `gpx/GpxParser`, `backup` decode half, `data/DataImporter` (zip), `suggest/NetReader` (+ up to 4
that name it), `debug/MainStall` (reads thread stacks), `routing/RouterRules`' `ThreadLocal`. Acceptable: each is a
platform edge that iOS writes anew or that 5b replaces.

### 2.2 Stored and synced formats – fixture first
"Frozen" = a test asserts the exact text / bytes. "RT" = only write-then-read. Paths: C = `core/src/main/kotlin/com/onpath/app/`,
S = `state/…/ui/`. Test folders: `core/src/test`, `state/src/test`, `app/src/test` (A:).

| # | Format (where) | Written in → read in | How written | Bytes matter beyond "readable"? | Test today | Must exist before the code changes |
| ---: | --- | --- | --- | --- | --- | --- |
| 1 | Backup GPX `Documents/OnPath/<date> <name>.gpx` | C `backup/BackupCodec.encode` → `decode`, `GpxParser` | `GpxWriter` + `org.json` record, XML-escaped; dropped points by `append(Double)` | **yes**: other GPX apps, older app after restore; the **file name is compared** (`TourBackup` renames when it differs) | A: `BackupTest` RT; frozen name `readableSafeFileNames`; fragments in `RideBackupTest` | **F1**: one whole backup file as fixture (old reader reads the new text and back); name test in 3 zones |
| 2 | Backup index `files/backup-index.json` | C `backup/TourBackup.BackupIndex.save` → `load` | `org.json` | no | RT; reader fixture in `BrokenFilesTest` | F1 |
| 3 | GPX export | C `gpx/GpxWriter.write`, `PlanGpx.export` → `GpxParser` | `StringBuilder`; `%.7f`, `%.1f` (US); `ISO_INSTANT` | **yes**: other apps; **runs in the planner** | RT; fragment `SportTypeTest` | **F4**: whole-file golden (with and without milliseconds, negative coordinates, 0.05 rounding) |
| 4 | Waypoints / pins (SQLite `tours.waypoints`, `pinned`; prefs `plan…`) | C `data/TourJson` | `org.json`, `put(double)` | restore to older apps | RT | F1 |
| 5 | Followed-plan copy (`tour_plan.line`) | C `data/TourPlan.lineToJson` → `lineFromJson` | hand-built, `append(Double)` after `Math.round(v*1e6)/1e6` | restore | RT with tolerance | **F2**: golden line text |
| 6 | Rides (`tours.rides`) | C `rides/Rides.RideRecord.toJson` | `org.json` | restore | RT | F1 |
| 7 | Multi-day days (`tours.days`) | C `data/MultiDay.toJson` | `org.json` | restore | RT | F1 |
| 8 | Multi-day plan state (prefs `planMultiDay`, `planDayFollow`) | S `MultiDayFlow.save` → `restore` | `org.json` | restore | RT (`MultiDayFlowTest`) | F1 |
| 9 | Navigation session `nav-session.json`, `nav-proposal.json` | C `navigation/NavSession` (`NavSessionStore`), `ActivityCheck` | `org.json`; way runs as pipe text | no (guard is the `token` number) | `NavSessionStoreTest` RT + broken-file fixtures | F1 |
| 10 | Followed line `follow.json` | C `data/FollowFile` | `org.json` | no | RT; reader fixture A: `BrokenAppFilesTest` | F1 |
| 11 | Leg store `files/legs/*.leg` | C `data/LegFiles.encode` → `decode` | hand-built; **every double / float as Kotlin prints it** | must read back **bit-exactly**; file name is the cache key | `LegFilesTest` RT | **F2**: golden file with awkward doubles (1e-7, 1e21, -0.0, float angles); on native later in `commonTest` |
| 12 | Trip state `trip-pace.json` | C `time/TripPace.toJson`, `BreakDetector.toJson` | `org.json` (NaN → -1 / null) | no | RT | F1 (with the NaN cases) |
| 13, 14 | `tour-times.json`, `plan-times.json` (in Android backup) | C `time/TourTimesStore`, `PlanTimesStore` | `org.json` | restore | RT; reader fixtures in `BrokenFilesTest` | F1 |
| 15 | Cell store index `cells/store/gNNNNNN/store.json` | C `cells/CellStore.toJson` → `read` | `org.json` **`toString(1)`** (indented); folder `"%s%06d"` no locale | folder name must match `GEN` | RT | F1 (indent form) + folder-name test |
| 16 | **Tile hash / POI hash** (in `store.json`) | C `cells/CellStore.tileHash`, `poiHashes` | SHA-256 over big-endian ids + tile bytes; POI hash over **gson text** | **yes: a change downloads every installed area again** | `TileHashTest` compares with a second implementation in the test – **no frozen value** | **F5**: frozen hex for a fixed small cell, tile hash and POI hash |
| 17 | Merged POIs `pois.geojson` | C `cells/GeoJsonMerge.merge` | envelope by hand + gson per feature | input of the POI hash; read by MapLibre | behaviour tests | F5 |
| 18 | PMTiles (`*.pmtiles`, `pois.pmtiles`) | C `cells/PmTiles.write` / `merge`, `PoiTiles.build` → `PmTiles.Reader`, MapLibre | binary little-endian, varints, gzip | valid PMTiles v3 for MapLibre; gzip bytes free | `PmTilesTest` RT | **F5**: golden header (127 bytes) + directory bytes for a 3-tile file |
| 19 | Cell download manifest | C `cells/CellStore.writeManifest` → `CellManifest.parse` | server text verbatim / `org.json` | no | RT | F1 |
| 20 | Cell queue / usage `cells/requests.json`, `usage.json` | C `cells/MapCells.saveQueue`, `saveUsage` | `org.json` | no | behaviour; reader fixture | F1 |
| 21 | Cell API request body | C `cells/CellApi.request` | `org.json` | parsed by the map server | none on the body | F1: golden body |
| 22 | **Sync item + blob** | C `sync/SyncItem` (`SyncFormat`), `SyncCrypto` | own binary + own AES-GCM + own Base64 | **yes** (planner, other phones) | **frozen**: `PlanSyncTest.keysAreFrozenForVersion1`, `blobIsFrozenForVersion1`, `joinItemIsFrozen`, `planItemWithRoutingFieldsIsFrozen`; A: `SyncPlacesTest.theFormatIsFrozen`; `SyncCryptoTest` vectors | – (enough) |
| 23 | Kept-place line (sync field 11, hashed id) | C ids from `search/PlaceGeocoder` (`%.5f` ROOT), `SharedPlaceResolve` (`"shared:$lat,$lon"`), `poi/Poi` (`"$kind@$lat,$lon"`) | interpolated doubles | **yes: SHA-256 of the line** | A: `SyncPlacesTest.theFormatIsFrozen` | **F2**: golden ids for awkward doubles |
| 24, 25 | Sync ledger, pairing, links, marks (`files/sync/*`) | C `sync/SyncLedger`, `plansync/SyncFiles`, `SyncMarks`, `BrowserLink` | line text, numbers are longs | planner keeps the same ledger text | RT + reader fixtures | F6: golden `write()` text of each |
| 26 | Mailbox HTTP | C `plansync/HttpMailbox` | raw blob; answers read with `org.json` | server | A: `SyncMailboxE2ETest` (real `sync.js` in Node) | – |
| 27 | Pairing code / link | C `sync/PairCode` | binary + Base64 | **yes** (planner) | **frozen** (`keysAreFrozenForVersion1`, `CorePairingLinkTest`) | – |
| 28 | Plan code / link `#p=…` | C `routing/PlanCode`, `PlanLinks` | binary, varints of 1e-5° (`roundToLong`) | **yes**; compared as text (`RouteItems.samePlan`) | **frozen**: `PlanCodeTest.formatVersion1IsFrozen`, `matchesTheJdkBase64`, `PlanCodeDaysTest.multiDayFormatIsFrozen` | – |
| 29 | Track code (sync field 4) | C `sync/TrackCode` | binary | **yes** | **frozen** A: `SyncTrackTest.trackFormatVersion1IsFrozen` | – |
| 30 | Routing data text / fingerprint | C `sync/SyncItem.SyncRoutingData`, `routing/RoutingFingerprint` | text + 12 hex of own SHA-256; day by `Math.floorDiv` | **yes**, compared across devices | frozen `PlanSyncTest.routingDataText`; fingerprint only "stable" | F6: frozen fingerprint of the app's profiles |
| 31 | Router class hash `2-<16 hex>` | C `routing/RouterClassHash` | `MessageDigest`; ISO-8859-1; `"%02x"` | **yes**: must equal the server's `WayClass.java` | A: `RouterClassHashTest.sameHashAsTheClassifier` (skipped without `javac`) | F6: frozen value |
| 32 | Partial rd5 files | C `routing/Rd5Parts.write` | `ByteBuffer` big-endian, CRC32 | **yes**: BRouter reads them; tails compared bytewise | A: `Rd5PartsTest` (`crcIsBRoutersCrc`, `writesPartialFileThatBRouterOpens`) | F6: golden index bytes for a synthetic file |
| 33, 34 | Problem log `problems.json`, `events.log` | C `debug/Problems`, `EventLog` | `org.json`; stamps `SimpleDateFormat` ROOT, device zone | no (people read the export) | `ProblemsTest` RT | F4: stamp text in 2 zones |
| 36–38 | Progress shares, routes sort, voice fix (prefs) | C `domain/WaitProgress` (`%.4f` ROOT), `routes/RouteSort`, `setup/VoiceSetup` | text | restore | reader goldens (`"v1:0.98,0.01,0.01"`) | F3 covers `%.4f` |
| 39 | Suggest file `<cell>.bin` | read only: C `suggest/SuggestFile` (`Inflater`) | – | – | `SuggestCoreTest` + 17 reference fixtures `core/src/test/resources/suggest/reference/` | – |
| 40 | Download checksum | C `net/HttpDownload.sha256Of` | hex | compared with the server's | `HttpDownloadTest` | – |

The `org.json` problem behind F1: `core/build.gradle.kts` compiles against JSON-java (`org.json:json:20240303`) and
the tests run it; the phone runs Android's. They differ in key order, number text and escaping, so **no JVM test can
pin today what the phone writes**. F1 therefore is: (a) the port of Android's `org.json` with its own tests (the AOSP
rules: insertion order, a whole double as a long, `\/`); (b) per format a fixture text **as Android writes it** –
taken once from the emulator by one instrumented test that dumps every writer for a fixed input
(`FormatDumpE2ETest`, run with `tools/ci/e2e_github.sh`), checked into `core/src/test/resources/formats/`;
(c) per format a JVM test: old reader (JSON-java, stays a test dependency) and new reader both read the fixture to
the same value, and the new writer's text equals the fixture byte for byte. No reader depends on key order (checked by
the catalogue), so a mismatch found in (c) is reported, not silently accepted.

### 2.3 What TeaVM can compile – probed
Probe: a 20-line Gradle build with `org.teavm` 0.16.0, target wasm-gc, one `main` per library feature (appendix C).
"Compiles" = TeaVM built the `.wasm`; **none was run**.

| Library use | Result | Consequence |
| --- | --- | --- |
| kotlinx-serialization-json 1.9.0, tree API (`buildJsonObject`, `Json.parseToJsonElement`), no compiler plugin | **compiles** | the fallback for JSON is open |
| okio 3.18.2 `Buffer`, `ByteString.hex`, `readLongLe` | **compiles** | okio types may appear in code the planner reaches |
| okio `GzipSink` / `GzipSource` | **compiles** | |
| okio `ByteString.sha256()` | **fails**: `java.security.MessageDigest` is not in TeaVM's class library | `Hasher` port; today's 3 `MessageDigest` sites work only because the planner never reaches them |
| okio `FileSystem.SYSTEM` | **fails**: `FileSystem.<clinit>` → `ResourceFileSystem` → `ClassLoader.getResources` | no subclass or use of `okio.FileSystem` in code the planner reaches → own `Files` port; the planner implements it over `java.io.File` (TeaVM has it) |
| kotlinx-datetime 0.6.2 (`Instant.parse`, `toString`, `toLocalDateTime(UTC)`) with `threetenbp` on the class path | **compiles** | as `java.time` today |
| atomicfu `SynchronizedObject`, coroutines `Mutex` | not probed | coroutines-core (which contains atomicfu calls) is on the engine's class path today, "reachable code only" |

TeaVM's class library 0.16 (listed from the jar): has `java.io.File`, `RandomAccessFile`, `java.util.zip.*`,
`java.nio.file.Files`, `ConcurrentHashMap`, `Atomic*`; has **no** `MessageDigest`, `javax.crypto`,
`java.util.concurrent.locks`, `Executors`, `java.time` (mapped to threetenbp by the build). So the planner build is
sensitive to what its entry points reach, not to what `core` contains – every piece runs
`./gradlew -p web/engine test generateWasmGC` as its proof.

## 3. Module shape

### 3.1 Recommendation: staged, sources stay in place
| | Staged (recommended) | Flip to multiplatform first |
| --- | --- | --- |
| Start | `:core` stays `kotlin("jvm")`. New side build `tools/common-check/` (multiplatform, `linuxX64` only, like `tools/spike-routing-kmp`) compiles `core/src/main/kotlin` minus the files in `core/jvm-only.txt` (222 lines at the start), with a generated texts stand-in | all 222 files would have to move to `src/jvmMain/kotlin` on day one and back one by one: every move changes a path in `web/parity.json`, the rule files and the risk table |
| Guard while pieces run | the compiler: a file not on the list that uses a JVM API, or uses a listed file, fails the check; a piece's result is "N lines deleted from the list" | the compiler |
| Risk to the app build | none until the last pieces | the Android build, Animal Sniffer, the gate and the planner build change at once, before any value |
| End | flip (pieces X2, X3): the list becomes the `jvmMain` folder | – |

After the flip:
```kotlin
// core/build.gradle.kts
kotlin {
    jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    linuxX64()                                   // needs the texts decision, §3.3
    sourceSets {
        commonMain { kotlin.srcDir("src/main/kotlin")          // the folder does NOT move
            dependencies { api(project(":strings")); api(coroutines); implementation(okio); implementation(datetime) } }
        jvmMain   { kotlin.srcDir("src/jvmMain/kotlin")         // the ≈ 16 JVM-only files + old File-based factories
            dependencies { api(gson); compileOnly(orgJson?); compileOnly(brouterJars) } }
        jvmTest   { kotlin.srcDir("src/test/kotlin"); kotlin.srcDir("src/testkit/kotlin") }   // all tests at first
        commonTest { kotlin.srcDir("src/commonTest/kotlin") }   // filled by piece X5
    }
}
```

### 3.2 Build consequences
| Who | Today | After the flip | Count / cost |
| --- | --- | --- | --- |
| `:app` | `implementation(project(":core"))`, a JVM library | picks `:core`'s JVM variant (as an Android app does with `okio-jvm`); package names unchanged | 0 edits expected; **not verified** in this build (Gradle attribute matching android ↔ jvm) |
| `:state` | JVM module on `:core` | flips after `:core` (10 files to clean); Compose runtime 1.7.3 **has** `linuxX64` (checked in the cache) | X3 |
| Planner `web/engine` | compiles the folder `core/src/main/kotlin` minus stand-ins (`debug/Problems.kt`) and `core-excludes.txt` (empty) | same folder, same paths; **+1 source folder** `core/src/jvmMain/kotlin` (it is a JVM build); new libraries on its class path (okio, kotlinx-datetime); implements `Files` / `Platform` instead of relying on TeaVM's `java.io.File` being reached | `web/engine/build.gradle.kts`: ≈ 6 lines. `web/parity.json`: 283 occurrences of `core/src/main/kotlin` **stay**; only entries of files that move to `jvmMain` change (≤ 16) |
| `app/web-shared-files.txt` | 39 entries (files still in `:app`, tests) | unchanged; its `[tests]` that move to `src/commonTest` need a third lookup folder in `inModules(…)` | 1 line |
| Source-reading tests | one helper `core/src/testkit/…/Sources.kt` (used by 29 app, 3 core, 7 state test files) + 5 app tests with a literal `../core/src/…` path (`SharedFilesRuleTest`, `RouterProfileTest`, `RecordingClientTest`, `StopTextTest`, `TourDbMirrorTest`) | `Sources.kt` gets the `jvmMain` folder as one more root | 1 file + ≤ 5 literal paths (only if their file moves) |
| Tools | `tools/ci/risk_tests.py` (+ its test, 7 lines), `tools/dev/core_move.py`, `tools/ci/release_check.sh`, `tools/routing/jvm-test/run.sh`, 6 files in `.claude/rules/` name the folder | add `core/src/jvmMain/kotlin`, `core/src/commonTest/kotlin` | 5 files |
| Animal Sniffer | checks `sourceSets.main` of `:core` / `:state` against API 26 | must point at the JVM compilation of the multiplatform module – **not verified** that `ru.vyarus.animalsniffer` 2.0.1 sees it; fallback: a 10-line own task running the signature check on `jvmMain`'s classes. Stays needed: `jvmMain` and the libraries still call the JDK | X2 |
| Gate | `:core:test`, `:state:test` by pattern | task names change (`:core:jvmTest`; keep an alias `test`); add `tools/common-check` (stage A) → `:core:linuxX64Test` (after the flip) when `core/**` or `state/**` changed | `gate.sh`, `risk_tests.py`: lead only |
| CI | `unit.yml` paths `core/**`, `state/**` | one more job (native compile + test) = a workflow change = pull request | X5 |
| CI time | core tests ≈ 1 min | **the spike measured run time, not build time** (native 1.86× the JVM on routes; program 1.8 MB). My probe: front end over 43,000 lines in 7–14 s; a full native compile + link + test of `:core` was not measured – expect 1–3 min cold, and the Kotlin/Native compiler download (≈ 1 GB) cached in CI. Measure in F0 |

### 3.3 The texts and `linuxX64` – a decision
Checked: `components-resources-1.7.3.module` lists android, desktop, iosArm64, iosSimulatorArm64, iosX64, js,
macosArm64, macosX64, wasmJs – **no Linux**. `:core` uses `Res.string.*` 607 times in 47 files (`:state`: 18 files);
by the name graph 181 of 264 core files depend on a texts file. So a real `linuxX64()` target on `:core` does not
resolve as long as `:strings` is built on Compose resources.

| Option | What | Cost / risk |
| --- | --- | --- |
| **T0 – check build only** (recommended for phase 3) | stage A's `tools/common-check` stays the non-JVM proof after phase 3: it generates the texts stand-in from the XML files (the generator exists in `web/engine/build.gradle.kts`, 60 lines) and can also run `commonTest` on `linuxX64` | none for the app. `:core` is then multiplatform with `jvm()` only; the plan's done-rule ("compiles for a non-JVM target") is met by the check build |
| T1 – own text keys | `:strings` `commonMain` declares its own `StringResource` / `Res.string.*` (generated, texts in tables as in the planner); Compose resources leave the module. `:app` uses only `str()` / `Txt` and names the type in 8 files, so the surface is small; the planner's two stand-ins go away | every text of the app then comes from a new reader: medium risk, own piece with the screenshot tests as proof (they already read the XML files through `XmlStrings`). Makes `linuxX64()` and later iOS targets independent of Compose resources |
| T2 – an Apple target instead of Linux | `iosArm64` klib compile on Linux (cross-compilation switch), no native test run | not verified at all; no native tests |

Recommendation: **T0 now, T1 as the first piece of the iOS project** (or as piece X4 if the lead wants a real
`linuxX64` target inside phase 3).

### 3.4 "Done" for phase 3, measurable
1. `core/jvm-only.txt` ≤ 20 files, each with a reason in the file; `state/jvm-only.txt` empty.
2. `tools/dev/gradle_slot.sh -- ./gradlew -p tools/common-check linuxX64Test` green: compiles `:core` + `:state`
   common sources (≥ 300 of 323 files) and runs the common tests natively (target: the 6 format-fixture test classes
   + ≥ 80 of the 106 JUnit-only core test files after X5).
3. `grep -rlE '^import (java|javax|org\.json|com\.google|btools)\.' core/src/main/kotlin state/src/main/kotlin` = 0
   (JVM-only files live in `src/jvmMain/kotlin`).
4. Every row of §2.2 marked F1–F6 has its fixture test, green on the JVM; rows 11 and 23 also green natively.
5. `gate --full`, `./gradlew -p web/engine test generateWasmGC`, `node web/parity/check.mjs`, the full E2E run green;
   tag `umbau-phase-3`.
Stay JVM-only among the tests and why: §1.5.

## 4. Pieces

Rules for all: own worktree, `gate --full`, fresh review, nothing the user sees changes, a red piece is taken back.
**Every piece changes code the planner compiles** (`core/` is its source folder): each piece ends with the planner
build and a line for the planner session in the backlog. "Planner ✱" marks pieces whose code the planner really runs
or that add a library / port it must provide. "Format ✱" marks stored or synced formats.

### 4.1 Fixtures and foundation (before any conversion)
| Id | What | Files | Risk | Notes |
| --- | --- | --- | --- | --- |
| F0 | `tools/common-check/` (linuxX64 build of the common-clean files, texts stand-in generated, `commonTest` folder), `core/jvm-only.txt` (222 lines) + `state/jvm-only.txt`, wired into `gate.sh` when `core/**` / `state/**` changed; measure cold + warm time | tools only | L | **lead**. Appendix B is the working start |
| F1 | Port of Android's `org.json` → `core/…/json/` (new files only, nothing uses it yet) + its tests; `FormatDumpE2ETest` (instrumented, dumps the 14 `org.json` formats as the phone writes them) + the fixtures + the 14 old-reader / new-reader tests of §2.2 | new files; tests | M – Format ✱ | needs one emulator run on GitHub (`tools/ci/e2e_github.sh --tests FormatDumpE2ETest --wait`); licence notice = pull request |
| F2 | Bits fixtures: `Geo` (distance, bearing, offsets for 200 fixed point pairs as raw bits), `TourPlan.lineToJson` golden, `LegFiles` golden file, place-id goldens (rows 5, 11, 23) | tests only | L – Format ✱ | made with today's code, on the JVM |
| F3 | `data/Fmt.kt` (new) + parity test against `String.format(Locale.ROOT, …)` (100,000 values per pattern `%.1f` `%.4f` `%.5f` `%.6f` `%.7f` `%02d` `%06d` `%02x`) | 1 new file + test | L | the parity test stays JVM-only for good |
| F4 | `time/TimeText.kt` (new) + parity tests against `DateTimeFormatter.ISO_INSTANT`, `ofPattern("yyyy-MM-dd")`, `SimpleDateFormat` stamps (3 zones incl. a half-hour one, DST change days), the GPX parse table (every time string in `GpxParserTest` and `app/src/test/resources/tracks/*.gpx` + 10 odd ones); whole-file GPX golden (row 3) | new file + tests; adds kotlinx-datetime | M – Planner ✱, Format ✱ | the planner gets a new library |
| F5 | Frozen hash values (tile hash, POI hash), PMTiles golden header + directory (rows 16–18) | tests only | L – Format ✱ | |
| F6 | Golden texts: sync ledger / pairing / links / marks, routing fingerprint, router class hash, rd5 index bytes (rows 24, 25, 30–32) | tests only | L – Format ✱ | |
| P1 | Ports: `io/Files.kt` + `Hasher` + `Platform` + `Http` (interfaces), `JvmFiles` / `JvmPlatform` (today's code moved from `AtomicFiles`), okio added; `AtomicFiles` delegates to `JvmFiles` (API unchanged) | ≈ 6 new files, `AtomicFiles` | **H** – Planner ✱ | "never lose a track": `AtomicFilesTest`, `AtomicFilesConcurrencyTest`, `BrokenFilesTest`, `NavSessionStoreTest`, `FollowStoreTest` unchanged and green; E2E `CoreFieldE2ETest`. **lead or the strongest helper** |

### 4.2 Conversion (stage A) – category × package; a file with > 60 errors is a piece of its own
"List −n" = lines that leave `core/jvm-only.txt` (own-error files; files blocked only by them leave with them).

| Id | What | Files (errors) | Risk | First (tests) | E2E after | Needs |
| --- | --- | --- | --- | --- | --- | --- |
| H1 | **Hubs I** – `kotlin.math`, `Volatile`, lock in `data/Geo` (12), `routing/RoutingContract` (5), `Junctions` (2), `time/PaceModel` (3), `cells/CellMath` (2), `sync/SyncItem` (2), `SaveRule` (2) | 7 (28) | M – Planner ✱, Format ✱ (plan code, sync day) | F2; frozen tests of rows 22, 28, 30 | `CorePlanE2ETest` | F0, F2 |
| H2 | **Hubs II** – `debug/Problems` (61), `EventLog` (87), `MapJank` (12), `SafeLaunch` (4), `ScreenLifecycle` (3): JSON port, stamps by `TimeText`, files by the port, writer threads passed in; `MainStall` stays JVM | 5 (167) | **H** – Planner ✱ (its `Problems` stand-in must follow the API) | F1, F4; `ProblemsTest` (12 tests) unchanged | `StartSmokeE2ETest`, `CoreFieldE2ETest` | P1, F1, F4 |
| H3 | **Hubs III** – `data/Format` (6), `time/TimeEstimate` (9), `navigation/NavText` (5), `domain/WaitProgress` (9), `search/PlaceGeocoder` (2), `SharedPlaceMenu` (3): `Fmt` | 6 (34) | M – Format ✱ (rows 23, 36) | F3; `FormatTest`, `NavTextTest`, `WaitProgressTest`; German decimal comma test | screenshot check (`gate --full`) | F3 |
| M1 | math + small bits: `domain` (8 files, 15), `navigation` small (`JogRule`, `VoiceGuide`, `JunctionCues`, `Rerouter`, `Provisional`, `Onward`, `ActivityCheck`: 23), `poi`, `map`, `setup` (3 files, 5) | 18 (43) | L | existing rule tests | – | H1 |
| M2 | math + bits: `suggest` without `NetReader` and the three files of D5 (15 files, 47); `NetReader` behind an interface | 15 (47) | M (results must stay identical) | the 17 reference fixtures `suggest/reference/*.json.gz` (`SuggestCoreTest`) | `RoundTripSuggestE2ETest` | H1 |
| M3 | math + locks: `routing` small (`TrackToPlan` 25, `LegCache` 14, `PlanCode` 12, `Planner` 9, `RouterRules` 4, `BRouterWayStats` 4, `RoutingFingerprint` 3, `PlanPoints` 2, `BreakdownRules` 1) | 9 (74) | M – Format ✱ (row 28, 30), Planner ✱ | frozen plan-code tests, F6 | `CorePlanE2ETest`, `CorePlanLinkE2ETest` | H1 |
| J1 | JSON: `data/TourJson` (30), `TourPlan` (49), `MultiDay` (71), `FollowFile` (27), `FollowProgress` (7) | 5 (184) | M – Format ✱ (rows 4, 5, 7, 10) | F1, F2 | `CoreFieldE2ETest`, `MultiDayTourE2ETest` | F1, P1, H2 |
| J2 | JSON + files: `time/BreakDetector` (41), `TripPace` (32), `PlanTimesStore` (31), `TourTimesStore` (26) | 4 (130) | M – Format ✱ (12–14) | F1 | `CoreFieldE2ETest`, `DoneTourReviewE2ETest` | F1, P1 |
| J3 | `navigation/NavSession` (141) + `FollowStore` (6) | 2 (147) | **H** – Format ✱ (row 9): the session survives a killed app | F1; `NavSessionStoreTest` (13), `FollowStoreTest` | `CoreFieldE2ETest`, `UnfinishedTourSetupE2ETest` | F1, P1 |
| J4 | `rides/Rides` (24), `RideView` (8), `RideApply` (7, date name through `Platform.dateText`) | 3 (39) | M – Format ✱ (row 6) | F1; `RideApplyTest`, `RideViewTest` | `DoneTourReviewE2ETest` | F1, F4 |
| J5 | `backup/BackupCodec` (117; encode common, decode stays JVM), `TourBackup` (19), `BackupQueue` (8) | 3 (144) | **H** – Format ✱ (rows 1, 2) | F1, F4; `BackupTest`, `RideBackupTest` | `CoreBackupE2ETest` | J1, J4, G1 |
| J6 | `state`: `MultiDayFlow` (83) | 1 (83) | M – Format ✱ (row 8) | F1; `MultiDayFlowTest` | `MultiDayTourE2ETest` | J1 |
| G1 | `gpx/GpxWriter` (10), `PlanGpx` (2): `Fmt`, `TimeText`; `GpxParser` stays JVM | 2 (12) | M – Format ✱ (row 3), **Planner ✱ (runs there)** | F3, F4 | `CoreBackupE2ETest` | F3, F4 |
| D1 | files: `data/LegFiles` (30), `Dem` (9), `recording/LatestFileWriter` (18) | 3 (57) | M – Format ✱ (row 11) | F2; `LegFilesTest`, `LatestFileWriterTest` | `CorePlanE2ETest`, `CoreFieldE2ETest` | P1 |
| D2 | files: `plansync/SyncFiles` (34), `SyncRouting` (11), `SyncMarks` (3), `PassGate` (2); `HttpMailbox` split (transport stays JVM) | 5 (103) | M – Format ✱ (24, 25), Planner ✱ | F6; A: `SyncFilesTest`, `PlanSyncTest` | `SyncMailboxE2ETest` | P1 |
| D3 | `routing/Rd5Parts` (89), `RouterClassHash` (14) | 2 (103) | **H** – Format ✱ (31, 32): BRouter must open the files | F6; A: `Rd5PartsTest`, `RouterClassHashTest` | `RoutingDataFreshInstallE2ETest` | P1 |
| D4 | `search/BaseMapPlaces` (32), `SearchText` (6, test now SearchTextRulesTest, `Platform.stripMarks`), `SharedPlaceText` (6, own URL decoding), `SharedPlace` (14, network half stays JVM), `SharedPlaceResolve` (3); `domain/RoutesTab` (4) | 6 (65) | M | `SharedPlaceTest` (26 tests), `SearchTextTest` | `KeptPlacesE2ETest` | P1, C2 (`PmTiles` reader) |
| D5 | `suggest/SuggestFile` (7), `SuggestService` (10), `SuggestFetch` (6) | 3 (23) | M | `SuggestCoreTest` | `RoundTripSuggestE2ETest` | P1, M2 |
| C1 | `cells/CellManifest` (18), `CellApi` (90; transport stays JVM), `RouterClassCheck`, `CellTriggers`; `net/HttpDownload` stays JVM behind `Http` | 4 (110) | M – Format ✱ (19, 21) | F1; `CellDownloadTest`, `HttpDownloadTest` | `NoStaleDownloadsE2ETest` | F1, P1 |
| C2 | `cells/PmTiles` (152) | 1 (152) | **H** – Format ✱ (18): MapLibre reads it | F5; `PmTilesTest` | `FreshInstallE2ETest` | P1, F5 |
| C3 | `cells/CellStore` (483) – files, JSON, hashes (`Hasher`), locks, `Fmt`; `PoiTiles`, `GeoJsonMerge` stay JVM behind `PoiMerge` | 1 (483) | **H** – Format ✱ (15, 16, 19): a hash change downloads everything again | F1, F5; `CellStoreReuseTest`, `CellReuseTest`, `BrokenFilesTest` (`StoreWriteCostTest` was removed 2026-11-19), A: `CellStoreTest` | `FreshInstallE2ETest`, `NoStaleDownloadsE2ETest` | C1, C2 |
| C4 | `cells/MapCells` (205) – 55 lock sites, queue + usage files | 1 (205) | **H** (downloads, never a stale file) – Format ✱ (20) | F1; `CellDownloadTest`, `NoStaleDownloadsTest`, `StorageNotReadyTest` | `NoStaleDownloadsE2ETest`, `RoutingDataFreshInstallE2ETest` | C3 |
| S1 | `state`: `PlanRouteSlice` (30, dispatchers passed in), `SettingsSlice` (7), `RoutesState` (4), `TourEndSlice`, `TourFollow`, `TourEdits`, `SuggestSlice`, `DemoRules`, `FrameStats` | 9 (53) | M (four routing threads) | `PlanRouteFlowTest`, `SettingsFlowTest` | `CorePlanE2ETest` | H1–H3 |

### 4.3 The flip (stage B)
| Id | What | Risk | Notes |
| --- | --- | --- | --- |
| X1 | Remaining JVM-only files get their final shape: old `File`-based factories and JVM implementations collected, list ≤ 20 with reasons | L | |
| X2 | `:core` → multiplatform (`jvm()`), folders as §3.1, JVM-only files → `src/jvmMain/kotlin`; Animal Sniffer; gate / `risk_tests.py` / `Sources.kt` / `core_move.py` / rules; planner build +1 folder; parity paths of the moved files | **H** (build) – Planner ✱ | **lead**. `:app:assembleDebug`, `assembleRelease` (R8), `compileDebugAndroidTestKotlin`, the planner build; full E2E |
| X3 | `:state` → multiplatform | M – Planner ✱ | after X2 |
| X4 | *optional (T1)*: own text keys in `:strings`, then `linuxX64()` on `:core` / `:state` and `tools/common-check` is deleted | M–H | screenshot check + `StringFilesTest` |
| X5 | Tests: the format-fixture tests + the JUnit-only test files of common-clean packages → `kotlin.test` in `src/commonTest/kotlin` (script: imports, **message argument first → last** in 1,266 calls, `TemporaryFolder` → a small helper on `Files`); native test job in CI (pull request) | M | per package, any time after F0; row T: a test that only pins wording is dropped, not moved |

### 4.4 Order, lanes, where to stop
- Night 1: F0 (lead) ‖ F1, F2, F3 (three helpers, tests / new files only). Night 2: F4, F5, F6 ‖ P1; then H1.
  Night 3: H2, H3 (serial – both touch files everything uses), M1 ‖ M2. Night 4: lane A = J1 → J3 → J5, lane B =
  M3 → D1 → D3, lane C = J2, J4, G1. Night 5: lane A = C1 → C2 → C3 → C4 (serial, one package), lane B = D2, D4, D5,
  J6, S1. Night 6: X1, X2, X3, full E2E, tag. Night 7: X5 (and X4 if decided), spare for taken-back pieces.
- Parallel is safe between different packages. **Never in parallel:** two pieces in `cells/`; H2 with anything; P1
  with any D / C piece; X2 with anything. The one shared file of all pieces is `core/jvm-only.txt` (each piece deletes
  its own lines – git merges that).
- A helper's brief needs: this file (§2.1's table is the rule book: one replacement per category, no other), its row,
  the fixture tests that must be green **before and after without being edited**, the checks (`gate --full`;
  `tools/common-check`; `./gradlew -p web/engine test generateWasmGC`; `node web/parity/check.mjs`;
  `:app:compileDebugAndroidTestKotlin -Ponpath.e2e=true`), and the report form of `umbau-phase-2-brief.md` (every
  non-mechanical change listed; five break proofs for a format piece: change the writer in five ways, a fixture test
  goes red each time).
- **Stop and report** when: a fixture test would have to be edited to pass; a lock is shared with code outside the
  piece (`:app`, `:state`, another package); a public signature would have to change for `:app` (use a factory with
  the old signature instead – if that is not possible, stop); the planner build fails on a class TeaVM does not have;
  a function's behaviour on bad input (broken file, `NaN`, empty folder) cannot be kept exactly; the piece grows past
  its files.

## 5. Risks, and what could make phase 3 not worth it

### 5.1 Risks
| Risk | How likely / how bad | Answer |
| --- | --- | --- |
| A stored format changes without a red test | the 14 `org.json` formats and 6 others have no frozen test today | F1–F6 before any conversion; the JSON port instead of a different library |
| The file write path changes on Android | would hit "never lose a track" | P1 keeps today's `java.io` code as the JVM implementation; okio writes files only on native |
| A cell hash changes | every user downloads every area again | F5 freezes it; gson and `PoiTiles` stay JVM; `Hasher` on the JVM is `MessageDigest` as today |
| A lock is split in two | rare races in downloads / recording, invisible in tests | atomicfu keeps the monitor; rule "all sites of a monitor in one piece"; `MapCells` is its own piece |
| The planner build breaks on a library | okio `FileSystem` and hashing fail under TeaVM (probed) | ports, no `expect`; every piece builds the planner |
| Double → text differs on Kotlin/Native | `LegFiles`, place ids, `TourPlan` line print doubles with `toString()` | F2 goldens run natively in `tools/common-check`; if they differ: own shortest-round-trip printer (≈ 150 lines) – not verified either way |
| Maths differs on native (`sin`, `cos`, `atan2`) | routes and distances in the last bit; the spike saw none on glibc, Apple untested | not a phase-3 problem (JVM unchanged); F2's `Geo` bits fixture shows it natively |
| `linuxX64` impossible because of the texts | certain with Compose resources 1.7.3 | T0 / T1, §3.3 |
| Size: 36 pieces instead of "3–4 nights" | the plan counted `File` in ~30 files; the compiler counts 113 files + 10 | §5.3 |

### 5.2 What phase 3 buys – in the plan's words, checked
- "`:core` also builds for one non-JVM target in CI – a JVM-only import fails the build" (plan §3): bought by F0
  already, on day one, for the clean part.
- "phases 3–5 – iOS becomes a build target instead of a rewrite" (plan §5): true for the rules, navigation, plans,
  sync, suggestions (≈ 300 of 323 files). **Not** bought: SQLite (`TourDb` is in `:app`, the test kit uses JDBC), the
  BRouter engine (5b), HTTP, GPX import, POI tiles with gson, the texts (T1) – each is named JVM-only above.
- 5b: needs `Files` (P1) and the hubs `Geo` / `RoutingContract` (H1) to plug in; it does not need the rest. The
  spike's engine has no texts, so it can have a real `linuxX64` target in its own module today.
- Faster tests: no. Native tests are an extra job; JVM tests stay as fast as they are.
- A side gain that is real: JVM tests run the phone's JSON code for the first time, 20 formats get frozen fixtures,
  the planner loses its dependence on "what TeaVM happens to reach".

### 5.3 If time is short – the minimal phase 3 (≈ 3 nights)
F0–F4, P1, H1–H3, M1–M3, J1–J4, G1, D1, S1, then X2 / X3. Left JVM-only with a reason each: `cells/` (13 files, 39 %
of the errors, all four **H** format risks C2–C4 and no gain before iOS has map downloads), `net`, `plansync` files,
`Rd5Parts`, `backup`, `search` network, `suggest` files. Result: ≈ 190 of 264 core files common, `core/jvm-only.txt`
≈ 75 – the done-rule of §3.4 with other numbers. The rest becomes iOS-project work, done when a feature needs it.

Not worth doing at all only if iOS is dropped: nothing else in the plan depends on a common `:core`.

## 6. Not verified
- That the app (`:app`, AGP 9.4.1) resolves a multiplatform `:core` with only `jvm()`; that Animal Sniffer 2.0.1 sees
  a multiplatform JVM compilation; that R8 / the release build is unaffected.
- Android's `org.json` behaviour (key order, numbers, `\/`, `toString(1)` layout) and JSON-java's differences – from
  memory of the sources, not re-read; F1's emulator dump is the proof. Whether Robolectric app tests run Android's
  `org.json` or JSON-java.
- Run-time behaviour of the three libraries under TeaVM – only the compile was probed; atomicfu not probed.
- okio on Android: that `FileHandle.flush` syncs to disk and `atomicMove` is atomic – avoided by keeping today's code.
  okio's gzip / inflate on native; `GzipSink` header bytes.
- kotlinx-datetime: which GPX time forms `Instant.parse` accepts compared with `OffsetDateTime.parse` (F4 measures);
  version 0.7 moves `Instant` to `kotlin.time` – 0.6.2 was probed.
- Whether `Math.toRadians` on the phone (ART) is `x / 180 * PI` or `x * (PI / 180)`; the design keeps JDK 17's bits.
- `Double.toString` on Kotlin/Native vs the JVM; maths functions on Apple.
- Native build time of `:core` (compile + link + tests) and the CI cost; the spike has no build times.
- The dependency numbers ("172 files depend on `Problems`", "181 depend on the texts") come from a name-based graph
  (class / object names), not from the compiler; the 42 / 113 / 109 split is the compiler's.
- That no reader depends on JSON key order, and the test classification (frozen / RT) of §2.2: from a catalogue made by
  grep and reading of the writers, not every listed test was opened.
- Whether a newer Compose Multiplatform publishes `components-resources` for Linux (1.7.3 does not).
- Klib cross-compilation of an iOS target on Linux (T2).
- How the cell server parses the request body; whether POI properties arrive as doubles in `Poi.fromProperties`.
- E2E classes per piece are chosen by name and area, not by reading each class.

## Appendix A – the inventory script
`python3 inventory.py --tsv out.tsv core/src/main/kotlin state/src/main/kotlin core/src/test/kotlin core/src/testkit/kotlin state/src/test/kotlin`
(the TSV's extra columns `texts`, `native_errors`, `native_state` come from the probe logs of appendix B).
```python
import re, sys, os, collections
CATS = [
 ("io",       r"\b(java\.io\.\w+|File|FileOutputStream|FileInputStream|RandomAccessFile|InputStream|OutputStream|ByteArrayInputStream|ByteArrayOutputStream|IOException|OutputStreamWriter|InputStreamReader|Closeable|BufferedReader|BufferedWriter|DataInputStream|DataOutputStream|EOFException|FileNotFoundException|PrintWriter|StringWriter|Writer|Reader)\b"),
 ("kio",      r"\.(readText|writeText|readBytes|writeBytes|appendText|appendBytes|readLines|useLines|forEachLine|inputStream|outputStream|bufferedReader|bufferedWriter|reader|writer|walk|walkTopDown|deleteRecursively|copyTo|copyRecursively|listFiles|mkdirs|renameTo|nameWithoutExtension|absolutePath|canonicalPath|parentFile|isDirectory|isFile|lastModified|setLastModified|canRead|freeSpace|usableSpace|totalSpace)\b"),
 ("nio",      r"\b(java\.nio\.[\w.]+|ByteBuffer|ByteOrder|StandardCopyOption|Files|AtomicMoveNotSupportedException|Paths|FileChannel)\b"),
 ("time",     r"\b(java\.time\.[\w.]+|Instant|ZoneId|ZoneOffset|OffsetDateTime|LocalDateTime|LocalDate|LocalTime|ZonedDateTime|DateTimeFormatter|DateTimeParseException|ChronoUnit)\b"),
 ("text",     r"\b(java\.text\.\w+|SimpleDateFormat|DecimalFormat|DecimalFormatSymbols|NumberFormat|Normalizer|Collator|DateFormat|MessageFormat|ParseException)\b"),
 ("locale",   r"\b(Locale|TimeZone|Calendar|GregorianCalendar|Date)\b"),
 ("zip",      r"\b(java\.util\.zip\.\w+|GZIPInputStream|GZIPOutputStream|ZipInputStream|ZipOutputStream|ZipEntry|ZipFile|Inflater|Deflater|InflaterInputStream|DataFormatException|CRC32|Adler32)\b"),
 ("conc",     r"\b(java\.util\.concurrent\.[\w.]+|TimeUnit|Executors|Executor|ExecutorService|ConcurrentHashMap|ConcurrentLinkedQueue|CopyOnWriteArrayList|CountDownLatch|FutureTask|AtomicInteger|AtomicLong|AtomicBoolean|AtomicReference|ReentrantLock|Semaphore|LinkedBlockingQueue)\b"),
 ("thread",   r"\b(Thread|ThreadLocal|Runnable|InterruptedException)\b|\bsynchronized\s*\(|@Synchronized\b|@Volatile\b|\bkotlin\.concurrent\.\w+|\bthread\s*[({]"),
 ("jvmanno",  r"@(JvmStatic|JvmField|JvmOverloads|JvmName|Throws|JvmSuppressWildcards|Transient|Strictfp)\b"),
 ("jutil",    r"\b(java\.util\.(?!concurrent|zip|regex|Locale|Date|Calendar|TimeZone)\w+|Arrays|Collections|TreeMap|TreeSet|PriorityQueue|IdentityHashMap|WeakHashMap|LinkedList|BitSet|EnumMap|Objects|Optional|UUID|Base64|Scanner|Properties|StringTokenizer|MissingResourceException)\b"),
 ("regexjvm", r"\b(java\.util\.regex\.\w+|Pattern\.compile|Matcher)\b|\.toPattern\("),
 ("security", r"\b(java\.security\.\w+|javax\.crypto\.[\w.]+|MessageDigest|SecureRandom|Cipher|SecretKeySpec|GCMParameterSpec|KeyGenerator|SecretKeyFactory|PBEKeySpec)\b"),
 ("net",      r"\b(java\.net\.\w+|javax\.net\.[\w.]+|URL|URI|HttpURLConnection|HttpsURLConnection|URLEncoder|URLDecoder|UnknownHostException|SocketTimeoutException|ConnectException|SocketException|SSLException|InetAddress)\b"),
 ("orgjson",  r"\b(org\.json\.\w+|JSONObject|JSONArray|JSONException|JSONTokener|JSONStringer)\b"),
 ("gson",     r"\b(com\.google\.gson\.[\w.]+|JsonParser|JsonElement|JsonReader|JsonToken|Gson|JsonObject|JsonArray)\b"),
 ("xml",      r"\b(org\.xml\.[\w.]+|javax\.xml\.[\w.]+|org\.w3c\.[\w.]+|SAXParserFactory|DefaultHandler|SAXException|XmlPullParser|DocumentBuilderFactory)\b"),
 ("btools",   r"\bbtools\.[\w.]+|\b(OsmNode|OsmFile|PhysicalFile|NodesCache|MicroCache|DataBuffers|RoutingContext|ProfileCache|OsmNodesMap|OsmNodePairSet|MatchedWaypoint|CheapRuler)\b"),
 ("format",   r"\bString\.format\(|\"\.format\(|\.format\(Locale|\.format\(java\.util\.Locale"),
 ("system",   r"\bSystem\.(currentTimeMillis|nanoTime|getProperty|setProperty|arraycopy|err|out|identityHashCode|lineSeparator|getenv|gc|exit)\b|\bRuntime\.getRuntime\b"),
 ("jlang",    r"\b(Math\.\w+|java\.lang\.\w+|Integer\.\w+|Character\.\w+|Class\.forName|StackTraceElement|OutOfMemoryError|StackOverflowError)\b|\.stackTrace\b|\.(toByteArray|toCharArray)\(\)|\bCharsets\.\w+|\.(uppercase|lowercase)\(Locale|\.(toUpperCase|toLowerCase)\(|\.javaClass\b|::class\.java\b|\bkotlin\.reflect\.|\.intern\(\)|\bmeasureTimeMillis\b|\bmeasureNanoTime\b"),
 ("jdbc",     r"\b(java\.sql\.\w+|DriverManager|PreparedStatement|ResultSet|SQLException)\b"),
 ("junit",    r"\borg\.junit[\w.]*|@(Test|Before|After|Rule|BeforeClass|AfterClass|Ignore)\b|\bTemporaryFolder\b"),
 ("srcread",  r"\b(sourceFile|sourceText|sourceDir|sourceFiles|repoFile|repoRoot)\("),
 ("coroutine", r"\b(Dispatchers\.IO|runBlocking|asCoroutineDispatcher|Dispatchers\.Main)\b"),
]
RX = [(n, re.compile(p)) for n, p in CATS]

def scan(path):                      # uses per category; import lines only mark the file
    c = collections.Counter(); imp = set(); incomment = False
    for line in open(path, encoding="utf-8"):
        s = line.strip()
        if incomment:
            incomment = "*/" not in s; continue
        if s.startswith("/*"):
            incomment = "*/" not in s; continue
        if s.startswith("//") or s.startswith("*"): continue
        s = re.sub(r"\s//.*$", "", s)
        for n, rx in RX:
            k = len(rx.findall(s))
            if k and s.startswith("import "): imp.add(n)
            elif k: c[n] += k
    return c, imp

args = sys.argv[1:]; out = None
if args[0] == "--tsv": out = open(args[1], "w"); args = args[2:]
names = [n for n, _ in CATS]; rows = []
for d in args:
    for dp, _, fs in os.walk(d):
        for f in sorted(fs):
            if f.endswith(".kt"): p = os.path.join(dp, f); rows.append((p,) + scan(p))
rows.sort()
if out:
    out.write("file\tset\tpackage\tlines\t" + "\t".join(names) + "\ttotal_jvm\n")
    for p, c, imp in rows:
        m = re.match(r"(core|state)/src/(\w+)/kotlin/com/onpath/app/([^/]+)", p)
        tot = sum(c[n] for n in names if n not in ("junit", "srcread", "coroutine"))
        out.write(f"{p}\t{m.group(1)}/{m.group(2)}\t{m.group(3)}\t{sum(1 for _ in open(p, encoding='utf-8'))}\t"
                  + "\t".join(str(c[n]) for n in names) + f"\t{tot}\n")
for n in names:
    fs = sorted(((c[n], p) for p, c, imp in rows if c[n] or n in imp), reverse=True)
    print(f"{n:10} files={len(fs):3} uses={sum(k for k, _ in fs):4}  " + ", ".join(f"{os.path.basename(p)[:-3]} {k}" for k, p in fs[:7]))
```
Other counts: `grep -o 'core/src/main/kotlin' web/parity.json | wc -l` (283) · `grep -vE '^\s*(#|$|\[)' app/web-shared-files.txt | wc -l` (39) ·
`grep -rlE '\b(sourceFile|sourceText|Sources\.)' app/src/test | wc -l` (29; core 3, state 7) ·
`grep -rlE '^import com\.onpath\.strings' core/src/main/kotlin | wc -l` (45; 47 with fully qualified uses) ·
`grep -rhoE 'Res\.(string|plurals)\.[a-z_0-9]+' core/src/main/kotlin | wc -l` (607) ·
`grep -rhoE '\bMath\.\w+' core/src/main/kotlin state/src/main/kotlin | sort | uniq -c` ·
`grep -rhE '^\s*@Test' core/src/test/kotlin | wc -l` (1,748) ·
`grep -rhoE 'assert(Equals|True|False|Null|NotNull|ArrayEquals|Same|NotEquals)\(\s*"' core/src/test/kotlin | wc -l` (1,266) ·
Compose targets: the `variants[].name` list of `~/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.components/components-resources/1.7.3/*/components-resources-1.7.3.module`.

## Appendix B – the native compile probe (start of `tools/common-check`)
```kotlin
// settings.gradle.kts: pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
//                      dependencyResolutionManagement { repositories { mavenCentral(); google() } }
// gradle.properties:   kotlin.native.ignoreDisabledTargets=true ; org.gradle.jvmargs=-Xmx3g
plugins { kotlin("multiplatform") version "2.2.10" }
val ex = file("exclude.txt").readLines().filter { it.isNotBlank() }      // = core/jvm-only.txt
kotlin {
    linuxX64()
    sourceSets {
        commonMain {
            kotlin.srcDir("<repo>/core/src/main/kotlin")                 // second run: + <repo>/state/src/main/kotlin
            kotlin.srcDir("stub")                                        // the texts stand-in, see below
            kotlin.exclude(ex.map { "com/onpath/app/$it" })
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.compose.runtime:runtime:1.7.3")   // for :state
            }
        }
    }
}
```
Texts stand-in (`stub/`): `org/jetbrains/compose/resources/R.kt` with `class StringResource(val key: String)` and
`class PluralStringResource(val key: String)`; `com/onpath/strings/Res.kt` with `object Res { object string; object plurals }`,
`object Txt { var onError…; fun language(); fun s(res, vararg args); fun p(res, count, vararg args) }` and one line per
key of `strings/src/commonMain/composeResources/values/*.xml` (1,638 strings, 50 plurals):
`val Res.string.<key>: StringResource get() = StringResource("<key>")`.
Run: `tools/dev/gradle_slot.sh -- ./gradlew -p <probe> compileKotlinLinuxX64`; count `grep -c '^e: '`; files:
`sed -nE 's#^e: file://\S*/com/onpath/app/(\S+\.kt):.*#\1#p' | sort -u`. Loop: append those files to `exclude.txt`,
run again, until green (rounds: 113 → +95 → +13 → +1 → green; 42 files left).

## Appendix C – the TeaVM probe
```kotlin
plugins { kotlin("jvm") version "2.2.10"; id("org.teavm") version "0.16.0" }
kotlin { jvmToolchain(21) }
dependencies {
    implementation(teavm.libs.jsoApis)
    implementation("com.squareup.okio:okio:3.18.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
    implementation("org.threeten:threetenbp:1.6.9")
}
teavm { all { mainClass = "probe.${project.findProperty("probe")}Kt" }
        wasmGC { addedToWebApp = false; outOfProcess = true; processMemory = 2048 } }
```
Six `main` functions, one per row of §2.3 (`Json`, `OkioBuf`, `OkioHash`, `OkioGzip`, `OkioFs`, `Date`), each 3–8 lines
using exactly the calls named there; `./gradlew generateWasmGC -Pprobe=<name>`, 2–5 s each. Failures as printed:
`Class java.security.MessageDigest was not found at okio.ByteString.digest$okio` and
`Method java.lang.ClassLoader.getResources(…) was not found at okio.internal.ResourceFileSystem.toClasspathRoots … at okio.FileSystem.<clinit>`.

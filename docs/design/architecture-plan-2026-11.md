# Architecture plan 2026-11: one core, three shells (Android, browser, iOS)

Captain, 2026-11-18, on the owner's word: "rethink a clean, maintainable and token-optimized structure, future safe,
reduce testing time … considering the future need to also build for iOS. Propose a plan ready for execution."
Status: **go from the owner 2026-11-18 late – phases 0–4 in one run, back to back; 5a beside it** (decisions in §7: 1–4 yes, 5 not now).

## 1. What the code looks like today (measured on main, 0.18.0)

| Fact | Number |
| --- | --- |
| Gradle modules | 2: `:app` (everything), `:strings` (Compose Multiplatform resources, Android target only) |
| App code | 21 packages, ~78,000 lines; `ui/` alone 28,900 lines in 98 files |
| Packages with **no** Android import | `domain`, `navigation`, `sync`, `time`, `gpx`, `rides`, `setup`, `net` (0 files); `routing`, `suggest`, `search`, `plansync`, `cells`, `backup` (1–4 files each) – together 35,500 lines in 230 files. **Checked 2026-11-18 [umbau]:** 43 of them use the texts module (Android-only target today), 13 use Android's `org.json`, and most need hubs outside (`Problems`, `Geo`, `TourDb` types …) – only 65 files can move untouched; the cut is in `umbau-phase-1.md` |
| Biggest files | `AppViewModel` 5,760 lines (266 functions, 78 `mutableStateOf`), `RecordingService` 1,832, `NavigateScreen` 1,344, `MapCells` 1,274, `MapController` 1,263 |
| `ui/` files without any `@Composable` | 28 – state and logic living in the UI package |
| Browser planner | compiles **103 app files + 29 tests named in a list** (`app/web-shared-files.txt`; 178 entries with texts, jars, profiles) with TeaVM, plus 31 stand-in files for Android classes; **52 more app files are re-written by hand** and watched by `web/parity.json`; 12,400 lines TypeScript + 2,800 lines Kotlin of its own |
| Routing engine | BRouter as 5 Java jars; used directly in only 2 files |
| JVM-only library use in the Android-free packages | `java.io.File` (~30 files), `java.time` / `Locale` / `Calendar`, `java.util.concurrent`, `java.util.zip`, `java.net.URL`, gson |
| Tests | 490 unit test files (55,000 lines, ~3,700 tests); 83 of them read **source text** of main files; 48 screenshot test files with 549 PNG pictures; `.git` 607 MB |
| CI | unit run ~7 min; **the full E2E run on every push takes 45–85 min with its queue time (a run is capped at 45 min) and is cancelled by the next push** – on a busy day it never finishes (runs 802, 803, 805 of 2026-11-18); 120 sleeps in the E2E code |
| Docs every session loads | `docs/backlog.md` 110 KB (~28,000 tokens), `core-features.md` 55 KB, `requirements.md` 33 KB, `project-status.md` 22 KB |
| Session cost | one planner session reached 855,000 tokens of context before a restart |

## 2. The three structural problems

1. **Boundaries are kept by convention, not by the compiler.** One module means any file may import any other.
   83 source-text tests, the shared-files list, its rule test and `parity.json` exist only to guard borders a module
   system would guard for free. Every guard costs test time and tokens, and each breaks on harmless edits.
2. **The logic of the screens is not shared.** The pure rules are shared with the planner, but what a tap does
   (`AppViewModel`) is written twice: Kotlin in the app, TypeScript / `WebApi` in the planner. Every app change makes the
   planner run red until the laptop mirrors it. An iOS app would make it three times.
3. **The core is pure in spirit but tied to the JVM in fact** (`java.io.File`, `java.time`, gson, BRouter in Java).
   Nothing of it compiles for iOS today.

## 3. Target structure

```
:core            pure Kotlin, Kotlin Multiplatform (commonMain). Rules and data: domain, navigation, plan logic (routing/),
                 suggest, sync, time, gpx, rides, plan-sync logic, search logic, map-cell logic, backup format.
                 Talks to the outside only through small interfaces ("ports"): Files, Clock, Http, RoutingEngine, Log.
:state           pure Kotlin, multiplatform. What the screens do: the slices cut out of AppViewModel (plan document,
                 route run, recent plans, sync driver, multi-day, suggest, tour control). No Android, no Compose UI.
                 One implementation for the app, the planner and iOS.
:engine-brouter  JVM. The BRouter jars behind core's RoutingEngine port. Android and the browser (TeaVM) use it.
:platform-android  the ports on Android: files, SQLite, location, text-to-speech, downloads, the recording service.
:app             Android only: Compose screens, MapLibre, MainActivity, wiring.
:strings         as today, plus an iOS target later.
web/engine       depends on :core + :state + :engine-brouter as modules. The file list, most stand-ins and most of
                 parity.json go away.
(later) :app-ios  Compose Multiplatform screens or SwiftUI on :core + :state; MapLibre Native for iOS.
```

Rules that keep it clean, each checked by the build, not by a text test:
- `:core` and `:state` have no Android dependency – it cannot compile otherwise.
- `:core` also builds for one non-JVM target (`linuxX64`, later `iosArm64`) in CI – a JVM-only import fails the build.
- A size ratchet (one test, not 83): no file above 800 lines, `AppViewModel` only ever shrinks.
- `ui/` holds composables only; state lives in `:state`.

## 4. iOS: the honest picture

| Part | State for iOS | Work |
| --- | --- | --- |
| Rules, navigation, plans, sync, suggestions | reusable once in `:core` commonMain | phases 1–3 below |
| Screen logic | reusable once in `:state` | phase 2 |
| Texts | Compose Multiplatform resources already | small |
| Map | MapLibre Native exists for iOS; our style and tiles are plain files | medium – a second map wrapper |
| Recording, GPS, voice, background | must be written for iOS (CoreLocation, AVSpeech) against the same ports | medium – per platform by nature |
| **Routing (BRouter, Java)** | **does not run on iOS** | **the one big item** |

Three ways for routing on iOS – recommendation first:
- **A. Port BRouter's routing core to Kotlin** (in `:core`). Mechanical for agents, and we have the method: reference
  routes from the Java engine as fixtures, a parity tool, as done for the suggestions port. It also lets the planner
  move from TeaVM to Kotlin/Wasm later, which removes the stand-in layer. Risk: speed and memory of Kotlin/Native on
  long routes – unknown until measured.
- B. Run today's browser engine (Wasm) inside the iOS app. Quick to show, but background navigation and big offline
  files inside a web view are fragile – against "robust first".
- C. Route on a server for iOS. Breaks "offline" and "everything on the phone".

Before committing to A: a **two-day spike** (phase 5a) – port the rd5 reader and one profile, route 10 reference routes
on `linuxX64`, compare result and time. iOS screens: decide Compose Multiplatform vs SwiftUI after phases 1–3; nothing
in this plan depends on it.

## 5. Execution plan

Each piece: built in its own worktree, reviewed fresh by a second model, `gate --full` green, nothing the user sees
changes, a red piece is taken back. Phases 1–4 need no owner test; each ends with a release candidate.

| Phase | What | Done when | Size |
| --- | --- | --- | --- |
| **0 Test time and tokens** (no code risk) | (a) E2E: not on every push; nightly on main + before a release + one class on demand; 4 emulator shards in parallel. (b) 120 sleeps → one `waitUntil` helper. (c) Backlog: only live lines (cap 20 KB, checked by the gate), detail per item in `docs/items/`, read lines deleted; `core-features.md` one file per feature with a short index. (d) Session rule: handoff and restart at ~350,000 tokens. | the nightly E2E finishes green in < 20 min; `backlog.md` < 20 KB | 1 night |
| **1 `:core` as a module** | Move the Android-free packages into `:core` (JVM module first). The 1–3 Android files per package go behind ports. `web/engine` depends on `:core`; `web-shared-files.txt` and `SharedFilesRuleTest` are deleted. Source-text tests that only guarded a border are deleted (measured: 2 files – ~70 of the 75 source-reading tests guard other things and stay). | `:app` builds on `:core`; planner builds from the module; core tests run without the Android build (~1 min) | 2–3 nights |
| **2 `:state`** | Finish cutting `AppViewModel` into slices (plan editing, map data flow, search, settings, navigation session, POI) and move all slices into `:state`. The view model becomes thin wiring (target < 800 lines). The planner's `WebApi` calls the same slices; the hand mirrors in `parity.json` shrink with each slice. | `AppViewModel` < 800 lines; state-logic mirrors 8 → 0 (**corrected 2026-11-19 [umbau]** after measuring: of the 52 mirrors only 8 watch state logic – 17 watch how Composables look, 13 are pure rules rewritten in TypeScript, 8 map code, 6 stand-ins; the honest floor after phase 2 is ~40, „under 15“ needs the planner to run the slices, a laptop step); every slice has JVM behaviour tests. Cut list, order and briefs: `umbau-phase-2.md` | 4–6 nights |
| **3 Core off the JVM** | In `:core` and `:state`: `java.io.File` → the Files port (okio underneath), `java.time` → kotlinx-datetime, gson → kotlinx.serialization, locks / executors → coroutines. Turn both into multiplatform modules; CI builds `linuxX64`. Stored formats stay byte-compatible (fixture tests first). | `:core` + `:state` compile for a non-JVM target; all format fixtures pass | 3–4 nights |
| **4 Platform edge** | `RecordingService` split: recording engine and navigation session into `:core` / `:state` behind Location / Speech ports; the service keeps only Android lifecycle (< 500 lines). Screenshot tests: components instead of whole screens where the screen is not the point (549 → ~200 pictures). Lint fails on new findings (the open proposal). | service < 500 lines; picture count; lint gate on | 2–3 nights |
| **T Tests: by risk and behaviour** (owner 2026-11-18 night; runs inside phases 1–4, not after) | Continue `docs/design/test-audit.md` (2026-11-06; since then 327 → 490 test files). Per piece that moves: its tests are sorted KEEP / CONVERT / DROP **before** the move. Keep: field-bug reproductions, `Core*Test`, rules with real risk (track loss, navigation, sync, stored formats). Convert: many small tests of one function → one behaviour test per feature flow (the slice harness + random-flow property tests). Drop: tests that pin wording or structure of the code, twin tests of the same rule, source-text tests the module border now covers. New rule in `working-agreement.md`: a new test must name the risk or the behaviour it guards. | test lines 61,000 → under 35,000 with no core feature (`core-features.md`) left without a behaviour test; source-text tests ≤ 15; unit run under 3 min | with each phase |
| **5a Routing spike** | see §4 | numbers on the table; owner decides A / B / later | 2 days |
| **5b BRouter in Kotlin** (only after 5a) | port with route fixtures and a parity tool; Android switches when equal and not slower | all reference routes identical; phone times within +10 % | 1–2 weeks |
| **6 iOS app** (own project) | shell, map, GPS, voice, background on the ports | – | after 5 |

Order and overlap: phases 0 and 1 can start now. Phase 2 touches what `mehrtage` and `IR` just built – one writer at a
time in `AppViewModel`; feature work on screens pauses during phase 2 or lands as slices itself. Phase 3 changes shared
files daily – the laptop mirrors nothing new by then (that is the point of phases 1–2).

What you gain, in order: phase 0 – a nightly E2E that actually finishes, and ~25,000 tokens less per session start;
phase 1 – core tests in about a minute and no shared-files bookkeeping; phase 2 – app and planner change together, the
planner stops going red after app pushes; phases 3–5 – iOS becomes a build target instead of a rewrite.

## 6. Models

| Work | Model | Why |
| --- | --- | --- |
| Design of each phase, the module cut, the `AppViewModel` slices, the Files / Location ports, the BRouter spike and port | **Fable 5.2** | the hard part is deciding the borders; mistakes there cost every later piece |
| Bulk moves, import rewrites, sleeps → `waitUntil`, backlog / docs split, library swaps after the pattern is set | **Sonnet 5.5** as helpers under a Fable session | high volume, mechanical, checked by the compiler and the tests |
| Fresh review of every piece before it merges | **Opus 5.5** | a second pair of eyes that did not write it – the rule that worked in the cleanup night |

One standing session on donald leads it (Fable), helpers in worktrees, the laptop follows for `web/`.

## 7. Decisions for the owner

1. Go for phases 0 and 1 now? (recommendation: yes – no visible change, pays back at once)
2. Phase 2 pauses new screen features for about a week. OK? (recommendation: yes, right after the 0.18 test round)
3. E2E no longer on every push, but nightly and before a release. OK? (recommendation: yes – today it never finishes)
4. Routing spike 5a after phase 3, or earlier in parallel? (recommendation: in parallel, it is small and decides the iOS path)
5. `.git` is 607 MB, mostly old pictures. Shrinking needs a history rewrite. (recommendation: not now)

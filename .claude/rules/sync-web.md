---
paths:
  - "app/src/main/java/com/onpath/app/sync/**"
  - "core/src/main/kotlin/com/onpath/app/sync/**"
  - "core/src/test/kotlin/com/onpath/app/sync/**"
  - "app/src/main/java/com/onpath/app/plansync/**"
  - "core/src/main/kotlin/com/onpath/app/plansync/**"
  - "core/src/test/kotlin/com/onpath/app/plansync/**"
  - "app/web-shared-files.txt"
  - "web/**"
  - "tools/sync/**"
  - "docs/design/web-sync.md"
  - "docs/design/web-planner.md"
---
# Plan sync and the browser planner (loads when you touch sync/, plansync/, web/ or the shared-file list)

| Path | What |
| --- | --- |
| `  sync/`, `plansync/` | Plan sync phone ⇄ browser (A-007, `docs/design/web-sync.md`): `sync/` = the pure code shared with the web planner (pairing code + keys, AES-GCM, item format with plans and imported tracks – `TrackCode`, ledger, merge "newer wins, older stays as a copy", `SyncRound`; `SharedCoreRuleTest`); `plansync/` = the app's side: `PlanSync` (background thread, triggers, never during a recording; `SyncPoll` – every 15 s while the app shows My routes / Browser sync (owner 2026-10-30: not the Plan map; 2026-11-13: 15 s, was 60 s) + pull to refresh on My routes → Planned when paired), `PlanSyncPass` (routes / imported tracks ⇄ items; a delete from outside → recycle bin), `HttpMailbox` (plan.onpath.earth/sync/v1), `SyncFiles` (files/sync/, atomic, not in Android backup); Settings → Browser sync. Test against the real mailbox in Node: `tools/sync/e2e.sh` |
| `core/` + `app/web-shared-files.txt` | **What the web planner compiles from the app**: the whole folder `core/src/main/kotlin` (minus the stand-ins in `web/engine/src/main/kotlin/com/onpath/app/` – same path = replaces the real file, a stand-in with no counterpart fails the build – and `web/engine/core-excludes.txt`, each with a reason) + the few files still in `:app` named in `app/web-shared-files.txt` `[files]` (+ its `[tests]`). A change = shared code ("Web planner" CI runs; tell the laptop if it turns red) |
| *(other repo)* | **Website onpath.earth** lives in its own repo `happychriss/onpath.earth` (Cloudflare Pages, owner) – not in this repo; app sessions don't add website files here. The browser planner (`web/`, on `main` since 2026-11-06) is a different thing: its code stays here, it is served under onpath.earth (`plan.onpath.earth`, owner). onpath.earth is OnPath's main address (welcome, help, privacy, imprint); 44-2.de is the developer’s personal background site: link only to `https://44-2.de/`, not its old OnPath/OnPass project pages, which the owner plans to remove. Product and help links use onpath.earth. |

- Shared code (`:core` + every file in `app/web-shared-files.txt`): a change is announced to the planner side (backlog); app release and planner deploy go out together after `tools/ci/release_check.sh` says GO.

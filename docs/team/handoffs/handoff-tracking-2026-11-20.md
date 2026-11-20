# Handoff [tracking] – live tracking, 2026-11-20 afternoon

Start sentence for the fresh session: „Du bist die Rolle **tracking** (OnPath). Lies `docs/team/handoffs/handoff-tracking-2026-11-20.md`,
dann `docs/design/live-tracking.md` (Design, Entscheidungen §12, Bauplan §14 mit den „Built“-Absätzen).“

## What is on main
- `:core` `live/`: link code + keys, item formats, chunker, upload / viewer pacing, status-line rule, the uploader with its
  ports (`LiveUploader.kt`), incl. „the final ended head goes at once“ (1365b8a94). Tests `Live*Test` (50).
- `:app` `live/`: the Android edge (`LiveEdge.attach` in `OnPathApp`, lane beside the recording, `RecordingService`
  untouched) – **dormant: nothing calls `liveStart`**.
- `web/host` `live.js` + migration 0004, `web/app` viewer page `/live/` + `live-smoke.mjs`, engine `WebLive.kt`. Deployed
  by the laptop: page 200, API answers (401 / 404), tables exist. **No tour has ever been uploaded to the online server.**

## Open branches / pull requests
- **`feat/live-share-ui-2`** (pushed, 97eecb765, rebased on main 1365b8a94, gate --full green there; the older
  `feat/live-share-ui` is the first version – superseded, not force-pushed): slice `LiveShareSlice` + UI, built to the
  owner's two decisions:
  1. „Live teilen“ as the **first entry with a symbol in the ⚙ Settings opened from Navigate** (a card at the top, only
     while a tour records; sharing: state row, „Link nochmal teilen“, „Teilen beenden“, „Name auf der Seite“).
  2. The sign that sharing is on **„oben bei den Richtungen“**: three proposals pictured (A pill above the turn card, B a
     dot chip, C „● Live“ inside the turn card – recommended, the branch's code carries C; a free tour falls back to A).
  Gallery for the owner (public repo): https://github.com/happychriss/OnPath/blob/feat/live-share-ui-2/docs/design/live-app-pictures.md
  – also linked in the cloud doc. **Owner 2026-11-20 afternoon (chat boxes): the sign is C** (in the turn card; free tour = A's pill) **and „Ja,
  einbauen“** – the Settings entry as pictured goes to main so donald can ship it; merging makes sharing reachable for
  every user. In work: the review, then the merge.
  **Owner 2026-11-20 (chat, his pick 3 of five names): no „Live“ as a word – the entry is „Standort teilen“ / „Share
  location“, the sign „● Standort“ / „● Location“** (waiting: „Standort · wartet auf Netz“). „Link nochmal teilen“ /
  „Teilen beenden“ stay. [tracking]'s own follow-ups, told to him: share text „Verfolge meine Tour: ‹link›“ / „Follow my
  tour: ‹link›“, viewer page title „OnPath – geteilter Standort“ / „OnPath – shared location“. Change on the branch
  before the merge (strings `strings_live.xml`, web `strings_live.xml`, pictures re-recorded). Before the merge: a big-model review of the branch (recording
  is a core feature), the non-chosen variants removed. After it: `node web/parity/check.mjs --update` is the laptop's
  (Prefs.kt mirror + two new strings files) – a line for him; then a line for donald „ready to ship“. Known gaps: the
  Settings card stays if the tour ends while the page is open; the Android glue (share sheet, collector) has no test.
- **Pull request 17** (`pr/live-host-config`): `wrangler.toml` (per-IP limit `LIVE_RATE`, hourly clean-up cron,
  `run_worker_first`, `LIVE_NEW_PER_IP_DAY`) + `web.yml` (live-smoke in CI). Waits for the owner (backlog „Waiting“).
- `feat/live-server`, `feat/live-viewer`: merged content, kept only for the viewer pictures
  (`docs/design/img/live/` on `feat/live-viewer` – **not on main**; the cloud doc links them). Do not delete (prune rules).

## Waiting for the owner
- His remarks on the viewer page's look (he answered „Passt, aber Kleinigkeiten“ **before he could see the pictures** –
  not a yes). Cloud doc with links: https://claude.ai/artifact/PbX86VASovbaNPWDXY2FYn (complete: gallery link, the three sign proposals, the Settings pictures).
- Pull request 17.

## Still to do (design §10 / §14.7)
- Pixel walk before any release of the feature: screen-off network for hours, battery beside a walk without sharing,
  Android's SQLite under a long catch-up read while recording, a real upload to the online server, „Continue tour“ after
  a kill keeps the link. Phone rules: `docs/team/owner-preferences.md`.
- Privacy text (proposal in the design §13) – by pull request, app string + a line for cloud-web (website).
- Requirements: move „Live tracking“ from out of scope into scope with F-LIVE ids; a help line.
- Not built: „· Live“ in the tour notification (needs `RecordingService.kt`, which may only shrink), a Settings row to
  change the name, the slim engine for the viewer page (it pulls 1.9 MB gzip, the full planner engine), `gpsLost` on the page.
- The secret scanner: frozen test vectors carry `// ggignore` + a comment; keep that for every new frozen value.

## Rules that cost me time
- This session type cannot attach files to the chat and has no artifact upload: pictures reach the owner only as links
  (public repo) – say so, never write „Bilder liegen im Chat“.
- The push hook wants `tools/ci/gate.sh` run in a command *before* the one that pushes.
- `check-web.sh` does not run through on donald (Node 22.14, no JVM results / cell store): run the pieces, then watch
  `web.yml` on the push (`gh run list --workflow "Web planner"`); a newer push cancels the older run.
- Token rules (owner, 2026-11-20): helpers on Sonnet for reading / search / tests / pictures, one big-model review per
  piece; handoff at ~350k.

## Incident 2026-11-20 afternoon – the share UI was on main for ~1 h, unreviewed (reverted)
The read-only reviewer ran `git checkout --detach origin/feat/live-share-ui-2` **in the main session's worktree**; my next
`pull --rebase` + push (36ef7b048) carried the branch's two commits (7a8e888f2, ca4196310) to main. Reverted without
history rewrite (two revert commits, main 513a99441, gate --full green; app / core / state / strings equal to before).
**No release contains it** (v0.18.3 and v0.18.4 checked: no `LiveShareUi.kt`). Lesson: a helper that only reads gets
`isolation: worktree` too, or the brief says „`git show` / `git diff` only, never checkout“; and look at `git status -sb`
before a push.

## Next step for the fresh session: fix on a new branch from `feat/live-share-ui-2`, then merge (the owner said „Ja, einbauen“)
Review (big model, 2026-11-20) – verdict „merge after fixes“. To fix before the merge:
1. **Leak:** `LiveShareWiring.kt` – the weak map's value holds its key (prefs / host / notices capture the view model) →
   every view model stays forever. Remove the entry in `vm.addCloseable { … }` or keep the slice in a holder the view model owns.
2. **„Teilen wurde beendet“ is never seen in Navigate** (INFO at `NoticePlace.MAP` is log-only outside Plan) but is marked
   seen. Show it where Navigate / Settings shows lines, or do not call `liveSeen()` before it was shown; the test must check
   what the user sees.
3. **Privacy (uploader, on main):** „Teilen beenden“ offline, then sharing again → `LiveUploader.start` drops the old share
   that is still stopping; its box lives up to 48 h and the first link still shows the way up to the stop. Keep the pending
   DELETE(s) (a list of boxes still to delete in the stored share) – test first.
4. **Share / Stop wait for the network before the UI changes** (`LiveLane.start/stop` publish only after `step()`, up to
   ~25 s on a weak network; the share sheet pops up late). Publish the snapshot right after `uploader.start/stop`, before `step()`.
5. The Settings card is not recomposed when the tour ends (`host.recording()` reads a plain `StateFlow.value`) – collect it
   as state.
6. The 12-min „fresh“ rule is only evaluated on recomposition, and `lastOkMs == null` after a process restart counts as
   green – a small ticker / treat null as waiting. Design §2 says „< 5 min“: the code's 12 min is right, fix the doc.
7. Slice created and collector started inside `remember` during composition – create outside composition (with fix 1).
8. TalkBack: the turn card's `clearAndSetSemantics` wipes the sign's role – the menu is unreachable there (Settings has both actions).
9. `askName` survives Settings being closed by code → `nameCancelled()` in `onDispose`.
10. `liveName` lies in `onpath.xml`, which is in Android's cloud backup – exclude the key or tell the owner.
11. **The owner's words:** „Standort teilen“ / „Share location“, sign „● Standort“ / „● Location“, waiting „Standort · wartet
    auf Netz“; share text „Verfolge meine Tour: ‹link›“; viewer title „OnPath – geteilter Standort“ (app `strings_live.xml`
    + web `strings_live.xml`); re-record pictures, check the longer word inside the turn card (DE, EN, large font).
12. Delete what was only for pictures: `LiveSignLook` (B dot, the `look` parameter), screenshot tests `aLive / aWaiting /
    bLive / bWaiting` + their PNGs, the A / B parts of the gallery; `LiveMenuItems` private. Keep C + the free-tour pill.
Checked fine by the review: no crash / stall for users who never share, recomposition cost, threading (UI only queues on
the lane), a share from a previous process shows, the first-share flow, link / secret never logged, catch rules.
Then: gate --full, merge to main, line for [laptop] (`node web/parity/check.mjs --update`: Prefs.kt + the two new strings
files), line for [donald] „ready to ship“, the Pixel walk.

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
- **`feat/live-share-ui`** (pushed; a helper is still working in its worktree
  `.claude/worktrees/agent-a84441c86e57a214b`): slice `LiveShareSlice` + UI. The owner's decisions for it:
  1. „Live teilen“ sits **in the ⚙ Settings opened from Navigate, as the first entry with a symbol** (his words: „In der
     Navigation bei den Einstellungen, als erster Eintrag mit Symbol“) – not in the status card (first build, being removed).
  2. The sign that sharing is on: **„oben bei den Richtungen anzeigen“** – he wants **proposals (≤ 3) as pictures he can
     see from the cloud**. The helper builds variants + a gallery `docs/design/live-app-pictures.md` on the branch (the repo
     is public: GitHub renders it; raw image URLs open without login).
  The UI goes to main **only after his yes to the pictures** (sketch before build). Merging makes sharing reachable for
  every user. After the merge: `node web/parity/check.mjs --update` is the laptop's (Prefs.kt mirror + new strings files) –
  write him a line; then a line for donald „ready to ship“ (owner's weekend mode: donald releases updates).
- **Pull request 17** (`pr/live-host-config`): `wrangler.toml` (per-IP limit `LIVE_RATE`, hourly clean-up cron,
  `run_worker_first`, `LIVE_NEW_PER_IP_DAY`) + `web.yml` (live-smoke in CI). Waits for the owner (backlog „Waiting“).
- `feat/live-server`, `feat/live-viewer`: merged content, kept only for the viewer pictures
  (`docs/design/img/live/` on `feat/live-viewer` – **not on main**; the cloud doc links them). Do not delete (prune rules).

## Waiting for the owner
- His remarks on the viewer page's look (he answered „Passt, aber Kleinigkeiten“ **before he could see the pictures** –
  not a yes). Cloud doc with links: https://claude.ai/artifact/PbX86VASovbaNPWDXY2FYn (its two upper sections are still
  placeholders: fill with the gallery link + recommendation when the helper's pictures are there).
- Pull request 17.
- The Live-sign proposals (coming).

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

# [tidy] The repo tidy-up of 2026-11-20 – what was done, what was found, the wip branches

## Done (six commits on main, docs and tools only)
- **Personal read** (f72298882): backlog, all items, all handoffs, roles, project status, owner preferences read in full
  by three helpers. Cut or worded neutrally: two phone serials, the owner's email (in `docs/design/web-planner.md`),
  a family member's feedback (item renamed), a venue and a street name next to a test place, the coordinates of a demo
  ride, absence notes, 20-odd private picture-page links (`claude.ai/artifact/…`) in backlog, items, handoffs.
  No key, token or password value was found anywhere.
- **Guide page** `docs/README.md` (178801504); `docs/team/README.md` and `experiments/README.md` came with their moves.
- **Clean-out** (65f53fa74): 91 item files without a backlog line, 12 handoffs older than each role's newest, 10 role
  lines without a reader tag; "Last session" in `docs/project-status.md` back to the newest four entries.
- **Spikes** (1d5103dea): `tools/spike-routing-kmp` and `tools/style_analysis` → `experiments/`.
- **One folder** (8136a0ea7): `docs/team/` = backlog, items, roles, handoffs, working agreement, owner preferences,
  deleted-branches log. Paths followed in CLAUDE.md, README, gate, `backlog_slim.py`, `prune_branches.sh`, hooks, design pages.

Files: `docs/` 380 → 283 (of them `docs/team/` 187; outside the notes 95) · items 257 → 171 · handoffs 16 → 4 ·
`tools/` 343 → 251 · `experiments/` 0 → 93.

## Left as it is – for the owner or the role to decide
- **Test places stay.** Hilden, Neuss, Zons and the test areas are in product code and tests (`DemoLocation.kt`,
  `HildenVoiceTest`, `tools/routing/jvm-test/JogHildenTest.kt` with exact start coordinates, about 60 files). Read
  together they hint at a home region. Changing them is product code and test data – not done; the owner's call.
- **Picture-page links in design pages and code comments** (`docs/design/tour-suggestions.md`, `multi-day-tours.md`,
  `ui-style.md`, `map-colour-analysis.md`, `docs/ux-search-proposals.md`, 5 Kotlin files, `tools/suggest-poc/page.html`):
  private pages, dead links for a contributor. Not personal; left.
- **`tools/suggest-poc/` stays in `tools/`:** `core/` and `app/` tests read its `settings.json` and fixtures.
- **A stale path in product code:** `state/src/test/…/WebReadyRuleTest.kt:11` names `docs/items/2026-11-19-how-we-split-web.md`
  in a comment (now `docs/team/items/…`). [umbau]'s file.
- **`tools/cloud/Dockerfile.job.dockerignore`** still lists `style_analysis` (harmless; a change there starts a maps deploy).
- **`docs/history/`** holds only `release-draft-v0.18.2.md` now – the folder can go when the release notes are out.
- **The branch name `donald-father-feedback`** stays in `deleted-branches.md` (the log is what `restore` reads).
- No workflow under `.github/workflows/` names a moved path.

## The wip/* branches (nothing deleted)
The captain passed on the owner's yes to deleting the five old ones. [tidy] did not delete: the session's brief says the
owner decides, and the bare `git push origin --delete` is blocked in `.claude/settings.json` – the way is
`tools/dev/prune_branches.sh` (it logs each branch for `restore`). The check it asked for:

**The five old ones – all hold work that is NOT on main:**

| Branch | Last commit | Not on main | What |
| --- | --- | --- | --- |
| `wip/donald-2026-11-08-smooth-map` | be609d8d0 | 2 commits | smooth map, WIP |
| `wip/donald-2026-11-13-planner-suggest` | 8bae9a3ed | 1 | planner tour-suggestion framework, switched off (an earlier design than what is live) |
| `wip/donald-2026-11-13-suggest-round7` | fd2593f05 | 1 | tour suggestions round 7, stopped unfinished, untested |
| `wip/donald-2026-11-14-planner-suggest-remote` | 0ae208fea | 3 | planner test setting for the remote suggest trial |
| `wip/donald-2026-11-14-suggest-next` | f50159937 | 1 | tour suggestions, stopped unfinished, untested |

**Wholly on main (safe to prune):** `wip/ir-2026-11-17-aims`, `-official-access`, `-pill-words`, `-pills`, `-port-batch`,
`wip/ir-2026-11-18-app-suggest-core`, `-app-suggest-ui`, `-card-row`, `-card-row-on`, `-core-memory`, `-planner-suggest`,
`wip/laptop-2026-11-17-suggest-layer`, `wip/donald-2026-11-19-progress-design`, `wip/donald-2026-11-19-undo-sheet`.

**Not on main, newer:** `wip/donald-2026-11-19-honest-progress` a07af6144 (1) · `wip/donald-2026-11-19-zuhause`
5a958c25f (1, Zuhause is off by the owner's word) · `wip/laptop-2026-11-05-terrain-fixes` 21c6b365b (1) ·
`wip/laptop-2026-11-07-parity-colours` c9d59a960 (2) · `wip/mehrtage-2026-11-17-stage23-planner` 7180c036a (3) ·
`wip/mehrtage-2026-11-17-stage4-phone` 090831ddd (1) · `wip/umbau-2026-11-18-kn-spike` 59edf557e (5) ·
`wip/umbau-web-slices-2026-11-20` a90e8f4a9 (3, the live offer to the laptop).

"Not on main" is counted by patch (`git cherry origin/main <branch>`); a branch whose work was rebuilt differently on
main still counts.

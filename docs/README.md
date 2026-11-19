# The docs – where to look

Two kinds of text live here. **For contributors**: what the app must do, how it is built and why. **Internal working
notes**: the to-do list and hand-over notes of the sessions that build OnPath day by day – open to read, but not needed
to contribute, and they change every hour.

## For contributors

| You want to … | Read |
| --- | --- |
| know what the app must do | [requirements.md](requirements.md) (stable IDs like `F-PLAN-04`), [core-features.md](core-features.md) + [core-features/](core-features/) (what must never break, each with its test) |
| understand the code's shape | [architecture-contract.md](architecture-contract.md) (modules, who may call whom), [design/architecture-plan-2026-11.md](design/architecture-plan-2026-11.md) (the rebuild and its phases) |
| build and test | the root [README](../README.md) (build from source), [CLAUDE.md](../CLAUDE.md) (`tools/ci/gate.sh` before a push), [release-rundgang.md](release-rundgang.md), [play-build.md](play-build.md) |
| change the screens or texts | [ux-two-modes.md](ux-two-modes.md), [design/ui-style.md](design/ui-style.md), [i18n.md](i18n.md) (English + German) |
| work on the map | [map-pipeline.md](map-pipeline.md) (from OSM to the files on the phone), [map-style-notes.md](map-style-notes.md), [poi-integration.md](poi-integration.md), [poi-categories.md](poi-categories.md), [design/map-hosting.md](design/map-hosting.md) (the map server, also self-hosted) |
| work on routing and navigation | [plan-auto-reroute.md](plan-auto-reroute.md), [plan-time-estimate.md](plan-time-estimate.md), [design/bike-routing.md](design/bike-routing.md), [design/voice-guidance.md](design/voice-guidance.md) |
| work on the browser planner | [design/web-planner.md](design/web-planner.md), [design/web-sync.md](design/web-sync.md) |
| see why a feature is the way it is | [design/](design/) – one page per topic: the decision, what was measured, what was dropped |
| read what a release brought | [release-notes/](release-notes/) and the GitHub releases |

A change that touches a core feature needs a test that shows it; a field bug first gets a test that reproduces it.
Questions and ideas: the GitHub discussions and issues of this repository.

## Internal working notes

OnPath is built by several Claude Code sessions working side by side for the owner. Their shared memory is in the
repository, so it is public – but it is a workbench, not documentation:

| What | Where |
| --- | --- |
| the to-do list, claims and messages between the sessions | [backlog.md](backlog.md), detail per item in [items/](items/) |
| where things stand, the last hand-over | [project-status.md](project-status.md), longer hand-overs in [history/](history/) |
| how the sessions work together, who does what | [working-agreement.md](working-agreement.md), [roles/](roles/), [owner-preferences.md](owner-preferences.md) |

Finished work is not kept there: the git history and the releases hold it. Trials that are in no build live in
[`experiments/`](../experiments/README.md) at the top of the repository.

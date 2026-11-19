# Experiments

Trials and measurements that are **in no build**: nothing here is compiled into the app or the planner, no CI job and no
script under `tools/` reads it. Each folder has a page that says what was learned.

| Folder | What it was | Result |
| --- | --- | --- |
| `spike-routing-kmp/` | BRouter's routing core as pure Kotlin (JVM + native), a Gradle build of its own | `docs/design/routing-kmp-spike.md` |
| `style_analysis/` | the colour maths behind the route green on the map | `docs/design/map-colour-analysis.md` |

`tools/suggest-poc/` (tour suggestions) is not here on purpose: its `settings.json` and fixtures are still read by the
tests of `core/` and `app/`.

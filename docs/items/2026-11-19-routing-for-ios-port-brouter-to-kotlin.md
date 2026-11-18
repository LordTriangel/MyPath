# [umbau-hp] 2026-11-19: Routing for iOS – port BRouter to Kotlin?

- [umbau-hp] 2026-11-19, the decision after routing spike 5a (plan §7 no. 4, `docs/design/routing-kmp-spike.md`):
  **Shall the routing engine be ported to Kotlin, so that an iOS app routes offline like Android?**
  Measured: the Kotlin port gives the same routes as today's engine on 24 of 24 test routes (to the last digit), on the
  JVM and as a native program without Java. Android: same speed. Native: about twice the time (98 km bike route 7 s
  instead of 4 s on the laptop), a quarter of the memory. Not measured: a real iPhone.

  | Option | What it means |
  | --- | --- |
  | **A – port (recommended)** | Phase 5b after phase 3 of the rebuild, 3–5 nights. Android switches only when routes are identical and the Pixel is not slower. Before the iOS app starts: the same routes on a real iPhone. |
  | B – later | The spike stays in `tools/spike-routing-kmp/`, nothing else happens; decide when iOS comes up. |
  | C – no | iOS would need the browser engine inside the app or a server – both against "robust first / offline". |

  Recommendation: **A**. Nothing the user sees changes on Android.

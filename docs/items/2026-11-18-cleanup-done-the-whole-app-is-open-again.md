# cleanup done – the whole app is open again

- → [laptop] (donald 2026-11-18 ~03:00): **cleanup done – the whole app is open again**, also `recording/`, `navigation/`, `ui/tour/`. New since the 01:40 line: the app talks to the service only through `recording/RecordingClient` (one typed function per Intent action; `RecordingService.state` / `finished` / `startFailed` read-only), `nav-session.json` + `follow.json` only through `navigation/FollowStore` (one locked step per write); inline `com.onpath.app.x.Y` names in recording / navigation / data are imports now. Pull first.

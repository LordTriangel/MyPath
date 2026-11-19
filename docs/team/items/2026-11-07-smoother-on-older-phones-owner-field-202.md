# [donald] Smoother on older phones (owner field 2026-11-07, Galaxy S10, via cloud-web)

- **[donald] Smoother on older phones (owner field 2026-11-07, Galaxy S10, via cloud-web)** – "runs well, a little
  hackelig". The S10 has a fixed 60 Hz screen, so it is not the refresh rate. Measure first: `FrameJankMeter` today only
  covers the plan-sheet drag – add map pan / zoom, Navigate (route following) and the Meine Touren list with route
  pictures; numbers from the S10-class device (or the Pixel with the GPU slowed) into the handoff. Then try what helps
  most, one at a time with numbers: Relief and Höhenlinien off while the map moves, the pre-rendered relief raster
  instead of live hillshade from the DEM, fewer path / trail layers below z13, MapLibre pixel ratio / max FPS, Compose
  recompositions on every camera move, route pictures in Meine Touren drawn once and cached. If one setting makes the
  big difference, offer it as "Leichte Karte (ältere Handys)" in Einstellungen → Karte, or switch it by itself.
  **Device (owner 2026-11-07):** the owner can plug an older Samsung phone into donald's USB instead of the Pixel – it takes the
  `pixel` slot of `tools/dev/device.sh` (`adb -d`, only one phone on USB). It runs the **release** build: a debug APK
  has the same package id but another signature, so `adb install` fails and uninstalling would delete his tours – never
  uninstall; measure with the installed release, or install only a release-signed APK.
  Start without a new build: `adb -d shell dumpsys gfxinfo com.onpath.app reset`, then map pan / zoom, Navigate, Meine
  Touren, then `dumpsys gfxinfo com.onpath.app` (janky frames, percentiles) – once with Relief + Höhenlinien on, once off.
  Save tokens: numbers, not screenshots; result as a short table in the handoff.

# [laptop] Pairing from a phone camera ends in the browser (owner field 2026-11-07, Galaxy S10, via cloud-web)

- **[laptop] Pairing from a phone camera ends in the browser (owner field 2026-11-07, Galaxy S10, via cloud-web)** – the
  owner scanned the pairing QR with the phone's own camera: Samsung's camera opens the link in the browser, not in OnPath
  (App Link and `assetlinks.json` are fine – checked live). Two fixes: (1) `web_pair_menu_path` still says "☰ → Mit Browser
  verbinden", the app's menu says **"Mit Browser koppeln"** (EN "Pair with browser") – the dialog should say plainly
  "Scan with OnPath, not with the camera app"; (2) when a `#pair=` link opens in a **phone** browser, don't start the
  browser join – show "Open in OnPath" (an `intent://…#Intent;package=com.onpath.app;…` link) and the in-app way.

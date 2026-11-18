# [donald] Handy einrichten: "Links vom Planer in OnPath öffnen" as a check (owner field 2026-11-07, Galaxy S10, via cloud-web)

- **[donald] Handy einrichten: "Links vom Planer in OnPath öffnen" as a check (owner field 2026-11-07, Galaxy S10, via
  cloud-web)** – pairing / plan links from plan.onpath.earth opened in the browser on the S10; the owner fixed it by hand
  (Einstellungen → Apps → OnPath → Standardmäßig öffnen → "Unterstützte Links öffnen"). Owner asks to build it into the
  setup. A new `CheckId` (e.g. `LINKS`, RECOMMENDED, `docs/design/onboarding-phone-check.md`): Android 12+ –
  `DomainVerificationManager.getDomainVerificationUserState(packageName)`: plan.onpath.earth VERIFIED or SELECTED → ✓;
  else a short line ("Links vom Planer öffnen sich sonst im Browser") and a button that opens
  `Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS` (`package:com.onpath.app`); back in the app the check is read again.
  Below 12: no check (verified at install). Server side checked live: `assetlinks.json` matches the release key – so also
  find out why the S10 was not verified (other signature? no network at install?), say in the handoff.

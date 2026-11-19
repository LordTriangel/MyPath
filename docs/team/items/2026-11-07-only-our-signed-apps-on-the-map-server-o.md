# [donald] Only our signed apps on the map server? (owner 2026-11-07, chat)

- **[donald] Only our signed apps on the map server? (owner 2026-11-07, chat)** – App Links need no per-phone step
  (one fingerprint per signing key: GitHub test key + Play App Signing key, both in `web/host` assetlinks). Server
  access cannot be tied to our signature without Google Play Integrity (needs Play Services – against the "no Google
  Play Services" rule – and locks out GitHub / F-Droid builds); a key in the APK or an in-app signature check is easy to
  strip from a rebuild. Recommendation: per-phone / per-IP quotas, rotatable key, supporter code
  (`docs/design/map-access.md`); Play Integrity only for building new cells if abuse shows up. Owner decides.

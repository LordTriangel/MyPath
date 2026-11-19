# Docs pictures follow the UI by themselves (owner 2026-11-03)

- **Docs pictures follow the UI by themselves (owner 2026-11-03)** – three parts:
  (1) **[cloud-web]** make `docs/design/website-screens.md` the full shot list: every picture on onpath.earth (home,
  help DE + EN, planner pages) and in the store listing, with key, file names DE · EN, what exactly it shows (state,
  activity, route, scroll position, open sheet / menu, what is blurred), the fixed scenario (emulator, Demo ride Vellau →
  Leiteralm / A-019 setup) and the app files it depends on. Planner pictures with their `ui-shots` / `pair-shots` name.
  (2) **[donald]** `tools/ci/gate.sh` prints "Bilder betroffen: <keys>" when changed files match the list's last
  column; the unit re-shoots those keys while checking its change (`tools/dev/website_shot.sh <key>`, DE + EN) – for a
  big round after its review – and drops the PNGs + one line each (key, app version, date) into
  `onpath.private/marketing/bilder/aktuell/`. The same rule in `.claude/rules/ui.md`.
  (3) **[captain]** a daily cloud run (after the 05:47 planner sync) takes new files from `bilder/aktuell/` into
  onpath.earth (help + home, `scripts/help_shots.py`), builds, checks links, pushes, moves the used PNGs to
  `bilder/eingebaut/<date>/` and reports in one line; set up 2026-11-03.

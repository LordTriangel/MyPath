#!/usr/bin/env python3
"""The frozen POC's reference outputs for the Kotlin port (stage 0 of the build plan, see FROZEN.md).

    SUGGEST_FILE=1 SUGGEST_FILES=~/onpath-suggest-poc/suggest-file/cells-c2 \
    python3 tools/suggest-poc/reference.py core/src/test/resources/suggest/reference       the wishes kept in git
    python3 tools/suggest-poc/reference.py ~/onpath-suggest-poc/reference --all          every case start too

Run with SUGGEST_FILE=1 and the suggest files the port reads (content 2, with the AIMS section): then the POC takes
everything – land, names, aims, official loops – from the same files as the Kotlin core, as phone and planner will.
    python3 tools/suggest-poc/reference.py <out dir> hilden     only wishes whose name contains one of the words

Per wish one `<name>.json.gz`: the wish as the method got it, the place's yardstick and way out, and every loop of the
pool (ours and the official loops near the start, as on the page) with its line (coordinates, lon / lat, 6 decimals),
origin, the four raw place values, the five axes, points, the reason a hard rule dropped it, whether it was chosen,
and its numbers. The port must give the same pool, axes, points and choice for the same wish and map data.
Wishes: the starts of `cases.py`, a few more with a Ziel (EXTRA) and five official loops' wishes (3 NRW, 2 RLP) as
`official.wish_for` makes them.
"""
import gzip
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cases  # noqa: E402
import official  # noqa: E402
import suggest as S  # noqa: E402
from character import wish as make_wish  # noqa: E402

# the official loops' wishes (name, loops file, OSM relation id): NRW hike / hike / bike, RLP hike (Traumpfad) / bike (road-heavy)
OFFICIAL = [("nrw-doerenther-klippen", "nrw", 11372986), ("nrw-monschau", "nrw", 12104493), ("nrw-rad-naafbachtal", "nrw", 12244602),
            ("rlp-saynsteig", "", 1689964), ("rlp-rad-altenkirchen", "", 8208770)]
# the case starts kept in git (small enough: hikes ~50 KB, bikes ~250 KB gzip): two hikes in Hilden, a bike in
# Nordkirchen, the Rhine at Zons, a flat wish in the mountains, a low ascent maximum in the hills
IN_GIT = ("hilden-wandern", "heide-wandern", "nordkirchen-rad-3h", "zons-rad-3h", "meran-flach",
          # the batch of 2026-11-17: official loops from the start, the Ziel, the aims in the mountains (dhuenn-rad-350
          # grew to 0.75 MB with the aims: outside git now, ~/onpath-suggest-poc/reference/)
          "nordkirchen-rad-kurz", "elbsee-wasser", "mittenwald-wasser", "garmisch-gipfel", "mittenwald-einkehr",
          "mittenwald-gipfel", "hilden-kultur")
# wishes with a Ziel beyond the cases (the batch port of 2026-11-17): one per kind of Ziel not in cases.py
EXTRA = [("mittenwald-gipfel", {"start": [11.26143, 47.44178], "hours": 3, "ziel": ["gipfel"]}),
         ("hilden-kultur", {"start": [6.9395, 51.168], "hours": 2, "ziel": ["kultur", "aussicht"]}),
         ("heide-einkehr", {"start": [6.9866, 51.1645], "hours": [2, 3], "ziel": ["einkehr"]})]
KEEP = ("id", "origin", "chosen", "out", "score", "axes", "raw", "hours", "up", "m", "doubled_m", "stem_m", "cross", "coords",
        "numbers", "name", "official", "reached", "water_goal")


def official_wishes(st):
    out = []
    for name, region, osm in OFFICIAL:
        with open(os.path.join(official.OUT, "loops-%s.json" % region if region else "loops.json"), encoding="utf-8") as f:
            every = json.load(f)
        loop = next(lp for lp in every if lp["id"] == osm)
        w, _ = official.wish_for(loop, st)
        if w:
            out.append((name, dict(w, start_name=loop["name"][:60])))
    return out


def main():
    every = "--all" in sys.argv
    dest, words = sys.argv[1], [a for a in sys.argv[2:] if not a.startswith("--")]
    os.makedirs(dest, exist_ok=True)
    st = S.load_settings()
    wishes = [(name, make_wish(body, st)) for name, body, _, _ in cases.CASES if every or name in IN_GIT]
    wishes += [(name, make_wish(body, st)) for name, body in EXTRA if every or name in IN_GIT] + official_wishes(st)
    for name, w in wishes:
        if words and not any(x in name for x in words):
            continue
        r = S.suggest(w, st, official.near(w, st))
        cands = [{k: c[k] for k in KEEP if k in c} for c in r["candidates"]]
        doc = {"name": name, "version": r["version"], "wish": r["wish"], "hours": r["hours"], "ascent": r["ascent"],
               "character": r["character"], "place": r["place"], "notes": r["notes"], "candidates": cands}
        with gzip.open(os.path.join(dest, name + ".json.gz"), "wt", encoding="utf-8") as f:
            json.dump(doc, f, ensure_ascii=False, separators=(",", ":"))
        print("%-24s %3d Runden, %d gezeigt, erste %s" % (name, len(cands), sum(c["chosen"] for c in cands),
                                                          next((c["name"] for c in cands if c["chosen"]), "–")), flush=True)


if __name__ == "__main__":
    main()

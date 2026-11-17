#!/usr/bin/env python3
"""The owner's comments from the page, each with the route it belongs to.

  python3 tools/suggest-poc/feedback.py            # all comments, oldest first
  python3 tools/suggest-poc/feedback.py --last 5   # the newest five

The page saves one line per comment to ~/onpath-suggest-poc/feedback.jsonl (run, route id, name, numbers, wish,
comment). Lines of older versions are read too; where a line lacks the route's numbers they come from the saved run.
"""
import json
import os
import sys

DATA = os.environ.get("SUGGEST_DATA", os.path.expanduser("~/onpath-suggest-poc"))


def line(d):
    w, n, name = d.get("wish"), d.get("numbers"), d.get("name")
    p = os.path.join(DATA, "runs", "%s.json" % d.get("run"))
    if (n is None or w is None or name is None) and os.path.isfile(p):
        with open(p, encoding="utf-8") as f:
            run = json.load(f)
        c = next((x for x in run["candidates"] if x["id"] == d.get("candidate")), {})
        w, n, name = w or run["wish"], n or c.get("numbers"), name or c.get("name")
    w, n = w or {}, n or {}
    taste = w.get("simple") or w  # older versions kept the three levels under "simple"
    # since 2026-11-17 the wish has a Ziel instead of the levels Natur / Ruhe / Bekannte Wege and "am Wasser"
    want = ("Ziel " + ", ".join(w["ziel"]) if w.get("ziel") else "ohne Ziel") if "ziel" in w else \
        "Natur %s · Ruhe %s · Bekannte Wege %s%s" % (taste.get("natur", "?"), taste.get("ruhe", "?"), taste.get("bekannt", "?"),
                                                    " · am Wasser" if taste.get("wasser") else "")
    out = ["%s · Lauf %s · Runde %s" % (d.get("at", "?"), d.get("run"), d.get("candidate")),
           "   Wunsch: %s · %s · %s h%s · %s" % (w.get("start_name") or w.get("start"), w.get("activity", "hike"), w.get("hours"),
                                                " · ↑ %s m" % w["ascent"] if w.get("ascent") else "", want)]
    if n:
        out.append("   Runde: %s · %s km · %s h · am Wald %s %% · durch Häuser %s %% · Straße %s %%" % (
            name, n.get("km"), n.get("hours"), n.get("forest_pct"), n.get("town_pct", n.get("built_pct", "?")), n.get("street_pct")))
    return "\n".join(out + ["   » " + (d.get("comment") or d.get("reason") or "")])


if __name__ == "__main__":
    p = os.path.join(DATA, "feedback.jsonl")
    if not os.path.isfile(p):
        sys.exit("noch keine Kommentare: " + p)
    with open(p, encoding="utf-8") as f:
        rows = [json.loads(x) for x in f if x.strip()]
    if "--last" in sys.argv:
        rows = rows[-int(sys.argv[sys.argv.index("--last") + 1]):]
    print("\n\n".join(line(d) for d in rows))
    print("\n%d Kommentare · %s" % (len(rows), p))

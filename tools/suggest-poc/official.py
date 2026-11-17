#!/usr/bin/env python3
"""Our loops against official circular routes (OpenStreetMap route=hiking / bicycle / mtb relations that are closed loops).

  <venv>/bin/python official.py extract <file.osm.pbf> [--bbox W,S,E,N]   (needs pyosmium; once)
  python3 official.py png --set base-traumpfade --name Saynsteig   pictures from a saved comparison (our first blue, official red)
  python3 official.py summary [--labels base,neu]   the sets' medians side by side -> official-compare/summary.json (the page's "Vergleich")
  python3 official.py compare [--kind hike|wheel] [--name Traumpfad] [--skip Traumpf] [--sample 24] [--max 25]
                              [--complete] [--set traumpfade] [--png Saynsteig,Vulkanpfad] [--loops nrw --pick nrw-wandern]

extract  keeps closed loops (hiking 4–20 km, cycling / MTB 15–60 km) whose ways chain up end to end, with name and network
         (no description texts: the cards carry our own line); the start is the loop point nearest a parking place
         -> official-compare/loops.json (the page uses it too: official loops near a start)
compare  per loop: a wish built from the loop (start; hike T2 or its own T level, a cycling route bike – gravel when less
         than half of it is paved –, an MTB route mtb; time ±15 %, ascent 60–130 %), suggest(),
         and the official loop laid onto our way network as one more pool member, judged with the same yardstick.
         Measures (a) how much of the official loop our first / any of our loops walk (25 m), (b) the five axes of
         both, (c) the plain numbers side by side -> official-compare/compare-partial.json (or -complete) and a table.
Everything goes to ~/onpath-suggest-poc/official-compare/ (SUGGEST_DATA). Not part of the method.
"""
import json
import re
import math
import os
import sys

import cells as CE
import suggest as S
from build_suggest import loops as osm_loops, square as cell, squares_around as around
from character import Character, wish as make_wish
from generate import assemble, measure
from net import climb, dijkstra, dist, load, path, runs_along, INF

OUT = os.path.join(S.DATA, "official-compare")
NEAR = 25.0  # metres: a point of one loop counts as walked by the other within this distance


# ---------------------------------------------------------------- extract (pyosmium)

KM = {"hike": (4.0, 20.0), "bicycle": (15.0, 60.0), "mtb": (15.0, 60.0)}
PARKING = 250.0  # metres: a parking place this near the loop's start counts as "has parking"


def as_loop(lp):
    """A loop as build_suggest.loops finds it / a suggest file has it -> as the comparison and the page use it."""
    return {"id": lp["id"], "kind": lp["kind"], "name": lp["name"], "ref": lp["ref"], "network": lp["network"],
            "operator": lp.get("operator", ""), "km": round(lp["m"] / 1000.0, 2),
            "parking": lp["parking_m"] is not None and lp["parking_m"] < PARKING, "t": lp["t"], "coords": lp["coords"]}


def extract(pbf, bbox=None, region=""):
    loops = [as_loop(lp) for lp in osm_loops(pbf, KM, bbox, log=print)]
    os.makedirs(OUT, exist_ok=True)
    name = "loops-%s.json" % region if region else "loops.json"
    with open(os.path.join(OUT, name), "w", encoding="utf-8") as f:
        json.dump(loops, f, ensure_ascii=False)
    count = lambda k: sum(lp["kind"] == k for lp in loops)
    print("closed loops: %d hiking 4–20 km, %d cycling + %d MTB 15–60 km -> %s" % (
        count("hike"), count("bicycle"), count("mtb"), os.path.join(OUT, name)))


# ---------------------------------------------------------------- geometry helpers

def dense(coords, step=10.0):
    """Points every `step` metres along a line."""
    out = [coords[0]]
    for a, b in zip(coords, coords[1:]):
        n = max(1, int(dist(a, b) / step))
        out += [[a[0] + (b[0] - a[0]) * k / n, a[1] + (b[1] - a[1]) * k / n] for k in range(1, n + 1)]
    return out


def covered(pts, other):
    """Share of `pts` within NEAR metres of the line `other`."""
    grid = {}
    for q in dense(other):
        grid.setdefault(cell(q, NEAR), []).append(q)
    return sum(1 for p in pts if any(dist(p, q) <= NEAR for q in around(grid, p, NEAR))) / max(1, len(pts))


def trail(net, ok, coords):
    """The official loop on our way network: the cost of laying it on (edges that run along it cheap, others dear), the
    edges along it, and its turning points – the nodes of the ways along it in the loop's order, one per ~200 m."""
    pts = dense(coords)
    on = runs_along(net, pts, [ei for ei in range(len(net.edges)) if ok[ei]], NEAR)
    cost = [(e[2] if ei in on else 25.0 * e[2]) if ok[ei] else -1.0 for ei, e in enumerate(net.edges)]
    igrid, index = {}, {}
    for i, p in enumerate(pts):
        igrid.setdefault(cell(p, NEAR), []).append(i)
    for ei in on:
        for v in net.edges[ei][:2]:
            p = net.nodes[v]
            i = min(around(igrid, p, NEAR), key=lambda i: dist(p, pts[i]), default=None)
            if i is not None and dist(p, pts[i]) <= NEAR:
                index[v] = i
    return cost, on, index


def thin(index, start=None):
    """One turning point per ~200 m of the loop, in its order (not `start`)."""
    aims, last = [], -99
    for v in sorted(index, key=index.get):
        if index[v] - last >= 20 and v != start:
            aims.append(v)
            last = index[v]
    return aims


def follow(net, cost, start, aims):
    """From `start` through the turning points and home on the laid-on cost; a point our network reaches only by a long
    detour (a piece of the loop missing on our map) is skipped. -> (edges in walking order, the points kept) or None."""
    edges, cur, kept = [], start, []
    for w in aims + [start]:
        d, prev, pe, ln = dijkstra(net, cur, cost, stop=w)
        if d[w] == INF:
            return None
        if w != start and ln[w] > 4.0 * dist(net.nodes[cur], net.nodes[w]) + 1000.0:
            continue  # our network lacks a piece of it here: skip this point rather than walk a long detour
        edges += path(prev, pe, w)
        kept += [w] * (w != start)
        cur = w
    out = []  # a step to a turning point and straight back is not part of it
    for ei in edges:
        out.pop() if out and out[-1] == ei else out.append(ei)
    return out, kept


def lay_on(net, ok, coords, start=None):
    """The official loop on our way network: edges that run along it are cheap, others dear; the loop goes from `start`
    (default: our start) through a point every ~200 m of it and home. -> edges in walking order, or None."""
    start = net.start if start is None else start
    cost, _, index = trail(net, ok, coords)
    r = follow(net, cost, start, thin(index, start))
    return r and r[0]


def nodes_of(net, edges, start=None):
    out = [net.start if start is None else start]
    for ei in edges:
        out.append(net.other(ei, out[-1]))
    return out


def line_of(net, edges, start=None):
    node = net.start if start is None else start
    out = [list(net.nodes[node][:2])]
    for ei in edges:
        out += net.geom(ei, node)[1:]
        node = net.other(ei, node)
    return out


# ---------------------------------------------------------------- official loops near a start (the page)

_region = []
# jogging and fitness circuits are signed loops too, but no tour to suggest (Hilden: "Laufstrecke Reh")
NOT_A_TOUR = re.compile(r"laufstrecke|lauftreff|nordic[- ]?walking|trimm|finnenbahn|walking[- ]?strecke", re.I)


def loops_in_reach(start, reach):
    """The official loops to look at around a start: from the suggest files of the cells in reach when every cell there
    that has map data has one with loops (a loop is in the file of the cell that holds most of it; the file keeps
    more lengths than we offer – KM), else from this computer's loops files."""
    files = [CE.suggest(*c) for c in CE.cells_for(start[0], start[1], reach) if CE.usable(*c)]
    if CE.file_only() or (files and all(sf and sf["loops"] is not None for sf in files)):
        return [as_loop(lp) for sf in files if sf for lp in sf["loops"] or ()
                if KM[lp["kind"]][0] <= lp["m"] / 1000.0 <= KM[lp["kind"]][1] and not NOT_A_TOUR.search(lp["name"])]
    return region_loops()


def region_loops():
    """Every official loop this computer knows: the loops files in official-compare/ (one per extracted region;
    Rheinland-Pfalz, Nordrhein-Westfalen)."""
    if not _region:
        import glob
        for p in sorted(glob.glob(os.path.join(OUT, "loops*.json"))):
            with open(p, encoding="utf-8") as f:
                _region.extend(lp for lp in json.load(f) if not NOT_A_TOUR.search(lp["name"]))
    return _region


FAMILY = ("Traumpfädchen", "Traumpfad", "Saar-Hunsrück-Traumschleife", "Nationalpark-Traumschleife", "Traumschleife",
          "MTB-Park Pfälzerwald")
NETWORK = {"hike": "Rundwanderweg", "bicycle": "Radrundweg", "mtb": "MTB-Rundtour"}


def badge(loop):
    """'Traumpfad' for a Traumpfad, else what kind of official loop it is."""
    return next((f for f in FAMILY if loop["name"].startswith(f)), NETWORK[loop.get("kind", "hike")])


def near(wish, S):
    """extra(P) for suggest(): the official loops whose every point lies inside the loaded network and that are of the
    wish's kind (on foot: hiking loops; by bike: cycling loops, MTB loops for mtb), each as a ride FROM the wished start
    (owner 2026-11-17: the wished start is where one starts): the cheapest way to the loop's best joining point (its
    turning point cheapest to reach by our search cost, not necessarily its parking place), once round in its own
    order (either direction, the shorter kept; a ride meeting its own way more than allowed is dropped, as our own
    loops are), home from there – `assemble` with the loop's turning points, the loop's
    parts on the laid-on cost, the way there and home as for our own loops (the same way back allowed, a different one
    preferred: its stem is judged like any loop's way out). Time, km, ascent and axes are those of the whole ride."""
    kinds = {"hike": ("hike",), "bike": ("bicycle",), "gravel": ("bicycle", "mtb"), "mtb": ("mtb", "bicycle")}[wish["activity"]]
    start = wish["start"]

    def extra(P):
        reach = 0.95 * min(P.ch.family["reach_max_m"], max(1500.0, S["search"]["radius"] * P.ch.max_m))
        out = []
        for lp in loops_in_reach(start, reach):
            if lp.get("kind", "hike") not in kinds or not all(dist(start, p) <= reach for p in lp["coords"][::5]):
                continue
            cost, on, index = trail(P.net, P.F.ok, lp["coords"])
            seq = [v for v in thin(index) if P.d0[v] < INF]
            if len(seq) < 3:
                continue
            j = min(range(len(seq)), key=lambda k: P.d0[seq[k]])  # the best joining point
            r = follow(P.net, cost, seq[j], seq[j + 1:] + seq[:j])
            if not r or len(r[1]) < 2:
                continue
            best = None
            for kept in (r[1], r[1][::-1]):  # round in the loop's order, either direction
                points = [seq[j]] + kept + [seq[j]]
                edges = assemble(P, S, points, {i: cost for i in range(1, len(points))})
                m = measure(P, edges) if edges else None
                # like our own loops: a ride that meets its own way more often than allowed is not offered
                if m and m["cross"] <= S["form"]["max_cross"] and (best is None or m["m"] < best["m"]):
                    best = m
            best = best and best["edges"]
            if best and covered(dense(lp["coords"]), line_of(P.net, best)) >= 0.8:
                out.append({"edges": best, "origin": "offiziell", "aim": [lp["name"]], "show": True,
                            "official": {"name": lp["name"], "badge": badge(lp), "osm": lp["id"],
                                         "loop_km": round(sum(P.F.ln[e] for e in best if e in on) / 1000.0, 1)}})
        return out
    return extra


# ---------------------------------------------------------------- compare

def wish_for(loop, st):
    """The wish a person would make for this loop: its start and activity, its time ±15 %, its ascent 60–130 %."""
    act = {"hike": "hike", "bicycle": "bike", "mtb": "mtb"}[loop.get("kind", "hike")]
    w = make_wish({"start": loop["coords"][0], "start_name": loop["name"][:60], "activity": act, "t_level": max(2, loop["t"]),
                   "hours": [1, 2]}, st)
    ch = Character(w, st)
    S.ensure_service()
    reach = 300.0 + max(dist(w["start"], p) for p in loop["coords"])
    net = load(S.SERVICE, ch.profile, ch.kv, w["start"], reach, ch.family["coarse"])
    edges = lay_on(net, [min(e[3], e[4]) < 9999 for e in net.edges], loop["coords"])
    if not edges:
        return None, None
    if act == "bike":  # a cycling route on mostly unpaved ways is a gravel ride
        from facts import of_tags
        paved = sum(net.edges[e][2] for e in edges if of_tags(net.tags[net.edges[e][5]], st["facts"], True)[1] == 0)
        if paved < 0.5 * sum(net.edges[e][2] for e in edges):
            w = dict(w, activity="gravel", pace=st["activities"]["gravel"]["pace"])
            ch = Character(w, st)
    up = climb(net, nodes_of(net, edges))
    h = ch.time(1000.0 * loop["km"], up)
    w["hours"] = [round(0.85 * h, 2), round(1.15 * h, 2)]
    w["ascent"] = [round(0.6 * up, -1), round(1.3 * up + 20.0, -1)]
    return w, up


def compare_one(loop, st):
    w, _ = wish_for(loop, st)
    if not w:
        return None
    lay = {}

    def extra(P):
        edges = lay_on(P.net, P.F.ok, loop["coords"])
        if not edges:
            return []
        lay["fit"] = covered(dense(loop["coords"]), line_of(P.net, edges))
        return [{"edges": edges, "origin": "offiziell", "aim": [loop["name"]]}]
    r = S.suggest(w, st, extra)
    off = next((c for c in r["candidates"] if c["origin"] == "offiziell"), None)
    ours = [c for c in r["candidates"] if c["chosen"]]
    if not off or not ours:
        return None
    pts = dense(loop["coords"])
    return {"id": loop["id"], "name": loop["name"], "km": loop["km"], "parking": loop["parking"], "wish": w, "run": r["run"],
            "fit": round(lay["fit"], 2), "first": round(covered(pts, ours[0]["coords"]), 2),
            "best": round(max(covered(pts, c["coords"]) for c in ours), 2),
            "pool_best": round(max(covered(pts, c["coords"]) for c in r["candidates"] if c is not off), 2),
            "official": pick(off), "ours": pick(ours[0]), "shown": len(ours),
            "official_rank": 1 + sum(1 for c in r["candidates"] if c is not off and not c["out"] and c["score"] > off["score"]),
            "result": r}


def pick(c):
    n = c["numbers"]
    return {"score": c["score"], "axes": {k: round(100 * v) for k, v in c["axes"].items()}, "out": c["out"],
            "km": n["km"], "up": n["ascent"], "forest": n["forest_pct"], "deep": n["deep_pct"], "town": n["town_pct"],
            "street": n["street_pct"], "road": n["road_longest_m"], "signed": n["signed_pct"], "water_km": n["water_km"],
            "ground": n["ground_pct"], "doubled": n["doubled_pct"], "cross": n["self_cross"],
            "reserves": [x[0] for x in n["reserves"]], "known": [x[0] for x in n["known"]], "sights": n["sights"], "rim_km": n["rim_km"],
            "origin": c["origin"], "name": c.get("name", ""), "coords": c["coords"]}


def picture(row, tag="vs"):
    """One map: our first loop (blue) and the official loop (red, dashed), from a run file made for it."""
    import run
    if "result" not in row:  # a row of a saved comparison: its run is in runs/
        with open(os.path.join(S.DATA, "runs", row["run"] + ".json"), encoding="utf-8") as f:
            row = dict(row, result=json.load(f))
    r = dict(row["result"], run=row["run"] + "-" + tag)
    off = next(c for c in r["candidates"] if c["origin"] == "offiziell")
    first = next(c for c in r["candidates"] if c["chosen"])
    off = dict(off, chosen=True, name="Offiziell: " + row["name"], reasons=["%.1f km" % row["km"]], hints=[], why="", line="")
    r["candidates"] = [first, off]
    with open(os.path.join(S.DATA, "runs", r["run"] + ".json"), "w", encoding="utf-8") as f:
        json.dump(r, f, ensure_ascii=False)
    return run.pictures(r["run"], 0, vs=True)


SETS = {"traumpfade": "Traumpfade Mayen–Koblenz", "traumschleifen": "Traumschleifen Saar-Hunsrück",
        "wheel": "Rad- und MTB-Rundwege Rheinland-Pfalz", "andere": "andere Rundwanderwege Rheinland-Pfalz",
        "nrw-wandern": "bekannte Rundwanderwege Nordrhein-Westfalen", "nrw-rad": "Rad- und MTB-Rundwege Nordrhein-Westfalen"}
# hand-picked comparison sets (OSM relation ids) from loops-<region>.json: well-known loops over different landscapes,
# no jogging circuits, no tiny village loops
PICKS = {
    "nrw-wandern": [71391, 11898298, 11230849, 11510957, 13525574, 12104493, 9644622, 970089,   # Eifel, Rureifel, Monschau
                    14865493, 14482475, 11659767, 2581603,                                    # Siebengebirge, Sieg
                    112280, 537638, 14977732, 12130356,                                       # Bergisches Land, Neanderland
                    9857315, 3538592, 10307518,                                               # Sauerland, Rothaar
                    1018916, 18906942, 8457628,                                               # Niederrhein
                    11372986, 4054617, 252131],                                               # Teutoburger Wald, Egge
    "nrw-rad": [31665, 11930753, 12244602, 2736292, 2577631, 6594500, 17354714, 2092950, 13641942, 2946192, 13898849,
                14378818, 3032109, 3321835, 11332858]}


def medians(rows):
    med = lambda k: sorted(r[k] for r in rows)[len(rows) // 2]
    return {"n": len(rows), "first": round(100 * med("first")), "is_first": sum(r["first"] >= 0.8 for r in rows),
            "shown": round(100 * med("best")), "pool": round(100 * med("pool_best")), "rank": med("official_rank"),
            "off_top": sum(r["official_rank"] == 1 for r in rows)}


def summary(labels):
    """The comparison sets' medians per version (label) and map (today / complete) -> summary.json and a table."""
    out = {"sets": []}
    for key, title in SETS.items():
        rows = []
        for label in labels:
            for mode in ("partial", "complete"):
                p = os.path.join(OUT, "compare-%s-%s-%s.json" % (label, key, mode))
                if os.path.isfile(p):
                    with open(p, encoding="utf-8") as f:
                        rows.append(dict(medians(json.load(f)), label=label, map="heute" if mode == "partial" else "vollständig"))
        if rows:
            out["sets"].append({"key": key, "title": title, "rows": rows})
            print("%s (%d Runden)" % (title, rows[0]["n"]))
            print("  %-10s %-12s %6s %6s %8s %6s %6s %6s" % ("Stand", "Karte", "erste", "≥80%", "gezeigt", "Pool", "Rang", "vorn"))
            for r in rows:
                print("  %-10s %-12s %5d%% %6d %7d%% %5d%% %6d %6d" % (r["label"], r["map"], r["first"], r["is_first"], r["shown"],
                                                                      r["pool"], r["rank"], r["off_top"]))
    with open(os.path.join(OUT, "summary.json"), "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)


def main():
    import argparse
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("what", choices=("extract", "compare", "summary", "png"))
    ap.add_argument("--labels", default="base,neu")
    ap.add_argument("pbf", nargs="?")
    ap.add_argument("--bbox", type=lambda s: [float(x) for x in s.split(",")])
    ap.add_argument("--kind", default="hike", choices=("hike", "wheel", "all"))
    ap.add_argument("--name", default="", help="only loops whose name contains one of these (comma-separated)")
    ap.add_argument("--skip", default="", help="no loops whose name contains one of these (comma-separated)")
    ap.add_argument("--sample", type=int, default=0, help="this many loops picked at random (always the same ones)")
    ap.add_argument("--set", default="", help="name of the comparison set: results go to compare-<set>-<partial|complete>.json")
    ap.add_argument("--max", type=int, default=25)
    ap.add_argument("--region", default="", help="extract: write loops-<region>.json (the page reads every loops*.json)")
    ap.add_argument("--loops", default="", help="compare: take the loops from loops-<this>.json (e.g. nrw)")
    ap.add_argument("--pick", default="", choices=("",) + tuple(PICKS), help="compare: a hand-picked set of loops (any kind)")
    ap.add_argument("--png", default="", help="a picture (official + our first) for the loops whose name contains one of these")
    ap.add_argument("--complete", action="store_true",
                    help="as if our map carried every circular route whole (today a way keeps only its most important route)")
    a = ap.parse_args()
    if a.what == "extract":
        return extract(a.pbf, a.bbox, a.region)
    if a.what == "summary":
        return summary(a.labels.split(","))
    if a.what == "png":
        with open(os.path.join(OUT, "compare-%s-partial.json" % a.set), encoding="utf-8") as f:
            for row in json.load(f):
                if any(n.lower() in row["name"].lower() for n in a.name.split(",")):
                    print("\n".join(picture(row, a.set.split("-")[0])))
        return
    st = S.load_settings()
    with open(os.path.join(OUT, "loops-%s.json" % a.loops if a.loops else "loops.json"), encoding="utf-8") as f:
        every = json.load(f)
    has = lambda lp, words: any(n.lower() in lp["name"].lower() for n in words.split(","))
    kind = lambda lp: a.kind == "all" or (lp.get("kind", "hike") == "hike") == (a.kind == "hike")
    loops = [lp for lp in every if kind(lp) and (not a.name or has(lp, a.name)) and not (a.skip and has(lp, a.skip))]
    if a.pick:
        loops = [lp for i in PICKS[a.pick] for lp in every if lp["id"] == i]
    if a.sample:
        import random
        loops = random.Random(1).sample(loops, min(a.sample, len(loops)))
    if a.complete:
        import named
        partial = named.load

        def complete(start, reach_m, bike, ranks):
            whole = [{"kind": "route", "name": lp["name"], "rank": ranks["l"], "dense": dense(lp["coords"], 25.0)}
                     for lp in every if lp["name"] and (lp.get("kind", "hike") != "hike") == bike and all(dist(start, p) <= reach_m for p in lp["coords"][::10])]
            names = {t["name"] for t in whole}
            return [t for t in partial(start, reach_m, bike, ranks) if t["name"] not in names] + whole
        named.load = complete
    rows = []
    for lp in loops[:a.max]:
        row = compare_one(lp, st)
        if not row:
            print("%-40s  nicht auf unser Wegenetz zu legen" % lp["name"][:40])
            continue
        S.save_run(row["result"])
        row["run"] = row["result"]["run"]
        rows.append(row)
        o, u = row["official"], row["ours"]
        print("%-40s %5.1f km lage %3d%%  erste %3d%% beste %3d%% pool %3d%% | off %5.1f N%3d R%3d W%3d B%3d F%3d Rang %2d%s | "
              "uns %5.1f N%3d R%3d W%3d B%3d F%3d" % (
                  lp["name"][:40], lp["km"], 100 * row["fit"], 100 * row["first"], 100 * row["best"], 100 * row["pool_best"],
                  o["score"], *(o["axes"][k] for k in ("natur", "ruhe", "weg", "besonderes", "form")), row["official_rank"],
                  " AUS" if o["out"] else "", u["score"], *(u["axes"][k] for k in ("natur", "ruhe", "weg", "besonderes", "form"))),
              flush=True)
    med = lambda k: sorted(r[k] for r in rows)[len(rows) // 2] if rows else 0
    print("%d Runden · Median: erste %d %% · beste der gezeigten %d %% · bester Pool %d %% · Rang der offiziellen %d · "
          "offizielle vorn %d · unsere erste ist sie (>= 80 %%) %d" % (
              len(rows), 100 * med("first"), 100 * med("best"), 100 * med("pool_best"), med("official_rank"),
              sum(r["official_rank"] == 1 for r in rows), sum(r["first"] >= 0.8 for r in rows)))
    keep = [{k: v for k, v in r.items() if k != "result"} for r in rows]
    with open(os.path.join(OUT, "compare-%s%s.json" % (a.set + "-" if a.set else "", "complete" if a.complete else "partial")), "w",
              encoding="utf-8") as f:
        json.dump(keep, f, ensure_ascii=False)
    for row in (r for r in rows if a.png and any(n.lower() in r["name"].lower() for n in a.png.split(","))):
        print("\n".join(picture(row)))


if __name__ == "__main__":
    sys.exit(main())

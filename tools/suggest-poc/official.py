#!/usr/bin/env python3
"""Our loops against official circular hiking routes (OpenStreetMap route=hiking relations that are closed loops).

  <venv>/bin/python official.py extract <file.osm.pbf> [--bbox W,S,E,N]   (needs pyosmium; once)
  python3 official.py compare [--name Traumpfad] [--max 25] [--complete] [--png Saynsteig,Vulkanpfad]

extract  keeps closed loops of 4–20 km whose ways chain up end to end; the start is the loop point nearest a parking
         place (else its first point) -> official-compare/loops.json
compare  per loop: a wish built from the loop (start, hike T2 or its own T level, time ±15 %, ascent 60–130 %), suggest(),
         and the official loop laid onto our way network as one more pool member, judged with the same yardstick.
         Measures (a) how much of the official loop our first / any of our loops walk (25 m), (b) the five axes of
         both, (c) the plain numbers side by side -> official-compare/compare-partial.json (or -complete) and a table.
Everything goes to ~/onpath-suggest-poc/official-compare/ (SUGGEST_DATA). Not part of the method.
"""
import json
import math
import os
import sys

import suggest as S
from character import Character, wish as make_wish
from net import climb, dijkstra, dist, load, path, runs_along, INF

OUT = os.path.join(S.DATA, "official-compare")
SAC = {"hiking": 1, "mountain_hiking": 2, "demanding_mountain_hiking": 3, "alpine_hiking": 4,
       "demanding_alpine_hiking": 5, "difficult_alpine_hiking": 6}
NEAR = 25.0  # metres: a point of one loop counts as walked by the other within this distance


# ---------------------------------------------------------------- extract (pyosmium)

def extract(pbf, bbox=None):
    import osmium
    rels, need = [], set()
    for r in osmium.FileProcessor(pbf, osmium.osm.RELATION):
        t = r.tags
        if t.get("type") == "route" and t.get("route") in ("hiking", "foot"):
            ways = [m.ref for m in r.members if m.type == "w" and m.role in ("", "main", "forward", "backward", "route")]
            if ways:
                rels.append({"id": r.id, "name": t.get("name", ""), "ref": t.get("ref", ""), "network": t.get("network", ""),
                             "operator": t.get("operator", ""), "ways": ways})
                need.update(ways)
    geo, sac, parking = {}, {}, []
    for o in osmium.FileProcessor(pbf, osmium.osm.NODE | osmium.osm.WAY).with_locations():
        if o.is_node():
            if o.tags.get("amenity") == "parking":
                parking.append((o.location.lon, o.location.lat))
        elif o.id in need or o.tags.get("amenity") == "parking":
            try:
                pts = [(n.lon, n.lat) for n in o.nodes]
            except osmium.InvalidLocationError:  # expected: a way at the edge of the extract
                continue
            if o.id in need:
                geo[o.id] = pts
                sac[o.id] = SAC.get(o.tags.get("sac_scale"), 1)
            if o.tags.get("amenity") == "parking":
                parking.append(pts[0])
    pgrid = {}
    for p in parking:
        pgrid.setdefault(cell(p, 300.0), []).append(p)
    loops = []
    for r in rels:
        ways = r.pop("ways")
        ring = chain([geo[w] for w in ways if w in geo])
        if not ring:
            continue
        km = sum(dist(a, b) for a, b in zip(ring, ring[1:])) / 1000.0
        if not 4.0 <= km <= 20.0 or (bbox and not all(bbox[0] <= p[0] <= bbox[2] and bbox[1] <= p[1] <= bbox[3] for p in ring)):
            continue
        best = (1e9, 0)
        for i, p in enumerate(ring):
            for q in around(pgrid, p, 300.0):
                best = min(best, (dist(p, q), i))
        k = best[1]
        ring = ring[k:-1] + ring[:k + 1]  # the loop starts and ends at the parking place
        loops.append(dict(r, km=round(km, 2), parking=best[0] < 250.0, t=max(sac.get(w, 1) for w in ways),
                          coords=[[round(x, 6), round(y, 6)] for x, y in ring]))
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, "loops.json"), "w", encoding="utf-8") as f:
        json.dump(loops, f, ensure_ascii=False)
    print("%d hiking relations, %d closed loops of 4–20 km -> %s" % (len(rels), len(loops), os.path.join(OUT, "loops.json")))


def chain(ways, gap=40.0):
    """The member ways end to end (nearest free end next, either direction) -> a closed ring of points, or None when
    they do not form one loop (a gap, a branch, an approach path)."""
    if not ways:
        return None
    ring, free = list(ways[0]), ways[1:]
    while free:
        tail, head = ring[-1], ring[0]
        d, i, flip, front = min((min((dist(tail, w[0]), i, False, False), (dist(tail, w[-1]), i, True, False),
                                     (dist(head, w[-1]), i, False, True), (dist(head, w[0]), i, True, True)))
                                 for i, w in enumerate(free))
        if d > gap:
            return None
        w = free.pop(i)
        w = w[::-1] if flip else w
        ring = (w + ring) if front else (ring + w)
    return ring if dist(ring[0], ring[-1]) <= gap else None


def cell(p, size):
    return int(p[0] * 111320.0 * math.cos(math.radians(p[1])) / size), int(p[1] * 111320.0 / size)


def around(grid, p, size):
    cx, cy = cell(p, size)
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            yield from grid.get((cx + dx, cy + dy), ())


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


def lay_on(net, ok, coords):
    """The official loop on our way network: edges that run along it are cheap, others dear; the loop goes from our
    start through a point every ~200 m of it and home. -> edges in walking order, or None."""
    pts = dense(coords)
    on = runs_along(net, pts, [ei for ei in range(len(net.edges)) if ok[ei]], NEAR)
    cost = [(e[2] if ei in on else 25.0 * e[2]) if ok[ei] else -1.0 for ei, e in enumerate(net.edges)]
    # turning points: the nodes of the ways along it, in the order of the official loop, one per ~200 m
    igrid, index = {}, {}
    for i, p in enumerate(pts):
        igrid.setdefault(cell(p, NEAR), []).append(i)
    for ei in on:
        for v in net.edges[ei][:2]:
            p = net.nodes[v]
            i = min(around(igrid, p, NEAR), key=lambda i: dist(p, pts[i]), default=None)
            if i is not None and dist(p, pts[i]) <= NEAR:
                index[v] = i
    aims, last = [], -99
    for v in sorted(index, key=index.get):
        if index[v] - last >= 20 and v != net.start:
            aims.append(v)
            last = index[v]
    edges, cur = [], net.start
    for w in aims + [net.start]:
        d, prev, pe, ln = dijkstra(net, cur, cost, stop=w)
        if d[w] == INF:
            return None
        if w != net.start and ln[w] > 4.0 * dist(net.nodes[cur], net.nodes[w]) + 1000.0:
            continue  # our network lacks a piece of it here: skip this point rather than walk a long detour
        edges += path(prev, pe, w)
        cur = w
    out = []  # a step to a turning point and straight back is not part of it
    for ei in edges:
        out.pop() if out and out[-1] == ei else out.append(ei)
    return out


def nodes_of(net, edges):
    out = [net.start]
    for ei in edges:
        out.append(net.other(ei, out[-1]))
    return out


def line_of(net, edges):
    out, node = [list(net.nodes[net.start][:2])], net.start
    for ei in edges:
        out += net.geom(ei, node)[1:]
        node = net.other(ei, node)
    return out


# ---------------------------------------------------------------- compare

def wish_for(loop, st):
    """The wish a person would make for this loop: its start, hike T2 (its own T level if higher), its time ±15 %, its ascent 60–130 %."""
    w = make_wish({"start": loop["coords"][0], "start_name": loop["name"][:60], "activity": "hike", "t_level": max(2, loop["t"]),
                   "hours": [1, 2]}, st)
    ch = Character(w, st)
    S.ensure_service()
    net = load(S.SERVICE, ch.profile, ch.kv, w["start"], 300.0 + max(dist(w["start"], p) for p in loop["coords"]))
    edges = lay_on(net, [min(e[3], e[4]) < 9999 for e in net.edges], loop["coords"])
    if not edges:
        return None, None
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
            "reserves": [x[0] for x in n["reserves"]], "known": [x[0] for x in n["known"]], "views": n["views"],
            "origin": c["origin"], "name": c.get("name", ""), "coords": c["coords"]}


def picture(row):
    """One map: the official loop (blue) and our first loop (pink), from a run file made for it."""
    import run
    r = dict(row["result"], run=row["run"] + "-vs")
    off = next(c for c in r["candidates"] if c["origin"] == "offiziell")
    first = next(c for c in r["candidates"] if c["chosen"])
    off = dict(off, chosen=True, name="Offiziell: " + row["name"], reasons=["%.1f km" % row["km"]], hints=[], why="")
    r["candidates"] = [off, first]
    with open(os.path.join(S.DATA, "runs", r["run"] + ".json"), "w", encoding="utf-8") as f:
        json.dump(r, f, ensure_ascii=False)
    return run.pictures(r["run"], 0)


def main():
    import argparse
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("what", choices=("extract", "compare"))
    ap.add_argument("pbf", nargs="?")
    ap.add_argument("--bbox", type=lambda s: [float(x) for x in s.split(",")])
    ap.add_argument("--name", default="", help="only loops whose name contains one of these (comma-separated)")
    ap.add_argument("--max", type=int, default=25)
    ap.add_argument("--png", default="", help="a picture (official + our first) for the loops whose name contains one of these")
    ap.add_argument("--complete", action="store_true",
                    help="as if our map carried every circular route whole (today a way keeps only its most important route)")
    a = ap.parse_args()
    if a.what == "extract":
        return extract(a.pbf, a.bbox)
    st = S.load_settings()
    with open(os.path.join(OUT, "loops.json"), encoding="utf-8") as f:
        every = json.load(f)
    loops = [lp for lp in every if not a.name or any(n.lower() in lp["name"].lower() for n in a.name.split(","))]
    if a.complete:
        import named
        partial = named.load

        def complete(start, reach_m, bike, ranks):
            whole = [{"kind": "route", "name": lp["name"], "rank": ranks["l"], "dense": dense(lp["coords"], 25.0)}
                     for lp in every if lp["name"] and all(dist(start, p) <= reach_m for p in lp["coords"][::10])]
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
    with open(os.path.join(OUT, "compare-%s.json" % ("complete" if a.complete else "partial")), "w", encoding="utf-8") as f:
        json.dump(keep, f, ensure_ascii=False)
    for row in (r for r in rows if a.png and any(n.lower() in r["name"].lower() for n in a.png.split(","))):
        print("\n".join(picture(row)))


if __name__ == "__main__":
    sys.exit(main())

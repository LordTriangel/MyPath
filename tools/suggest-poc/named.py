"""Named things from our own map areas: known signed routes, trail names, lakes, sights – and which way lies on them.

The same files the phone has (cells.py fetches them): overlays.pmtiles layer `routes` (kind hike / bike, name, ref,
net 4 international … 2 regional) and, where a cell has it, `routelines` (every signed route relation whole, also a
local one on the ways of a bigger one – every cell from schema 8; an older cell has only `routes`), `trailnames` (local
circular walks, promenades, Waalwege), base.pmtiles `water` + `water_name`, pois.geojson (sights: viewpoints, castles,
ruins, chapels, caves, waterfalls). Read once per map area, kept as attr-v7.json beside it.
"""
import json
import os
import re

import cells as CE
from build_suggest import label, named as _named
from net import dist

NET = {4: "i", 3: "n", 2: "r", 1: "l"}
SIGHTS = {"viewpoint": "Aussichtspunkt", "castle": "Burg", "ruins": "Ruine", "chapel": "Kapelle", "cave": "Höhle",
          "waterfall": "Wasserfall"}
NOT_A_LAKE = re.compile(r"becken|filter|klär|fang$|hafen", re.I)
_mem = {}


def read_cell(x, y):
    """One map area -> {routes: [{kind, label, net, lines, trail}], lakes: [{name, lines}], sights: [{name, what, p}]}:
    from the cell's suggest file where it has one, else from its tiles (kept as attr-v7.json beside them)."""
    words = lambda sights: [{"name": s["name"] or SIGHTS[s["kind"]], "what": SIGHTS[s["kind"]], "p": s["p"]} for s in sights]
    sf = CE.suggest(x, y)
    if sf:
        return {"routes": sf["routes"], "lakes": sf["lakes"], "sights": words(sf["sights"])}
    d = CE.folder(x, y)
    cache = os.path.join(d, "attr-v7.json")
    if os.path.isfile(cache):
        with open(cache, encoding="utf-8") as f:
            return json.load(f)
    got = _named(os.path.join(d, "overlays.pmtiles"), os.path.join(d, "base.pmtiles"), os.path.join(d, "pois.geojson"), x, y)
    out = {"routes": got["routes"], "lakes": got["lakes"], "sights": words(got["sights"])}
    tmp = "%s.%d" % (cache, os.getpid())  # written whole, then moved: runs side by side must never read half a file
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False)
    os.replace(tmp, cache)
    return out


def _dense(lines, step=25.0):
    out = []
    for line in lines:
        for a, b in zip(line, line[1:]):
            n = max(1, int(dist(a, b) / step))
            out += [[a[0] + (b[0] - a[0]) * i / n, a[1] + (b[1] - a[1]) * i / n] for i in range(n)]
        out.append(line[-1])
    return out


def _merged(cells, bike):
    key = (tuple(cells), bike)
    if key not in _mem:
        routes, lakes, sights = {}, {}, []
        for c in cells:
            if not CE.usable(*c):
                continue
            d = read_cell(*c)
            for r in d["routes"]:
                if r["kind"] == ("bike" if bike else "hike"):
                    g = routes.setdefault(label(r["label"], None), {"net": 0, "lines": [], "trail": True})
                    g["net"], g["trail"] = max(g["net"], r["net"]), g["trail"] and r.get("trail", False)
                    g["lines"] += r["lines"]
            for lk in d["lakes"]:
                lakes.setdefault(lk["name"], []).extend(lk["lines"])
            sights += d["sights"]
        real = [k.lower() for k, g in routes.items() if not g["trail"]]
        for k in [k for k, g in routes.items() if g["trail"] and any(k.lower() in r for r in real)]:
            del routes[k]  # a trail label that only repeats a real route
        _mem[key] = (routes, lakes, sights)
    return _mem[key]


def load(start, reach_m, bike, ranks):
    """The named things within reach_m of the start: [{kind: route | lake | sight, name, rank 0..1, dense: points}]."""
    routes, lakes, sights = _merged(CE.cells_for(start[0], start[1], reach_m), bike)
    out = []
    for name, g in routes.items():
        near = [p for p in _dense(g["lines"]) if dist(start, p) <= reach_m]
        if len(near) * 25.0 >= (1000.0 if g["trail"] else 25.0):
            out.append({"kind": "route", "name": name, "rank": ranks[NET.get(g["net"], "l")], "dense": near})
    for name, lines in lakes.items():
        near = [p for p in _dense(lines) if dist(start, p) <= reach_m]
        if len(near) * 25.0 >= 600.0 and not NOT_A_LAKE.search(name):
            out.append({"kind": "lake", "name": name, "rank": 1.0, "dense": near})
    out += [{"kind": "sight", "name": v["name"], "what": v["what"], "rank": 1.0, "dense": [v["p"]]} for v in sights
            if dist(start, v["p"]) <= reach_m]
    return out


def known_lines(start, reach_m, bike):
    """The known routes around the start as lines for the page's overlay: [{name, net, trail, lines}]."""
    out = []
    for name, g in _merged(CE.cells_for(start[0], start[1], reach_m), bike)[0].items():
        lines = [[[round(q[0], 5), round(q[1], 5)] for q in ln[::2] + ln[-1:]] for ln in g["lines"]
                 if dist(start, ln[0]) <= reach_m or dist(start, ln[-1]) <= reach_m]
        if lines:
            out.append({"name": name, "net": NET.get(g["net"], "l") + ("cn" if bike else "wn"), "trail": g["trail"], "lines": lines})
    return out


class Index:
    """Which named thing is a point on? A grid of the things' dense points (cells of ~30 m); lakes reach two cells
    further (a shore path often runs 50–100 m from the water)."""
    CX, CY = 0.00045, 0.00028

    def __init__(self, things):
        self.things, self.cells = things, {}
        for i, a in enumerate(things):
            pad = 2 if a["kind"] == "lake" else 0
            for p in a["dense"]:
                cx, cy = int(p[0] / self.CX), int(p[1] / self.CY)
                for dx in range(-pad, pad + 1):
                    for dy in range(-pad, pad + 1):
                        self.cells.setdefault((cx + dx, cy + dy), set()).add(i)

    def at(self, p, reach=1):
        cx, cy, hit = int(p[0] / self.CX), int(p[1] / self.CY), set()
        for dx in range(-reach, reach + 1):
            for dy in range(-reach, reach + 1):
                hit |= self.cells.get((cx + dx, cy + dy), set())
        return hit

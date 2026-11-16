"""What the land around a point is – nature, built-up land, open water – from our own map areas.

From our own map areas (base.pmtiles of a cell, zoom 12): the `landcover` polygons that are forest / heath / wetland
and the `park` polygons that are protected nature (nature reserve, national park), the `landuse` polygons that are
built-up (residential, commercial, industrial, retail, railway, schools …) and the `water` polygons (lake, river,
pond) are drawn into a grid of ~45 m per map area (cached beside the cell as nature-v<N>.bin + .json). The measure
for a point is then simply the share of grid squares of a kind within a radius around it (`around`):

  a path in the middle of a big forest or reserve  -> nature ~1.0
  a path along its edge                            -> ~0.5
  a green strip, a park between houses             -> 0.1 – 0.3
  a street inside a housing area                   -> built ~1.0 (a green corridor between houses: ~0)

So size and depth come out of one number, without joining polygons across tile borders. `around` also says in which
protected area a point lies; `place_near` gives the nearest named place (for naming an unnamed area: "Wald bei Kalstert").

The terrain model of the map area (terrain.pmtiles, Terrarium-encoded WebP, zoom 12) is sampled into the same grid
(height-v1.bin; decoding needs Pillow – without it, or without the file, there are simply no heights). `rim` says
whether a point is at an edge with a view: the ground within ~300 m on one side lies clearly lower and is open.
"""
import json
import math
import os
from array import array

import cells as CE
from build_suggest import BUILT, DLAT, DLON, FOREST, NO_HEIGHT, PROTECTED, WATER, WOOD, grid as _grid, heights as _heights  # noqa: F401

VERSION = 5

_mem = {}


def build(x, y):
    d = CE.folder(x, y)
    g = _grid(os.path.join(d, "base.pmtiles"), x, y)
    w, h, west, north, labels = g["w"], g["h"], g["west"], g["north"], g["labels"]
    for name, data in (("nature-v%d.bin" % VERSION, g["grid"] + g["area"]),
                       ("nature-v%d.json" % VERSION, json.dumps({"w": w, "h": h, "west": west, "north": north, "labels": labels,
                                                                "places": g["places"]}, ensure_ascii=False).encode("utf-8"))):
        tmp = os.path.join(d, "%s.%d" % (name, os.getpid()))  # written whole, then moved: runs side by side
        with open(tmp, "wb") as f:
            f.write(data)
        os.replace(tmp, os.path.join(d, name))


def cell(x, y):
    """The grid of one map area, with summed-area tables for fast "share within a radius"; None when the area's
    files are not there (the measures are then simply 0 – never an error)."""
    if (x, y) in _mem:
        return _mem[(x, y)]
    d = CE.folder(x, y)
    meta = os.path.join(d, "nature-v%d.json" % VERSION)
    sf = CE.suggest(x, y)  # the cell's suggest file has the grid, the names and the heights ready
    try:
        if sf:
            m = {k: sf[k] for k in ("w", "h", "west", "north", "labels", "places")}
            raw = sf["grid"] + sf["area"]
        elif CE.file_only():
            _mem[(x, y)] = None
            return None
        elif not os.path.isfile(meta):
            if not os.path.isfile(os.path.join(d, "base.pmtiles")):
                _mem[(x, y)] = None
                return None
            build(x, y)
        if not sf:
            with open(meta, encoding="utf-8") as f:
                m = json.load(f)
            with open(os.path.join(d, "nature-v%d.bin" % VERSION), "rb") as f:
                raw = f.read()
    except Exception:  # expected: a broken / half-written cache – the measure is 0 for this area
        _mem[(x, y)] = None
        return None
    w, h = m["w"], m["h"]
    grid, names = raw[:w * h], raw[w * h:]
    W1 = w + 1
    for key, mask in (("sat", FOREST | PROTECTED), ("sat_built", BUILT), ("sat_water", WATER)):
        sat = array("I", bytes(4 * (w + 1) * (h + 1)))
        for r in range(h):
            run = 0
            base, up = (r + 1) * W1, r * W1
            row = grid[r * w:(r + 1) * w]
            for c in range(w):
                if row[c] & mask:
                    run += 1
                sat[base + c + 1] = sat[up + c + 1] + run
        m[key] = sat
    m.update({"grid": grid, "names": names, "east": m["west"] + w * DLON, "south": m["north"] - h * DLAT,
              "height": sf["heights"] if sf else heights(x, y, m)})
    _mem[(x, y)] = m
    return m


def heights(x, y, m):
    """The terrain model sampled at the grid's squares (metres; NO_HEIGHT where unknown) – or None without terrain."""
    d = CE.folder(x, y)
    path, src = os.path.join(d, "height-v1.bin"), os.path.join(d, "terrain.pmtiles")
    if not os.path.isfile(path):
        if not os.path.isfile(src):
            return None
        try:
            out = _heights(src, m["w"], m["h"], m["west"], m["north"])
        except ImportError:  # expected: no Pillow on this computer – no heights, no "Kante mit Blick"
            return None
        with open(path, "wb") as f:
            out.tofile(f)
    out = array("h")
    with open(path, "rb") as f:
        out.frombytes(f.read())
    return out


RIM_DIRS = [(math.sin(math.radians(a)), math.cos(math.radians(a))) for a in range(0, 360, 45)]
_rim = {}


def rim(p, drop_from, drop_full, reach_m=300.0):
    """0..1 – is p at an edge with a view? Per direction the ground 90 m … reach_m away: how much lower it lies on average
    (nothing below drop_from metres, full from drop_full) × how open it is (share not under trees); the best direction."""
    m = _last[0]
    if not (m and m["west"] <= p[0] < m["east"] and m["south"] < p[1] <= m["north"]):
        m = _last[0] = cell(*CE.cell_of(p[0], p[1]))
    if not m or not m["height"]:
        return 0.0
    w, h, H, grid = m["w"], m["h"], m["height"], m["grid"]
    c, r = min(w - 1, int((p[0] - m["west"]) / DLON)), min(h - 1, int((m["north"] - p[1]) / DLAT))
    key = (id(m), r * w + c)
    if key in _rim:
        return _rim[key]
    h0, best = H[r * w + c], 0.0
    steps = [d / 45.0 for d in range(90, int(reach_m) + 1, 60)]
    if h0 != NO_HEIGHT:
        for dx, dy in RIM_DIRS:
            hs, open_ = [], 0
            for k in steps:
                cc, rr = c + int(round(k * dx)), r - int(round(k * dy))
                if 0 <= cc < w and 0 <= rr < h and H[rr * w + cc] != NO_HEIGHT:
                    hs.append(H[rr * w + cc])
                    open_ += not grid[rr * w + cc] & WOOD
            if len(hs) >= 0.6 * len(steps):
                drop = h0 - sum(hs) / len(hs)
                best = max(best, max(0.0, min(1.0, (drop - drop_from) / (drop_full - drop_from))) * open_ / len(hs))
    _rim[key] = best
    return best


_last = [None]


def around(p):
    """What the land is around p = [lon, lat], in one look-up: (nature within 100 m, 300 m, 900 m; built-up within
    45 m; open water within 90 m, 300 m – each a share 0..1; name of the protected area p lies in or '')."""
    m = _last[0]
    if not (m and m["west"] <= p[0] < m["east"] and m["south"] < p[1] <= m["north"]):
        m = _last[0] = cell(*CE.cell_of(p[0], p[1]))
        if not m:
            return 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, ""
    w, h = m["w"], m["h"]
    c, r = min(w - 1, int((p[0] - m["west"]) / DLON)), min(h - 1, int((m["north"] - p[1]) / DLAT))
    W1 = w + 1

    def share(s, k):
        c0, c1, r0, r1 = max(0, c - k), min(w, c + k + 1), max(0, r - k), min(h, r + k + 1)
        return (s[r1 * W1 + c1] - s[r0 * W1 + c1] - s[r1 * W1 + c0] + s[r0 * W1 + c0]) / float((c1 - c0) * (r1 - r0))
    nat, wat, i = m["sat"], m["sat_water"], r * w + c
    return (share(nat, 2), share(nat, 6), share(nat, 20), share(m["sat_built"], 1), share(wat, 2), share(wat, 6),
            m["labels"][m["names"][i]] if m["grid"][i] & PROTECTED else "")


def place_near(p, max_m=2500.0):
    """Name of the nearest named place (village, suburb, hamlet …) in p's map area; '' if none within max_m."""
    m = cell(*CE.cell_of(p[0], p[1]))
    if not m:
        return ""
    kx = math.cos(math.radians(p[1]))
    best = None
    for nm, lon, lat, _cls in m.get("places", []):
        d2 = ((lon - p[0]) * kx) ** 2 + (lat - p[1]) ** 2
        if best is None or d2 < best[0]:
            best = (d2, nm)
    return best[1] if best and math.sqrt(best[0]) * 111320.0 <= max_m else ""


if __name__ == "__main__":
    import sys
    for a in sys.argv[1:]:
        lon, lat = [float(v) for v in a.split(",")]
        print(a, "Natur 100 / 300 / 900 m: %.2f %.2f %.2f  bebaut: %.2f  Wasser 90 / 300 m: %.2f %.2f  Schutzgebiet: %s" % around([lon, lat]),
              " Ort:", place_near([lon, lat]) or "–", " Kante mit Blick: %.2f" % rim([lon, lat], 20.0, 60.0))

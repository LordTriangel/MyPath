#!/usr/bin/env python3
"""Build the suggest file of one map cell (`suggest.bin`, format: tools/suggest_file.py, design:
docs/design/tour-suggestions.md §4a) – everything the tour suggestions need from the map, computed once per cell:

  python3 tools/build_suggest.py OUT/suggest.bin --cell 10/531/342 --from DIR [--osm cell.osm.pbf [--ids IDX_DIR]]
  python3 tools/build_suggest.py --sizes FILE …          the sections of built files with their sizes

  --from DIR      the cell's built layers: base.pmtiles, overlays.pmtiles, pois.geojson, terrain.pmtiles (each can be
                  given alone: --base, --overlays, --pois, --terrain; pois and terrain may be missing)
  --osm FILE      the cell's OSM cut (build_cell.sh: cell + buffer, route relations complete) for the signed loops;
                  without it the file has no loops section
  --ids DIR       `osmium renumber -i DIR` index of that cut: the loops get their real OSM relation ids
  --osm-date D    written into the file (default: the cut's header date)

What goes in (raw classes and lines, no scores – the method's numbers stay in the reader's settings):
  grid      ~45 m squares from base.pmtiles z12: nature / protected / built-up / water / trees, the protected area's
            name per square, place names                                     (was tools/suggest-poc/land.py build)
  heights   terrain.pmtiles z12 (Terrarium WebP, needs Pillow) at the grid's squares  (was land.py heights)
  named     signed routes (overlays `routes` + `routelines`, trail names), lake shores (base `water` + `water_name`),
            sights (pois.geojson)                                            (was named.py read_cell)
  aims      more turning points for the tour suggestions' "Ziel": named peaks with height (base `mountain_peak`),
            places to eat (hut, restaurant, café), named springs, museums (pois.geojson)
  loops     signed routes that are closed loops, in walking order, from the OSM cut (needs pyosmium) – only the loops
            this cell holds most of                                          (was official.py extract)
The POC modules import these functions; nothing here is written twice.
"""
import argparse
import json
import math
import os
import re
import struct
import sys
from array import array

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import suggest_file as SF  # noqa: E402
import suggest_tiles as pmt  # noqa: E402

DLAT = 0.0004
DLON = 0.00064
NO_HEIGHT = SF.NO_HEIGHT
FOREST, PROTECTED, BUILT, WATER, WOOD = SF.NATURE, SF.PROTECTED, SF.BUILT, SF.WATER, SF.WOOD
NATURE_CLASSES = ("wood", "wetland")
NATURE_SUB = ("wood", "forest", "heath", "scrub", "wetland", "bog", "marsh", "swamp")
PROTECTED_CLASSES = ("naturschutzgebiet", "nature_reserve", "national_park", "nationalpark", "naturwaldzelle", "vogelschutzgebiet", "biotop")
BUILT_CLASSES = ("residential", "commercial", "industrial", "retail", "railway", "garages", "school", "college",
                 "university", "education", "kindergarten", "hospital", "military")
WATER_CLASSES = ("lake", "river", "pond")
PLACE_CLASSES = SF.PLACE_CLASSES


def cell_of(lon, lat):
    return (int((lon + 180.0) / 360.0 * 1024),
            int((1 - math.asinh(math.tan(math.radians(lat))) / math.pi) / 2 * 1024))


def cell_bbox(x, y):
    """west, south, east, north of the z10 cell."""
    def lat(yy):
        return math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * yy / 1024.0))))
    return x / 1024.0 * 360 - 180, lat(y + 1), (x + 1) / 1024.0 * 360 - 180, lat(y)


def dist(a, b):
    """Metres between two [lon, lat] points (flat-earth, fine for a few kilometres)."""
    kx = math.cos(math.radians((a[1] + b[1]) / 2.0)) * 111320.0
    return math.hypot((a[0] - b[0]) * kx, (a[1] - b[1]) * 111320.0)


# ---------------------------------------------------------------- the land grid (base.pmtiles)

def _fill(grid, w, h, west, north, rings, bit, names=None, idx=0):
    """Even-odd scanline fill of one polygon (all its rings) into the grid: sets `bit`; names[i] = idx where given."""
    rows = {}
    for ring in rings:
        for (x1, y1), (x2, y2) in zip(ring, ring[1:]):
            if y1 == y2:
                continue
            ra, rb = (north - y1) / DLAT - 0.5, (north - y2) / DLAT - 0.5
            lo, hi = (ra, rb) if ra < rb else (rb, ra)
            r0, r1 = max(0, int(math.ceil(lo))), min(h - 1, int(math.floor(hi)))
            for r in range(r0, r1 + 1):
                yc = north - (r + 0.5) * DLAT
                rows.setdefault(r, []).append(x1 + (yc - y1) * (x2 - x1) / (y2 - y1))
    for r, xs in rows.items():
        xs.sort()
        for a, b in zip(xs[0::2], xs[1::2]):
            c0 = max(0, int(math.ceil((a - west) / DLON - 0.5)))
            c1 = min(w - 1, int(math.floor((b - west) / DLON - 0.5)))
            base = r * w
            for c in range(c0, c1 + 1):
                grid[base + c] |= bit
                if names is not None:
                    names[base + c] = idx


def grid(base_path, x, y):
    """The cell's land grid -> {x, y, w, h, west, north, dlon, dlat, grid, area, labels, places}."""
    west, south, east, north = cell_bbox(x, y)
    w, h = int(math.ceil((east - west) / DLON)), int(math.ceil((north - south) / DLAT))
    grid, names = bytearray(w * h), bytearray(w * h)
    labels = [""]
    places = {}
    ba = pmt.PMTiles(base_path)
    for z, tx, ty in pmt.tiles_of_cell(x, y, 12):
        raw = ba.tile(z, tx, ty)
        if not raw:
            continue
        L = pmt.layers(raw, {"landcover", "park", "landuse", "water", "place"}, z, tx, ty)
        for f in L.get("landcover", []):
            t = f["tags"]
            if f["type"] == 3 and (t.get("class") in NATURE_CLASSES or t.get("subclass") in NATURE_SUB):
                wood = t.get("class") == "wood" or t.get("subclass") in ("wood", "forest")
                _fill(grid, w, h, west, north, f["parts"], FOREST | (WOOD if wood else 0))
        for f in L.get("landuse", []):
            if f["type"] == 3 and f["tags"].get("class") in BUILT_CLASSES:
                _fill(grid, w, h, west, north, f["parts"], BUILT)
        for f in L.get("water", []):
            if f["type"] == 3 and f["tags"].get("class") in WATER_CLASSES:
                _fill(grid, w, h, west, north, f["parts"], WATER)
        for f in L.get("place", []):
            t = f["tags"]
            nm = t.get("name:de") or t.get("name") or ""
            if f["type"] == 1 and nm and t.get("class") in PLACE_CLASSES and f["parts"] and f["parts"][0]:
                q = f["parts"][0][0]
                if west <= q[0] < east and south < q[1] <= north:
                    places["%s|%.3f|%.3f" % (nm, q[0], q[1])] = [nm, round(q[0], 5), round(q[1], 5), t.get("class")]
        for f in L.get("park", []):
            t = f["tags"]
            if f["type"] != 3 or t.get("class") not in PROTECTED_CLASSES:
                continue
            nm = t.get("name") or t.get("name:de") or ""
            if nm and nm not in labels and len(labels) < 255:
                labels.append(nm)
            _fill(grid, w, h, west, north, f["parts"], PROTECTED, names, labels.index(nm) if nm in labels else 0)
    return {"x": x, "y": y, "w": w, "h": h, "west": west, "north": north, "dlon": DLON, "dlat": DLAT,
            "grid": bytes(grid), "area": bytes(names), "labels": labels, "places": list(places.values())}


def heights(terrain_path, w, h, west, north):
    """The terrain model (Terrarium WebP, zoom 12) sampled at the grid's squares: array 'h' of metres, NO_HEIGHT where
    unknown. Raises ImportError without Pillow."""
    import io
    from PIL import Image
    z = 12
    out, tiles, n = array("h", [NO_HEIGHT]) * (w * h), {}, 1 << z
    tp = pmt.PMTiles(terrain_path)
    for r in range(h):
        lat = north - (r + 0.5) * DLAT
        fy = (1 - math.asinh(math.tan(math.radians(lat))) / math.pi) / 2 * n
        for c in range(w):
            fx = (west + (c + 0.5) * DLON + 180.0) / 360.0 * n
            key = (int(fx), int(fy))
            if key not in tiles:
                raw = tp.tile(z, *key)
                tiles[key] = Image.open(io.BytesIO(raw)).convert("RGB").load() if raw else None
            px = tiles[key]
            if px:
                rr, gg, bb = px[min(255, int((fx - key[0]) * 256)), min(255, int((fy - key[1]) * 256))]
                out[r * w + c] = max(-32000, min(32000, int(round(rr * 256 + gg + bb / 256.0 - 32768))))
    return out


# ---------------------------------------------------------------- named things (overlays, base, pois)

SIGHT_KINDS = SF.SIGHT_KINDS


def _in_poly(p, ring):
    x, y, inside = p[0], p[1], False
    for a, b in zip(ring, ring[1:]):
        if (a[1] > y) != (b[1] > y) and x < (b[0] - a[0]) * (y - a[1]) / (b[1] - a[1]) + a[0]:
            inside = not inside
    return inside


def label(name, ref):
    """'X30 Neandertalweg [Hilden]' + 'X30' -> 'X30 Neandertalweg': one label per route whatever its pieces are called."""
    name = re.sub(r"^\[[^\]]*\]\s*|\s*\[[^\]]*\]\s*$", "", name or "").strip()
    name = re.sub(r"\s+-\s+(Teilstrecke|Verbindung|Abschnitt|Etappe|Teil|Variante)\b.*$|\s*\((West|Ost|Nord|Süd|Mitte)\)$", "", name)
    ref = (ref or "").strip()
    if name and ref and len(ref) <= 6 and ref.lower() not in name.lower() and not ref.startswith("<"):
        return "%s · %s" % (ref, name)
    return name or ref


def named(overlays_path, base_path, pois_path, x, y):
    """One cell -> {routes: [{kind, label, net, lines, trail}], lakes: [{name, lines}], sights: [{kind, name, p}],
    routelines: the overlays have the layer with every signed route whole (cell schema 8)}."""
    box = cell_bbox(x, y)
    rnd = lambda part: [[round(p[0], 6), round(p[1], 6)] for p in part]
    ov, groups, whole = pmt.PMTiles(overlays_path), {}, False
    for z, tx, ty in pmt.tiles_of_cell(x, y, 12):
        raw = ov.tile(z, tx, ty)
        west, east = tx / float(1 << z) * 360 - 180, (tx + 1) / float(1 << z) * 360 - 180
        L = pmt.layers(raw, {"routes", "routelines"}, z, tx, ty) if raw else {}
        whole = whole or bool(L.get("routelines"))
        for f in [f for fs in L.values() for f in fs]:
            t = f["tags"]
            name, ref = t.get("name"), t.get("ref")
            # links of the cycle node network ("Dormagen (83) - Neuss (84)") are good ways but no destination
            if not (name or ref) or re.search(r"\(\d+\)", name or "") or re.match(r"^\d+\s*-\s*\d+$", ref or ""):
                continue
            g = groups.setdefault((t.get("kind"), label(name, ref)), {"net": 0, "lines": []})
            g["net"] = max(g["net"], int(t.get("net") or 0))
            g["lines"] += [rnd(q) for q in ([p for p in part if west <= p[0] <= east] for part in f["parts"]) if len(q) > 1]
    for z, tx, ty in pmt.tiles_of_cell(x, y, 13):  # the map's trail labels: a trail's own name (tier 1 / 2)
        raw = ov.tile(z, tx, ty)
        for f in pmt.layers(raw, {"trailnames"}, z, tx, ty).get("trailnames", []) if raw else []:
            t = f["tags"]
            if t.get("t") != "n" or not t.get("name") or (t.get("tier") or 9) > 2 or ("bike", label(t["name"], None)) in groups:
                continue
            g = groups.setdefault(("hike", label(t["name"], None)), {"net": 1, "lines": [], "trail": True})
            if g.get("trail"):
                g["lines"] += [rnd(part) for part in f["parts"] if len(part) > 1]
    out = {"routes": [{"kind": k, "label": lb, "net": g["net"], "lines": g["lines"], "trail": bool(g.get("trail"))}
                      for (k, lb), g in groups.items()], "lakes": [], "sights": [], "routelines": whole}
    ba, names = pmt.PMTiles(base_path), {}
    for z, tx, ty in pmt.tiles_of_cell(x, y, 14):
        raw = ba.tile(z, tx, ty)
        for f in pmt.layers(raw, {"water_name"}, z, tx, ty).get("water_name", []) if raw else []:
            if f["tags"].get("name") and f["parts"] and f["tags"].get("class") in ("lake", "pond", None):
                names.setdefault(f["tags"]["name"], f["parts"][0][0])
    raw = ba.tile(10, x, y)
    polys = [r for f in (pmt.layers(raw, {"water"}, 10, x, y).get("water", []) if raw else [])
             if f["tags"].get("class") in ("lake", "pond") for r in f["parts"] if len(r) > 3]
    for nm, p in names.items():
        hit = [r for r in polys if _in_poly(p, r)]
        if not hit:
            continue
        lines, cur = [], []  # the shore inside this area only (the neighbour has the rest)
        for q in max(hit, key=len) + [None]:
            if q and box[0] < q[0] < box[2] and box[1] < q[1] < box[3]:
                cur.append([round(q[0], 6), round(q[1], 6)])
            else:
                lines += [cur] if len(cur) > 1 else []
                cur = []
        if lines:
            out["lakes"].append({"name": nm, "lines": lines})
    if pois_path and os.path.isfile(pois_path):
        with open(pois_path, encoding="utf-8") as f:
            out["sights"] = [{"kind": k, "name": ft["properties"].get("name") or "", "p": ft["geometry"]["coordinates"][:2]}
                             for ft in json.load(f).get("features", []) for k in [ft["properties"].get("kind")] if k in SIGHT_KINDS]
    return out


AIM_POIS = {"hut": "hut", "restaurant": "restaurant", "cafe": "cafe", "spring": "spring", "museum": "museum"}


def aims(base_path, pois_path, x, y):
    """One cell -> [{kind, name, p, ele}]: what a loop can aim at beyond the sights (SIGHT_KINDS) – named peaks with
    their height (base.pmtiles `mountain_peak`, every zoom up to 14: a lower zoom keeps only the higher ranks), and from
    pois.geojson named huts, restaurants, cafés, springs, museums. Only things with a name: an aim names the card."""
    out, seen = [], set()
    ba, box = pmt.PMTiles(base_path), cell_bbox(x, y)
    for z in (12, 13, 14):
        for t in pmt.tiles_of_cell(x, y, z):
            raw = ba.tile(*t)
            for f in pmt.layers(raw, {"mountain_peak"}, *t).get("mountain_peak", []) if raw else []:
                tg = f["tags"]
                if tg.get("class") not in ("peak", "volcano") or not tg.get("name") or not f["parts"]:
                    continue
                p = f["parts"][0][0]
                key = (tg["name"], round(p[0], 3), round(p[1], 3))
                if key in seen or not (box[0] <= p[0] < box[2] and box[1] <= p[1] < box[3]):
                    continue
                seen.add(key)
                out.append({"kind": "peak", "name": tg["name"], "p": [round(p[0], 6), round(p[1], 6)],
                            "ele": int(tg.get("ele") or 0)})
    if pois_path and os.path.isfile(pois_path):
        with open(pois_path, encoding="utf-8") as f:
            for ft in json.load(f).get("features", []):
                pr = ft["properties"]
                if pr.get("kind") in AIM_POIS and pr.get("name"):
                    q = ft["geometry"]["coordinates"]
                    out.append({"kind": AIM_POIS[pr["kind"]], "name": pr["name"], "p": [round(q[0], 6), round(q[1], 6)],
                                "ele": int(pr.get("ele") or 0)})
    return out


# ---------------------------------------------------------------- signed loops (the OSM cut, pyosmium)

KINDS = {"hiking": "hike", "foot": "hike", "bicycle": "bicycle", "mtb": "mtb"}
SAC = {"hiking": 1, "mountain_hiking": 2, "demanding_mountain_hiking": 3, "alpine_hiking": 4,
       "demanding_alpine_hiking": 5, "difficult_alpine_hiking": 6}
# what the file keeps (kilometres); the reader offers the lengths its method wants (official.py KM) – wider here, so
# "also short loops" is a setting, not a rebuild of every cell
KM_FILE = {"hike": (1.5, 35.0), "bicycle": (5.0, 120.0), "mtb": (5.0, 120.0)}
PARKING_REACH = 300.0


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


def square(p, size):
    return int(p[0] * 111320.0 * math.cos(math.radians(p[1])) / size), int(p[1] * 111320.0 / size)


def squares_around(grid, p, size):
    cx, cy = square(p, size)
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            yield from grid.get((cx + dx, cy + dy), ())


def owner(ring):
    """The z10 cell that holds most of the loop's length (ties: the smaller cell) – every cell whose cut has the loop
    whole decides the same, so a loop is in exactly one suggest file."""
    share = {}
    for a, b in zip(ring, ring[1:]):
        c = cell_of((a[0] + b[0]) / 2.0, (a[1] + b[1]) / 2.0)
        share[c] = share.get(c, 0.0) + dist(a, b)
    return min(share, key=lambda c: (-round(share[c]), c))


def loops(pbf, km=KM_FILE, bbox=None, only_cell=None, ids=None, log=None):
    """The signed routes of an OSM file that are closed loops (hiking / foot, bicycle, mtb relations whose ways chain
    up end to end), within the `km` limits per kind: [{id, kind, name, ref, network, operator, m, parking_m, t,
    coords}] – coords in walking order, starting (and ending) at the loop point nearest a parking place; parking_m =
    that distance or None when there is none within 300 m; t = the hardest sac_scale on it (1–6).
    bbox: only loops wholly inside it. only_cell = (x, y): only the loops that cell owns (`owner`). ids: new id ->
    OSM relation id (a renumbered cut)."""
    import osmium
    rels, need = [], set()
    for r in osmium.FileProcessor(pbf, osmium.osm.RELATION):
        t = r.tags
        if t.get("type") == "route" and t.get("route") in KINDS:
            ways = [(m.ref, m.role) for m in r.members if m.type == "w" and m.role in ("", "main", "forward", "backward", "route")]
            if ways:
                rels.append({"id": r.id, "kind": KINDS[t.get("route")], "name": t.get("name", ""), "ref": t.get("ref", ""),
                             "network": t.get("network", ""), "operator": t.get("operator", ""), "ways": ways})
                need.update(w for w, _ in ways)
    # the filters work inside osmium: Python sees only parking nodes and ways (a cell's cut has millions of nodes)
    geo, sac = {}, {}
    parking = [(o.location.lon, o.location.lat) for o in osmium.FileProcessor(pbf, osmium.osm.NODE).with_filter(
        osmium.filter.TagFilter(("amenity", "parking")))]
    for o in osmium.FileProcessor(pbf, osmium.osm.NODE | osmium.osm.WAY).with_locations().with_filter(
            osmium.filter.EntityFilter(osmium.osm.WAY)):
        if o.id in need or o.tags.get("amenity") == "parking":
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
        pgrid.setdefault(square(p, PARKING_REACH), []).append(p)
    out, metres = [], {}

    def ring_of(ids, hi):
        """chain – but not for ways that are together longer than any loop we keep (a long-distance route's
        thousands of ways would cost minutes and can never fit)."""
        for w in ids:
            if w not in metres:
                metres[w] = sum(dist(a, b) for a, b in zip(geo[w], geo[w][1:]))
        return chain([geo[w] for w in ids]) if sum(metres[w] for w in ids) <= 1000.0 * hi else None
    for r in rels:
        ways = r.pop("ways")
        lo, hi = km[r["kind"]]
        # a cycle route often runs one way on each side of a road ("forward" / "backward"): then the one direction is the ring
        ring = ring_of([w for w, _ in ways if w in geo], hi) or ring_of([w for w, role in ways if w in geo and role != "backward"], hi)
        if not ring:
            continue
        m = sum(dist(a, b) for a, b in zip(ring, ring[1:]))
        if not lo <= m / 1000.0 <= hi or (bbox and not all(bbox[0] <= p[0] <= bbox[2] and bbox[1] <= p[1] <= bbox[3] for p in ring)):
            continue
        if only_cell and owner(ring) != tuple(only_cell):
            continue
        best = (1e9, 0)
        for i, p in enumerate(ring):
            for q in squares_around(pgrid, p, PARKING_REACH):
                best = min(best, (dist(p, q), i))
        k = best[1]
        ring = ring[k:-1] + ring[:k + 1]  # the loop starts and ends at the parking place
        out.append(dict(r, id=ids(r["id"]) if ids else r["id"], m=m, parking_m=best[0] if best[0] < PARKING_REACH else None,
                        t=max(sac.get(w, 1) for w, _ in ways), coords=[[round(x, 6), round(y, 6)] for x, y in ring]))
    if log:
        count = lambda k: sum(lp["kind"] == k for lp in out)
        log("%d route relations, closed loops: %d hiking, %d cycling, %d MTB" % (len(rels), count("hike"), count("bicycle"), count("mtb")))
    return out


def renumbered(idx_dir):
    """osmium renumber -i DIR: new relation id -> the OSM id (tools/cells/cells.py remap_ids reads the same files)."""
    with open(os.path.join(idx_dir, "start_ids")) as f:
        start = int(f.read().strip().split(",")[2])
    with open(os.path.join(idx_dir, "relation.idx"), "rb") as f:
        raw = f.read()
    return lambda new: struct.unpack_from("<q", raw, 8 * (new - start))[0]


def ascent(coords, G, H):
    """Metres up along a loop from the cell's heights (rises under 6 m are noise, as net.climb) – None when the loop
    leaves the grid or crosses unknown heights: the reader then takes the way network's heights."""
    w, h, up, ref = G["w"], G["h"], 0.0, None
    for a, b in zip(coords, coords[1:]):
        n = max(1, int(dist(a, b) / 25.0))
        for k in range(n):
            lon, lat = a[0] + (b[0] - a[0]) * k / n, a[1] + (b[1] - a[1]) * k / n
            c, r = int(math.floor((lon - G["west"]) / DLON)), int(math.floor((G["north"] - lat) / DLAT))
            if not (0 <= c < w and 0 <= r < h) or H[r * w + c] == NO_HEIGHT:
                return None
            ele = H[r * w + c]
            if ref is None or ele < ref - 6.0:
                ref = ele
            elif ele > ref + 6.0:
                up, ref = up + ele - ref, ele
    return up


# ---------------------------------------------------------------- the file

def osm_date(pbf):
    import osmium
    r = osmium.io.Reader(pbf, osmium.osm.osm_entity_bits.NOTHING)
    try:
        h = r.header()
        return (h.get("osmosis_replication_timestamp") or h.get("timestamp") or "")[:10]
    finally:
        r.close()


def build(x, y, base, overlays, pois=None, terrain=None, osm=None, ids=None, date=None, log=None):
    """Everything of one cell -> the dict suggest_file.pack takes."""
    d = grid(base, x, y)
    d["heights"] = heights(terrain, d["w"], d["h"], d["west"], d["north"]) if terrain and os.path.isfile(terrain) else None
    d.update(named(overlays, base, pois, x, y))
    d["aims"] = aims(base, pois, x, y)
    d["loops"], d["osm_date"] = None, date or ""
    if osm:
        d["loops"] = loops(osm, only_cell=(x, y), ids=renumbered(ids) if ids else None, log=log)
        for lp in d["loops"]:
            lp["ascent"] = ascent(lp["coords"], d, d["heights"]) if d["heights"] else None
        d["osm_date"] = date or osm_date(osm)
    return d


def write(path, d):
    data = SF.pack(d)
    tmp = "%s.%d" % (path, os.getpid())  # written whole, then moved: a reader never sees half a file
    with open(tmp, "wb") as f:
        f.write(data)
    os.replace(tmp, path)
    return len(data)


def sizes(paths):
    for p in paths:
        with open(p, "rb") as f:
            data = f.read()
        d = SF.unpack(data)
        print("%s  %d bytes  format %d  content %d  cell %d/%d  grid %d × %d  osm %s" % (
            p, len(data), d["version"], d["content"], d["x"], d["y"], d["w"], d["h"], d["osm_date"] or "–"))
        for tag, raw, packed in SF.sections(data):
            print("  %s %9d -> %7d" % (tag, raw, packed))
        print("  %d protected areas, %d places, %d routes, %d lakes, %d sights, %s aims, loops: %s, heights: %s" % (
            len(d["labels"]) - 1, len(d["places"]), len(d["routes"]), len(d["lakes"]), len(d["sights"]),
            "–" if d["aims"] is None else len(d["aims"]),
            "–" if d["loops"] is None else len(d["loops"]), "yes" if d["heights"] else "no"))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("out", nargs="*")
    ap.add_argument("--sizes", action="store_true")
    ap.add_argument("--cell", help="10/X/Y")
    ap.add_argument("--from", dest="src", help="folder with base.pmtiles, overlays.pmtiles, pois.geojson, terrain.pmtiles")
    for name in ("base", "overlays", "pois", "terrain", "osm", "ids"):
        ap.add_argument("--" + name)
    ap.add_argument("--osm-date")
    a = ap.parse_args()
    if a.sizes:
        return sizes(a.out)
    if len(a.out) != 1 or not a.cell:
        ap.error("one output file and --cell 10/X/Y")
    z, x, y = [int(v) for v in a.cell.replace("-", "/").split("/")]
    if z != 10:
        ap.error("a suggest file is per z10 cell")
    src = {k: getattr(a, k) or (os.path.join(a.src, n) if a.src else None)
           for k, n in (("base", "base.pmtiles"), ("overlays", "overlays.pmtiles"), ("pois", "pois.geojson"), ("terrain", "terrain.pmtiles"))}
    for k in ("base", "overlays"):
        if not src[k] or not os.path.isfile(src[k]):
            sys.exit("build_suggest: %s.pmtiles is needed (--from DIR or --%s FILE)" % (k, k))
    d = build(x, y, src["base"], src["overlays"], src["pois"], src["terrain"], a.osm, a.ids, a.osm_date, print)
    n = write(a.out[0], d)
    print("suggest file %s: %d bytes – %d routes, %d lakes, %d sights, %s loops, heights %s, routelines %s" % (
        a.out[0], n, len(d["routes"]), len(d["lakes"]), len(d["sights"]), "no" if d["loops"] is None else len(d["loops"]),
        "yes" if d["heights"] else "no", "yes" if d["routelines"] else "no"))


if __name__ == "__main__":
    sys.exit(main())

"""The suggest file of a map cell (`suggest.bin`) – what the tour suggestions read instead of rasterising map tiles,
decoding terrain and guessing loops at run time. Format, writer and reader; standard library only.
Design: docs/design/tour-suggestions.md §4a. Producer: tools/build_suggest.py. Readers: tools/suggest-poc (land.py,
named.py, official.py) and, later, the shared Kotlin core (java.util.zip.Inflater + a byte buffer – nothing else).

    0   4   magic "OPSG"
    4   2   u16 format version (FORMAT_VERSION) – bumped only when an old reader would read a new file wrongly
    6   2   u16 0 (reserved)
    8   …   one zlib stream (deflate) = the body

The body is a list of sections: 4 ASCII letters, u32 payload length, payload. A reader skips sections it does not know,
so adding a section or appending fields to the END of HEAD needs no new format version. Everything little-endian.
uv = unsigned varint (7 bits per byte, low first); zz = zig-zag varint; str = uv byte length + UTF-8;
point = zz lon, zz lat in 1e-6 degrees; line = uv count, first point absolute, the others as differences.

  HEAD  u16 x, u16 y (the z10 cell), u16 w, u16 h (grid columns, rows), f64 west, f64 north (the grid's corner),
        f64 dlon, f64 dlat (one square, ~45 m), u16 content (CONTENT: what the producer puts into the classes),
        u16 flags (HAS_HEIGHTS | HAS_ROUTELINES | HAS_LOOPS), str OSM date of the loops' extract ("" unknown)
  GRID  w × h bytes, row by row from the north-west corner: land bits NATURE 1 (forest, heath, wetland),
        PROTECTED 2, BUILT 4, WATER 8, WOOD 16 (trees – a view is closed there)
  AREA  w × h bytes: number of the protected area the square lies in (0 = none / unnamed), then uv count + str names
        (name 0 is "")
  HGHT  u8 coding (1), then the heights in metres (int16) as differences to a guess from the squares already read:
        left + above − above-left (a square outside the grid counts 0), taken as 16-bit numbers, zig-zag; w × h low
        bytes followed by w × h high bytes. NO_HEIGHT (-32768) = unknown. The edge with a view is NOT stored: it is
        a rule over these heights (land.rim), so its thresholds stay settings.
  PLAC  uv count; each: str name, point, u8 class (index into PLACE_CLASSES) – for "Wald bei <Ort>"
  ROUT  uv count; each: u8 kind (0 hike, 1 bike, 2 mtb), u8 net (4 international … 1 local, 0 unknown), u8 flags (1 = only a
        trail name of the map, no route relation), str label, uv lines, lines – the signed routes as the map has them
        (pieces, unordered: for "this way is on the Neandertalweg" and for following a line)
  LAKE  uv count; each: str name, uv lines, lines (the shore inside this cell)
  SGHT  uv count; each: u8 kind (index into SIGHT_KINDS), str name ("" = unnamed), point
  LOOP  uv count; each: uv OSM relation id, u8 kind (0 hike, 1 bicycle, 2 mtb), u8 T level (1–6, the hardest
        sac_scale on it), uv parking (metres from the start to the nearest parking place + 1; 0 = none within 300 m),
        uv length in centimetres, uv ascent in metres + 1 (0 = unknown: the loop leaves this cell's heights), str name,
        str ref, str network (OSM: lwn, rwn, lcn …), line = the closed loop in walking order, starting at the point
        nearest a parking place. A loop is in exactly one cell: the one that holds most of its length.
No description texts of any kind (copyright, owner 2026-11-16).
"""
import struct
import zlib
from array import array

MAGIC = b"OPSG"
FORMAT_VERSION = 1
CONTENT = 1  # the producer's class lists / rules (build_suggest.py); a reader may ask for a minimum
NO_HEIGHT = -32768
NATURE, PROTECTED, BUILT, WATER, WOOD = 1, 2, 4, 8, 16
HAS_HEIGHTS, HAS_ROUTELINES, HAS_LOOPS = 1, 2, 4
PLACE_CLASSES = ("city", "town", "village", "suburb", "hamlet", "quarter", "neighbourhood")
SIGHT_KINDS = ("viewpoint", "castle", "ruins", "chapel", "cave", "waterfall")
ROUTE_KINDS = ("hike", "bike", "mtb")
LOOP_KINDS = ("hike", "bicycle", "mtb")
NAME = "suggest.bin"
E6 = 1000000.0


class NotASuggestFile(ValueError):
    pass


# ---------------------------------------------------------------- writing

class _W:
    def __init__(self):
        self.b = bytearray()

    def u8(self, v):
        self.b.append(v & 255)

    def uv(self, v):
        if v < 0:
            raise ValueError("negative uv")
        while v > 127:
            self.b.append((v & 127) | 128)
            v >>= 7
        self.b.append(v)

    def zz(self, v):
        self.uv(v * 2 if v >= 0 else -v * 2 - 1)

    def text(self, s):
        raw = (s or "").encode("utf-8")
        self.uv(len(raw))
        self.b += raw

    def point(self, p, last=(0, 0)):
        q = (int(round(p[0] * E6)), int(round(p[1] * E6)))
        self.zz(q[0] - last[0])
        self.zz(q[1] - last[1])
        return q

    def line(self, pts):
        self.uv(len(pts))
        last = (0, 0)
        for p in pts:
            last = self.point(p, last)

    def lines(self, lines):
        self.uv(len(lines))
        for ln in lines:
            self.line(ln)


def _heights(H, w, h):
    lo, hi = bytearray(w * h), bytearray(w * h)
    for r in range(h):
        base = r * w
        left = corner = 0
        for c in range(w):
            v, up = H[base + c], (H[base - w + c] if r else 0)
            d = ((v - (left + up - corner) + 32768) & 0xFFFF) - 32768  # 16-bit: unknown next to a mountain wraps
            left, corner = v, up
            z = (d * 2 if d >= 0 else -d * 2 - 1) & 0xFFFF
            lo[base + c], hi[base + c] = z & 255, z >> 8
    return b"\x01" + bytes(lo) + bytes(hi)


def pack(d):
    """The file's bytes from the producer's dict (see `unpack` for the keys)."""
    w, h = d["w"], d["h"]
    if len(d["grid"]) != w * h or len(d["area"]) != w * h:
        raise ValueError("grid size")
    sec = []
    flags = (HAS_HEIGHTS if d.get("heights") else 0) | (HAS_ROUTELINES if d.get("routelines") else 0) \
        | (HAS_LOOPS if d.get("loops") is not None else 0)
    x = _W()
    x.b += struct.pack("<HHHHddddHH", d["x"], d["y"], w, h, d["west"], d["north"], d["dlon"], d["dlat"], CONTENT, flags)
    x.text(d.get("osm_date") or "")
    sec.append((b"HEAD", x.b))
    sec.append((b"GRID", bytes(d["grid"])))
    x = _W()
    x.b += bytes(d["area"])
    x.uv(len(d["labels"]))
    for s in d["labels"]:
        x.text(s)
    sec.append((b"AREA", x.b))
    if d.get("heights"):
        sec.append((b"HGHT", _heights(d["heights"], w, h)))
    x = _W()
    x.uv(len(d["places"]))
    for nm, lon, lat, cls in d["places"]:
        x.text(nm)
        x.point((lon, lat))
        x.u8(PLACE_CLASSES.index(cls))
    sec.append((b"PLAC", x.b))
    x = _W()
    x.uv(len(d["routes"]))
    for r in d["routes"]:
        x.u8(ROUTE_KINDS.index(r["kind"]))
        x.u8(r["net"])
        x.u8(1 if r.get("trail") else 0)
        x.text(r["label"])
        x.lines(r["lines"])
    sec.append((b"ROUT", x.b))
    x = _W()
    x.uv(len(d["lakes"]))
    for lk in d["lakes"]:
        x.text(lk["name"])
        x.lines(lk["lines"])
    sec.append((b"LAKE", x.b))
    x = _W()
    x.uv(len(d["sights"]))
    for s in d["sights"]:
        x.u8(SIGHT_KINDS.index(s["kind"]))
        x.text(s["name"])
        x.point(s["p"])
    sec.append((b"SGHT", x.b))
    if d.get("loops") is not None:
        x = _W()
        x.uv(len(d["loops"]))
        for lp in d["loops"]:
            x.uv(lp["id"])
            x.u8(LOOP_KINDS.index(lp["kind"]))
            x.u8(lp["t"])
            x.uv(0 if lp["parking_m"] is None else int(round(lp["parking_m"])) + 1)
            x.uv(int(round(lp["m"] * 100.0)))
            x.uv(0 if lp.get("ascent") is None else int(round(lp["ascent"])) + 1)
            x.text(lp["name"])
            x.text(lp["ref"])
            x.text(lp["network"])
            x.line(lp["coords"])
        sec.append((b"LOOP", x.b))
    body = b"".join(tag + struct.pack("<I", len(p)) + bytes(p) for tag, p in sec)
    return MAGIC + struct.pack("<HH", FORMAT_VERSION, 0) + zlib.compress(body, 9)


# ---------------------------------------------------------------- reading

class _R:
    def __init__(self, b):
        self.b, self.i = b, 0

    def u8(self):
        self.i += 1
        return self.b[self.i - 1]

    def uv(self):
        v, s = 0, 0
        while True:
            c = self.b[self.i]
            self.i += 1
            v |= (c & 127) << s
            if c < 128:
                return v
            s += 7

    def zz(self):
        v = self.uv()
        return (v >> 1) ^ -(v & 1)

    def text(self):
        n = self.uv()
        self.i += n
        return bytes(self.b[self.i - n:self.i]).decode("utf-8")

    def point(self, last=(0, 0)):
        return last[0] + self.zz(), last[1] + self.zz()

    def line(self):
        out, last = [], (0, 0)
        for _ in range(self.uv()):
            last = self.point(last)
            out.append([last[0] / E6, last[1] / E6])
        return out

    def lines(self):
        return [self.line() for _ in range(self.uv())]


def version_of(path):
    """The file's format version without unpacking it; None when there is no (whole) suggest file."""
    try:
        with open(path, "rb") as f:
            head = f.read(8)
    except OSError:  # expected: the cell has no suggest file (an older map)
        return None
    return struct.unpack("<H", head[4:6])[0] if len(head) == 8 and head[:4] == MAGIC else None


def unpack(data):
    """The file's bytes -> {version, content, flags, x, y, w, h, west, north, dlon, dlat, osm_date, grid, area, labels,
    heights (array 'h' or None), places [[name, lon, lat, class]], routes [{kind, label, net, trail, lines}],
    lakes [{name, lines}], sights [{kind, name, p}], loops [{id, kind, t, parking_m, m, ascent, name, ref, network,
    coords}] or None (built without the OSM extract)}. Raises NotASuggestFile for anything else or a newer format."""
    if len(data) < 8 or data[:4] != MAGIC:
        raise NotASuggestFile("no suggest file")
    version = struct.unpack("<H", data[4:6])[0]
    if version > FORMAT_VERSION:
        raise NotASuggestFile("suggest file of a newer format (%d > %d)" % (version, FORMAT_VERSION))
    body = zlib.decompress(data[8:])
    d = {"version": version, "heights": None, "loops": None, "places": [], "routes": [], "lakes": [], "sights": [],
         "labels": [""]}
    i = 0
    while i + 8 <= len(body):
        tag, n = body[i:i + 4], struct.unpack("<I", body[i + 4:i + 8])[0]
        p = memoryview(body)[i + 8:i + 8 + n]
        i += 8 + n
        r = _R(p)
        if tag == b"HEAD":
            (d["x"], d["y"], d["w"], d["h"], d["west"], d["north"], d["dlon"], d["dlat"], d["content"],
             d["flags"]) = struct.unpack("<HHHHddddHH", p[:44])
            r.i = 44
            d["osm_date"] = r.text()
        elif tag == b"GRID":
            d["grid"] = bytes(p)
        elif tag == b"AREA":
            size = d["w"] * d["h"]
            d["area"] = bytes(p[:size])
            r.i = size
            d["labels"] = [r.text() for _ in range(r.uv())]
        elif tag == b"HGHT" and p[0] == 1:
            w, h = d["w"], d["h"]
            lo, hi = p[1:1 + w * h], p[1 + w * h:1 + 2 * w * h]
            H = array("h", bytes(2 * w * h))
            for row in range(h):
                base = row * w
                left = corner = 0
                for c in range(w):
                    z, up = lo[base + c] | (hi[base + c] << 8), (H[base - w + c] if row else 0)
                    v = ((left + up - corner + ((z >> 1) ^ -(z & 1)) + 32768) & 0xFFFF) - 32768
                    H[base + c] = v
                    left, corner = v, up
            d["heights"] = H
        elif tag == b"PLAC":
            for _ in range(r.uv()):
                nm, q = r.text(), r.point()
                d["places"].append([nm, q[0] / E6, q[1] / E6, PLACE_CLASSES[r.u8()]])
        elif tag == b"ROUT":
            for _ in range(r.uv()):
                kind, net, fl = ROUTE_KINDS[r.u8()], r.u8(), r.u8()
                d["routes"].append({"kind": kind, "label": r.text(), "net": net, "lines": r.lines(), "trail": bool(fl & 1)})
        elif tag == b"LAKE":
            for _ in range(r.uv()):
                d["lakes"].append({"name": r.text(), "lines": r.lines()})
        elif tag == b"SGHT":
            for _ in range(r.uv()):
                kind, nm, q = SIGHT_KINDS[r.u8()], r.text(), r.point()
                d["sights"].append({"kind": kind, "name": nm, "p": [q[0] / E6, q[1] / E6]})
        elif tag == b"LOOP":
            d["loops"] = []
            for _ in range(r.uv()):
                lp = {"id": r.uv(), "kind": LOOP_KINDS[r.u8()], "t": r.u8()}
                park, lp["m"], up = r.uv(), r.uv() / 100.0, r.uv()
                lp["parking_m"], lp["ascent"] = (park - 1 if park else None), (up - 1 if up else None)
                lp["name"], lp["ref"], lp["network"] = r.text(), r.text(), r.text()
                lp["coords"] = r.line()
                d["loops"].append(lp)
    if "grid" not in d or "area" not in d:
        raise NotASuggestFile("suggest file without a grid")
    return d


def read(path):
    with open(path, "rb") as f:
        return unpack(f.read())


def sections(data):
    """[(tag, bytes in the body, bytes when deflated alone)] – for the size table (build_suggest.py --sizes)."""
    body = zlib.decompress(data[8:])
    out, i = [], 0
    while i + 8 <= len(body):
        n = struct.unpack("<I", body[i + 4:i + 8])[0]
        out.append((body[i:i + 4].decode("ascii"), n, len(zlib.compress(body[i + 8:i + 8 + n], 9))))
        i += 8 + n
    return out


def selftest():
    w, h = 5, 4
    H = array("h", [NO_HEIGHT, 1200, 1210, -5, 0, 3, 2, NO_HEIGHT, 32000, -32000] * 2)
    d = {"x": 531, "y": 342, "w": w, "h": h, "west": 6.6796875, "north": 51.17934, "dlon": 0.00064, "dlat": 0.0004,
         "grid": bytes(range(w * h)), "area": bytes([0, 1] * 10), "labels": ["", "Ohligser Heide"], "heights": H,
         "places": [["Köln", 6.95, 50.94, "city"]], "osm_date": "2026-11-15", "routelines": True,
         "routes": [{"kind": "mtb", "label": "X30 · Neandertalweg", "net": 2, "trail": True,
                     "lines": [[[6.9, 51.1], [6.900001, 51.099999], [-0.5, -70.25]]]}],
         "lakes": [{"name": "Elbsee", "lines": []}], "sights": [{"kind": "ruins", "name": "", "p": [6.99385, 51.11678]}],
         "loops": [{"id": 12290089, "kind": "bicycle", "t": 3, "parking_m": None, "m": 17641.237, "ascent": 0.0,
                    "name": "Rund um Haßloch", "ref": "RH", "network": "lcn",
                    "coords": [[6.9, 51.1], [6.91, 51.1], [6.9, 51.1]]},
                   {"id": 1, "kind": "hike", "t": 1, "parking_m": 12.4, "m": 4000.0, "ascent": None, "name": "",
                    "ref": "", "network": "", "coords": []}]}
    data = pack(d)
    u = unpack(data)
    for k in ("x", "y", "w", "h", "west", "north", "dlon", "dlat", "grid", "area", "labels", "places", "osm_date",
              "lakes", "sights", "routes"):
        assert u[k] == d[k], k
    assert u["heights"] == H and u["version"] == FORMAT_VERSION and u["content"] == CONTENT
    assert u["flags"] == HAS_HEIGHTS | HAS_ROUTELINES | HAS_LOOPS
    a, b = u["loops"]
    assert (a["id"], a["kind"], a["t"], a["parking_m"], a["ascent"], a["name"], a["coords"]) == \
        (12290089, "bicycle", 3, None, 0, "Rund um Haßloch", d["loops"][0]["coords"])
    assert abs(a["m"] - 17641.24) < 1e-9 and (b["parking_m"], b["ascent"], b["coords"]) == (12, None, [])
    # a section a reader does not know is skipped; a newer format is refused; no loops section = built without OSM
    body = zlib.decompress(data[8:]) + b"NEW!" + struct.pack("<I", 3) + b"abc"
    assert unpack(data[:8] + zlib.compress(body))["loops"] == u["loops"]
    assert unpack(pack(dict(d, loops=None, heights=None)))["loops"] is None
    try:
        unpack(MAGIC + struct.pack("<HH", FORMAT_VERSION + 1, 0) + data[8:])
        raise AssertionError("a newer format must be refused")
    except NotASuggestFile:
        pass
    print("ok")


if __name__ == "__main__":
    selftest()

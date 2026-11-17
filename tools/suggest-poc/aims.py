"""Aims – what a loop can lead to: a summit, a lake, a viewpoint, a hut or a café, a castle or a chapel, a waterfall.

One mechanism for every kind (owner 2026-11-17): an aim in reach is a turning point (generate.pool), the loop is built
around it; a loop that really reaches one (not 110 m beside it) counts more on Besonderes (score.judge), the more when
the wish names its Ziel; the card names it. Where an aim lies:
  a point (peak, viewpoint, hut, castle …)  the ways within reach_m of it (a peak: peak_reach_m, and the way not more
                                            than peak_below metres lower) – the far end of the nearest one is the
                                            turning point, so a loop that turns there passes it; a dead end up to a
                                            summit is walked out and back (generate.cut_spurs keeps it)
  a lake                                    its shore (the ways along it, named.Index): the turning point is the shore
                                            node farthest from where one first meets it; the shore's ways are cheap
                                            while the loop aims at it – so the loop goes round, or along one side there
                                            and the other back
Pure: place + settings -> aims; a loop -> what it reaches.
"""
from net import dist, INF

SIGHT_WHAT = {"Aussichtspunkt": "viewpoint", "Burg": "castle", "Ruine": "ruins", "Kapelle": "chapel",
              "Wasserfall": "waterfall"}


def find(P, S):
    """The aims in reach of the start: [{ziel, what, name, ele, worth, node, edges, p, shore_m}] – `edges` are the ways
    at the aim (for a lake: its shore), `node` the turning point. Only aims a way leads to."""
    net, F, Z = P.net, P.F, S["aims"]
    of = {w: z for z, ws in Z["kinds"].items() for w in ws}
    reach = lambda ei: F.ok[ei] and P.d0[net.edges[ei][0]] < INF
    K = 0.0012  # a grid (~90 m squares) of the reachable ways' points near the point aims, to find the ways near one
    cell = lambda q: (int(q[0] / K), int(q[1] / K * 1.5))
    hot = {(c[0] + dx, c[1] + dy) for th in F.things if th["kind"] in ("aim", "sight") for c in [cell(th["dense"][0])]
           for dx in (-2, -1, 0, 1, 2) for dy in (-2, -1, 0, 1, 2)}
    grid, lake_edges = {}, {}
    for ei in range(len(net.edges)):
        if not reach(ei):
            continue
        for i in F.named[ei]:
            if F.things[i]["kind"] == "lake":
                lake_edges.setdefault(i, []).append(ei)
        a, b = net.edges[ei][:2]
        if F.ln[ei] < 150.0 and cell(net.nodes[a]) not in hot and cell(net.nodes[b]) not in hot:
            continue  # a short way far from every aim (most of them: reading every way's points is slow on wheels)
        g = net.geom(ei)
        for p, q in zip(g, g[1:]):
            n = max(1, int(dist(p, q) / 50.0))
            for k in range(n + 1):
                c = cell([p[0] + (q[0] - p[0]) * k / n, p[1] + (q[1] - p[1]) * k / n])
                if c in hot:
                    grid.setdefault(c, set()).add(ei)

    def near(p, r):
        cx, cy = cell(p)
        out = {}
        for e in {e for dx in (-1, 0, 1) for dy in (-1, 0, 1) for e in grid.get((cx + dx, cy + dy), ())}:
            g = net.geom(e)
            pts = [[a[0] + (b[0] - a[0]) * k / n, a[1] + (b[1] - a[1]) * k / n]
                   for a, b in zip(g, g[1:]) for n in [max(1, int(dist(a, b) / 20.0))] for k in range(n)] + g[-1:]
            d = min(dist(p, q) for q in pts)
            if d <= r:
                out[e] = d
        return out
    out = []
    for i, th in enumerate(F.things):
        what = th.get("what") if th["kind"] == "aim" else SIGHT_WHAT.get(th.get("what")) if th["kind"] == "sight" else \
            "lake" if th["kind"] == "lake" else None
        if what not in of or (th["kind"] == "sight" and th["name"] == th["what"] and what != "viewpoint"):
            continue  # an unnamed castle or chapel is no aim; an unnamed viewpoint is ("Aussichtspunkt")
        a = {"ziel": of[what], "what": what, "name": th["name"], "ele": th.get("ele", 0), "worth": Z["worth"][what]}
        if what == "lake":
            es = lake_edges.get(i, [])
            m = sum(F.ln[e] for e in es)
            if m < Z["shore_m"]:
                continue
            nodes = {x for e in es for x in net.edges[e][:2] if P.d0[x] < INF}
            entry = min(nodes, key=P.len0.__getitem__)
            a.update(edges=set(es), shore_m=m, p=list(net.nodes[entry][:2]), dense=th["dense"],
                     node=max(nodes, key=lambda x: dist(net.nodes[x], net.nodes[entry])))
        else:
            p = th["dense"][0]
            peak = what == "peak"
            hits = near(p, Z["peak_reach_m"] if peak else Z["reach_m"])
            if peak and th.get("ele"):
                low = th["ele"] - Z["peak_below"]
                hits = {e: d for e, d in hits.items() if max(net.nodes[x][2] for x in net.edges[e][:2]) >= low}
            if not hits:
                continue
            e = min(hits, key=hits.get)
            a.update(edges=set(hits), p=p, shore_m=0.0, node=max(net.edges[e][:2], key=lambda x: P.d0[x]))
        if P.d0[a["node"]] < INF and a["node"] != net.start:
            out.append(a)
    best = {}  # one aim per kind and name (a peak in two map areas, two points of one hut)
    for a in out:
        k = (a["what"], a["name"])
        if k not in best or P.d0[a["node"]] < P.d0[best[k]["node"]]:
            best[k] = a
    return list(best.values())


def reached(P, S, edges):
    """What a loop really reaches beyond the way out: [{…aim, at: share of the loop where it is first met, round: share
    of a lake's shore the loop runs along (within shore_near metres)}] in walking order. A lake counts from shore_m
    along its shore and lake_min of its shore (one side of a small lake, a stretch of a big one)."""
    F, Z, net = P.F, S["aims"], P.net
    first, acc = {}, 0.0
    for e in edges:
        if P.core(e):  # an aim on the unavoidable way out (the museum in the town centre) is no aim of this loop
            first.setdefault(e, acc)
        acc += F.ln[e]
    total = acc or 1.0
    out, grid = [], None
    K, near = 0.0008, Z["shore_near"]
    for a in P.aims:
        hit = [first[e] for e in a["edges"] if e in first]
        if not hit:
            continue
        if a["what"] == "lake":
            if sum(F.ln[e] for e in a["edges"] if e in first) < Z["shore_m"]:
                continue
            if grid is None:  # the loop's points, for "how much of the shore does it run along"
                grid = {}
                for e in first:
                    for q in net.geom(e):
                        grid.setdefault((int(q[0] / K), int(q[1] / K * 1.5)), []).append(q)
            close = lambda p: any(dist(p, q) <= near for dx in (-1, 0, 1) for dy in (-1, 0, 1)
                                  for q in grid.get((int(p[0] / K) + dx, int(p[1] / K * 1.5) + dy), ()))
            along = sum(map(close, a["dense"]))  # the shore's points lie ~25 m apart
            share = along / float(len(a["dense"]))
            if share >= Z["lake_min"] or 25.0 * along >= Z["lake_min_m"]:
                out.append(dict(a, at=min(hit) / total, round=share))
        else:
            out.append(dict(a, at=min(hit) / total, round=0.0))
    return sorted(out, key=lambda a: a["at"])


def worth(S, a, ziel):
    """What reaching this aim adds to the loop's raw Besonderes: its worth, more when the wish names its Ziel; a place to
    eat counts full in the middle of the loop and nothing at its ends (one does not stop at the start)."""
    Z = S["aims"]
    w = a["worth"] * Z["reached"] * (Z["chosen"] if a["ziel"] in ziel else 1.0)
    if a["ziel"] == "einkehr":
        w *= max(0.0, 1.0 - 2.0 * abs(a["at"] - 0.5))
    if a["what"] == "lake":  # along one side half, round (or there along one side, back along the other) full
        w *= 0.5 + 0.5 * min(1.0, a["round"] / Z["round"])
    return w

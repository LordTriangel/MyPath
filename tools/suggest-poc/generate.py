"""Building loops – one pool, one assembly routine.

Every loop is a list of turning points handed to `assemble`: start → point → … → start, each part the cheapest way
(place.cost = how good a way is here), ways already in the loop cost more and so do ways touching them – that keeps
the way home apart from the way out, so nothing is walked twice or crossed. The generators differ only in how they
choose the points:
  turning points  the most pleasant point per direction at the right distance, alone or in pairs;
                  when a climb is wished, also points high enough and near enough for the time
  chains          the stand-out areas of the place, in order around the start, each crossed through its heart
  rings           a signed route that is itself a loop of the right length, walked round (also from its pieces)
  lines           follow a known route, a rail trail or a bank, come home another way
A loop inside one big area needs no generator of its own: there the pleasant turning points lie inside it.
The wished time is a range: turning points are taken across all of it, and distance is counted in time.
"""
from net import bearing, climb, dijkstra, dist, path, runs_along, INF
from score import road_run


def assemble(P, S, points, costs=None):
    """-> the loop's edges in walking order, or None. costs = {part index: cost list} for a part that follows a line."""
    net, F, Q = P.net, P.F, S["search"]
    free = P.ch.family["free_stem_m"]
    pen, edges, cur, walked = {}, [], net.start, 0.0
    for i, w in enumerate(points + [net.start]):
        cost = (costs or {}).get(i, P.cost)
        if i == 0 and cost is P.cost:
            leg = path(P.prev0, P.pe0, w)
        else:
            d, prev, pe, _ = dijkstra(net, cur, cost, pen, stop=w)
            leg = path(prev, pe, w) if d[w] < INF else None
        if leg is None or (not leg and w != cur):
            return None
        node = cur
        for ei in leg:
            walked += F.ln[ei]
            node = net.other(ei, node)
            if walked > free:  # the first stretch from the start stays free: a short way out and back is fine
                pen[ei] = Q["used"]
                for _, e2 in net.adj[node]:
                    pen.setdefault(e2, Q["beside_used"])
        edges += leg
        cur = w
    edges = cut_spurs(net, F, edges)
    return edges if len(edges) >= 3 else None


def cut_spurs(net, F, edges):
    """A spur – out to a turning point off the way and back the same way, also with a little loop at its tip – is not
    part of a loop: cut it. (A closed piece of the walk of which a third or more is walked twice.)"""
    while True:
        out = []
        for ei in edges:  # straight there and back
            if out and out[-1] == ei:
                out.pop()
            else:
                out.append(ei)
        edges, nodes, first = out, [net.start], {net.start: 0}
        for k, ei in enumerate(edges):
            x = net.other(ei, nodes[-1])
            nodes.append(x)
            i = first.setdefault(x, k + 1)
            piece = edges[i:k + 1]
            if 0 < i <= k and len(piece) < 0.6 * len(edges):  # back at a node passed before: is the piece between a spur?
                walked = sum(F.ln[e] for e in piece)
                if walked - sum(F.ln[e] for e in set(piece)) >= 0.3 * walked:
                    edges = edges[:i] + edges[k + 1:]
                    break
        else:
            return edges


def measure(P, edges, start=None):
    """The plain measures of a loop: nodes, metres, ascent, hours, metres walked twice beyond the free stem, and how
    often it meets its own way (a crossing, an eight's middle, a shared stretch = one meeting each; the start, the
    stem and a touch within the free stem do not count). start = its first node (default: the wish's start)."""
    net, F, ch = P.net, P.F, P.ch
    start = net.start if start is None else start
    nodes, at = [start], [0.0]
    for ei in edges:
        nodes.append(net.other(ei, nodes[-1]))
        at.append(at[-1] + F.ln[ei])
    if nodes[-1] != start:
        return None
    up = climb(net, nodes)
    free, total = ch.family["free_stem_m"], at[-1]
    stem = 0
    while stem < len(edges) // 2 and edges[stem] == edges[-1 - stem]:
        stem += 1
    count = {}
    for ei in edges:
        count[ei] = count.get(ei, 0) + 1
    doubled = sum(F.ln[e] * (c - 1) for e, c in count.items() if c > 1)
    seen, cross, in_run, last = {}, 0, False, -1e9
    for i in range(stem, len(nodes) - 1 - stem):
        j = seen.get(nodes[i])
        if j is not None and i - j > 1 and not (at[j] <= free and total - at[i] <= free):
            cross += not in_run and at[i] - last > 150.0
            in_run, last = True, at[i]
        else:
            in_run = False
        seen[nodes[i]] = i
    return {"edges": edges, "nodes": nodes, "m": total, "up": up, "hours": ch.time(total, up), "stem_m": at[stem],
            "doubled_m": max(0.0, doubled - min(at[stem], free)), "cross": int(cross)}


def pool(P, S):
    """All loops worth judging for this wish: [{edges, nodes, m, up, hours, …, origin, aim}]. Loops that fit but climb
    more than the wished maximum go to P.steep.
    Everything is planned in time, not metres: a turning point 300 m higher is further away than one on the flat."""
    net, ch, fam, Q = P.net, P.ch, P.ch.family, S["search"]
    (h_lo, h_hi), start = ch.hours, net.nodes[net.start]
    h_mid = 0.5 * (h_lo + h_hi)
    out, seen = [], set()
    P.steep = []

    road_max, F = fam["road_walk_max_m"], P.F
    roadside = lambda e: F.traffic[e] >= 0.45 and not F.built[e]
    calm_cost = [c * Q["road_repair"] if roadside(e) else c for e, c in enumerate(P.cost)] if road_max else None

    def add(points, origin, aim=(), costs=None):
        """Assemble, measure, keep if it fits. -> its measures (also when it does not fit), None if nothing new came.
        A loop that would be out for a long stretch along a road (on foot) is built once more with such stretches
        made dear: the way to the same turning points mostly exists without the road."""
        edges = assemble(P, S, points, costs)
        if edges and road_max and road_run(F, edges) > road_max:
            calm = {i: calm_cost for i in range(len(points) + 1)}
            for i, cost in (costs or {}).items():
                calm[i] = [c * Q["road_repair"] if roadside(e) else c for e, c in enumerate(cost)]
            edges = assemble(P, S, points, calm) or edges
        m = measure(P, edges) if edges and frozenset(edges) not in seen else None
        if m:
            seen.add(frozenset(edges))
            if m["cross"] <= S["form"]["max_cross"] and (1 - S["form"]["short_zero"]) * h_lo <= m["hours"] \
                    <= (1 + S["form"]["long_zero"]) * h_hi:
                # above the wished ascent a loop is not offered – kept aside only for when nothing here stays below it
                (out if m["up"] <= ch.up[1] else P.steep).append(dict(m, origin=origin, aim=list(aim)))
        return m

    reached = [v for v in range(len(net.nodes)) if 0 < P.len0[v] < ch.max_m and P.d0[v] < INF]
    bear = {v: bearing(start, net.nodes[v]) for v in reached}
    rise = lambda v: abs(net.nodes[v][2] - start[2]) if min(net.nodes[v][2], start[2]) > -1000 else 0.0
    hours = lambda metres, *via: ch.time(metres, max(rise(v) for v in via))  # a loop over these points, estimated
    there_and_back = lambda v: hours(2.2 * P.len0[v], v)  # the way home is another way, a little longer

    def anchors(fits):
        """Per direction the node that fits and is reached most pleasantly."""
        best = {}
        for v in reached:
            if fits(v):
                k, r = int(bear[v] * fam["sectors"] / 360.0), P.d0[v] / P.len0[v]
                if k not in best or r < best[k][0]:
                    best[k] = (r, v)
        return [v for _, v in best.values()]

    # --- turning points: one per direction (at the short and the long end of the range), then pairs a quarter turn apart
    for h in ((h_mid,) if h_hi < 1.25 * h_lo else (h_lo, h_hi)):
        for v in anchors(lambda v: 0.9 * h <= there_and_back(v) <= 1.1 * h):
            add([v], "Wendepunkt")
    if ch.up[0] >= 100.0:  # a climb is wished: turning points high enough
        for v in anchors(lambda v: 0.8 * ch.up[0] <= net.nodes[v][2] - start[2] <= ch.up[1] and h_lo <= there_and_back(v) <= h_hi):
            add([v], "Anstieg")
    A = anchors(lambda v: 0.6 * h_lo <= there_and_back(v) <= 0.8 * h_hi)
    pairs = []
    for a in A:
        est = lambda b: hours(P.len0[a] + 1.3 * dist(net.nodes[a], net.nodes[b]) + P.len0[b], a, b)
        fit = lambda b: max(0.0, h_lo - est(b), est(b) - h_hi)
        side = [b for b in A if 40.0 <= (bear[b] - bear[a]) % 360.0 <= 110.0]
        if side:
            pairs.append((fit(min(side, key=fit)), a, min(side, key=fit)))
    for _, a, b in sorted(pairs)[:fam["pairs"]]:
        add([a, b], "Wendepunkte")

    # --- chains through the stand-out areas, in order around the start, each through its heart
    areas = sorted(P.areas, key=lambda a: bearing(start, a["p"]))
    chains = []
    for i in range(len(areas)):
        for k in range(1, min(4, len(areas)) + 1):
            seq = [areas[(i + j) % len(areas)] for j in range(k)]
            if k == 1:  # one area: through its heart to the far side that the time allows
                turn = min(seq[0]["nodes"], key=lambda v: abs(there_and_back(v) - h_mid))
                pts = [seq[0]["heart"], turn] if P.len0[seq[0]["heart"]] < P.len0[turn] - 200.0 else [turn]
                est = there_and_back(turn)
            else:
                pts = [a["heart"] for a in seq]
                est = hours(P.len0[pts[0]] + P.len0[pts[-1]] + 1.35 * sum(dist(net.nodes[x], net.nodes[y]) for x, y in zip(pts, pts[1:])), *pts)
            if 0.7 * h_lo <= est <= 1.25 * h_hi:
                chains.append((-sum(a["m"] * a["good"] for a in seq), i, k, pts, [a["name"] for a in seq]))
    for _, _, _, pts, names in sorted(chains)[:fam["chains"]]:
        add(pts, "Kette", names)

    # --- a signed route that is itself a loop of fitting length: walk it round. Our map knows a route's ways, not their
    #     order. Where its ways close into a ring (stubs dropped), that ring is order enough and is walked exactly.
    #     Else pieces are missing (a way carries only its most important route): seen from its middle a loop lies all
    #     around (a line or an arc does not) – the route's points in 24 directions, each direction's mean point in
    #     order, are the ring; turning points on the route there, its ways cheap, the gaps by the best way.
    F, k = P.F, fam["ring_points"]
    for i, th in enumerate(F.things):
        if th["kind"] != "route" or len(th["dense"]) < 40:
            continue
        mid = [sum(p[0] for p in th["dense"]) / len(th["dense"]), sum(p[1] for p in th["dense"]) / len(th["dense"])]
        bins = {}
        for p in th["dense"]:
            bins.setdefault(int(bearing(mid, p) / 15.0), []).append(p)
        if len(bins) < 18:
            continue
        on = runs_along(net, th["dense"], [e for e in range(len(net.edges)) if i in F.named[e] and P.d0[net.edges[e][0]] < INF])
        whole, deg = sum(F.ln[e] for e in on), {}
        while on:
            deg = {}
            for e in on:
                for x in net.edges[e][:2]:
                    deg[x] = deg.get(x, 0) + 1
            stubs = {e for e in on if 1 in (deg[net.edges[e][0]], deg[net.edges[e][1]])}
            if not stubs:
                break
            on -= stubs
        if not deg:
            continue
        entry = min(deg, key=P.len0.__getitem__)
        m = sum(F.ln[e] for e in on)
        exact = [F.ln[e] if e in on else -1.0 for e in range(len(net.edges))]
        _, prev, _, way = dijkstra(net, entry, exact)
        if on and m >= 0.85 * whole and all(way[x] < INF for x in deg):  # a closed ring: its thirds as turning points
            if not 0.6 * h_lo <= ch.time(m + 2.0 * P.len0[entry], 0.0) <= 1.3 * h_hi:
                continue
            third = lambda vs: min(vs, key=lambda v: abs(way[v] - m / 3.0))
            a, first_third = third(deg), set()
            v = a
            while v >= 0:
                first_third.add(v)
                v = prev[v]
            rest = [v for v in deg if v not in first_third]
            if rest:
                add([entry, a, third(rest)], "Rundweg", [th["name"]], {1: exact, 2: exact, 3: exact})
            continue
        ring = [[sum(p[0] for p in b) / len(b), sum(p[1] for p in b) / len(b)] for _, b in sorted(bins.items())]
        about = max(sum(dist(a, b) for a, b in zip(ring, ring[1:] + ring[:1])), 25.0 * len(th["dense"])) * 1.2
        nodes = [x for x in {x for e in range(len(net.edges)) if i in F.named[e] for x in net.edges[e][:2]} if P.d0[x] < INF]
        entry = min(nodes, key=P.len0.__getitem__)
        if not 0.6 * h_lo <= ch.time(about + 2.0 * P.len0[entry], 0.0) <= 1.3 * h_hi:  # the mean points cut corners
            continue
        first = min(range(len(ring)), key=lambda j: dist(ring[j], net.nodes[entry]))
        aims = [min(nodes, key=lambda v: dist(net.nodes[v], ring[(first + j * len(ring) // k) % len(ring)])) for j in range(1, k)]
        cost = list(P.cost)
        for e in range(len(net.edges)):
            if i in F.named[e]:
                cost[e] *= Q["line"]
        add([entry] + aims, "Rundweg", [th["name"]], {j: cost for j in range(1, k + 1)})

    # --- lines: join at the nearest point, follow it (both ways), come home another way
    for ln in P.lines[:fam["lines"]]:
        entry = ln["near"]
        if P.len0[entry] > 0.35 * ch.target_m:
            continue
        along_cost = list(P.cost)
        for e in ln["edges"]:
            along_cost[e] *= Q["line"]
        d, _, _, along = dijkstra(net, entry, along_cost, max_len=0.75 * ch.max_m)
        on = [v for v in ln["nodes"] if d[v] < INF and v != entry and v in bear]
        if not on:
            continue
        turn = lambda u, v: abs((bearing(net.nodes[entry], net.nodes[u]) - bearing(net.nodes[entry], net.nodes[v]) + 180.0) % 360.0 - 180.0)
        half = lambda v: abs(hours(2.0 * (P.len0[entry] + along[v]), v) - h_mid)  # half the loop on the line, as a first guess
        first = min(on, key=half)
        other = [v for v in on if turn(v, first) >= 90.0]
        for guess in [first] + ([min(other, key=half)] if other else []):
            # the way home is not known before it is built (a river: is there a way back on the other bank?),
            # so three turning points are built per direction and the judging decides
            same = [u for u in on if turn(u, guess) < 60.0]
            for share in (1.0, 1.15, 0.85):
                add([entry, min(same, key=lambda u: abs(along[u] - share * along[guess]))], "Linie", [ln["name"]], {1: along_cost})
    return out

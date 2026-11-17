"""Judging loops: five axes, a few hard rules, one selection – and the words that say why.

  Natur       green beside the way and being deep inside nature
  Ruhe        away from traffic and houses
  Weg         the ground fits the character
  Besonderes  water, known routes, a rail trail, a named protected area, an edge with a view, castles, ruins, chapels,
              viewpoints, a tip – what makes it more than a way
  Form        a clean loop: about the wished length, nothing walked twice, not crossing itself, ascent that fits

The first four are means over the loop beyond the unavoidable way out, read on the place's yardstick: between
"poor here" (a low quantile of the ways in reach) and "as good as a loop gets here" (a high quantile of the ways beyond
the way out, `place.top`) – a loop's points depend only on the place and the loop itself, never on which other loops
were built (2026-11-16; before, 100 was the best loop of the pool). Form is absolute. The total is their mean,
weighted by the character.
"""
import re

import aims as AIMS
from net import direction, dist

AXES = ("natur", "ruhe", "weg", "besonderes", "form")
WORDS = {"natur": "Natur", "ruhe": "Ruhe", "weg": "Weg", "besonderes": "Besonderes", "form": "Form"}


def judge(P, S, cands, sights):
    """Fills every loop with axes, points, numbers and the reason a hard rule drops it (c["out"]).
    Each loop on its own: on each of the four place axes it is read on the place's yardstick (P.low .. P.top), so
    its points never depend on which other loops were built."""
    F, net, K = P.F, P.net, S["form"]
    for c in cands:
        core = [e for e in c["edges"] if P.core(e)] or c["edges"]
        m = sum(F.ln[e] for e in core)
        c["raw"] = {a: sum(P.v[a][e] * F.ln[e] for e in core) / m for a in AXES[:4]}
        c["sights"] = [dict(s) for s in {s["name"]: s for s in sights
                                          if any(dist(net.nodes[x], s["p"]) < 120.0 for x in c["nodes"][::3])}.values()]
        # going round a signed loop is what the place's makers laid out – but along a road is always bad (owner
        # 2026-11-16: on wheels the road rule beats the bonus; on foot such a loop is out anyway)
        ring = c["origin"] in ("Rundweg", "offiziell") and road_run(F, c["edges"]) <= K["ring_road_m"]
        c["raw"]["besonderes"] += K["sight"] * min(3, len(c["sights"])) + K["ring"] * ring
        # aims really reached (a summit, a lake gone round or along, a hut in the middle …) count more than passing
        # by; the more when the wish names their Ziel (aims.py)
        got = AIMS.reached(P, S, c["edges"])
        gain = [(AIMS.worth(S, a, P.ch.ziel), a) for a in got]
        c["raw"]["besonderes"] += sum(sorted((w for w, _ in gain), reverse=True)[:S["aims"]["most"]])
        c["reached"] = [{"ziel": a["ziel"], "what": a["what"], "name": a["name"], "ele": a["ele"], "at": round(a["at"], 2),
                         "round": round(a["round"], 2), "worth": a["worth"], "goal": a["ziel"] in P.ch.ziel and w > 0.0} for w, a in gain]
        c["variety"] = variety(P, S, core)
        ax = {a: P.judge(a, c["raw"][a]) for a in AXES[:4]}
        V = S["quality"]["variety"]  # quality trails: the landscape changes along the way, not one uniform forest
        ax["natur"] *= 1.0 - V["share"] + V["share"] * min(1.0, c["variety"] / V["per_km"])
        _finish(P, S, c, ax)
    return cands


def variety(P, S, edges):
    """Landscape changes per km along the walk (Deutscher Wanderverband: 'Abwechslung / Landschaftswechsel'): every
    step_m stretch gets its dominant kind – village, water, forest, open land – and each change of it counts."""
    F, step = P.F, S["quality"]["variety"]["step_m"]
    kind = lambda e: 0 if F.built[e] else 1 if F.water[e] > 0 else 2 if F.green[e] >= 0.5 else 3
    kinds, acc, cur = [], 0.0, [0.0] * 4
    for e in edges:
        cur[kind(e)] += F.ln[e]
        acc += F.ln[e]
        if acc >= step:
            kinds.append(max(range(4), key=cur.__getitem__))
            acc, cur = 0.0, [0.0] * 4
    total = sum(F.ln[e] for e in edges)
    return round(sum(a != b for a, b in zip(kinds, kinds[1:])) / max(1.0, total / 1000.0), 2)


def _finish(P, S, c, ax):
    ch, K, seen = P.ch, S["form"], c.pop("sights")
    fam, clamp, h = ch.family, lambda x: max(0.0, min(1.0, x)), c["hours"]
    # inside the wished ranges of time and ascent every loop is equally fine; a bit short or flat falls off gently
    parts = {"Zeit": 1.0 - clamp(max((ch.hours[0] - h) / (K["short_zero"] * ch.hours[0]), (h - ch.hours[1]) / (K["long_zero"] * ch.hours[1]))),
             "Höhenmeter": 1.0 - 0.7 * clamp((ch.up[0] - c["up"]) / max(ch.up[0], 1.0)),
             "doppelt": 1.0 - clamp(c["doubled_m"] / c["m"] / K["doubled_zero"]),
             "kreuzt sich": K["cross"] ** c["cross"]}
    ax["form"] = parts["Zeit"] * parts["Höhenmeter"] * parts["doppelt"] * parts["kreuzt sich"]
    c["axes"] = {a: round(ax[a], 3) for a in AXES}
    c["score"] = round(100.0 * sum(ch.axes[a] * ax[a] for a in AXES), 1)
    c["form_parts"] = {k: round(v, 2) for k, v in parts.items() if v < 0.995}
    n = c["numbers"] = numbers(P, c, seen)
    c["water_goal"] = P.ch.water and n["water_pct"] >= 20
    c["out"] = "langes Stück an einer Straße mit Autos" if 0 < fam["road_walk_max_m"] < n["road_longest_m"] else None


def numbers(P, c, sights):
    """The plain facts of a loop for the card and for judging – absolute, not relative to the place."""
    F, net, edges, total = P.F, P.net, c["edges"], c["m"]
    pct = lambda test: int(round(100.0 * sum(F.ln[e] for e in edges if test(e)) / total))
    km_by = {}
    for e in edges:
        for key in [("reserves", F.areas[F.area[e]])] * (F.area[e] > 0) + [
                ({"route": "known", "lake": "lakes"}.get(F.things[i]["kind"]), F.things[i]["name"]) for i in F.named[e]]:
            if key[0]:
                km_by[key] = km_by.get(key, 0.0) + F.ln[e] / 1000.0
    top = lambda kind, least: [[nm, round(km, 1)] for (k, nm), km in sorted(km_by.items(), key=lambda x: -x[1]) if k == kind and km >= least][:4]
    return {"km": round(total / 1000.0, 2), "hours": round(c["hours"], 2), "ascent": int(c["up"]),
            "forest_pct": pct(lambda e: F.green[e] >= 0.5), "deep_pct": pct(lambda e: F.depth[e] >= 0.5),
            "town_pct": pct(lambda e: F.built[e]), "street_pct": pct(lambda e: F.traffic[e] >= 0.15),
            "road_longest_m": int(road_run(F, edges)), "signed_pct": pct(lambda e: F.signed[e] > 0),
            "water_km": round(sum(F.ln[e] for e in edges if F.water[e] > 0) / 1000.0, 1),
            "water_pct": int(round(100.0 * sum(F.ln[e] * F.water[e] for e in edges) / total)),  # big water counts full, small half
            "rail_km": round(sum(F.ln[e] for e in edges if F.rail[e]) / 1000.0, 1),
            "ground_pct": [pct(lambda e, g=g: F.ground[e] == g) for g in range(4)],
            "ferry_m": int(sum(F.ln[e] for e in set(edges) if F.ferry[e])), "self_cross": c["cross"],
            "doubled_pct": int(round(100.0 * c["doubled_m"] / total)), "stem_m": int(c["stem_m"]),
            "reserves": top("reserves", 0.8), "known": top("known", 0.8), "lakes": top("lakes", 0.4),
            "rim_km": round(sum(F.ln[e] for e in edges if F.rim[e] >= 0.5) / 1000.0, 1),
            "sights": [[s["what"], s["name"]] for s in sights][:6],
            "path_pct": pct(lambda e: F.width[e] == 0), "track_pct": pct(lambda e: F.width[e] == 1),
            "variety": c.get("variety", 0.0), "aims": c.get("reached", [])}


def road_run(F, edges):
    """The longest stretch in metres along a road with cars outside a town."""
    run = longest = 0.0
    for e in edges:
        run = run + F.ln[e] if F.traffic[e] >= 0.45 and not F.built[e] else 0.0
        longest = max(longest, run)
    return longest


def short(name, most=30):
    """A protected area's name short enough for a title: without brackets and what follows " mit "; over 40
    characters without a place tail (" zwischen …", " von …"); a list ("A, B und C", "A - B") gives its more telling
    part (fewer small words; the first when equal) while it is longer than `most`."""
    s = re.sub(r"\s*\([^)]*\)", "", name).split(" mit ")[0].strip()
    if len(s) > 40:
        s = re.split(r" (?:zwischen|von|vom|bei|südlich|nördlich|westlich|östlich) ", s)[0]
    small = lambda p: sum(1 for w in p.split() if not w[:1].isupper())
    for sep in (", ", " - ", " – ", " / ", " und "):
        while len(s) > most and re.search(r"(?<!-)" + sep, s):
            a, b = re.split(r"(?<!-)" + sep, s, maxsplit=1)
            s = a if small(a) <= small(b) else b
    return s or name


def short_route(name):
    """A signed route's name for a title or the line: without brackets; over 40 characters only its head (before
    ", " or " - ": a stage, a part), then without its ref ("NST · …")."""
    s = re.sub(r"\s*\([^)]*\)", "", name).strip()
    for sep in (", ", " - "):
        if len(s) > 40 and sep in s:
            s = s.split(sep)[0]
    if len(s) > 40 and " · " in s:
        s = s.split(" · ", 1)[1]
    return s or name


def explain(P, c):
    """Title, the reason line and the five-word verdict of a loop."""
    n, net = c["numbers"], P.net
    far = max(c["nodes"], key=lambda x: dist(net.nodes[net.start], net.nodes[x]))
    where = "Richtung %s" % direction(net.nodes[net.start], net.nodes[far])
    # what the loop leads to names it first: the wished Ziel's aims, then the finest reached (a summit, a lake …)
    # (one per kind of Ziel: a place to eat the one nearest the middle; a lake when gone along a good part)
    worthy = lambda a: (a["goal"] or a["worth"] >= 0.7) and (a["what"] != "lake" or a["round"] >= 0.5) and a["name"] != "Aussichtspunkt"
    per_kind = {}
    for a in sorted((a for a in n["aims"] if worthy(a)),
                    key=lambda a: (not a["goal"], abs(a["at"] - 0.5) if a["ziel"] == "einkehr" else
                                   -a["worth"] * (1 + a["round"]) - a["ele"] / 10000.0)):  # of two summits the higher
        per_kind.setdefault(a["ziel"], a)
    lead = [a["name"] for a in sorted(per_kind.values(), key=lambda a: (not a["goal"], -a["worth"]))]
    lead = [x for i, x in enumerate(lead) if not any(y in x or x in y for y in lead[:i])]  # "Eckbauer", not "Gasthof Eckbauer"
    main = list(dict.fromkeys(lead[:2] + [short(x[0]) for x in n["reserves"][:2]] + [x[0] for x in n["lakes"][:1]]
                              + [short_route(x[0]) for x in n["known"][:1] if x[1] >= 0.25 * n["km"]]
                              + (["Bahntrasse"] if n["rail_km"] >= 0.25 * n["km"] else [])))[:2]
    title = " – ".join(main)
    c["name"] = (title if len(title) <= 45 else main[0]) if main else next((a for a in c["aim"] if " bei " in a or " im " in a), where)
    c["bearing"] = where
    km = lambda rows, pre: ["%s km %s%s" % (str(k).replace(".", ","), pre, nm) for nm, k in rows]
    kind = lambda nm: nm if "schutzgebiet" in nm.lower() or nm.startswith(("Nationalpark", "Naturpark")) else "Schutzgebiet " + nm
    c["reasons"] = (aim_bits(n) + km([[kind(nm), k] for nm, k in n["reserves"]], "im ") + ["%s km %s" % (str(k).replace(".", ","), am(nm)) for nm, k in n["lakes"]] + km(n["known"], "auf ")
                    + (["%s km am Wasser" % str(n["water_km"]).replace(".", ",")] if n["water_km"] >= 1 and not n["lakes"] else [])
                    + (["%s km Bahntrasse" % str(n["rail_km"]).replace(".", ",")] if n["rail_km"] >= 1 else [])
                    + ["%s %s" % (w, nm) if nm != w else w for w, nm in n["sights"][:1]] + (["mit Fähre"] if n["ferry_m"] else []))[:5]
    ch, h = P.ch, c["hours"]
    edge = lambda v, rng: rng[1] > rng[0] and (v - rng[0]) / (rng[1] - rng[0])
    c["hints"] = (["↑ %d m – mehr als gewünscht (%d m)" % (c["up"], ch.up[1])] if c["up"] > ch.up[1] else []) \
        + (["etwas kürzer als gewünscht"] if h < ch.hours[0] else ["etwas länger als gewünscht"] if h > ch.hours[1] else
                  ["am oberen Rand der gewünschten Zeit"] if edge(h, ch.hours) > 0.9 else []) \
        + (["weniger Höhenmeter als gewünscht"] if c["up"] < ch.up[0] else
           ["am oberen Rand der Höhenmeter"] if 0.9 < edge(c["up"], ch.up) <= 1.0 else []) \
        + (["%d m an einer Straße am Stück" % n["road_longest_m"]] if n["road_longest_m"] >= 200 else []) \
        + (["%d %% doppelt" % n["doubled_pct"]] if n["doubled_pct"] >= 8 else []) \
        + (["kreuzt sich einmal"] if n["self_cross"] else []) + (["%d %% durch Häuser" % n["town_pct"]] if n["town_pct"] >= 30 else [])
    c["line"] = line(n)
    ranked = sorted(AXES, key=lambda a: -c["axes"][a])
    weak = [a for a in ranked[::-1] if c["axes"][a] < 0.5][:2]
    c["why"] = "stark bei %s" % " und ".join(WORDS[a] for a in ranked[:2]) + (
        " · schwächer bei %s" % " und ".join(WORDS[a] + (" (%s)" % ", ".join(c["form_parts"]) if a == "form" and c["form_parts"] else "")
                                               for a in weak) if weak else "")
    return c


def um(name):
    """'rund um den Lautersee', 'rund um die Dhünntalsperre', 'rund um das Pulvermaar'."""
    low = name.lower()
    art = "die " if low.endswith(("sperre", "bucht", "aue", "lache")) else "das " if low.endswith("maar") else "den "
    return "rund um " + art + name


def aim_bits(n):
    """The aims a loop reaches, as words for its line: a summit with its height, a lake gone round, a place to eat in
    the middle, a spring, a museum (castles, chapels, viewpoints, waterfalls are said with the sights)."""
    out = []
    for a in sorted(n.get("aims") or [], key=lambda a: (not a["goal"], a["at"])):
        w, nm = a["what"], a["name"]
        if w == "peak":
            out.append("Gipfel %s%s" % (nm, " (%d m)" % a["ele"] if a["ele"] else ""))
        elif w == "lake" and a["round"] >= 0.7:
            out.append(um(nm))
        elif w in ("hut", "restaurant", "cafe") and 0.2 <= a["at"] <= 0.8 and not any(x.startswith("Einkehr") for x in out):
            mid = min((b for b in n["aims"] if b["ziel"] == "einkehr"), key=lambda b: abs(b["at"] - 0.5))
            out.append("Einkehr " + mid["name"])
        elif w == "spring":
            out.append(nm if "quelle" in nm.lower() or "brunn" in nm.lower() else "Quelle " + nm)
        elif w == "museum":
            out.append(nm if "museum" in nm.lower() else "Museum " + nm)
    return list(dict.fromkeys(out))[:3]


def am(name):
    """'am Elbsee', 'an der Dhrontalsperre'."""
    return ("an der " if name.lower().endswith(("sperre", "bucht", "aue", "lache")) else "am ") + name


OWN_WORD = ("burg", "schloss", "ruine", "kapelle", "höhle", "wasserfall", "turm", "blick", "kirche", "aussicht", "fels")


def line(n):
    """One line on what the loop really passes – protected areas, a lake, castles / ruins / chapels, edges with a view,
    viewpoints, a known route, water, a rail trail – from the numbers of the card, nothing invented."""
    num = lambda x: str(x).replace(".", ",")
    kind = lambda nm: nm if "schutzgebiet" in nm.lower() or nm.startswith(("Nationalpark", "Naturpark")) else "das Schutzgebiet " + nm
    say = lambda what, nm: nm if nm != what and any(w in nm.lower() for w in OWN_WORD) else what if nm == what else "%s %s" % (what, nm)
    both = lambda xs: " und ".join(xs) if len(xs) < 3 else ", ".join(xs[:-1]) + " und " + xs[-1]
    named = [(w, nm) for w, nm in n["sights"] if w != "Aussichtspunkt" and nm != w]
    things = [say(w, nm) for w, nm in named] + [w for w, nm in n["sights"] if nm == w and w != "Aussichtspunkt"
                                                and w not in {x for x, _ in named}]  # an unnamed one only when none of its kind has a name
    views = [nm for w, nm in n["sights"] if w == "Aussichtspunkt"]
    rounds = {a["name"] for a in n.get("aims") or [] if a["what"] == "lake" and a["round"] >= 0.7}
    bits = aim_bits(n) + (["durch " + both([kind(short(nm)) for nm, _ in n["reserves"][:2]])] if n["reserves"] else []) \
        + ([am(n["lakes"][0][0])] if n["lakes"] and n["lakes"][0][0] not in rounds else []) \
        + (["vorbei an " + both(list(dict.fromkeys(things))[:3])] if things else []) \
        + (["%s km an Kanten mit Weitblick" % num(n["rim_km"])] if n["rim_km"] >= 0.5 else []) \
        + (["Aussicht " + (views[0] if views[0] != "Aussichtspunkt" else "unterwegs")] if views else []) \
        + ["%s km auf %s" % (num(k), short_route(nm)) for nm, k in n["known"][:1] if k >= 0.25 * n["km"]] \
        + (["%s km am Wasser" % num(n["water_km"])] if n["water_km"] >= 1 and not n["lakes"] else []) \
        + (["%s km Bahntrasse" % num(n["rail_km"])] if n["rail_km"] >= 1 else [])
    text = ", ".join(bits) or "%d %% am Wald, %d %% auf Naturwegen" % (n["forest_pct"], n["ground_pct"][2] + n["ground_pct"][3])
    return text[:1].upper() + text[1:]


def choose(P, S, cands):
    """One selection over all cards – our loops and the official loops that fit the wish alike. The best first;
    further ones must be different (no two share more than `overlap` of the shorter one's way beyond the way out) and
    not far behind; what stands out in reach (a top line, an area) gets the loop that uses it best, if that loop is
    good enough. Between similar loops an official one wins unless it is clearly worse (`official_ahead` points).
    When fewer than `least` are shown, further different loops may lie up to `least_behind` points behind; fewer is
    fine when the rest is poor."""
    F, K = P.F, S["select"]
    lo, hi = P.ch.hours
    fits = lambda c: lo <= c["hours"] <= hi  # the wished time is the wish: loops inside it come before loops near it
    pts = lambda c: c["score"] + (K["official_ahead"] if c.get("official") else 0.0)
    ranked = sorted((c for c in cands if not c["out"]), key=lambda c: (not fits(c), -pts(c)))
    if not ranked:
        return []
    core = {id(c): {e for e in c["edges"] if P.core(e)} or set(c["edges"]) for c in ranked}
    length = lambda es: sum(F.ln[e] for e in es) or 1.0

    def fresh(c, shown):
        return all(length(core[id(c)] & core[id(o)]) <= K["overlap"] * min(length(core[id(c)]), length(core[id(o)])) for o in shown)
    uses = lambda c, thing: length(core[id(c)] & thing["edges"]) >= (0.25 * P.target if thing["kind"] != "area" else min(2500.0, 0.3 * thing["m"]))
    top = pts(ranked[0])
    # what the wish names is always shown: per chosen Ziel the best loop that leads to it (and first); for the
    # gemütlich end the best loop along the first known route here
    route = next((ln for ln in P.lines if ln["kind"] == "route" and ln["rank"] >= 0.9), None) if P.ch.signed >= 1.0 else None
    shown = [ranked[0]]
    for z in P.ch.ziel:
        if not any(z in goals(c) for c in shown):
            shown += [c for c in ranked if z in goals(c)][:1]
    if route:  # the clean loop that stays on the route longest
        on_it = [c for c in ranked if uses(c, route) and c["axes"]["form"] >= 0.5]
        shown += [c for c in sorted(on_it, key=lambda c: -length(core[id(c)] & route["edges"]))[:1] if c not in shown]
    # what stands out here is shown if its best loop is good enough: an official loop inside the wished time (what
    # the place's makers laid out), the top lines, then the areas
    for c in ranked:
        if c.get("official") and fits(c) and c not in shown and len(shown) < K["max"] and pts(c) >= top - K["cover_behind"] \
                and fresh(c, shown):
            shown.append(c)
    # the finest aims in reach (a summit, a lake, a castle) get their best loop if it is good enough
    finest = {}
    for c in ranked:
        for a in c["reached"]:
            if a["worth"] >= 0.9 and (a["what"] != "lake" or a["round"] >= 0.5):
                finest.setdefault(a["name"], c)
    for c in list(finest.values())[:2]:
        if c not in shown and len(shown) < K["max"] and pts(c) >= top - K["cover_behind"] and fresh(c, shown):
            shown.append(c)
    for thing in [ln for ln in P.lines if ln["rank"] >= 0.9][:2] + P.areas:
        best = next((c for c in ranked if uses(c, thing)), None)
        if best and best not in shown and len(shown) < K["max"] and pts(best) >= top - K["cover_behind"] and fresh(best, shown):
            shown.append(best)
    for behind, most in ((K["behind"], K["max"]), (K["least_behind"], K["least"])):
        for c in ranked:
            if len(shown) >= most:
                break
            if c not in shown and pts(c) >= top - behind and fresh(c, shown):
                shown.append(c)
    return sorted(shown[:K["max"]], key=lambda c: order(P, c))


def goals(c):
    """The wished Ziel kinds a loop leads to (a reached aim of that kind; Wasser also: a fifth of the way at water)."""
    out = {a["ziel"] for a in c["reached"] if a["goal"]}
    return out | ({"wasser"} if c.get("water_goal") else set())


def order(P, c):
    """The order of the list: with a Ziel the loops that lead to it first, then loops about inside the wished time, each
    by points. "About": a few minutes short or long count as inside (owner 2026-11-17: „wenn nur ganz knapp drüber,
    drunter – dann auch oben lassen“) – 10 % of the range's end, at least 10 minutes."""
    lo, hi = P.ch.hours
    slack = lambda h: max(0.1 * h, 1.0 / 6.0)
    inside = lo - slack(lo) <= c["hours"] <= hi + slack(hi)
    return not (P.ch.ziel and goals(c)), not inside, -c["score"]

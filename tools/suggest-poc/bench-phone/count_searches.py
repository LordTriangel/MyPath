"""How many graph searches one POC run makes, and where its time goes.

  python3 tools/suggest-poc/bench-phone/count_searches.py Hilden hike 2
  python3 tools/suggest-poc/bench-phone/count_searches.py Zons bike 3

Wraps net.dijkstra / generate.assemble / the /graph fetch of the POC (nothing in the POC changes) and prints one JSON
line: nodes, links, searches by kind (full = from the start without end, stop = start → turning point, bounded = along a
line with a length limit), nodes settled per kind, assembly calls, and the run's own phase times.
"""
import json
import os
import sys
import time
import urllib.request

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import generate  # noqa: E402
import net  # noqa: E402
import place  # noqa: E402
import suggest as S  # noqa: E402
from character import wish as make_wish  # noqa: E402

stats = {"full": 0, "stop": 0, "bounded": 0, "settled": {"full": 0, "stop": 0, "bounded": 0}, "assemble": 0,
         "dijkstra_s": 0.0, "graph_fetch_s": 0.0, "graph_parse_s": 0.0}
_dij = net.dijkstra


def dijkstra(n, src, cost, pen=None, stop=None, max_len=None):
    t = time.time()
    r = _dij(n, src, cost, pen, stop, max_len)
    stats["dijkstra_s"] += time.time() - t
    k = "stop" if stop is not None else "bounded" if max_len is not None else "full"
    stats[k] += 1
    stats["settled"][k] += sum(1 for x in r[0] if x < net.INF)
    return r


place.dijkstra = generate.dijkstra = dijkstra
_asm = generate.assemble


def assemble(*a, **k):
    stats["assemble"] += 1
    return _asm(*a, **k)


generate.assemble = assemble
_open = urllib.request.urlopen


def urlopen(*a, **k):
    t = time.time()
    r = _open(*a, **k)
    stats["graph_fetch_s"] += time.time() - t
    return r


urllib.request.urlopen = urlopen
_init = net.Net.__init__


def init(self, text):
    t = time.time()
    _init(self, text)
    stats["graph_parse_s"] += time.time() - t
    stats["nodes"], stats["edges"], stats["graph_text_mb"] = len(self.nodes), len(self.edges), round(len(text) / 1e6, 1)


net.Net.__init__ = init

if __name__ == "__main__":
    st = S.load_settings()
    pl = S.find_place(sys.argv[1], st)
    h = sys.argv[3] if len(sys.argv) > 3 else "2"
    hours = [float(x) for x in h.split("-")] if "-" in h else float(h)
    r = S.suggest(make_wish({"start": pl[1], "start_name": pl[0], "activity": sys.argv[2], "hours": hours}, st), st)
    for k in ("dijkstra_s", "graph_fetch_s", "graph_parse_s"):
        stats[k] = round(stats[k], 2)
    stats.update(place=sys.argv[1], activity=sys.argv[2], hours=h, start=pl[1], target_km=r["target_km"],
                 seconds=r["seconds"], times=r["times"], pool=len(r["candidates"]))
    print(json.dumps(stats, ensure_ascii=False))

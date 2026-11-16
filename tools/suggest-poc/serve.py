#!/usr/bin/env python3
"""Tour suggestions – the local page.   python3 tools/suggest-poc/serve.py   → http://localhost:8790

Other ports: --port / SUGGEST_PORT; route service: SUGGEST_SERVICE=http://127.0.0.1:<port> (started when it is not
running). settings.json is read again on every request. Runs and comments go to ~/onpath-suggest-poc/ (SUGGEST_DATA).
Official loops near the start come from the loops files of official.py (Rheinland-Pfalz, Nordrhein-Westfalen); /vergleich shows
the comparison sets' numbers (official-compare/summary.json).
"""
import html
import argparse
import json
import os
import re
import time
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import named
import official
import suggest as S
from character import wish as make_wish


class H(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def send(self, body, ctype="application/json", code=200):
        if not isinstance(body, bytes):
            body = (body if isinstance(body, str) else json.dumps(body, ensure_ascii=False)).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", ctype + "; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        u = urllib.parse.urlparse(self.path)
        q = {k: v[0] for k, v in urllib.parse.parse_qs(u.query).items()}
        # behind a path prefix (a preview pane) the calls arrive as <prefix>/places …: the end of the path decides
        tail = re.search(r"/(places|place|known|vergleich|run/[^/]+)$", u.path)
        what = tail.group(1) if tail else ""
        st = S.load_settings()
        if what == "places":
            return self.send({"places": st["places"], "version": st["version"],
                              "activities": {k: v["label"] for k, v in st["activities"].items() if k != "_about"},
                              "paces": {k: v["pace"] for k, v in st["activities"].items() if k != "_about"}})
        if what == "place":
            r = S.find_place(q.get("q", ""), st)
            return self.send({"name": r[0], "lonlat": r[1]} if r else {"error": "Ort nicht gefunden"})
        if what == "known":  # the known signed routes around a point, from our own map areas (the page's overlay)
            try:
                return self.send({"routes": named.known_lines([float(q["lon"]), float(q["lat"])], float(q.get("reach", 12000)),
                                                              q.get("activity", "hike") != "hike")})
            except Exception as e:  # shown as "no overlay" on the page – a POC has no other place for it
                return self.send({"routes": [], "error": "%s: %s" % (type(e).__name__, e)})
        if what == "vergleich":
            return self.send(compare_page(), "text/html")
        if what.startswith("run/"):
            p = os.path.join(S.DATA, "runs", os.path.basename(what) + ".json")
            if os.path.isfile(p):
                with open(p, "rb") as f:
                    return self.send(f.read())
            return self.send({"error": "Lauf nicht gefunden"}, code=404)
        if u.path.endswith("/favicon.ico"):
            return self.send({"error": "not found"}, code=404)
        with open(os.path.join(S.HERE, "page.html"), "rb") as f:  # any other address shows the page
            return self.send(f.read(), "text/html")

    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
        try:
            if self.path.endswith("/suggest"):
                st = S.load_settings()
                w = make_wish(body, st)
                res = S.suggest(w, st, official.near(w, st) if body.get("official", True) else None)
                S.save_run(res)
                return self.send(res)
            if self.path.endswith("/feedback"):
                os.makedirs(S.DATA, exist_ok=True)
                body["at"] = time.strftime("%Y-%m-%dT%H:%M:%S")
                with open(os.path.join(S.DATA, "feedback.jsonl"), "a", encoding="utf-8") as f:
                    f.write(json.dumps(body, ensure_ascii=False) + "\n")
                return self.send({"ok": True})
        except Exception as e:  # shown on the page – a POC has no other place for it
            return self.send({"error": "%s: %s" % (type(e).__name__, e)})
        self.send({"error": "not found"}, code=404)


def compare_page():
    """The comparison sets with their medians per version – what this round of changes did (official.py summary)."""
    try:
        with open(os.path.join(official.OUT, "summary.json"), encoding="utf-8") as f:
            sets = json.load(f)["sets"]
    except (OSError, ValueError):  # expected: no comparison made on this computer yet
        sets = []
    e = html.escape
    rows = "".join("<h2>%s <small>(%d Runden)</small></h2><table><tr><th>Stand</th><th>Karte</th><th>erste Runde auf der offiziellen</th>"
                   "<th>erste ist sie (≥ 80 %%)</th><th>beste gezeigte</th><th>bester Pool</th><th>Rang der offiziellen</th>"
                   "<th>offizielle vorn</th></tr>%s</table>" % (e(s["title"]), s["rows"][0]["n"], "".join(
                       "<tr><td>%s</td><td>%s</td><td>%d %%</td><td>%d</td><td>%d %%</td><td>%d %%</td><td>%d</td><td>%d</td></tr>" % (
                           e(r["label"]), e(r["map"]), r["first"], r["is_first"], r["shown"], r["pool"], r["rank"], r["off_top"])
                       for r in s["rows"])) for s in sets)
    return ("<!doctype html><meta charset='utf-8'><title>Vergleich</title><style>body{font:14px/1.4 system-ui,sans-serif;"
            "margin:20px;max-width:960px;color:#22302a} table{border-collapse:collapse} td,th{border:1px solid #d9ded9;"
            "padding:4px 8px;text-align:right} th{background:#f2f4f1;font-weight:600} td:first-child,td:nth-child(2){text-align:left}"
            "</style><h1>Unsere Runden gegen offizielle Rundwege</h1><p>Je Rundweg ein Wunsch wie aus ihm gemacht (Start am "
            "Parkplatz, Aktivität, seine Zeit ±15 %%, Höhenmeter 60–130 %%). Gemessen: wie viel der offiziellen Runde unsere "
            "erste / beste gezeigte / beste aller gebauten Runden abläuft (Median), wie oft unsere erste die offizielle ist, "
            "und welchen Rang die offizielle in unseren eigenen Punkten hätte. „Karte heute“ = unsere Karte, wie sie ist; "
            "„vollständig“ = als ob jeder Rundweg ganz in der Karte stünde. Stand: base / neu = vor / nach der Runde vom 15.11.; "
            "b16 / f16 = vor / nach der Runde vom 16.11. (Räder: Straßenregel vor dem Rundweg-Bonus; kürzere Titel; NRW-Sätze neu).</p>%s" % (rows or "<p>Noch kein Vergleich gerechnet.</p>"))


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=int(os.environ.get("SUGGEST_PORT", 8790)))
    a = ap.parse_args()
    S.ensure_service()
    print("Tourvorschläge: http://localhost:%d   – Daten: %s" % (a.port, S.DATA))
    ThreadingHTTPServer(("127.0.0.1", a.port), H).serve_forever()

#!/usr/bin/env python3
"""Compares a route dump of the Kotlin port with the Java reference, route by route.
Usage: python3 -I compare.py fixtures/reference.txt.gz out/<impl>-dump.txt   → one line per route, exit 1 on any difference."""
import gzip, sys

def load(p):
    op = gzip.open if p.endswith('.gz') else open
    routes, cur = {}, None
    with op(p, 'rt', encoding='utf-8') as f:
        for line in f:
            line = line.rstrip('\n')
            if line.startswith('R '):
                cur = line.split(' ')[1]; routes[cur] = []
            elif cur is not None:
                routes[cur].append(line)
    return routes

ref, got = load(sys.argv[1]), load(sys.argv[2])
bad = 0
for name, r in ref.items():
    g = got.get(name)
    if g is None:
        print(f'MISSING   {name}'); bad += 1; continue
    if g == r:
        kind = 'error text' if r and r[0].startswith('E ') else f'{sum(1 for l in r if l[0] == "N")} nodes, {sum(1 for l in r if l[0] == "H")} hints'
        print(f'IDENTICAL {name}  ({kind})'); continue
    bad += 1
    rn = [l.split(' ')[1:3] for l in r if l.startswith('N ')]; gn = [l.split(' ')[1:3] for l in g if l.startswith('N ')]
    first = next((i for i, (a, b) in enumerate(zip(r, g)) if a != b), min(len(r), len(g)))
    print(f'DIFFERENT {name}  same node positions: {rn == gn} · lines {len(r)} / {len(g)} · first difference at line {first}:')
    print('    java  : ' + (r[first] if first < len(r) else '<end>')[:160])
    print('    kotlin: ' + (g[first] if first < len(g) else '<end>')[:160])
print(f'{len(ref) - bad} of {len(ref)} routes identical')
sys.exit(1 if bad else 0)

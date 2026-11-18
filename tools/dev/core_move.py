#!/usr/bin/env python3
"""core_move.py – moves pure Kotlin from :app to :core (docs/design/umbau-phase-1.md, piece B). stdlib only.

  core_move.py plan  [--only pkg,pkg] [--files a/B.kt,c/D.kt] [--no-tests]   print the closed set that can move, the blocked
                                                                                candidates with their first reason, the tests
  core_move.py move  [same options] [--dry-run]                              do it: git mv + path rewrites (below)

The set (`plan`): app files of the candidate packages (CAND) and the hub files named in the paper (HUBS) that
  (a) have no android. / androidx. / org.maplibre. / com.google.android. import or fully-qualified use, no BuildConfig / R.
      use, no btools. use (unless in ALLOW_BTOOLS: BRouter jars are compileOnly in :core), no library :core does not have, and
  (b) reference – by import, same-package identifier, wildcard import or fully-qualified name – only files that are in
      the set too (or are in :core already: a second run builds on the first).
`--only` narrows the packages, `--files` names exactly the candidates. Identifier matching is approximate: `plan` is a
PROPOSAL, the compiler decides (a file :core refuses goes back with `git mv`; see the ends of `move`'s output).

Tests move when they use at least one moved class and nothing that stays in :app (and no android / paparazzi), together with
the test helpers they need. A helper that tests of both modules need goes to core/src/testkit/kotlin (one extra test source
dir of BOTH modules – simpler than java-test-fixtures: no extra Gradle variant, the file is just compiled twice). Test resources:
a first-level folder of app/src/test/resources that a moved test reads moves to core/src/test/resources, which is also a
resource dir of :app's tests (app/build.gradle.kts), so class-loader reads keep working in both; File("src/test/resources/…")
paths are rewritten.

`move` rewrites: path literals src/main/java/com/onpath/app/<pkg>/<F>.kt of moved files in every test (app tests: ../core/…,
moved tests: own module), File(".../$f") readers → sourceFile("$f") (core/src/testkit/…/Sources.kt), web/parity.json keys,
app/lint-baseline.xml issues of moved files (dropped: :app:lintDebug no longer sees them). Never rewritten (by hand, once, in
the commit that introduced them): web/engine/build.gradle.kts, web/parity/check.mjs, SharedFilesRuleTest, .claude/rules globs,
tools/*.
"""
import argparse, collections, json, os, re, subprocess, sys

ROOT = subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True).stdout.strip() or os.getcwd()
APP_MAIN, CORE_MAIN = "app/src/main/java/com/onpath/app", "core/src/main/kotlin/com/onpath/app"
APP_TEST, CORE_TEST = "app/src/test/java/com/onpath/app", "core/src/test/kotlin/com/onpath/app"
CORE_KIT = "core/src/testkit/kotlin/com/onpath/app"
APP_RES, CORE_RES = "app/src/test/resources", "core/src/test/resources"

CAND = "domain navigation sync time gpx rides setup net routing suggest search plansync cells backup".split()
HUBS = [  # pure hub files outside the candidate packages (the paper: "the pure hubs"); missing ones are skipped
    "data/Geo.kt", "data/Format.kt", "data/AtomicFiles.kt", "debug/EventLog.kt", "debug/UserText.kt",
    "data/AppSettings.kt", "data/HoldSetting.kt",
    # piece A (untangling) creates these; they join the plan as soon as they exist
    "data/Tour.kt", "recording/GpsStatus.kt", "recording/MoveWatch.kt", "recording/PointFilter.kt",
]
ALLOW_BTOOLS = {"suggest/NetReader.kt", "routing/Junctions.kt", "routing/Rd5Parts.kt"}  # BRouter jars: compileOnly in :core
ANDROID = re.compile(r"^(android\.|androidx\.|org\.maplibre\.|com\.google\.android)")
# libraries :core has (build.gradle.kts); an import outside this list blocks the file
OK_IMPORT = ("java.", "javax.", "kotlin.", "kotlinx.coroutines", "com.google.gson", "org.json", "org.w3c.", "org.xml.",
             "com.onpath.strings", "org.jetbrains.compose.runtime", "org.jetbrains.compose.resources", "btools.", "org.junit")
TEST_ANDROID = ("android.", "androidx.", "app.cash", "org.robolectric", "org.maplibre", "com.google.android")
DECL = re.compile(r"^(?:@\w+(?:\([^)]*\))?\s+)*(?:(?:public|internal|private|data|sealed|enum|abstract|open|annotation|inline|value|fun|const|suspend|operator|infix|tailrec|external|expect|actual|lateinit)\s+)*(class|interface|object|typealias|fun|val|var)\s+(?:<[^>]*>\s*)?(?:[\w.<>?,\s]*?\.)?(\w+)", re.M)
IMPORT = re.compile(r"^import\s+([\w.]+?)(\.\*)?(?:\s+as\s+\w+)?\s*$", re.M)


def read(p):
    with open(os.path.join(ROOT, p), encoding="utf-8") as f:
        return f.read()


def kt_files(base):
    out = []
    for d, _, fs in os.walk(os.path.join(ROOT, base)):
        out += [os.path.relpath(os.path.join(d, f), ROOT) for f in fs if f.endswith(".kt")]
    return sorted(out)


def body_tokens(t):
    b = re.sub(r"^(import|package).*$", "", t, flags=re.M)
    b = re.sub(r"/\*.*?\*/", "", b, flags=re.S)
    b = re.sub(r"//.*", "", b)
    b = re.sub(r'""".*?"""', lambda m: " ".join(re.findall(r"\$\{?(\w+)", m.group(0))), b, flags=re.S)
    b = re.sub(r'"(?:\\.|[^"\\\n])*"', lambda m: " ".join(re.findall(r"\$\{?(\w+)", m.group(0))), b)
    return b, set(re.findall(r"\b[A-Za-z_]\w*\b", b))


class Src:
    """One Kotlin file: package, declared top-level symbols, what it uses."""
    def __init__(self, path):
        self.path, self.text = path, read(path)
        m = re.search(r"^package\s+([\w.]+)", self.text, re.M)
        self.pkg = m.group(1) if m else ""
        self.imports = [(m.group(1), bool(m.group(2))) for m in IMPORT.finditer(self.text)]
        self.body, self.tokens = body_tokens(self.text)
        self.decls, self.internal = set(), set()
        for m in DECL.finditer(self.text):
            head = m.group(0).lstrip()
            if head.startswith("private "):
                continue
            self.decls.add(m.group(2))
            if re.match(r"(?:@\w+(?:\([^)]*\))?\s+)*internal\s", head):
                self.internal.add(m.group(2))
        self.lines = self.text.count("\n") + 1
        self.has_test = "@Test" in self.text


class Model:
    def __init__(self):
        self.main = {}   # path -> Src, both modules
        for p in kt_files(APP_MAIN) + kt_files(CORE_MAIN):
            self.main[p] = Src(p)
        self.tests = {p: Src(p) for p in kt_files(APP_TEST) + kt_files(CORE_TEST) + kt_files(CORE_KIT)}
        self.sym = collections.defaultdict(set)  # (package, name) -> {path}
        for p, s in self.main.items():
            for d in s.decls:
                self.sym[(s.pkg, d)].add(p)
        self.tsym = collections.defaultdict(set)
        for p, s in self.tests.items():
            for d in s.decls:
                self.tsym[(s.pkg, d)].add(p)
        self.by_pkg = collections.defaultdict(list)  # package -> names, for wildcard / same-package look-ups
        self._deps, self._tdeps = {}, {}

    def rel(self, p):  # path relative to the package root
        for b in (APP_MAIN, CORE_MAIN, APP_TEST, CORE_TEST, CORE_KIT):
            if p.startswith(b + "/"):
                return p[len(b) + 1:]
        raise ValueError(p)

    def deps(self, p):
        """(files it needs, unresolved com.onpath.app imports) – main files only."""
        if p in self._deps:
            return self._deps[p]
        s, need, unres = self.main[p], set(), []
        for name, star in s.imports:
            if not name.startswith("com.onpath.app."):
                continue
            if star:
                for (pk, sy), fs in self.sym.items():
                    if pk == name and sy in s.tokens:
                        need |= fs
                continue
            pk, sy = name.rsplit(".", 1)
            fs = self.sym.get((pk, sy))
            if not fs:  # a member import: pkg.Obj.member
                pk2, sy2 = pk.rsplit(".", 1)
                fs = self.sym.get((pk2, sy2))
            if fs:
                need |= fs
            elif name not in ("com.onpath.app.R", "com.onpath.app.BuildConfig"):
                unres.append(name)
        for (pk, sy), fs in self.sym.items():
            if pk == s.pkg and sy in s.tokens:
                need |= fs
        for fq in set(re.findall(r"com\.onpath\.app\.[\w.]+", s.body)):
            parts = fq.split(".")
            for i in range(3, len(parts)):
                fs = self.sym.get((".".join(parts[:i]), parts[i]))
                if fs:
                    need |= fs
                    break
        need.discard(p)
        self._deps[p] = (need, unres)
        return self._deps[p]

    def own_block(self, p):
        """Why the file itself cannot be in :core (None = it can)."""
        s = self.main[p]
        r = self.rel(p)
        for name, star in s.imports:
            if ANDROID.match(name):
                return f"imports {name}"
            if name.startswith("com.onpath.app."):
                continue
            if name.startswith("btools.") and r not in ALLOW_BTOOLS:
                return f"uses {name} (BRouter; not in the allow list)"
            if not name.startswith(OK_IMPORT):
                return f"imports {name} (no such library in :core)"
        for m in re.finditer(r"\b(android|androidx)\.[a-z]+\.\w+", s.body):
            return f"uses {m.group(0)}"
        if re.search(r"\bBuildConfig\b", s.body):
            return "uses BuildConfig"
        if re.search(r"(?<![\w.])R\.(string|drawable|color|raw|xml|mipmap|id|layout|plurals)\b", s.body):
            return "uses R.*"
        if re.search(r"\bbtools\.", s.body) and r not in ALLOW_BTOOLS:
            return "uses btools.* (BRouter; not in the allow list)"
        return None


def uses_symbol(src, pkg, name):
    """Does src mention pkg.name (same package, or imported, or the package imported with *)?"""
    if name not in src.tokens:
        return False
    return src.pkg == pkg or any(n == f"{pkg}.{name}" or (star and n == pkg) for n, star in src.imports)


def in_core(p):
    return p.startswith(CORE_MAIN + "/")


def closure(model, candidates):
    """candidates: app paths. Returns (set, {blocked path: reason})."""
    ok, why = set(), {}
    for p in candidates:
        r = model.own_block(p)
        if r:
            why[p] = r
        else:
            ok.add(p)
    changed = True
    while changed:
        changed = False
        for p in sorted(ok):
            need, unres = model.deps(p)
            bad = [n for n in sorted(need) if not in_core(n) and n not in ok]
            leak = [(d, q) for q in model.main if q not in ok and not in_core(q) and q != p
                    for d in model.main[p].internal if uses_symbol(model.main[q], model.main[p].pkg, d)]
            if leak and not bad and not unres:
                ok.discard(p)
                why[p] = f"internal {leak[0][0]} is used by {os.path.basename(leak[0][1])} (stays in :app)"
                changed = True
                continue
            if bad or unres:
                ok.discard(p)
                why[p] = f"needs {os.path.basename(bad[0])}" + (f" (+{len(bad) - 1})" if len(bad) > 1 else "") if bad else f"unresolved import {unres[0]}"
                changed = True
    return ok, why


def root_cause(p, why, seen=()):
    r = why.get(p, "")
    m = re.match(r"needs (\S+)", r)
    if not m or p in seen:
        return r
    for q in why:
        if os.path.basename(q) == m.group(1):
            inner = root_cause(q, why, seen + (p,))
            return f"{r} -> {inner}" if inner else r
    return r + " (not a candidate)"


def candidates(model, only, files):
    allapp = [p for p in model.main if p.startswith(APP_MAIN + "/")]
    if files:
        out = [f"{APP_MAIN}/{f}" for f in files]
        missing = [f for f in out if f not in model.main]
        if missing:
            sys.exit(f"core_move: not in :app: {missing}")
        return out
    pk = lambda p: model.rel(p).split("/")[0]
    hub = {f"{APP_MAIN}/{h}" for h in HUBS}
    out = [p for p in allapp if (pk(p) in CAND or p in hub) and "/" in model.rel(p)]
    if only:
        out = [p for p in out if pk(p) in only]
    return out


REASONS = {}


def plan_tests(model, moved):
    """Which tests move. moved = set of main paths that go to :core. Returns (tests: path->'core'|'kit', stays: set of app test paths)."""
    app_tests = [p for p in model.tests if p.startswith(APP_TEST + "/")]
    mainref = {}
    for p in app_tests:
        s = model.tests[p]
        refs = set()
        imps = {n.rsplit(".", 1)[1]: n.rsplit(".", 1)[0] for n, star in s.imports if not star}
        wild = {n for n, star in s.imports if star}
        for (pk, sy), fs in model.sym.items():
            if sy in s.tokens and (pk == s.pkg or pk in wild or imps.get(sy) == pk):
                refs |= fs
        for fq in set(re.findall(r"com\.onpath\.app\.[\w.]+", s.body)):
            parts = fq.split(".")
            for i in range(3, len(parts)):
                fs = model.sym.get((".".join(parts[:i]), parts[i]))
                if fs:
                    refs |= fs
                    break
        mainref[p] = refs
    tref = {}
    for p in app_tests:
        s = model.tests[p]
        refs = set()
        imps = {n.rsplit(".", 1)[1]: n.rsplit(".", 1)[0] for n, star in s.imports if not star}
        wild = {n for n, star in s.imports if star}
        for (pk, sy), fs in model.tsym.items():
            if sy in s.tokens and (pk == s.pkg or pk in wild or imps.get(sy) == pk):
                refs |= fs
        refs.discard(p)
        tref[p] = refs
    core_like = lambda q: not q.startswith(APP_TEST + "/")  # already in core (test or testkit)
    cand = set()
    for p in app_tests:
        s = model.tests[p]
        a = [n for n, _ in s.imports if n.startswith(TEST_ANDROID)] or [m.group(0) for m in re.finditer(r"\b(android|androidx)\.[a-z]+\.\w+", s.body)]
        if a:
            REASONS[p] = f"android: {a[0]}"
            continue
        refs = mainref[p]
        bad = sorted(r for r in refs if not (in_core(r) or r in moved))
        if bad:
            REASONS[p] = f"uses {os.path.basename(bad[0])}" + (f" (+{len(bad) - 1})" if len(bad) > 1 else "")
            continue
        cand.add(p)
    changed = True
    while changed:
        changed = False
        for p in list(cand):
            badt = [q for q in tref[p] if not (core_like(q) or q in cand)]
            if badt:
                REASONS[p] = f"needs test helper {os.path.basename(badt[0])}"
                cand.discard(p)
                changed = True
    movers = {p for p in cand if mainref[p] & moved}  # tests that use a moved class
    helpers = {p for p in cand if not model.tests[p].has_test and p not in movers}
    go = {}
    need = set()
    work = list(movers)
    while work:
        p = work.pop()
        for q in tref[p]:
            if q in cand and q not in need and q not in movers:
                need.add(q)
                work.append(q)
    for p in movers | need:
        go[p] = "core"
    # a helper that a test staying in :app also uses must be visible to both
    stayers = set(app_tests) - set(go)
    for p in list(go):
        if p not in movers and any(p in tref[q] for q in stayers):
            go[p] = "kit"
    # a mover that a stayer needs (not a helper) cannot move
    for p in list(go):
        if p in movers and any(p in tref[q] for q in stayers):
            go[p] = "kit"
    return go, set(app_tests) - set(go)


def test_dest(model, p, kind):
    return f"{CORE_KIT if kind == 'kit' else CORE_TEST}/{model.rel(p)}"


def main_dest(model, p):
    return f"{CORE_MAIN}/{model.rel(p)}"


def resource_dirs(model, moved_tests):
    """First-level folders of app/src/test/resources that a moved test reads."""
    base = os.path.join(ROOT, APP_RES)
    out = []
    if os.path.isdir(base):
        for d in sorted(os.listdir(base)):
            pat = re.compile(r'["/]' + re.escape(d) + r'(/|")')
            if any(pat.search(model.tests[p].text) for p in moved_tests):
                out.append(d)
    return out


def rewrite_test(text, loc, moved_rel, res_moved):
    """Path literals in a test living in module `loc` ('app' | 'core')."""
    def src_path(m):
        pre, rel = m.group(1), m.group(2)
        to_core = rel in moved_rel
        dest = "core" if to_core else "app"
        sub = "kotlin" if to_core else "java"
        if pre == "app/":
            return f"{dest}/src/main/{sub}/com/onpath/app/{rel}"
        if loc == dest:
            return f"src/main/{sub}/com/onpath/app/{rel}"
        return f"../{dest}/src/main/{sub}/com/onpath/app/{rel}"
    t = re.sub(r"(?<![\w.-])((?:app/)?)src/main/java/com/onpath/app/([\w/]+\.kt)", src_path, text)
    # File("src/main/java/com/onpath/app/$f") readers → sourceFile("$f")
    t2 = re.sub(r'(?:java\.io\.)?File\(\s*"src/main/java/com/onpath/app/(\$\w+|\$\{[^}"]*\})"\s*\)', lambda m: "sourceFile(%s)" % (m.group(1)[1:] if re.fullmatch(r"\$\w+", m.group(1)) else '"%s"' % m.group(1)), t)
    if t2 != t:
        t = t2
        if "import com.onpath.app.testkit.sourceFile" not in t and "package com.onpath.app.testkit" not in t:
            t = re.sub(r"^(import )", r"import com.onpath.app.testkit.sourceFile\n\1", t, count=1, flags=re.M) \
                if re.search(r"^import ", t, re.M) else re.sub(r"^(package .*\n)", r"\1\nimport com.onpath.app.testkit.sourceFile\n", t, count=1, flags=re.M)
    for d in res_moved:
        t = re.sub(r'(?<![\w/.-])app/src/test/resources/' + re.escape(d) + r"\b", f"core/src/test/resources/{d}", t)
        if loc == "app":
            t = re.sub(r'(?<![\w/.-])src/test/resources/' + re.escape(d) + r"\b", f"../core/src/test/resources/{d}", t)
    if loc == "core":  # what stays in :app (assets, resources, manifest) is one folder over
        t = re.sub(r'(?<![\w/.-])src/main/(assets|res|AndroidManifest)', r"../app/src/main/\1", t)
        t = re.sub(r'File\("(libs|web-shared-files\.txt|lint-baseline\.xml)', r'File("../app/\1', t)
    return t


def git(*a, check=True):
    r = subprocess.run(["git", *a], cwd=ROOT, capture_output=True, text=True)
    if check and r.returncode:
        sys.exit(f"git {' '.join(a)}: {r.stderr.strip()}")
    return r.stdout


def mv(src, dst, dry):
    print(f"  git mv {src} -> {dst}")
    if dry:
        return
    os.makedirs(os.path.join(ROOT, os.path.dirname(dst)), exist_ok=True)
    git("mv", src, dst)


def lint_drop(moved_rel, dry):
    p = os.path.join(ROOT, "app/lint-baseline.xml")
    if not os.path.isfile(p):
        return 0
    t = open(p, encoding="utf-8").read()
    n = 0
    def keep(m):
        nonlocal n
        loc = re.search(r'<location\s+file="src/main/java/com/onpath/app/([\w/]+\.kt)"', m.group(0))
        if loc and loc.group(1) in moved_rel:
            n += 1
            return ""
        return m.group(0)
    t2 = re.sub(r"[ \t]*<issue\b.*?</issue>\n?", keep, t, flags=re.S)
    if n and not dry:
        open(p, "w", encoding="utf-8").write(t2)
    return n


def parity_rewrite(moved_main, moved_tests, dry):
    p = os.path.join(ROOT, "web/parity.json")
    t = open(p, encoding="utf-8").read()
    n = 0
    for rel in moved_main:
        a, b = f'"{APP_MAIN}/{rel}"', f'"{CORE_MAIN}/{rel}"'
        n += t.count(a)
        t = t.replace(a, b)
    for rel in moved_tests:
        a, b = f'"{APP_TEST}/{rel}"', f'"{CORE_TEST}/{rel}"'
        n += t.count(a)
        t = t.replace(a, b)
    if n and not dry:
        open(p, "w", encoding="utf-8").write(t)
    return n


def build(args):
    model = Model()
    only = set(args.only.split(",")) if args.only else None
    files = args.files.split(",") if args.files else None
    cand = candidates(model, only, files)
    ok, why = closure(model, cand)
    return model, cand, ok, why


args_why_tests = False


def report(model, cand, ok, why, tests, stays, show_tests=True):
    per = collections.defaultdict(lambda: [0, 0, 0, 0])
    for p in cand:
        k = model.rel(p).split("/")[0]
        per[k][0] += 1
        per[k][1] += model.main[p].lines
        if p in ok:
            per[k][2] += 1
            per[k][3] += model.main[p].lines
    print("== package: candidates / lines / movable / lines")
    for k in sorted(per):
        print(f"  {k:12} {per[k][0]:4} {per[k][1]:6} {per[k][2]:4} {per[k][3]:6}")
    print(f"  total        {sum(v[0] for v in per.values()):4} {sum(v[1] for v in per.values()):6} {len(ok):4} {sum(v[3] for v in per.values()):6}")
    print(f"== MOVABLE ({len(ok)})")
    for p in sorted(ok):
        print("  " + model.rel(p))
    print(f"== BLOCKED candidates ({len(why)})")
    for p in sorted(why):
        print(f"  {model.rel(p)}: {root_cause(p, why)}")
    if show_tests:
        kits = [p for p, k in tests.items() if k == "kit"]
        cores = [p for p, k in tests.items() if k == "core"]
        print(f"== TESTS that move to core/src/test ({len(cores)}), to core/src/testkit ({len(kits)}); {len(stays)} stay")
        for p in sorted(cores):
            print("  " + model.rel(p))
        for p in sorted(kits):
            print("  [testkit] " + model.rel(p))
        if args_why_tests:
            print("== tests that use a moved class but stay in :app")
            for p in sorted(stays):
                if p in REASONS and model.tests[p].tokens & {d for q in ok for d in model.main[q].decls}:
                    print(f"  {model.rel(p)}: {REASONS[p]}")
        print("== RESOURCE folders:", resource_dirs(model, tests) or "none")


def cmd_plan(args):
    model, cand, ok, why = build(args)
    tests, stays = ({}, set()) if args.no_tests else plan_tests(model, ok)
    global args_why_tests
    args_why_tests = args.why_tests
    report(model, cand, ok, why, tests, stays, not args.no_tests)


def cmd_move(args):
    model, cand, ok, why = build(args)
    if not ok:
        sys.exit("core_move: nothing movable")
    tests, stays = ({}, set()) if args.no_tests else plan_tests(model, ok)
    dry = args.dry_run
    moved_rel = {model.rel(p) for p in ok}
    moved_test_rel = {model.rel(p) for p in tests}
    res = resource_dirs(model, tests)
    print(f"-- moving {len(ok)} main files")
    for p in sorted(ok):
        mv(p, main_dest(model, p), dry)
    print(f"-- moving {len(tests)} test files")
    for p in sorted(tests):
        mv(p, test_dest(model, p, tests[p]), dry)
    print(f"-- moving resource folders {res}")
    for d in res:
        mv(f"{APP_RES}/{d}", f"{CORE_RES}/{d}", dry)
    # path literals in every test of both modules (+ moved ones read from their new place)
    print("-- rewriting path literals in tests")
    changed = 0
    for old, s in model.tests.items():
        if old in tests:
            new, loc = test_dest(model, old, tests[old]), "core"
        else:
            new, loc = old, ("core" if not old.startswith(APP_TEST + "/") else "app")
        text = rewrite_test(s.text, loc, moved_rel, res)
        if text != s.text:
            changed += 1
            print(f"  {new}")
            if not dry:
                with open(os.path.join(ROOT, new), "w", encoding="utf-8") as f:
                    f.write(text)
    print(f"-- {changed} test file(s) rewritten")
    n = lint_drop(moved_rel, dry)
    print(f"-- app/lint-baseline.xml: {n} issue(s) of moved files dropped")
    n = parity_rewrite(moved_rel, moved_test_rel, dry)
    print(f"-- web/parity.json: {n} key(s) renamed")
    # leftovers that need a human
    print("-- check by hand (the tool cannot rewrite these):")
    pk_moved = {r.split("/")[0] for r in moved_rel}
    left = 0
    for d, _, fs in os.walk(os.path.join(ROOT, "app/src/test")):
        for f in fs:
            if not f.endswith(".kt"):
                continue
            path = os.path.join(d, f)
            for i, line in enumerate(open(path, encoding="utf-8"), 1):
                # a literal that is not a path to one .kt file (a folder, a root, a concatenation): the file may have moved
                for m in re.finditer(r'src/main/java(?:/com/onpath/app(?:/[\w/$]*)?)?(?!/?[\w/$]*\.kt)', line):
                    rest = m.group(0)[len("src/main/java/com/onpath/app/"):].split("/")[0]
                    if m.group(0) == "src/main/java" or m.group(0).endswith("/app") or rest in pk_moved or not rest:
                        left += 1
                        print(f"  {os.path.relpath(path, ROOT)}:{i}: {line.strip()[:110]}")
                        break
    for old, kind in sorted(tests.items()):
        new = test_dest(model, old, kind)
        for i, line in enumerate(open(os.path.join(ROOT, new), encoding="utf-8") if not dry else [], 1):
            for m in re.finditer(r'File\("([^"$]*)"', line):
                lit = m.group(1)
                if lit and not lit.startswith(("../", "src/main/kotlin", "src/test/resources", "/", "build/", "http")):
                    left += 1
                    print(f"  {new}:{i}: File(\"{lit}\") – relative to the module folder: is it still right in :core?")
    if not left:
        print("  (none)")
    print("""-- next: verify (the compiler is the truth; a file :core refuses goes back with `git mv core/... app/...`)
     tools/dev/gradle_slot.sh -- ./gradlew :core:compileKotlin :core:compileTestKotlin :app:compileDebugUnitTestKotlin
     tools/ci/gate.sh --full
     tools/dev/gradle_slot.sh -- ./gradlew :app:assembleDebug :app:compileDebugAndroidTestKotlin -Ponpath.e2e=true
     node web/parity/check.mjs && tools/dev/gradle_slot.sh -- ./gradlew -p web/engine test""")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("cmd", choices=["plan", "move"])
    ap.add_argument("--only", help="comma separated packages")
    ap.add_argument("--files", help="comma separated files relative to the package root, e.g. suggest/X.kt,sync/Y.kt")
    ap.add_argument("--no-tests", action="store_true")
    ap.add_argument("--why-tests", action="store_true", help="plan: list the tests that use a moved class but stay, with the reason")
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()
    {"plan": cmd_plan, "move": cmd_move}[a.cmd](a)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Risk-based unit tests: for a release (owner: the full suite takes too long for every release) and for the local
agent gate (tools/ci/gate.sh, owner 2026-10-31 "D": shorter builds on donald).

Release (default mode) – prints the Gradle arguments for `testDebugUnitTest`:
- always the core-feature packages (robust first – never lose a track): recording, navigation, data, gpx, backup, plansync, sync;
- plus every app package whose main or test code changed since the last release tag;
- ui/ or the texts (strings/) changed → the ui tests (with the screenshot tests);
- build files, BRouter jars / profiles, or nothing to compare with → "" = the full suite.
The full suite still runs after every push to main (unit.yml), beside the release.

Gate (--gate) – prints shell assignments for gate.sh (same selection, plus):
- nothing Gradle reads changed (docs, web, most of tools/) → no Gradle run at all (unit.yml skips these pushes too);
- only the version line of app/build.gradle.kts (gate.sh passes it as "app/build.gradle.kts#version") → the update
  tests, not everything; with only release notes / docs beside it → release=1 (gate.sh may skip on a green CI);
- the source-scanning rule tests (*RuleTest: texts, font sizes, shared core …) always – they read other packages;
- the screenshot check (verifyPaparazziDebug) only when a selected package has screenshot tests (ui/, search/), or
  the snapshots / texts / resources changed, or everything runs.

Three modules (docs/design/umbau-phase-2.md): a package may live in :app (app/src/{main,test}/java/…),
in :core and in :state (core|state/src/{main,test}/kotlin/…, plain JVM). A changed file of any selects that package's
tests in ALL modules where the package has tests (a --tests filter that matches nothing fails Gradle's task, so a module
only gets the patterns of packages it has tests for). Release mode prints the :app filters only –
:app:testDebugUnitTest runs the whole :core:test and :state:test first (app/build.gradle.kts); the gate names them with
their own filters (core_patterns, state_patterns).

Usage: risk_tests.py [--gate] <changed file> …  (one path per argument, relative to the repo root)
"""
import os, re, shlex, sys

CORE = ["recording", "navigation", "data", "gpx", "backup", "plansync", "sync"]
FULL_TRIGGERS = (
    "app/build.gradle.kts", "build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradle/",
    "app/libs/", "app/src/main/assets/brouter/", "strings/build.gradle.kts", "app/src/test/resources/",
    "core/build.gradle.kts", "core/src/test/resources/", "core/src/testkit/",  # testkit: helpers all modules compile
    "state/build.gradle.kts",
)
PKG = re.compile(r"^(?:app/src/(?:main|test)/java|(?:core|state)/src/(?:main|test)/kotlin)/com/onpath/app/([a-z0-9_]+)/")
ROOT_PKG = ("app/src/main/java/com/onpath/app/", "app/src/test/java/com/onpath/app/",
            "core/src/main/kotlin/com/onpath/app/", "core/src/test/kotlin/com/onpath/app/",
            "state/src/main/kotlin/com/onpath/app/", "state/src/test/kotlin/com/onpath/app/")
VERSION_ONLY = "app/build.gradle.kts#version"  # gate.sh: the diff of app/build.gradle.kts touches only appVersion
# Files outside app/ and strings/ that unit tests or the build read (gate: these need a Gradle run)
GRADLE_INPUTS = ("app/", "core/", "state/", "strings/", "gradle/", "build.gradle.kts", "settings.gradle.kts", "gradle.properties",
                 "gradlew", "keystore/", "tools/sync/", "tools/routing/wayclass/",
                 # the planner's stand-ins / excludes decide the set SharedFilesRuleTest locks
                 "web/engine/core-excludes.txt", "web/engine/src/main/kotlin/com/onpath/app/")
TOOL_TESTS = {"tools/sync/": "plansync", "tools/routing/wayclass/": "routing"}  # SyncMailboxE2ETest, RouterClassHashTest
RELEASE_FILES = ("docs/",)  # beside the version line, a release changes only notes / docs
REPO = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
TEST_ROOT = os.path.join(REPO, "app", "src", "test", "java", "com", "onpath", "app")
CORE_TEST_ROOT = os.path.join(REPO, "core", "src", "test", "kotlin", "com", "onpath", "app")
STATE_TEST_ROOT = os.path.join(REPO, "state", "src", "test", "kotlin", "com", "onpath", "app")


def select(changed):
    if not changed:
        return None  # nothing to compare with: everything
    pkgs = set(CORE)
    for f in changed:
        if f == VERSION_ONLY:
            pkgs.add("update")  # BuildConfig.VERSION_NAME: the updater compares versions
            continue
        if f.startswith(FULL_TRIGGERS):
            return None
        m = PKG.match(f)
        if m:
            pkgs.add(m.group(1))
            if m.group(1) == "domain":
                # the plan / route rules moved out of ui/ (2026-11-18) still drive the screens: their ui tests and
                # screenshots stay in ui/ (ScreenshotTest, PlanStatusTest …) – a domain change runs those too
                pkgs.add("ui")
        elif f.startswith(ROOT_PKG):
            return None  # a file in the root package (MainActivity, OnPathApp …): everything
        elif f.startswith("strings/") or f.startswith("app/src/test/snapshots/") or f.startswith("app/src/main/res/"):
            pkgs.add("ui")
        elif f.startswith("app/src/main/java/btools/") or f.startswith("app/src/test/java/btools/"):
            pkgs.update(["routing", "btools"])  # BRouter patches: our routing tests + the btools.router tests
        elif f.startswith("app/src/main/assets/"):
            pkgs.update(["map", "ui"])
        else:
            for prefix, pkg in TOOL_TESTS.items():
                if f.startswith(prefix):
                    pkgs.add(pkg)
    return sorted(pkgs)


def test_pattern(pkg):
    return "btools.*" if pkg == "btools" else f"com.onpath.app.{pkg}.*"


def test_packages(root):
    """Packages (folders) that hold at least one .kt / .java test under root."""
    out = set()
    if os.path.isdir(root):
        for pkg in os.listdir(root):
            for _, _, files in os.walk(os.path.join(root, pkg)):
                if any(n.endswith((".kt", ".java")) for n in files):
                    out.add(pkg)
                    break
    return out


def app_test_packages():
    pkgs = test_packages(TEST_ROOT)
    if os.path.isdir(os.path.join(TEST_ROOT, "..", "..", "..", "btools")):
        pkgs.add("btools")
    return pkgs


def root_rule_tests(root):
    """Does this test root hold *RuleTest classes (the source-scanning rule tests, always run by the gate)?"""
    for dirpath, _, files in os.walk(root):
        if any(n.endswith("RuleTest.kt") for n in files):
            return True
    return False


def gradle_args(changed, app_pkgs=None):
    """Release: the :app filters. Only packages with app tests (a filter without a test fails the task); none left → ""."""
    pkgs = select(changed)
    if pkgs is None:
        return ""
    app_pkgs = app_test_packages() if app_pkgs is None else set(app_pkgs)
    pkgs = [p for p in pkgs if p in app_pkgs]
    return " ".join(f"--tests '{test_pattern(p)}'" for p in pkgs)


def screenshot_packages(root=TEST_ROOT):
    """App test packages holding Paparazzi tests (today ui/ and search/)."""
    out = set()
    if os.path.isdir(root):
        for pkg in os.listdir(root):
            for dirpath, _, files in os.walk(os.path.join(root, pkg)):
                for name in files:
                    if name.endswith(".kt"):
                        with open(os.path.join(dirpath, name), encoding="utf-8", errors="replace") as fh:
                            if "app.cash.paparazzi" in fh.read():
                                out.add(pkg)
                                break
                if pkg in out:
                    break
    return out


def gate(changed, shot_pkgs=None, app_pkgs=None, core_pkgs=None, rule_tests=None, state_pkgs=None):
    """The plan for gate.sh: dict with mode (skip | select | full), patterns (--tests filters, space-separated),
    core_patterns / state_patterns (the same for :core:test / :state:test; empty = no run of that module), paparazzi,
    python (tools/ci changed: its own tests), release, why. rule_tests = (app has *RuleTest, core has, state has)."""
    shot_pkgs = screenshot_packages() if shot_pkgs is None else set(shot_pkgs)
    app_pkgs = app_test_packages() if app_pkgs is None else set(app_pkgs)
    core_pkgs = test_packages(CORE_TEST_ROOT) if core_pkgs is None else set(core_pkgs)
    state_pkgs = test_packages(STATE_TEST_ROOT) if state_pkgs is None else set(state_pkgs)
    app_rules, core_rules, state_rules = ((True, root_rule_tests(CORE_TEST_ROOT), root_rule_tests(STATE_TEST_ROOT))
                                          if rule_tests is None else rule_tests)
    python = any(f.startswith("tools/ci/") for f in changed)
    relevant = [f for f in changed if f == VERSION_ONLY or f.startswith(GRADLE_INPUTS)]
    release = VERSION_ONLY in changed and all(f == VERSION_ONLY or f.startswith(RELEASE_FILES) or
                                              ("/" not in f and f.endswith(".md")) for f in changed)
    plan = {"mode": "skip", "patterns": "", "core_patterns": "", "state_patterns": "", "paparazzi": 0, "python": int(python), "release": int(release), "why": ""}
    if not changed:
        plan["why"] = "no changes against the base"
        return plan
    if not relevant:
        plan["why"] = "nothing Gradle reads changed (" + ", ".join(sorted({f.split("/")[0] for f in changed})) + ")"
        return plan
    pkgs = select(relevant)
    if pkgs is None:
        plan.update(mode="full", paparazzi=1, why="build files / root package / BRouter / test resources changed")
        return plan
    shots = any(p in shot_pkgs for p in pkgs) or any(
        f.startswith(("app/src/test/snapshots/", "strings/", "app/src/main/res/")) for f in relevant)
    patterns = [test_pattern(p) for p in pkgs if p in app_pkgs] + (["*RuleTest"] if app_rules else [])
    core_patterns = [test_pattern(p) for p in pkgs if p in core_pkgs] + (["*RuleTest"] if core_rules else [])
    state_patterns = [test_pattern(p) for p in pkgs if p in state_pkgs] + (["*RuleTest"] if state_rules else [])
    plan.update(mode="select", patterns=" ".join(patterns), core_patterns=" ".join(core_patterns),
                state_patterns=" ".join(state_patterns), paparazzi=int(shots),
                why="packages: " + " ".join(pkgs))
    return plan


if __name__ == "__main__":
    argv = [a for a in sys.argv[1:] if a.strip()]
    if argv[:1] == ["--gate"]:
        for k, v in gate(argv[1:]).items():
            print(f"{k}={shlex.quote(str(v))}")
    else:
        print(gradle_args(argv))

import unittest, risk_tests as r

class RiskTests(unittest.TestCase):
    def test_core_always(self):
        self.assertEqual(r.select(["docs/backlog.md"]), sorted(r.CORE))
    def test_changed_package_added(self):
        self.assertIn("search", r.select(["app/src/main/java/com/onpath/app/search/OnlineSearch.kt"]))
    def test_texts_run_the_ui_tests(self):
        self.assertIn("ui", r.select(["strings/src/commonMain/composeResources/values/strings_plan.xml"]))
    def test_build_files_run_everything(self):
        self.assertIsNone(r.select(["app/build.gradle.kts"]))
        self.assertEqual(r.gradle_args(["app/libs/x.jar"]), "")
    def test_root_package_runs_everything(self):
        self.assertIsNone(r.select(["app/src/main/java/com/onpath/app/MainActivity.kt"]))
    def test_nothing_to_compare_runs_everything(self):
        self.assertIsNone(r.select([]))
    def test_args(self):
        self.assertEqual(r.gradle_args(["app/src/main/java/com/onpath/app/map/X.kt"], r.CORE + ["map"]).count("--tests"), len(r.CORE) + 1)

    def test_btools_tests_are_selected(self):
        self.assertIn("btools", r.select(["app/src/test/java/btools/router/OnPathHintsTest.java"]))
        self.assertIn("--tests 'btools.*'", r.gradle_args(["app/src/main/java/btools/router/X.java"], r.CORE + ["btools", "routing"]))

    # gate.sh (local agent gate)
    SHOTS = {"ui", "search"}
    def test_gate_docs_only_skips_gradle(self):
        g = r.gate(["docs/backlog.md", "CLAUDE.md", "web/x.ts"], self.SHOTS)
        self.assertEqual((g["mode"], g["paparazzi"], g["release"]), ("skip", 0, 0))
    def test_gate_no_changes_skips(self):
        self.assertEqual(r.gate([], self.SHOTS)["mode"], "skip")
    def test_gate_app_change_outside_ui_no_screenshots(self):
        g = r.gate(["app/src/main/java/com/onpath/app/routing/Planner.kt"], self.SHOTS)
        self.assertEqual((g["mode"], g["paparazzi"]), ("select", 0))
        self.assertIn("com.onpath.app.routing.*", g["patterns"].split())
        self.assertIn("*RuleTest", g["patterns"].split())
        self.assertNotIn("com.onpath.app.ui.*", g["patterns"].split())
    def test_gate_ui_texts_snapshots_run_screenshots(self):
        for f in ["app/src/main/java/com/onpath/app/ui/Theme.kt", "strings/src/commonMain/composeResources/values/strings_plan.xml",
                  "app/src/test/snapshots/images/x.png", "app/src/main/java/com/onpath/app/search/SearchSheet.kt"]:
            self.assertEqual(r.gate([f], self.SHOTS)["paparazzi"], 1, f)
    def test_gate_build_files_run_everything(self):
        g = r.gate(["gradle.properties"], self.SHOTS)
        self.assertEqual((g["mode"], g["paparazzi"]), ("full", 1))
    def test_gate_version_line_only_is_a_release(self):
        g = r.gate([r.VERSION_ONLY, "docs/release-notes/v1.2.3.md", "docs/project-status.md"], self.SHOTS)
        self.assertEqual((g["mode"], g["release"], g["paparazzi"]), ("select", 1, 0))
        self.assertIn("com.onpath.app.update.*", g["patterns"].split())
    def test_gate_release_with_code_is_not_release_only(self):
        self.assertEqual(r.gate([r.VERSION_ONLY, "app/src/main/java/com/onpath/app/map/X.kt"], self.SHOTS)["release"], 0)
    def test_gate_tools_read_by_tests(self):
        self.assertIn("plansync", r.gate(["tools/sync/mailbox-server.mjs"], self.SHOTS)["why"])
        self.assertEqual(r.gate(["tools/ci/gate.sh"], self.SHOTS)["python"], 1)
    def test_screenshot_packages_found_in_repo(self):
        self.assertIn("ui", r.screenshot_packages())

    # :core (core/src/{main,test}/kotlin): a package may live in both modules; a filter only goes to a module with tests
    APP = set(r.CORE) | {"ui", "search", "routing", "map", "suggest"}
    CORE = {"suggest", "sync", "domain"}
    STATE = {"ui"}
    def gate_core(self, files, core=None, rules=(True, True), state=None, state_rules=True):
        return r.gate(files, self.SHOTS, self.APP, self.CORE if core is None else core, tuple(rules)[:2] + (state_rules,),
                      self.STATE if state is None else state)
    def test_core_file_selects_its_package(self):
        self.assertIn("suggest", r.select(["core/src/main/kotlin/com/onpath/app/suggest/X.kt"]))
        self.assertIn("suggest", r.select(["core/src/test/kotlin/com/onpath/app/suggest/XTest.kt"]))
    def test_core_root_package_and_build_run_everything(self):
        self.assertIsNone(r.select(["core/src/main/kotlin/com/onpath/app/Y.kt"]))
        self.assertIsNone(r.select(["core/build.gradle.kts"]))
        self.assertIsNone(r.select(["core/src/test/resources/suggest/a.json"]))
        self.assertIsNone(r.select(["core/src/testkit/kotlin/com/onpath/app/testkit/Sources.kt"]))
        self.assertEqual(self.gate_core(["core/build.gradle.kts"])["mode"], "full")
    def test_gate_core_file_runs_core_and_app_tests_of_that_package(self):
        g = self.gate_core(["core/src/main/kotlin/com/onpath/app/suggest/X.kt"])
        self.assertEqual(g["mode"], "select")
        self.assertIn("com.onpath.app.suggest.*", g["patterns"].split())
        self.assertIn("com.onpath.app.suggest.*", g["core_patterns"].split())
        self.assertIn("*RuleTest", g["core_patterns"].split())
    def test_gate_package_only_in_one_module(self):
        g = self.gate_core(["core/src/main/kotlin/com/onpath/app/suggest/X.kt"])
        self.assertIn("com.onpath.app.sync.*", g["core_patterns"].split())  # a core-feature package living in both
        self.assertNotIn("com.onpath.app.recording.*", g["core_patterns"].split())  # no core test: no filter there
        self.assertIn("com.onpath.app.recording.*", g["patterns"].split())
        g = self.gate_core(["app/src/main/java/com/onpath/app/map/X.kt"], rules=(True, False))
        self.assertNotIn("com.onpath.app.map.*", g["core_patterns"].split())
        self.assertNotIn("*RuleTest", g["core_patterns"].split())
    def test_gate_no_core_tests_no_core_patterns(self):
        g = self.gate_core(["app/src/main/java/com/onpath/app/map/X.kt"], core=set(), rules=(True, False))
        self.assertEqual(g["core_patterns"], "")
    # :state (state/src/{main,test}/kotlin): the third module of the same scheme (docs/design/umbau-phase-2.md)
    def test_state_file_selects_its_package_and_runs_state_and_app_tests(self):
        f = "state/src/main/kotlin/com/onpath/app/ui/routes/RoutesState.kt"
        self.assertIn("ui", r.select([f]))
        self.assertIn("ui", r.select(["state/src/test/kotlin/com/onpath/app/ui/routes/RecentPlansTest.kt"]))
        g = self.gate_core([f])
        self.assertEqual(g["mode"], "select")
        self.assertIn("com.onpath.app.ui.*", g["state_patterns"].split())
        self.assertIn("com.onpath.app.ui.*", g["patterns"].split())
        self.assertIn("*RuleTest", g["state_patterns"].split())
    def test_state_root_package_build_and_web_list(self):
        self.assertIsNone(r.select(["state/src/main/kotlin/com/onpath/app/Z.kt"]))
        self.assertIsNone(r.select(["state/build.gradle.kts"]))
        self.assertEqual(self.gate_core(["state/build.gradle.kts"])["mode"], "full")
        # web-ready.txt is read by WebReadyRuleTest: a Gradle input
        self.assertNotEqual(self.gate_core(["state/web-ready.txt"])["mode"], "skip")
    def test_gate_state_gets_filters_only_for_packages_with_state_tests(self):
        g = self.gate_core(["core/src/main/kotlin/com/onpath/app/suggest/X.kt"])
        self.assertNotIn("com.onpath.app.suggest.*", g["state_patterns"].split())  # no state test there
        g = self.gate_core(["app/src/main/java/com/onpath/app/map/X.kt"], state=set(), state_rules=False)
        self.assertEqual(g["state_patterns"], "")
    def test_release_args_only_for_packages_with_app_tests(self):
        a = r.gradle_args(["core/src/main/kotlin/com/onpath/app/suggest/X.kt"], {"suggest", "data"})
        self.assertIn("com.onpath.app.suggest.*", a)
        self.assertNotIn("recording", a)  # moved out of :app: its filter would match nothing there
    def test_gate_core_dir_is_a_gradle_input(self):
        self.assertNotEqual(self.gate_core(["core/src/test/kotlin/com/onpath/app/suggest/XTest.kt"])["mode"], "skip")
    def test_gate_engine_stand_ins_and_excludes_are_gradle_inputs(self):
        for f in ["web/engine/core-excludes.txt", "web/engine/src/main/kotlin/com/onpath/app/debug/Problems.kt"]:
            self.assertNotEqual(self.gate_core([f])["mode"], "skip", f)
        self.assertEqual(self.gate_core(["web/engine/src/main/kotlin/com/onpath/web/WebApi.kt"])["mode"], "skip")


if __name__ == "__main__":
    unittest.main()


class DomainRunsUiTests(__import__("unittest").TestCase):
    def test_a_domain_change_runs_the_ui_tests_and_screenshots(self):
        import risk_tests
        pkgs = risk_tests.select(["app/src/main/java/com/onpath/app/domain/PlanGaps.kt"])
        self.assertIn("domain", pkgs)
        self.assertIn("ui", pkgs)  # the screens that use the moved rules are tested in ui/

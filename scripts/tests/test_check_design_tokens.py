import importlib.util
import pathlib
import subprocess
import sys
import tempfile
import unittest

SCRIPT = pathlib.Path(__file__).resolve().parents[1] / "check_design_tokens.py"
REPO_ROOT = SCRIPT.parents[1]

_spec = importlib.util.spec_from_file_location("design_token_check", SCRIPT)
design_tokens = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(design_tokens)

SOURCE = "app/src/main/kotlin/com/example"
TOKEN_SOURCE = "app/src/main/kotlin/com/ichichuang/cinema/core/design"
FEATURE_SOURCE = "app/src/main/kotlin/com/ichichuang/cinema/feature"

# Independent copy of the only files allowed in the whitelist. Deliberately not
# derived from the script: the guard has to notice when the script grows an entry
# for a screen that is not pre-R18 old UI. Shrinking the set is fine (R21 empties it).
PRE_R18_SCREENS = frozenset({
    f"{FEATURE_SOURCE}/home/HomeScreen.kt",
    f"{FEATURE_SOURCE}/settings/SettingsScreen.kt",
})


def run_checker(root: pathlib.Path) -> subprocess.CompletedProcess:
    """Run the real script (not a copy of its logic) against a temporary tree."""
    return subprocess.run(
        [sys.executable, str(SCRIPT), "--root", str(root)],
        capture_output=True,
        text=True,
    )


class DesignTokenScriptTests(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self._tmp.name)

    def tearDown(self):
        self._tmp.cleanup()

    def write(self, relative: str, text: str) -> pathlib.Path:
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
        return path

    def test_clean_tree_passes(self):
        self.write(f"{SOURCE}/Clean.kt", "val gap = Spacing.md\n")
        result = run_checker(self.root)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("violations=0", result.stdout)

    def test_color_literal_is_rejected(self):
        self.write(f"{SOURCE}/Bad.kt", "val accent = Color(0xFFD4A056)\n")
        result = run_checker(self.root)
        self.assertEqual(1, result.returncode)
        self.assertIn("Bad.kt:1: color-literal", result.stderr)

    def test_animation_specs_are_rejected(self):
        for spec in ("tween(300)", "spring(stiffness = 400f)", "keyframes { 0f at 0 }"):
            with self.subTest(spec=spec):
                self.write(f"{SOURCE}/Bad.kt", f"val spec = {spec}\n")
                result = run_checker(self.root)
                self.assertEqual(1, result.returncode, spec)
                self.assertIn(":1:", result.stderr)

    def test_explicit_type_argument_tween_is_rejected(self):
        # `tween<Float>(` is the spelling Compose actually infers; a rule that only
        # matches `tween(` misses the majority of call sites.
        self.write(f"{SOURCE}/Bad.kt", "val fade = tween<Float>(durationMillis = 200)\n")
        result = run_checker(self.root)
        self.assertEqual(1, result.returncode)
        self.assertIn("Bad.kt:1: animation-spec", result.stderr)

    def test_explicit_type_argument_spring_is_rejected(self):
        self.write(f"{SOURCE}/Bad.kt", "val lift = spring<Float>(stiffness = 400f)\n")
        result = run_checker(self.root)
        self.assertEqual(1, result.returncode)
        self.assertIn("Bad.kt:1: animation-spec", result.stderr)

    def test_duration_literal_is_rejected(self):
        self.write(f"{SOURCE}/Bad.kt", "val pause = tween(durationMillis = 300)\n")
        result = run_checker(self.root)
        self.assertEqual(1, result.returncode)
        self.assertIn(":1:", result.stderr)

    def test_duration_literal_is_rejected_without_any_spec_call(self):
        # Isolates the duration rule: this line carries no tween/spring token, so the
        # only rule that can reject it is `duration-literal`.
        self.write(f"{SOURCE}/Bad.kt", "val step = builder(durationMillis = 300)\n")
        result = run_checker(self.root)
        self.assertEqual(1, result.returncode)
        self.assertIn("Bad.kt:1: duration-literal", result.stderr)

    def test_dp_and_sp_literals_are_rejected(self):
        self.write(f"{SOURCE}/Bad.kt", "val gap = 12.dp\nval size = 16.sp\n")
        result = run_checker(self.root)
        self.assertEqual(1, result.returncode)
        self.assertIn("Bad.kt:1: dp-literal", result.stderr)
        self.assertIn("Bad.kt:2: sp-literal", result.stderr)

    def test_fractional_dp_literal_is_rejected(self):
        # `1.5.dp` is still a rhythm decision written at the call site, and it is not
        # a hairline even though it starts with `1`.
        self.write(f"{SOURCE}/Bad.kt", "val gap = 1.5.dp\n")
        result = run_checker(self.root)
        self.assertEqual(1, result.returncode)
        self.assertIn("Bad.kt:1: dp-literal", result.stderr)

    def test_fractional_sp_literal_is_rejected(self):
        self.write(f"{SOURCE}/Bad.kt", "val size = 13.5.sp\n")
        result = run_checker(self.root)
        self.assertEqual(1, result.returncode)
        self.assertIn("Bad.kt:1: sp-literal", result.stderr)

    def test_hairline_dp_values_are_allowed(self):
        self.write(f"{SOURCE}/Fine.kt", "val a = 0.dp\nval b = 1.dp\n")
        result = run_checker(self.root)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_density_scaled_metrics_are_not_dp_literals(self):
        # `m.dp(24f)` goes through the metrics helper: the value is a design decision
        # expressed in reference dp, not a literal unit at the call site.
        self.write(f"{SOURCE}/Fine.kt", "val a = m.dp(24f)\nval b = Spacing.sm\nval c = Radii.md\n")
        result = run_checker(self.root)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_line_numbers_point_at_the_offending_line(self):
        self.write(f"{SOURCE}/Multi.kt", "val a = Spacing.md\nval b = Radii.md\nval c = 8.dp\n")
        result = run_checker(self.root)
        self.assertEqual(1, result.returncode)
        self.assertIn("Multi.kt:3: dp-literal", result.stderr)
        self.assertNotIn("Multi.kt:1:", result.stderr)
        self.assertNotIn("Multi.kt:2:", result.stderr)

    def test_token_ok_exempts_only_its_own_line(self):
        self.write(
            f"{SOURCE}/Mixed.kt",
            "val a = 8.dp // token-ok: measured against the legacy grid\nval b = 8.dp\n",
        )
        result = run_checker(self.root)
        self.assertEqual(1, result.returncode)
        self.assertIn("Mixed.kt:2: dp-literal", result.stderr)
        self.assertNotIn("Mixed.kt:1:", result.stderr)
        self.assertIn("exempted=1", result.stdout)

    def test_token_ok_requires_a_reason(self):
        self.write(f"{SOURCE}/Bad.kt", "val a = 8.dp // token-ok:\n")
        result = run_checker(self.root)
        self.assertEqual(1, result.returncode, result.stdout)
        self.assertIn("Bad.kt:1: dp-literal", result.stderr)

    def test_rule_matches_inside_comments_and_strings(self):
        # Documented scope: the script is a line regex, it does not parse Kotlin. A
        # literal quoted in a comment is still a call site waiting to be copied, so
        # it is reported; a genuine false positive takes an explicit token-ok.
        self.write(f"{SOURCE}/Bad.kt", "// remember to replace 24.dp here\nval s = \"tween(200)\"\n")
        result = run_checker(self.root)
        self.assertEqual(1, result.returncode)
        self.assertIn("Bad.kt:1: dp-literal", result.stderr)
        self.assertIn("Bad.kt:2: animation-spec", result.stderr)

    def test_core_design_is_the_token_authority(self):
        self.write(f"{TOKEN_SOURCE}/Tokens.kt", "val gap = 12.dp\nval ink = Color(0xFF0A0A0C)\n")
        result = run_checker(self.root)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("token-dir=2", result.stdout)

    def test_legacy_whitelist_covers_only_listed_files(self):
        listed = sorted(design_tokens.LEGACY_WHITELIST)
        if listed:
            self.write(listed[0], "val gap = 8.dp\n")
        self.write(f"{SOURCE}/New.kt", "val gap = 8.dp\n")
        result = run_checker(self.root)
        self.assertEqual(1, result.returncode)
        self.assertIn("New.kt:1: dp-literal", result.stderr)
        self.assertIn(f"legacy-whitelist={1 if listed else 0}", result.stdout)

    def test_every_whitelist_entry_exists(self):
        # Iterates whatever the whitelist holds: emptying it (R21) must not fail here.
        for relative in sorted(design_tokens.LEGACY_WHITELIST):
            with self.subTest(relative=relative):
                self.assertTrue((REPO_ROOT / relative).is_file(), f"{relative} does not exist")

    def test_whitelist_holds_only_pre_r18_screens(self):
        # A probe screen of R12-R16 must never be whitelisted: it is live UI, so its
        # literals have to be tokens.
        self.assertLessEqual(set(design_tokens.LEGACY_WHITELIST), set(PRE_R18_SCREENS))

    def test_empty_whitelist_does_not_break_the_scan(self):
        original = design_tokens.LEGACY_WHITELIST
        try:
            design_tokens.LEGACY_WHITELIST = frozenset()
            report = design_tokens.Report()
            path = f"{FEATURE_SOURCE}/home/HomeScreen.kt"
            design_tokens.scan_text(path, "val gap = 8.dp\n", report)
            self.assertEqual(1, report.violations_count)
            self.assertEqual(0, report.whitelisted)
        finally:
            design_tokens.LEGACY_WHITELIST = original

    def test_missing_source_root_is_a_usage_error(self):
        result = run_checker(self.root)
        self.assertEqual(2, result.returncode)
        self.assertIn("could not find", result.stderr)

    def test_repository_itself_is_clean(self):
        result = subprocess.run(
            [sys.executable, str(SCRIPT)],
            capture_output=True,
            text=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("violations=0", result.stdout)


if __name__ == "__main__":
    unittest.main()

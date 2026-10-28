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

    def test_duration_literal_is_rejected(self):
        self.write(f"{SOURCE}/Bad.kt", "val pause = tween(durationMillis = 300)\n")
        result = run_checker(self.root)
        self.assertEqual(1, result.returncode)
        self.assertIn(":1:", result.stderr)

    def test_dp_and_sp_literals_are_rejected(self):
        self.write(f"{SOURCE}/Bad.kt", "val gap = 12.dp\nval size = 16.sp\n")
        result = run_checker(self.root)
        self.assertEqual(1, result.returncode)
        self.assertIn("Bad.kt:1: dp-literal", result.stderr)
        self.assertIn("Bad.kt:2: sp-literal", result.stderr)

    def test_hairline_dp_values_are_allowed(self):
        self.write(f"{SOURCE}/Fine.kt", "val a = 0.dp\nval b = 1.dp\n")
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

    def test_core_design_is_the_token_authority(self):
        self.write(f"{TOKEN_SOURCE}/Tokens.kt", "val gap = 12.dp\nval ink = Color(0xFF0A0A0C)\n")
        result = run_checker(self.root)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("token-dir=2", result.stdout)

    def test_legacy_whitelist_covers_only_listed_files(self):
        listed = sorted(design_tokens.LEGACY_WHITELIST)[0]
        self.write(listed, "val gap = 8.dp\n")
        self.write(f"{SOURCE}/New.kt", "val gap = 8.dp\n")
        result = run_checker(self.root)
        self.assertEqual(1, result.returncode)
        self.assertIn("New.kt:1: dp-literal", result.stderr)
        self.assertNotIn(listed, result.stderr)
        self.assertIn("legacy-whitelist=1", result.stdout)

    def test_every_whitelist_entry_exists_and_is_old_ui(self):
        self.assertTrue(design_tokens.LEGACY_WHITELIST)
        for relative in sorted(design_tokens.LEGACY_WHITELIST):
            with self.subTest(relative=relative):
                self.assertTrue((REPO_ROOT / relative).is_file(), f"{relative} does not exist")
                self.assertIn("/feature/", relative)
                self.assertNotIn("/core/design/", relative)

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

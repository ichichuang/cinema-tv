"""Single-repository metadata endpoints; no release or key material is exercised."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
SETTINGS = ROOT / "app/src/main/kotlin/com/ichichuang/cinema/data/settings/CinemaSettings.kt"


def defaults(name):
    source = SETTINGS.read_text(encoding="utf-8")
    match = re.search(r"\bval\s+" + re.escape(name) + r"\s*=\s*listOf\((.*?)\n\s*\)", source, re.S)
    if match is None:
        raise AssertionError("Missing " + name)
    return re.findall(r'"(https://[^"]+)"', match.group(1))


class SingleRepositoryDistributionTests(unittest.TestCase):
    def test_update_manifest_defaults_use_main_repository(self):
        self.assertEqual(defaults("DEFAULT_UPDATE_URLS"), [
            "https://gh-proxy.com/https://raw.githubusercontent.com/ichichuang/cinema-tv/main/update/version.json",
            "https://cdn.jsdelivr.net/gh/ichichuang/cinema-tv@main/update/version.json",
        ])

    def test_source_catalog_defaults_use_main_repository(self):
        self.assertEqual(defaults("DEFAULT_CATALOG_URLS"), [
            "https://gh-proxy.com/https://raw.githubusercontent.com/ichichuang/cinema-tv/main/update/sources.json",
            "https://cdn.jsdelivr.net/gh/ichichuang/cinema-tv@main/update/sources.json",
        ])
        self.assertTrue((ROOT / "update/sources.json").is_file())
        self.assertTrue((ROOT / "update/version.json").is_file())


if __name__ == "__main__":
    unittest.main()

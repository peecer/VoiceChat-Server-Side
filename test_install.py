import importlib.util, json, tempfile, unittest
from pathlib import Path
root = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("installer", root / "install.py")
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)
class Tests(unittest.TestCase):
    def test_rejects_tampered_download(self):
        dep = json.loads((root / "dependencies.lock.json").read_text())["dependencies"][0]
        with self.assertRaises(ValueError):
            mod.verify(b"bad jar", dep)
    def test_lock_has_unique_ids_and_https(self):
        lock = json.loads((root / "dependencies.lock.json").read_text())
        deps = lock["dependencies"]
        self.assertEqual(len(deps), len({d["mod_id"] for d in deps}))
        for d in deps:
            self.assertTrue(d["url"].startswith("https://cdn.modrinth.com/"))
            self.assertEqual(len(d["sha512"]), 128)
            self.assertGreater(d["size"], 0)
        if lock["side"] == "client":
            self.assertEqual((lock["minecraft"], lock["fabric_loader"]), ("1.21.11", "0.18.2"))
if __name__ == "__main__":
    unittest.main()

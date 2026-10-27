#!/usr/bin/env python3
"""Download pinned official mods; verify bytes before installing. Python 3.9+."""
import argparse, hashlib, io, json, os, shutil, tempfile, urllib.request, zipfile
from pathlib import Path
ROOT = Path(__file__).resolve().parent

def verify(data, dep):
    if len(data) != dep["size"] or hashlib.sha512(data).hexdigest() != dep["sha512"]:
        raise ValueError("Checksum/size mismatch: " + dep["filename"])
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        meta = json.loads(z.read("fabric.mod.json"))
        if meta["id"] != dep["mod_id"] or meta["version"] != dep["mod_version"]:
            raise ValueError("Unexpected mod metadata: " + dep["filename"])
        if z.testzip():
            raise ValueError("Corrupt JAR: " + dep["filename"])
    return data

def install(output, cache=None):
    lock = json.loads((ROOT / "dependencies.lock.json").read_text())
    mods = output / "mods"
    all_deps = lock["dependencies"] + ([lock["addon"]] if "addon" in lock else [])
    wanted = {d["mod_id"]: d for d in all_deps}
    # Refuse duplicate mod IDs or a competing voice engine, regardless of filename.
    if mods.exists():
        for jar in mods.glob("*.jar"):
            with zipfile.ZipFile(jar) as z:
                if "fabric.mod.json" not in z.namelist():
                    continue
                meta = json.loads(z.read("fabric.mod.json"))
            if meta["id"] == "plasmovoice":
                raise ValueError("Remove Plasmo Voice before installing this voice setup: " + str(jar))
            if meta["id"] in wanted:
                dep = wanted[meta["id"]]
                if jar.name != dep["filename"]:
                    raise ValueError("Move the old/conflicting mod out first: " + str(jar))
                verify(jar.read_bytes(), dep)
    # Download every file before touching the target directory.
    with tempfile.TemporaryDirectory(prefix="voicechat-") as temp:
        staging = Path(temp)
        for dep in all_deps:
            cached = (ROOT / "addons" / dep["filename"]) if dep == lock.get("addon") else (cache / dep["filename"] if cache else None)
            if cached and cached.is_file():
                data = cached.read_bytes()
            else:
                req = urllib.request.Request(dep["url"], headers={"User-Agent": "PeecerVoiceSetup/1.0"})
                with urllib.request.urlopen(req, timeout=120) as response:
                    data = response.read()
            (staging / dep["filename"]).write_bytes(verify(data, dep))
        mods.mkdir(parents=True, exist_ok=True)
        for dep in all_deps:
            target = mods / dep["filename"]
            if not target.exists():
                tmp = target.with_suffix(".jar.part")
                shutil.copyfile(staging / dep["filename"], tmp)
                os.replace(tmp, target)
    if lock["side"] == "server":
        cfg = output / "config/voicechat/voicechat-server.properties"
        cfg.parent.mkdir(parents=True, exist_ok=True)
        if not cfg.exists():
            shutil.copyfile(ROOT / "config/voicechat/voicechat-server.properties", cfg)
        else:
            print("Existing voice config preserved. Ensure enable_groups=true and check UDP port.")
    print(f"Installed {lock['side']} mods for Minecraft {lock['minecraft']}, Fabric Loader {lock['fabric_loader']}.")
    print("Install that Minecraft/Fabric runtime separately; this installer only installs mods/config.")

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "installed")
    parser.add_argument("--cache", type=Path, help="Optional local JAR cache; checksums still verified")
    args = parser.parse_args()
    install(args.output.resolve(), args.cache.resolve() if args.cache else None)

if __name__ == "__main__":
    main()

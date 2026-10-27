#!/usr/bin/env python3
"""Build a Modrinth client pack or server installer ZIP without republishing upstream JARs."""
import json, zipfile
from pathlib import Path
root = Path(__file__).resolve().parent
lock = json.loads((root / "dependencies.lock.json").read_text())
out = root / "dist"
out.mkdir(exist_ok=True)
if lock["side"] == "client":
    manifest = {"formatVersion": 1, "game": "minecraft", "versionId": "1.0.0",
                "name": "Peecer Voice Chat Client", "summary": "Simple voice chat and private global groups",
                "dependencies": {"minecraft": lock["minecraft"], "fabric-loader": lock["fabric_loader"]},
                "files": [{"path": "mods/" + d["filename"],
                           "hashes": {"sha1": d["sha1"], "sha512": d["sha512"]},
                           "env": {"client": "required", "server": "unsupported"},
                           "downloads": [d["url"]], "fileSize": d["size"]} for d in lock["dependencies"]]}
    dest = out / "VoiceChat-Client-1.21.11-Fabric-0.18.2.mrpack"
    with zipfile.ZipFile(dest, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("modrinth.index.json", json.dumps(manifest, indent=2))
        z.write(root / "README.md", "overrides/VOICECHAT-README.md")
        z.write(root / "addons" / lock["addon"]["filename"], "overrides/mods/" + lock["addon"]["filename"])
else:
    dest = out / "VoiceChat-Server-26.3-Setup.zip"
    with zipfile.ZipFile(dest, "w", zipfile.ZIP_DEFLATED) as z:
        for name in ["README.md", "dependencies.lock.json", "install.py", "install-windows.bat", "install-unix.sh", "config/voicechat/voicechat-server.properties"]:
            z.write(root / name, name)
        z.write(root / "addons" / lock["addon"]["filename"], "addons/" + lock["addon"]["filename"])
print(dest)

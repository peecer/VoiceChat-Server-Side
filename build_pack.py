#!/usr/bin/env python3
"""Build the Leaf/Paper installer bundle; upstream plugins are downloaded on install."""
import json, zipfile
from pathlib import Path
root = Path(__file__).resolve().parent
lock = json.loads((root / 'dependencies.lock.json').read_text())
out = root / 'dist'; out.mkdir(exist_ok=True)
dest = out / 'VoiceChat-Leaf-Paper-26.3-1.0.1-Setup.zip'
with zipfile.ZipFile(dest, 'w', zipfile.ZIP_DEFLATED) as z:
    for name in ['README.md', 'dependencies.lock.json', 'install.py', 'install-windows.bat', 'install-unix.sh', 'config/voicechat-server.properties']:
        z.write(root / name, name)
    z.write(root / 'addons' / lock['addon']['filename'], 'addons/' + lock['addon']['filename'])
print(dest)

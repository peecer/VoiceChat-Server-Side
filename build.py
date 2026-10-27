#!/usr/bin/env python3
"""Build and test the TCP addon using a real upstream voice API JAR and JDK 25."""
import argparse, hashlib, json, os, subprocess, zipfile, shutil
from pathlib import Path
root = Path(__file__).resolve().parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--cache', type=Path)
args = parser.parse_args()
lock = json.loads((root / 'dependencies.lock.json').read_text())
dep = next(d for d in lock['dependencies'] if d['project'] == 'simple-voice-chat')
# Import the verification/downloading helper without installing or accepting an EULA.
from install import verify
import urllib.request
cached = args.cache / dep['filename'] if args.cache else None
if cached and cached.exists():
    data = cached.read_bytes()
else:
    with urllib.request.urlopen(dep['url'], timeout=120) as r: data = r.read()
verify(data, dep)
build = root / 'build'; build.mkdir(exist_ok=True)
api = build / dep['filename']; api.write_bytes(data)
classes = build / 'classes'
if classes.exists(): shutil.rmtree(classes)
classes.mkdir()
testclasses = build / 'testclasses'; testclasses.mkdir(exist_ok=True)
java_home = os.environ.get('JAVA_HOME')
def tool(name):
    return str(Path(java_home) / 'bin' / name) if java_home else name
build_dep = lock['build_dependencies'][0]
cached_api = args.cache / build_dep['filename'] if args.cache else None
if cached_api and cached_api.exists(): api_data = cached_api.read_bytes()
else:
    with urllib.request.urlopen(build_dep['url'], timeout=120) as response: api_data = response.read()
if len(api_data) != build_dep['size'] or hashlib.sha512(api_data).hexdigest() != build_dep['sha512']:
    raise ValueError('Build API checksum mismatch')
bukkit_api = build / build_dep['filename']; bukkit_api.write_bytes(api_data)
compile_cp = os.pathsep.join(map(str, [api, bukkit_api]))
sources = sorted((root / 'src/main/java').rglob('*.java'))
subprocess.run([tool('javac'), '--release', '21', '-Xlint:all,-path', '-cp', compile_cp, '-d', str(classes), *map(str, sources)], check=True)
cp = os.pathsep.join(map(str, [classes, api]))
subprocess.run([tool('javac'), '--release', '21', '-cp', cp, '-d', str(testclasses), *map(str, (root / 'src/test/java').rglob('*.java'))], check=True)
subprocess.run([tool('java'), '-cp', os.pathsep.join([str(testclasses), cp]), 'peecer.voice.tcp.TransportTest'], check=True, timeout=30)
meta = {'name': 'PeecerVoiceTCP', 'version': '1.0.1'}
addon = root / 'addons' / 'peecer-voice-tcp-paper-1.0.1.jar'
addon.parent.mkdir(exist_ok=True)
with zipfile.ZipFile(addon, 'w', zipfile.ZIP_DEFLATED) as z:
    for p in sorted(classes.rglob('*.class')): z.write(p, str(p.relative_to(classes)))
    z.write(root / 'src/main/resources/plugin.yml', 'plugin.yml')
    z.write(root / 'LICENSE', 'LICENSE')
data = addon.read_bytes()
lock['addon'] = {'filename': addon.name, 'sha512': hashlib.sha512(data).hexdigest(), 'size': len(data), 'plugin_name': meta['name'], 'plugin_version': meta['version']}
(root / 'dependencies.lock.json').write_text(json.dumps(lock, indent=2) + '\n')
print(addon)

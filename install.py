#!/usr/bin/env python3
"""Install pinned Paper/Leaf voice plugins. Python 3.9+. No Fabric server mods."""
import argparse, hashlib, io, json, os, re, shutil, tempfile, urllib.request, zipfile
from pathlib import Path
ROOT = Path(__file__).resolve().parent

def plugin_name(data):
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        if 'fabric.mod.json' in z.namelist() and 'plugin.yml' not in z.namelist():
            raise ValueError('Fabric mod supplied where a Paper plugin is required')
        descriptor = z.read('plugin.yml').decode('utf-8')
        match = re.search(r'^name:\s*[\'\"]?([^\'\"\r\n]+)', descriptor, re.MULTILINE)
        if not match: raise ValueError('Missing plugin name')
        if z.testzip(): raise ValueError('Corrupt JAR')
        return match.group(1).strip()

def verify(data, dep):
    if len(data) != dep['size'] or hashlib.sha512(data).hexdigest() != dep['sha512']:
        raise ValueError('Checksum/size mismatch: ' + dep['filename'])
    if plugin_name(data) != dep['plugin_name']:
        raise ValueError('Unexpected plugin: ' + dep['filename'])
    return data

def install(output, cache=None):
    lock = json.loads((ROOT / 'dependencies.lock.json').read_text())
    deps = lock['dependencies'] + [lock['addon']]
    wanted = {d['plugin_name'].lower(): d for d in deps}
    plugins = output / 'plugins'
    if plugins.exists():
        for jar in plugins.glob('*.jar'):
            data = jar.read_bytes()
            with zipfile.ZipFile(io.BytesIO(data)) as z:
                if 'fabric.mod.json' in z.namelist() and 'plugin.yml' not in z.namelist():
                    raise ValueError('Move this Fabric mod out of plugins before continuing: ' + str(jar))
                if 'plugin.yml' not in z.namelist(): continue
            name = plugin_name(data).lower()
            if name in wanted:
                dep = wanted[name]
                if jar.name != dep['filename']:
                    raise ValueError('Move old/renamed duplicate plugin out first: ' + str(jar))
                verify(data, dep)
    with tempfile.TemporaryDirectory(prefix='voice-paper-') as temp:
        staging = Path(temp)
        for dep in deps:
            cached = (ROOT / 'addons' / dep['filename']) if dep == lock['addon'] else (cache / dep['filename'] if cache else None)
            if cached and cached.is_file(): data = cached.read_bytes()
            else:
                with urllib.request.urlopen(dep['url'], timeout=120) as response: data = response.read()
            (staging / dep['filename']).write_bytes(verify(data, dep))
        plugins.mkdir(parents=True, exist_ok=True)
        for dep in deps:
            target = plugins / dep['filename']
            if not target.exists():
                tmp = target.with_suffix('.jar.part')
                shutil.copyfile(staging / dep['filename'], tmp)
                os.replace(tmp, target)
    cfg = plugins / 'voicechat/voicechat-server.properties'
    cfg.parent.mkdir(parents=True, exist_ok=True)
    if not cfg.exists(): shutil.copyfile(ROOT / 'config/voicechat-server.properties', cfg)
    else: print('Existing voice config preserved. Check enable_groups=true and TCP port=25791 (or your allocation).')
    print('Installed Paper/Leaf plugins into ' + str(plugins))
    print('Keep your existing Leaf/Paper server. No Fabric server loader, ViaFabric or Fabric API is needed.')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT / 'installed')
    parser.add_argument('--cache', type=Path)
    args = parser.parse_args()
    install(args.output.resolve(), args.cache.resolve() if args.cache else None)

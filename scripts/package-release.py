#!/usr/bin/env python3
"""Package prebuilt release APKs and ARM64 OCR; sign using a local development key."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def run(*args, **kwargs):
    return subprocess.check_output(args, text=True, cwd=ROOT, **kwargs).strip()

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', type=Path, default=Path(os.environ.get('ANDROID_SDK_ROOT', str(Path.home() / 'Library/Android/sdk'))))
    parser.add_argument('--keystore', type=Path, default=Path.home() / '.android/debug.keystore')
    args = parser.parse_args()
    dirty = run('git', 'diff', 'HEAD', '--name-only').splitlines()
    if any(not p.startswith('.idea/') for p in dirty):
        raise SystemExit('Commit release sources first (local .idea changes may remain).')
    config = (ROOT / 'controller-app/build.gradle').read_text()
    version = re.search(r'versionName = "([^"]+)"', config).group(1)
    code = int(re.search(r'versionCode = (\d+)', config).group(1))
    shell_config = (ROOT / 'android-shell/build.gradle').read_text()
    assert f'versionName = "{version}"' in shell_config
    assert f'versionCode = {code}' in shell_config
    tag = 'v' + version
    out = ROOT / 'dist' / tag
    if out.exists():
        raise SystemExit(f'Output exists: {out}; preserve or remove it explicitly before rebuilding.')
    tools = args.sdk / 'build-tools/36.0.0'
    ndk = args.sdk / 'ndk/23.1.7779620'
    inputs = {
        'controller': ROOT / 'controller-app/build/outputs/apk/release/controller-app-release-unsigned.apk',
        'shell': ROOT / 'android-shell/build/outputs/apk/release/android-shell-release-unsigned.apk',
    }
    runtime = ROOT / 'android-shell/build/ocr-runtime'
    for path in [*inputs.values(), args.keystore, tools/'apksigner', tools/'zipalign', runtime/'lib/arm64-v8a/libNative.so', runtime/'models/ch_PP-OCRv2/det_db.nb', ndk/'NOTICE', ndk/'NOTICE.toolchain']:
        if not path.is_file():
            raise SystemExit(f'Missing build input: {path}')
    out.mkdir(parents=True)
    stage = out / f'QuietPilot-{tag}-android-arm64'
    stage.mkdir()
    licenses = stage / 'licenses'
    shutil.copytree(ROOT/'docs/third-party', licenses)
    shutil.copy2(ROOT/'paddler-ocr/OpenCV/LICENSE', licenses/'OpenCV-LICENSE.txt')
    shutil.copy2(ndk/'NOTICE', licenses/'Android-NDK-NOTICE.txt')
    shutil.copy2(ndk/'NOTICE.toolchain', licenses/'Android-NDK-TOOLCHAIN-NOTICE.txt')
    for name in ['LICENSE', 'NOTICE']:
        shutil.copy2(ROOT/name, stage/name)
        shutil.copy2(ROOT/name, licenses/name)
    certs = {}
    for kind, source in inputs.items():
        unsigned = out / f'{kind}-notices-unsigned.apk'
        with zipfile.ZipFile(source) as src, zipfile.ZipFile(unsigned, 'w', compression=zipfile.ZIP_DEFLATED) as dst:
            for info in src.infolist():
                dst.writestr(info, src.read(info.filename))
            for path in sorted(licenses.iterdir()):
                dst.write(path, 'assets/quietpilot-licenses/'+path.name)
        aligned = out / f'{kind}-aligned.apk'
        subprocess.run([str(tools/'zipalign'), '-f', '-p', '4', str(unsigned), str(aligned)], check=True)
        target = out / f'QuietPilot-{tag}-{kind}.apk'
        sign_env = dict(os.environ, QUIETPILOT_SIGN_PASS='android')
        subprocess.run([str(tools/'apksigner'), 'sign', '--ks', str(args.keystore), '--ks-key-alias', 'androiddebugkey', '--ks-pass', 'env:QUIETPILOT_SIGN_PASS', '--key-pass', 'env:QUIETPILOT_SIGN_PASS', '--out', str(target), str(aligned)], env=sign_env, check=True)
        certs[kind] = run(str(tools/'apksigner'), 'verify', '--verbose', '--print-certs', str(target))
        subprocess.run([str(tools/'zipalign'), '-c', '-p', '4', str(target)], check=True)
        unsigned.unlink()
        aligned.unlink()
    controller = out / f'QuietPilot-{tag}-controller.apk'
    shutil.copy2(controller, stage/controller.name)
    shutil.copy2(out/f'QuietPilot-{tag}-shell.apk', stage/'silent-shell.apk')
    shutil.copytree(runtime, stage/'ocr')
    shutil.copy2(ROOT/'scripts/release/start-quietpilot.sh', stage/'start-quietpilot.sh')
    shutil.copy2(ROOT/'docs/INSTALL.md', stage/'INSTALL.md')
    shutil.copy2(ROOT/f'docs/releases/{tag}.md', stage/'RELEASE-NOTES.md')
    badging = {kind: run(str(tools/'aapt'), 'dump', 'badging', str(out/f'QuietPilot-{tag}-{kind}.apk')) for kind in inputs}
    for kind in inputs:
        assert 'application-debuggable' not in badging[kind], kind
        assert f"versionCode='{code}'" in badging[kind]
        assert f"versionName='{version}'" in badging[kind]
        assert "sdkVersion:'29'" in badging[kind]
    assert "name='com.silentauto.controller'" in badging['controller']
    assert "application-label:'QuietPilot'" in badging['controller']
    assert "application-label-zh:'隐航'" in badging['controller']
    assert list((stage/'ocr/lib').iterdir()) == [stage/'ocr/lib/arm64-v8a']
    inner_hashes = {str(p.relative_to(stage)):sha(p) for p in sorted(stage.rglob('*')) if p.is_file()}
    (stage/'FILES-SHA256.json').write_text(json.dumps(inner_hashes, indent=2)+'\n')
    archive = out / (stage.name+'.zip')
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
        for path in sorted(stage.rglob('*')):
            if path.is_file():
                z.write(path, str(path.relative_to(out)))
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None
        assert not any(x.startswith('/') or '..' in Path(x).parts for x in z.namelist())
    sums = {p.name:sha(p) for p in [controller, archive]}
    (out/'SHA256SUMS.txt').write_text(''.join(f'{h}  {name}\n' for name,h in sums.items()))
    manifest = {'tag':tag, 'sourceCommit':run('git','rev-parse','HEAD'), 'versionCode':code, 'signing':'existing Android debug/development certificate; release builds, non-debuggable; no key distributed', 'signerVerification':certs, 'apkBadging':badging, 'artifactSHA256':sums, 'zipFiles':len(inner_hashes)+1, 'abi':'arm64-v8a', 'minSdk':29, 'physicalInstallTest':False}
    (out/'release-manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2)+'\n')
    print(json.dumps({'tag':tag,'sourceCommit':manifest['sourceCommit'],'output':str(out),'uploadAssets':[controller.name, archive.name,'SHA256SUMS.txt','release-manifest.json']}, ensure_ascii=False))

if __name__ == '__main__':
    main()

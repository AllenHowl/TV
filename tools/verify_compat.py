#!/usr/bin/env python3
"""Verify, collect and document the four installable compatibility APKs."""
from pathlib import Path
from datetime import datetime
from zoneinfo import ZoneInfo
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SYSTEM_LIBS = {'libandroid.so','libc.so','libdl.so','libEGL.so','libGLESv1_CM.so','libGLESv2.so','libGLESv3.so','liblog.so','libm.so','libOpenMAXAL.so','libOpenSLES.so','libstdc++.so','libvulkan.so','libz.so','libmediandk.so','libjnigraphics.so','libnativewindow.so','libaaudio.so','libcamera2ndk.so','libbinder_ndk.so'}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--project', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    sdk = Path(os.environ.get('ANDROID_HOME', '/workspace/android-sdk'))
    build_tools = sdk / 'build-tools/37.0.0'
    readelf = sdk / 'ndk/29.0.14206865/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf'
    args.output.mkdir(parents=True, exist_ok=True)
    results = []
    variants = [(flavor, label, variant, abi, arch) for flavor, label in [('leanback','tv'), ('mobile','mobile')] for variant, abi, arch in [('Arm64_v8a', 'arm64-v8a', 'arm64'), ('Armeabi_v7a', 'armeabi-v7a', 'arm32')]]
    for flavor, label, variant, abi, arch in variants:
        flavor_abi = abi.replace('-', '_')
        source = args.project / f'app/build/outputs/apk/{flavor}{variant}/debug/app-{flavor}-{flavor_abi}-debug.apk'
        assert source.is_file(), f'APK missing: {source}'
        verification = subprocess.check_output([str(build_tools / 'apksigner'),'verify','--verbose','--print-certs',str(source)], text=True)
        assert 'Verifies' in verification and 'v2): true' in verification
        badging = subprocess.check_output([str(build_tools / 'aapt2'),'dump','badging',str(source)], text=True)
        assert "name='com.fongmi.android.tv.sources'" in badging
        assert "versionName='5.5.6-sources-compat.3'" in badging
        assert "versionCode='558'" in badging
        assert "minSdkVersion:'24'" in badging
        assert f"native-code: '{abi}'" in badging
        manifest = subprocess.check_output([str(build_tools / 'aapt2'),'dump','xmltree',str(source),'--file','AndroidManifest.xml'], text=True)
        assert 'com.fongmi.android.tv.App' in manifest
        with zipfile.ZipFile(source) as archive, tempfile.TemporaryDirectory(prefix='tv-apk-native-') as temp:
            catalog = json.loads(archive.read('assets/builtin_sources.json'))
            vod_count = sum(item['type'] == 0 for item in catalog['sources'])
            live_count = sum(item['type'] == 1 for item in catalog['sources'])
            assert (vod_count,live_count) == (56,2)
            dex = [archive.read(name) for name in archive.namelist() if re.fullmatch(r'classes\d*\.dex',name)]
            for component in [b'Lcom/fongmi/android/tv/ui/dialog/SourceDialog;',b'Lcom/fongmi/android/tv/source/SourceCatalog;', b'Lcom/fongmi/android/tv/ui/dialog/SourceDialogLayout;']:
                assert any(component in item for item in dex), f'Missing source manager component: {component}'
            libraries = [name for name in archive.namelist() if name.startswith('lib/') and name.endswith('.so')]
            assert all(name.startswith(f'lib/{abi}/') for name in libraries)
            packaged = {Path(name).name for name in libraries}
            needed = set()
            alignments = {}
            for name in libraries:
                path = Path(temp) / Path(name).name
                path.write_bytes(archive.read(name))
                dynamic = subprocess.check_output([str(readelf),'-d',str(path)],text=True)
                needed.update(re.findall(r'Shared library: \[(.*?)\]',dynamic))
                headers = subprocess.check_output([str(readelf),'-lW',str(path)],text=True)
                load_alignment = [int(line.split()[-1],16) for line in headers.splitlines() if line.strip().startswith('LOAD')]
                alignments[path.name] = min(load_alignment)
            unresolved = sorted(needed - packaged - SYSTEM_LIBS)
            assert not unresolved, f'Unresolved native dependencies: {unresolved}'
        target = args.output / f'TV-Sources-{label}-{arch}-debug.apk'
        shutil.copy2(source,target)
        (args.output / f'{label}-{arch}-signature.txt').write_text(verification)
        (args.output / f'{label}-{arch}-package.txt').write_text(badging)
        results.append({'file':target.name,'bytes':target.stat().st_size,'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'signature':'APK Signature Scheme v2 verified','certificate_sha256':re.search(r'certificate SHA-256 digest: (.*)',verification).group(1),'package':'com.fongmi.android.tv.sources','version':'5.5.6-sources-compat.3','version_code':558,'min_sdk':24,'abi':abi,'vod_entries':vod_count,'live_entries':live_count,'native_libraries':len(libraries),'unresolved_native_dependencies':unresolved,'native_16k_alignment':all(value>=0x4000 for value in alignments.values()),'elf_alignments':alignments})
    assert len({item['certificate_sha256'] for item in results}) == 1
    tests = []
    for variant in ['LeanbackArmeabi_v7a', 'MobileArmeabi_v7a']:
        group = []
        for path in (args.project / f'app/build/test-results/test{variant}DebugUnitTest').glob('TEST-*.xml'):
            root = ET.parse(path).getroot()
            item = {key: root.attrib.get(key) for key in ['name','tests','failures','errors','skipped']}
            item['variant'] = variant
            group.append(item)
        assert sum(int(item['tests']) for item in group) == 20, (variant, group)
        assert any(item['name'].endswith('SourceDialogLayoutTest') and int(item['tests']) == 9 for item in group)
        assert all(int(item['failures']) + int(item['errors']) + int(item['skipped']) == 0 for item in group)
        tests.extend(group)
    info = {'built_at':datetime.now(ZoneInfo('Asia/Hong_Kong')).isoformat(timespec='seconds'),'tv_baseline':'4573249cd6679c6becca9957f6a011bf6c4a5afd','media_commit':'3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d','artifacts':results,'gradle_tests':tests,'device_validation':'Not run: no connected Android device; no KVM device available.','repository':'https://github.com/AllenHowl/TV','tag':'v5.5.6-sources-compat.3','reproduction':'JAVA_HOME=<JDK21> bash tools/build_compat.sh'}
    (args.output / 'build-info.json').write_text(json.dumps(info,ensure_ascii=False,indent=2)+'\n')
    (args.output / 'SHA256SUMS').write_text(''.join(item['sha256']+'  '+item['file']+'\n' for item in results))
    print(json.dumps({'artifacts':[{key:item[key] for key in ['file','bytes','package','signature','native_16k_alignment']} for item in results],'gradle_test_executions':40},ensure_ascii=False,indent=2))


if __name__ == '__main__':
    main()

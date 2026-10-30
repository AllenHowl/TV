#!/usr/bin/env python3
"""Run the Android-independent source tests even if optional player AARs are absent."""
import hashlib
import argparse
import pathlib
import os
import subprocess
import tempfile
import tomllib
from source_test_fixtures import write_stubs

ROOT = pathlib.Path(__file__).resolve().parents[1]
CATALOG = tomllib.loads((ROOT / 'gradle/libs.versions.toml').read_text())
DEPENDENCIES = [
    ('com/google/code/gson/gson', CATALOG['versions']['gson'], 'gson'),
    ('junit/junit', CATALOG['versions']['junit'], 'junit'),
    ('org/hamcrest/hamcrest-core', '1.3', 'hamcrest-core'),
]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--project', type=pathlib.Path, default=ROOT)
    project = parser.parse_args().project.resolve()
    catalog = tomllib.loads((project / 'gradle/libs.versions.toml').read_text())
    dependencies = [
        ('com/google/code/gson/gson', catalog['versions']['gson'], 'gson'),
        ('junit/junit', catalog['versions']['junit'], 'junit'),
        ('org/hamcrest/hamcrest-core', '1.3', 'hamcrest-core'),
    ]
    with tempfile.TemporaryDirectory(prefix='tv-source-tests-') as directory:
        work = pathlib.Path(directory)
        jars = []
        for group, version, artifact in dependencies:
            filename = f'{artifact}-{version}.jar'
            url = f'https://repo.maven.apache.org/maven2/{group}/{version}/{filename}'
            target = work / filename
            checksum = work / (filename + '.sha1')
            for address, path in [(url, target), (url + '.sha1', checksum)]:
                subprocess.run(['curl', '-fsSL', '--connect-timeout', '15', '--max-time', '90', address, '-o', str(path)], check=True)
            expected = checksum.read_text().split()[0].lower()
            if hashlib.sha1(target.read_bytes()).hexdigest() != expected:
                raise RuntimeError(f'Checksum mismatch: {filename}')
            jars.append(str(target))
        sources = project / 'app/src/main/java/com/fongmi/android/tv/source'
        tests = project / 'app/src/test/java/com/fongmi/android/tv/source'
        classes = work / 'classes'
        classes.mkdir()
        classpath = ':'.join(jars)
        jdk = pathlib.Path(os.environ['JAVA_HOME']) / 'bin' if os.environ.get('JAVA_HOME') else None
        javac = str(jdk / 'javac') if jdk else 'javac'
        java = str(jdk / 'java') if jdk else 'java'
        queue_stubs = write_stubs(work / "queue-services")
        queue_core = project / "app/src/main/java/com/fongmi/android/tv/api/config/BaseConfig.java"
        queue_tests = ROOT / "tools/fixtures/SourceSwitchTest.java"
        subprocess.run([javac, '-cp', classpath, '-d', str(classes),
                        str(sources / 'SourceContent.java'), str(sources / 'SourceDirectory.java'),
                        str(tests / 'SourceContentTest.java'), str(tests / 'SourceDirectoryTest.java'),
                        str(queue_core), str(queue_tests), *queue_stubs], check=True)
        subprocess.run([java, '-cp', str(classes) + ':' + classpath, 'org.junit.runner.JUnitCore',
                        'com.fongmi.android.tv.source.SourceContentTest',
                        'com.fongmi.android.tv.source.SourceDirectoryTest',
                        'com.fongmi.android.tv.api.config.SourceSwitchTest'], cwd=project / 'app', check=True)


if __name__ == '__main__':
    main()

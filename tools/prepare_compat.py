#!/usr/bin/env python3
"""Stage a public-player-compatible TV application without changing the primary checkout."""
from pathlib import Path
import argparse
import shutil
import subprocess
from compat.adapt import adapt

ROOT = Path(__file__).resolve().parents[1]
BASE = '4573249cd6679c6becca9957f6a011bf6c4a5afd'  # TV 5.5.6, before unpublished player effects.


def edit(path, old, new, count=1):
    text = path.read_text()
    if old not in text:
        raise RuntimeError(f'Port anchor missing in {path}: {old[:80]}')
    path.write_text(text.replace(old, new, count))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    out = args.output.resolve()
    if out == ROOT or ROOT in out.parents:
        raise RuntimeError('Compatibility build must be staged outside the primary checkout')
    marker = out / '.compat-generated'
    if out.exists() and any(out.iterdir()) and not marker.exists():
        raise RuntimeError('Refusing to overwrite an unmarked directory')
    out.mkdir(parents=True, exist_ok=True)
    archive = subprocess.Popen(['git', '-C', str(ROOT), 'archive', BASE], stdout=subprocess.PIPE)
    subprocess.run(['tar', '-xf', '-', '-C', str(out)], stdin=archive.stdout, check=True)
    archive.stdout.close()
    if archive.wait():
        raise RuntimeError('Baseline archive failed')
    marker.write_text(BASE + '\n')
    app = out / 'app/src/main/java/com/fongmi/android/tv'
    source_app = ROOT / 'app/src/main/java/com/fongmi/android/tv'
    shutil.copytree(source_app / 'source', app / 'source', dirs_exist_ok=True)
    shutil.copy(source_app / 'ui/dialog/SourceDialog.java', app / 'ui/dialog/SourceDialog.java')
    shutil.copy(source_app / 'ui/dialog/SourceDialogLayout.java', app / 'ui/dialog/SourceDialogLayout.java')
    edit(app / 'ui/dialog/SourceDialog.java', '"leanback".equals(BuildConfig.FLAVOR)', 'BuildConfig.FLAVOR.startsWith("leanback")')
    for folder in ['values', 'values-zh-rCN', 'values-zh-rTW']:
        shutil.copy(ROOT / 'app/src/main/res' / folder / 'sources.xml', out / 'app/src/main/res' / folder / 'sources.xml')
    shutil.copy(ROOT / 'app/src/main/assets/builtin_sources.json', out / 'app/src/main/assets/builtin_sources.json')
    # The old baseline uses an application Task facade; the queue implementation is shared.
    shutil.copy(source_app / 'api/config/BaseConfig.java', app / 'api/config/BaseConfig.java')
    edit(app / 'api/config/BaseConfig.java', 'import com.github.catvod.utils.Task;', 'import com.fongmi.android.tv.utils.Task;')
    loader = app / 'api/loader/BaseLoader.java'
    original_loader = (source_app / 'api/loader/BaseLoader.java').read_text()
    stage = original_loader[original_loader.index('    /** Retain the previous loader'):original_loader.index('    private static boolean isJs')]
    edit(loader, '    private static boolean isJs', stage + '    private static boolean isJs')

    vod = app / 'api/config/VodConfig.java'
    current_vod = (source_app / 'api/config/VodConfig.java').read_text()
    snapshot = current_vod[current_vod.index('    @Override\n    protected void prepare'):current_vod.index('    @Override\n    protected void afterLoaded')]
    edit(vod, '    @Override\n    protected String getTag()', snapshot + '''    @Override
    protected void afterLoaded() {
        if (SourcePreferences.followsVod() && !LiveConfig.get().getConfig().isEmpty()) LiveConfig.get().getConfig().update();
    }

    @Override
    protected String getTag()''')
    imports = '\nimport com.fongmi.android.tv.source.SourceContent;\nimport com.fongmi.android.tv.source.SourcePreferences;\nimport com.fongmi.android.tv.source.SourceSettings;'
    edit(vod, 'import com.fongmi.android.tv.App;', 'import com.fongmi.android.tv.App;' + imports)
    edit(vod, 'get().clear().config(config).load(callback);', 'get().switchConfig(config, callback);')
    edit(vod, '        checkJson(config, Json.parse(json).getAsJsonObject());', '        SourceContent.requireContent(json);\n        checkJson(config, Json.parse(json).getAsJsonObject());')
    edit(vod, '    private void parseConfig(Config config, JsonObject object) {', '    private void parseConfig(Config config, JsonObject object) {\n        if (!object.has("sites") || !object.get("sites").isJsonArray() || object.getAsJsonArray("sites").isEmpty()) throw new IllegalArgumentException("No sites in configuration");')
    edit(vod, '        initLive(config, object);\n', '')
    edit(vod, '        config.setDanmaku(Json.safeString(object, "danmaku"));', '        config.setDanmaku(Json.safeString(object, "danmaku"));\n        initLive(config, object);')
    edit(vod, '        if (Json.isEmpty(object, "lives")) return;', '''        if (!SourcePreferences.followsVod()) return;
        if (Json.isEmpty(object, "lives")) { LiveConfig.get().prepare(Config.create(LIVE)); return; }''')
    edit(vod, 'config(temp.update()).parse(object)', 'config(temp).parse(object)')
    edit(vod, 'Site.findAll()', 'SourceSettings.sites(config.getUrl())')
    edit(vod, 'site -> site.sync(items.get(site.getKey()))', 'site -> site.settingsUrl(config.getUrl()).sync(items.get(site.getKey()))')

    live = app / 'api/config/LiveConfig.java'
    current_live = (source_app / 'api/config/LiveConfig.java').read_text()
    static_load = current_live[current_live.index('    public static void load(Config config'):current_live.index('    public LiveConfig init()')]
    old_live = live.read_text()
    a = old_live.index('    public static void load(Config config'); b = old_live.index('    public LiveConfig init()')
    live.write_text(old_live[:a] + static_load + old_live[b:])
    edit(live, 'import com.fongmi.android.tv.api.Decoder;', 'import com.fongmi.android.tv.api.Decoder;' + imports)
    snapshot = current_live[current_live.index('    @Override\n    public boolean needSync'):current_live.index('    @Override\n    protected String getTag()')]
    edit(live, '    @Override\n    protected String getTag()', snapshot + '    @Override\n    protected String getTag()')
    edit(live, 'this.sync = config.getUrl().equals(VodConfig.getUrl());', 'this.sync = SourcePreferences.followsVod() && config.getUrl().equals(VodConfig.getUrl());')
    edit(live, '        if (Json.isObj(json))', '        SourceContent.requireContent(json);\n        if (Json.isObj(json))')
    edit(live, 'private void parseText(Config config, String text) {', 'private void parseText(Config config, String text) throws java.io.IOException {\n        SourceContent.requirePlaylist(text);')
    edit(live, 'new Live(UrlUtil.getName(config.getUrl()), config.getUrl()).sync();', 'new Live(UrlUtil.getName(config.getUrl()), config.getUrl()).settingsUrl(config.getUrl());\n        SourceSettings.lives(config.getUrl()).stream().filter(item -> item.getName().equals(live.getName())).findFirst().ifPresent(live::sync);')
    edit(live, '    private void parseConfig(Config config, JsonObject object) {', '    private void parseConfig(Config config, JsonObject object) {\n        if (!object.has("lives") || !object.get("lives").isJsonArray() || object.getAsJsonArray("lives").isEmpty()) throw new IllegalArgumentException("No live sources in configuration");')
    edit(live, 'Live.findAll()', 'SourceSettings.lives(config.getUrl())')
    edit(live, 'live -> live.sync(items.get(live.getName()))', 'live -> live.settingsUrl(config.getUrl()).sync(items.get(live.getName()))')
    edit(app / 'api/Decoder.java', '            HttpUrl httpUrl', '            if (!res.isSuccessful()) throw new java.io.IOException("Source HTTP " + res.code());\n            HttpUrl httpUrl')

    for name, loader_name, kind in [('Site', 'VodConfig', 0), ('Live', 'LiveConfig', 1)]:
        path = app / 'bean' / (name + '.java')
        edit(path, 'import com.fongmi.android.tv.App;', 'import com.fongmi.android.tv.App;\nimport com.fongmi.android.tv.api.config.' + loader_name + ';')
        current = (source_app / 'bean' / (name + '.java')).read_text()
        fields = current[current.index('    @Ignore\n    private transient String settingsUrl;'):current.index('    @NonNull\n    @PrimaryKey')]
        edit(path, '    @NonNull\n    @PrimaryKey', fields + '    @NonNull\n    @PrimaryKey')
        edit(path, '        AppDatabase.get().get' + name + 'Dao().insertOrUpdate(this);', '        AppDatabase.get().get' + name + 'Dao().insertOrUpdate(this);\n        com.fongmi.android.tv.source.SourceSettings.save(' + str(kind) + ', getSettingsUrl(), List.of(this));')
    config = app / 'bean/Config.java'
    edit(config, '        Config item = AppDatabase.get().getConfigDao().findOne(0);', '        String url = Prefers.getString("config_0");\n        Config item = TextUtils.isEmpty(url) ? null : AppDatabase.get().getConfigDao().find(url, 0);\n        if (item == null) item = AppDatabase.get().getConfigDao().findOne(0);')
    edit(app / 'db/dao/ConfigDao.java', 'WHERE type = :type ORDER BY time DESC LIMIT 1', 'WHERE type = :type AND time > 0 ORDER BY time DESC LIMIT 1')

    for flavor in ['leanback', 'mobile']:
        ui = out / f'app/src/{flavor}/java/com/fongmi/android/tv/ui'
        setting = ui / ('activity/SettingActivity.java' if flavor == 'leanback' else 'fragment/SettingFragment.java')
        edit(setting, 'import com.fongmi.android.tv.ui.dialog.ConfigDialog;', 'import com.fongmi.android.tv.ui.dialog.ConfigDialog;\nimport com.fongmi.android.tv.ui.dialog.SourceDialog;')
        host = 'this' if flavor == 'leanback' else 'requireActivity()'
        edit(setting, 'ConfigDialog.create().vod().show(this);', 'SourceDialog.show(' + host + ', 0);')
        edit(setting, 'ConfigDialog.create().live().show(this);', 'SourceDialog.show(' + host + ', 1);')
        home = ui / 'activity/HomeActivity.java'
        edit(home, 'import com.fongmi.android.tv.api.config.VodConfig;', 'import com.fongmi.android.tv.api.config.VodConfig;\nimport com.fongmi.android.tv.ui.dialog.SourceDialog;')
        edit(home, '        initConfig();', '        initConfig();\n        App.post(() -> SourceDialog.offer(this), 300);')
        if flavor == 'leanback':
            edit(home, '        mBinding.title.setListener(this);', '        mBinding.title.setListener(this);\n        mBinding.sourcePicker.setOnClickListener(view -> SourceDialog.show(this, 0));')
        live_activity = ui / 'activity/LiveActivity.java'
        edit(live_activity, 'import com.fongmi.android.tv.ui.dialog.LiveDialog;', 'import com.fongmi.android.tv.ui.dialog.LiveDialog;\nimport com.fongmi.android.tv.ui.dialog.SourceDialog;')
        edit(live_activity, 'HistoryDialog.create().live().readOnly().show(this);', 'SourceDialog.show(this, 1);')
        old = live_activity.read_text().replace('                LiveConfig.load(config, new Callback());\n', '')
        live_activity.write_text(old)
    layout = out / 'app/src/leanback/res/layout/activity_home.xml'
    current_layout = (ROOT / 'app/src/leanback/res/layout/activity_home.xml').read_text()
    a = current_layout.index('        <Button\n            android:id="@+id/sourcePicker"'); b = current_layout.index('        <com.google.android.material.textview.MaterialTextView\n            android:id="@+id/clock"', a)
    edit(layout, '        <com.google.android.material.textview.MaterialTextView\n            android:id="@+id/clock"', current_layout[a:b] + '        <com.google.android.material.textview.MaterialTextView\n            android:id="@+id/clock"')
    menu = out / 'app/src/mobile/res/menu/menu_vod.xml'
    edit(menu, '</menu>', '    <item android:id="@+id/sourcePicker" android:title="@string/source_manage" app:showAsAction="never" />\n</menu>')
    fragment = out / 'app/src/mobile/java/com/fongmi/android/tv/ui/fragment/VodFragment.java'
    edit(fragment, 'import com.fongmi.android.tv.ui.dialog.SiteDialog;', 'import com.fongmi.android.tv.ui.dialog.SiteDialog;\nimport com.fongmi.android.tv.ui.dialog.SourceDialog;')
    edit(fragment, '        if (item.getItemId() == R.id.keep)', '        if (item.getItemId() == R.id.sourcePicker) SourceDialog.show(requireActivity(), 0);\n        else if (item.getItemId() == R.id.keep)')

    catalog = out / 'gradle/libs.versions.toml'
    text = catalog.read_text()
    if '\njunit = ' not in text:
        text = text.replace('[versions]', '[versions]\njunit = "4.13.2"', 1)
        text = text.replace('[libraries]', '[libraries]\njunit = { group = "junit", name = "junit", version.ref = "junit" }', 1)
    catalog.write_text(text)

    edit(out / 'chaquo/build.gradle', '            version = "3.10"', '            version = "3.10"\n            buildPython rootProject.providers.gradleProperty("compatBuildPython").getOrElse("python3.10")')

    build = out / 'app/build.gradle'
    edit(build, 'versionName "5.5.6"', 'versionName "5.5.6-sources-compat.3"')
    edit(build, 'versionCode 556', 'versionCode 558')
    edit(build, '    buildFeatures {', '''    testOptions {
        unitTests.includeAndroidResources = true
        unitTests.all {
            systemProperty 'robolectric.dependency.repo.url', 'https://repo.maven.apache.org/maven2'
        }
    }

    buildFeatures {''')
    edit(build, '    buildTypes {', '''    buildTypes {
        debug {
            applicationIdSuffix ".sources"
            signingConfig = signingConfigs.release
        }''')
    edit(build, '    implementation fileTree', "    testImplementation libs.junit\n    testImplementation 'org.robolectric:robolectric:4.16.1'\n    implementation fileTree")
    # Independent app ID also requires matching FileProvider authorities from the manifest.
    shutil.copy(ROOT / 'local.properties', out / 'local.properties')
    shutil.copy(ROOT / 'tools/compat/requirements.txt', out / 'chaquo/requirements.txt')
    shutil.copytree(ROOT / 'app/src/test', out / 'app/src/test', dirs_exist_ok=True)
    for folder in ['values', 'values-zh-rCN', 'values-zh-rTW']:
        strings = out / 'app/src/main/res' / folder / 'strings.xml'
        if strings.exists():
            import re
            text = re.sub(r'(<string name="app_name">).*?(</string>)', r'\1TV Sources\2', strings.read_text())
            strings.write_text(text)
    adapt(out)
    print(f'Prepared TV Sources compatibility build: {out}\nBaseline: {BASE}')


if __name__ == '__main__':
    main()

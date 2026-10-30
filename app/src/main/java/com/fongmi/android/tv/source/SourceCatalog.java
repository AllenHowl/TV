package com.fongmi.android.tv.source;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.github.catvod.utils.Prefers;
import com.fongmi.android.tv.source.SourceDirectory.Entry;
import com.fongmi.android.tv.source.SourceDirectory.Line;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Bundled entries are never bulk-inserted into Config (whose timestamps select the active source). */
public final class SourceCatalog {
    private static volatile List<Entry> bundled;

    private SourceCatalog() {}

    public static List<Entry> bundled() {
        if (bundled != null) return bundled;
        synchronized (SourceCatalog.class) {
            if (bundled != null) return bundled;
            try (InputStream stream = App.get().getAssets().open("builtin_sources.json")) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                for (int count; (count = stream.read(buffer)) != -1;) bytes.write(buffer, 0, count);
                bundled = SourceDirectory.parse(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new IllegalStateException("Unable to read the bundled source directory", e);
            }
            return bundled;
        }
    }

    public static String key(int type, String url) {
        return SourceDirectory.key(bundled(), type, url);
    }

    public static List<Entry> entries(int type) {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : bundled()) if (entry.type == type) result.add(entry);
        for (Config config : Config.getAll(type)) {
            if (config.isEmpty() || result.stream().anyMatch(entry -> entry.contains(config.getUrl()))) continue;
            Entry entry = new Entry();
            entry.id = key(type, config.getUrl());
            entry.type = type;
            entry.name = config.getDesc();
            entry.builtin = false;
            entry.lines = List.of(new Line(config.getDesc(), config.getUrl()));
            result.add(entry);
        }
        return result;
    }

    public static Config current(int type) {
        return type == 0 ? Config.vod() : SourcePreferences.followsVod() ? LiveConfig.get().getConfig() : Config.live();
    }

    public static void rememberCurrent(int type) {
        Config current = current(type);
        if (current.isEmpty()) return;
        String key = key(type, current.getUrl());
        Prefers.put("source_home_" + key, current.getHome());
        if (type == 0) Prefers.put("source_parse_" + key, current.getParse());
    }

    public static Config target(Entry entry, int line) {
        Config config = Config.find(entry.lines.get(line).url, entry.name, entry.type);
        String home = Prefers.getString("source_home_" + entry.id, config.getHome());
        String parse = Prefers.getString("source_parse_" + entry.id, config.getParse());
        config.setHome(home);
        config.setParse(parse);
        return config;
    }
}

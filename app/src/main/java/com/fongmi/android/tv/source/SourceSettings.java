package com.fongmi.android.tv.source;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.db.AppDatabase;
import com.github.catvod.utils.Prefers;

import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Scope legacy global site/live options without changing Room entities or their keys. */
public final class SourceSettings {
    private SourceSettings() {}

    private static String key(int type, String url) {
        return "source_options_" + SourceCatalog.key(type, url);
    }

    public static List<Site> sites(String url) {
        String json = Prefers.getString(key(0, url));
        if (!json.isEmpty()) {
            try {
                Site[] items = App.gson().fromJson(json, Site[].class);
                return items == null ? List.of() : Arrays.asList(items);
            } catch (RuntimeException ignored) { return List.of(); }
        }
        if (url.equals(Config.vod().getUrl()) && !Prefers.getBoolean("source_migrated_0")) {
            List<Site> items = AppDatabase.get().getSiteDao().findAll();
            save(0, url, items);
            Prefers.put("source_migrated_0", true);
            return items;
        }
        return List.of();
    }

    public static List<Live> lives(String url) {
        String json = Prefers.getString(key(1, url));
        if (!json.isEmpty()) {
            try {
                Live[] items = App.gson().fromJson(json, Live[].class);
                return items == null ? List.of() : Arrays.asList(items);
            } catch (RuntimeException ignored) { return List.of(); }
        }
        if (url.equals(Config.live().getUrl()) && !Prefers.getBoolean("source_migrated_1")) {
            List<Live> items = AppDatabase.get().getLiveDao().findAll();
            save(1, url, items);
            Prefers.put("source_migrated_1", true);
            return items;
        }
        return List.of();
    }

    public static void save(int type, String url, List<?> items) {
        if (url == null || url.isEmpty()) return;
        String saved = Prefers.getString(key(type, url), "[]");
        // A single toggle is a patch, not a replacement of every station option.
        if (type == 0) {
            Map<String, Site> merged = new LinkedHashMap<>();
            try {
                Site[] previous = App.gson().fromJson(saved, Site[].class);
                if (previous != null) for (Site item : previous) merged.put(item.getKey(), item);
            } catch (RuntimeException ignored) {}
            for (Object value : items) { Site item = (Site) value; merged.put(item.getKey(), item); }
            Prefers.put(key(type, url), App.gson().toJson(merged.values().stream().map(item -> Map.of(
                    "key", item.getKey(), "searchable", item.getSearchable(), "changeable", item.getChangeable())).toList()));
        } else {
            Map<String, Live> merged = new LinkedHashMap<>();
            try {
                Live[] previous = App.gson().fromJson(saved, Live[].class);
                if (previous != null) for (Live item : previous) merged.put(item.getName(), item);
            } catch (RuntimeException ignored) {}
            for (Object value : items) {
                Live item = (Live) value;
                merged.put(item.getName(), new Live(item.getName(), "").sync(item));
            }
            Prefers.put(key(type, url), App.gson().toJson(merged.values()));
        }
    }
}

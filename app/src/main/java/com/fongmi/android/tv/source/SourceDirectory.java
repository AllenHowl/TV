package com.fongmi.android.tv.source;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Directory validation and stable identities; independent of Android and network access. */
public final class SourceDirectory {
    private SourceDirectory() {}

    public static final class Line {
        @SerializedName("name") public String name;
        @SerializedName("url") public String url;
        public Line(String name, String url) { this.name = name; this.url = url; }
    }

    public static final class Entry {
        @SerializedName("id") public String id;
        @SerializedName("name") public String name;
        @SerializedName("type") public int type;
        @SerializedName("lines") public List<Line> lines;
        @SerializedName("recommended") public boolean recommended;
        public boolean builtin = true;
        public boolean contains(String url) { return lines.stream().anyMatch(line -> line.url.equals(url)); }
    }

    private static final class Document {
        @SerializedName("version") int version;
        @SerializedName("sources") List<Entry> sources;
    }

    public static List<Entry> parse(String json) {
        Document document = new Gson().fromJson(json, Document.class);
        if (document == null || document.version != 1 || document.sources == null) throw new IllegalArgumentException("Invalid source directory");
        Set<String> ids = new HashSet<>();
        for (Entry entry : document.sources) {
            if (entry == null || entry.id == null || entry.id.isBlank() || !ids.add(entry.id)
                    || entry.name == null || entry.name.isBlank() || entry.type < 0 || entry.type > 1
                    || entry.lines == null || entry.lines.isEmpty()) throw new IllegalArgumentException("Invalid source entry");
            Set<String> urls = new HashSet<>();
            for (Line line : entry.lines) {
                if (line == null || line.name == null || line.name.isBlank() || line.url == null
                        || !line.url.matches("https?://[^/\\s]+/.*") || !urls.add(line.url)) throw new IllegalArgumentException("Invalid source line");
            }
        }
        return List.copyOf(document.sources);
    }

    public static String key(List<Entry> sources, int type, String url) {
        for (Entry entry : sources) if (entry.type == type && entry.contains(url)) return entry.id;
        return "custom-" + UUID.nameUUIDFromBytes((type + ":" + url).getBytes(StandardCharsets.UTF_8));
    }

}

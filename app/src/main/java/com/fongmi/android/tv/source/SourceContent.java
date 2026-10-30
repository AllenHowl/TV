package com.fongmi.android.tv.source;

import java.io.IOException;
import java.util.Locale;

/** Content checks deliberately do not trust HTTP 200 or a filename extension. */
public final class SourceContent {
    private SourceContent() {}

    public static void requireContent(String text) throws IOException {
        String value = text == null ? "" : text.replace("\uFEFF", "").strip();
        if (value.isEmpty()) throw new IOException("Empty source configuration");
        String head = value.substring(0, Math.min(512, value.length())).toLowerCase(Locale.ROOT);
        if (head.startsWith("<") && (head.contains("<!doctype html") || head.contains("<html") || head.contains("<head") || head.contains("<body"))) {
            throw new IOException("The source returned a web page instead of a configuration");
        }
    }

    public static void requirePlaylist(String text) throws IOException {
        requireContent(text);
        boolean m3u = text.contains("#EXTM3U") && text.contains("#EXTINF:");
        boolean hasUrl = false;
        for (String line : text.split("\\R")) {
            String value = line.strip();
            if (m3u && !value.startsWith("#") && value.contains("://")) hasUrl = true;
            if (!m3u && value.contains(",") && !value.contains("#genre#") && value.substring(value.indexOf(',') + 1).contains("://")) hasUrl = true;
        }
        if (!hasUrl) throw new IOException("No channel URLs in this playlist");
    }
}

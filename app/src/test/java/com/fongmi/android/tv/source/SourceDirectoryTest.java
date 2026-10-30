package com.fongmi.android.tv.source;

import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.Assert.*;

public class SourceDirectoryTest {
    @Test public void bundledDirectoryGroupsMirrorsAndExcludesBrokenLiveMirrors() throws Exception {
        List<SourceDirectory.Entry> entries = SourceDirectory.parse(Files.readString(Path.of("src/main/assets/builtin_sources.json")));
        assertTrue(entries.stream().filter(entry -> entry.type == 0).count() >= 55);
        assertEquals(3, entries.stream().filter(entry -> entry.type == 0 && entry.recommended).count());
        assertEquals(2, entries.stream().filter(entry -> entry.type == 1).count());
        for (SourceDirectory.Entry entry : entries) {
            assertFalse(entry.lines.isEmpty());
            if (entry.type == 1) assertTrue(entry.lines.get(0).url.startsWith("https://ztha.top/TVBox/lib/"));
        }
    }
    @Test public void mirrorsShareAnIdentityAndCustomSourcesAreTypeScoped() throws Exception {
        List<SourceDirectory.Entry> entries = SourceDirectory.parse(Files.readString(Path.of("src/main/assets/builtin_sources.json")));
        SourceDirectory.Entry entry = entries.stream().filter(item -> item.recommended && item.type == 0).findFirst().orElseThrow();
        for (SourceDirectory.Line line : entry.lines) assertEquals(entry.id, SourceDirectory.key(entries, 0, line.url));
        String custom = "https://example.org/custom.json";
        assertEquals(SourceDirectory.key(entries, 0, custom), SourceDirectory.key(entries, 0, custom));
        assertNotEquals(SourceDirectory.key(entries, 0, custom), SourceDirectory.key(entries, 1, custom));
    }
    @Test public void rejectsDuplicateStableIds() {
        String entry = "{\"id\":\"same\",\"name\":\"Test\",\"type\":0,\"lines\":[{\"name\":\"Original\",\"url\":\"https://example.org/config\"}]}";
        assertThrows(IllegalArgumentException.class, () -> SourceDirectory.parse("{\"version\":1,\"sources\":[" + entry + "," + entry + "]}"));
    }
    @Test public void rejectsEmptyLinesAndUnsupportedDirectoryVersion() {
        assertThrows(IllegalArgumentException.class, () -> SourceDirectory.parse("{\"version\":1,\"sources\":[{\"id\":\"x\",\"name\":\"X\",\"type\":0,\"lines\":[]}]}"));
        assertThrows(IllegalArgumentException.class, () -> SourceDirectory.parse("{\"version\":2,\"sources\":[]}"));
    }
}

package com.fongmi.android.tv.source;

import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.assertThrows;

public class SourceContentTest {
    @Test public void rejectsHomepageReturnedWithAPlaylistExtension() {
        assertThrows(IOException.class, () -> SourceContent.requirePlaylist("<!DOCTYPE html><html><body>Source directory</body></html>"));
    }
    @Test public void rejectsHtmlWithBomWhitespaceAndComment() {
        assertThrows(IOException.class, () -> SourceContent.requireContent("\uFEFF \n<!-- cache -->\n<HTML><HEAD></HEAD></HTML>"));
    }
    @Test public void acceptsM3uRegardlessOfFilenameExtension() throws IOException {
        SourceContent.requirePlaylist("\uFEFF#EXTM3U\r\n#EXTINF:-1,CCTV1\r\nhttps://example.org/live.m3u8\r\n");
    }
    @Test public void acceptsTxtWithoutAnExplicitGroup() throws IOException {
        SourceContent.requirePlaylist("CCTV1,https://example.org/live.m3u8\n");
    }
    @Test public void rejectsEmptyPlaylistAndHeaderOnlyPlaylist() {
        assertThrows(IOException.class, () -> SourceContent.requirePlaylist(""));
        assertThrows(IOException.class, () -> SourceContent.requirePlaylist("#EXTM3U\n#EXTINF:-1,Offline\n"));
    }
    @Test public void rejectsErrorPageContainingOnlyAHelpUrl() {
        assertThrows(IOException.class, () -> SourceContent.requirePlaylist("Request failed. Visit https://example.org/help"));
    }
    @Test public void acceptsJsonNoticeContainingHtml() throws IOException {
        SourceContent.requireContent("{\"notice\":\"<html>an example</html>\",\"sites\":[{}]}");
    }
}

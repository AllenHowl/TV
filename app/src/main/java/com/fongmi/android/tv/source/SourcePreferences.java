package com.fongmi.android.tv.source;

import com.github.catvod.utils.Prefers;

public final class SourcePreferences {
    private SourcePreferences() {}

    public static boolean followsVod() {
        return Prefers.getBoolean("source_follow_vod", false);
    }

    public static void followVod(boolean value) {
        Prefers.put("source_follow_vod", value);
    }
}

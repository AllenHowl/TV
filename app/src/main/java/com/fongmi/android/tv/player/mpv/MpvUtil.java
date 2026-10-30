package com.fongmi.android.tv.player.mpv;

import android.text.TextUtils;
import androidx.media3.common.Player;
import androidx.media3.common.util.Util;
import androidx.media3.mpvplayer.MpvPlayer;
import androidx.media3.mpvplayer.MpvPlayerConfig;
import androidx.media3.mpvplayer.MpvAndroidOptions;
import androidx.media3.mpvplayer.MpvSubtitleOptions;
import androidx.media3.ui.SubtitleView;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.track.LangUtil;
import com.fongmi.android.tv.player.util.PlayerHelper;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.PreloadSetting;
import com.fongmi.android.tv.setting.Setting;
import com.github.catvod.utils.Path;

/** Adapter for the public, pinned FongMi media API. */
public final class MpvUtil {
    public static boolean isAvailable() {
        try { return MpvPlayer.isAvailable(); }
        catch (Throwable error) { return false; }
    }

    public static MpvPlayer buildPlayer(int decode, Player.Listener listener) {
        MpvPlayer player = new MpvPlayer.Builder(App.get()).setDecode(decode).setConfig(buildConfig()).build();
        player.setPlayWhenReady(true);
        player.addListener(listener);
        return player;
    }

    public static void setSubtitleStyle(MpvPlayer player) {
        player.setSubtitleOptions(subtitles());
    }

    private static MpvSubtitleOptions subtitles() {
        float position = PlayerSetting.getSubtitlePosition();
        float size = PlayerSetting.getSubtitleTextSize();
        MpvSubtitleOptions.Builder builder = new MpvSubtitleOptions.Builder()
                .setPosition(position == 0 ? 100.0 : Util.constrainValue(100.0 - position * 100.0, 0.0, 150.0))
                .setScale(size == 0 ? 1.0 : Util.constrainValue(size / SubtitleView.DEFAULT_TEXT_SIZE_FRACTION, 0.5, 3.0));
        if (PlayerSetting.isCaption()) builder.setSystemCaptionStyle();
        return builder.build();
    }

    private static MpvPlayerConfig buildConfig() {
        String ua = Setting.getUa();
        MpvAndroidOptions android = new MpvAndroidOptions.Builder()
                .setShaderCacheDirectory(Path.mpvCache())
                .setAudioPassthroughEnabled(PlayerSetting.isAudioPassThrough())
                .setGpuNextEnabled(PlayerSetting.isMpvGpuNext())
                .setVulkanEnabled(PlayerSetting.isMpvVulkan()).build();
        MpvPlayerConfig.Builder builder = new MpvPlayerConfig.Builder()
                .setDefaultUserAgent(TextUtils.isEmpty(ua) ? PlayerHelper.getDefaultUa() : ua)
                .addConfigDirectory(Path.mpv()).addAndroidFontConfig(Path.mpv(), Path.mpvCache())
                .addAndroidDefaults(android)
                .addTlsCaFileFromAsset(App.get(), "cacert.pem", Path.files("cacert.pem"))
                .addAppOwnedStringOption("slang", LangUtil.getPreferredTextLanguageList())
                .addAndroidSubtitleOptions(App.get(), subtitles());
        if (PreloadSetting.isPreload()) {
            builder.addDiskCacheOptions(Path.mpvCache(), PreloadSetting.getPreloadTimeSeconds());
            builder.addAppOwnedStringOption("demuxer-max-bytes", Long.toString(PreloadSetting.getPreloadSizeMb() * 1024L * 1024L));
        }
        return builder.build();
    }
}

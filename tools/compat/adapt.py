"""Translate real public media APIs for the compatibility application."""
from pathlib import Path
import shutil


def adapt(out):
    app = out / 'app/src/main/java/com/fongmi/android/tv'
    def replace(path, old, new):
        p = app / path
        s = p.read_text()
        if old not in s: raise RuntimeError(f'Player adapter anchor missing: {path}: {old}')
        p.write_text(s.replace(old, new))
    guard_updates(out)
    shutil.copy(Path(__file__).parent / 'MpvUtil.java', app / 'player/mpv/MpvUtil.java')
    replace('player/exo/MediaSourceFactory.java', 'import static androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS;\n', '')
    # The public extractor now handles HDMV DTS unconditionally, with no opt-in flag.
    replace('player/exo/MediaSourceFactory.java', '.setTsExtractorFlags(FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS)', '')
    replace('player/media/MediaItemFactory.java', 'buildUpon(spec).setDecode(decode).build()', 'buildUpon(spec).build()')
    replace('player/exo/ExoUtil.java', 'import androidx.media3.common.AudioAttributes;', 'import androidx.media3.common.AudioAttributes;\nimport androidx.media3.common.C;\nimport androidx.media3.common.DolbyVisionOutputPolicy;\nimport androidx.media3.exoplayer.trackselection.DecodeTrackSelector;')
    replace('player/exo/ExoUtil.java', '.setTrackSelector(buildTrackSelector())', '.setTrackSelector(buildTrackSelector(decode))')
    replace('player/exo/ExoUtil.java', 'private static TrackSelector buildTrackSelector() {\n        DefaultTrackSelector trackSelector = new DefaultTrackSelector(App.get());', 'private static TrackSelector buildTrackSelector(int decode) {\n        DecodeTrackSelector trackSelector = new DecodeTrackSelector(App.get());\n        configureDecode(trackSelector, decode);')
    replace('player/exo/ExoUtil.java', '    private static int getRenderMode', '''    static void configureDecode(DecodeTrackSelector selector, int decode) {
        selector.setRendererDecodePreferences(
                PlayerSetting.isAudioPrefer() ? C.DECODE_SOFTWARE : decode,
                PlayerSetting.isVideoPrefer() ? C.DECODE_SOFTWARE : decode);
    }

    private static int getRenderMode''')
    replace('player/exo/ExoUtil.java', 'factory.setFfmpegAudioPrefer(audioPrefer).setFfmpegVideoPrefer(videoPrefer).setEnableDecoderFallback(true).setEnableDv7HevcFallback(PlayerSetting.isDv7HevcFallback()).setExtensionRendererMode(renderMode)', 'factory.setEnableDecoderFallback(true).setDolbyVisionOutputPolicy(PlayerSetting.isDv7HevcFallback() ? DolbyVisionOutputPolicy.AUTO : DolbyVisionOutputPolicy.ASSUME_SUPPORTED).setExtensionRendererMode(renderMode)')
    replace('player/exo/ExoPlayerEngine.java', '        this.decode = decode;\n        return true;', '''        this.decode = decode;
        if (player.getTrackSelector() instanceof androidx.media3.exoplayer.trackselection.DecodeTrackSelector selector) {
            ExoUtil.configureDecode(selector, decode);
            return true;
        }
        return false;''')
    replace('ui/activity/PlaybackActivity.java', '        getPlayerView().hideDebugView();', '        if (getPlayerView().isDebugViewVisible()) getPlayerView().toggleDebugView();')
    replace('ui/dialog/SubtitleDialog.java', '''        subtitleView.addPosition(0.005f);
        PlayerSetting.putSubtitlePosition(subtitleView.getPosition());''', '''        float position = Math.min(0.9f, PlayerSetting.getSubtitlePosition() + 0.005f);
        subtitleView.setBottomPosition(position);
        PlayerSetting.putSubtitlePosition(position);''')
    replace('ui/dialog/SubtitleDialog.java', '''        subtitleView.subPosition(0.005f);
        PlayerSetting.putSubtitlePosition(subtitleView.getPosition());''', '''        float position = Math.max(-0.5f, PlayerSetting.getSubtitlePosition() - 0.005f);
        subtitleView.setBottomPosition(position);
        PlayerSetting.putSubtitlePosition(position);''')
    for method, delta, boundary in [('addTextSize', '+', 'Math.min(0.15f,'), ('subTextSize', '-', 'Math.max(0.01f,')]:
        replace('ui/dialog/SubtitleDialog.java', f'''        subtitleView.{method}(0.002f);
        PlayerSetting.putSubtitleTextSize(subtitleView.getTextSize());''', f'''        float current = PlayerSetting.getSubtitleTextSize();
        if (current <= 0) current = SubtitleView.DEFAULT_TEXT_SIZE_FRACTION;
        float size = {boundary} current {delta} 0.002f);
        subtitleView.setFractionalTextSize(size);
        PlayerSetting.putSubtitleTextSize(size);''')


def guard_updates(out):
    # A co-installed compatibility package must not offer an unrelated upstream APK as its update.
    path = out / 'app/src/main/java/com/fongmi/android/tv/Updater.java'
    text = path.read_text()
    text = text.replace('    public Updater force() {', '    private boolean manualCompatibilityCheck;\n\n    public Updater force() {\n        manualCompatibilityCheck = true;')
    text = text.replace('    public void start(FragmentActivity activity) {', '''    public void start(FragmentActivity activity) {
        if (BuildConfig.APPLICATION_ID.endsWith(".sources")) {
            if (manualCompatibilityCheck) Notify.show(R.string.compat_update_notice);
            manualCompatibilityCheck = false;
            return;
        }''')
    path.write_text(text)
    messages = {
        'values': 'Update this compatibility build using APKs generated by this project.',
        'values-zh-rCN': '这是源管理兼容版，请使用本项目生成的安装包更新。',
        'values-zh-rTW': '這是來源管理相容版，請使用本專案產生的安裝包更新。',
    }
    for locale, message in messages.items():
        resource = out / 'app/src/main/res' / locale / 'sources.xml'
        resource.write_text(resource.read_text().replace('</resources>', f'    <string name="compat_update_notice">{message}</string>\n</resources>'))

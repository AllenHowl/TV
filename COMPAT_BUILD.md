# TV Sources 兼容版构建与交付

已生成电视版和手机版 ARM32 / ARM64 Debug APK，包名 `com.fongmi.android.tv.sources`，版本 `5.5.6-sources-compat.2`（版本代码 557），最低 Android 7.0（API 24）。两种界面使用同一包名，应按设备选择一个安装；可与原版 `com.fongmi.android.tv` 并存。沿用初版签名，可以覆盖更新初版 TV Sources。

本次修复源管理列表高度为 0、底部按钮被裁切的问题。弹窗提供确定的内容高度，并为列表保留视口；短屏或大字号时顶部控件可滚动。Robolectric 使用实际弹窗复现修复前列表高度为 0，修复后验证推荐目录、56 项完整目录及末项滚动、2 项直播、遥控器焦点、短横屏、竖屏和大字号。

安装包位于 `artifacts/TV-Sources-{tv,mobile}-{arm32,arm64}-debug.apk`，校验和与包信息见同目录 `SHA256SUMS`、`build-info.json`。

## 采用的路线

1. 当前 5.8.4 主工作区缺少配套播放器 AAR，直接构建失败。其代码使用的多个定制 API（包括 DecoderManager、LibassPlaybackSession 等）未在作者当前公开的媒体分支找到。
2. 已从作者公开的 `FongMi/media` 定制分支固定提交 `3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d`，实际构建出 18 个 AAR，包括 FFmpeg JNI 和 mpv。所需原生库的来源与校验信息保留在媒体源码中。
3. 为产出可安装的应用，选择 TV 5.5.6 提交 `4573249cd6679c6becca9957f6a011bf6c4a5afd` 作为兼容基线，将当前源管理功能移植到隔离的生成工程，并适配公开播放器的真实 API。字幕大小/位置、硬软解码、mpv 配置保留对应实现；HDMV DTS 使用公开解析器已有的默认支持。

原来的 5.8.4 源码与源管理修改均保留，没有回退主工作区。兼容版不是 5.8.4 的完整功能构建：该版本之后新增的双字幕、部分音视频特效、蓝光/ISO 高级导航、新版独立运行时模块管理等不在此基线中。Python 3.10、QuickJS、FFmpeg 与 mpv 已随包编译打包。未做真实设备上的播放验证。

兼容版使用独立的数据目录，原版数据不受影响，也不会自动迁移到新包。已关闭兼容版指向原版 APK 的自动更新路径；手动检查更新会提示使用本项目生成的安装包。

## 源管理

内置 56 条点播配置记录与 2 个直播原始地址；默认只展示推荐项，搜索覆盖完整目录。保留自定义源、线路选择、首次使用提示和稍后选择、点播/直播独立记忆、显式跟随点播、失败恢复、连续切换保护、按源隔离设置。第三方源的可用性和播放器实际解码效果仍需设备验证，目录不将条目标记为已验证可播放。

## 可复现命令

在当前云环境，已安装的 JDK 21、Android 命令行工具、uv 和原开发签名均保留。

```sh
cd /workspace/TV
JAVA_HOME=/workspace/.gradle/jdks/jetbrains_s_r_o_-21-amd64-linux.2 bash tools/build_compat.sh
```

该脚本固定媒体库与 TV 基线提交，安装必要 SDK/NDK/CMake、固定 Python 3.10.22 和 Android Python 依赖，构建 AAR，使用 `tools/prepare_compat.py` 生成 `/workspace/tooling/tv-compat-556`，构建两种 APK，运行工程单元测试并检查签名、包信息及原生库依赖。

生成工程使用 `.compat-generated` 标识，脚本不会覆盖没有标识的非空目录。源管理代码来自当前主工作区；播放器适配代码在 `tools/compat/`。Python 的 17 个已解析依赖固定在 `tools/compat/requirements.txt`。

## 验证结果

- 18 个公开媒体 AAR 构建成功。
- 电视版、手机版 ARM32 / ARM64 Debug APK 构建成功。
- 兼容工程的电视、手机版各 18 项 Gradle 检查通过，包括各 7 项实际弹窗布局回归；两种界面的测试用例有重叠。
- 对实际配置加载队列与目录校验执行的独立回归测试 13 项通过（与 Gradle 的 11 项有重叠，不应相加为 24 项独立测试）。
- 四份 APK 的 v2 签名、独立包名、版本、最低系统版本、对应 ARM 架构、内置目录和源管理类检查通过。
- 根目录的 26 个原生库依赖均可在 APK 或 Android 系统库中解析；ELF LOAD 段对齐均达到 16 KB。此检查不等同于在 16 KB 真机上验证。
- 环境没有连接 Android 设备，也没有 KVM 设备；安装、遥控器焦点、切源及实际播放未进行真机验证。

安装示例：

```sh
adb install -r artifacts/TV-Sources-tv-arm64-debug.apk
# 手机选择 TV-Sources-mobile-arm64-debug.apk。
```

首次打开在推荐源中选择点播或直播；电视首页、手机点播页菜单及设置页均可再次进入源管理。真实设备应检查分类、搜索、详情、播放、直播切台、断网恢复、重启保留选择，以及遥控器和触屏操作。

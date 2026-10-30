# 构建 TV Sources 兼容版

## 固定来源

- TV 基线：`4573249cd6679c6becca9957f6a011bf6c4a5afd`（5.5.6）。
- 公开媒体库：[FongMi/media](https://github.com/FongMi/media/tree/3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d)，固定提交 `3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d`。
- 从该媒体提交构建的 18 个 AAR 附在 Release 的 `TV-Sources-player-libs.zip`，整体与每个 AAR 的 SHA-256 都记录在 `tools/`。该媒体源码已包含对应的原生库、FFmpeg 头文件及来源清单，源码构建任务为对应 `lib-*` 模块的 `assembleRelease`。
- Android Python 的已解析版本固定在 `chaquo/requirements.txt`。

## 前置工具

需要 JDK 21、Android SDK Platform 37.0 和 Build Tools 37.0.0，以及 Python 3.10。Gradle wrapper 会按项目声明准备 JetBrains JDK 21 工具链。设置 `JAVA_HOME` 和 `ANDROID_HOME`；必要时设置 `TV_BUILD_PYTHON` 指向 Python 3.10 可执行文件。

## 构建

```sh
JAVA_HOME=/path/to/jdk21 ANDROID_HOME=/path/to/android-sdk TV_BUILD_PYTHON=/path/to/python3.10 bash tools/build_release.sh
```

脚本会校验或下载发布的播放器依赖包、准备本地开发签名（仅在没有 `local.properties` 时）、构建电视版和手机版 ARM64 Debug APK，并运行工程单元测试。已有本地配置会保留。

原始 Gradle 命令：

```sh
bash gradlew --no-configuration-cache --max-workers=2 \
  -PcompatBuildPython=/path/to/python3.10 \
  :app:assembleLeanbackArm64_v8aDebug \
  :app:assembleMobileArm64_v8aDebug \
  :app:testLeanbackArm64_v8aDebugUnitTest
```

APK 在 `app/build/outputs/apk/` 下对应的 `debug/` 目录。独立目录与加载队列测试可执行 `python3 tools/test_sources.py`；设置 `JAVA_HOME` 指向含 javac 的 JDK 21。测试脚本下载官方 Maven 依赖并校验发布的校验和。

本次发布使用云环境中的开发签名。自行生成的新签名与发布 APK 的签名不同，后续覆盖更新需要保持同一签名；本仓库不包含签名私钥或实际本地签名配置。

## 已完成与未完成的验证

- 18 个媒体 AAR 构建成功。
- 两份 ARM64 Debug APK 构建成功，APK v2 签名与 SHA-256 校验通过。
- Android 工程中的 11 项目录/内容单元测试通过；独立加载队列与目录测试共 13 项通过（两者有重叠）。
- 包名、版本、ARM64 架构、最低 SDK、内置目录、源管理类和原生库依赖闭包检查通过。
- 26 个根目录原生库的 ELF LOAD 段对齐达到 16 KB。
- 没有连接真机，安装、焦点、切源与实际播放未进行设备验证；16 KB 设备亦未实测。

用 `adb install -r <对应APK路径>` 安装。首次启动可选择点播与直播源，也可稍后从设置、电视首页或手机版菜单进入源管理。

# CleanYourTV launcher — Xiaomi TV 本地改版

在 [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) 的启动器（MIT）基础上，
为 **小米电视 MiTV-MFTR0（Android 14 / SDK 34，board freeguy）** 做的本地定制。

> **个人改版，不是官方版本**，也不打算合并回上游。原版请看
> [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv)。

- `launcher/` —— 改过的启动器源码（可直接 `gradle assembleRelease`）
- `icons/` —— 68 张 640×360 的 App 图标，逐张来源见 [icons/ICONS-NOTICE.md](icons/ICONS-NOTICE.md)
- [CHANGES.md](CHANGES.md) —— 与原版的逐文件差异
- [NOTICE.md](NOTICE.md) —— 上游署名、图标出处、商标声明

## 加了什么

| 功能 | 说明 |
|---|---|
| 天气行 | 时钟下面一行「河津 11° 阴」，Open-Meteo 免费接口，用经纬度取数 |
| 农历 | 日期下面一行农历，设置里可开关 |
| 返回键进时钟屏保 | Home 上按返回进时钟；再按任意键返回 |
| 闲置进屏保 | 关 / 5 / 15 / 30 分钟，设置里可选 |
| 自定义 App 图标 | 长按格子 →「换图标」；图库带缩略图，也能从电视本地文件夹选图，可一键恢复原图标 |
| 自定义 App 名称 | 长按格子 →「重命名」，用电视输入法改名 |
| 字体 | Inter 可变字体替代 Outfit |
| 应用库 | 手机版 App（无 Leanback 入口）可以加到首页 |

## 构建

JDK 17 + Android SDK Platform 34：

```bash
cd launcher
gradle --no-daemon assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

默认包名 `com.gathin.tvhome`。小米电视的系统桌面白名单要求包名是 `com.ts.launcher`
才允许接管 HOME，所以本机是这样构建的（只在编译期改 applicationId，源码里仍是原包名）：

```bash
gradle --no-daemon -PlauncherApplicationId=com.ts.launcher assembleRelease
```

## 安装（自担风险）

```bash
adb install -r launcher/app/build/outputs/apk/release/app-release.apk
adb shell cmd package set-home-activity com.ts.launcher/com.gathin.tvhome.MainActivity
```

主题、图标等运行期文件放在 `/sdcard/Android/data/<包名>/files/`，由启动器自带的
ContentProvider 写入（Android 11+ 上 adb shell 没有权限直接写该目录）。

## 许可

启动器代码沿用上游 MIT 许可（见 [LICENSE](LICENSE)）。图标不是本项目创作的，
来源与许可见 [icons/ICONS-NOTICE.md](icons/ICONS-NOTICE.md)，商标归各自所有者。

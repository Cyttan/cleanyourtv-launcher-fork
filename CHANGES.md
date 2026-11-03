# 与原版的差异（相对 `Gathin23/cleanyourtv` main @ 2026-11-03，启动器 3.3 / versionCode 12）

对比范围：`launcher/` 目录，排除 `build/`、`.gradle/`、`local.properties`。
原版 245 个文件 → 本改版 249 个文件：**17 个修改、5 个新增、1 个删除，+583 / −17 行**。

## 修改

| 文件 | +/- | 说明 |
|---|---:|---|
| `LauncherSettingsActivity.java` | +344 / −2 | 自定义图标（选图 / 文件夹浏览 / 缩略图）、重命名入口、屏保时长、农历开关 |
| `MainActivity.java` | +78 / −0 | 天气启动、图标目录变化重建、长按菜单加「换图标」「重命名」、重命名弹窗 |
| `AppAdapter.java` | +11 / −1 | 自定义图标与自定义名字优先于 banner / 应用名 |
| `Config.java` | +9 / −2 | 农历键、屏保分钟键及读取 |
| `ThemeFiles.java` | +20 / −0 | 放行 `icons/`、`icon-picker/` 的写入与删除 |
| `ThemeProvider.java` | +12 / −0 | 同上，整目录清空 |
| `AndroidManifest.xml` | +6 / −0 | `INTERNET`、`READ_MEDIA_IMAGES`（+ ≤SDK32 的 `READ_EXTERNAL_STORAGE`） |
| `activity_launcher_settings.xml` | +16 / −0 | 「屏保：开（N 分钟）」「自定义图标」两行 |
| `activity_main.xml` | +22 / −3 | 天气行 `weatherLine`；时钟/日期分割线 36dp、垂直居中 |
| `values/config.xml` | +20 / −4 | 见下方开关表 |
| `values/strings.xml` | +20 / −0 | 新文案 |
| `values-zh-rCN/strings.xml` | +20 / −0 | 新文案 |
| `font/outfit_{light,regular,medium,extralight}.xml` | 各 +1 / −1 | 字族指向 Inter |
| `values/fonts.xml` | +1 / −1 | `font_body` 指向 Inter |

## 新增

| 文件 | 大小 | 说明 |
|---|---:|---|
| `java/.../WeatherLine.java` | 6.8 KB | Open-Meteo 取数、缓存、天气码转中文 |
| `java/.../IconOverrides.java` | 4.2 KB | 扫 `icons/<包名>.<扩展名>`、解码、缓存、变更检测 |
| `java/.../AppNames.java` | 1.1 KB | 自定义 App 名称的存取 |
| `res/font/inter.ttf` | 856 KB | Inter 可变字体（wght 100–900） |
| `res/drawable/thumb_folder.xml` | 0.3 KB | 文件夹浏览里的占位块 |

## 删除

| 文件 | 大小 |
|---|---:|
| `res/font/outfit.ttf` | 108 KB |

## `values/config.xml` 开关变化

| 键 | 原版 | 本改版 |
|---|---|---|
| `lunar_date` | `false` | `true` |
| `app_library` | `false` | `true`（并预置 6 个手机版 App 到 `library_apps`） |
| `home_idle_clock_minutes` | `0` | `5` |
| `back_opens_clock` | `false` | `true` |
| `show_weather` / `weather_city` / `weather_lat` / `weather_lon` / `weather_refresh_minutes` | 无 | `true` / `河津` / `35.58` / `110.70` / `30` |

## 体积

| | 原版 3.3 | 本改版 |
|---|---:|---:|
| 源码树 | 1,473 KB | 2,260 KB（+787 KB，主要是字体） |
| release APK | 588,061 B（574 KB） | 1,002,833 B（979 KB，+405 KB） |

APK 增量明细：`res/SR.ttf`（Inter）+876,576 B、原 `res/fK.ttf`（Outfit）−110,884 B、
`classes.dex` +14,580 B、`resources.arsc` +5,812 B。

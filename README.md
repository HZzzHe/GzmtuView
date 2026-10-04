# GZMTU View

原生 Android（Kotlin + Jetpack Compose）的高校课表客户端，对接正方教务系统 9.0（`jwglxt`）。
课表 / 考试 / 成绩 / 体测 / 电费估算 / 桌面小部件，全套「液态玻璃」视觉。

> **免责声明**：本项目是个人练手作品，**与任何学校官方无关**，仅限学习与研究用途。
> 使用后请自行遵守所在学校教务系统的使用规定；请勿用于高频抓取或任何自动化选退课。

## 克隆后必须先改的两处

1. **教务系统地址** — `app/src/main/java/com/gzmtuview/data/net/ZFClient.kt`

   ```kotlin
   const val DEFAULT_BASE_URL = "https://your-academic-affairs-server.example.edu.cn"
   ```

   改成你自己学校的地址（只写协议 + 域名，`/jwglxt` 前缀由代码拼接）。
   仍是占位值时，任何一次教务请求都会直接抛「教务系统地址尚未配置……」，
   不会真的去连 example 域名、也不会让你等 5 秒连接超时看天书。

   > 只有正方 9.0（`jwglxt`）这套接口被验证过。其它版本 / 其它厂商的教务系统需要自行改
   > `ZFClient` 的请求路径与解析逻辑。

2. **署名与检查更新** — `app/src/main/java/com/gzmtuview/data/net/ProjectRepo.kt`

   ```kotlin
   const val SLUG = ""   // 形如 "your-name/gzmtu-view"
   ```

   留空 = 还没有公开仓库：设置页不出现「项目主页」一行，「检查更新」直接返回无信息，
   不会去打任何人的 GitHub API。填上自己的 `<owner>/<repo>` 即可启用。

   > 本项目的版权署名是 `HZzzHe`（见 `LICENSE` 末尾与 `NOTICE` 第 2 行）。
   > 你要基于它做自己的发行版，请把这两处的版权行换成你自己的名字，
   > 并按 Apache-2.0 的要求保留 `LICENSE` 与 `NOTICE` 全文。

   > 包名/命名空间已中性化为 `com.gzmtuview`（`app/build.gradle.kts` 两处 +
   > `app/src/main/java/com/gzmtuview/` 目录 + 全仓 `import` 字符串）。
   > 直接用它发布没问题；想换成自己的反域名，就按这三处一起改，只改其一会编译不过。

## 功能

- **登录与会话**：品牌横幅字标（浅/深两版随系统主题自动切换）、RSA 加密登录、持久化 Cookie 会话恢复、
  账号密码保存在系统 Keystore 加密存储中；**本机有课表缓存就直接进门看旧数据**，
  只有新装/清空数据才需要联网确认会话（启动等待有文字提示，不会只剩一个转圈）
- **每日同步芯片**（课表页右下角，与周次步进器同位同尺寸）：当天首次打开时在这里显示航海蓝
  「同步中」（图标旋转 + 底色忽明忽暗），课表 / 考试时间 / 成绩在**同一次作业**里取完，
  成功后芯片退场、步进器回来；数据有变化弹玻璃对话框点名（课表 / 考试时间 / 成绩）；
  失败变警示红（与考试页「回顾中」同色同材质）的「同步失败」，点开是说明对话框
  （展示本地缓存数据，联网后自动更新），可「再试一次」或「关闭」；
  会话失效变「重新登录」，点它回登录页并预填学号。
  一个状态只留芯片这一个提示出口，**没有顶部离线横幅**，也没有 WorkManager / 闹钟 / 前台服务等
  任何系统级后台任务——同步只由「用户当天主动打开 App」触发。
- **课表**：周视图 Canvas 网格（30 色课程分配）、全部课程视图、横竖屏自适应、学期切换、
  打开即自动定位当天所在周、周次步进器（点按切周 / 长按跳周，窄屏与底栏重叠时自动缩小、
  再不够则抬到底栏正上方）、缓存当天生效、手动刷新确认、离线直接看缓存、
  导出 WakeUp 兼容 CSV、节假日调休（Beta，默认关，需重启；离线内置国务院放假/补班表）
- **考试 / 成绩**：双 Tab、倒计时、学期切换、跨天旧缓存后台刷新；
  当天取数由上面那次每日同步一并完成，进考试页不再单独发请求（同步在跑时等它的结果）
- **工具箱**：体测成绩计算（国标 120 档评分器）、电费估算（Open-Meteo 真实温度 + 宿舍模型 + 双轴图表）
- **桌面小部件**：今日课程（动态进度，最多 2 门，每小时刷新，深浅色跟随，跟随节假日调休）
- **体验**：自定义壁纸（Beta，背板层 + 全局/仅课表双模式 + 压暗与模糊强度；玻璃底栏/顶栏会折射壁纸，
  底栏与周次步进器按壁纸下缘亮度自动翻深浅样式）、交互触感、周次/日期选择等若干开关。
  「功能」页用与工具箱子页同款的渐隐磨砂页头（正文滚进页头下方时才溶解），里面 8 项体验开关：
  课程定位辅助线、**课程胶囊**（关掉只去掉课程卡顶部那条装饰胶囊，文字位置与排版不动）、学生姓名、
  任课老师姓名、考试倒计时、周次步进器、交互触感、优先 https 通道
- **体积**：R8 裁剪 + 资源收缩 + 语言过滤后 release APK ≈ **1.8 MB**

## 构建

环境：JDK 17+（21 亦可）、Android SDK 37（`compileSdk 37`，`minSdk 26`，`targetSdk 36`）、
Gradle 9.7.1（用自带 wrapper 即可）、Kotlin 2.4.10、Compose BOM 2026.08.00。

```bash
# SDK 位置二选一：写 local.properties（sdk.dir=...）或导出 ANDROID_HOME
./gradlew assembleDebug      # 调试包
./gradlew assembleRelease    # R8 混淆 + 资源收缩
```

release 默认走 **debug 签名**，所以没有 keystore 也能直接产出可安装的 APK。
要正式签名：自备 `release/keystore/gmuview-release.jks`，并在**用户级** `gradle.properties`
里提供 `GMUVIEW_RELEASE_STORE_PASSWORD`（`GMUVIEW_RELEASE_KEY_ALIAS` 默认 `gmuview`）、
`GMUVIEW_RELEASE_KEY_PASSWORD`；两者齐备时 release 自动切换到正式签名。
密钥与密码都不入仓库，`local.properties` / `release/` 已在 `.gitignore` 里。

ABI 只保留 `arm64-v8a`（真机目标）。需要其它架构就在
`app/build.gradle.kts` 的 `ndk { abiFilters ... }` 里加。

## 代码结构

```
app/src/main/java/com/gzmtuview/
├── MainActivity.kt / GmuApplication.kt   # 入口；Application 持有长生命周期的 syncScope
├── data/
│   ├── model/      # 课表/考试/成绩/学期/体测/电费/调休 等纯数据
│   ├── net/        # ZFClient（正方 9.0 协议）、ProjectRepo、WeatherService、UpdateService
│   ├── cache/      # ScheduleCache：课表/考试/成绩磁盘缓存，缓存日期即「今天是否已同步」标记
│   ├── color/ pref/ export/
├── ui/
│   ├── sync/DailySync.kt   # 当天一次、四份数据合并取完的同步作业 + 芯片状态机
│   ├── schedule/ exams/ tools/ settings/ feature/ login/
│   ├── glass/    # 液态玻璃组件（部分派生自 AndroidLiquidGlass，见下）
│   └── wallpaper/  # 自定义壁纸背板与亮度自适应
└── widget/           # 桌面小部件 Provider 与推送
```

## 图标与横幅

`UI/icon/*.svg` 是设计母版（纯 path、无 live text，不依赖字体），产物 PNG 全部由根目录脚本生成：

```bash
pip install pillow numpy
python gen_icons.py
```

脚本用 Edge headless 把 SVG 光栅化到 2048px（找不到 Edge 时设 `GMUVIEW_EDGE` 指向 `msedge.exe`），
再在**预乘 alpha** 空间做 **BOX 面积缩放**，最后反预乘并按母版色域钳制。别改回 LANCZOS 之类带负瓣的核——
母版只有 `#3a8dcc` / `#b8e2f5` / `#fff` 三色且恒满足 `R<=G<=B`，负瓣过冲会让 G 通道在蓝↔白边缘
最先穿到 0，反预乘后得到「蓝 + 无绿」= 品红/紫，即字标上的紫边（实测旧产物有 12.9% 像素越界，
换 BOX 后为 0；验收判据 `R > G+8` 的像素数须为 0）。
登录页横幅按版式限宽出 `640×282`，浅色版在光栅化时把淡蓝手写体压成品牌蓝
（`#b8e2f5` 在浅色页面基色上只有 1.16:1，等于看不见），**设计母版原样不动**。
`res/` 下的 PNG 都是产物，勿手改。

## 隐私

- 账号密码只存本机（`EncryptedSharedPreferences` + 系统 Keystore），日志与崩溃信息不上传。
- 教务数据缓存只在本机，**当天有效**，当日再次打开不重复访问教务系统。
- 退出登录清凭据与缓存；「重新登录」只把你送回登录页，不清凭据、不清缓存。
- 除你自己学校教务系统与 Open-Meteo（天气）之外，不发任何第三方网络请求；本仓库不含任何真实接口地址。

## 许可与第三方

- 本仓库源码采用 **Apache License 2.0**，见 `LICENSE` 与 `NOTICE`。
- `ui/glass/` 下若干文件派生自 [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)
  （Copyright 2025 Kyant，Apache-2.0），每个文件头部都标明了来源；许可文本副本在
  `third_party/`。运行时依赖 `io.github.kyant0:backdrop:2.0.1`、`io.github.kyant0:shapes:1.2.1`。
- 离线天气数据来自 [Open-Meteo](https://open-meteo.com/)（CC-BY 4.0），署名同时写在
  `app/src/main/assets/weather_pack.json` 内。
- 节假日调休表为国务院办公厅公开发布的放假安排（2025–2027），非学校数据。

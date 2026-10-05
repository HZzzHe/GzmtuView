# 安全政策

先划清边界：本政策只覆盖**本仓库源码与由它构建出的 App**。
你所在学校教务系统的漏洞请报给学校信息化部门，发到本仓库既修不了，也可能让漏洞多一个人知道。

## 支持版本

| 版本 | 是否支持 |
| --- | --- |
| `main` 最新提交 | ✅ |
| 最新 Release（当前 `v0.1.1`） | ✅ |
| 更早的 Release | ❌ —— 请先把改动带到 `main` 上复现 |

单人维护的项目只修 `main`，不对历史版本回溯打补丁。

## 如何报告

- 走仓库的 **Security → Report a vulnerability**（私有漏洞报告）。
  **不要**开公开 issue，也不要在 issue / PR / 评论里贴复现细节、日志或截图 ——
  这些内容公开可见，而本 App 经手的是你的学号、课表与成绩。
- 若私有漏洞报告未开启，先开一个正文只写「存在安全问题，请联系我」的 issue，
  剩下的对话私下进行；确认修复方案后再把可公开的部分整理成 issue 或 advisory。
- 报告里请写清：App 版本与 commit、机型与系统版本、受影响的功能、复现步骤、
  你认为的影响范围；有补丁思路更好，没有也不影响受理。
- **日志与截图先脱敏**：学号、姓名、课程名、成绩、`JSESSIONID` / Cookie、教务系统域名
  一律换成占位串。带真实学籍信息的附件请直接删掉。
- 回复预期：7 天内确认收到，30 天内给出评估结论。在修复落地前我不会公开你的报告。

## 我们欢迎上报的（非穷尽）

- 学号 / 密码 / 会话 Cookie 明文落盘、进日志，或被发往 README「隐私」一节所列之外的任何地址
- 凭据加密存储（`EncryptedSharedPreferences` + 系统 Keystore）可被绕过
- 导出与分享（`FileProvider`）把接收方本不该拿到的文件递了出去
- 「检查更新」通道可被诱导让用户安装并非发布者提供的安装包
- 构建与发布链可被污染：CI 产物被替换、`gradle-wrapper.jar` 被换、依赖被劫持
- 由界面输入的教务系统地址（`baseUrlOverride`）导致凭据流向非预期主机

## 通常不作为安全问题处理

- 教务系统自身的漏洞、数据泄露或访问控制缺失
- 需要先物理控制已解锁设备 / 已 root 才能读本机缓存 —— 这属于平台沙箱边界
- 高频抓取、绕过学校限流或验证码 —— 这是使用规定问题，见 README 的免责声明
- 第三方库的上游已知漏洞：直接提 PR 升版本号比开报告更快

## 已知的设计取舍

这些是有意为之或已记录的现状，按「讨论」处理而不是按漏洞报告处理；如果你有更好的方案，欢迎开功能类 issue：

1. **https ↔ http 会在连接失败时自动降级重试一次**。`ZFClient.request()` 遇到
   `ConnectException` / 连接超时会切到相反协议再试（`switchScheme()`），一次会话内只降一次。
   原因是不少学校系统只有 http 或者 https 证书不可用。后果是凭据**可能**在明文信道上传输。
   「功能」页的「优先使用 https 通道」只决定首次用哪个协议起连（关掉它就直接走 http），
   **拦不住**连接失败后的那一次反向切换 —— 把它改成「降级前先问用户」是可讨论的，欢迎开功能 issue。
2. **课表 / 考试 / 成绩缓存是明文 JSON**，放在 App 私有目录 `files/schedule_cache/`，
   靠 Android 沙箱隔离；`android:allowBackup` 未显式关闭。
   凭据本身是加密的，而 Keystore 主密钥不可导出，所以备份包里的凭据在别的设备上解不开。
3. **`FileProvider` 的 `files-path` 授权根是整个 `files/` 目录**（`cache-path` 是 `exports/`）。
   当前实现只对 `exports/` 下的 CSV 生成 URI，且权限是单次临时授予。收窄根目录要动
   `app/src/main/res/xml/file_paths.xml`，属于可做未做。
4. **更新检查不下载安装包**：`UpdateService` 只读 `api.github.com` 的最新 Release，
   弹对话框后用 `ACTION_VIEW` 把 Release 页交给浏览器，App 内没有 `REQUEST_INSTALL_PACKAGES` 权限。
   且 `ProjectRepo.SLUG` 出厂留空 → 这条请求默认一次都不会发。
5. **只验证过正方 9.0（`jwglxt`）**。换成别的教务系统后出现的解析错误、登录失败是兼容性问题，
   不是漏洞，请走「问题反馈」表单。

## 供应链

- Gradle wrapper 的 `distributionUrl` 指向腾讯镜像（国内下载考虑）。CI 第一步用
  `gradle/actions/wrapper-validation` 校验仓库里的 `gradle-wrapper.jar` 是否与 Gradle 官方发布物
  一致 —— 有人换掉 wrapper jar（那就等于改写下载逻辑）的 PR 会在这一步失败。
  需要说清楚的是：这一步**不校验**镜像上那个 `gradle-9.7.1-bin.zip` 本身，那条链目前只靠
  https 与镜像的公开性兜着。要收紧就在 `gradle/wrapper/gradle-wrapper.properties` 里补
  `distributionSha256Sum`，Gradle 会核对下载到的发行包；替换镜像 URL 的人请同时核对校验和。
- 依赖只来自 `google()` 与 `mavenCentral()`（见 `settings.gradle.kts`），
  没有私有仓库、没有随仓库提交的 `.aar`/`.jar`。
- **CI 只构建、不发布**：workflow 里没有签名密钥，也没有上传 Release 的步骤。
  `app/build.gradle.kts` 在没有 keystore 时让 release 回退到 debug 签名（为了「克隆完就能
  `assembleRelease`」），所以 CI 产物只能拿来试装，**不能当作正式发布物分发**。

## 数据面（便于你判断影响范围）

- 无遥测、无崩溃上报、无广告 / 统计 SDK。
- 网络出口只有三个：你自己配置的教务系统、Open-Meteo（「电费估算」取日均气温，
  只发内置常量坐标，不读设备定位）、`api.github.com`（仅当 `ProjectRepo.SLUG` 被填上）。
  详见 README 的「隐私」一节。

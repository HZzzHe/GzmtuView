# 安全政策

本政策适用于本仓库源码及由此源码构建的应用。官方发布的安装包由私有工作树构建，
包名、教务地址与签名配置不同，功能与安全机制一致。所在学校教务系统的漏洞请联系学校信息化部门；
提交至本仓库无法修复，也可能扩大知悉范围。

## 支持版本

| 版本 | 是否支持 |
| --- | --- |
| `main` 最新提交 | ✅ |
| 最新 Release v0.1.5 | ✅ |
| 更早的 Release | ❌ 请在 `main` 最新提交上复现 |

项目由个人维护，仅修复 `main` 分支，不对历史版本回溯补丁。

## 如何报告

- 请通过仓库的 **Security → Report a vulnerability** 提交私有漏洞报告。
  请勿创建公开 issue，也请勿在 issue、PR 或评论中披露复现细节、日志或截图。
  上述内容公开可见，而本应用处理学号、课表与成绩。
- 若私有漏洞报告未启用，请创建一个正文仅包含「存在安全问题，请联系我」的 issue，
  后续沟通转为私有，确认修复方案后再整理可公开部分。
- 报告请包含 App 版本与 commit、机型与系统版本、受影响功能、复现步骤与影响范围。
  如有修复思路请一并说明，不影响受理。
- 日志与截图请先脱敏。学号、姓名、课程名、成绩、`JSESSIONID`、Cookie 与教务系统域名
  请替换为占位字符串，包含真实学籍信息的附件请删除。
- 处理时限：7 天内确认收到，30 天内给出评估结论。修复完成前不会公开报告内容。

## 受理范围

- 学号、密码或会话 Cookie 明文落盘、进入日志，或被发送至 README「隐私」一节所列之外的地址
- 凭据加密存储（`EncryptedSharedPreferences` 与系统 Keystore）可被绕过
- 导出与分享（`FileProvider`）向接收方提供了无权访问的文件
- 检查更新通道可被诱导安装非发布者提供的安装包
- 构建与发布链可被污染：CI 产物被替换、`gradle-wrapper.jar` 被替换、依赖被劫持
- 由界面输入的教务系统地址（`baseUrlOverride`）导致凭据流向非预期主机

## 通常不视为安全问题

- 教务系统自身的漏洞、数据泄露或访问控制缺失
- 需要物理接触已解锁设备或已 root 设备才能读取本机缓存，属于平台沙箱边界
- 高频抓取、绕过学校限流或验证码，属于使用规定问题，见 README 免责声明
- 第三方库的上游已知漏洞，提交依赖升级 PR 更为高效

## 已知的设计取舍

以下为有意设计或已记录的现状，按讨论事项处理，不作为漏洞。如有改进方案，欢迎提交功能 issue。

1. **协议只允许 http → https 单向升级**。`ZFClient.request()` 仅在当前为 http 且
   连接失败或被系统明文策略拦截时升级到 https 重试一次；https 失败不会回退明文，
   而是直接报错。「优先使用 https 通道」只决定首次连接协议；关闭该开关时，
   首次请求仍可能以 http 发出，需用户自行判断所在网络环境。
2. **课表、考试与成绩缓存为明文 JSON**，保存在 App 私有目录 `files/schedule_cache/`，
   依赖 Android 沙箱隔离；`android:allowBackup` 未显式关闭。凭据使用加密存储，
   Keystore 主密钥不可导出，备份中的凭据无法在其他设备解密。
3. **`FileProvider` 的 `files-path` 授权根为整个 `files/` 目录**，`cache-path` 包含
   `exports/` 与 `update/`。当前仅对 `exports/` 下的 CSV 与 `update/` 下的安装包生成 URI，
   且为单次临时授权。收窄根目录需修改 `app/src/main/res/xml/file_paths.xml`，尚未实施。
4. **应用内更新会下载并安装 APK**。`UpdateService` 读取 `api.github.com` 的最新 Release，
   从 Release 资产下载 APK，校验包名与 versionCode 后调起系统安装器；
   应用声明 `REQUEST_INSTALL_PACKAGES` 权限。`ProjectRepo.SLUG` 出厂留空，默认不发起请求。
   安装前校验包名与版本号，不校验签名证书，安全性依赖仓库账户与 Release 资产的完整性。
5. **仅验证正方 9.0（`jwglxt`）**。更换教务系统后出现的解析错误与登录失败属于兼容性问题，
   请通过问题反馈表单提交。

## 供应链

- Gradle wrapper 的 `distributionUrl` 指向腾讯镜像，以改善国内下载速度。CI 第一步使用
  `gradle/actions/wrapper-validation` 校验仓库内 `gradle-wrapper.jar` 与 Gradle 官方发布物一致，
  替换 wrapper jar 等同于替换下载逻辑，会在该步骤失败。该步骤不校验镜像中的
  `gradle-9.7.1-bin.zip`，目前该链路依赖 https 与镜像可信度。如需收紧，可在
  `gradle/wrapper/gradle-wrapper.properties` 中添加 `distributionSha256Sum`，由 Gradle
  校验下载的发行包；替换镜像 URL 时请同步核对校验和。
- 依赖仅来自 `google()` 与 `mavenCentral()`，见 `settings.gradle.kts`。
  项目不使用私有仓库，也不提交 `.aar` 或 `.jar` 文件。
- **CI 仅构建，不发布**。workflow 不包含签名密钥，也不包含上传 Release 的步骤。
  `app/build.gradle.kts` 在未配置 keystore 时让 release 回退到 debug 签名，
  以便克隆后直接执行 `assembleRelease`。CI 产物仅用于试装，不得作为正式发布产物分发。

## 数据面

- 无遥测、无崩溃上报、无广告与统计 SDK。
- 网络出口仅三个：用户配置的教务系统、Open-Meteo（「电费估算」获取日均气温，
  仅发送内置常量坐标，不读取设备定位）、`api.github.com` 与 GitHub 资产地址
  （仅当 `ProjectRepo.SLUG` 已填写，用户确认下载后才获取 APK）。详见 README「隐私」一节。

# GMU View 原生版混淆规则
#
# 说明：Manifest 里的组件（Activity / Application / FileProvider / 小部件 Receiver）
# 由 AGP 自动注入 keep 规则，无需在此重复声明；本文件只记录「不写就会出错」的例外。

# androidx.security-crypto（EncryptedSharedPreferences）内置的 Tink 引用了
# errorprone 的编译期注解，该 jar 不进运行时包，引用永远不可能解析 → 忽略。
-dontwarn com.google.errorprone.annotations.**

# CrashGuard 落盘的 crash_last.txt 是本应用在无 adb 环境下唯一的排障通道，
# 因此必须保留行号：类名会被混淆，靠「行号 + 当次 build 的 mapping.txt」还原。
# mapping.txt 每次 release 都要留档（build/outputs/mapping/release/，不入库）。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

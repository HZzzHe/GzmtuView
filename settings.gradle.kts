pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// 标准仓库即可构建。若在国内网络下拉取依赖慢，可在用户级 Gradle 初始化脚本里
// 前置华为云/阿里云镜像（此处的标准仓库会作为兜底保留）。
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "GzmtuView"
include(":app")

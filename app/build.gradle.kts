plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.gzmtuview"
    // compileSdk 37：最新 androidx/okhttp 要求；SDK 已装 android-37.0
    compileSdk = 37

    defaultConfig {
        applicationId = "com.gzmtuview"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.1.3"
        // 按用户偏好仅构建 arm64-v8a（真机目标）
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    // 正式签名：密钥与密码都不入库。自备 release/keystore/gmuview-release.jks，
    // 并在用户级 gradle.properties 里提供 GMUVIEW_RELEASE_STORE_PASSWORD（可选
    // GMUVIEW_RELEASE_KEY_ALIAS，默认 gmuview）、GMUVIEW_RELEASE_KEY_PASSWORD。
    // 两者缺任一项 → release 回退 debug 签名，保证克隆后 assembleRelease 直接可跑。
    val releaseKeystore = file("../release/keystore/gmuview-release.jks")
    val hasReleaseSigning = releaseKeystore.exists() &&
        providers.gradleProperty("GMUVIEW_RELEASE_STORE_PASSWORD").orNull != null
    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = releaseKeystore
                storePassword = providers.gradleProperty("GMUVIEW_RELEASE_STORE_PASSWORD").orNull
                keyAlias = providers.gradleProperty("GMUVIEW_RELEASE_KEY_ALIAS").orNull ?: "gmuview"
                keyPassword = providers.gradleProperty("GMUVIEW_RELEASE_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            // R8 裁剪 + 资源收缩：未裁剪时三个 dex 合计约 50MB，
            // dexopt 生成的 vdex 等比跟着膨胀（实测 49MB），是平板存储占用的主项。
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    androidResources {
        // 仅保留中英文：AndroidX / Material 的 AAR 携带上百种语言资源
        localeFilters += listOf("zh", "en")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    // Compose 基础（BOM 统一版本）
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // 生命周期
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")

    // 网络（OkHttp + Cookie 会话）
    implementation("com.squareup.okhttp3:okhttp:5.5.0")

    // 偏好存储（DataStore）与凭据加密存储
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.security:security-crypto:1.1.0")

    // 液态玻璃效果库（调研验证：Kotlin 2.4.10 编译的 KMP 库，minSdk 21）
    implementation("io.github.kyant0:backdrop:2.0.1")
    // backdrop 以 implementation 发布 shapes（Capsule 等形状），需显式引入
    implementation("io.github.kyant0:shapes:1.2.1")

    // 测试
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

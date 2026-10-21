plugins {
    id("com.android.application")
    // AGP 9 内置 Kotlin（不要再加 kotlin.android 插件）
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ichichuang.cinema"
    // 必须是 37：2026 年的 AndroidX（Compose 1.12.1 / navigation 2.10.2 / core-ktx 1.19.1）
    // 在 AAR 元数据里强制要求消费方 compileSdk >= 37，否则 checkAarMetadata 直接失败。
    // 注意：compileSdk 只影响"能用哪些新 API"，不影响装机兼容性（那是 minSdk 的事）。
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ichichuang.cinema"
        // 24（不是 23）：2026 年的 androidx.navigation 要求 minSdk >= 24。
        // 实测电视能装 sdk23 的包 → 说明系统 API >= 23；2026 款雷鸟灵控系统实际是 Android 9+，
        // 所以 24 是安全的。万一电视拒装（说明系统真的是 API 23），退路有两条：
        //   1) 去掉 navigation 依赖，用手写导航状态机（其余库都支持 23）
        //   2) tools:overrideLibrary 强压（不推荐，可能在 API 23 上运行期崩）
        minSdk = 24
        // 故意压低：老 TV 系统对高 targetSdk 会加限制（如实测被拦）
        targetSdk = 28
        // ⚠️ 每次发布会话都要 +1 且 versionName 跟着升 —— 在线更新靠 versionCode 判新旧，
        //    忘了加就会出现"下载了新版却提示已是最新"的怪事。
        versionCode = 16
        versionName = "0.9.6"

        // 只出 32 位，和 FFALCON 65S78A（armeabi-v7a）匹配
        ndk {
            abiFilters += listOf("armeabi-v7a")
        }
    }

    signingConfigs {
        create("releaseKey") {
            storeFile = rootProject.file("keystore/release.jks")
            storeType = "PKCS12"
            storePassword = "cinema2026tv"
            keyAlias = "cinema"
            keyPassword = "cinema2026tv"
        }
    }

    buildTypes {
        release {
            // 阶段 0 先不混淆：先保证"一次编译成功 + 装到电视能跑"，
            // 等界面稳定后再开 isMinifyEnabled 压缩体积
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("releaseKey")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        // ExpiredTargetSdkVersion 是 Google Play 的上架规则（要求 targetSdk >= 33），
        // 与"侧载到电视"无关。本项目刻意压低 targetSdk（老 TV 固件对高 targetSdk 会加限制），
        // 所以只关这一条；其它 lint 错误仍然会让 release 构建失败，不放过真问题。
        disable += "ExpiredTargetSdkVersion"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // ── Compose（BOM 统一版本）──────────────────────────────
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    // 官方 TV 设计系统（阶段 2 用它的焦点感知卡片/列表）
    implementation("androidx.tv:tv-material:1.1.0")

    // ── 基础 ────────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")

    // ── 架构：导航 + DI ─────────────────────────────────────
    // navigation-compose 会带入 lifecycle-viewmodel-compose（koinViewModel 需要）
    implementation("androidx.navigation:navigation-compose:2.10.2")

    // Koin 4.2 起 koin-androidx-compose 只是个 8KB 的垫片，不再带 android 部分，
    // 所以必须显式引 koin-android —— 否则 androidContext / viewModel{} DSL 找不到。
    implementation("io.insert-koin:koin-android:4.2.2")
    implementation("io.insert-koin:koin-androidx-compose:4.2.2")

    // ── 网络 + JSON（阶段 1：苹果CMS 接口）──────────────────
    // 只引 OkHttp，不用 Retrofit：接口地址是运行时由用户填的、还要自己处理
    // GBK 编码和防盗链 UA，Retrofit 的静态 baseUrl + 转换器抽象在这里只会挡路。
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // ── 海报图（Coil3）──────────────────────────────────────
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")

    // ── 设置存储（接口地址）────────────────────────────────
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // ── 二维码（设置页「扫码配置」）────────────────────────────
    // zxing core 是纯 Java，无原生库 → 32 位电视零 ABI 风险，体积也只有几十 KB。
    // 刻意不用 zxing-android-embedded：那是给手机相机扫码用的，我们只需要"画出码"。
    implementation("com.google.zxing:core:3.5.3")

    // ── 播放器（阶段 2）────────────────────────────────────
    // media3 = ExoPlayer 的现名。纯 Java/Kotlin，解码走系统 MediaCodec，
    // 不引入任何原生库 → 对 32 位电视没有 ABI 风险。
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    // HLS(m3u8) 在单独模块里 —— 国内片源绝大多数是 m3u8，必须显式加
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    // 官方 Compose 播放画面（PlayerSurface）+ 状态 Composable
    implementation("androidx.media3:media3-ui-compose:1.11.1")
}

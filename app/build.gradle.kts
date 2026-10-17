plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ichichuang.cinema"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ichichuang.cinema"
        // 电视实测：能安装 sdk23 的包，留余量取 23
        minSdk = 23
        // 故意压低：老 TV 系统对高 targetSdk 会加限制（如实测被拦）
        targetSdk = 28
        versionCode = 1
        versionName = "0.1.0"

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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.tv:tv-material:1.1.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
}

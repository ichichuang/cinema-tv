import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

// A probe APK is only built together with its instrumentation APK. No install/distribution roots.
val playbackRoomProbe = providers.gradleProperty("cinema.playbackRoomProbe")
    .map { it.toBooleanStrict() }.getOrElse(false)
if (playbackRoomProbe) {
    val pair = setOf(":app:assembleDebug", ":app:assembleDebugAndroidTest")
    if (gradle.startParameter.taskNames.toSet() != pair) {
        throw GradleException("Room 播放探针仅允许完整 Debug / DebugAndroidTest 测试配对；禁止 Release、bundle、发布、签名或安装任务。")
    }
    gradle.taskGraph.whenReady {
        if (allTasks.any { it.name.contains("release", ignoreCase = true) ||
                it.name.startsWith("publish", ignoreCase = true) || it.name == "bundleDebug" }) {
            throw GradleException("Room 播放探针任务图不得包含 Release、bundle、发布或生产签名任务。")
        }
    }
}

val storageTestArm64 = providers.gradleProperty("cinema.storageTestArm64")
    .map { it.toBooleanStrict() }.getOrElse(false)

// Browse restoration shares the verified R06/R07 test authority, never a separate credential.
val browseRestoreProbe = providers.gradleProperty("cinema.browseRestoreProbe")
    .map { it.toBooleanStrict() }.getOrElse(false)
if (browseRestoreProbe) {
    val pair = setOf(":app:assembleDebug", ":app:assembleDebugAndroidTest")
    if (gradle.startParameter.taskNames.toSet() != pair || !playbackRoomProbe || !storageTestArm64) {
        throw GradleException("浏览恢复探针仅允许完整 Debug / DebugAndroidTest 配对且必须启用已有播放测试授权和 ARM64 存储测试；禁止安装、Release、bundle、发布或签名任务。")
    }
    gradle.taskGraph.whenReady {
        if (allTasks.any { it.name.contains("release", ignoreCase = true) ||
                it.name.startsWith("publish", ignoreCase = true) || it.name.startsWith("install", ignoreCase = true) ||
                it.name == "bundleDebug" }) {
            throw GradleException("浏览恢复探针任务图禁止安装、Release、bundle、发布或生产签名任务。")
        }
    }
}

// This opt-in only assembles a disposable instrumentation APK pair; it never installs.
if (storageTestArm64) {
    val allowedTasks = setOf(":app:assembleDebug", ":app:assembleDebugAndroidTest")
    if (gradle.startParameter.taskNames.isEmpty() || gradle.startParameter.taskNames.any { it !in allowedTasks }) {
        throw GradleException("ARM64 存储测试仅允许 :app:assembleDebug 和 :app:assembleDebugAndroidTest；禁止 Release、bundle、发布或安装任务。")
    }
    gradle.taskGraph.whenReady {
        if (allTasks.any { task ->
                task.name.contains("release", ignoreCase = true) ||
                    task.name.startsWith("publish", ignoreCase = true) || task.name == "bundleDebug"
            }) {
            throw GradleException("ARM64 存储测试任务图不得包含 Release、bundle 或发布任务。")
        }
    }
}

// Removing this freeze requires review of the signing migration and TV upgrade evidence.
// A command-line property alone cannot re-enable signing during the incident.
val releaseSigningFrozen = true
val releaseSigningRequested = providers.gradleProperty("cinema.enableReleaseSigning")
    .map { it.toBooleanStrict() }.getOrElse(false)
if (releaseSigningRequested && releaseSigningFrozen) {
    throw GradleException("Release signing is frozen. See SECURITY.md and docs/RELEASE.md.")
}

val releaseProperties = Properties()
if (releaseSigningRequested) {
    val propertiesFile = rootProject.file("signing.properties")
    if (propertiesFile.isFile) propertiesFile.inputStream().use { releaseProperties.load(it) }
}
fun signingValue(environment: String, property: String): String =
    (providers.environmentVariable(environment).orNull ?: releaseProperties.getProperty(property))
        ?.takeIf { it.isNotBlank() }
        ?: throw GradleException("Missing signing setting: $environment / $property")

android {
    namespace = "com.ichichuang.cinema"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ichichuang.cinema"
        minSdk = 24
        // Preserve the current device compatibility contract during incident remediation.
        targetSdk = 28
        versionCode = 18
        versionName = "0.9.8"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "PLAYBACK_ROOM_PROBE", "false")
        buildConfigField("boolean", "BROWSE_RESTORE_PROBE", "false")
    }

    signingConfigs {
        if (releaseSigningRequested) {
            create("externalRelease") {
                val keyFile = rootProject.file(signingValue("SIGNING_STORE_FILE", "storeFile")).canonicalFile
                if (!keyFile.isFile || keyFile.toPath().startsWith(rootProject.rootDir.canonicalFile.toPath())) {
                    throw GradleException("The signing keystore must be an existing file outside the repository.")
                }
                storeFile = keyFile
                storeType = providers.environmentVariable("SIGNING_STORE_TYPE").orNull
                    ?: releaseProperties.getProperty("storeType", "PKCS12")
                storePassword = signingValue("SIGNING_STORE_PASSWORD", "storePassword")
                keyAlias = signingValue("SIGNING_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("SIGNING_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "PLAYBACK_ROOM_PROBE", playbackRoomProbe.toString())
            buildConfigField("boolean", "BROWSE_RESTORE_PROBE", browseRestoreProbe.toString())
            ndk { abiFilters += if (storageTestArm64) "arm64-v8a" else "armeabi-v7a" }
            // Contributor builds cannot replace the installed production application.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            ndk { abiFilters += "armeabi-v7a" }
            isMinifyEnabled = false
            // No fallback to the debug key or any repository-owned signing key.
            signingConfig = signingConfigs.findByName("externalRelease")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    lint { disable += "ExpiredTargetSdkVersion" }
    // Retain dependency license texts when several dependencies contribute the same name.
    packaging { resources { merges += listOf("/META-INF/AL2.0", "/META-INF/LGPL2.1") } }
}

room { schemaDirectory("$projectDir/schemas") }

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.room:room-runtime:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.tv:tv-material:1.1.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("io.insert-koin:koin-android:4.2.2")
    implementation("io.insert-koin:koin-androidx-compose:4.2.2")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-ui-compose:1.11.1")
}

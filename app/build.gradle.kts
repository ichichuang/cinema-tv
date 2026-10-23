import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("androidx.room")
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
        ndk { abiFilters += listOf("armeabi-v7a") }
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
            // Contributor builds cannot replace the installed production application.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
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
    buildFeatures { compose = true }
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

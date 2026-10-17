// 极简影院 · Android TV
// 只做一件事：让人用遥控器舒服地挑电影、看电影。
//
// ⚠️ AGP 9 的重大变化：不再声明 org.jetbrains.kotlin.android。
//    AGP 9 内置 Kotlin 支持，自带 KGP 2.2.10。但 2026 年的库（Coil 3.6.3 等）
//    是用 Kotlin 2.4 编译的，2.2 的编译器读不了它们的元数据 →
//    按 AGP 官方文档的通道，把 KGP 显式抬到 2.4.20（buildscript classpath）。
//    Compose 编译器插件版本必须和 KGP 完全一致。
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}

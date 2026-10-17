// 极简影院 · Android TV
// 只做一件事：让人用遥控器舒服地挑电影、看电影。
//
// ⚠️ AGP 9 的重大变化：不再声明 org.jetbrains.kotlin.android。
//    AGP 9 内置 Kotlin 支持（自带 KGP，9.4.1 → KGP 2.2.10），手动声明会直接报错。
//    Compose 编译器插件仍然需要，版本必须和内置 KGP 对齐 → 2.2.10。
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}

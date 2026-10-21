package com.ichichuang.cinema.feature.settings

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ComponentInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.accessibility.AccessibilityManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class DiagnosticField(val label: String, val value: String)

data class DiagnosticLaunch(
    val label: String,
    val action: String,
    val packageName: String,
    val className: String,
)

data class DiagnosticEntry(
    val title: String,
    val fields: List<DiagnosticField>,
    val launch: DiagnosticLaunch? = null,
)

data class DiagnosticSection(val id: String, val title: String, val entries: List<DiagnosticEntry>)

data class DeviceDiagnosticsSnapshot(val collectedAt: String, val sections: List<DiagnosticSection>) {
    /** 仅序列化本次白名单采集结果；不读取设置存储、账号、设备唯一标识或片源。 */
    fun toJson(): String {
        val payload = buildJsonObject {
            put("collectedAt", collectedAt)
            put("scope", "当前应用可见的公开 Android API 只读快照；不包含设备唯一标识、账号、凭据或片源地址。")
            put("limitations", "入口发现或打开不证明页面功能可用；HOME 解析不证明遥控器 HOME 行为；组件存在不证明安装兼容。")
            putJsonArray("sections") {
                sections.forEach { section ->
                    addJsonObject {
                        put("id", section.id)
                        put("title", section.title)
                        putJsonArray("entries") {
                            section.entries.forEach { entry ->
                                addJsonObject {
                                    put("title", entry.title)
                                    putJsonArray("fields") {
                                        entry.fields.forEach { field ->
                                            addJsonObject {
                                                put("label", field.label)
                                                put("value", field.value)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), payload)
    }
}

/**
 * 诊断与播放、片源及存储隔离。所有查询均为公开 API；单项失败不会丢弃其它结果。
 * 唯一可产生界面跳转的方法是 openSettings，且只能由电视上的明确点击触发。
 */
class DeviceDiagnostics(context: Context) {
    private val context = context.applicationContext
    private val pm get() = context.packageManager

    fun collect(): DeviceDiagnosticsSnapshot {
        val discoveredSettings = settingsActions.flatMap { (title, action) -> settingsEntries(title, action) }
        return DeviceDiagnosticsSnapshot(
            collectedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date()),
            sections = listOf(
                DiagnosticSection("device", "设备与系统", deviceEntries()),
                DiagnosticSection("settings", "系统设置入口", discoveredSettings + debuggingEntry() + homeEntries() + accessibilityEntries()),
                DiagnosticSection("components", "相关组件", relevantPackages.map { (name, title) -> packageEntry(name, title) }),
            ),
        )
    }

    /** 不尝试隐藏 Activity、不回退到任意页面、不请求权限，也不替用户更改设置。 */
    fun openSettings(target: DiagnosticLaunch): String {
        if (settingsActions.none { it.second == target.action }) {
            return "该入口不属于允许查询的系统设置。"
        }
        return try {
            val info = querySettings(target.action).firstOrNull {
                it.packageName == target.packageName && it.name == target.className
            } ?: return "该入口已不可见，请刷新诊断；没有打开其它页面。"
            if (!isSystemApp(info.applicationInfo) || !componentState(info, info.permission).canOpen) {
                return "该入口不是系统应用、未启用、未导出、权限不足或状态未知；没有请求修改权限。"
            }
            context.startActivity(
                Intent(target.action)
                    .setComponent(ComponentName(info.packageName, info.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            "已向系统请求打开${target.label}；页面内容和功能是否可用，仍需你在电视上确认。"
        } catch (_: ActivityNotFoundException) {
            "系统没有接受该入口，页面可能已移除或不可用。"
        } catch (_: SecurityException) {
            "系统拒绝打开该入口；没有请求额外权限或绕过限制。"
        } catch (error: Exception) {
            "打开失败：${safeError(error)}"
        }
    }

    private fun deviceEntries(): List<DiagnosticEntry> {
        val memory = runCatching {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: throw UnsupportedOperationException()
            ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        }
        val displayMode = runCatching {
            val manager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
                ?: throw UnsupportedOperationException()
            manager.getDisplay(Display.DEFAULT_DISPLAY)?.mode ?: throw UnsupportedOperationException()
        }
        val app = runCatching { pm.getPackageInfo(context.packageName, 0) }
        return listOf(
            DiagnosticEntry("设备", listOf(
                readField("制造商") { Build.MANUFACTURER },
                readField("品牌") { Build.BRAND },
                readField("型号") { Build.MODEL },
                readField("产品标识") { Build.PRODUCT },
                readField("设备代号") { Build.DEVICE },
            )),
            DiagnosticEntry("Android 与固件", listOf(
                readField("Android 版本") { Build.VERSION.RELEASE },
                readField("SDK") { Build.VERSION.SDK_INT.toString() },
                readField("固件显示名称") { Build.DISPLAY },
                readField("构建 ID") { Build.ID },
                readField("增量构建") { Build.VERSION.INCREMENTAL },
                readField("构建指纹（非设备唯一标识）") { Build.FINGERPRINT },
                readField("安全补丁") { Build.VERSION.SECURITY_PATCH },
                readField("系统支持 ABI") { Build.SUPPORTED_ABIS.joinToString(" / ") },
            )),
            DiagnosticEntry("内存与显示", listOf(
                resultField("系统总内存", memory) { memorySize(it.totalMem) },
                resultField("采集时可用内存", memory) { memorySize(it.availMem) },
                resultField("系统低内存状态", memory) { yesNo(it.lowMemory) },
                resultField("默认显示器当前模式", displayMode) {
                    "${it.physicalWidth} × ${it.physicalHeight} · ${String.format(Locale.ROOT, "%.2f", it.refreshRate)} Hz"
                },
                readField("应用资源画布（非面板分辨率）") {
                    context.resources.displayMetrics.let { "${it.widthPixels} × ${it.heightPixels} px · ${it.densityDpi} dpi" }
                },
                DiagnosticField("显示说明", "显示模式来自系统报告；应用画布可能经缩放，不能据此推断面板原生分辨率。"),
            )),
            DiagnosticEntry("极简影院", listOf(
                DiagnosticField("应用包名", context.packageName),
                resultField("应用版本", app) { "${it.versionName.orEmpty()}（${versionCode(it)}）" },
                readField("应用目标 SDK") { context.applicationInfo.targetSdkVersion.toString() },
            )),
        )
    }

    private fun settingsEntries(title: String, action: String): List<DiagnosticEntry> {
        val result = runCatching { querySettings(action) }
        if (result.isFailure) return listOf(DiagnosticEntry(title, listOf(
            DiagnosticField("发现状态", safeError(result.exceptionOrNull())),
            DiagnosticField("标准 Action", action),
        )))
        val activities = result.getOrThrow()
        if (activities.isEmpty()) return listOf(DiagnosticEntry(title, listOf(
            DiagnosticField("发现状态", "未发现或对当前应用不可见；不代表固件没有此功能。"),
            DiagnosticField("标准 Action", action),
        )))
        return activities.map { info ->
            val state = componentState(info, info.permission)
            val canOpen = isSystemApp(info.applicationInfo) && state.canOpen
            DiagnosticEntry(
                title = title,
                fields = listOf(
                    DiagnosticField("发现状态", "已发现匹配的 Activity；未验证页面内容。"),
                    DiagnosticField("标准 Action", action),
                ) + state.fields + DiagnosticField("可打开状态", if (canOpen) {
                    "公开状态允许尝试；打开后仍可能空白、被拒绝或无有效功能。"
                } else "不是系统应用、未满足启用/导出/权限条件，或状态未知。"),
                launch = if (canOpen) DiagnosticLaunch(title, action, info.packageName, info.name) else null,
            )
        }
    }

    private fun querySettings(action: String): List<ActivityInfo> =
        pm.queryIntentActivities(Intent(action), PackageManager.MATCH_DEFAULT_ONLY or PackageManager.MATCH_DISABLED_COMPONENTS)
            .mapNotNull { it.activityInfo }
            .distinctBy { ComponentName(it.packageName, it.name) }
            .sortedWith(compareBy({ it.packageName }, { it.name }))

    private fun debuggingEntry(): DiagnosticEntry {
        fun flag(label: String, key: String): DiagnosticField = readField(label) {
            when (Settings.Global.getString(context.contentResolver, key)) {
                "1" -> "系统向本应用返回 1（启用记录，仅供参考）"
                "0" -> "系统向本应用返回 0；可能被系统屏蔽，实际状态未知。"
                null -> "未知：系统未提供或不可读取。"
                else -> "未知：系统返回非标准值。"
            }
        }
        return DiagnosticEntry("系统调试记录（仅供参考）", listOf(
            flag("开发者选项记录", Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
            flag("USB ADB 记录", Settings.Global.ADB_ENABLED),
            DiagnosticField("证据边界", "系统可能对第三方应用固定返回 0；这些记录不证明开发者页面或 ADB 网络端点可用。没有连接或启用 ADB。"),
        ))
    }

    private fun homeEntries(): List<DiagnosticEntry> {
        val result = runCatching {
            pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo
        }
        val fields = mutableListOf(DiagnosticField("查询范围", "仅解析标准 MAIN + HOME Intent；没有模拟按键或启动桌面。"))
        result.fold(
            onSuccess = { info ->
                if (info == null) fields += DiagnosticField("解析结果", "无可见解析结果。")
                else {
                    fields += DiagnosticField("解析结果", "系统返回以下 Activity；也可能是系统选择器。")
                    fields += componentState(info, info.permission).fields
                }
            },
            onFailure = { fields += DiagnosticField("解析结果", safeError(it)) },
        )
        fields += DiagnosticField("证据边界", "这不证明遥控器 HOME 路由、桌面替换或 Aerial 兼容性。")
        return listOf(DiagnosticEntry("标准 HOME 解析", fields))
    }

    private fun accessibilityEntries(): List<DiagnosticEntry> {
        val managerResult = runCatching {
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                ?: throw UnsupportedOperationException()
        }
        val manager = managerResult.getOrNull() ?: return listOf(DiagnosticEntry("无障碍状态", listOf(
            DiagnosticField("读取状态", safeError(managerResult.exceptionOrNull())),
        )))
        // 系统 API 返回服务列表；立即过滤，只保留相关包，不导出其它应用的使用状态。
        val installed = runCatching {
            manager.installedAccessibilityServiceList.mapNotNull { it.resolveInfo?.serviceInfo }
                .filter { it.packageName in relevantPackages }
        }
        val enabled = runCatching {
            manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .mapNotNull { it.resolveInfo?.serviceInfo }
                .filter { it.packageName in relevantPackages }
        }
        val entries = mutableListOf(DiagnosticEntry("无障碍状态", listOf(
            readField("系统无障碍是否启用") { yesNo(manager.isEnabled) },
            resultField("相关已识别服务", installed) { "${it.size} 项（仅相关包）" },
            resultField("相关已启用服务", enabled) { "${it.size} 项（仅相关包）" },
            DiagnosticField("说明", "只读状态；已启用不代表服务正在运行或 HOME 功能有效。"),
        )))
        val services = (installed.getOrNull().orEmpty() + enabled.getOrNull().orEmpty())
            .distinctBy { ComponentName(it.packageName, it.name) }
            .sortedWith(compareBy({ it.packageName }, { it.name }))
        services.forEach { info ->
            val state = componentState(info, info.permission)
            entries += DiagnosticEntry("相关无障碍服务", state.fields + resultField("无障碍启用列表", enabled) { list ->
                if (list.any { it.packageName == info.packageName && it.name == info.name }) "已列入" else "未列入"
            } + DiagnosticField("权限说明", "服务的绑定权限属于系统调用条件；本应用不绑定或启用服务。"))
        }
        return entries
    }

    private fun packageEntry(packageName: String, title: String): DiagnosticEntry {
        val result = runCatching { pm.getPackageInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS) }
        val fields = mutableListOf(DiagnosticField("包名", packageName))
        result.fold(
            onSuccess = { info ->
                fields += DiagnosticField("发现状态", "当前用户范围内已发现且可见。")
                fields += DiagnosticField("组件版本", "${info.versionName.orEmpty()}（${versionCode(info)}）")
                val app = info.applicationInfo
                if (app != null) fields += applicationFields(app)
                else fields += DiagnosticField("应用状态", "系统未返回应用状态。")
            },
            onFailure = { fields += DiagnosticField("发现状态", safeError(it)) },
        )
        fields += DiagnosticField("证据边界", "包存在或启用不代表安装通道、桌面或屏保功能可用；导出与调用权限属于具体组件。")
        return DiagnosticEntry(title, fields)
    }

    private data class ComponentState(val fields: List<DiagnosticField>, val canOpen: Boolean)

    private fun componentState(info: ComponentInfo, permission: String?): ComponentState {
        val component = ComponentName(info.packageName, info.name)
        val appOverride = runCatching { pm.getApplicationEnabledSetting(info.packageName) }
        val componentOverride = runCatching { pm.getComponentEnabledSetting(component) }
        val permissionResult = runCatching {
            permission.isNullOrEmpty() || context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }
        val app = info.applicationInfo
        val appEnabled = app?.let { effectiveEnabled(appOverride.getOrNull(), it.enabled) }
        val componentEnabled = effectiveEnabled(componentOverride.getOrNull(), info.enabled)
        val suspended = app?.let { it.flags and ApplicationInfo.FLAG_SUSPENDED != 0 }
        val canOpen = appEnabled == true && componentEnabled == true && suspended == false &&
            info.exported && permissionResult.getOrNull() == true
        return ComponentState(listOf(
            DiagnosticField("组件", component.flattenToShortString()),
            DiagnosticField("属于系统应用", yesNo(isSystemApp(app))),
            DiagnosticField("应用启用字段", yesNo(app?.enabled)),
            resultField("应用启用覆盖", appOverride, ::enabledSetting),
            DiagnosticField("组件启用字段", yesNo(info.enabled)),
            resultField("组件启用覆盖", componentOverride, ::enabledSetting),
            DiagnosticField("有效启用状态", if (appEnabled == null || componentEnabled == null) "未知" else yesNo(appEnabled && componentEnabled)),
            DiagnosticField("系统暂停", yesNo(suspended)),
            DiagnosticField("对外导出", yesNo(info.exported)),
            DiagnosticField("调用所需权限", permission?.takeIf { it.isNotBlank() } ?: "未要求权限"),
            resultField("本应用权限检查", permissionResult) { if (permission.isNullOrEmpty()) "无需权限" else if (it) "已具备" else "未具备" },
        ), canOpen)
    }

    private fun applicationFields(info: ApplicationInfo): List<DiagnosticField> {
        val override = runCatching { pm.getApplicationEnabledSetting(info.packageName) }
        return listOf(
            DiagnosticField("应用启用字段", yesNo(info.enabled)),
            resultField("应用启用覆盖", override, ::enabledSetting),
            DiagnosticField("有效启用状态", effectiveEnabled(override.getOrNull(), info.enabled)?.let(::yesNo) ?: "未知"),
            DiagnosticField("系统暂停", yesNo(info.flags and ApplicationInfo.FLAG_SUSPENDED != 0)),
        )
    }

    private fun effectiveEnabled(override: Int?, reported: Boolean): Boolean? = when (override) {
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> reported
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
        else -> null
    }

    private fun enabledSetting(value: Int): String = when (value) {
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> "DEFAULT（遵循系统返回的启用字段）"
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> "ENABLED（显式启用）"
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> "DISABLED（显式停用）"
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER -> "DISABLED_USER（用户范围停用）"
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> "DISABLED_UNTIL_USED（使用前停用）"
        else -> "系统返回未知状态"
    }

    private fun readField(label: String, block: () -> String): DiagnosticField =
        resultField(label, runCatching(block)) { it }

    private fun <T> resultField(label: String, result: Result<T>, format: (T) -> String): DiagnosticField =
        DiagnosticField(label, result.fold(
            onSuccess = { value -> runCatching { format(value).ifBlank { "系统未提供" } }.getOrElse(::safeError) },
            onFailure = ::safeError,
        ))

    private fun yesNo(value: Boolean?): String = when (value) { true -> "是"; false -> "否"; null -> "未知" }
    private fun isSystemApp(info: ApplicationInfo?): Boolean =
        info != null && info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    private fun memorySize(bytes: Long): String = "${String.format(Locale.ROOT, "%.2f", bytes / 1073741824.0)} GiB（$bytes 字节）"

    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()

    private fun safeError(error: Throwable?): String = when (error) {
        is PackageManager.NameNotFoundException -> "未发现或对当前应用不可见；不能认定未安装。"
        is SecurityException -> "系统拒绝访问（权限或固件限制）。"
        is UnsupportedOperationException -> "系统不支持此公开查询。"
        is IllegalArgumentException -> "系统不接受此查询。"
        else -> "读取失败，结果未知；未导出异常详情。"
    }

    private companion object {
        val settingsActions = listOf(
            "开发者设置" to Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
            "无障碍设置" to Settings.ACTION_ACCESSIBILITY_SETTINGS,
            "设备信息" to Settings.ACTION_DEVICE_INFO_SETTINGS,
            "默认桌面设置" to Settings.ACTION_HOME_SETTINGS,
        )
        val relevantPackages = linkedMapOf(
            "app.orchard.tv" to "Aerial",
            "com.tcl.cyberui" to "TCL cyberui",
            "com.android.packageinstaller" to "Android 安装器",
            "com.tcl.MultiScreenInteraction_TV" to "TCL 多屏互动",
            "com.tcl.appmarket2" to "TCL 应用市场",
            "com.tcl.ui_mediaCenter" to "TCL 媒体中心",
            "com.tcl.guard" to "TCL guard",
            "com.tcl.walleve" to "TCL walleve",
            "com.tcl.appreciate.art" to "TCL 艺术屏保",
        )
    }
}

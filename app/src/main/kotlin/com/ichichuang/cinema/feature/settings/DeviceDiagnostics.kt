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

enum class DiagnosticLaunchKind {
    /** 标准 Android 设置 Action；点击时按 Action 重新查询并核对组件。 */
    STANDARD_ACTION,

    /** 已核对的厂商显式组件（目前仅 TCL 设置主页面候选）；点击时按组件重新查询。 */
    VENDOR_SETTINGS_MAIN,
}

data class DiagnosticLaunch(
    val label: String,
    val action: String,
    val packageName: String,
    val className: String,
    val kind: DiagnosticLaunchKind = DiagnosticLaunchKind.STANDARD_ACTION,
)

/** 按包名读取到的声明组件；只读信息，只有标准入口和已核对候选会另行提供打开按钮。 */
data class DiagnosticDetail(val title: String, val fields: List<DiagnosticField>)

data class DiagnosticEntry(
    val title: String,
    val fields: List<DiagnosticField>,
    val launch: DiagnosticLaunch? = null,
    val details: List<DiagnosticDetail> = emptyList(),
)

/** 一次由用户点击触发的启动请求；成功只代表系统接受了请求。 */
data class DiagnosticLaunchAttempt(
    val attemptedAt: String,
    val label: String,
    val component: String,
    val request: String,
    val accepted: Boolean,
    val outcome: String,
)

data class DiagnosticSection(val id: String, val title: String, val entries: List<DiagnosticEntry>)

data class DeviceDiagnosticsSnapshot(
    val collectedAt: String,
    val sections: List<DiagnosticSection>,
    val launchAttempts: List<DiagnosticLaunchAttempt> = emptyList(),
) {
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
                                    if (entry.details.isNotEmpty()) {
                                        putJsonArray("details") {
                                            entry.details.forEach { detail ->
                                                addJsonObject {
                                                    put("title", detail.title)
                                                    putJsonArray("fields") {
                                                        detail.fields.forEach { field ->
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
                    }
                }
            }
            putJsonArray("launchAttempts") {
                launchAttempts.forEach { attempt ->
                    addJsonObject {
                        put("attemptedAt", attempt.attemptedAt)
                        put("label", attempt.label)
                        put("component", attempt.component)
                        put("request", attempt.request)
                        put("accepted", attempt.accepted)
                        put("outcome", attempt.outcome)
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
            collectedAt = nowUtc(),
            sections = listOf(
                DiagnosticSection("device", "设备与系统", deviceEntries()),
                DiagnosticSection("settings", "系统设置入口", discoveredSettings + debuggingEntry() + homeEntries() + accessibilityEntries()),
                DiagnosticSection("declared", "设置应用声明组件", declaredSettingsEntries()),
                DiagnosticSection("components", "相关组件", relevantPackages.map { (name, title) -> packageEntry(name, title) }),
            ),
        )
    }

    /**
     * 点击时重新核对，不信任快照里的旧状态。
     * 只允许标准设置 Action 和已核对的 TCL 设置主页面候选；不发出隐藏 Activity、服务或广播。
     */
    fun openSettings(target: DiagnosticLaunch): DiagnosticLaunchAttempt {
        val attemptedAt = nowUtc()
        val component = "${target.packageName}/${target.className}"
        val request = when (target.kind) {
            DiagnosticLaunchKind.STANDARD_ACTION -> "标准 Action：${target.action}"
            DiagnosticLaunchKind.VENDOR_SETTINGS_MAIN -> "显式组件：ACTION_MAIN"
        }
        fun attempt(accepted: Boolean, outcome: String) =
            DiagnosticLaunchAttempt(attemptedAt, target.label, component, request, accepted, outcome)

        if (target.kind == DiagnosticLaunchKind.STANDARD_ACTION &&
            settingsActions.none { it.second == target.action }
        ) {
            return attempt(false, "该入口不属于允许查询的系统设置；未提交启动请求。")
        }
        if (target.kind == DiagnosticLaunchKind.VENDOR_SETTINGS_MAIN &&
            (target.packageName != TCL_SETTINGS_PACKAGE || target.className != TCL_SETTINGS_MAIN)
        ) {
            return attempt(false, "该组件不是已核对候选；未提交启动请求，也没有打开其它页面。")
        }
        return try {
            val info = when (target.kind) {
                DiagnosticLaunchKind.STANDARD_ACTION -> querySettings(target.action).firstOrNull {
                    it.packageName == target.packageName && it.name == target.className
                } ?: return attempt(false, "该入口已不可见，请刷新诊断；没有打开其它页面。")

                DiagnosticLaunchKind.VENDOR_SETTINGS_MAIN ->
                    runCatching {
                        pm.getActivityInfo(
                            ComponentName(target.packageName, target.className),
                            PackageManager.MATCH_DISABLED_COMPONENTS,
                        )
                    }.getOrElse {
                        return attempt(false, "点击时重新查询失败（${safeError(it)}）；没有打开其它页面。")
                    }
            }
            val state = componentState(info, info.permission)
            if (!isSystemApp(info.applicationInfo) || !state.canOpen) {
                return attempt(false, "点击时复核未通过：不是系统应用、未启用、未导出、权限不足或状态未知；没有请求修改权限。")
            }
            val intent = when (target.kind) {
                DiagnosticLaunchKind.STANDARD_ACTION -> Intent(target.action)
                DiagnosticLaunchKind.VENDOR_SETTINGS_MAIN -> Intent(Intent.ACTION_MAIN)
            }
                .setComponent(ComponentName(info.packageName, info.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            attempt(true, "已向系统提交打开${target.label}的请求；页面是否出现、内容是否可用，仍需你在电视上确认。")
        } catch (_: ActivityNotFoundException) {
            attempt(false, "系统没有接受该入口，页面可能已移除或不可用。")
        } catch (_: SecurityException) {
            attempt(false, "系统拒绝打开该入口；没有请求额外权限或绕过限制。")
        } catch (error: Exception) {
            attempt(false, "打开失败：${safeError(error)}")
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

    /** 三个设置包的声明组件 + TCL 主页面候选；与标准 Intent 发现分开呈现。 */
    private fun declaredSettingsEntries(): List<DiagnosticEntry> =
        declaredSettingsPackages.map { (name, title) -> settingsPackageEntry(name, title) } +
            tclSettingsCandidateEntry()

    private fun settingsPackageEntry(packageName: String, title: String): DiagnosticEntry {
        val result = runCatching {
            pm.getPackageInfo(
                packageName,
                PackageManager.GET_ACTIVITIES or PackageManager.MATCH_DISABLED_COMPONENTS,
            )
        }
        val fields = mutableListOf(
            DiagnosticField("包名", packageName),
            DiagnosticField("查询方式", "按包名读取声明组件（GET_ACTIVITIES，含禁用组件）；不是标准 Intent 匹配。"),
            DiagnosticField("查询状态", "已查询"),
        )
        return result.fold(
            onSuccess = { info ->
                fields += DiagnosticField("查询结果", "已发现（当前用户范围内可见）。")
                fields += DiagnosticField("组件版本", "${info.versionName.orEmpty()}（${versionCode(info)}）")
                val app = info.applicationInfo
                if (app != null) fields += applicationFields(app)
                else fields += DiagnosticField("应用状态", "系统未返回应用状态。")
                val activities = info.activities
                if (activities == null) {
                    fields += DiagnosticField("声明组件数量", "系统未返回")
                    fields += DiagnosticField("说明", "系统没有返回 Activity 列表；不能据此认定该包没有声明组件。")
                } else {
                    val distinct = activities
                        .distinctBy { ComponentName(it.packageName, it.name) }
                        .sortedWith(compareBy({ it.name }))
                    fields += DiagnosticField("声明组件数量", distinct.size.toString())
                    fields += DiagnosticField("其中 Activity 别名", distinct.count { it.targetActivity != null }.toString())
                    fields += DiagnosticField("读取范围", "包含未导出和已禁用组件；仅记录，不启用、不启动。")
                }
                DiagnosticEntry(
                    title = title,
                    fields = fields,
                    details = info.activities.orEmpty()
                        .distinctBy { ComponentName(it.packageName, it.name) }
                        .sortedWith(compareBy({ it.name }))
                        .map { activityDetail(it) },
                )
            },
            onFailure = { error ->
                fields += DiagnosticField("查询结果", queryResult(error))
                DiagnosticEntry(title, fields)
            },
        )
    }

    private fun activityDetail(info: ActivityInfo): DiagnosticDetail {
        val state = componentState(info, info.permission)
        val aliasTarget = info.targetActivity?.takeIf { it.isNotBlank() }
        val label = runCatching { info.loadLabel(pm).toString() }.getOrNull().orEmpty()
        return DiagnosticDetail(
            title = info.name + if (aliasTarget != null) "（别名）" else "",
            fields = listOf(
                DiagnosticField("完整组件名", ComponentName(info.packageName, info.name).flattenToString()),
                DiagnosticField("类型", if (aliasTarget != null) "Activity 别名" else "Activity"),
                DiagnosticField("别名目标", aliasTarget ?: "不适用"),
                DiagnosticField("界面标签", label.ifBlank { "系统未提供" }),
            ) + state.fields + DiagnosticField("可打开状态", if (state.canOpen && isSystemApp(info.applicationInfo)) {
                "公开状态允许尝试；但仅标准设置入口和已核对候选提供按钮，本组件只读展示。"
            } else "非系统应用、未启用、未导出、权限不足或状态未知；只读记录，不提供打开。"),
        )
    }

    /** 同平台标识设备的笔记只是线索；必须在本机按显式组件重新查询后才提供按钮。 */
    private fun tclSettingsCandidateEntry(): DiagnosticEntry {
        val component = ComponentName(TCL_SETTINGS_PACKAGE, TCL_SETTINGS_MAIN)
        val result = runCatching {
            pm.getActivityInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS)
        }
        val fields = mutableListOf(
            DiagnosticField("查询方式", "getActivityInfo 显式组件查询（不是 Intent 解析；解析为空不等于组件不存在）。"),
            DiagnosticField("候选组件", component.flattenToShortString()),
            DiagnosticField("候选来源", "Aerial MT5879 设备笔记记录可用 ACTION_MAIN 打开该组件；同平台标识不构成本机验证。"),
            DiagnosticField("查询状态", "已查询"),
        )
        val info = result.getOrNull()
        if (info == null) {
            fields += DiagnosticField("查询结果", queryResult(result.exceptionOrNull()))
            fields += DiagnosticField("可打开状态", "未查询到组件，不提供打开按钮，也不回退到其它页面。")
            return DiagnosticEntry("TCL 设置主页面候选", fields)
        }
        fields += DiagnosticField("查询结果", "已发现（系统返回了该组件）。")
        val state = componentState(info, info.permission)
        val label = runCatching { info.loadLabel(pm).toString() }.getOrNull().orEmpty()
        val canOpen = isSystemApp(info.applicationInfo) && state.canOpen
        fields += DiagnosticField("界面标签", label.ifBlank { "系统未提供" })
        fields += state.fields
        fields += DiagnosticField("可打开状态", if (canOpen) {
            "公开状态允许提交显式启动请求；请求成功不等于页面出现，也不代表页面包含开发者选项。"
        } else "不是系统应用、未启用、未导出、权限不足或状态未知；只读记录，不提供打开。")
        return DiagnosticEntry(
            title = "TCL 设置主页面候选",
            fields = fields,
            launch = if (canOpen) {
                DiagnosticLaunch(
                    label = "TCL 设置主页面",
                    action = Intent.ACTION_MAIN,
                    packageName = component.packageName,
                    className = component.className,
                    kind = DiagnosticLaunchKind.VENDOR_SETTINGS_MAIN,
                )
            } else null,
        )
    }

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
        val entries = mutableListOf<DiagnosticEntry>(DiagnosticEntry("无障碍状态", listOf(
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

    private fun nowUtc(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date())

    /** 包查询结果只保留状态类别，不导出异常详情。 */
    private fun queryResult(error: Throwable?): String = when (error) {
        null -> "已发现"
        is PackageManager.NameNotFoundException -> "未发现或对当前应用不可见"
        is SecurityException -> "系统拒绝访问（权限或固件限制）"
        is UnsupportedOperationException -> "系统不支持此公开查询"
        is IllegalArgumentException -> "系统不接受此查询"
        else -> "读取失败，结果未知"
    }

    private fun safeError(error: Throwable?): String = when (error) {
        is PackageManager.NameNotFoundException -> "未发现或对当前应用不可见；不能认定未安装。"
        is SecurityException -> "系统拒绝访问（权限或固件限制）。"
        is UnsupportedOperationException -> "系统不支持此公开查询。"
        is IllegalArgumentException -> "系统不接受此查询。"
        else -> "读取失败，结果未知；未导出异常详情。"
    }

    private companion object {
        const val TCL_SETTINGS_PACKAGE = "com.tcl.settings"
        const val TCL_SETTINGS_MAIN = "com.tcl.settings.ui.MainActivity"

        val settingsActions = listOf(
            "系统设置" to Settings.ACTION_SETTINGS,
            "开发者设置" to Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
            "无障碍设置" to Settings.ACTION_ACCESSIBILITY_SETTINGS,
            "设备信息" to Settings.ACTION_DEVICE_INFO_SETTINGS,
            "默认桌面设置" to Settings.ACTION_HOME_SETTINGS,
        )
        val declaredSettingsPackages = linkedMapOf(
            "com.tcl.settings" to "TCL 设置",
            "com.android.settings" to "Android 设置",
            "com.android.tv.settings" to "Android TV 设置",
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

# 权属与第三方材料审计

日期：2026-10-23。此文件记录检查范围和义务，不为原创源码授予许可证，也不宣称权属全部已清。

## 原创源码与捆绑资源

起点历史的 32 个提交记录同一作者名 `ichichuang`；Git 作者字段不足以证明每段代码原创或获得授权。尚需维护者确认源码来源、外部复制片段、既往合作授权及图标/横幅的制作或授权记录。

当前受跟踪资源为 5 个密度的 launcher PNG、1 个横幅 PNG，以及字符串、主题和 FileProvider XML；没有捆绑字体。`gradle-wrapper.jar` 是唯一受跟踪的第三方工具二进制，内部含 `META-INF/LICENSE`，保持原文件。FongMi 技能和配置整理脚本只提供独立操作工作流，本源码树未捆绑 FongMi APK；不能因此推断其外部配置或来源内容具有再分发权。

## 依赖与 notices

已解析当前 `releaseRuntimeClasspath` 的 216 个模块坐标，并逐一读取对应 POM 或继承的 parent POM，均取得许可声明。另检查过 118 份主要依赖族缓存 POM；缓存包含非当前版本。解析图包括 BOM/元数据坐标，216 不是最终 APK 内二进制组件数量，也不等于完成了发行 SBOM；直接依赖版本以 `app/build.gradle.kts` 和解析图为准。

| 材料 | 已读 POM/捆绑证据 | 处理与剩余门槛 |
| --- | --- | --- |
| AndroidX Compose、TV、Core、Activity、Navigation、Media3、Graphics | Apache License 2.0 系列声明 | 保留许可证与 NOTICE；复查发行包内传递组件 |
| AndroidX DataStore | Apache 2.0；datastore-preferences-external-protobuf 为 BSD-3-Clause | BSD 对应外部 protobuf Java 组件，不是凭原生库名推断；实际 APK 的独立 BSD 文本及版权补充需保留 |
| Kotlin、kotlinx、Koin | Apache 2.0 系列声明 | 依据实际解析坐标保留许可及通知 |
| OkHttp、Okio、Coil、ZXing | Apache 2.0 系列声明 | 依据实际解析坐标保留许可及通知 |
| Gradle wrapper | JAR 内含 LICENSE | 保留现有 JAR 与内置许可 |
| JUnit 4.13.2 | Eclipse Public License 1.0 | 仅 testImplementation，不视为随 APK 分发；若另行分发需重审 |

构建原先排除了 `META-INF/AL2.0` 和 `META-INF/LGPL2.1`；现改为合并保留，防止主动删除依赖许可。复验实际 APK 含 8 份 AndroidX LICENSE 文本，没有 AL2.0/LGPL2.1 同名资源；不能仅凭配置声称所有许可已经装入 APK。此改动不能代替完整、与最终发行产物逐项对应的 SBOM/版权通知核对。不能以文件名出现 LGPL 推断本应用采用 LGPL，或以当前表格替代依赖自身条款。

## 具体许可证建议

建议维护者在确认原创源码及图标/横幅授权后，为原创部分选择 [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)，并保留第三方各自声明与通知。它与已检查的大多数 Android 依赖的许可处理方式一致，并包含明确的专利授权条款。已读 POM 未为原创代码提供授权；测试依赖也不能决定项目许可证。

当前没有维护者的许可证批准，也没有图像来源证明，因此本轮不新增 `LICENSE`、不修改源文件的归属声明、不宣布可公开发行。最终传递依赖/资源权属、NOTICE 完整性及维护者许可决定仍是源码公开与正式发布的验收门。

## 检查点后的产物关联清单与通知

[component-inventory.json](evidence/component-inventory.json) 以既有 releaseRuntimeClasspath、debugUnitTestRuntimeClasspath 和 buildscript classpath 的实际 resolved JAR/AAR 为输入，固定 artifact 下载地址及 SHA-256，记录 archive 类数、APK DEX 中匹配的类、原生文件摘要和 notice 资源摘要。它区分 runtime、build、test；同坐标同字节合并，BOM/空平台模块不冒充出货代码。匹配类证明存在，不能证明独占归属，也不声称是完整法律 SBOM。

本轮解析 244 份不同坐标/字节的 artifact：136 份带 runtime scope（其中 114 份有 DEX/原生文件在 APK 内的证据），108 份带 build scope，两者部分重合；另有 JUnit 和 Hamcrest 两份 test-only。原 216 是模块解析图坐标，不与二进制数量混用。build scope 中六个 POM 没有读到许可声明，清单保留空值和 artifact notice 位置，没有编造 SPDX 或把构建工具混进 APK。此清单不展开 JDK、SDK、Gradle 发行包的所有内部组件。

135 份当前 runtime 对应的已发布 source JAR 已只读取得并固定 URL/摘要，用于核对版权头；唯一不可取得 sources 的 listenablefuture 空占位 artifact 没有类。版权观察和 upstream 精确版本文本保存在 [app/src/main/assets/third_party](../app/src/main/assets/third_party/)，随 debug/unsigned release 一并打包，包含 Apache 文本、AndroidX protobuf 的实际 BSD 文本及版权补充、Kotlin/Coil/Coroutines/Stately/OkHttp 通知和逐组件版权。没有把这些第三方许可当作原创项目的 LICENSE。

进一步核对 Kotlin 2.4.20 官方第三方索引发现 stdlib 的 ThreeTenBP 派生时间代码适用 BSD-3-Clause，JVM 数学派生代码适用 Boost Software License 1.0，不能仅根据 POM 的 Apache 声明遗漏；相应原文及署名已随包补齐。依据固定版本的 [Kotlin 索引](https://github.com/JetBrains/kotlin/blob/v2.4.20/license/README.md)。DataStore 内 shaded protobuf 的确切上游版本本轮未证明；补充的 Google 版权文本标记为 supplement，不把其来源版本推定为内嵌版本。

生产仍无 LICENSE 批准，源码及六个图像的权属见 [ASSET_PROVENANCE.md](ASSET_PROVENANCE.md)，操作和批准集中于 [OWNER_DECISIONS.md](OWNER_DECISIONS.md)。当前 notices 是实际检查所得材料，不宣布所有法务或发行义务已经完成。

## 再核对方法

在现有 SDK/JDK 环境执行 `bash ./gradlew :collectBuildComponents :app:collectRuntimeComponents -I scripts/component_inventory.init.gradle --offline`（只有缓存完整时用 offline），只写被忽略的 `.local/remediation/component-inputs-{build,app}.json`。该 init 脚本不修改生产构建/依赖。

构建 unsigned release 后运行 `python3 scripts/collect_component_inventory.py --inputs .local/remediation/component-inputs-app.json .local/remediation/component-inputs-build.json --apk app/build/outputs/apk/release/app-release-unsigned.apk --output "$COMPONENT_REVIEW_JSON"`。输出位置由维护者选择，避免在共享日志输出个人目录。source archive 与 upstream notice 的 URL/摘要在已提交清单中，用于人工复核版权；脚本不根据 POM 自动宣称源码权利。

清单固定的是其中明确记录 SHA-256 的审阅 APK。之后只有 Git VCS 元数据变化的重新构建不能沿用整个 APK 摘要；最终验收须重新比较组件/notice/DEX/原生条目，并单列最终 APK 摘要。未上传任何本轮 APK。

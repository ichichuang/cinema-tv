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
| AndroidX DataStore | Apache 2.0；部分原生组件元数据含 BSD-3-Clause | 两份 armeabi-v7a 原生库中包含 DataStore 组件；需保留其 BSD 版权与条件 |
| Kotlin、kotlinx、Koin | Apache 2.0 系列声明 | 依据实际解析坐标保留许可及通知 |
| OkHttp、Okio、Coil、ZXing | Apache 2.0 系列声明 | 依据实际解析坐标保留许可及通知 |
| Gradle wrapper | JAR 内含 LICENSE | 保留现有 JAR 与内置许可 |
| JUnit 4.13.2 | Eclipse Public License 1.0 | 仅 testImplementation，不视为随 APK 分发；若另行分发需重审 |

构建原先排除了 `META-INF/AL2.0` 和 `META-INF/LGPL2.1`；现改为合并保留，防止主动删除依赖许可。此改动不能代替完整、与最终发行产物逐项对应的 SBOM/版权通知核对。不能以文件名出现 LGPL 推断本应用采用 LGPL，或以当前表格替代依赖自身条款。

## 具体许可证建议

建议维护者在确认原创源码及图标/横幅授权后，为原创部分选择 [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)，并保留第三方各自声明与通知。它与已检查的大多数 Android 依赖的许可处理方式一致，并包含明确的专利授权条款。已读 POM 未为原创代码提供授权；测试依赖也不能决定项目许可证。

当前没有维护者的许可证批准，也没有图像来源证明，因此本轮不新增 `LICENSE`、不修改源文件的归属声明、不宣布可公开发行。最终传递依赖/资源权属、NOTICE 完整性及维护者许可决定仍是源码公开与正式发布的验收门。

# 开发指南

项目使用 Java 25 和 Gradle Wrapper 9.6.0，依赖版本固定在 `gradle.properties`。Minecraft 26.3 使用官方可读名称，无 Yarn / intermediary mappings 依赖。

## 项目标识

| 标识 | 值 |
| --- | --- |
| 显示名称 / Gradle 项目名 | PrefabLitematica |
| 版本 | 0.1.0 |
| 项目原创代码许可证 | `AGPL-3.0-only` |
| Fabric Mod ID / 资源与网络命名空间 | `prefablitematica` |
| Java 包 / Maven group | `dev.tensin.prefablitematica` |
| 集成测试 Mod ID | `prefablitematica-test` |
| 服务端配置 | `config/prefablitematica.json` |
| 世界内数据 | `prefablitematica/blueprints/` |

`Blueprint` 用于表示建筑蓝图领域对象，例如 `BlueprintData` 和 `BlueprintWorkbenchScreen`。

## 目录结构

```text
src/main/java/dev/tensin/prefablitematica/
  PrefabLitematicaMod.java      注册物品、方块和服务端生命周期
  blueprint/                   结构模型、序列化、材料分析与进度存储
  material/                    方块计费、流体计费与材料 Tags
  security/                    压缩大小限制与 NBT 清洗
  placement/                   旋转、权限检查、范围预留和分阶段放置
  network/                     分片上传及服务端状态同步
  block/、item/、screen/        工程台、蓝图物品和容器逻辑
  config/                      服务端配置
  mixin/                       放置范围内的世界修改保护
  datagen/                     配方、Tags、语言、模型及原创像素贴图
src/client/java/               客户端入口、GUI 与可选 Litematica 适配
src/main/resources/            Fabric 元数据与 Mixin 配置
src/main/generated/            随源码提交的生成资源
src/test/java/                 JUnit 单元测试
src/gametest/                  服务端及客户端集成测试、测试专用原理图
docs/                         使用、配置、架构、验证及截图
```

客户端适配和 GUI 独立放在 `src/client/java`，Dedicated Server 不加载这些类。Litematica 通过反射隔离可选依赖，当前适配 0.29.1，接口不兼容时返回明确错误。

## 构建与测试

以下命令在项目根目录的 Windows PowerShell 执行。Linux / macOS 使用 `./gradlew`，首次运行先执行 `chmod +x gradlew`。

```powershell
# 编译、JUnit、服务端 GameTests、发布 JAR 和源码 JAR
.\gradlew.bat build

# Dedicated Server 集成测试，不安装 Litematica
.\gradlew.bat runGameTest

# 客户端 GUI 与真实 Litematica 导入回归，需要图形环境
.\gradlew.bat -PwithLitematica runClientGameTest

# 生成配方、Tags、语言、模型和贴图；随后重新构建
.\gradlew.bat runDatagen
.\gradlew.bat build

# 含可选 Litematica / MaLiLib 的开发客户端
.\gradlew.bat -PwithLitematica runClient

# 完整项目源码归档
.\gradlew.bat sourceDistribution
```

`build` 包含 JUnit 和 Dedicated Server GameTests，不自动执行客户端 GameTests。可以用 `build -x runGameTest` 仅执行编译、JUnit 与打包。Datagen 与构建分开执行，生成结果应随相关源码一起提交。项目通过本地 Gradle 命令构建与验证，发布产物由维护者自行分发。

产物：

- `build/libs/prefablitematica-fabric-26.3-0.1.0.jar`
- `build/libs/prefablitematica-fabric-26.3-0.1.0-sources.jar`
- `build/distributions/prefablitematica-fabric-26.3-0.1.0-source.zip`

版本只在 `gradle.properties` 的 `version` 更新，主模组和测试模组元数据由资源处理任务展开；Minecraft 版本由 `minecraft_version` 控制产物前缀。

## 测试资源与提交范围

客户端原理图回归资源位于 `src/gametest/resources/prefablitematica-test/swamp-mob-farm.litematic`，来自用户提供的 `沼泽刷怪塔-hsds.litematic`，原作者为 `white_elephant_`。它仅用于测试，包含在项目源码归档中，不进入发布版 JAR。

`.gitignore` 排除 Gradle 缓存、构建输出、运行目录、日志、本地参考资料、IDE 配置及机器私有文件。提交包含 Gradle Wrapper、全部源代码、生成资源、测试和文档。

发布前执行构建与对应回归，检查 JAR 中的名称、版本、入口和资源命名空间，并更新 [验证记录](VALIDATION.md) 与 [更新日志](../CHANGELOG.md)。

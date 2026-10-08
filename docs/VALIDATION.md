# 验证记录

验证日期：2026-10-08。项目：PrefabLitematica 0.1.0。环境：Minecraft 26.3、Fabric Loader 0.19.5、Fabric API 0.161.0+26.3、Java 25.0.3、Gradle 9.6.0。

## 本次结果

| 验证 | 结果 |
| --- | --- |
| `gradlew.bat clean runDatagen` | 成功；生成 67 个资源，包括 45 类材料 Tags、配方、语言、模型和六张贴图 |
| `gradlew.bat build runGameTest` | 成功；客户端与服务端编译、JAR 与源码 JAR 输出成功 |
| JUnit | 24 项通过，无失败或跳过；遍历 35,563 种允许导入的原版方块状态 |
| Dedicated Server GameTests | 19 项项目测试与 1 项 Fabric 运行时测试全部通过，无 Litematica 环境 |
| `gradlew.bat -PwithLitematica runClientGameTest` | 四项客户端回归通过，使用 Litematica 0.29.1 和 MaLiLib 0.30.2；全部文档截图重新生成 |
| AGPLv3 更新后的 `gradlew.bat build` | 成功；重新编译、执行 24 项 JUnit 与 20 项服务端测试并打包 |
| 发布 JAR 内容 | 名称、Mod ID、版本、入口、Mixin、资源命名空间和 `AGPL-3.0-only` 标识正确；内置 LICENSE 与项目正文一致；未包含测试原理图或旧命名空间 |
| `gradlew.bat sourceDistribution` | 成功；项目源码归档包含源代码、生成资源、文档、Wrapper、Git 配置文件和 AGPLv3 正文，排除本地运行与缓存 |
| 项目静态检查 | 64 个 JSON 可解析；41 个 Java 文件的包路径与 SPDX 标识正确；中英文语言键一致、静态引用均存在；文档链接有效 |
| 截图检查 | 双 3×3 网格、材料图标、缺少与完成项排序、4096 数量显示及游戏内工程台模型正常 |

发布产物：`build/libs/prefablitematica-fabric-26.3-0.1.0.jar`、`build/libs/prefablitematica-fabric-26.3-0.1.0-sources.jar`。AGPLv3 正文来自 [GNU 官方许可文本](https://www.gnu.org/licenses/agpl-3.0.txt)。

## 测试覆盖

单元测试检查分批充能与超额输入、材料完成条件和跨页排序、多方块结构配对、流体计费、容器与告示牌 NBT 清洗、BlockEntity 类型、NBT 深度、旋转、序列化、重复与越界坐标及压缩炸弹。材料注册表回归覆盖植物茎段、竹笋、高海草、气泡柱、霜冰、六方向普通与黏性活塞配对，以及残缺或移动中的活塞拒绝。

服务端测试检查真实 Tags 的形态与颜色规则、替代材料保留原始 BlockState、流体桶返还、创造电池优先充能且不产生空桶、SAFE 全范围预检、共享 UUID 锁定、旋转放置、进度落盘和每 Tick 预算。容器回归验证 4096 数量及物品组件的存档往返、普通取出与 Shift-click、背包满时余量保留、潜影盒内材料消耗及组件保留、九格返还区与溢出掉落。

四项客户端回归：

- `PrefabLitematicaClientGameTest`：通过真实 Litematica 公共接口创建原理图和未启用投影，从列表导入、清理箱子内容、转换蓝图物品并使用材料区电池充能。
- `SwampSchematicClientGameTest`：导入真实沼泽刷怪塔文件，验证尺寸 52×131×37、6,047 个方块、7 格气泡柱、2 个水桶及 1 个灵魂沙需求，并成功充能。
- `MaterialInputClientGameTest`：通过真实容器网络包验证 64＋64＝128、4032＋64＝4096、普通取出及潜影盒充能；空桶和剩余物品正确返还。
- `WorkbenchLayoutClientGameTest`：验证双 3×3 网格、跨页排序、补齐材料后回到第一页、返还区最后一格 Shift-click，以及游戏内物品图标与工程台模型。

## 截图

| 截图 | 内容 |
| --- | --- |
| [建筑列表](screenshots/blueprint-building-list.png) | 未加载原理图列表导入 |
| [已充满蓝图](screenshots/blueprint-workbench-charged-zh-cn.png) | 材料区电池充能 |
| [128 个材料](screenshots/blueprint-material-input-128.png) | 网络同步后的材料累加 |
| [4096 个材料](screenshots/blueprint-material-input-4096.png) | 大堆叠与潜影盒输入 |
| [双网格与材料图标](screenshots/blueprint-3x3-returns-material-icons.png) | 输入、返还及完成项排序 |
| [充能后排序](screenshots/blueprint-materials-resorted-after-charge.png) | 材料列表重新排序 |
| [真实原理图导入](screenshots/blueprint-swamp-mob-farm-imported.png) | 沼泽刷怪塔材料分析 |
| [工程台外观](screenshots/blueprint-workbench-vanilla-style.png) | 与原版工作站并排对照 |

## 未验证范围

尚未进行 500000 方块的长时间性能压测或多客户端压力测试；沼泽刷怪塔回归未验证整座结构的放置与运行效率。开始放置后的进程强制终止可能留下部分建筑，充能保持已消耗；行为说明见 [架构与数据安全](ARCHITECTURE.md)。

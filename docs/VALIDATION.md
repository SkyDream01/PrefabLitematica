# 验证记录

验证日期：2026-10-09（0.1.3 完整功能回归），首版记录为 2026-10-08。项目：PrefabLitematica 0.1.3。环境：Minecraft 26.3、Fabric Loader 0.19.5、Fabric API 0.161.0+26.3、Java 25.0.3、Gradle 9.6.0。

## 0.1.3 完整功能回归 — 2026-10-09

本轮实际启动专用服务器和 Minecraft 图形客户端，安装 Litematica 0.29.1 / MaLiLib 0.30.2 验证可选集成，并另外启动未安装这两个模组的客户端验证内置投影。覆盖项目当前公开的导入、材料、工程台、放置、投影、配置和持久化功能；性能与第三方环境边界见文末。

| 验证 | 结果 |
| --- | --- |
| `gradlew.bat runDatagen` | 成功；生成 67 个资源，中英文包含完整说明和两行操作提示 |
| `gradlew.bat build` | 32 项 JUnit 与 46 项专用服务器 GameTests 通过，无失败或跳过；其中包含 1 项 Fabric 运行时测试 |
| `gradlew.bat -PwithLitematica runClientGameTest` | 完整 7 项真实客户端回归通过：三种原理图来源、真实文件列表与网络导入、材料输入、工程台布局、投影确认、真实建筑和门/保留方块策略 |
| `gradlew.bat -PprojectionOnly runClientGameTest` | 无 Litematica 环境通过：数据面板、轮廓投影、冲突禁用、三轴坐标、四方向、执行、取消和清理 |
| `gradlew.bat -PwithLitematica -PpolicyOnly runClientGameTest` | 地狱门专项通过；英文实际截图与完整套件中的中文截图均确认操作提示可读 |
| 合成与工程台拆除 | 原始配方实际合成工程台与空白蓝图；拆除后 4096 个输入材料无损返还，第九返还槽的潜影盒名称和内容、蓝图 UUID 与已付充能全部保留 |
| 材料与容器 | 全部允许的原版方块状态规则、45 类替代与形态区分、分批/大堆叠、工具转换及逐次耐久、潜影盒、细雪/水/岩浆桶返还、背包满溢出和电池优先回归通过 |
| 地狱门捕获 | X/Z 两轴、无角完整门框、缺边切门、过小/过大门洞及旋转/镜像捕获通过；门洞转为明确空气单元，不收门洞材料，移除门方块自带 Tick，正确区分点火与切门提示 |
| 门提示与网络 | 真实文件导入、两行提示、投影分发与原生方块显示、门标记保存/读取通过；关闭生存导入后真实网络请求被拒绝，空白蓝图保留 |
| 8 类保留方块 | 服务端真实 Tags 与材料分析通过；在 SAFE/REPLACE 两种模式和四方向中不报占用冲突、不计费，并完整保留目标箱子的钻石库存及全部 NBT；客户端实际 Litematica 投影包含所有 8 类方块 |
| 明确空气粘贴 | 门洞的明确空气单元可以在 REPLACE 下清除容器及 NBT；未列出的普通空气单元不会清空世界；SAFE 下仍拒绝占用的门洞 |
| 权限与预留 | 包围盒空格上的权限拒绝在预览和最终检查均生效，失败不扣充能；普通目标仍不能覆盖世界基岩；预留范围阻止外部改块，开始扣费前取消可释放范围并保留充能 |
| 配置与定时 Tick | 用后消耗选项移除原物品并持久化停用所有 UUID 副本；保存的方块/流体 Tick 在真实世界中调度到旋转后坐标 |
| 沼泽刷怪塔 | 经真实 UI 导入、充能后整座 90° 跨区块粘贴，6047 个方块状态逐块一致；扣费落盘且原结构不变 |
| 珍珠炮 | 2723 个旋转方块、72 个完整 BlockEntity NBT、60 个比较器输出与原文件直接粘贴一致；三条粘贴路径 0～160 Tick 的 161 帧 TNT 运行轨迹一致，峰值均为 68；旧蓝图重新导入保留 UUID 与充能 |
| 旧格式与上传数据 | BPR1/BPR2/BPR3/BPR4 往返和兼容通过；非法坐标、重复坐标、Tick 类型/优先级、超限 NBT、命令与容器清洗及压缩炸弹拒绝回归通过 |
| 发布检查 | 0.1.3 发布 JAR、源码 JAR 与源码 ZIP 生成；版本、入口、许可证与语言资源正确，不包含测试类或原理图；JSON、翻译键/占位符、文档链接及 `git diff --check` 通过 |

发现并修复：门提示原先被单行截断，点火操作不可见，现改为两行并检查中英文实际字体宽度；BPR4 新增两个提示字段后，旧比较器测试仍按 BPR2 截断数据，现正确构造旧格式并新增四代兼容验证。新增客户端测试同时修正重开工程台时的等待条件，使用不会被按钮截短的测试文件名。

发布产物：`build/libs/prefablitematica-fabric-26.3-0.1.3.jar`、`build/libs/prefablitematica-fabric-26.3-0.1.3-sources.jar`、`build/distributions/prefablitematica-fabric-26.3-0.1.3-source.zip`。以下保留历次验证记录，当前结论以上表为准。

## 材料转换与细雪 — 2026-10-09

| 验证 | 结果 |
| --- | --- |
| `gradlew.bat runDatagen`，随后单独执行 `gradlew.bat build` | 成功；31 项 JUnit、38 项服务端 GameTests 全部通过，发布 JAR 与源码 JAR 生成成功 |
| 泥土与工具转换 | 泥土无需工具可供料草方块；铲子造土径、锄头造耕地、剪刀雕刻南瓜、斧头去皮均逐次扣材料与耐久；成品优先、错误工具和严格匹配下的错误树种/形态不扣料或耐久 |
| 耐久与部分充能 | 耐久附魔和创造玩家不减免；多把工具可共同支付操作次数，耐久不足时保留未转换的材料；用尽的工具消失，补工具后只支付剩余需求；砂土变耕地两次，涂蜡氧化铜灯除蜡并去三级氧化共四次 |
| 潜影盒与存档 | 盒内工具可为另一盒原料供料，工具名称、剩余耐久、未用材料和原槽位保留，工具盒也正确返还；工具耐久及蓝图充能落盘后可读取，原容器组件不被修改 |
| 细雪 | 每格细雪及每个细雪炼药锅各收一个细雪桶，锅另计费；水桶不能替代，普通输入和盒内桶共返还准确数量的空桶；保存与放置保留细雪方块及炼药锅液位 |
| 发布内容 | JAR 包含转换注册表、更新后的工程台和中英文转换提示，不包含服务端测试类或测试原理图；`git diff --check` 通过 |

本轮编译客户端并检查生成语言键，未新增客户端悬浮提示截图回归。

## 实际粘贴入口与珍珠炮运行对照 — 2026-10-09

上一轮只比较方块状态，未比较比较器方块实体数据，因此不能证明机器等效。用户原文件的 60 个比较器均有 `OutputSignal=1`；旧白名单把它清空为默认 0，这是方块外观一致而红石行为不同的实质差异。

| 验证 | 结果 |
| --- | --- |
| `gradlew.bat -PwithLitematica -PpistonOnly runClientGameTest build` | 成功；29 项 JUnit、30 项服务端 GameTests 全部通过，Litematica 0.29.1 / MaLiLib 0.30.2 实际粘贴对照通过 |
| 实际调用 | 单人集成服务器通过反射调用 `SchematicPlacingUtils.placeToWorldWithinChunk`；专用服务器运行对应的 `BlueprintPaste`，不依赖客户端 Litematica 类 |
| 原文件对照 | 同坐标、同 90° 方向依次比较原始 `.litematic` 直接粘贴、蓝图原生粘贴、专用服务器实现；2,723 个方块状态与 72 个方块实体完整保存 NBT 一致，60 个比较器输出均为 1 |
| 自动 TNT 运行 | 三条路径自动将原图压力板压下 10 Tick，逐 Tick 比较 0–160 Tick 的完整方块状态、BlockEntity NBT 和区域 TNT 实体数量；161 个采样帧全部一致，TNT 峰值均为 68；白色混凝土支撑及熔炉结构在运行后保持完整 |
| 旧蓝图修复 | 真实工程台 UI 与网络流程重新导入相同原文件，保留原 UUID 和 100% 充能；单元测试拒绝不同结构、不同材料需求，以及仍然缺失比较器数据的替换 |
| 数据与兼容 | 比较器运行值保留并限制到 0–15；定时 Tick 类型、优先级、触发值与子序号往返一致，拒绝类型错配、越界和非法优先级；旧 BPR1 石块蓝图仍可读取，旧比较器数据被标记为需要修复 |

本对照验证了这份珍珠炮的粘贴元数据与自动 TNT 运行。没有预装玩家珍珠、测试最终传送落点或不同模组环境下的弹道。原文件的库存、实体及定时 Tick 列表为空；材料系统的一般库存/实体清洗规则仍适用，其他方块实体只保留支持的运行字段，详见 [架构与数据安全](ARCHITECTURE.md)。单区块状态与 NBT 同次完成，每 Tick 配置是目标预算，旧严格逐块预算已改为按区块边界验证。

## 粘贴模式验证 — 2026-10-09

| 验证 | 结果 |
| --- | --- |
| `gradlew.bat -PwithLitematica -PpistonOnly runClientGameTest build` | 成功；25 项 JUnit、30 项服务端 GameTests 全部通过，真实 Litematica 珍珠炮导入、充能及整座旋转粘贴回归通过，发布 JAR 与源码 JAR 生成成功 |
| 原始状态保留 | 无电源的无头伸出活塞、强度 15 的悬空红石线、通电侦测器、悬空火把和沙子、无支撑火焰、含水台阶和等级 5 的流动水直接写入，经过服务端 Tick 后仍保持原状态；放置不人为生成方块或流体定时 Tick |
| 预览与权限 | 预览允许悬空方块，旋转后的包围盒与实际粘贴一致；全范围占用、权限、边界、预留及确认复检继续通过原有回归 |
| 选区外更新 | 粘贴红石块不激活选区外活塞；随后主动触发正常邻居更新，活塞仍能伸出并生成头，验证抑制仅作用于放置流程 |
| REPLACE / BlockEntity | 覆盖状态完全相同的箱子也重建方块实体，旧钻石库存与自定义名称被清除，无掉落；恢复 NBT 不通知选区外比较器 |
| 真实珍珠炮 | 用户原理图导入与充能后，以 90° 旋转跨区块粘贴全部 2,723 个方块，逐块坐标和 BlockState 与原理图旋转结果一致；持久化充能为零且原始结构保持不变 |

实现对照 [Litematica 26.3 的直接粘贴代码](https://github.com/sakura-ryoko/litematica/blob/26.3/src/main/java/fi/dy/masa/litematica/util/SchematicPlacingUtils.java)。早期投影和放置检查中的缺少支撑拒绝、火焰跳过及完成后的邻居/流体更新已移除。蓝图不导入原理图定时 Tick、实体或容器内容；后续正常外部更新、已有定时 Tick、随机 Tick 和方块实体 Tick 仍会生效。珍珠炮发射效果与长时间运行尚未验证。

## 无头伸出活塞兼容性验证 — 2026-10-09

| 验证 | 结果 |
| --- | --- |
| `gradlew.bat -PwithLitematica -PpistonOnly runClientGameTest build` | 成功；25 项 JUnit、27 项服务端 GameTests 全部通过，真实 Litematica 客户端珍珠炮导入与充能回归通过，发布 JAR 与源码 JAR 生成成功 |
| 材料规则 | 六方向普通/黏性活塞在缺头或前方为空气时按一个底座计费；已有活塞头的错误朝向、错误类型和运动中短头仍拒绝，孤立头及移动活塞仍拒绝 |
| 服务端放置 | 普通/黏性活塞以真实材料充能，四档旋转放置后保留伸出状态与朝向；通电底座经过邻居更新后仍保持原状态，不在选区外生成活塞头；原始状态与消耗后的充能正确落盘 |
| 用户原理图 | `pearl_X1012.8_Z1012.8_TNT24-24_BOOST20_v9-zero-axis.litematic` 经真实列表选择、上传、服务端解码及材料分析，完整保留 32×101×24 尺寸及 2,723 个方块；`(31,89,14)` 朝东、`(4,89,23)` 朝南的粘性活塞均保留 `extended=true`，计费与底座总数一致，存档往返一致并成功充能 |
| 发布与静态检查 | 发布 JAR 包含修正后的材料解析器，不包含客户端测试类和 `.litematic` 测试资源；`git diff --check` 通过，测试原理图与用户文件逐字节一致 |

此项早期真实原理图回归验证导入、保存、材料计费与充能；整座建筑放置已在上方粘贴模式回归中补测，发射效果尚未验证。

## 投影更新验证 — 2026-10-09

| 验证 | 结果 |
| --- | --- |
| `gradlew.bat runDatagen` | 成功；生成新增投影、按键、坐标反馈的中英文资源 |
| `gradlew.bat -PprojectionOnly runClientGameTest build` | 成功；24 项 JUnit、26 项服务端 GameTests 全部通过，JAR 与源码 JAR 生成成功；内置投影 UI 回归通过 |
| `gradlew.bat -PwithLitematica runClientGameTest` | 上一轮完整五项客户端回归通过；本轮入口和 UI 调整另以独立投影回归复测 |
| `gradlew.bat -PwithLitematica -PprojectionOnly runClientGameTest` | 最新 UI 流程通过；按 G 打开数据面板后无投影，点击「显示投影」才显示并出现执行按钮；三轴输入、±90° 分档及实际落块一致 |
| UI 操作限制 | 世界中的 R、方向键、PageUp、Enter、Backspace 及 UI 外的移动/执行调用均不改变或执行投影；执行仅由面板按钮发起 |
| 发布 JAR | 包含投影服务端、客户端、Litematica 可选适配器和最新语言资源，不包含测试类 |
| 视觉与静态检查 | 实际游戏截图确认冲突红色标记和禁用确认按钮、无冲突状态、坐标与方向提示正常；中英文键与占位符一致、文档链接有效、Git 差异无空白错误 |

新增服务端测试覆盖包围盒内空格的障碍、扫描预算、缺少支撑、越界仍可投影、右击不显示或放置、未充能或未手持时不能打开面板、SHOW 前不能执行、冲突不能执行、执行时新出现的障碍、旧版本与错误令牌拒绝、取消和断线保留充能。客户端回归使用实际网络和可选 Litematica 接口，验证初始只显示建筑数据、独立投影按钮、临时投影不存档、±90° 四方向坐标与朝向方块状态、工具切换时投影保留、UI 执行后准确落块、放置后的未充能蓝图不能打开新面板，以及取消后的充能和投影清理。

投影截图：[建筑数据面板](screenshots/blueprint-projection-panel.png)、[有冲突与控制面板](screenshots/blueprint-projection-conflict.png)、[无冲突并完成移动和旋转](screenshots/blueprint-projection-ready.png)。未进行最大体积投影或多玩家同时投影的压力测试。

## 首版验证结果 — 2026-10-08

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

单元测试检查分批充能与超额输入、材料完成条件和跨页排序、多方块结构配对、流体计费、容器与告示牌 NBT 清洗、BlockEntity 类型、NBT 深度、旋转、序列化、重复与越界坐标及压缩炸弹。材料注册表回归覆盖植物茎段、竹笋、高海草、气泡柱、霜冰、六方向普通与黏性活塞配对、无头伸出底座计费，以及不匹配或移动中的活塞结构拒绝。

服务端测试检查真实 Tags 的形态与颜色规则、替代材料保留原始 BlockState、流体桶返还、创造电池优先充能且不产生空桶、SAFE 全范围预检、共享 UUID 锁定、旋转放置、进度落盘和每 Tick 预算。容器回归验证 4096 数量及物品组件的存档往返、普通取出与 Shift-click、背包满时余量保留、潜影盒内材料消耗及组件保留、九格返还区与溢出掉落。

客户端原理图及工程台回归：

- `PrefabLitematicaClientGameTest`：通过真实 Litematica 公共接口创建原理图和未启用投影，从列表导入、清理箱子内容、转换蓝图物品并使用材料区电池充能。
- `SwampSchematicClientGameTest`：导入真实沼泽刷怪塔文件，验证尺寸 52×131×37、6,047 个方块、7 格气泡柱、2 个水桶及 1 个灵魂沙需求，并成功充能。
- `PearlSchematicClientGameTest`：导入真实珍珠炮文件，验证尺寸 32×101×24、2,723 个方块、两个边界无头伸出活塞的状态、底座计费、保存及充能，并对整座建筑进行 90° 旋转粘贴和逐块状态比较。
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

尚未进行 500000 方块的长时间性能压测、多客户端压力测试或第三方领地模组组合测试。沼泽刷怪塔已验证整座放置，但未测运行效率；珍珠炮已对照自动 TNT 发射，但未预装玩家珍珠或验证最终传送落点。开始放置后的进程强制终止可能留下部分建筑，充能保持已消耗；行为说明见 [架构与数据安全](ARCHITECTURE.md)。

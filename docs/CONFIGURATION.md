# 服务端配置

首次运行生成 `config/prefablitematica.json`，修改后重启服务端：

```json
{
  "maxBlueprintBlocks": 500000,
  "blocksPlacedPerTick": 4096,
  "placementMode": "SAFE",
  "consumeBlueprintAfterPlacement": false,
  "copyContainerContents": false,
  "allowMaterialSubstitution": true,
  "creativeBatteryEnabled": true,
  "maxDimension": 512,
  "maxUploadBytes": 33554432,
  "maxExpandedBytes": 100663296,
  "maxBlockEntityBytes": 16384,
  "maxConcurrentTasks": 2,
  "allowSurvivalImports": true
}
```

`SAFE` 在修改世界前验证整个包围盒，只接受空气和可替换方块；蓝图目标为下文列出的保留方块时，该格跳过冲突检查。`REPLACE` 允许覆盖普通方块，但仍尊重世界边界、出生点保护和权限回调，不能覆盖不可破坏方块。REPLACE 会清空被替换的容器，避免掉落复制。

两个模式均参考 Litematica 的直接粘贴方式保留原始状态，放置期间抑制回调和邻居更新，完成后不额外调度流体、重力或火焰 Tick。`placementMode` 只控制是否允许覆盖场地；悬空方块可以粘贴，后续正常游戏更新仍会生效。

单人集成服务器安装 Litematica 时直接调用其粘贴入口；专用服务器使用对应实现。每个区块的状态和 NBT 在同一次调用中完成，因此 `blocksPlacedPerTick` 是目标预算，单次完整区块可能超过该数值。原理图自带的定时 Tick 会恢复，预算与旧版严格逐块限制不同。

`consumeBlueprintAfterPlacement=true` 除消耗原物品外，也会持久化停用整个 UUID，其他复制出来的蓝图副本无法继续使用。

为限制稀疏巨型结构的验证开销，**包围盒体积也受 maxBlueprintBlocks 限制**。默认每轴上限 512、总包围盒体积 500000。服务器可降低上限。

`copyContainerContents=true` 被明确拒绝，第一版不提供可复制容器内容的模式。无法识别的特殊方块或非原版流体采用拒绝导入的策略。

以下方块可导入并在投影中显示，但不收取材料、不计入冲突，也不会被蓝图覆盖：基岩、末地传送门框架、末地传送门、末地折跃门、紫水晶母岩、试炼刷怪笼、试炼宝库和普通刷怪笼。放置时保留目标位置当前的方块及方块实体数据。地狱门方块由客户端导入时转换为空气；命令方块及其链式/循环变体、结构方块、拼图方块、屏障、光照方块、移动活塞、未经转换的地狱门及工程台仍被拒绝。

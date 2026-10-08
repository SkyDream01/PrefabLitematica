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

`SAFE` 在修改世界前验证整个包围盒，只接受空气和可替换方块。`REPLACE` 允许覆盖普通方块，但仍尊重世界边界、出生点保护和权限回调，不能覆盖不可破坏方块。REPLACE 会清空被替换的容器，避免掉落复制。

`consumeBlueprintAfterPlacement=true` 除消耗原物品外，也会持久化停用整个 UUID，其他复制出来的蓝图副本无法继续使用。

为限制稀疏巨型结构的验证开销，**包围盒体积也受 maxBlueprintBlocks 限制**。默认每轴上限 512、总包围盒体积 500000。服务器可降低上限。

`copyContainerContents=true` 被明确拒绝，第一版不提供可复制容器内容的模式。无法识别的特殊方块或非原版流体采用拒绝导入的策略。

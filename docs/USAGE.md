<p align="center">
  <img src="assets/prefablitematica-logo.png" alt="PrefabLitematica 方块与蓝图标志" width="96" />
</p>

# 使用指南

适用于 PrefabLitematica 0.1.0。安装环境见[项目首页](../README.md)，材料计费规则见[材料规则](MATERIALS.md)。

## 1. 制作蓝图与导入建筑

1. 合成蓝图工程台：上排为「空、书本、空」，中排为「青金石块、墨囊、红石块」，下排为「空、黑曜石、空」；每种材料各一个，可合成一个工程台。
2. 合成空白蓝图：书本、青金石、红石粉和墨囊各一个，无序合成一个空白蓝图。
3. 将 `.litematic` 文件放入 Litematica 原理图目录。也可从已读入的原理图或投影列表导入；无需先把投影加载到世界。
4. 打开工程台，将空白蓝图放入蓝图槽，点击「载入建筑」，再从可搜索、可分页的列表选择建筑。导入期间保持界面打开且不要更换蓝图槽中的物品。
5. 导入成功后，空白蓝图变为已加载蓝图，并显示名称、尺寸和独立的蓝色图标。已加载仅表示建筑数据已保存，仍须充能到 100% 才能放置。

![在工程台中搜索并选择建筑](screenshots/blueprint-building-list.png)

导入 `.litematic` 文件不会创建、启用或选中世界投影。以下示例展示已导入建筑的尺寸和材料清单：

![沼泽刷怪塔已载入，界面显示建筑尺寸与材料需求](screenshots/blueprint-swamp-mob-farm-imported.png)

## 2. 提交材料并充能

将材料放入左侧 3×3 输入区，再点击「开始充能」。同种物品可在同一格累加，例如先放入 64 个、再放入 64 个，该格会显示 128；每格上限为 4096。普通放入和 Shift-click 均可合并，输入格满时 Shift-click 会尝试转入其他材料格。

材料可分批补齐，未使用或不匹配的材料会保留。也可以直接投入装有材料的潜影盒。详细替代关系和潜影盒返还规则见[材料规则](MATERIALS.md)。

![材料输入格累计显示 128 个物品](screenshots/blueprint-material-input-128.png)

![材料输入格显示 4096 个物品的上限](screenshots/blueprint-material-input-4096.png)

工程台右侧显示物品图标、接受的材料类别、已投入数量和需求数量。缺少材料排在前面；满足需求的材料变灰并排在后面。每次充能后列表重新排序，并回到第一页；排序针对完整清单，不只针对当前页。

![充能后材料清单重新排序并分页](screenshots/blueprint-materials-resorted-after-charge.png)

水桶、岩浆桶和细雪桶使用后会返还空桶。返还优先进入右侧 3×3 容器返还区；若没有空位，则转入背包或掉落在工程台旁。返还区不能手动投入材料。

### 使用创造充能电池

也可以在同一材料输入区放入一个创造蓝图充能电池，再点击充能。电池优先消耗一个，立即满足全部材料需求，不消耗其他材料，也不产生空桶；工程台没有单独的电池槽。

## 3. 取出蓝图并放置

充能达到 100% 后取出蓝图，右击一个方块的表面，将建筑放在该面的相邻格。潜行右击蓝图可依次切换 0°、90°、180°、270° 方向。

![已充满的蓝图与材料返还区](screenshots/blueprint-workbench-charged-zh-cn.png)

默认情况下，建筑放置完成后蓝图物品保留，但共享充能归零；再次放置前需要重新充能。

## 命令与物品

管理员可用以下命令获得工程台、空白蓝图或创造充能电池：

```mcfunction
/give @s prefablitematica:workbench
/give @s prefablitematica:blank_blueprint
/give @s prefablitematica:creative_charge_battery
```

创造充能电池只在创造物品栏中提供，没有合成配方、战利品、交易或世界生成来源。管理员给予生存玩家的电池仍可使用。空白蓝图可合成，也可从创造栏获得；已加载蓝图只会在成功导入后生成。

空白蓝图的物品 ID 为 `prefablitematica:blank_blueprint`；已加载蓝图的物品 ID 为 `prefablitematica:blueprint`。

## 界面与方块外观

工程台采用原版容器风格。界面面板、槽位和进度条使用 Minecraft 资源，可随资源包替换；工程台方块使用项目自有的 16×16 贴图。

![工程台、蓝图方块与原版工作台的游戏内外观](screenshots/blueprint-workbench-vanilla-style.png)

不同步骤的工程台界面截图：

![空工程台界面](screenshots/blueprint-workbench-empty.png)

![建筑载入后的工程台界面](screenshots/blueprint-workbench-imported.png)

![工程台的 3×3 材料输入与容器返还区](screenshots/blueprint-3x3-returns-material-icons.png)

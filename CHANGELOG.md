# 更新日志

## 0.1.0 — 2026-10-08

PrefabLitematica 首个版本，适用于 Minecraft 26.3 / Fabric。

- 从 Litematica 原理图文件、已读入原理图或投影导入建筑。
- 服务端验证、保存建筑并计算材料需求；充能后按 Tick 放置，保留原始方块状态。
- 蓝图工程台提供 3×3 材料输入和容器返还，支持每槽 4096 个材料、潜影盒充能和创造电池。
- 提供 45 类材料替代规则、水与岩浆计费、四方向旋转及中英文界面。
- 包含单元测试、服务端 GameTests、真实 Litematica 客户端回归和资源生成工具。
- 统一使用 `prefablitematica` 命名空间及 `prefablitematica-fabric-26.3-0.1.0.jar` 发布包命名。
- 项目原创代码采用 AGPLv3（`AGPL-3.0-only`）开源。

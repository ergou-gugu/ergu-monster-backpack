# 王二咕的怪物背包 — 设计文档 v0

> **Ergu's Monster Backpack** · Minecraft 26.2 / Fabric
> 本文档是设计与实现的**唯一依据**。
> 所有"原版数值"与"技术机制"必须标注源码出处(`refs/mc-26.2/`、`refs/fabric-api-src/`)。
> **未标注出处的数值 = 本项目自定的设计值。**

---

## 〇、命名与标识

| 项 | 值 |
| --- | --- |
| 中文名(模组) | **王二咕的怪物背包** |
| 英文名(模组) | **Ergu's Monster Backpack** |
| modid | **`ergu_monster_backpack`** |
| 包名 | `com.ergu.monsterbackpack` |
| 主类 | `ErguMonsterBackpack` / `ErguMonsterBackpackClient` |
| 工程根 | `D:\MC\dev\monster_backpack` |
| 游戏版本 | Minecraft 26.2 |
| 加载器 | Fabric(Loader 0.19.5 / Fabric API 0.161.0+26.2) |
| Java | 25 |
| 许可 | MIT |

## 一、定位

> **待定。**

核心玩法尚未确定。确定后在此写一句话定位 —— 参照姊妹作
「王二咕的怪物工具」的写法:一句话说清它是什么、玩家为什么想用它。

## 二、核心循环

**待定。**

## 三、状态模型

**待定。**

## 四、待定事项

1. **核心玩法**:养成型(吃掉怪物掉落物扩容 / 解锁功能)?还是按材质分级的普通扩容背包?
2. **容量怎么表达**:原版 `DataComponents.CONTAINER` 组件(参照怪物工具的材料存储),
   还是自定义 `Container` + 菜单?
3. **要不要界面**:需要打开背包界面就得走双端容器链路(按键 → C2S 包 → `player.openMenu` →
   `AbstractContainerMenu` → `Screen`),参考技能 `fabric-262-screen-container`。
4. **与怪物工具的关系**:共用命名风格与素材来源,但不共用代码(两个独立工程)。

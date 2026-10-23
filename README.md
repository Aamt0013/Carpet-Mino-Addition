# Carpet Mino Addition

[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-3C8527)](https://github.com/Aamt0013/Carpet-Mino-Addition/releases)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

一个 [Carpet](https://github.com/gnembon/fabric-carpet) 扩展，为 Minecraft **26.2** 提供几条生存向规则。

## 规则

五条规则都在 `Mino` 分类下，用 `/carpet <规则名> <值>` 设置。

| 规则 | 默认 | 作用 |
| --- | --- | --- |
| `mobDropMultiplier` | 空 | 生物掉落倍率，可单独覆盖某个生物 |
| `wanderingTraderSellsIce` | `false` | 流浪商人有概率卖冰 |
| `villagerTradePro` | `false` | 村民的装备有概率带满级附魔，农民有概率卖附魔金苹果 |
| `wanderingTraderDespawnSeconds` | `0` | 流浪商人停留秒数，`-1` 为永不消失 |
| `friendlyIronGolem` | `false` | 非玩家建造的铁傀儡不攻击玩家 |
| `sturdySnowGolem` | `false` | 雪傀儡不会因为温暖群系或淋雨受伤 |
| `mendingMax` | `false` | 自动用经验栏修补身上所有带经验修补的装备 |

### mobDropMultiplier

控制生物掉落物数量。

| 写法 | 效果 |
| --- | --- |
| `3` | 所有生物 3 倍 |
| `cow=10` | 只有牛 10 倍 |
| `3,cow=10` | 全局 3 倍，牛单独 10 倍 |
| 留空 | 原版 |

倍率范围 1-1999。生物 id 可省略 `minecraft:` 前缀，模组生物要写全命名空间。
掉落物会自动合并堆叠，倍率拉高也不会刷出一地散落物品。

不想记语法也可以用命令：

```mcfunction
/mino drop cow 10
/mino drop all 3
/mino drop list
/mino drop clear
```

### wanderingTraderSellsIce

原版流浪商人只卖浮冰和蓝冰，没有普通冰。开启后把冰加入同一个候选池，和它们一起随机抽取，
约 **12.5%** 概率出现，1 绿宝石换 8 个。

### villagerTradePro

村民出售的装备有概率换成满级附魔组合，另给农民加一条附魔金苹果交易。

| 职业 | 物品 | 单件概率 |
| --- | --- | --- |
| 工具商 | 钻石斧 / 铲 / 镐 | 15% |
| 武器匠 | 钻石斧 / 剑 | 10% |
| 盔甲商 | 钻石护腿 / 靴 / 盔 / 胸甲 | 8% |
| 制箭师 | 弓（4 级）、弩（5 级） | 12% |
| 农民 | 附魔金苹果，64 绿宝石 | 约 6.7% |

满级附魔是 2-3 条互不冲突的组合，75% 出 3 条。
不会添加经验修补——它不在原版村民交易的附魔标签里。

> 26.2 的村民只生成**当前等级**那一组交易，不累加低等级。
> 所以 `/summon ... level:5` 的村民只有 5 级商品；自然升到满级的村民才有全部等级的交易。

### wanderingTraderDespawnSeconds

原版流浪商人 40 分钟后消失。设成 `-1` 就永不消失，也可以指定秒数（1-604800）。
只对新生成的商人生效，已经在世界里的商人仍按原定时间消失。

### friendlyIronGolem

原版只有**玩家建造**的铁傀儡不打玩家，村庄自然生成的那批仍会攻击村庄好感度低的玩家。
开启后所有非玩家建造的铁傀儡都变成友善的。

只影响目标选择，它们照常攻击僵尸、骷髅这些敌对生物。

### sturdySnowGolem

原版雪傀儡有两个致死点：

- **温暖群系融化**：进沙漠、恶地这类群系会持续掉血
- **淋雨或泡水**：下雨天在外面走、掉进水里都会持续掉血

开启后这两种环境伤害都不再生效。雪傀儡照常铺雪、投掷雪球，
被玩家或生物打、掉进岩浆、摔落等照常受伤。

### mendingMax

原版经验修补有两个限制：

- **只修一件**：捡到经验球时从所有带经验修补的装备里**随机挑一件**修，
  手里拿着快坏的剑，经验却可能补到靴子上
- **只花球的经验**：球的经验不够修满就只能修一半，剩下的进了经验栏不会再回流

开启后每 tick 自动结算：主手、副手、头盔、胸甲、护腿、靴子六处，
所有耐久未满且带经验修补的装备一次性修满，经验从经验栏扣，
扣到修满或经验见底为止。

只动经验栏、不碰经验球，所以不会和经验球自身的修补逻辑重复扣经验。

## 安装

1. 装好 **Fabric Loader** 和 **Fabric API**
2. 装好 **Carpet**
3. 把 `carpet-mino-addition-<版本>.jar` 放进 `mods/` 目录

## 依赖

| 组件 | 版本 |
| --- | --- |
| Minecraft | 26.2 |
| Fabric Loader | >= 0.19.5 |
| Java | >= 25 |
| Carpet | >= 26.2 |
| Fabric API | 任意版本 |

## 从源码构建

需要 JDK 25。

```powershell
.\gradlew.bat build
```

产物在 `build/libs/`。开发相关的说明见 [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)。

## 许可证

MIT，见 [LICENSE](LICENSE)。

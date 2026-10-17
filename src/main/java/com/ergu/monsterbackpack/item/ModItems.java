package com.ergu.monsterbackpack.item;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.Equippable;

import com.ergu.monsterbackpack.ErguMonsterBackpack;
import com.ergu.monsterbackpack.block.ModBlocks;
import com.ergu.monsterbackpack.component.ModComponents;
import com.ergu.monsterbackpack.growth.BackpackGrowth;

/**
 * 物品注册。
 *
 * <p>26.2 的写法:{@code Registry.register(Registry, ResourceKey, T)} ——
 * 物品必须先有一个 {@link ResourceKey}(<b>且要 {@code setId} 写进 Properties</b>,见工作区约定坑 1),
 * 再连同实例写进 {@link BuiltInRegistries#ITEM}。
 *
 * <p>注册名必须全小写;显示名走语言文件({@code item.<ns>.<path>})。
 */
public final class ModItems {

	/** 怪物背包(物品形态):{@code ergu_monster_backpack:monster_backpack}。 */
	public static final ResourceKey<Item> MONSTER_BACKPACK_KEY = ResourceKey.create(
		Registries.ITEM,
		ErguMonsterBackpack.id("monster_backpack")
	);

	/** 仪式匕首:{@code ergu_monster_backpack:ritual_dagger}。纯道具,只服务于仪式。 */
	public static final ResourceKey<Item> RITUAL_DAGGER_KEY = ResourceKey.create(
		Registries.ITEM,
		ErguMonsterBackpack.id("ritual_dagger")
	);

	/**
	 * 🆕 背包"打不烂"的伤害类型标签(见 DESIGN.md §五)。
	 *
	 * <p>内容见 `data/ergu_monster_backpack/tags/damage_type/backpack_indestructible.json`:
	 * <pre>
	 *   #minecraft:is_fire        ← 火 / 熔岩 / 岩浆块 / 火焰弹
	 *   #minecraft:is_explosion   ← 爆炸(含床 / 重生锚 / 烟花)
	 *   minecraft:cactus          ← 仙人掌
	 * </pre>
	 *
	 * <p>⚠️ **它防不住"掉虚空"** —— 那不是伤害,是 {@code Entity} 掉到 {@code y < minY} 被删。
	 * 那条归「护主」处理(用户 2026-10-15 定),**不要往这个标签里加**。
	 *
	 * <p>⚠️ **声明位置在 {@link #MONSTER_BACKPACK} 【之前】** ——
	 * 虽然 {@code delayedComponent} 的求值本来就是延迟的(不会真的读到 null),
	 * 但把"依赖的东西"写在"用它的东西"**之前**可以省掉一次怀疑。
	 */
	public static final TagKey<DamageType> INDESTRUCTIBLE = TagKey.create(
		Registries.DAMAGE_TYPE,
		ErguMonsterBackpack.id("backpack_indestructible"));

	public static final Item MONSTER_BACKPACK = Registry.register(
		BuiltInRegistries.ITEM,
		MONSTER_BACKPACK_KEY,
		new MonsterBackpackItem(ModBlocks.MONSTER_BACKPACK, backpackProperties())
	);

	/**
	 * 仪式匕首 —— <b>纯道具</b>:
	 * 不加攻击力、不加耐久、不加任何能力。它的作用完全写在<b>背包方块的 {@code useItemOn} 里</b>
	 * (见 DESIGN.md §六),所以这里就是一个最普通的 {@link Item}。
	 */
	public static final Item RITUAL_DAGGER = Registry.register(
		BuiltInRegistries.ITEM,
		RITUAL_DAGGER_KEY,
		new Item(new Item.Properties().setId(RITUAL_DAGGER_KEY))
	);

	private static Item.Properties backpackProperties() {
		return new Item.Properties()
			// ⚠️ 必须最先设:Item 构造器第一句就读它(坑 1)
			.setId(MONSTER_BACKPACK_KEY)
			// 背包不能叠加,否则两个背包叠一起内容无从归属(潜影盒同此)
			.stacksTo(1)
			// 穿在胸甲栏。⚠️ damageOnHurt 默认是 true,必须显式关掉 ——
			// 否则背包会"因为挨打而掉耐久",而本 mod 的设计是【没有耐久】
			.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST)
				.setDamageOnHurt(false)
				.build())
			// CONTAINER 组件自己实现了 TooltipProvider,会自动往 tooltip 塞"内含物品"。
			// 本 mod 的界面里已经能看内容了,这里藏掉(坑 17)
			.component(DataComponents.TOOLTIP_DISPLAY,
				TooltipDisplay.DEFAULT.withHidden(DataComponents.CONTAINER, true))
			// ⚠️ 默认 = 【原型态】(0 格,还没醒)。
			//
			// 这是刻意的:配方合成出来的就是物品的默认组件 —— 而"合成出来的原型背包"
			// 本来就该是【没醒的】,要靠唤醒仪式才会变成 3 格。
			// 创造栏里的"初始背包"是单独设了 3 的(见 ModItemGroups)。
			.component(ModComponents.UNLOCKED_SLOTS, 0)
			.component(ModComponents.UNLOCK_START, -1)
			.component(ModComponents.SATIETY, 0)
			// 阶段:独立存的(⚠️ 不从格数算 —— 每行格子数会变,见 ModComponents.STAGE)
			.component(ModComponents.STAGE, 1)
			// 🆕 护主的保护冷却:存"上次触发是游戏时间几点",-1 = 没在冷却(§十二 F1)
			.component(ModComponents.GUARDIAN_COOLDOWN, -1)
			// 🆕 天生不会被销毁 —— 火 / 熔岩 / 岩浆块 / 爆炸 / 仙人掌(见 DESIGN.md §五)
			//
			// ⚠️ 用户 2026-10-15 的要求:"误操作后挫败感太强"。
			// 判据:【不可逆的损失】做成基础属性,【可逆的】(死亡散落)才做成技能树能力。
			//
			// ⚠️ 必须用 delayedComponent —— 标签要等注册表冻结后才能查(坑 11)。
			// 这正是原版 `Item.Properties.fireResistant()`(Item.java:425)的写法,
			// 只是把 `is_fire` 换成了我们自己的标签(多包了爆炸和仙人掌)。
			.delayedComponent(DataComponents.DAMAGE_RESISTANT,
				ctx -> new DamageResistant(ctx.getOrThrow(INDESTRUCTIBLE)));
	}

	/**
	 * 唤醒成功后送出的格数(见 DESIGN.md §六)。
	 *
	 * <p>第 0 行的前 3 格是"出生"白送的,所以 {@code unlocked_slots: 0 → 3}。
	 */
	public static final int AWAKENED_SLOTS = 3;

	private ModItems() {
	}

	/** 空方法,唯一作用是触发本类的静态初始化。 */
	public static void bootstrap() {
	}
}

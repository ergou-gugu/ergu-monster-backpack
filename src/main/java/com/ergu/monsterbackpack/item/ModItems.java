package com.ergu.monsterbackpack.item;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
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
			// ⚠️ V0.1 临时:创造栏直接给【初始背包】= 3 格。
			// 正式流程是「原型背包(unlocked_slots = 0)→ 唤醒仪式 → 3 格」,
			// 唤醒仪式安排在 V0.2,所以现在先给一个已唤醒的。
			.component(ModComponents.UNLOCKED_SLOTS, V0_1_STARTING_SLOTS)
			.component(ModComponents.UNLOCK_START, -1)
			// 饱食度:初始给满,让玩家一上来能长一会儿(设计见 §六 的 F15)
			.component(ModComponents.SATIETY, BackpackGrowth.MAX_SATIETY)
			// 阶段:独立存的(⚠️ 不从格数算 —— 每行格子数会变,见 ModComponents.STAGE)
			.component(ModComponents.STAGE, 1);
	}

	/**
	 * V0.1 创造栏给出的初始格数。
	 *
	 * <p>按 DESIGN.md §六:唤醒成功后 {@code unlocked_slots: 0 → 3}
	 * (第 0 行的前 3 格是"出生"白送的)。
	 */
	private static final int V0_1_STARTING_SLOTS = 3;

	private ModItems() {
	}

	/** 空方法,唯一作用是触发本类的静态初始化。 */
	public static void bootstrap() {
	}
}

package com.ergu.monsterbackpack.item;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import com.ergu.monsterbackpack.ErguMonsterBackpack;
import com.ergu.monsterbackpack.component.ModComponents;
import com.ergu.monsterbackpack.growth.BackpackGrowth;

/**
 * 创造模式栏位注册。
 *
 * <p><b>必须用 {@link FabricCreativeModeTab#builder()}</b>,不要用原版的
 * {@code CreativeModeTab.builder(Row, int)}。原因:Fabric 的分页机制会取消原版的位置重复校验,
 * 并对注册表里的**每一个**栏位做一次到 {@code FabricCreativeModeTabImpl} 的强转;
 * 原版 builder 造出来的实例没实现该接口,打开创造栏时会直接抛 {@code ClassCastException}(坑 2)。
 *
 * <p>另外 {@code title(...)} <b>必填</b>,不设会抛 {@code IllegalStateException}。
 */
public final class ModItemGroups {

	/** 栏位 ID:{@code ergu_monster_backpack:main}。 */
	public static final ResourceKey<CreativeModeTab> MAIN_KEY = ResourceKey.create(
		Registries.CREATIVE_MODE_TAB,
		Identifier.fromNamespaceAndPath(ErguMonsterBackpack.MOD_ID, "main")
	);

	/** 「王二咕的怪物背包」创造栏。 */
	public static final CreativeModeTab MAIN = Registry.register(
		BuiltInRegistries.CREATIVE_MODE_TAB,
		MAIN_KEY,
		FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.ergu_monster_backpack.main"))
			.icon(() -> new ItemStack(ModItems.MONSTER_BACKPACK))
			.displayItems((parameters, output) -> {
				// 初始背包 = 已经醒过来的(3 格)—— 想跳过仪式的人直接用这个
				output.accept(awakenedBackpack());
				// 原型背包 = 没醒的(0 格)= 【物品的默认】—— 也正是合成出来的那一个
				output.accept(ModItems.MONSTER_BACKPACK);
				// 🆕 完全体 = 84 格全开 + 阶段 4(开发期用来直接验证后段功能)
				output.accept(fullyGrownBackpack());
				output.accept(ModItems.RITUAL_DAGGER);
				// 🆕 挂饰(暂时无合成,先放创造栏验证效果)
				output.accept(ModCharmItems.LUCK_CHARM);
				output.accept(ModCharmItems.MAGNET);
			})
			.build()
	);

	/**
	 * 初始背包 —— 已经醒过来的(3 格)。
	 *
	 * <p>物品的<b>默认</b>是原型态(0 格,见 {@code ModItems});所以"醒着的"要单独造一个。
	 */
	private static ItemStack awakenedBackpack() {
		final ItemStack stack = new ItemStack(ModItems.MONSTER_BACKPACK);
		stack.set(ModComponents.UNLOCKED_SLOTS, ModItems.AWAKENED_SLOTS);
		stack.set(ModComponents.UNLOCK_START, -1);
		stack.set(ModComponents.STAGE, 1);
		stack.set(ModComponents.SATIETY, BackpackGrowth.MAX_SATIETY);
		return stack;
	}

	/**
	 * 「完全体」背包 —— 开发期用来<b>直接验证后段功能</b>(挂饰槽、高阶段的东西),
	 * 免得每次都等它长十分钟。
	 *
	 * <p>⚠️ <b>正式发布前要决定它的去留</b> —— 创造栏里放一个"什么都开好了"的背包,
	 * 会让玩家跳过整个养成过程。
	 */
	private static ItemStack fullyGrownBackpack() {
		final ItemStack stack = new ItemStack(ModItems.MONSTER_BACKPACK);
		stack.set(ModComponents.UNLOCKED_SLOTS,
			com.ergu.monsterbackpack.growth.GrowthRules.TOTAL_SLOTS);
		stack.set(ModComponents.UNLOCK_START, -1);
		stack.set(ModComponents.STAGE, 4);
		stack.set(ModComponents.SATIETY, BackpackGrowth.MAX_SATIETY);
		stack.set(DataComponents.CUSTOM_NAME,
			Component.translatable("item.ergu_monster_backpack.monster_backpack.creative"));
		return stack;
	}

	private ModItemGroups() {
	}

	/** 空方法,唯一作用是触发本类的静态初始化。 */
	public static void bootstrap() {
	}
}

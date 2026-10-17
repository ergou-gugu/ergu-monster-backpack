package com.ergu.monsterbackpack.growth;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.ergu.monsterbackpack.component.ModComponents;

/**
 * 「它的胃」—— 把粗矿消化成锭(见 DESIGN.md §十二 F16)。
 *
 * <h2>和熔炉的差别</h2>
 * <ul>
 *   <li><b>没有输出格</b> —— 消化完<b>直接塞进背包</b>(用户定)</li>
 *   <li><b>速度随阶段变快</b>:阶段 2 = 14 秒、阶段 3 = 9 秒、阶段 4 = <b>5 秒</b>
 *       (原版熔炉是 10 秒,所以最终形态是它的 2 倍)</li>
 *   <li>要<b>烧饱食度</b> —— 它不是免费的,吃的是"它自己的饭"</li>
 * </ul>
 *
 * <h2>存法</h2>
 * 输入存在 {@code stomach} 组件、计时存在 {@code stomach_start}(存起点不存进度)。
 * <b>关掉界面它照样消化</b>,因为它不属于菜单(坑 19)。
 */
public final class StomachSmelter {

	/** 一秒钟多少 tick。 */
	private static final int SECOND = 20;

	/** 消化一份要耗多少饱食度。 */
	private static final int SATIETY_COST = 1;

	private StomachSmelter() {
	}

	/**
	 * 这一份东西要多少 tick 才能消化完 —— **按已解锁的最高一级砂囊节点**。
	 *
	 * <h2>⚠️ 2026-10-15 改了依据:从 {@code stage} 换成技能树节点</h2>
	 * 以前是 `ticksFor(stage)`,而 `stage` 是**成长阶段** ——
	 * 那个设计是"技能树还没做"时的占位。
	 *
	 * <p>现在 `stage` 只负责**限制"什么时候能解锁"**(见 {@code SkillNodes.Node.requiredStage}),
	 * <b>不再直接决定效果</b>。所以玩家解锁了「砂囊 II」,速度就真的变快 ——
	 * 而不是"长到阶段 3 就自动变快"。
	 *
	 * <p>⚠️ **取最高一级而不是逐级叠加** —— 三个节点是独立解锁的,
	 * 玩家可能直接解锁 III(如果材料够、阶段够)。所以从高往低问。
	 *
	 * @return 一个都没解锁时返回最慢那档(那种情况下玩家根本不该用到砂囊;
	 *         但**不能抛异常** —— 砂囊侧签是开局就有的,而节点要阶段 2 才解锁)
	 */
	public static int ticksFor(final ItemStack backpack) {
		if (com.ergu.monsterbackpack.skill.SkillUnlock.isUnlocked(
				backpack, com.ergu.monsterbackpack.skill.SkillNodes.GIZZARD_3)) {
			return 5 * SECOND;
		}
		if (com.ergu.monsterbackpack.skill.SkillUnlock.isUnlocked(
				backpack, com.ergu.monsterbackpack.skill.SkillNodes.GIZZARD_2)) {
			return 9 * SECOND;
		}
		// 没解锁 I 也走这档 —— 和 I 一样慢。
		// ⚠️ 这里【刻意不返回 0 或抛异常】:砂囊侧签开局就在,玩家随时能往里放东西,
		// 而"砂囊 I"要阶段 2 才解锁。返回最慢那档 = "它现在还很不熟练",语义是对的。
		return 14 * SECOND;
	}

	/** 这个东西能不能进胃(只有粗矿可以)。 */
	public static boolean isSmeltable(final ItemStack stack) {
		return resultOf(stack) != ItemStack.EMPTY;
	}

	/** 粗矿 → 锭。不是粗矿就返回空栈。 */
	public static ItemStack resultOf(final ItemStack raw) {
		if (raw.is(Items.RAW_IRON)) {
			return new ItemStack(Items.IRON_INGOT);
		}
		if (raw.is(Items.RAW_COPPER)) {
			return new ItemStack(Items.COPPER_INGOT);
		}
		if (raw.is(Items.RAW_GOLD)) {
			return new ItemStack(Items.GOLD_INGOT);
		}
		return ItemStack.EMPTY;
	}

	/**
	 * 跑一拍胃。由每 5 tick 的检查调用(物品形态与方块形态共用)。
	 *
	 * @param backpack 背包物品(方法内部可能改它的组件)
	 * @param gameTime 游戏时间
	 */
	public static void tick(final ItemStack backpack, final long gameTime) {
		final ItemStack input = readInput(backpack);

		if (input.isEmpty()) {
			// 胃里空的 → 顺便把计时清掉,省得下次放东西时"接着上次算"
			if (backpack.getOrDefault(ModComponents.STOMACH_START, -1) >= 0) {
				backpack.set(ModComponents.STOMACH_START, -1);
			}
			return;
		}

		final int start = backpack.getOrDefault(ModComponents.STOMACH_START, -1);

		if (start < 0) {
			// 刚放进去 → 现在开始消化
			backpack.set(ModComponents.STOMACH_START, (int) gameTime);
			return;
		}

		// ⚠️ 速度按【技能树节点】算,不按 stage(2026-10-15 改)
		if (gameTime - start < ticksFor(backpack)) {
			return;      // 还没好
		}

		// ---- 消化完成 ----

		final ItemStack result = resultOf(input);

		if (result.isEmpty()) {
			// 万一里面被塞了不能烧的东西(理论上进不来),吐出来
			backpack.set(ModComponents.STOMACH, net.minecraft.world.item.component.ItemContainerContents.EMPTY);
			backpack.set(ModComponents.STOMACH_START, -1);
			return;
		}

		// 产物【直接进背包】。⚠️ 背包塞不下就先不动 —— 免得东西凭空消失。
		if (!BackpackGrowth.tryInsert(backpack, result)) {
			return;
		}

		// 扣掉吃的那一份
		input.shrink(1);

		if (input.isEmpty()) {
			backpack.set(ModComponents.STOMACH, net.minecraft.world.item.component.ItemContainerContents.EMPTY);
		} else {
			backpack.set(ModComponents.STOMACH,
				net.minecraft.world.item.component.ItemContainerContents.fromItems(java.util.List.of(input)));
		}

		backpack.set(ModComponents.STOMACH_START, -1);

		// 消化要烧饱食度
		final int satiety = backpack.getOrDefault(ModComponents.SATIETY, 0);
		backpack.set(ModComponents.SATIETY, Math.max(0, satiety - SATIETY_COST));
	}

	/** 读出胃里那一样东西(没有就是空栈)。 */
	public static ItemStack readInput(final ItemStack backpack) {
		final var contents = backpack.get(ModComponents.STOMACH);

		if (contents == null) {
			return ItemStack.EMPTY;
		}

		final var list = net.minecraft.core.NonNullList.withSize(1, ItemStack.EMPTY);
		contents.copyInto(list);
		return list.get(0);
	}

	/** 往胃里放一样东西(覆盖)。 */
	public static void writeInput(final ItemStack backpack, final ItemStack stack) {
		if (stack.isEmpty()) {
			backpack.set(ModComponents.STOMACH,
				net.minecraft.world.item.component.ItemContainerContents.EMPTY);
		} else {
			backpack.set(ModComponents.STOMACH,
				net.minecraft.world.item.component.ItemContainerContents.fromItems(
					java.util.List.of(stack)));
		}
		// 换了东西 → 计时重来
		backpack.set(ModComponents.STOMACH_START, -1);
	}

	/** 这一份食物值多少(拿掉,只给 {@code FeedSlot} 用)。 */
	public static boolean isFood(final ItemStack stack) {
		return stack.has(DataComponents.FOOD);
	}
}

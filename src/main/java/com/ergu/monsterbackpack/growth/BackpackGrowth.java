package com.ergu.monsterbackpack.growth;

import net.minecraft.world.item.ItemStack;

import com.ergu.monsterbackpack.component.ModComponents;
import com.ergu.monsterbackpack.item.ModItems;

/**
 * 生长逻辑的<b>唯一实现</b> —— 物品形态与方块形态共用。
 *
 * <h2>为什么抽出来</h2>
 * 背包可能待在两个地方:玩家身上(物品形态)或世界里(方块形态)。
 * 两边的**计时规则完全一样**,只有"怎么找到这个背包"不同。
 * 所以规则放在这里,两边各自负责找到 {@code ItemStack} 然后调 {@link #tick}。
 *
 * <h2>⚠️ 绝不每 tick 写组件</h2>
 * 见 DESIGN.md 与工作区约定坑 20。这里的做法是:
 * <ul>
 *   <li>调用方<b>每 5 tick 才来一次</b></li>
 *   <li>只在<b>状态真的变了</b>时才 {@code set}</li>
 * </ul>
 */
public final class BackpackGrowth {

	/** 饱食度上限。 */
	public static final int MAX_SATIETY = 20;

	/**
	 * 生长期间,多少 tick 的等待折算成 1 点饱食度。
	 *
	 * <p>实现上不是"按时间滴扣",而是<b>长开一格时按它花掉的时间一次性结算</b> ——
	 * 效果等价,但省掉一个额外的计时器组件(少一个组件就少一份同步负担)。
	 */
	private static final int TICKS_PER_SATIETY = 20 * 20;

	private BackpackGrowth() {
	}

	/**
	 * 给一个背包跑一拍生长。不是背包物品、或者已经长满,就直接返回。
	 *
	 * @param backpack 背包物品(方法内部可能修改它的组件)
	 * @param gameTime 当前游戏时间
	 */
	public static void tick(final ItemStack backpack, final long gameTime) {
		if (backpack.getItem() != ModItems.MONSTER_BACKPACK) {
			return;
		}

		final int unlocked = backpack.getOrDefault(ModComponents.UNLOCKED_SLOTS, 0);

		// ⚠️⚠️ 原型态(0 格)= 【还没醒】,不参与生长。
		//
		// 漏掉这一句会出一个很迷惑的 bug:格 0/1/2 在规则里是"出生位"(ticksFor 返回 0),
		// 于是原型背包拿在手里几 tick 之内就会【自己解锁到 3 格】——
		// 表现为"明明是原型背包,右键却能打开",而且"怎么刚好是 3"。
		// 唤醒只能由仪式来做,不能被生长逻辑顺手做掉。
		if (unlocked <= 0) {
			return;
		}

		if (unlocked >= GrowthRules.TOTAL_SLOTS) {
			return;      // 已经长满了
		}

		final int satiety = backpack.getOrDefault(ModComponents.SATIETY, 0);

		// 🆕 饿了 → 【暂停生长,但不重置进度】(用户定的 A + C)。
		//
		// 做法:把"这一格的计时起点"往后推 —— 等价于"饿着的这段时间不算数"。
		// 于是玩家喂饱之后,那一格会从暂停处接着长,而不是从头再来。
		if (satiety <= 0) {
			backpack.set(ModComponents.UNLOCK_START, (int) gameTime);
			return;
		}

		final int start = backpack.getOrDefault(ModComponents.UNLOCK_START, -1);

		if (start < 0) {
			// 这一格还没开始 → 现在开始(写一次,之后不再重复写)
			backpack.set(ModComponents.UNLOCK_START, (int) gameTime);
			return;
		}

		final int needed = GrowthRules.ticksFor(unlocked);

		if (needed <= 0 || gameTime - start >= needed) {
			// 到点了 —— 也可能这一格是"出生 / 突破位"(needed = 0,直接长开)
			final int next = unlocked + 1;
			backpack.set(ModComponents.UNLOCKED_SLOTS, next);
			backpack.set(ModComponents.UNLOCK_START, -1);

			// 结算这一格烧掉的饱食度。出生位是白送的,不收费。
			final int cost = needed <= 0 ? 0 : Math.max(1, needed / TICKS_PER_SATIETY);
			if (cost > 0) {
				backpack.set(ModComponents.SATIETY, Math.max(0, satiety - cost));
			}

			// 🆕 跨进阶段首行 → 推进 stage 组件。
			//
			// ⚠️ 用 COLS 算,不写死格数 —— 每行格子数是随时会变的(用户 2026-10-15 指出),
			// 写成"第 19 格"那种判断,列数一改就全错。
			//
			// 而且这里是【写一次组件】,不是"每次读的时候现算" ——
			// 以后突破仪式做好了,只是换个人来写这个值,读的地方一行都不用改。
			final int row = next / GrowthRules.COLS;
			if (next % GrowthRules.COLS == 0
				&& row > 0
				&& GrowthRules.isStageHeadRow(row)) {
				backpack.set(ModComponents.STAGE, GrowthRules.stageOfRow(row));
			}
		}
	}

	/** 喂食:往饱食度里加,封顶 {@link #MAX_SATIETY}。返回实际加了多少。 */
	public static int feed(final ItemStack backpack, final int amount) {
		final int before = backpack.getOrDefault(ModComponents.SATIETY, 0);
		final int after = Math.min(MAX_SATIETY, before + amount);
		if (after != before) {
			backpack.set(ModComponents.SATIETY, after);
		}
		return after - before;
	}
}

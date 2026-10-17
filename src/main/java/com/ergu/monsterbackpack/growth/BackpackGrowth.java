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

		// 🆕 「它的胃」独立于格子生长:即使在长满之后、或者饿着的时候,
		// 只要胃里有东西它就继续消化(它吃的是自己那份饱食度)。
		StomachSmelter.tick(backpack, gameTime);

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

	/**
	 * 往背包内容里塞一样东西 —— 先并进同类堆,再找第一个空位。
	 *
	 * <p>用途:「它的胃」消化完的锭<b>直接进背包</b>(§十二 F16 没有输出格)。
	 *
	 * <p>⚠️ <b>只往已解锁的格子里塞</b> —— 否则东西会跑进"还没长开"的格子里,
	 * 玩家既看不到也拿不到。
	 *
	 * @return 塞进去了吗。<b>false = 背包满了</b> —— 调用方这时候<b>不该消耗来源</b>,
	 *         否则东西就凭空没了。
	 */
	public static boolean tryInsert(final ItemStack backpack, final ItemStack stack) {
		if (stack.isEmpty()) {
			return true;
		}

		// ⚠️⚠️ 容器互斥必须在这里也拦一次 —— 【不能只靠 BackpackSlot.mayPlace】。
		//
		// 那道检查是【GUI 点击路径】的钩子(moveItemStackTo / clicked 会问它),
		// 而本方法走的是【服务端逻辑路径】(磁力吸取、砂囊产物),
		// 那条路径上原版根本不会替我们问 mayPlace。
		//
		// 踩点(2026-10-15,用户实测"怪物背包还能互相存"):
		// 磁力把地上另一个怪物背包吸了进来 —— 它绕过了 mayPlace,直接落进空格。
		//
		// ⇒ 规则:**凡是"往背包里塞东西"的入口,都要自己问一次 canFitInsideContainerItems**。
		if (!stack.getItem().canFitInsideContainerItems()) {
			return false;
		}

		final int unlocked = backpack.getOrDefault(ModComponents.UNLOCKED_SLOTS, 0);

		if (unlocked <= 0) {
			return false;      // 还没醒的背包,没有格子可放
		}

		final net.minecraft.core.NonNullList<ItemStack> items =
			net.minecraft.core.NonNullList.withSize(GrowthRules.TOTAL_SLOTS, ItemStack.EMPTY);

		final var contents = backpack.get(net.minecraft.core.component.DataComponents.CONTAINER);
		if (contents != null) {
			contents.copyInto(items);
		}

		// ① 先并进同类堆
		for (int i = 0; i < unlocked && !stack.isEmpty(); i++) {
			final ItemStack slot = items.get(i);

			if (slot.isEmpty() || !ItemStack.isSameItemSameComponents(slot, stack)) {
				continue;
			}

			final int room = slot.getMaxStackSize() - slot.getCount();
			if (room > 0) {
				final int move = Math.min(room, stack.getCount());
				slot.grow(move);
				stack.shrink(move);
			}
		}

		// ② 再找空位
		for (int i = 0; i < unlocked && !stack.isEmpty(); i++) {
			if (items.get(i).isEmpty()) {
				items.set(i, stack.copy());
				stack.setCount(0);
			}
		}

		if (!stack.isEmpty()) {
			return false;      // 背包满了
		}

		backpack.set(net.minecraft.core.component.DataComponents.CONTAINER,
			net.minecraft.world.item.component.ItemContainerContents.fromItems(items));
		return true;
	}

	// ────────────────────────────────────────────────────────────────
	// ⚠️ 【已移除】purgeVoid —— "清掉背包里已经在白名单上的东西"
	// ────────────────────────────────────────────────────────────────
	//
	// 2026-10-15 用户明确要求去掉:
	//
	//   原话:"我要的虚空升级是【捡起来的】被销毁,背包里的东西不应该被同步销毁"
	//
	// 也就是说虚空只作用于「**新进入背包的物品**」,对「**已经躺在背包里的**」一律不碰。
	// (参考 mod 有两种做法:旅行者背包不清已存的,精妙背包清 —— **用户要的是前者**。)
	//
	// 移除这个方法带来三个好处:
	//   ① 玩家**手动把东西放进背包不会被吃掉** —— "放进去"永远是安全动作
	//   ② 不再有"每 5 tick 扫全包"的开销
	//   ③ 不再有"绕过容器改组件 ⇒ 界面不刷新"那条坑(坑 26 的配套)
	//
	// ⚠️ 现在虚空的判定点**只有一处**:MagnetCollector 在 tryInsert 之前问 VoidRules.shouldVoid。
	// **以后如果加了别的自动拾取入口(漏斗 / 管道 / 其他 mod 的转移)**,要在【那些入口】各自补一次,
	// **不要退回"每 tick 扫全包"那种做法** —— 那正是用户否掉的那个语义。
}

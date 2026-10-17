package com.ergu.monsterbackpack.skill;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.ergu.monsterbackpack.component.ModComponents;
import com.ergu.monsterbackpack.storage.BackpackStorage;

/**
 * 技能树的**判定与解锁** —— 服务端逻辑。
 *
 * <p>规则见 {@code DESIGN.md} §六「解锁规则」。这里分成两层:
 * <ul>
 *   <li>{@link #check} —— <b>只判断,不改任何东西</b>。界面用它来上色(能不能点)。</li>
 *   <li>{@link #unlock} —— <b>真的要解锁了就调它</b>,扣材料 + 写等级。</li>
 * </ul>
 * 刻意分开:界面<b>每帧</b>都要知道"能不能解锁",而那是纯读取;把判断和写入混在一起,
 * 迟早有人在一个只该"看看"的地方把材料扣了。
 *
 * <h2>⚠️ 材料来源:背包 + 玩家物品栏【两边都算】</h2>
 * 用户 2026-10-15 定。扣除顺序是<b>先扣背包、再扣玩家</b> ——
 * 让玩家身上那点东西尽量留着应急(背包才是"它的胃",理应先吃背包里的)。
 *
 * <h2>⚠️ 为什么不做"材料预存"</h2>
 * 材料<b>当场扣、当场解锁</b>,没有中间状态 ⇒
 * 关界面 / 掉线 / 崩溃都<b>不会</b>出现"交了材料没解锁"。这是本次设计里最省心的一个选择。
 */
public final class SkillUnlock {

	/** 一次判定的结果。 */
	public enum Status {
		/** 已满级(单级节点就是"已解锁")。 */
		MAXED,
		/** 阶段不够 —— 玩家还不该看到它能点。 */
		STAGE_LOCKED,
		/** 🆕 **前置节点还没解锁** —— 见 {@code Node.requiredNode}。 */
		PREREQ_LOCKED,
		/** 阶段够,但材料不够。 */
		NEED_MATERIALS,
		/** ✅ 现在就能点。 */
		READY
	}

	/**
	 * 一条材料这一趟"实际能凑到多少" —— 判断和扣除共用同一个统计口径。
	 *
	 * <p>⚠️ 必须共用:如果判断时数一遍、扣除时又按另一套数,就会出现
	 * "界面说能点,点了却扣不满"这种最难查的不一致。
	 *
	 * <p>⚠️ 只有 {@link #unlock} 用它(那里要"先扣背包、不够再扣玩家"的明细)。
	 * 判断走的是 {@link MaterialCounter},不需要这份明细。
	 */
	private record Availability(Item item, int required, int inBackpack, int inPlayer) {
	}

	// ────────────────────────────────────────────────────────────────
	// 等级读写
	// ────────────────────────────────────────────────────────────────

	/** 全部节点的等级。⚠️ 返回的是**拷贝**,改它不会影响背包。 */
	public static Map<String, Integer> readLevels(final ItemStack backpack) {
		final Map<String, Integer> stored = backpack.get(ModComponents.SKILLS);
		return stored == null ? Map.of() : Map.copyOf(stored);
	}

	/** 某个节点的等级;没解锁 = 0。 */
	public static int level(final ItemStack backpack, final SkillNodes.Node node) {
		final Integer value = readLevels(backpack).get(node.id());
		return value == null ? 0 : value;
	}

	/** 某个节点是不是已解锁(maxLevel 目前都是 1,所以"等级 >= 1"就是解锁)。 */
	public static boolean isUnlocked(final ItemStack backpack, final SkillNodes.Node node) {
		return level(backpack, node) >= node.maxLevel();
	}

	/**
	 * 写等级。
	 *
	 * <p>⚠️ 组件是 {@code Map},也是**不可变**的 —— 要拷一份改完再 {@code set} 回去,
	 * {@code backpack.get(SKILLS).put(...)} 会直接抛 {@code UnsupportedOperationException}
	 * (我们读出来的是 {@code Map.copyOf})。
	 */
	private static void setLevel(final ItemStack backpack, final SkillNodes.Node node, final int newLevel) {
		final Map<String, Integer> next = new HashMap<>(readLevels(backpack));
		next.put(node.id(), newLevel);
		backpack.set(ModComponents.SKILLS, Map.copyOf(next));
	}

	// ────────────────────────────────────────────────────────────────
	// 判定
	// ────────────────────────────────────────────────────────────────

	/**
	 * 能不能解锁 —— **纯读取,不改任何东西**。
	 *
	 * <p>⚠️ 它会<b>真的去数背包和玩家物品栏</b>(每条材料数两处)。
	 * 界面在<b>每帧画 17 个节点</b>时会调到它,所以那条路要走
	 * {@link #check(int, ItemStack, Player, SkillNodes.Node, MaterialCounter)} 传一个缓存过的计数器,
	 * 不要直接用这个重载(否则每帧把整个背包数 17 遍)。
	 *
	 * @param stage 背包当前阶段(1~4;⚠️ 不从格数算,见 §四 {@code stage} 组件)
	 */
	public static Status check(
		final int stage,
		final ItemStack backpack,
		final Player player,
		final SkillNodes.Node node
	) {
		return check(stage, backpack, player, node,
			item -> BackpackStorage.countItem(backpack, item) + countInPlayer(player, item));
	}

	/**
	 * 「某材料一共能凑到多少」的取数方式 —— 抽出来是为了让界面能传一个**帧内缓存**的版本。
	 *
	 * <p>⚠️ 为什么值得抽:判断逻辑和取数方式分开之后,
	 * "数几遍"就变成了调用方的选择,而"够不够"的规则仍然只有这一处实现 ——
	 * 不会出现"界面算一套、解锁算另一套"的不一致。
	 */
	@FunctionalInterface
	public interface MaterialCounter {
		int count(Item item);
	}

	/**
	 * 能不能解锁(用指定的取数方式)。
	 *
	 * @param counter 材料计数。⚠️ 判断和扣除用**同一个** counter 是没问题的 ——
	 *                但扣除那条路<b>必须用实时值**(见 {@link #unlock}),不能用界面缓存。
	 */
	public static Status check(
		final int stage,
		final ItemStack backpack,
		final Player player,
		final SkillNodes.Node node,
		final MaterialCounter counter
	) {
		if (isUnlocked(backpack, node)) {
			return Status.MAXED;
		}
		if (stage < node.requiredStage()) {
			return Status.STAGE_LOCKED;
		}
		// 🆕 前置节点 —— ⚠️ 放在阶段之后、材料之前。
		//
		// 顺序的理由:玩家看到的提示应该是"**最靠前**的那个不满足条件"。
		// 如果前置和材料都不够,说"材料不够"会让人以为凑齐材料就能点 —— 那是误导。
		if (!prerequisiteMet(backpack, node)) {
			return Status.PREREQ_LOCKED;
		}
		// 🔹 自动解锁的节点:阶段一到就"可以点"(其实不用点,见 syncAutomatic)
		if (node.isAutomatic()) {
			return Status.READY;
		}

		for (final SkillNodes.Cost cost : node.costs()) {
			if (counter.count(cost.item()) < cost.count()) {
				return Status.NEED_MATERIALS;
			}
		}
		return Status.READY;
	}

	/**
	 * 🆕 某个节点的**前置**满足了没(没前置 ⇒ 恒为 true)。
	 *
	 * <p>⚠️ 查不到那个 id 时**当作满足**(而不是"永远锁死") ——
	 * 因为查不到只可能是我写错了 id,那时把节点锁死会让玩家**无从排查**
	 * ("为什么这个点不动?");放开至少是可用的,而且 `SkillNodes.BY_ID` 那边
	 * 有重复 id 检查、`byId` 有查找,写错了在测试时就会暴露。
	 *
	 * <p>⚠️ **只看一级,不递归** —— 因为 `guardian_2` 要求 `guardian_1`,
	 * 而 `guardian_1` 自己没有前置。**递归是多余的**,链条本来就是直的。
	 */
	public static boolean prerequisiteMet(final ItemStack backpack, final SkillNodes.Node node) {
		if (!node.hasPrerequisite()) {
			return true;
		}

		return SkillNodes.byId(node.requiredNode())
			.map(required -> isUnlocked(backpack, required))
			.orElse(true);
	}

	/** 把每条材料的"需要多少 / 背包有多少 / 玩家有多少"列出来。 */
	private static java.util.List<Availability> survey(
		final ItemStack backpack,
		final Player player,
		final SkillNodes.Node node
	) {
		final java.util.List<Availability> list = new java.util.ArrayList<>(node.costs().size());
		for (final SkillNodes.Cost cost : node.costs()) {
			list.add(new Availability(
				cost.item(),
				cost.count(),
				BackpackStorage.countItem(backpack, cost.item()),
				countInPlayer(player, cost.item())));
		}
		return list;
	}

	/** 玩家物品栏(含快捷栏,共 36 格)里某种物品一共多少个。 */
	public static int countInPlayer(final Player player, final Item item) {
		final Inventory inventory = player.getInventory();
		int total = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			final ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && stack.getItem() == item) {
				total += stack.getCount();
			}
		}
		return total;
	}

	// ────────────────────────────────────────────────────────────────
	// 解锁
	// ────────────────────────────────────────────────────────────────

	/**
	 * 真的解锁 —— **会扣材料、会写组件**。
	 *
	 * <p>⚠️ 内部<b>重算一遍</b> {@link #check},不信任调用方传来的状态 ——
	 * 因为客户端那个 {@code Status} 是它<b>自己算的</b>,可能已经过期(玩家在点击前一瞬间把材料丢了)。
	 * <b>永远不要用客户端的判断来决定服务端要不要扣东西。</b>
	 *
	 * @return 实际成功了没有(失败 = 什么都没改)
	 */
	public static boolean unlock(
		final int stage,
		final ItemStack backpack,
		final Player player,
		final SkillNodes.Node node
	) {
		if (check(stage, backpack, player, node) != Status.READY) {
			return false;
		}

		// ⚠️ 先把每条材料"应扣多少"定下来,再统一扣 ——
		// 边算边扣会导致"第一条扣完了才发现第二条不够",材料就白扣了。
		// 这里 check() 已经保证了每条都够,所以下面只负责扣准。
		for (final Availability availability : survey(backpack, player, node)) {
			int remaining = availability.required();

			// ① 先扣背包
			final int fromBackpack = BackpackStorage.consume(backpack, availability.item(), remaining);
			remaining -= fromBackpack;

			// ② 不够的再从玩家物品栏扣
			if (remaining > 0) {
				consumeFromPlayer(player, availability.item(), remaining);
			}
		}

		setLevel(backpack, node, node.maxLevel());
		return true;
	}

	/** 从玩家物品栏里扣掉至多 {@code amount} 个,返回实际扣掉的。 */
	private static int consumeFromPlayer(final Player player, final Item item, final int amount) {
		final Inventory inventory = player.getInventory();
		int taken = 0;

		for (int i = 0; i < inventory.getContainerSize() && taken < amount; i++) {
			final ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty() || stack.getItem() != item) {
				continue;
			}

			final int want = amount - taken;
			final int take = Math.min(want, stack.getCount());
			stack.shrink(take);
			taken += take;

			// ⚠️ 改成 EMPTY 而不是留空栈 —— 同 BackpackStorage.consume 的理由
			if (stack.isEmpty()) {
				inventory.setItem(i, ItemStack.EMPTY);
			}
		}

		if (taken > 0) {
			// ⚠️ 让客户端看到扣了东西(不然要等下一次库存同步才刷新)
			player.getInventory().setChanged();
		}
		return taken;
	}

	// ────────────────────────────────────────────────────────────────
	// 🔹 自动解锁
	// ────────────────────────────────────────────────────────────────

	/**
	 * 把「🔹 到时自动解锁」的节点补上 —— 阶段够了就白送,不花材料。
	 *
	 * <p>设计用意(§六):它们<b>起引导作用</b> —— 让玩家在树里看到"以后会有什么"。
	 * 所以"到时候自动点亮"是<b>刻意的</b>,不是省事。
	 *
	 * <p>⚠️ 调用时机:<b>打开界面时 + 阶段变化时</b>。不需要每 tick ——
	 * 阶段只在突破时变,而突破本来就有一个明确的入口。
	 *
	 * @return 这次点亮了几个(>0 说明组件被改过,调用方可能要存档)
	 */
	public static int syncAutomatic(final int stage, final ItemStack backpack) {
		int changed = 0;

		for (final SkillNodes.Node node : SkillNodes.ALL) {
			if (!node.isAutomatic() || isUnlocked(backpack, node)) {
				continue;
			}
			if (stage < node.requiredStage()) {
				continue;
			}
			// 🆕 前置 —— ⚠️ 自动解锁的节点也要查。
			//
			// 否则阶段一跳,`charm_slot_2` 会被一起点亮,
			// 而"**一个阶段解锁一个挂饰槽**"的节奏就没了(§六 的原话)。
			//
			// ⚠️ 走的是同一个 `prerequisiteMet` —— 判定口径只有一处。
			if (!prerequisiteMet(backpack, node)) {
				continue;
			}
			setLevel(backpack, node, node.maxLevel());
			changed++;
		}

		return changed;
	}

	private SkillUnlock() {
	}
}

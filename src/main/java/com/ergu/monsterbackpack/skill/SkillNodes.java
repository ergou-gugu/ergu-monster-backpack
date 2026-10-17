package com.ergu.monsterbackpack.skill;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 技能树的**节点定义表** —— 全 mod 唯一的一份。
 *
 * <p>设计见 {@code DESIGN.md} §六「技能树」。这里只有**数据**,没有逻辑:
 * 判定在 {@link SkillRules},扣除在 {@link SkillUnlock},界面在 {@code BackpackScreen}。
 *
 * <h2>⚠️⚠️ 为什么节点用【字符串 id】而不是枚举序号</h2>
 * 等级存在物品组件 {@code skills} 里,而那是个 {@code Map<String,Integer>}(见
 * {@code ModComponents.SKILLS})。**id 一旦发布就不能改** ——
 * 改了等于玩家所有该节点的等级<b>静默归零</b>(组件里那个键再也对不上)。
 *
 * <p>所以本类里每个 id 都是一串**下划线小写**,和 {@code DESIGN.md} §六 的表逐行对应。
 * <b>增删节点是安全的</b>(其他节点的键不受影响);<b>改 id 是不安全的</b>。
 *
 * <h2>⚠️ 超大堆叠(F5)刻意不在这里</h2>
 * 它要先过 **V4 技术验证**(能不能突破原版 {@code getMaxStackSize()} 的层层截断),
 * 验证不过就整个不做。**不要为了"三列好看"把它硬塞进来** ——
 * 一个永远无法解锁的节点比缺一个节点更糟。
 */
public final class SkillNodes {

	/** 三条分支。颜色在 {@code BackpackScreen} 里按这个上色。 */
	public enum Branch {
		/** 守卫线(红)—— 它会保护你。 */
		GUARDIAN,
		/** 收纳线(蓝)—— 它替你收拾。 */
		STORAGE,
		/** 本能线(绿)—— 它的天性。 */
		INSTINCT
	}

	/**
	 * 一条材料需求。
	 *
	 * <p>⚠️ 只记**种类 + 数量**,不含 NBT / 附魔 / 耐久 ——
	 * 判定用 {@code ItemStack.getItem() == item},和虚空过滤(§六 F17)保持同一套语义。
	 *
	 * <p>⚠️ 判定逻辑<b>不在这里</b> —— 在 {@code SkillUnlock}。这里只有数据。
	 * (我一开始写了个 {@code matches(ItemStack)},后来发现判定统一走
	 *  {@code MaterialCounter} 那条路更清楚 —— 统计口径只有一处实现,不会两边不一致。)
	 */
	public record Cost(Item item, int count) {
	}

	/**
	 * 一个技能节点。
	 *
	 * @param id            组件里的键。⚠️ **发布后不可改**
	 * @param branch        属于哪条分支(决定颜色与列)
	 * @param requiredStage 从哪个阶段起可解锁(1~4;阶段不够时点不动)
	 * @param maxLevel      最高等级。**1 = 单级节点**;I/II/III 这种拆成三个独立节点的各为 1
	 * @param costs         **升一级**需要的材料;空表 = 🔹 到时自动解锁(不花材料)
	 * @param requiredNode  🆕 **必须先解锁的那个节点的 id**;{@code null} = 没有前置
	 */
	public record Node(
		String id,
		Branch branch,
		int requiredStage,
		int maxLevel,
		List<Cost> costs,
		String requiredNode
	) {
		/**
		 * 没有前置的节点(绝大多数)。
		 *
		 * <p>⚠️ 这个便捷构造器**是刻意留的** —— 不然加了前置字段之后
		 * 现有 17 个节点全要改一遍,而它们**本来就不需要前置**。
		 */
		public Node(
			final String id,
			final Branch branch,
			final int requiredStage,
			final int maxLevel,
			final List<Cost> costs
		) {
			this(id, branch, requiredStage, maxLevel, costs, null);
		}

		/** 🔹 到时自动解锁的节点 —— 不花材料,只受阶段限制。 */
		public boolean isAutomatic() {
			return this.costs.isEmpty();
		}

		/** 🆕 有没有前置。 */
		public boolean hasPrerequisite() {
			return this.requiredNode != null && !this.requiredNode.isBlank();
		}

		/** 语言键:节点名走 lang 文件,键由 id 拼出,见 {@code lang/*.json}。 */
		public String nameKey() {
			return "gui.ergu_monster_backpack.skill." + this.id;
		}
	}

	// ────────────────────────────────────────────────────────────────
	// 节点表 —— 与 DESIGN.md §六 的表格逐行对应,改这里也要改那里
	// ────────────────────────────────────────────────────────────────

	/** 守卫线(红)。 */
	//
	// ⚠️ 2026-10-15 用户实测反馈:「护主二在护主一没解锁前就能开,**不符合设计初衷**」
	// ⇒ 加上前置。**I/II/III 是同一个能力的递进,必须先有低的那一级。**
	public static final Node GUARDIAN_1 = new Node("guardian_1", Branch.GUARDIAN, 2, 1,
		List.of(new Cost(Items.IRON_BLOCK, 4), new Cost(Items.GOLD_BLOCK, 4)));
	public static final Node GUARDIAN_2 = new Node("guardian_2", Branch.GUARDIAN, 3, 1,
		List.of(new Cost(Items.DIAMOND, 6), new Cost(Items.PRISMARINE_SHARD, 6)), "guardian_1");
	public static final Node GUARDIAN_3 = new Node("guardian_3", Branch.GUARDIAN, 4, 1,
		List.of(new Cost(Items.ECHO_SHARD, 8), new Cost(Items.SHULKER_SHELL, 8)), "guardian_2");
	// ⚠️ DANGER_SENSE / ENDGAME 【刻意没有前置】—— 它们不是 I/II/III 的递进,
	// 而是"另一条能力"。要不要挂前置(比如 ENDGAME 要求前面全解锁)还没定,留给用户。
	public static final Node DANGER_SENSE = new Node("danger_sense", Branch.GUARDIAN, 3, 1,
		List.of(new Cost(Items.SPIDER_EYE, 8), new Cost(Items.ENDER_PEARL, 8)));
	public static final Node ENDGAME = new Node("endgame", Branch.GUARDIAN, 5, 1,
		List.of(new Cost(Items.NETHER_STAR, 1), new Cost(Items.ECHO_SHARD, 8)));

	/** 收纳线(蓝)。 */
	public static final Node SORTING = new Node("sorting", Branch.STORAGE, 2, 1,
		List.of(new Cost(Items.BOOKSHELF, 2), new Cost(Items.REDSTONE, 16)));
	public static final Node VOIDING_1 = new Node("voiding_1", Branch.STORAGE, 2, 1,
		List.of(new Cost(Items.COAL, 4), new Cost(Items.ENDER_PEARL, 4)));
	public static final Node VOIDING_2 = new Node("voiding_2", Branch.STORAGE, 3, 1,
		List.of(new Cost(Items.ECHO_SHARD, 6)), "voiding_1");

	/** 本能线(绿)。 */
	public static final Node AUTO_EAT = new Node("auto_eat", Branch.INSTINCT, 1, 1,
		List.of(new Cost(Items.WHEAT, 8), new Cost(Items.ROTTEN_FLESH, 8)));
	// ⚠️ §六 写的是「自动进食(阶段1)→ 投喂玩家(阶段2)/ 大黑白名单(阶段3)」
	// —— 后两个是【并列】的升级,都以前者为前置。
	public static final Node FEED_PLAYER = new Node("feed_player", Branch.INSTINCT, 2, 1,
		List.of(new Cost(Items.GOLDEN_APPLE, 1)), "auto_eat");
	public static final Node BIG_LIST = new Node("big_list", Branch.INSTINCT, 3, 1,
		List.of(new Cost(Items.SHULKER_SHELL, 4), new Cost(Items.PRISMARINE_SHARD, 4)), "auto_eat");
	public static final Node GIZZARD_1 = new Node("gizzard_1", Branch.INSTINCT, 2, 1,
		List.of(new Cost(Items.COAL, 4), new Cost(Items.RAW_IRON, 4)));
	public static final Node GIZZARD_2 = new Node("gizzard_2", Branch.INSTINCT, 3, 1,
		List.of(new Cost(Items.MAGMA_CREAM, 6), new Cost(Items.IRON_BLOCK, 6)), "gizzard_1");
	public static final Node GIZZARD_3 = new Node("gizzard_3", Branch.INSTINCT, 4, 1,
		List.of(new Cost(Items.BLAZE_ROD, 8), new Cost(Items.ANCIENT_DEBRIS, 8)), "gizzard_2");

	// 🔹 到时自动解锁的 —— 起"路线图"作用,不花材料
	//
	// ⚠️ 它们归【本能线】而不是单开一列 —— 依据是 DESIGN.md §六「三条分支」那张表的
	// 本能线一行明确写着「磁石 F2 · 喂食器 F3 · 砂囊 F16 · **挂饰组合 F6**」。
	// (§六 下面那张节点表把它们的分支写成「挂饰」,但那【不是】三条分支之一 —— 以分支表为准。)
	//
	// ⚠️ 自动解锁的节点**也要前置** —— 否则阶段一跳就直接送第 2 个槽,
	// "一个阶段解锁一个"的节奏就没了(§六 的原话)。
	public static final Node CHARM_SLOT_1 = new Node("charm_slot_1", Branch.INSTINCT, 2, 1, List.of());
	public static final Node CHARM_SLOT_2 = new Node("charm_slot_2", Branch.INSTINCT, 3, 1, List.of(), "charm_slot_1");
	public static final Node CHARM_COMBO = new Node("charm_combo", Branch.INSTINCT, 3, 1, List.of(), "charm_slot_1");

	/**
	 * 全部节点 —— **顺序就是界面上【从上到下】的顺序**。
	 *
	 * <p>⚠️ 加节点时<b>直接往对应分支的位置插</b>,不要全塞在末尾 ——
	 * 界面按本表的顺序画,顺序乱了玩家看到的成长路线就乱了。
	 */
	public static final List<Node> ALL = List.of(
		// 守卫线:护主 → 危险感知 → 终局
		GUARDIAN_1, GUARDIAN_2, GUARDIAN_3, DANGER_SENSE, ENDGAME,
		// 收纳线
		VOIDING_1, VOIDING_2, SORTING,
		// 本能线:进食 → 砂囊 → 挂饰(🔹 自动解锁的排最后)
		AUTO_EAT, FEED_PLAYER, BIG_LIST, GIZZARD_1, GIZZARD_2, GIZZARD_3,
		CHARM_SLOT_1, CHARM_SLOT_2, CHARM_COMBO
	);

	/** id → 节点。⚠️ {@code Map.copyOf} 是**不可变**的,别想着往里塞。 */
	private static final Map<String, Node> BY_ID = buildIndex();

	private static Map<String, Node> buildIndex() {
		final java.util.Map<String, Node> map = new java.util.HashMap<>();
		for (final Node node : ALL) {
			final Node previous = map.put(node.id(), node);
			if (previous != null) {
				// ⚠️ 重复 id 是【致命】的:组件里的键只能对应一个节点,
				// 而且这种错误在运行时不报错,只是"某个节点永远解锁不了"。
				// 所以这里直接抛,启动就能发现。
				throw new IllegalStateException("重复的技能节点 id: " + node.id());
			}
		}
		return Map.copyOf(map);
	}

	/** 按 id 找节点;找不到返回空(⚠️ 网络包里传来的 id 是**不可信**的,必须走这条)。 */
	public static Optional<Node> byId(final String id) {
		return Optional.ofNullable(BY_ID.get(id));
	}

	/** 某个分支的全部节点,按 {@link #ALL} 里的顺序。 */
	public static List<Node> ofBranch(final Branch branch) {
		return ALL.stream().filter(n -> n.branch() == branch).toList();
	}

	private SkillNodes() {
	}
}

package com.ergu.monsterbackpack.growth;

/**
 * 成长规则 —— <b>从格子索引推导「这一格长开要多久」</b>。
 *
 * <h2>7 行 = 4 个阶段(见 DESIGN.md §六)</h2>
 * <pre>
 *   偶数行(0/2/4/6)= 阶段首行:前 3 格是「出生 / 突破位」,后 6 格是【快速生长】(甜头)
 *   奇数行(1/3/5)  = 9 格【慢速生长】
 * </pre>
 * 所以"这一格属于哪一类"是<b>推导出来的,不需要存</b>。
 *
 * <h2>⚠️ V0.1 的简化</h2>
 * 三次突破的**正式仪式**还没做,所以"突破位"(每阶段首行的前 3 格)
 * 在 V0.1 里<b>直接白送</b>(返回 0 tick)。
 * 唤醒仪式同理 —— 创造栏给的背包已经是从 3 格开始了。
 */
public final class GrowthRules {

	/** 总格数:7 行 × 12 列 = 84(2026-10-15 用户实测后从 63 上调,见 §七)。 */
	public static final int TOTAL_SLOTS = 84;

	public static final int ROWS = 7;
	public static final int COLS = 12;

	/** 一秒 = 20 tick。 */
	private static final int SECOND = 20;

	/**
	 * 第 {@code index} 格需要多少 tick 才长开。
	 *
	 * @return tick 数;<b>0 = 立即解锁</b>(出生 / 突破位)
	 */
	public static int ticksFor(final int index) {
		if (index < 0 || index >= TOTAL_SLOTS) {
			return 0;
		}

		final int row = index / COLS;
		final int col = index % COLS;

		if (row % 2 == 0) {
			// 阶段首行:前 3 格 = 出生 / 突破位 → V0.1 直接给
			if (col < 3) {
				return 0;
			}
			// 后 6 格 = 快速生长
			return switch (row) {
				case 0 -> 5 * SECOND;
				case 2 -> 10 * SECOND;
				default -> 15 * SECOND;      // row 4 / 6
			};
		}

		// 奇数行:9 格慢速生长(时长待实机调,见 DESIGN.md §十三 的临时决定)
		return switch (row) {
			case 1 -> 20 * SECOND;
			case 3 -> 30 * SECOND;
			default -> 40 * SECOND;          // row 5
		};
	}

	/** 这一格是不是"出生 / 突破位"(即不需等待的格子)。 */
	public static boolean isFreeSlot(final int index) {
		return ticksFor(index) <= 0;
	}

	/**
	 * 第 {@code row} 行是不是<b>阶段首行</b>。
	 *
	 * <p>规则:阶段首行是<b>偶数行</b>(行 0 / 2 / 4 / 6)—— 也就是"7 行 = 4 阶段"那条。
	 * 用行号判断而不是格数,所以<b>列数改了也不受影响</b>。
	 */
	public static boolean isStageHeadRow(final int row) {
		return row % 2 == 0;
	}

	/**
	 * 行 {@code row} 属于第几阶段(1 起)。
	 *
	 * <p>阶段首行 0/2/4/6 → 阶段 1/2/3/4。
	 * ⚠️ 这里算的是"这一行<b>属于</b>哪个阶段",用于判断<b>什么时候该推进 {@code stage} 组件</b>;
	 * <b>不要</b>拿它去替代那个组件(列数一变就全错 —— 见 {@code ModComponents.STAGE})。
	 */
	public static int stageOfRow(final int row) {
		return row / 2 + 1;
	}

	private GrowthRules() {
	}
}

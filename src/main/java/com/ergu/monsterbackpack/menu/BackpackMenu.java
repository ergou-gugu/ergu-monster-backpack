package com.ergu.monsterbackpack.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import com.ergu.monsterbackpack.block.entity.MonsterBackpackBlockEntity;
import com.ergu.monsterbackpack.component.ModComponents;
import com.ergu.monsterbackpack.item.ModItems;

/**
 * 背包界面(物品形态 + 方块形态共用一个)的容器。
 *
 * <h2>布局</h2>
 * <pre>
 *   7 行 × 12 列 = 84 格(背包内容)   ← 贴左边
 *   右边 6 列宽                      ← 预留给挂饰槽与后续功能
 *   + 玩家 3 行主背包 + 1 行快捷栏
 * </pre>
 *
 * <h2>⚠️ 为什么需要 {@link ContainerData}</h2>
 * 物品形态的格数靠<b>原版库存同步</b>就能到客户端;
 * 但<b>方块形态的方块实体不会自动同步内容</b> —— 客户端那边那份永远是刚放下时的样子。
 *
 * <p>所以格数与计时额外走一条菜单数据通道:{@code addDataSlots(...)}
 * (原版用它同步熔炉的燃烧进度,机制完全一样)。
 * 服务端读真实值,客户端收到后<b>写回自己那份物品</b>,
 * 于是界面继续安心地从物品读,不必区分两种形态。
 */
public class BackpackMenu extends AbstractContainerMenu {

	// ---------------------------------------------------------------- 面板几何
	/**
	 * 面板宽:<b>18 列宽</b> ≈ 340 px(2026-10-15 用户实测后定,见 §七)。
	 *
	 * <p>背包占左边 12 列(216 px),右边留 <b>约 116 px</b> 给挂饰槽与后续额外功能。
	 * 逻辑宽 480 px(1920×1080 + GUI scale 4)下占 71%,放得下。
	 */
	public static final int PANEL_WIDTH = 340;

	/** 面板高:7 行背包 + 玩家物品栏(与原版公式 {@code 114 + 行数×18} 一致)。 */
	public static final int PANEL_HEIGHT = 240;

	/** 背包格区。<b>右移</b>给左侧的侧签条让位。 */
	public static final int GRID_X = 80;
	public static final int GRID_Y = 18;
	public static final int GRID_ROWS = 7;
	public static final int GRID_COLS = 12;

	/** 背包格总数。 */
	public static final int GRID_SLOTS = GRID_ROWS * GRID_COLS;

	/** 玩家物品栏的标签行 y(照原版 {@code imageHeight - 94})。 */
	public static final int INVENTORY_LABEL_Y = PANEL_HEIGHT - 94;

	/**
	 * 玩家物品栏那 9 列在面板内<b>居中</b>。
	 *
	 * <p>它不属于背包区,所以不跟 {@link #GRID_X} 一起靠左 ——
	 * 原版也是这么算的({@code (imageWidth - 9 * 18) / 2})。
	 */
	private static final int INVENTORY_X = (PANEL_WIDTH - 9 * 18) / 2;
	private static final int INVENTORY_MAIN_Y = PANEL_HEIGHT - 82;
	private static final int INVENTORY_HOTBAR_Y = PANEL_HEIGHT - 24;

	// ---------------------------------------------------------------- 侧签
	/**
	 * 侧签条 —— <b>贴面板左边缘</b>(用户 2026-10-15 定,参考精妙背包的版面)。
	 *
	 * <p>点开之后内容<b>向左展开到面板外</b>(见 {@link #POPUP_WIDTH}),
	 * 所以 {@code BackpackScreen} 会把整个面板<b>右移</b>给弹窗腾地方。
	 */
	public static final int TAB_STRIP_X = 2;

	/** 侧签按钮的高度(紧凑,照精妙背包那种一列小按钮)。 */
	public static final int TAB_BUTTON_HEIGHT = 18;

	/** 侧签条宽度(竖排的那一列)。 */
	public static final int TAB_STRIP_WIDTH = 36;

	/** 侧签条的起点 y 与每个签的高度。 */
	public static final int TAB_TOP = 22;
	public static final int TAB_HEIGHT = 22;

	/**
	 * 侧签按钮上的短标签。
	 *
	 * <p>按钮宽 36px,放得下两个字,所以短标签<b>本身就是完整的名字</b>。
	 */
	public static final String[] TAB_SHORT_KEYS = {
		"gui.ergu_monster_backpack.tab.feed.short",
		"gui.ergu_monster_backpack.tab.charm.short",
		"gui.ergu_monster_backpack.tab.gizzard.short",
		"gui.ergu_monster_backpack.tab.void.short",
	};

	/**
	 * 悬停时浮出的<b>说明</b>(不是重复名字 —— 名字按钮上已经写着了)。
	 *
	 * <p>2026-10-15 用户实测反馈:"浮出全名那个没法测,短标签就显示完了" ——
	 * 说明浮出内容跟按钮重复是没意义的,所以改成<b>这个功能是干什么的</b>。
	 */
	public static final String[] TAB_HINT_KEYS = {
		"gui.ergu_monster_backpack.tab.feed.hint",
		"gui.ergu_monster_backpack.tab.charm.hint",
		"gui.ergu_monster_backpack.tab.gizzard.hint",
		"gui.ergu_monster_backpack.tab.void.hint",
	};

	/** 侧签弹窗的宽度 —— 它在面板<b>左侧外面</b>,所以坐标是负的。 */
	public static final int POPUP_WIDTH = 104;

	/** 弹窗的垂直起点(相对面板顶)。 */
	public static final int POPUP_TOP = 18;

	/**
	 * 弹窗的高度 —— <b>按签不同,而且虚空还要再按等级</b>。
	 *
	 * <h2>⚠️ 为什么不做成"统一用最大的那个高度"</h2>
	 * 试过,不好看:<b>喂食签只有一个格子</b>,却要拖着一个 190px 高的空盒子,
	 * 底下 100px 全是空灰 —— 看起来像"界面没画完"。
	 *
	 * <p>所以高度跟着内容走。
	 *
	 * <h2>⚠️ 虚空为什么还要参数</h2>
	 * 它的高度取决于<b>样本槽排了几行</b>,而那是随等级变的
	 * (I 级 3×3 → 3 行;II 级 5×5 → 5 行)。见 {@link #voidRows(int)}。
	 */
	public static final int POPUP_HEIGHT_FEED = 110;
	public static final int POPUP_HEIGHT_CHARM = 110;
	public static final int POPUP_HEIGHT_STOMACH = 110;

	/**
	 * 虚空弹窗高度 = 标题(34) + 网格(行数 × 18) + 说明两行 + 按钮 + 底边。
	 *
	 * @param rows 样本槽的行数(3 或 5)
	 */
	public static int popupHeightVoid(final int rows) {
		return voidTipLine1Y(rows) + 22;
	}

	/**
	 * 某一个签的弹窗高度。
	 *
	 * @param tab      当前打开的签
	 * @param voidRows 虚空样本槽的行数(其它签用不到;见 {@link #voidRows(int)})
	 */
	public static int popupHeight(final int tab, final int voidRows) {
		return tab == TAB_VOID ? popupHeightVoid(voidRows) : POPUP_HEIGHT_FEED;
	}

	// ---------------------------------------------------------------- 虚空的纵向排布
	//
	// ⚠️ 底下这几段【全部由行数推出来】,不能再写成固定常量 ——
	// I 级 3 行、II 级 5 行,任何固定值都必然有一档是错的。
	//
	// 排布(自上而下):标题 22 → 网格 34 → 说明两行 → 按钮 → 底边
	// 弹窗里纵向只有 100 多像素,每一段都得算,不能凭感觉放。
	// (踩过:第一版把按钮放 150、说明放 144,结果说明第二行压住了按钮。)

	/** 样本网格第一行的 y。 */
	public static final int VOID_SLOT_Y = 34;

	/** 说明第一行的 y(网格下面留 4px)。 */
	public static int voidTipLine1Y(final int rows) {
		return VOID_SLOT_Y + rows * 18 + 4;
	}

	/** 说明第二行的 y。 */
	public static int voidTipLine2Y(final int rows) {
		return voidTipLine1Y(rows) + 9;
	}

	/** 开关按钮的 y(说明下面留 9px)。 */
	public static int voidButtonY(final int rows) {
		return voidTipLine1Y(rows) + 22;
	}

	public static final int VOID_BUTTON_WIDTH = 60;
	public static final int VOID_BUTTON_HEIGHT = 14;

	/** 按钮的左边缘(弹窗里居中,与行数无关)。 */
	public static int voidButtonX() {
		return POPUP_RIGHT - (POPUP_WIDTH + VOID_BUTTON_WIDTH) / 2;
	}

	/**
	 * 侧签弹窗相对面板的矩形 —— <b>绘制与命中判定唯一的几何来源</b>。
	 *
	 * <h2>⚠️ 为什么要有这个东西</h2>
	 * 参考成熟 mod 的做法(见 {@code refs/背包界面研究报告.md} §7):
	 * 精妙背包用 {@code Tab.getTabRectangle()} <b>让控件自报矩形</b>,
	 * 旅行者用"随开合变的 {@code isMouseOver}" ——
	 * 两家都做到了<b>"矩形只有一个来源"</b>。
	 *
	 * <p>我们原来把这个矩形在<b>三处各写一遍</b>:
	 * {@code BackpackMenu} 的常量、{@code extractPopup} 的绘制、
	 * {@code hasClickedOutside} 的判定。
	 * 那正是工作区约定<b>坑 25</b>("东西被丢到地上")的温床 ——
	 * 三处只要有一处改了忘了改另两处,就会出现"看着在弹窗里、判定却在外面"。
	 *
	 * <p>⛔ <b>不要再用零散常量去拼这个矩形</b>,一律走这里。
	 *
	 * @param tab      当前打开的签({@link #TAB_NONE} 表示没开)
	 * @param voidRows 虚空样本槽的行数(见 {@link #voidRows(int)})
	 */
	public static PopupRect popupRect(final int tab, final int voidRows) {
		return new PopupRect(-POPUP_WIDTH, POPUP_TOP, POPUP_WIDTH, popupHeight(tab, voidRows));
	}

	public record PopupRect(int x, int y, int width, int height) {

		/** 点在不在这个矩形里(用<b>面板相对</b>坐标,即鼠标坐标减去 leftPos/topPos)。 */
		public boolean contains(final int px, final int py) {
			return px >= this.x && px < this.x + this.width
				&& py >= this.y && py < this.y + this.height;
		}
	}

	/**
	 * 侧签槽"藏起来"时的 x。
	 *
	 * <p>参考成熟 mod 的做法(精妙用 {@code -2000}、旅行者用 {@code -1000}) ——
	 * <b>把坐标踢出屏幕就等于禁用</b>,连原版自己的命中判定都失效,
	 * 不需要再依赖 {@code isActive()}。
	 */
	public static final int HIDDEN_SLOT_X = -2000;

	/** 弹窗贴面板左边缘的那个相对 x(也就是 0),内容从这个 x 往左排。 */
	public static final int POPUP_RIGHT = 0;

	/** 全关。 */
	public static final int TAB_NONE = -1;
	public static final int TAB_FEED = 0;
	public static final int TAB_CHARM = 1;
	public static final int TAB_STOMACH = 2;
	public static final int TAB_VOID = 3;
	/** 侧签总数。 */
	public static final int TAB_COUNT = 4;

	public static final String[] TAB_KEYS = {
		"gui.ergu_monster_backpack.tab.feed",
		"gui.ergu_monster_backpack.tab.charm",
		"gui.ergu_monster_backpack.tab.gizzard",
		"gui.ergu_monster_backpack.tab.void",
	};

	// ────────────────────────────────────────────────────────────────
	// 🆕 顶部主页签(§七)
	// ────────────────────────────────────────────────────────────────

	/** 背包页 —— 84 格 + 左侧签。 */
	public static final int PAGE_BACKPACK = 0;

	/**
	 * 成长页 —— 技能树(§六)。
	 *
	 * <p>⚠️ 它<b>不显示背包格</b> —— 切到这页时 84 个格子全部踢出屏幕,
	 * 整个面板让给技能树(见 §七「成长页布局」)。
	 */
	public static final int PAGE_GROWTH = 1;

	/** 主页签总数。 */
	public static final int PAGE_COUNT = 2;

	public static final String[] PAGE_KEYS = {
		"gui.ergu_monster_backpack.page.backpack",
		"gui.ergu_monster_backpack.page.growth",
	};

	/** 喂食格的位置 —— 在左侧弹窗里居中(坐标是负的,因为弹窗在面板外)。 */
	private static final int FEED_SLOT_X = POPUP_RIGHT - (POPUP_WIDTH + 18) / 2;
	private static final int FEED_SLOT_Y = 46;

	// ---------------------------------------------------------------- 挂饰槽
	/** 挂饰槽数量(见 §八:一个阶段解锁一个)。 */
	public static final int CHARM_SLOTS = 2;

	/** 两个挂饰槽在弹窗里的位置 —— 竖着排。 */
	private static final int CHARM_SLOT_X = POPUP_RIGHT - (POPUP_WIDTH + 18) / 2;
	private static final int CHARM_SLOT_Y = 40;
	/** 两个槽之间的间距(留一行写字)。 */
	private static final int CHARM_SLOT_GAP = 26;

	// ---------------------------------------------------------------- 胃 F16
	/** 胃的输入格位置(在「胃」侧签的弹窗里)。 */
	private static final int STOMACH_SLOT_X = POPUP_RIGHT - (POPUP_WIDTH + 18) / 2;
	private static final int STOMACH_SLOT_Y = 54;

	// 排布由用户 2026-10-15 定:**I 级 3×3(9 格) → II 级 5×5(25 格)**。
	//
	// ⚠️ 这是【动态】的(和别的签不同):等级一变,列数、行数、每个槽的位置都要重算。
	// 所以虚空的槽不交给 layoutSideTabSlots(),而是 layoutVoidSlots() 单独排。
	//
	// 📌 为什么 9 格:精妙普通虚空(9)和旅行者虚空(9,可配 1~20)的默认值都是 9 ——
	// 这是这类功能的收敛值,不是随口定的。

	/** 样本槽网格的左边缘(在弹窗里居中)。 */
	private static int voidGridX(final int cols) {
		return POPUP_RIGHT - (POPUP_WIDTH + cols * 18) / 2;
	}

	/**
	 * 虚空现在解锁到第几格 —— **由技能树节点推**。
	 *
	 * <pre>
	 *   没解锁 voiding_1  →  0 格(侧签点开是空的,提示玩家去成长页解锁)
	 *   voiding_1        →  9 格(3×3)
	 *   voiding_2        → 25 格(5×5)
	 * </pre>
	 *
	 * <h2>⚠️ 2026-10-15:从 {@code stage} 换成技能树节点</h2>
	 * 以前是 `stage() >= 3 ? TIER2 : TIER1`,方法的注释自己写着
	 * "等技能树做好了,只需要把这个方法换成读节点 —— 别处一行都不用改"。
	 * **现在就是那时候,而那句话是对的:调用点一行都没动。**
	 *
	 * <p>⚠️ 为什么值得换:`stage` 是**成长阶段**(长到第几行),
	 * 而"虚空能过滤几格"是**玩家自己花材料解锁的能力** —— 两者本来就不该绑定。
	 *
	 * <p>⚠️ **0 格那个分支是新加的**:以前"没解锁"也白送 9 格
	 * (`stage()` 默认返回 1,而 `1 >= 3` 是假 ⇒ 走 TIER1)。
	 * 现在技能树解锁才算数,所以必须显式处理"一级都没有"。
	 */
	public int voidUnlockedSlots() {
		if (unlocked(com.ergu.monsterbackpack.skill.SkillNodes.VOIDING_2)) {
			return ModComponents.VOID_FILTER_TIER2;
		}
		if (unlocked(com.ergu.monsterbackpack.skill.SkillNodes.VOIDING_1)) {
			return ModComponents.VOID_FILTER_TIER1;
		}
		return 0;
	}

	/** 虚空现在排几列(3 或 5)。⚠️ 0 格时给 3 —— 免得下面 `+ cols - 1` 除零。 */
	public int voidColumns() {
		return unlocked(com.ergu.monsterbackpack.skill.SkillNodes.VOIDING_2)
			? ModComponents.VOID_COLS_TIER2
			: ModComponents.VOID_COLS_TIER1;
	}

	/** 🆕 虚空功能解锁了没 —— 界面上"没解锁"和"解锁了但没登记"要区分开。 */
	public boolean voidUnlocked() {
		return this.voidUnlockedSlots() > 0;
	}

	/** 🆕 某个技能节点解锁了没(读数用的简写 —— 省得每处都写一长串全限定名)。 */
	private boolean unlocked(final com.ergu.monsterbackpack.skill.SkillNodes.Node node) {
		return com.ergu.monsterbackpack.skill.SkillUnlock.isUnlocked(this.currentBackpack(), node);
	}

	/**
	 * 🆕 第 n 个挂饰槽解锁了没(读技能树节点 `charm_slot_1` / `charm_slot_2`)。
	 *
	 * <p>⚠️ 这两个节点在 §六 里标着 **🔹 到时自动解锁** —— 它们不花材料,
	 * 阶段到了就由 {@code syncAutomaticSkills()} 白送。**所以玩家不需要去点它们**,
	 * 但界面照样把它们画成一个节点(起"路线图"作用)。
	 *
	 * @param slotIndex 0 或 1(见 {@link #CHARM_SLOTS})
	 */
	private boolean charmSlotUnlocked(final int slotIndex) {
		return unlocked(slotIndex == 0
			? com.ergu.monsterbackpack.skill.SkillNodes.CHARM_SLOT_1
			: com.ergu.monsterbackpack.skill.SkillNodes.CHARM_SLOT_2);
	}

	/** 虚空现在排几行 —— 向上取整(9/3 = 3;25/5 = 5)。 */
	public int voidRows() {
		final int cols = this.voidColumns();
		return (this.voidUnlockedSlots() + cols - 1) / cols;
	}

	/**
	 * 重排虚空样本槽 —— <b>等级或签变化时都要调</b>。
	 *
	 * <h2>⚠️ 为什么单独一个方法,不并进 {@link #layoutSideTabSlots()}</h2>
	 * 别的侧签槽"打开就回原位",位置恒定;而虚空的列数<b>随等级变</b>,
	 * 所以它的 {@code x} 和 {@code y} <b>都要重算</b>
	 * (其它槽的 {@code homeX()} 是常量,连 y 都不用动)。
	 *
	 * <h2>⚠️⚠️ 两个调用点缺一不可(坑 27)</h2>
	 * 本方法会改 {@code slot.x/y},而那是<b>客户端渲染与命中判定</b>用的 —— 所以:
	 * <ol>
	 *   <li>{@link #setTab}(服务端主动改签)</li>
	 *   <li>{@code DATA_TAB / DATA_STAGE} 的 {@code ContainerData.set}(客户端收同步)</li>
	 * </ol>
	 * <b>漏掉客户端那条的症状</b>:等级涨了但槽还排在旧位置 —— "看着对、实际不对"。
	 */
	private void layoutVoidSlots() {
		final int cols = this.voidColumns();
		final int unlocked = this.voidUnlockedSlots();
		final int gridX = voidGridX(cols);

		// ⚠️ 遍历的是【自己的 voidSlots 列表】,不是 menu.slots ——
		// 它们刻意没进 menu.slots(见那个字段的注释)。
		for (int i = 0; i < this.voidSlots.size(); i++) {
			final VoidSlot slot = this.voidSlots.get(i);

			// ⚠️ 未解锁的槽【也要踢出屏幕】,不能留在网格里。
			//
			// 踩过:I 级(只解锁 9 格)时,25 个槽按 3 列排成了【9 行】,
			// 后 6 行画到弹窗外面去了。根因是两处对"格数"的理解不一致 ——
			// voidRows() 按【解锁数】算高度,而这里却把【全部 25 个】都排了进去。
			if (this.openTab != TAB_VOID || i >= unlocked) {
				slot.x = HIDDEN_SLOT_X;
			} else {
				slot.x = gridX + (i % cols) * 18;
				slot.y = VOID_SLOT_Y + (i / cols) * 18;
			}
		}
	}

	// ------------------------------------------------------------ 同步数据索引
	private static final int DATA_UNLOCKED = 0;
	private static final int DATA_START = 1;
	/** 🆕 饱食度走同一条通道 —— 方块形态那边同样不会自动同步。 */
	private static final int DATA_SATIETY = 2;

	/**
	 * 🆕 <b>当前打开的侧签</b>也要走这条通道。
	 *
	 * <p>它是菜单自己的字段,既不是组件、也不在槽位里,所以<b>原版容器同步根本不会管它</b>。
	 * 实测症状(2026-10-15):点侧签后服务端改了,但<b>客户端界面纹丝不动</b> ——
	 * 看起来就像"点了没反应"。
	 *
	 * <p>⚠️ 它<b>不写回物品</b>(只是菜单状态),所以 {@code set} 里直接改字段。
	 */
	private static final int DATA_TAB = 3;

	/** 🆕 阶段 —— 独立存的组件,同样要走同步通道(方块形态下客户端物品不准)。 */
	private static final int DATA_STAGE = 4;

	/**
	 * 🆕 自动收纳开关。
	 *
	 * <p>组件是 {@code Boolean},而 {@code ContainerData} 只搬 {@code int} ——
	 * 所以走通道时 <b>1 = 开 / 0 = 关</b>,两头各转一次。
	 */
	private static final int DATA_AUTO_COLLECT = 5;

	/**
	 * 🆕 虚空开关(F17)。
	 *
	 * <p>和自动收纳同理:{@code Boolean} 组件走 {@code ContainerData} 时 <b>1 = 开 / 0 = 关</b>。
	 *
	 * <p>⚠️ <b>必须走同步通道</b> —— 方块形态下客户端手里那个背包物品的组件是<b>不准的</b>
	 * (内容不跟着方块实体同步),直接读组件会得到旧值。
	 */
	private static final int DATA_VOID_ENABLED = 6;

	/**
	 * 🆕 <b>当前打开的主页签</b>(背包 / 成长,见 §七)。
	 *
	 * <p>和 {@link #DATA_TAB} 是同一类东西:菜单自己的状态,**原版不会同步**,
	 * 必须走 {@code ContainerData} 通道,否则服务端改了客户端看不到。
	 *
	 * <p>⚠️⚠️ 它<b>还会影响布局</b>(成长页要把 84 个背包格全部踢出屏幕),
	 * 所以它和其它纯"显示用"的状态不同 —— <b>两条到达路径都必须重排布局</b>,
	 * 少一条就会变成"页签切了但格子还在"(坑 27)。
	 */
	private static final int DATA_PAGE = 7;

	private static final int DATA_COUNT = 8;

	// ---------------------------------------------------------------- 实例
	private final BackpackContainer container;
	private final Inventory playerInventory;
	/** 背包在哪(两端一致)。 */
	private final BackpackOpenData openData;

	/**
	 * 玩家物品栏里<b>要锁住</b>的那一格(原版索引)。{@code -1} = 不用锁。
	 *
	 * <p>只有物品形态需要:它的背包就待在玩家物品栏里,能被拖走。
	 * 方块形态的背包在世界里,物品栏中没有对应格子。
	 */
	private final int lockedInventoryIndex;

	/**
	 * 玩家物品栏槽在菜单里的<b>结束位置</b>(开区间上界)。
	 *
	 * <h2>⚠️⚠️ 为什么不能直接用 {@code slots.size()}</h2>
	 * 菜单里槽的顺序是:<b>背包格 → 物品栏 → 侧签槽 → 虚空槽</b>。
	 * 所以 {@code slots.size()} <b>不是</b>"物品栏的末尾",而是"最后一个虚空槽的下一个"。
	 *
	 * <p><b>踩点(2026-10-15,用户实测"收纳坏了")</b>:
	 * {@code quickMoveStack} 里写的是
	 * <pre>
	 *   moveItemStackTo(stack, GRID_SLOTS, this.slots.size(), true)   // 「背包 → 物品栏」
	 * </pre>
	 * 加了 25 个虚空槽之后,这个范围从 {@code 84..124} 变成 {@code 84..149} ——
	 * <b>把虚空样本槽也包进去了</b> ⇒ Shift 左键从背包取东西时,
	 * <b>物品被塞进了虚空样本槽</b>(因为 {@code VoidSlot.mayPlace} 什么都能收)。
	 *
	 * <p>⇒ <b>凡是"某个区间的槽"的操作,都要用区间的端点,不要用 {@code slots.size()}</b>。
	 */
	private int playerInvEnd;

	/**
	 * 「虚空」的样本格 —— ⚠️ <b>刻意【不放进菜单的 slots 列表】</b>。
	 *
	 * <h2>⚠️⚠️ 为什么(2026-10-15 踩了个实打实的坑之后改的)</h2>
	 * 第一版把它们当成普通 {@code Slot} 加进了 {@code this.slots},结果实测:
	 * <b>每点一下样本格,鼠标光标上的物品数量就 +1</b>,一直加到 64 才停。
	 *
	 * <p>根因在 {@code AbstractContainerMenu.clicked}(26.2 的 {@code :459-465}):
	 * <pre>
	 *   } else if (ItemStack.isSameItemSameComponents(clicked, carried)) {
	 *       slot.tryRemove(clicked.getCount(), ...).ifPresent(itemsTaken ->
	 *           carried.grow(itemsTaken.getCount()));      // ← 样本格里 1 个 ⇒ 光标 +1
	 * </pre>
	 * 那就是<b>原版自带的"把同款物品从槽里收进光标"</b>的逻辑,而它<b>只认 {@code menu.slots}</b>。
	 *
	 * <p><b>精妙背包的解法</b>({@code SettingsContainerMenu.java:66 与 :121-128}):
	 * 样本格存在自己的 {@code ghostSlots} 列表里,并<b>重写 {@code addSlot} 不调 super</b> ——
	 * <b>原版根本看不到它们</b>,于是这类冲突从结构上不可能发生
	 * (代价是渲染 / 点击 / 同步都要自己写;它连同步都得自己写,因为它的样本存在方块实体数据里)。
	 *
	 * <h2>⭐ 我们比精妙省一步:同步不用自己写</h2>
	 * 我们的样本存在<b>物品组件</b> {@code void_filter} 上,而那是
	 * {@code networkSynchronized} 的 ⇒ <b>原版自己会同步</b>。
	 * 把它挪出 {@code menu.slots} <b>不影响组件同步</b>。
	 *
	 * <h2>⚠️ 代价:下面这几处都要单独处理(别再遍历 {@code this.slots} 找虚空槽)</h2>
	 * <ul>
	 *   <li>{@link #layoutVoidSlots()} —— 自己排</li>
	 *   <li>{@code BackpackScreen} —— 槽框、物品、半透明叠加都要自己画</li>
	 *   <li>{@code BackpackScreen.slotAt(...)} —— 命中判定要把它算进来</li>
	 * </ul>
	 * <b>好处</b>:{@code quickMoveStack} 的范围问题从根上消失了
	 * (以前 "背包 → 物品栏" 的搜索范围会把它包进去)。
	 */
	public final java.util.List<VoidSlot> voidSlots = new java.util.ArrayList<>();

	/** 物品栏槽的起点 —— 背包格之后就是它。 */
	public static final int PLAYER_INV_START = GRID_SLOTS;

	/** 格数与计时的那条通道。 */
	private final ContainerData state;

	/** 当前打开的侧签。**两端一致** —— 它决定哪些功能槽是活的。 */
	private int openTab = TAB_NONE;

	/**
	 * 🆕 当前打开的主页签(背包 / 成长)。
	 *
	 * <p>⚠️⚠️ 它比 {@link #openTab} 更进一步:<b>它不只决定"哪些槽是活的",还决定"背包格在不在"</b>。
	 * 所以改变它的两条路径(服务端收到包 / 客户端收到同步)**都必须调布局重排**,
	 * 否则会出现"页签切到成长页了,但 84 个格子还画在屏幕上"。
	 */
	private int openPage = PAGE_BACKPACK;

	/**
	 * 喂食格用的临时容器。
	 *
	 * <p>它<b>不需要持久化</b>:东西放进去当场就被吃掉,格子立刻空回来,
	 * 所以关界面时它本来就是空的。
	 */
	private final net.minecraft.world.SimpleContainer feedContainer =
		new net.minecraft.world.SimpleContainer(1);

	/**
	 * 两个挂饰槽的内容 —— 存在背包的 {@code charms} 组件上(插上就跟着背包走)。
	 *
	 * <p>⚠️ <b>必须在构造器体里赋值,不能写成字段初始化器</b>:
	 * 字段初始化器在<b>构造器体之前</b>执行,而它构造时要立刻读一次背包,
	 * 那时 {@link #openData} 还是 {@code null} ⇒ 一开界面就 NPE。
	 * (2026-10-15 实测踩到:"背包直接打不开了"。)
	 */
	private final CharmContainer charmContainer;

	/**
	 * 「胃」的内容 —— 存在 {@code stomach} 组件上(<b>关掉界面也继续消化</b>,坑 19)。
	 *
	 * <p>⚠️ 同样必须在构造器体里赋值(理由见上面 {@code charmContainer})。
	 */
	private final StomachContainer stomachContainer;

	/**
	 * 「虚空」的样本槽白名单 —— 存在 {@code void_filter} 组件上(关掉界面也还在)。
	 *
	 * <p>⚠️ 同样必须在构造器体里赋值(理由见上面 {@code charmContainer})。
	 */
	private final VoidContainer voidContainer;

	public BackpackMenu(final int containerId, final Inventory inventory, final BackpackOpenData data) {
		super(ModMenus.BACKPACK, containerId);

		this.playerInventory = inventory;
		this.openData = data;
		this.lockedInventoryIndex = computeLockedIndex(inventory, data);

		// ⚠️ 顺序要紧:它们构造时会立刻读一次背包,所以必须在 openData 赋值【之后】
		this.charmContainer = new CharmContainer(this::currentBackpack);
		this.stomachContainer = new StomachContainer(this::currentBackpack);
		this.voidContainer = new VoidContainer(this::currentBackpack);

		this.state = new ContainerData() {
			@Override
			public int get(final int index) {
				if (index == DATA_TAB) {
					return BackpackMenu.this.openTab;
				}
				if (index == DATA_PAGE) {
					return BackpackMenu.this.openPage;
				}

				final ItemStack backpack = BackpackMenu.this.currentBackpack();
				return switch (index) {
					case DATA_UNLOCKED -> backpack.getOrDefault(ModComponents.UNLOCKED_SLOTS, 0);
					case DATA_SATIETY -> backpack.getOrDefault(ModComponents.SATIETY, 0);
					case DATA_STAGE -> backpack.getOrDefault(ModComponents.STAGE, 1);
					// 布尔 → int:1 = 开
					case DATA_AUTO_COLLECT -> backpack.getOrDefault(
						com.ergu.monsterbackpack.charm.CharmComponents.AUTO_COLLECT, true) ? 1 : 0;
					case DATA_VOID_ENABLED -> backpack.getOrDefault(
						ModComponents.VOID_ENABLED, Boolean.FALSE) ? 1 : 0;
					default -> backpack.getOrDefault(ModComponents.UNLOCK_START, -1);
				};
			}

			@Override
			public void set(final int index, final int value) {
				// 侧签是【菜单自己的状态】,不写回物品
				if (index == DATA_TAB) {
					BackpackMenu.this.openTab = value;
					// ⚠️⚠️ 必须跟着重排侧签槽!
					//
					// 这条通道才是【客户端】知道"签变了"的唯一途径 ——
					// 服务端那边的 setTab() 里也调了一次,但那只改了服务端对象的 slot.x,
					// 而 slot.x 是【客户端渲染和命中判定】用的。
					//
					// 漏掉这一句的实测症状(2026-10-15):签打开了,但侧签槽没挪进弹窗,
					// 而是留在构造时的位置 —— 看起来像"槽跑到背包格上了"。
					BackpackMenu.this.layoutSideTabSlots();
					return;
				}

				// 🆕 主页签 —— 同样是【菜单自己的状态】,但⚠️它还要重排【背包格】:
				// 成长页要把 84 个格子全部踢出屏幕(§七「成长页布局」)。
				//
				// ⚠️ 漏掉这一句的症状:页签切到成长页,但 84 个格子照旧画着 + 照旧能点。
				if (index == DATA_PAGE) {
					BackpackMenu.this.openPage = value;
					// ⚠️ 关掉侧签 —— 侧签是"背包页"的东西,成长页没有它们的位置。
					// (不关的话,侧签槽会浮在技能树上面,而且玩家能点到一个看不见的弹窗)
					if (value != PAGE_BACKPACK) {
						BackpackMenu.this.openTab = TAB_NONE;
					}
					BackpackMenu.this.layoutSideTabSlots();
					return;
				}

				// 其余三项:客户端把服务端同步来的值写回【自己那份】物品,
				// 这样界面照旧从物品读就行,不必为方块形态写两套逻辑。
				final ItemStack backpack = BackpackMenu.this.currentBackpack();
				// ⚠️ 拿不到真背包时【绝不写】—— 这条通道是客户端写本地物品的,
				// 写进 ItemStack.EMPTY 就是污染全局空栈(背包已经不在了的场合)。
				if (backpack.isEmpty()) {
					return;
				}
				switch (index) {
					case DATA_UNLOCKED -> backpack.set(ModComponents.UNLOCKED_SLOTS, value);
					case DATA_SATIETY -> backpack.set(ModComponents.SATIETY, value);
					case DATA_STAGE -> {
						backpack.set(ModComponents.STAGE, value);

						// ⚠️⚠️ 阶段一变,虚空的【列数】就变了(3 → 5)——
						// 所以必须重排,否则槽还排在旧的 3 列位置上。
						//
						// 这条也是坑 27("一个状态两条到达路径")的又一次应用:
						// 服务端那边是 setTab / 生长逻辑改的,客户端【只能靠这里】知道。
						BackpackMenu.this.layoutVoidSlots();
					}
					// int → 布尔
					case DATA_AUTO_COLLECT -> backpack.set(
						com.ergu.monsterbackpack.charm.CharmComponents.AUTO_COLLECT, value != 0);
					case DATA_VOID_ENABLED -> backpack.set(
						ModComponents.VOID_ENABLED, value != 0);
					default -> backpack.set(ModComponents.UNLOCK_START, value);
				}
			}

			@Override
			public int getCount() {
				return DATA_COUNT;
			}
		};
		this.addDataSlots(this.state);

		// ⚠️ 传"取值方式"而不是 ItemStack ——
		// 原版容器同步会把实例换掉,抓着旧对象界面就永远不更新(工作区约定坑 22)。
		// ⚠️ 格数也走同步通道(方块形态下客户端物品里的格数不准)。
		this.container = new BackpackContainer(this::currentBackpack, this::unlockedSlots);

		this.addBackpackGrid();
		this.addPlayerInventory();
		this.addSideTabs();
	}

	/**
	 * 侧签里的功能槽。
	 *
	 * <p>它们<b>始终存在于菜单里</b>,只是根据 {@link #openTab} 决定活不活 ——
	 * 用坐标隐藏控制(见 {@link SideTabSlot})。这样切换侧签<b>不需要重建菜单</b>,
	 * 容器也不用重开(与 §七「一个 Menu + 一个 Screen,页签切换不重开容器」一致)。
	 */
	private void addSideTabs() {
		this.addSlot(new FeedSlot(this.feedContainer, 0, FEED_SLOT_X, FEED_SLOT_Y,
			this::currentBackpack, TAB_FEED));

		// 两个挂饰槽 —— 除了"签开着",还各有一道【技能树节点】门(见 CharmSlot)。
		// ⚠️ 2026-10-15:`this::stage` 换成了 `this::charmSlotUnlocked` ——
		// 以前是"阶段 2 / 阶段 3 自动开",现在要玩家在成长页解锁(那两个节点标着 🔹 自动解锁)。
		for (int i = 0; i < CHARM_SLOTS; i++) {
			this.addSlot(new CharmSlot(this.charmContainer, i,
				CHARM_SLOT_X, CHARM_SLOT_Y + i * CHARM_SLOT_GAP,
				this::charmSlotUnlocked, TAB_CHARM));
		}

		// 「砂囊」的输入格(只收粗矿,产物直接进背包)
		this.addSlot(new StomachSlot(this.stomachContainer, 0,
			STOMACH_SLOT_X, STOMACH_SLOT_Y, TAB_STOMACH));

		// 🆕 「虚空」的样本槽(F17)—— 【一次把 25 个位全建好】。
		//
		// ⚠️⚠️ 刻意【不调 this.addSlot(...)】—— 见 voidSlots 字段的长注释:
		// 进了 menu.slots,原版 clicked 就会拿它去"收进光标",把玩家光标上的数量加爆。
		// 精妙背包也是这么做的(重写 addSlot 不调 super)。
		//
		// ⚠️ 也就没有 menu.slots 的全局 index 了 ——
		// VoidSlot 内部一律用 getContainerSlot(),不依赖 index(那个坑先前已经踩过一次)。
		for (int i = 0; i < ModComponents.VOID_FILTER_SIZE; i++) {
			this.voidSlots.add(new VoidSlot(this.voidContainer, i,
				0, 0,
				TAB_VOID,
				this::voidUnlockedSlots));
		}

		// 构造时先按"没有签打开"摆一次 —— 所有侧签槽都会落到 HIDDEN_SLOT_X。
		this.layoutSideTabSlots();

		// 🆕 顺手把「🔹 到时自动解锁」的节点补上(阶段够了就白送,不花材料)。
		//
		// ⚠️⚠️ 只在【服务端】做。客户端跑这段会去改它手里那份背包物品的 skills 组件 ——
		// 那是"客户端预测",而组件是可同步的,两边各写一次最后会被服务端的值覆盖,
		// 中途还可能闪一下"解锁了又变回去"。
		//
		// ⚠️ 本来这里也能放"每 tick 检查",但没必要:阶段只在突破时变,
		// 而突破有明确的入口 —— 打开界面时补一次就够。
		if (!this.playerInventory.player.level().isClientSide()) {
			this.syncAutomaticSkills();
		}
	}

	/** 当前打开的侧签。 */
	public int openTab() {
		return this.openTab;
	}

	/** 切换侧签(服务端收到包后调用;两端会因此保持一致)。 */
	public void setTab(final int tab) {
		this.openTab = tab >= 0 && tab < TAB_COUNT ? tab : TAB_NONE;
		// 换了签 → 把侧签槽按新状态挪进/挪出可见区。
		// ⚠️ 这一步【必须跟着 setTab】:槽的坐标就是"它可不可用"的唯一表达。
		this.layoutSideTabSlots();
	}

	/** 🆕 当前打开的主页签(背包 / 成长)。 */
	public int openPage() {
		return this.openPage;
	}

	/**
	 * 🆕 切换主页签(服务端收到包后调用)。
	 *
	 * <p>⚠️ 和 {@link #setTab} 一样,换页<b>必须重排布局</b> ——
	 * 因为成长页要把 84 个背包格全部踢出屏幕(§七「成长页布局」)。
	 */
	public void setPage(final int page) {
		this.openPage = page >= 0 && page < PAGE_COUNT ? page : PAGE_BACKPACK;
		// ⚠️ 切到成长页时把侧签关掉 —— 侧签属于背包页,成长页没有它们的位置。
		if (this.openPage != PAGE_BACKPACK) {
			this.openTab = TAB_NONE;
		}
		this.layoutSideTabSlots();
	}

	/**
	 * 按当前打开的签,把侧签槽<b>挪进或挪出可见区</b>。
	 *
	 * <p>见 {@link SideTabSlot} 的类注释:隐藏靠<b>坐标</b>,不靠 {@code isActive()}。
	 * 好处有二:绘制时只要看 {@code slot.x}(不必为每种槽写分支);
	 * 而且不存在"看不见但能点到"的陷阱(坐标出屏后连原版自己也判不到)。
	 *
	 * <p>🆕 现在它还兼管<b>背包格的主页签显隐</b>(成长页要把 84 格全踢出去)——
	 * 两者都是"按菜单状态决定槽在哪",放一起才不会被漏掉(坑 27)。
	 */
	private void layoutSideTabSlots() {
		// 🆕 先处理背包格 —— 成长页不显示它们
		this.layoutGridSlots();

		for (final net.minecraft.world.inventory.Slot slot : this.slots) {
			// ⚠️ 虚空【不在这里排】—— 它的列数随等级变,x 和 y 都要重算,
			// 交给 layoutVoidSlots()(见那个方法的注释)。
			if (slot instanceof VoidSlot) {
				continue;
			}

			if (slot instanceof SideTabSlot sideTabSlot) {
				slot.x = sideTabSlot.tabId() == this.openTab
					? sideTabSlot.homeX()      // 它那个签开着 → 挪回原位
					: HIDDEN_SLOT_X;           // 否则踢出屏幕
			}
		}

		// 虚空槽自己排(签开了才进弹窗,而且位置按当前等级算)
		this.layoutVoidSlots();
	}

	/**
	 * 🆕 按主页签决定格子们在不在。
	 *
	 * <pre>
	 *   背包页 → 背包格回到 GRID_X + col*18 / GRID_Y + row*18,物品栏格回原位
	 *   成长页 → 【全部】踢出屏幕(84 个背包格 + 36 个物品栏格)
	 * </pre>
	 *
	 * <h2>⚠️ 为什么连【玩家物品栏】也一起隐藏</h2>
	 * 成长页要画三条竖列、每列最多 7 个节点(§七「成长页布局」)。
	 * 面板高只有 240px,而下面的物品栏区(标签 + 3 行 + 快捷栏)要吃掉约 90px ——
	 * 剩下 150px 按 26px/节点只放得下 5 行,**不够**。
	 *
	 * <p>而且成长页本来就不需要物品栏:它是"看路线图"的页面,
	 * 要挪东西就切回背包页。**把整块地方让给技能树**,两边都清爽。
	 *
	 * <p>⚠️ 位置是<b>从索引重算</b>的,不缓存 —— 缓存一份"原始坐标"就多一份会失效的状态。
	 * 网格布局本来就只有这一个公式({@link #GRID_X} / {@link #GRID_COLS}),现算零成本。
	 *
	 * <p>⚠️ 隐藏用 {@link #HIDDEN_SLOT_X},<b>不改 y</b> —— 只动一个轴就够,
	 * 而且 y 留着能让 {@code extractSlots} 的判断简单些。
	 */
	private void layoutGridSlots() {
		final boolean visible = this.openPage == PAGE_BACKPACK;

		for (final net.minecraft.world.inventory.Slot slot : this.slots) {
			if (slot instanceof BackpackSlot backpackSlot) {
				if (!visible) {
					slot.x = HIDDEN_SLOT_X;
					continue;
				}

				// ⚠️ 用 getContainerSlot() 而不是 index —— 后者是【菜单全局】索引(坑 31)
				final int i = backpackSlot.getContainerSlot();
				slot.x = GRID_X + (i % GRID_COLS) * 18;
				slot.y = GRID_Y + (i / GRID_COLS) * 18;
				continue;
			}

			// 玩家物品栏 —— 成长页一并让位。
			//
			// ⚠️ 只处理【物品栏区间】内的槽(PLAYER_INV_START 起)。
			// 侧签槽 / 虚空槽不归这里管:前者由 layoutSideTabSlots 的另一个分支处理,
			// 后者由 layoutVoidSlots 处理 —— 抢着设它们的 x 会让那两处失效(坑 27)。
			if (slot instanceof BackpackSlot || slot instanceof SideTabSlot || slot instanceof VoidSlot) {
				continue;
			}

			if (!visible) {
				slot.x = HIDDEN_SLOT_X;
			} else if (slot.x == HIDDEN_SLOT_X) {
				// 从成长页切回来:物品栏格要回原位。
				//
				// ⚠️ 这里不能像背包格那样"从索引重算" —— 玩家物品栏的布局是【原版
				// AbstractContainerScreen 自己算】的(它有 9 列 / 3 行 / 快捷栏的特殊间距),
				// 我们重算一遍迟早和原版对不上。
				//
				// 所以用【构造时记下来的原位】恢复。见 rememberPlayerSlotHomes()。
				final int home = this.playerSlotHomeX.getOrDefault(slot, Integer.MIN_VALUE);
				if (home != Integer.MIN_VALUE) {
					slot.x = home;
				}
			}
		}
	}

	/**
	 * 🆕 玩家物品栏格的"原位 x"。
	 *
	 * <p>⚠️ 只在<b>构造时记一次</b>(那时原版还没排过它们,所以位置是构造参数给的)。
	 * 之后 {@link #layoutGridSlots} 隐藏 / 恢复都靠这份记录 ——
	 * 比"我们自己按 9 列重算一遍"可靠,因为原版物品栏的间距并不完全规整。
	 *
	 * <p>⚠️ 用 {@code IdentityHashMap} 的语义即可({@code Slot} 没有重写 equals),
	 * 普通 {@code HashMap} 也行 —— 这里用 {@code HashMap} 是因为槽对象在菜单生命周期内不变。
	 */
	private final java.util.Map<net.minecraft.world.inventory.Slot, Integer> playerSlotHomeX =
		new java.util.HashMap<>();

	/** 🆕 见 {@link #playerSlotHomeX}。在构造器"物品栏加完之后"调一次。 */
	private void rememberPlayerSlotHomes() {
		for (final net.minecraft.world.inventory.Slot slot : this.slots) {
			if (slot instanceof BackpackSlot || slot instanceof SideTabSlot || slot instanceof VoidSlot) {
				continue;
			}
			this.playerSlotHomeX.put(slot, slot.x);
		}
	}

	/**
	 * 🆕 请求解锁一个技能树节点(<b>服务端</b>执行,见 {@code UnlockSkillPayload})。
	 *
	 * <h2>⚠️⚠️ 这个方法的全部职责就是"不信任客户端"</h2>
	 * 客户端送来的是一个<b>字符串 id</b>,仅此而已。这里要做的是:
	 * <ol>
	 *   <li><b>查表</b> —— {@code SkillNodes.byId} 查不到就<b>直接返回</b>,
	 *       不报错、不打日志刷屏(那是个能被打包器随便发的公开入口)。</li>
	 *   <li><b>重算一切</b> —— 阶段、等级、材料,全部由 {@link SkillUnlock#unlock} 自己再来一遍。
	 *       ⚠️ <b>不要</b>读客户端传来的任何"状态"(包里也刻意没有)。</li>
	 * </ol>
	 *
	 * <p>⚠️ 阶段从<b>组件</b>现读,不从格数算 —— 见 §四 {@code stage} 那段说明
	 * (列数会变,格数推不出阶段)。
	 */
	public void tryUnlockSkill(final String nodeId) {
		final var node = com.ergu.monsterbackpack.skill.SkillNodes.byId(nodeId).orElse(null);
		if (node == null) {
			return;
		}

		final ItemStack backpack = this.requireBackpack();
		if (backpack.isEmpty()) {
			return;
		}

		final int stage = backpack.getOrDefault(ModComponents.STAGE, 1);

		com.ergu.monsterbackpack.skill.SkillUnlock.unlock(
			stage, backpack, this.playerInventory.player, node);
	}

	/**
	 * 🆕 一键整理解锁了没 —— **界面据此把按钮画成灰的**。
	 *
	 * <p>⚠️ 客户端读到的组件可能是**旧的**(刚解锁完还没同步下来),
	 * 那时按钮会短暂画成灰的。**这是可接受的** —— 玩家再点一下就好,
	 * 而反过来(明明没解锁却画成亮的)才是真的误导。
	 *
	 * <p>⚠️ 这个方法<b>只影响画法</b> —— 真正"能不能整理"由服务端在
	 * {@link #sortBackpack()} 和 {@code ModNetworking} 里各判一次(客户端说了不算)。
	 */
	public boolean sortUnlocked() {
		return com.ergu.monsterbackpack.skill.SkillUnlock.isUnlocked(
			this.currentBackpack(), com.ergu.monsterbackpack.skill.SkillNodes.SORTING);
	}

	/**
	 * 🆕 一键整理(F4)—— **由服务端调用**(收到 {@code SortingPayload} 之后)。
	 *
	 * <h2>⚠️ 这里是"界面开着"那条路;界面关着走 {@code ModNetworking} 的另一条</h2>
	 * 两条路的区别很有针对性:
	 * <ul>
	 *   <li><b>界面开着</b>(这里) —— 有 {@code openData},所以<b>方块形态也能整理</b>,
	 *       而且 {@code currentBackpack()} 两端都能解析出正确的那个背包。</li>
	 *   <li><b>界面关着</b>({@code ModNetworking}) —— 只能从玩家身上找,<b>只支持物品形态</b>。
	 *       方块形态没法处理:"附近哪个方块是背包"没有可靠判定。</li>
	 * </ul>
	 *
	 * <h2>⚠️ 为什么在这里再查一次技能树</h2>
	 * 客户端的按键处理会先拦一道({@code ModKeyMappings}),但**客户端说了不算** ——
	 * 改过的客户端可以直接发包。所以服务端必须自己再查一次
	 * (和 {@code tryUnlockSkill} 里"重算一切"是同一个原则)。
	 */
	public void sortBackpack() {
		final ItemStack backpack = this.requireBackpack();

		if (backpack.isEmpty()) {
			return;
		}

		if (!com.ergu.monsterbackpack.skill.SkillUnlock.isUnlocked(
				backpack, com.ergu.monsterbackpack.skill.SkillNodes.SORTING)) {
			return;
		}

		com.ergu.monsterbackpack.storage.BackpackSorter.sort(
			backpack, this.unlockedSlots());
	}

	/**
	 * 🆕 把「🔹 到时自动解锁」的节点补上(阶段够了就白送)。
	 *
	 * <p>调用时机:**打开界面时**。不需要每 tick —— 阶段只在突破时变,
	 * 而突破本来就有明确的入口(见 §六「突破 —— 世界仪式」)。
	 *
	 * <p>⚠️ 两端都会调:服务端调是为了改数据,客户端调是为了**界面立刻显示对**
	 * (客户端那份物品的 {@code skills} 组件是同步来的,可能还没到)。
	 */
	public void syncAutomaticSkills() {
		final ItemStack backpack = this.currentBackpack();
		// ⚠️ 空栈不能写(客户端那条路上背包可能还没同步到)
		if (backpack.isEmpty()) {
			return;
		}
		final int stage = backpack.getOrDefault(ModComponents.STAGE, 1);
		com.ergu.monsterbackpack.skill.SkillUnlock.syncAutomatic(stage, backpack);
	}

	/**
	 * 🆕 某个节点的当前等级(给界面用)。
	 */
	public int skillLevel(final com.ergu.monsterbackpack.skill.SkillNodes.Node node) {
		return com.ergu.monsterbackpack.skill.SkillUnlock.level(this.currentBackpack(), node);
	}

	/**
	 * 🆕 某个节点现在的可解锁状态(给界面上色用)。<b>纯读取</b>。
	 *
	 * <p>⚠️ 走的是 {@link #skillMaterialCount} 那个<b>帧内缓存</b>的计数器 ——
	 * 界面对 17 个节点各问一次,实际只把背包数了一遍。
	 */
	public com.ergu.monsterbackpack.skill.SkillUnlock.Status skillStatus(
		final com.ergu.monsterbackpack.skill.SkillNodes.Node node
	) {
		final ItemStack backpack = this.currentBackpack();
		final int stage = backpack.getOrDefault(ModComponents.STAGE, 1);
		return com.ergu.monsterbackpack.skill.SkillUnlock.check(
			stage, backpack, this.playerInventory.player, node, this::skillMaterialCount);
	}

	/**
	 * 🆕 材料计数的**帧内缓存** —— 只为避免"每帧把整个背包数 17 遍"。
	 *
	 * <h2>⚠️ 为什么需要它</h2>
	 * 成长页要画 17 个节点,而每个节点的状态都依赖"材料够不够"。
	 * 如果不缓存,那就是<b>每帧 17 次</b>:每次 {@code BackpackStorage.read()} 一份
	 * 84 元素的 {@code NonNullList} + 遍历玩家 36 格。60fps 下等于每秒上千次无谓分配。
	 *
	 * <p>缓存口径很窄:键是 {@code Item},值是"背包 + 玩家一共多少个"。
	 * <b>由界面在每帧开头清一次</b>({@code BackpackScreen.extractBackground}),
	 * 所以它的有效期就是"一帧"—— 玩家在帧中间把东西丢了也不会读到脏值。
	 *
	 * <p>⚠️ <b>不要</b>扩大它的有效期(比如"打开界面时算一次就一直用"):
	 * 材料数会随着玩家挪动物品而变,缓存太久会让 tooltip 显示过期的数字。
	 */
	private final java.util.Map<net.minecraft.world.item.Item, Integer> materialCountCache =
		new java.util.HashMap<>();

	/** 🆕 清掉 {@link #materialCountCache}。界面每帧开头调一次。 */
	public void invalidateMaterialCounts() {
		this.materialCountCache.clear();
	}

	/**
	 * 🆕 某种材料现在**一共能凑到多少**(背包内容格 + 玩家物品栏)。
	 *
	 * <p>⚠️ 必须和 {@code SkillUnlock} 的统计口径一致 —— 它数的是同样的两处。
	 * 界面拿它来给 tooltip 上色(够 = 灰、不够 = 红)。<b>它只用于显示</b>,
	 * 真正决定"能不能解锁"的是服务端那次 {@code SkillUnlock.unlock}。
	 */
	public int skillMaterialCount(final net.minecraft.world.item.Item item) {
		// ⚠️ 用 containsKey 而不是 getOrDefault —— 0 是合法结果("一个都没有"),
		// 不能用"返回 null"来判断"没算过"。
		if (this.materialCountCache.containsKey(item)) {
			return this.materialCountCache.get(item);
		}

		final int total = com.ergu.monsterbackpack.storage.BackpackStorage.countItem(
				this.currentBackpack(), item)
			+ com.ergu.monsterbackpack.skill.SkillUnlock.countInPlayer(
				this.playerInventory.player, item);

		this.materialCountCache.put(item, total);
		return total;
	}

	/**
	 * 🆕 <b>让内容容器的缓存失效</b> —— 供"绕过容器直接改了背包组件"的地方回调。
	 *
	 * <h2>⚠️ 什么时候必须调</h2>
	 * 只要有人<b>没经过 {@link BackpackContainer}</b> 就改了 {@code CONTAINER} 组件
	 * (比如 {@code BackpackGrowth.purgeVoid} 清虚空物品、以后的批量整理),
	 * 就必须在改完之后调一次这个方法。
	 *
	 * <p>不调的后果:<b>服务端数据是对的,但界面照旧画着旧内容</b> ——
	 * 玩家的描述会是"东西还在""左键能拿到但不显示"这类。
	 * 根因见 {@link BackpackContainer#invalidate()} 的注释(对象身份判断的盲区)。
	 *
	 * <p>⚠️ 它<b>只失效缓存,不改数据</b> —— 调多了无害(最多多读一次组件)。
	 */
	public void invalidateBackpackCache() {
		// ⚠️ 四个容器都要失效。以前只失效了内容容器,
		// 于是"砂囊/挂饰/虚空 被绕过容器改过"的那几处照旧画着旧内容 ——
		// 砂囊那个还会让产物看起来没产生、让人重复放粗矿(被当成复制)。
		this.container.invalidate();
		this.stomachContainer.invalidate();
		this.charmContainer.invalidate();
		this.voidContainer.invalidate();
	}

	/**
	 * 现在那个背包物品 —— <b>并且保证它真的是背包</b>。
	 *
	 * <p>⚠️⚠️ 取不到时必须返回 {@link ItemStack#EMPTY},<b>不能把"别的物品"交出去</b>:
	 * 后面所有写操作都是 {@code stack.set(组件, ...)} ——
	 * 写在 {@code ItemStack.EMPTY} 上等于<b>污染全局空栈</b>(原版空栈是共享的单例),
	 * 写在别的物品上等于把它悄悄变成"隐藏容器"。两种都会让玩家的东西人间蒸发。
	 */
	private ItemStack currentBackpack() {
		if (this.openData.isBlock()) {
			final BlockEntity be = this.playerInventory.player.level()
				.getBlockEntity(this.openData.pos());
			if (be instanceof MonsterBackpackBlockEntity backpack) {
				return backpack.backpack();
			}
			return ItemStack.EMPTY;
		}
		return resolveFromPlayer(this.playerInventory.player, this.openData.source());
	}

	/**
	 * 🆕 写操作专用入口 —— 拿不到背包就<b>当场收摊,并告诉玩家</b>。
	 *
	 * <h2>为什么不能只靠 {@link #currentBackpack()} 返回空栈</h2>
	 * 返回空栈本身不会崩,但调用方大多没检查 —— 背包已经不在身上(被挖掉 / 被炸 /
	 * 按 Q 丢出去 / 数字键换格)时,玩家仍然能点解锁、开虚空、开收纳:
	 * <b>材料照扣,能力写进虚空</b>。所以这里直接把界面关掉。
	 */
	private ItemStack requireBackpack() {
		final ItemStack backpack = this.currentBackpack();

		if (!backpack.isEmpty()) {
			return backpack;
		}

		if (this.playerInventory.player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendSystemMessage(
				Component.translatable("gui.ergu_monster_backpack.error.backpack_gone"), true);
			serverPlayer.closeContainer();
		}
		return ItemStack.EMPTY;
	}

	/** 这一份物品是不是怪物背包。 */
	public static boolean isBackpackStack(final ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() == ModItems.MONSTER_BACKPACK;
	}

	/**
	 * 按位置在玩家身上找背包(物品形态)。<b>不是背包就返回空栈。</b>
	 *
	 * <p>⚠️ 这里必须过滤:格子里那件东西是会被玩家换掉的
	 * (数字键 SWAP 只在背包界面里被拦,别的界面 / Q 丢弃 / F 换手都拦不住全部路径)。
	 */
	public static ItemStack resolveFromPlayer(final Player player, final int source) {
		final ItemStack found = switch (source) {
			case BackpackOpenData.SOURCE_CHEST -> player.getItemBySlot(EquipmentSlot.CHEST);
			case BackpackOpenData.SOURCE_MAIN_HAND -> player.getMainHandItem();
			case BackpackOpenData.SOURCE_OFF_HAND -> player.getOffhandItem();
			default -> player.getInventory().getItem(source);
		};
		return isBackpackStack(found) ? found : ItemStack.EMPTY;
	}

	/** 老的调用名,留着免得别处引用要改。 */
	public static ItemStack resolveBackpack(final Player player, final int source) {
		return resolveFromPlayer(player, source);
	}

	/** 这个世界坐标上是不是真的有一个背包方块(方块形态开界面前的校验)。 */
	public static boolean isBackpackBlockAt(final Player player, final BlockPos pos) {
		return player.level().getBlockEntity(pos) instanceof MonsterBackpackBlockEntity;
	}

	private void addBackpackGrid() {
		for (int row = 0; row < GRID_ROWS; row++) {
			for (int col = 0; col < GRID_COLS; col++) {
				final int index = row * GRID_COLS + col;
				this.addSlot(new BackpackSlot(this.container, index,
					GRID_X + col * 18, GRID_Y + row * 18));
			}
		}
	}

	private void addPlayerInventory() {
		// 主背包 3 行(原版索引 9..35)
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				final int index = col + row * 9 + 9;
				this.addSlot(this.playerSlotAt(index,
					INVENTORY_X + col * 18, INVENTORY_MAIN_Y + row * 18));
			}
		}

		// 快捷栏 1 行(原版索引 0..8)
		for (int col = 0; col < 9; col++) {
			this.addSlot(this.playerSlotAt(col,
				INVENTORY_X + col * 18, INVENTORY_HOTBAR_Y));
		}

		// ⚠️⚠️ 记下玩家物品栏的【结束位置】—— 见 playerInvEnd 的字段注释。
		// 必须在这里记:此刻 slots 里刚好是"背包 + 物品栏",侧签槽还没加。
		this.playerInvEnd = this.slots.size();

		// 🆕 顺手记下【每个物品栏格的原位 x】—— 成长页要把它们藏起来,切回来得能还原。
		// ⚠️ 时机必须是这里:再往后侧签槽 / 虚空槽就会混进 slots,
		// 而那些不该被 rememberPlayerSlotHomes 当成"物品栏格"。
		this.rememberPlayerSlotHomes();
	}

	/**
	 * 玩家物品栏的槽位 —— <b>如果是"正被打开的那个背包"所在格,就换成锁住的</b>。
	 *
	 * <p>不锁的后果:玩家能把它拖到别的格子,而界面记的还是原来那一格,
	 * 于是读到不相干的物品(内容、格数全乱)。
	 */
	private Slot playerSlotAt(final int inventoryIndex, final int x, final int y) {
		if (inventoryIndex == this.lockedInventoryIndex) {
			return new LockedSlot(this.playerInventory, inventoryIndex, x, y);
		}
		return new Slot(this.playerInventory, inventoryIndex, x, y);
	}

	/** 算出要锁哪一格。见 {@link #lockedInventoryIndex}。 */
	private static int computeLockedIndex(final Inventory inventory, final BackpackOpenData data) {
		if (data.isBlock()) {
			return -1;      // 方块形态:背包不在物品栏里
		}

		final int source = data.source();
		if (source == BackpackOpenData.SOURCE_CHEST) {
			return -1;      // 胸甲栏不显示在这个界面里,也拖不走
		}
		if (source == BackpackOpenData.SOURCE_MAIN_HAND) {
			// 兜底:万一有人还按"主手"开界面,就锁打开时选中的那一格
			return inventory.getSelectedSlot();
		}
		if (source == BackpackOpenData.SOURCE_OFF_HAND) {
			// 副手不在这个界面里显示,也就没有可锁的格子
			return -1;
		}
		// 其余就是物品栏索引本身
		return source >= 0 && source < inventory.getContainerSize() ? source : -1;
	}

	/** 供界面读取的背包容器。 */
	public BackpackContainer backpackContainer() {
		return this.container;
	}

	/** 背包内容对应的那个物品。 */
	public ItemStack backpackStack() {
		return this.currentBackpack();
	}

	/**
	 * 已解锁格数 —— <b>走同步通道读,不从物品读</b>。
	 *
	 * <p>⚠️ 这一点对<b>方块形态</b>是必须的:客户端那份方块实体里的背包物品
	 * 拿不到服务端的真实格数,只有 {@code ContainerData} 是准的。
	 * 渲染判定(哪些格解锁、挂饰槽开不开)都要走这里。
	 */
	public int unlockedSlots() {
		return this.state.get(DATA_UNLOCKED);
	}

	/** 当前饱食度(0 ~ {@link BackpackGrowth#MAX_SATIETY})。 */
	public int satiety() {
		return this.currentBackpack().getOrDefault(ModComponents.SATIETY, 0);
	}

	/**
	 * 当前阶段(1 ~ 4) —— <b>读那个独立存的组件,不从格数算</b>。
	 *
	 * <p>(用户 2026-10-15 指出:用格数算阶段会在"每行格子数变了"之后全错。)
	 */
	public int stage() {
		return this.state.get(DATA_STAGE);
	}

	/** 自动收纳开着吗。 */
	public boolean autoCollect() {
		return this.state.get(DATA_AUTO_COLLECT) != 0;
	}

	/**
	 * 切换自动收纳开关(<b>服务端</b>执行)。
	 *
	 * <p>⚠️ 这是<b>背包自身的初始能力</b>,和插了什么挂饰无关 ——
	 * 「磁石」只负责扩大吸取范围(用户 2026-10-15 的拆分)。
	 */
	public void toggleAutoCollect() {
		final ItemStack backpack = this.requireBackpack();
		if (backpack.isEmpty()) {
			return;
		}
		final boolean now = backpack.getOrDefault(
			com.ergu.monsterbackpack.charm.CharmComponents.AUTO_COLLECT, true);

		backpack.set(com.ergu.monsterbackpack.charm.CharmComponents.AUTO_COLLECT, !now);
	}

	/** 虚空开着吗(F17)。 */
	public boolean voidEnabled() {
		return this.state.get(DATA_VOID_ENABLED) != 0;
	}

	/**
	 * 切换虚空开关(<b>服务端</b>执行)。
	 *
	 * <p>⚠️ <b>只切开关,不动样本槽</b> ——
	 * 关掉之后玩家配好的白名单留着,下次打开接着用。
	 */
	public void toggleVoid() {
		final ItemStack backpack = this.requireBackpack();
		if (backpack.isEmpty()) {
			return;
		}

		final boolean now = backpack.getOrDefault(ModComponents.VOID_ENABLED, Boolean.FALSE);

		backpack.set(ModComponents.VOID_ENABLED, !now);
	}

	/**
	 * 点一下虚空样本格(<b>服务端</b>执行,见 {@code SetVoidSamplePayload})。
	 *
	 * <pre>
	 *   鼠标拿着东西 → 这一格登记【它的种类】(鼠标上那份【不消耗】)
	 *   鼠标空着     → 清掉这一格的登记
	 * </pre>
	 *
	 * <h2>⚠️⚠️ 用 {@code getCarried()} 而不是主手(我第一版写错了)</h2>
	 * 用户 2026-10-15 原话:"**我要的不是主手拿着东西左键登记,而是鼠标拿着一个物品点一下的登记**"。
	 *
	 * <p>两者的区别在<b>玩家的意图</b>:
	 * <ul>
	 *   <li><b>主手</b> —— 玩家在战斗/挖掘时手边一直有东西,<b>他并没有"要拿它来登记"的意思</b>。
	 *       而且主手那份<b>还会被自己读到</b>,配上"登记不消耗的样本格"就极像"凭空复制了一个"。</li>
	 *   <li><b>鼠标({@code carried})</b> —— 玩家<b>主动</b>从格子里抓起来、悬在样本格上点一下。
	 *       这是"我要登记这个"的明确表达,和原版"把东西放进槽里"是同一个手势。</li>
	 * </ul>
	 *
	 * <p>⚠️ {@code carried} 由原版自己同步(服务端这份是准的),不需要我们额外发包传物品内容。
	 *
	 * <h2>⚠️ 写进去的是"种类",不是那一份实体</h2>
	 * {@code copyWithCount(1)} —— 一份<b>新的、数量为 1 的</b>登记副本:
	 * <ul>
	 *   <li>玩家鼠标上那份<b>一个不少</b>(这是用户要的"不需要放一个实际东西进去")</li>
	 *   <li>样本永远是 1 个,不会被误当成"要凑够 N 个才生效"</li>
	 *   <li>判定也只看种类({@code VoidRules.matchesIn} 用 {@code isSameItem})</li>
	 * </ul>
	 * ⚠️ 正因为"看起来像多了一份",界面那边把样本格<b>画成半透明</b> ——
	 * 见 {@code BackpackScreen} 的虚空槽绘制,那是"这是记录,不是物品"的视觉语言。
	 */
	public void setVoidSample(final int index) {
		if (index < 0 || index >= ModComponents.VOID_FILTER_SIZE) {
			return;
		}

		final ItemStack backpack = this.requireBackpack();
		if (backpack.isEmpty()) {
			return;
		}

		final ItemStack held = this.getCarried();

		// 现解成一份列表 —— 组件不可变,改完要整个写回(和另外几个容器一个套路)
		final net.minecraft.core.NonNullList<ItemStack> list =
			net.minecraft.core.NonNullList.withSize(ModComponents.VOID_FILTER_SIZE, ItemStack.EMPTY);

		final var contents = backpack.get(ModComponents.VOID_FILTER);
		if (contents != null) {
			contents.copyInto(list);
		}

		// 鼠标空着 → 清除;否则 → 登记它的种类(数量固定 1,而且【不消耗鼠标上那份】)
		list.set(index, held.isEmpty() ? ItemStack.EMPTY : held.copyWithCount(1));

		backpack.set(ModComponents.VOID_FILTER,
			net.minecraft.world.item.component.ItemContainerContents.fromItems(list));

		// ⚠️ 界面侧那个 VoidContainer 的缓存也要失效 ——
		// 它是按"对象身份"判断要不要重读的,而刚才 set(...) 改的是【同一个对象】的组件。
		this.voidContainer.invalidate();
	}

	/**
	 * 「胃」还要多久消化完(单位 tick;{@code 0} = 没在消化)。
	 *
	 * <p>纯展示用 —— 进度是<b>现算</b>的(存起点、不存进度,见 {@code StomachSmelter})。
	 */
	public int stomachRemainingTicks() {
		final ItemStack backpack = this.currentBackpack();

		if (com.ergu.monsterbackpack.growth.StomachSmelter.readInput(backpack).isEmpty()) {
			return 0;
		}

		final int start = backpack.getOrDefault(ModComponents.STOMACH_START, -1);
		if (start < 0) {
			return 0;
		}

		final long now = this.playerInventory.player.level().getGameTime();
		// ⚠️ 和 StomachSmelter.tick 用【同一个】算法 —— 两边都用 ticksFor(backpack),
		// 否则界面上显示"还要 3 秒"而实际 5 秒才好(那是"看着对、实际不对",坑 27 的同类)。
		final int needed = com.ergu.monsterbackpack.growth.StomachSmelter.ticksFor(backpack);

		return (int) Math.max(0, needed - (now - start));
	}

	/**
	 * Shift + 左键 = 一键转移(背包 ↔ 玩家物品栏)。
	 *
	 * <h2>两处"不用自己写"的安全保障</h2>
	 * <ul>
	 *   <li><b>未解锁的格子自动被跳过</b> —— {@code moveItemStackTo} 会逐个问
	 *       {@code slot.mayPlace(...)},而 {@link BackpackSlot#mayPlace} 对锁着的格子返回 false。</li>
	 *   <li><b>背包/潜影盒之类进不去</b> —— 同一个钩子还会问
	 *       {@code Item.canFitInsideContainerItems()},本 mod 的背包对它返回 false。</li>
	 * </ul>
	 */
	@Override
	public ItemStack quickMoveStack(final Player player, final int slotIndex) {
		final Slot slot = this.slots.get(slotIndex);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}

		final ItemStack stack = slot.getItem();
		final ItemStack original = stack.copy();

		if (slotIndex < GRID_SLOTS) {
			// 背包 → 玩家物品栏(从后往前找空位,先塞快捷栏)
			//
			// ⚠️⚠️ 上界用 playerInvEnd,【不要】用 slots.size() ——
			// 后者会把侧签槽和 25 个虚空槽一起算进来,
			// 结果 Shift 取出的东西被塞进虚空样本槽(2026-10-15 用户实测"收纳坏了")。
			if (!this.moveItemStackTo(stack, PLAYER_INV_START, this.playerInvEnd, true)) {
				return ItemStack.EMPTY;
			}
		} else {
			// 玩家物品栏 → 背包(只进已解锁的格,由 mayPlace 兜底)
			if (!this.moveItemStackTo(stack, 0, GRID_SLOTS, false)) {
				return ItemStack.EMPTY;
			}
		}

		if (stack.isEmpty()) {
			slot.set(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}

		return original;
	}

	/**
	 * 背包界面里<b>禁用数字键换位</b>({@link ContainerInput#SWAP})。
	 *
	 * <h2>为什么整个禁掉,而不是逐条堵</h2>
	 * 数字键(1~9,以及 40 = 副手)的语义是"把鼠标悬停的槽位与快捷栏某一格交换"。
	 * 它走的是 {@code Slot.setByPlayer(...)},而这条路径<b>不查 {@code mayPlace}</b> ——
	 * 想靠重写槽位方法堵住它,得逐条列举,既容易漏(实测已经漏过两次:
	 * 先是"能切走",再是"别的物品能切进来"),代码也难读。
	 *
	 * <p>而它在背包界面里的<b>价值本来就低</b>:有 <b>Shift + 左键</b>一键转移,
	 * 来回搬东西比数字键顺手得多。所以一刀切更可靠。
	 *
	 * <p>⚠️ 只拦 SWAP —— 拿取、放下、Shift 转移、Q 丢弃、拖拽都照常。
	 */
	@Override
	public void clicked(
		final int slotIndex,
		final int buttonNum,
		final ContainerInput input,
		final Player player
	) {
		if (input == ContainerInput.SWAP) {
			return;
		}
		super.clicked(slotIndex, buttonNum, input, player);
	}

	/**
	 * 界面还有效吗 —— <b>⚠️ 不能恒 {@code true}</b>。
	 *
	 * <h2>为什么必须真判</h2>
	 * 方块被挖掉 / 被苦力怕炸掉、物品被 Q 丢出去或换了格之后,菜单以前<b>照样开着</b>,
	 * 于是所有写操作都落在 {@code ItemStack.EMPTY} 上(<b>污染全局空栈</b>),
	 * 玩家放的 84 格东西静默蒸发。
	 *
	 * <p>原版每 tick 都会问一次这个方法(服务端判定后自动关界面),
	 * 所以这里是"背包不在了 ⇒ 界面自己关"的最省事一道闸,不必到处写检查。
	 */
	@Override
	public boolean stillValid(final Player player) {
		if (!player.isAlive()) {
			return false;
		}

		if (this.openData.isBlock()) {
			if (!(player.level().getBlockEntity(this.openData.pos())
					instanceof MonsterBackpackBlockEntity)) {
				return false;
			}
			// 8 格内 —— 与箱子同一个量级,够玩家走动,又不至于隔着半张地图开着
			return player.distanceToSqr(Vec3.atCenterOf(this.openData.pos())) <= 64.0;
		}

		return isBackpackStack(resolveFromPlayer(player, this.openData.source()));
	}

	// ⚠️ 刻意【不覆写】removed():
	// 内容存在物品/方块实体的组件上,关界面就该留着(工作区约定坑 19)。
}

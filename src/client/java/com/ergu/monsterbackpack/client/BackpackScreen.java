package com.ergu.monsterbackpack.client;

import java.util.HashSet;
import java.util.Set;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

import com.ergu.monsterbackpack.growth.BackpackGrowth;
import com.ergu.monsterbackpack.menu.BackpackContainer;
import com.ergu.monsterbackpack.menu.BackpackMenu;
import com.ergu.monsterbackpack.menu.BackpackSlot;
import com.ergu.monsterbackpack.menu.CharmSlot;
import com.ergu.monsterbackpack.menu.FeedSlot;
import com.ergu.monsterbackpack.menu.StomachSlot;
import com.ergu.monsterbackpack.menu.VoidSlot;
import com.ergu.monsterbackpack.net.SelectPagePayload;
import com.ergu.monsterbackpack.net.SelectTabPayload;
import com.ergu.monsterbackpack.net.SetVoidSamplePayload;
import com.ergu.monsterbackpack.net.SortingPayload;
import com.ergu.monsterbackpack.net.ToggleAutoCollectPayload;
import com.ergu.monsterbackpack.net.ToggleVoidPayload;
import com.ergu.monsterbackpack.net.UnlockSkillPayload;
import com.ergu.monsterbackpack.skill.SkillNodes;
import com.ergu.monsterbackpack.skill.SkillUnlock;

/**
 * 背包界面(物品形态 + 方块形态共用)。
 *
 * <h2>⚠️ 两个回调的坐标系不一样</h2>
 * <pre>
 * extractBackground(...)   ★屏幕绝对 —— 要自己加 leftPos / topPos
 *   graphics.pose().translate(leftPos, topPos)
 * extractLabels(...)       ★已平移 —— 只写相对面板坐标(再加一次 leftPos 就画到屏幕外)
 * extractSlots(...)        ★同理
 * </pre>
 *
 * <h2>⚠️ 颜色必须是 ARGB</h2>
 * {@code 0xFFFFFF} 是 24 位 RGB,<b>alpha = 0 → 完全透明</b>,画了看不见。必须 {@code 0xFFFFFFFF}。
 *
 * <h2>版面(340 × 240)—— 交互范式照精妙背包</h2>
 * <pre>
 *            ┌──────────────────────┐
 *   弹窗 ←   │签│   背包 12×7        │饱│
 *  (面板外)  │条│                    │食│
 *            └──────────────────────┘
 *   x -104..0     x 2..38   x 80..296   x 300..338
 * </pre>
 *
 * <h2>两个"手感"功能</h2>
 * <ul>
 *   <li><b>侧签条半隐藏</b>:平时只从面板左边缘露一点点,<b>鼠标指上去整个滑出来</b>
 *       (用户 2026-10-15 要求),这样它不占视线。</li>
 *   <li><b>Shift + 划动取物</b>:按住 Shift 拖动鼠标划过背包格,
 *       <b>经过的每一格都会被取出</b>(发 {@code QUICK_MOVE},原版会自己判断放不放得下)。</li>
 * </ul>
 */
public class BackpackScreen extends AbstractContainerScreen<BackpackMenu> {

	// 原版容器风格的配色(全部 ARGB)
	private static final int PANEL_BG = 0xFFC6C6C6;
	private static final int PANEL_HIGHLIGHT = 0xFFFFFFFF;
	private static final int PANEL_SHADOW = 0xFF555555;
	// ⚠️ 这四个是槽位框用的(手绘)——
	// 曾经在"界面美化第二批"里被删掉(因为换成原版精灵了),**当天回退时恢复**。
	private static final int SLOT_BG = 0xFF8B8B8B;
	/** 未解锁的格子:更暗。 */
	private static final int SLOT_LOCKED = 0xFF5B5B5B;
	private static final int SLOT_SHADOW = 0xFF373737;
	private static final int SLOT_HIGHLIGHT = 0xFFFFFFFF;

	private static final int TEXT_NORMAL = 0xFF404040;
	private static final int TEXT_DIM = 0xFF707070;

	/** 侧签条。 */
	private static final int TAB_BG = 0xFF9E9E9E;
	private static final int TAB_ACTIVE_BG = 0xFFE8E8E8;
	private static final int TAB_TEXT = 0xFF2A2A2A;
	private static final int TAB_TEXT_DIM = 0xFF6A6A6A;

	/** 弹窗底(比面板稍深,让它"浮"在外面)。 */
	private static final int POPUP_BG = 0xFFB0B0B0;

	/** 饱食度条。 */
	private static final int BAR_BG = 0xFF4A4A4A;
	private static final int BAR_FILL = 0xFFB03030;
	private static final int BAR_FRAME = 0xFF2A2A2A;

	/** 信息区(面板右侧)的起点。 */
	private static final int INFO_X = 300;

	// ────────────────────────────────────────────────────────────────
	// 🆕 顶部主页签(背包 / 成长)—— 画在【面板内的标题条】里
	// ────────────────────────────────────────────────────────────────
	//
	// ⚠️⚠️ 为什么【不】画在面板外面(我第一版就是那么写的,算了一下发现不行):
	//   topPos = (逻辑高 - 240) / 2,而 GUI scale 4 + 1080p 下逻辑高只有 270
	//   ⇒ topPos = 15,而面板外的页签要占 y-18 ⇒ 顶边落在 -3,【画到屏幕外】。
	// 也不该画在面板下方(那里是玩家物品栏)。
	//
	// ⇒ 放进面板顶部的【标题条】里(y+0 .. y+16)。那一带本来是空的
	//   (见 extractBackground:渐变从 y+18 才开始,分隔线在 y+16),
	//   正好是留给"标签页"的高度。**这样也不占用 84 个格子的任何空间。**
	//
	// ⚠️ 代价:它是"面板内"的区域,原版 hasClickedOutside 会认为它【在面板里】——
	//   这反而是好事,不用再为它写几何判断了(但 mouseClicked 里仍要【最先】拦,
	//   否则会落到原版"点空处"的逻辑上)。

	/** 页签尺寸。⚠️ 高必须 <= 16 —— 那是标题条的高度,超了就压到分隔线。 */
	private static final int PAGE_TAB_W = 62;
	private static final int PAGE_TAB_H = 15;
	/** 第一个页签的左边缘(相对面板)。 */
	private static final int PAGE_TAB_X = 6;
	/** 页签的 y —— 在标题条里垂直居中(标题条 = y+0 .. y+16)。 */
	private static final int PAGE_TAB_Y = 1;
	/** 页签之间的间隙。 */
	private static final int PAGE_TAB_GAP = 2;

	private static final int PAGE_TAB_BG = 0xFF9E9E9E;
	private static final int PAGE_TAB_ACTIVE_BG = 0xFFE8E8E8;
	private static final int PAGE_TAB_TEXT = 0xFF2A2A2A;
	private static final int PAGE_TAB_TEXT_DIM = 0xFF6A6A6A;

	// ────────────────────────────────────────────────────────────────
	// 🆕 技能树(成长页)—— 三条竖列
	// ────────────────────────────────────────────────────────────────
	//
	// 布局见 DESIGN.md §七「成长页布局」:3 列 × 列宽 100 × 节点高 24。

	/**
	 * 三列的左边缘 / 列宽 / 列间距。
	 *
	 * <p>⚠️ 这三个数要满足:<b>内容宽 ≤ 可视区宽</b>(340 - 2 = 338),否则一进成长页
	 * 就会显示"可以拖动"的提示 —— 明明全部看得见却提示能拖,玩家会以为画错了。
	 *
	 * <pre>
	 *   8×2 + 3×104 + 2×4 = 336  ✅ ≤ 338(留 2px)
	 *   10×2 + 3×106 + 2×4 = 346  ❌ 超 8px —— 第一版就是这个数
	 * </pre>
	 *
	 * <p>纵向不用管:内容高随节点数增长,拖就是了(那正是"可以拖动"要解决的问题)。
	 */
	private static final int SKILL_COL_X = 8;
	private static final int SKILL_COL_W = 104;
	private static final int SKILL_COL_GAP = 4;
	/**
	 * 节点高 / 间距 / 第一行的 y。
	 *
	 * <p>⚠️⚠️ 这三个数是被<b>最长的那条分支</b>倒推出来的,不是随便定的 ——
	 * 本能线现在有 <b>9 个</b>节点(自动进食 / 投喂 / 大名单 / 砂囊 I·II·III / 挂饰槽 ×2 / 挂饰组合),
	 * 面板可用高度约 236px:
	 *
	 * <pre>
	 *   24 + 8 × 26 + 24 = 256  ❌ 超了 20px —— 最后一个节点会被 skillNodeRect 跳过
	 *   20 + 8 × 22 + 20 = 216  ✅ 留 20px 余量
	 * </pre>
	 *
	 * <p>⇒ <b>以后往任何一条分支加节点,都要重算这三个数</b>。
	 * {@code skillNodeRect} 里的越界判断会兜住(不让它画到面板外),
	 * 但<b>代价是那个节点静默消失</b> —— 和"没做"一样难查。
	 *
	 * <p>⚠️ {@link #SKILL_TOP_Y} 还必须 <b>> 16</b>:顶部页签占了标题条(y+1..y+16),
	 * 而列标题画在 {@code SKILL_TOP_Y - 12} —— 若 TOP_Y 是 24,列标题就在 y+12,
	 * <b>正好压在页签上</b>。取 32 ⇒ 列标题在 y+20,让开了。
	 */
	private static final int SKILL_NODE_H = 20;
	private static final int SKILL_NODE_GAP = 2;
	private static final int SKILL_TOP_Y = 32;

	/** 三分支的主色(ARGB,别写 24 位)—— 守卫红 / 收纳蓝 / 本能绿。 */
	private static final int BRANCH_GUARDIAN = 0xFFB03030;
	private static final int BRANCH_STORAGE = 0xFF3060B0;
	private static final int BRANCH_INSTINCT = 0xFF30A040;

	/** 节点三种状态的底色 / 文字色。 */
	private static final int NODE_DONE_BG = 0xFF3A3A3A;
	private static final int NODE_READY_BG = 0xFF2A2A2A;
	private static final int NODE_LOCKED_BG = 0xFF6A6A6A;
	private static final int NODE_TEXT = 0xFFFFFFFF;
	private static final int NODE_TEXT_LOCKED = 0xFF9A9A9A;

	// ────────────────────────────────────────────────────────────────
	// 🆕 技能树的【拖动】状态
	// ────────────────────────────────────────────────────────────────
	//
	// 用户 2026-10-15 要求:"做成类似成就那种,嵌在框里可以拖动,这样技能树不管多长都放得下"。
	// 成就界面(AdvancementsScreen)确实是这个手感:按住拖画布 + 滚轮缩放/滚动。
	//
	// ⚠️ 偏移是【内容坐标 → 屏幕坐标】的平移量,恒为 <= 0
	// (0 = 内容贴可视区左上;越负 = 内容往左上被推出去了)。
	// 范围由 clampSkillOffset() 夹住。

	/** 技能树画布的拖动偏移。 */
	private int skillOffsetX = 0;
	private int skillOffsetY = 0;

	/** 正在拖技能树 —— 用来区分"拖动"和"点节点"。 */
	private boolean skillDragging = false;

	/** 按下左键时鼠标在哪 —— 拖动时用来算位移。 */
	private double skillDragAnchorX = 0;
	private double skillDragAnchorY = 0;

	/**
	 * 🆕 判定"这是一次拖动而不是点击"的阈值(像素)。
	 *
	 * <p>⚠️ 必须有这个阈值:鼠标按下时手会抖,位移 1px 就当成拖动的话,
	 * <b>节点永远点不中</b>(表现为"点了没反应" —— 最难查的那种)。
	 * 原版 GUI 里 4px 是个常见取值。
	 */
	private static final int SKILL_DRAG_THRESHOLD = 4;

	// ------------------------------------------------------------ 划动取物状态
	/** 正在 Shift 划动取物。 */
	private boolean shiftDragging = false;
	/** 这一轮划动里已经取过的格子(免得来回蹭同一格反复触发)。 */
	private final Set<Integer> dragPicked = new HashSet<>();

	public BackpackScreen(final BackpackMenu menu, final Inventory inventory, final Component title) {
		super(menu, inventory, title, BackpackMenu.PANEL_WIDTH, BackpackMenu.PANEL_HEIGHT);
		this.inventoryLabelY = BackpackMenu.INVENTORY_LABEL_Y;
	}

	/**
	 * 面板默认是<b>屏幕水平居中</b>的,但我们左侧要放弹窗 —— 所以整体<b>右移</b>,
	 * 给弹窗腾出地方(不这么做的话弹窗会有一部分跑到屏幕外)。
	 */
	@Override
	protected void init() {
		super.init();
		this.leftPos += BackpackMenu.POPUP_WIDTH / 2;
	}

	// ------------------------------------------------------------ 绘制

	@Override
	public void extractBackground(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float partialTick
	) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);

		// 🆕 先把"材料计数"的帧内缓存清掉 —— 见 BackpackMenu.materialCountCache 的注释。
		//
		// ⚠️ 必须在这里(绘制【之前】)清:成长页每个节点都要问一次"材料够不够",
		// 缓存让那次询问只把背包数一遍;清早了会读到上一帧的数字,清晚了这一帧就白缓存了。
		this.menu.invalidateMaterialCounts();

		// ★屏幕绝对坐标
		final int x = this.leftPos;
		final int y = this.topPos;

		extractPopup(graphics, x, y, mouseX, mouseY);

		// 面板底
		bevel(graphics, x, y, this.imageWidth, this.imageHeight,
			PANEL_BG, PANEL_HIGHLIGHT, PANEL_SHADOW);

		// 🆕 极轻的上亮下暗 —— 让面板"有厚度"而不是一张纸片。
		//
		// ⚠️ 幅度必须小(0xC6C6C6 → 0xB4B4B4)。原版容器面板是纯色的,
		// 做强烈渐变会立刻"不像原版",反而显廉价。
		//
		// ⚠️ fillGradient 【不做坐标归一化】(fill 会自动交换首尾,它不会)——
		// 必须保证 x0<x1 且 y0<y1。
		graphics.fillGradient(x + 1, y + 18, x + this.imageWidth - 1, y + this.imageHeight - 1,
			0xFFC6C6C6, 0xFFB4B4B4);

		// 🆕 三段分隔:标题条下沿 / 物品栏区上沿。
		//
		// 三家成熟 mod 都是把这两条线【烘焙进面板贴图】的
		// (原版 ContainerScreen.java:25-26 把面板分两次贴:标题+槽区 / 物品栏区)。
		// 我们零 PNG,所以用"1px 暗 + 1px 亮"手画 —— 这正是原版面板内阴影的观感。
		//
		// 效果:面板从"一整块灰"变成"标题 / 背包 / 物品栏"三段,视线有落点。
		graphics.fill(x + 1, y + 16, x + this.imageWidth - 1, y + 17, 0xFF8B8B8B);
		graphics.fill(x + 1, y + 17, x + this.imageWidth - 1, y + 18, 0xFFFFFFFF);

		// ⚠️ 第二条(物品栏区上沿)【只在背包页画】——
		// 成长页没有物品栏,留着这条线会让技能树看着像"被切成两半"
		// (2026-10-15 用户截图实测:一条横线横穿技能树中间)。
		if (this.menu.openPage() == BackpackMenu.PAGE_BACKPACK) {
			final int invTop = BackpackMenu.INVENTORY_LABEL_Y - 4;
			graphics.fill(x + 1, y + invTop, x + this.imageWidth - 1, y + invTop + 1, 0xFF8B8B8B);
			graphics.fill(x + 1, y + invTop + 1, x + this.imageWidth - 1, y + invTop + 2, 0xFFFFFFFF);
		}

		// 每个格子的凹陷框 —— 【按类型分别处理】。
		//
		// ⚠️ 三种槽的"画不画 / 画什么色"规则各不相同(2026-10-15 实测踩过两次):
		//   · 背包格   —— 永远画;未解锁的画【暗色】(让玩家知道"这里以后会长开")
		//   · 侧签槽   —— 只有【它那个签打开着】才画;槽本身没解锁的画【暗色】
		//   · 物品栏格 —— 永远画,正常色
		// 早先图省事写成"不 active 就跳过",结果未解锁的背包格和挂饰槽【连框都没有】,
		// 看起来像"功能没做",而不是"还没解锁"。
		for (Slot slot : this.menu.slots) {
			if (slot instanceof BackpackSlot backpackSlot) {
				slotFrame(graphics, x + slot.x - 1, y + slot.y - 1, !backpackSlot.isUnlocked());
				continue;
			}

			if (slot instanceof FeedSlot) {
				if (this.menu.openTab() != BackpackMenu.TAB_FEED) {
					continue;
				}
				slotFrame(graphics, x + slot.x - 1, y + slot.y - 1, false);
				continue;
			}

			if (slot instanceof CharmSlot charmSlot) {
				if (this.menu.openTab() != BackpackMenu.TAB_CHARM) {
					continue;
				}
				slotFrame(graphics, x + slot.x - 1, y + slot.y - 1, !charmSlot.isUnlocked());
				continue;
			}

			if (slot instanceof StomachSlot) {
				if (this.menu.openTab() != BackpackMenu.TAB_STOMACH) {
					continue;
				}
				slotFrame(graphics, x + slot.x - 1, y + slot.y - 1, false);
				continue;
			}

			// 玩家物品栏(普通 Slot)
			slotFrame(graphics, x + slot.x - 1, y + slot.y - 1, false);
		}

		// 🆕 虚空的样本格 —— ⚠️ 它们【不在 menu.slots 里】(见 BackpackMenu.voidSlots),
		// 所以原版那圈循环画不到它们,得在这里单独画。
		if (this.menu.openTab() == BackpackMenu.TAB_VOID) {
			for (final VoidSlot slot : this.menu.voidSlots) {
				if (slot.x == BackpackMenu.HIDDEN_SLOT_X) {
					continue;      // 未解锁的已经被踢出屏幕
				}
				slotFrame(graphics, x + slot.x - 1, y + slot.y - 1, false);
			}
		}

		// 🆕 成长页:背包页那一套(侧签 / 饱食度条 / 开关按钮)全部不画,
		// 整个面板让给技能树(§七「成长页布局」)。
		//
		// ⚠️ 槽框那圈循环【不用跳过】—— 成长页时它们的 x 已经被 layoutGridSlots()
		// 设成 HIDDEN_SLOT_X(-2000),画出来也在屏幕外,看不见也点不到。
		// 硬加一个 if 反而多一处"要记得同步改"的地方(坑 27 的同类)。
		if (this.menu.openPage() == BackpackMenu.PAGE_GROWTH) {
			extractSkillTree(graphics, x, y, mouseX, mouseY);
			extractPageTabs(graphics, x, y, mouseX, mouseY);
			return;
		}

		extractSideTabs(graphics, x, y, mouseX, mouseY);
		extractSatietyBar(graphics, x, y);
		extractAutoCollectButton(graphics, x, y, mouseX, mouseY);
		extractSortButton(graphics, x, y, mouseX, mouseY);
		extractVoidButton(graphics, x, y, mouseX, mouseY);
		extractPageTabs(graphics, x, y, mouseX, mouseY);
	}

	/**
	 * 🆕 顶部主页签(背包 / 成长)—— 画在面板【上方外面】。
	 *
	 * <p>⚠️ 本方法在 {@code extractBackground} 里跑,所以是<b>屏幕绝对坐标</b>。
	 *
	 * <p>三态和侧签保持同一套视觉语言(§七 的美化第一批):
	 * <b>选中的凸起 + 亮底 / 悬停的亮一点 / 其余凹下去</b>。
	 */
	private void extractPageTabs(final GuiGraphicsExtractor graphics, final int x, final int y,
		final int mouseX, final int mouseY) {

		for (int page = 0; page < BackpackMenu.PAGE_COUNT; page++) {
			final int tx = x + PAGE_TAB_X + page * (PAGE_TAB_W + PAGE_TAB_GAP);
			final int ty = y + PAGE_TAB_Y;

			final boolean active = this.menu.openPage() == page;
			final boolean hovered = !active
				&& mouseX >= tx && mouseX < tx + PAGE_TAB_W
				&& mouseY >= ty && mouseY < ty + PAGE_TAB_H;

			// ⚠️ 选中的是"凸起"(上亮下暗),未选中的是"凹下去"(上暗下亮)——
			// 就是靠把 bevel 的两个参数对调实现的,和侧签一个套路。
			if (active) {
				bevel(graphics, tx, ty, PAGE_TAB_W, PAGE_TAB_H,
					PAGE_TAB_ACTIVE_BG, PANEL_HIGHLIGHT, PANEL_SHADOW);
			} else if (hovered) {
				bevel(graphics, tx, ty, PAGE_TAB_W, PAGE_TAB_H,
					0xFFC0C0C0, PANEL_SHADOW, PANEL_HIGHLIGHT);
			} else {
				bevel(graphics, tx, ty, PAGE_TAB_W, PAGE_TAB_H,
					PAGE_TAB_BG, PANEL_SHADOW, PANEL_HIGHLIGHT);
			}

			final Component label = Component.translatable(BackpackMenu.PAGE_KEYS[page]);
			// ⚠️ 自己算宽度居中 —— centeredText 固定带阴影,和别处 text(..., false) 不一致(坑 15)
			graphics.text(this.font, label,
				tx + (PAGE_TAB_W - this.font.width(label)) / 2,
				ty + (PAGE_TAB_H - 8) / 2 + 1,
				active ? PAGE_TAB_TEXT : PAGE_TAB_TEXT_DIM,
				false);
		}
	}

	/** 🆕 鼠标下是哪个主页签;没有返回 -1。 */
	private int pageAt(final int mouseX, final int mouseY) {
		for (int page = 0; page < BackpackMenu.PAGE_COUNT; page++) {
			final int tx = this.leftPos + PAGE_TAB_X + page * (PAGE_TAB_W + PAGE_TAB_GAP);
			final int ty = this.topPos + PAGE_TAB_Y;
			if (mouseX >= tx && mouseX < tx + PAGE_TAB_W
				&& mouseY >= ty && mouseY < ty + PAGE_TAB_H) {
				return page;
			}
		}
		return -1;
	}

	/**
	 * 🆕 技能树的**节点矩形(内容坐标系)**。
	 *
	 * <p>⚠️ 这是<b>内容坐标</b>,不是屏幕坐标 —— 真正画的时候要加
	 * {@link #skillOffsetX} / {@link #skillOffsetY}(用户可拖动)。
	 *
	 * <p>⚠️⚠️ 这里【不再做越界判断】(上一版有,而且因此静默吃掉过节点):
	 * 现在整块内容可以拖动,不存在"放不下"这回事。
	 * 改成由 {@link #skillContentHeight()} 告诉外面"内容多高",再去限制拖动范围。
	 *
	 * <p>布局由 {@code SkillNodes.ofBranch(...)} 的<b>顺序</b>决定 ——
	 * 改节点表的顺序就是改界面顺序,刻意不另设坐标(§六 说"顺序就是成长路线")。
	 */
	private static int[] skillNodeRect(final SkillNodes.Branch branch, final int indexInBranch) {
		final int col = branch.ordinal();
		final int x = SKILL_COL_X + col * (SKILL_COL_W + SKILL_COL_GAP);
		final int y = SKILL_TOP_Y + indexInBranch * (SKILL_NODE_H + SKILL_NODE_GAP);
		return new int[] {x, y, SKILL_COL_W, SKILL_NODE_H};
	}

	/** 🆕 技能树**内容的宽**(三条列 + 左右留边)。 */
	private static int skillContentWidth() {
		return SKILL_COL_X * 2 + 3 * SKILL_COL_W + 2 * SKILL_COL_GAP;
	}

	/**
	 * 🆕 技能树**内容的高** —— 按<b>最长的那条分支</b>算。
	 *
	 * <p>⚠️ 这就是"以后再往树里加节点也不会画不下"的关键:
	 * 内容变高 ⇒ 拖动范围自动变大,不需要改任何常量。
	 */
	private static int skillContentHeight() {
		int most = 0;
		for (final SkillNodes.Branch branch : SkillNodes.Branch.values()) {
			most = Math.max(most, SkillNodes.ofBranch(branch).size());
		}
		// 顶部给列标题留 12px(标题画在 SKILL_TOP_Y - 12)
		return Math.max(SKILL_TOP_Y, most * (SKILL_NODE_H + SKILL_NODE_GAP)) + 6;
	}

	/**
	 * 🆕 技能树的**可视区(屏幕坐标)** —— 面板内、页签下面那整块。
	 *
	 * <p>⚠️ 这是绘制裁剪(scissor)和命中判定的**共同依据** ——
	 * 两处用同一个矩形,才不会出现"看得到但点不到"(坑 25 的同类)。
	 */
	private int[] skillViewRect() {
		// 页签底边 = PAGE_TAB_Y + PAGE_TAB_H = 1 + 15 = 16,再留 2px 透气
		final int top = this.topPos + PAGE_TAB_Y + PAGE_TAB_H + 2;
		return new int[] {
			this.leftPos + 1,
			top,
			this.leftPos + this.imageWidth - 1,
			this.topPos + this.imageHeight - 1
		};
	}

	/**
	 * 🆕 把拖动偏移**夹回合法范围**。
	 *
	 * <pre>
	 *   内容比可视区小 → 偏移只能是 0(内容贴左上,不居中 —— 居中的话拖动会显得"黏")
	 *   内容比可视区大 → 允许在 [可视区 - 内容, 0] 之间拖
	 * </pre>
	 *
	 * <p>⚠️ 这个方法必须在<b>每次拖动之后</b>调 —— 不然能把树拖出屏幕再也找不回来。
	 */
	private void clampSkillOffset() {
		final int[] view = this.skillViewRect();
		final int viewW = view[2] - view[0];
		final int viewH = view[3] - view[1];

		final int minX = Math.min(0, viewW - skillContentWidth());
		final int minY = Math.min(0, viewH - skillContentHeight());

		this.skillOffsetX = Math.max(minX, Math.min(0, this.skillOffsetX));
		this.skillOffsetY = Math.max(minY, Math.min(0, this.skillOffsetY));
	}

	/** 🆕 分支主色。 */
	private static int branchColor(final SkillNodes.Branch branch) {
		return switch (branch) {
			case GUARDIAN -> BRANCH_GUARDIAN;
			case STORAGE -> BRANCH_STORAGE;
			case INSTINCT -> BRANCH_INSTINCT;
		};
	}

	/**
	 * 🆕 成长页 —— 画技能树(三条竖列,**可拖动**)。
	 *
	 * <p>⚠️ 本方法在 {@code extractBackground} 里跑 ⇒ <b>屏幕绝对坐标</b>。
	 *
	 * <h2>⚠️ 裁剪(scissor)是必须的</h2>
	 * 内容是"一整块画布",会比可视区大 —— 不裁剪的话,节点会画到
	 * 顶部页签上、底部物品栏区上(2026-10-15 用户截图实测:挂饰组合那三个
	 * 节点画到了物品栏标签下面)。
	 *
	 * <h2>节点的三种状态</h2>
	 * 见 DESIGN.md §七:已解锁 / 可解锁 / 锁定。
	 * ⚠️ <b>锁定的也要画出来(灰底 + 暗字)</b> —— 那是"路线图"作用的关键。
	 */
	private void extractSkillTree(final GuiGraphicsExtractor graphics, final int x, final int y,
		final int mouseX, final int mouseY) {

		final int[] view = this.skillViewRect();

		// ⚠️ 裁剪区传的是【屏幕绝对坐标】—— 和 view 同一套(extractBackground 的坐标系)
		graphics.enableScissor(view[0], view[1], view[2], view[3]);

		// 内容坐标 → 屏幕坐标的平移量
		final int ox = x + this.skillOffsetX;
		final int oy = y + this.skillOffsetY;

		for (final SkillNodes.Branch branch : SkillNodes.Branch.values()) {
			// 列标题(分支名 + 一条主色横线)
			final int col = branch.ordinal();
			final int colX = ox + SKILL_COL_X + col * (SKILL_COL_W + SKILL_COL_GAP);
			final int colY = oy + SKILL_TOP_Y - 12;

			final Component title = Component.translatable(
				"gui.ergu_monster_backpack.branch." + branch.name().toLowerCase(java.util.Locale.ROOT));
			graphics.text(this.font, title, colX + 2, colY, TEXT_NORMAL, false);
			graphics.fill(colX, colY + 10, colX + SKILL_COL_W, colY + 11, branchColor(branch));

			final var nodes = SkillNodes.ofBranch(branch);
			for (int i = 0; i < nodes.size(); i++) {
				final int[] rect = skillNodeRect(branch, i);
				this.extractSkillNode(graphics, ox, oy, mouseX, mouseY, nodes.get(i), rect);
			}
		}

		graphics.disableScissor();

		// 🆕 可拖动时给个提示 —— 否则玩家不知道能拖(成就界面也有这个问题的)
		if (skillContentHeight() > view[3] - view[1] || skillContentWidth() > view[2] - view[0]) {
			final Component hint = Component.translatable("gui.ergu_monster_backpack.skill.drag");
			graphics.text(this.font, hint,
				view[2] - this.font.width(hint) - 3, view[3] - 11, TEXT_DIM, false);
		}
	}

	/** 🆕 单个技能节点。 */
	private void extractSkillNode(final GuiGraphicsExtractor graphics, final int x, final int y,
		final int mouseX, final int mouseY, final SkillNodes.Node node, final int[] rect) {

		final int nx = x + rect[0];
		final int ny = y + rect[1];
		final int w = rect[2];
		final int h = rect[3];

		final SkillUnlock.Status status = this.menu.skillStatus(node);
		final int accent = branchColor(node.branch());
		final boolean hovered = mouseX >= nx && mouseX < nx + w && mouseY >= ny && mouseY < ny + h;

		switch (status) {
			case MAXED -> {
				// 已解锁:分支色实心底
				bevel(graphics, nx, ny, w, h, accent, PANEL_HIGHLIGHT, 0xFF202020);
			}
			case READY -> {
				// 可解锁:深底 + 分支色描边 —— 一眼能看出"这个现在能点"
				bevel(graphics, nx, ny, w, h, NODE_READY_BG, 0xFF555555, 0xFF111111);
				graphics.outline(nx, ny, w, h, accent);
			}
			default -> {
				// 锁定(阶段不够 / 材料不够):灰底 —— 但仍然显示名字
				bevel(graphics, nx, ny, w, h, NODE_LOCKED_BG, 0xFF808080, 0xFF4A4A4A);
			}
		}

		// 🔹 自动解锁的节点加个标记,和"要花材料的"区分开(§六:它们起引导作用)
		final Component label = node.isAutomatic()
			? Component.literal("🔹 ").append(Component.translatable(node.nameKey()))
			: Component.translatable(node.nameKey());

		graphics.text(this.font, label,
			nx + 4,
			ny + (h - 8) / 2 + 1,
			status == SkillUnlock.Status.MAXED ? NODE_TEXT
				: status == SkillUnlock.Status.READY ? NODE_TEXT : NODE_TEXT_LOCKED,
			false);

		// 悬停 → 真 tooltip(材料清单)
		if (hovered) {
			// ⚠️ 用 4 参那个重载不行 —— 26.2 的签名是
			// (Font, List<Component>, Optional<TooltipComponent>, int, int),中间那个别漏
			// (GuiGraphicsExtractor.java:1079)。
			graphics.setTooltipForNextFrame(this.font, this.skillTooltip(node, status),
				java.util.Optional.empty(), mouseX, mouseY);
		}
	}

	/** 🆕 节点的悬停说明:名字 + 阶段要求 + 材料清单。 */
	private java.util.List<Component> skillTooltip(final SkillNodes.Node node,
		final SkillUnlock.Status status) {

		final java.util.List<Component> lines = new java.util.ArrayList<>();
		lines.add(Component.translatable(node.nameKey()));

		if (status == SkillUnlock.Status.MAXED) {
			lines.add(Component.translatable("gui.ergu_monster_backpack.skill.done")
				.withStyle(net.minecraft.ChatFormatting.GREEN));
			return lines;
		}

		lines.add(Component.translatable("gui.ergu_monster_backpack.skill.stage", node.requiredStage())
			.withStyle(status == SkillUnlock.Status.STAGE_LOCKED
				? net.minecraft.ChatFormatting.RED : net.minecraft.ChatFormatting.GRAY));

		// 🆕 前置节点 —— ⚠️ 要写清【是哪一个】,只写"需要前置"玩家不知道去点谁
		if (status == SkillUnlock.Status.PREREQ_LOCKED && node.hasPrerequisite()) {
			lines.add(Component.translatable("gui.ergu_monster_backpack.skill.prereq",
					Component.translatable("gui.ergu_monster_backpack.skill." + node.requiredNode()))
				.withStyle(net.minecraft.ChatFormatting.RED));
		}

		if (node.isAutomatic()) {
			lines.add(Component.translatable("gui.ergu_monster_backpack.skill.auto")
				.withStyle(net.minecraft.ChatFormatting.AQUA));
			return lines;
		}

		// ⚠️ 材料用原版物品名 —— 不自己写 lang 键,原版自己会解析(item.minecraft.xxx)
		for (final SkillNodes.Cost cost : node.costs()) {
			final int have = this.menu.skillMaterialCount(cost.item());
			final boolean enough = have >= cost.count();
			lines.add(Component.literal("  ")
				.append(Component.translatable(cost.item().getDescriptionId()))
				.append(Component.literal(" " + have + "/" + cost.count()))
				.withStyle(enough ? net.minecraft.ChatFormatting.GRAY : net.minecraft.ChatFormatting.RED));
		}

		if (status == SkillUnlock.Status.READY) {
			lines.add(Component.translatable("gui.ergu_monster_backpack.skill.click")
				.withStyle(net.minecraft.ChatFormatting.YELLOW));
		}
		return lines;
	}

	/**
	 * 🆕 鼠标下是哪个技能节点(成长页专用);没有返回 {@code null}。
	 *
	 * <p>⚠️ 两条都必须做,少一条就会出"看得见但点不到"或者"点到看不见的":
	 * <ol>
	 *   <li><b>加拖动偏移</b> —— 内容是画布坐标,鼠标是屏幕坐标</li>
	 *   <li><b>先判断在不在可视区内</b> —— 被裁掉的部分(scissor 外面的)
	 *       虽然算得出矩形,但玩家根本看不见它,不该能点到</li>
	 * </ol>
	 */
	private SkillNodes.Node skillNodeAt(final int mouseX, final int mouseY) {
		if (this.menu.openPage() != BackpackMenu.PAGE_GROWTH) {
			return null;
		}

		// ② 先在可视区内吗?不在就一个节点都别想命中。
		final int[] view = this.skillViewRect();
		if (mouseX < view[0] || mouseX >= view[2] || mouseY < view[1] || mouseY >= view[3]) {
			return null;
		}

		// ① 屏幕坐标 → 内容坐标
		final int relX = mouseX - this.leftPos - this.skillOffsetX;
		final int relY = mouseY - this.topPos - this.skillOffsetY;

		for (final SkillNodes.Branch branch : SkillNodes.Branch.values()) {
			final var nodes = SkillNodes.ofBranch(branch);
			for (int i = 0; i < nodes.size(); i++) {
				final int[] rect = skillNodeRect(branch, i);
				if (relX >= rect[0] && relX < rect[0] + rect[2]
					&& relY >= rect[1] && relY < rect[1] + rect[3]) {
					return nodes.get(i);
				}
			}
		}
		return null;
	}

	/**
	 * 「自动收纳」开关按钮 —— 放在信息区(饱食度条下面)。
	 *
	 * <p>这是<b>背包自身的初始能力</b>,不是挂饰效果 ——
	 * 「磁石」只负责扩大吸取范围(用户 2026-10-15 的拆分)。
	 */
	private void extractAutoCollectButton(
		final GuiGraphicsExtractor graphics,
		final int x, final int y,
		final int mouseX, final int mouseY
	) {
		final boolean on = this.menu.autoCollect();
		final boolean hovered = this.autoCollectHit(mouseX, mouseY);

		// ⚠️ 手绘(回退后)—— 不再用原版 `widget/button` 精灵,见文件末尾的回退说明。
		bevel(graphics, x + INFO_X + 2, y + AUTO_COLLECT_BUTTON_Y,
			AUTO_COLLECT_BUTTON_WIDTH, AUTO_COLLECT_BUTTON_HEIGHT,
			on ? BUTTON_ON_BG : BUTTON_OFF_BG,
			PANEL_HIGHLIGHT, PANEL_SHADOW);

		if (hovered) {
			// 悬停时描一圈亮边
			graphics.outline(x + INFO_X + 1, y + AUTO_COLLECT_BUTTON_Y - 1,
				AUTO_COLLECT_BUTTON_WIDTH + 2, AUTO_COLLECT_BUTTON_HEIGHT + 2, 0xFFFFFFFF);
		}
	}

	/** 自动收纳按钮相对面板的位置(判定与绘制共用)。 */
	private static final int AUTO_COLLECT_BUTTON_Y = 106;
	private static final int AUTO_COLLECT_BUTTON_WIDTH = 36;
	private static final int AUTO_COLLECT_BUTTON_HEIGHT = 14;

	/**
	 * 🆕 「整理」按钮 —— 放在「自动收纳」下面一行。
	 *
	 * <h2>⚠️ 2026-10-15:从快捷键改成按钮(用户要求)</h2>
	 * 原定 `R` 键,用户实测「**按 R 没反应**」。原因大概率是**技能树没解锁**
	 * (服务端会拦)—— 但玩家<b>没法区分"没解锁"和"按键冲突/没生效"</b>。
	 *
	 * <p>放进界面就解决了这个问题:<b>没解锁时画成灰的</b>,
	 * 玩家一眼就知道"这里有东西,但还不能用"。
	 *
	 * <p>⚠️ 这两个常量**和自动收纳那组是分开的** ——
	 * 共用的话改一个会连带改另一个(而且它们的语义不该绑在一起)。
	 */
	private static final int SORT_BUTTON_Y = 136;
	private static final int SORT_BUTTON_WIDTH = 36;
	private static final int SORT_BUTTON_HEIGHT = 14;
	private static final int SORT_LABEL_Y = 126;

	/**
	 * 🆕 画「整理」按钮。
	 *
	 * <p>⚠️ 三种外观:
	 * <ul>
	 *   <li><b>已解锁</b> —— 正常按钮底色(和自动收纳一样用 {@link #BUTTON_ON_BG} 那种"可用"观感)</li>
	 *   <li><b>未解锁</b> —— 暗色 + 文字也暗,**点了没反应**(服务端也拦)</li>
	 *   <li><b>悬停</b> —— 描一圈白边(只有已解锁时才描,免得"不能点还亮着")</li>
	 * </ul>
	 */
	private void extractSortButton(
		final GuiGraphicsExtractor graphics,
		final int x, final int y,
		final int mouseX, final int mouseY
	) {
		final boolean unlocked = this.menu.sortUnlocked();
		final boolean hovered = this.sortButtonHit(mouseX, mouseY);

		bevel(graphics, x + INFO_X + 2, y + SORT_BUTTON_Y,
			SORT_BUTTON_WIDTH, SORT_BUTTON_HEIGHT,
			unlocked ? BUTTON_ON_BG : BUTTON_OFF_BG,
			PANEL_HIGHLIGHT, PANEL_SHADOW);

		// ⚠️ 只有解锁了才给悬停高亮 —— 未解锁时亮边会让人以为"能点"
		if (hovered && unlocked) {
			graphics.outline(x + INFO_X + 1, y + SORT_BUTTON_Y - 1,
				SORT_BUTTON_WIDTH + 2, SORT_BUTTON_HEIGHT + 2, 0xFFFFFFFF);
		}
	}

	/** 鼠标是不是在「整理」按钮上。 */
	private boolean sortButtonHit(final int mouseX, final int mouseY) {
		final int bx = this.leftPos + INFO_X + 2;
		final int by = this.topPos + SORT_BUTTON_Y;

		return mouseX >= bx && mouseX < bx + SORT_BUTTON_WIDTH
			&& mouseY >= by && mouseY < by + SORT_BUTTON_HEIGHT;
	}

	/**
	 * 按钮上方那行说明字的位置。
	 *
	 * <p>⚠️ 单独列一个常量、不用 {@code BUTTON_Y - 10} ——
	 * 那样会和上面的饱食度数字(y = 82)撞在一起(2026-10-15 截图里实测叠字)。
	 */
	private static final int AUTO_COLLECT_LABEL_Y = 96;

	/** 开关按钮的两种底色 —— 手绘,不再换成原版按钮精灵(见文件末尾的回退说明)。 */
	private static final int BUTTON_ON_BG = 0xFF7BA05B;
	private static final int BUTTON_OFF_BG = 0xFF8B8B8B;

	/** 鼠标是不是在这个按钮上。 */
	private boolean autoCollectHit(final int mouseX, final int mouseY) {
		final int bx = this.leftPos + INFO_X + 2;
		final int by = this.topPos + AUTO_COLLECT_BUTTON_Y;

		return mouseX >= bx && mouseX < bx + AUTO_COLLECT_BUTTON_WIDTH
			&& mouseY >= by && mouseY < by + AUTO_COLLECT_BUTTON_HEIGHT;
	}

	// ------------------------------------------------------------ 虚空 F17

	/**
	 * 虚空的开关按钮(在虚空弹窗的底部)。
	 *
	 * <p>⚠️ 它<b>和自动收纳按钮不是一回事</b>:
	 * 自动收纳在面板右侧(常驻可见),虚空开关在弹窗里(只有虚空签开着才看得到)——
	 * 因为"虚空"是个要配置的东西,把它的开关<h3>放在它的配置旁边</h3>才符合直觉。
	 */
	private void extractVoidButton(
		final GuiGraphicsExtractor graphics,
		final int x, final int y,
		final int mouseX, final int mouseY
	) {
		if (this.menu.openTab() != BackpackMenu.TAB_VOID) {
			return;
		}

		// 🆕 一级都没解锁(0 格)⇒ 弹窗里只有"去成长页解锁它"那行字,
		// **不该画开关按钮** —— 一个没有格子的过滤功能,开关没有意义,
		// 而且会压在提示文字下面(2026-10-15 接线之后才可能出现这个状态)。
		if (!this.menu.voidUnlocked()) {
			return;
		}

		final boolean on = this.menu.voidEnabled();
		final boolean hovered = this.voidButtonHit(mouseX, mouseY);
		final int by = BackpackMenu.voidButtonY(this.menu.voidRows());

		// ⚠️ 手绘(回退后)—— 同「自动收纳」按钮。
		bevel(graphics, x + BackpackMenu.voidButtonX(), y + by,
			BackpackMenu.VOID_BUTTON_WIDTH, BackpackMenu.VOID_BUTTON_HEIGHT,
			on ? BUTTON_ON_BG : BUTTON_OFF_BG,
			PANEL_HIGHLIGHT, PANEL_SHADOW);

		if (hovered) {
			graphics.outline(x + BackpackMenu.voidButtonX() - 1,
				y + by - 1,
				BackpackMenu.VOID_BUTTON_WIDTH + 2, BackpackMenu.VOID_BUTTON_HEIGHT + 2,
				0xFFFFFFFF);
		}
	}

	/** 鼠标是不是在虚空开关上。 */
	private boolean voidButtonHit(final int mouseX, final int mouseY) {
		if (this.menu.openTab() != BackpackMenu.TAB_VOID) {
			return false;
		}

		final int bx = this.leftPos + BackpackMenu.voidButtonX();
		final int by = this.topPos + BackpackMenu.voidButtonY(this.menu.voidRows());

		return mouseX >= bx && mouseX < bx + BackpackMenu.VOID_BUTTON_WIDTH
			&& mouseY >= by && mouseY < by + BackpackMenu.VOID_BUTTON_HEIGHT;
	}

	/** 左侧弹窗 —— 在面板<b>外面</b>,所以 x 是负的。 */
	private void extractPopup(
		final GuiGraphicsExtractor graphics,
		final int x, final int y,
		final int mouseX, final int mouseY
	) {
		if (this.menu.openTab() == BackpackMenu.TAB_NONE) {
			return;
		}

		// ⚠️ 几何一律走 BackpackMenu.popupRect() —— 绘制与命中判定【唯一来源】。
		// 以前这里是 x - POPUP_WIDTH / y + POPUP_TOP 手抄一遍,
		// hasClickedOutside 里再抄一遍,三处不同步就是"东西被丢地上"那个 bug(坑 25)。
		final BackpackMenu.PopupRect popup = BackpackMenu.popupRect(
			this.menu.openTab(), this.menu.voidRows());

		// ⚠️ 手绘(回退后)—— 不再用原版 `popup/background` 精灵。
		bevel(graphics, x + popup.x(), y + popup.y(), popup.width(), popup.height(),
			POPUP_BG, PANEL_HIGHLIGHT, PANEL_SHADOW);
	}

	/**
	 * 侧签条 —— 固定在面板内左边缘(照精妙背包:一列小按钮 + 悬停提示)。
	 *
	 * <h2>🆕 三态(2026-10-15 改,原来"打开"和"悬停"共用一个颜色)</h2>
	 * <pre>
	 *   打开   → 【凸起】+ 最亮 + 底部一条白线   "它是弹出来的"
	 *   悬停   → 亮底 + 1px 白描边              "鼠标在上面"
	 *   未打开 → 【凹陷】(highlight/shadow 对调) "它是按进去的"
	 * </pre>
	 * 参考两家成熟 mod 的共同做法(精妙换底图 / 原版 {@code WidgetSprites} 三张图 /
	 * 旅行者"底图标 + 悬停叠边框")—— 它们都把**三态做成视觉可辨**。
	 *
	 * <p>我们零 PNG,所以用"倒角方向 + 描边"来表达同样的语义:
	 * <b>凹陷 = 没选中,凸起 = 选中</b>是原版按钮的通用语言。
	 */
	private void extractSideTabs(
		final GuiGraphicsExtractor graphics,
		final int x, final int y,
		final int mouseX, final int mouseY
	) {
		final int stripX = x + BackpackMenu.TAB_STRIP_X;

		for (int i = 0; i < BackpackMenu.TAB_COUNT; i++) {
			final boolean active = this.menu.openTab() == i;
			final boolean hovered = i == this.tabAt(mouseX, mouseY);
			final int tabY = y + BackpackMenu.TAB_TOP + i * BackpackMenu.TAB_HEIGHT;

			if (active) {
				// 打开:凸起 + 最亮。向左/上各溢 1px,让它"顶出来"
				bevel(graphics, stripX - 1, tabY - 1,
					BackpackMenu.TAB_STRIP_WIDTH + 1, BackpackMenu.TAB_BUTTON_HEIGHT + 2,
					TAB_ACTIVE_BG, PANEL_HIGHLIGHT, PANEL_SHADOW);

				// 底部一条亮线(原版页签表述"当前项"的手法)
				graphics.fill(stripX + 2, tabY + BackpackMenu.TAB_BUTTON_HEIGHT - 2,
					stripX + BackpackMenu.TAB_STRIP_WIDTH - 1,
					tabY + BackpackMenu.TAB_BUTTON_HEIGHT - 1, 0xFFFFFFFF);
			} else if (hovered) {
				// 悬停:亮底 + 1px 白描边
				bevel(graphics, stripX, tabY,
					BackpackMenu.TAB_STRIP_WIDTH, BackpackMenu.TAB_BUTTON_HEIGHT,
					0xFFD8D8D8, PANEL_HIGHLIGHT, PANEL_SHADOW);
				graphics.outline(stripX - 1, tabY - 1,
					BackpackMenu.TAB_STRIP_WIDTH + 2, BackpackMenu.TAB_BUTTON_HEIGHT + 2,
					0xFFFFFFFF);
			} else {
				// 未打开:凹陷(高光和阴影对调 = 按压态)
				bevel(graphics, stripX, tabY,
					BackpackMenu.TAB_STRIP_WIDTH, BackpackMenu.TAB_BUTTON_HEIGHT,
					TAB_BG, PANEL_SHADOW, PANEL_HIGHLIGHT);
			}
		}
	}

	/** 饱食度条 —— 放在面板右侧那条信息区。 */
	private void extractSatietyBar(final GuiGraphicsExtractor graphics, final int x, final int y) {
		final int barX = x + INFO_X + 4;
		final int barY = y + 18;
		final int barWidth = 32;
		final int barHeight = 60;

		bevel(graphics, barX - 1, barY - 1, barWidth + 2, barHeight + 2,
			BAR_FRAME, PANEL_HIGHLIGHT, PANEL_SHADOW);
		graphics.fill(barX, barY, barX + barWidth, barY + barHeight, BAR_BG);

		// 🆕 刻度:每 25% 一条 1px 暗线 —— 让玩家"读出"数值,而不是只看下面的数字。
		//
		// ⚠️ 刻度必须画在【填充之前】,否则会被填充盖掉(填充是从下往上长的)。
		for (int i = 1; i < 4; i++) {
			final int gy = barY + barHeight - barHeight * i / 4;
			graphics.fill(barX, gy, barX + barWidth, gy + 1, 0x60000000);
		}

		// 竖向填充:从下往上。
		//
		// 🆕 改成渐变(上亮下暗) —— 一块死红看着像"色块",渐变才有"液面"的感觉。
		// ⚠️ fillGradient 不归一化坐标,y0 必须 < y1。
		final int satiety = this.menu.satiety();
		final int filled = (int) Math.round((double) barHeight * satiety / BackpackGrowth.MAX_SATIETY);
		if (filled > 0) {
			graphics.fillGradient(barX, barY + barHeight - filled, barX + barWidth, barY + barHeight,
				0xFFE06060, 0xFF7A1010);
		}
	}

	/**
	 * 🆕 在物品之上给虚空样本格叠一层<b>半透明白</b> —— 「幽灵」观感。
	 *
	 * <h2>⚠️ 为什么必须做这一步</h2>
	 * 用户 2026-10-15 反馈:"**你现在会有复制物品的bug**"。
	 *
	 * <p>查下来<b>不是逻辑 bug</b> —— 样本登记本来就不消耗鼠标上那份(那是用户要的)。
	 * 真正的问题在<b>画法</b>:样本格里的东西画得和普通物品<b>一模一样</b>,
	 * 于是"鼠标上还有 64 个 + 样本格里出现 1 个"看起来就是<b>凭空复制</b>。
	 *
	 * <p>⇒ 用原版的视觉语言表达"这不是实体":
	 * <b>半透明 = 幽灵物品 = 只是一条记录</b>(旅行者背包的记忆格子也是这么画的)。
	 *
	 * <h2>⚠️ 为什么重写 {@code extractSlots} 而不是 {@code extractLabels}</h2>
	 * 调用链是 {@code ... → extractLabels → extractSlots},<b>槽位物品最后画</b> ——
	 * 想盖在物品<b>上面</b>,就必须在 {@code super.extractSlots(...)} 之后动笔。
	 */
	@Override
	protected void extractSlots(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY) {
		super.extractSlots(graphics, mouseX, mouseY);

		if (this.menu.openTab() != BackpackMenu.TAB_VOID) {
			return;
		}

		// 🆕 虚空的样本格 —— 物品也要自己画(它们不在 menu.slots 里,原版画不到)。
		//
		// ⚠️ 提取槽是【已平移】的坐标系(见类注释)—— 只能写相对面板坐标。
		//
		// ⚠️ 叠一层半透明白 = 「幽灵物品」,表达"这是一条记录,不是真实物品"。
		// 不加这一层的话,"光标上还有 64 个 + 样本格里出现 1 个"看起来就像凭空复制。
		for (final VoidSlot slot : this.menu.voidSlots) {
			if (slot.x == BackpackMenu.HIDDEN_SLOT_X || !slot.hasItem()) {
				continue;
			}

			graphics.item(slot.getItem(), slot.x, slot.y);
			graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x80FFFFFF);
		}
	}

	/** ★相对坐标系 —— 不要再加 leftPos / topPos。 */
	@Override
	protected void extractLabels(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY) {
		// 🆕 成长页:这里画的全是背包页的东西(侧签标签 / 弹窗文字 / 饱食度数字 / 物品栏标签),
		// 成长页一样都不需要 —— 直接早退。
		//
		// ⚠️ 不早退的症状(2026-10-15 用户截图实测):成长页的的技能树底下
		// 还浮着「20/20」「关」「物品栏」这些背包页的字,和节点叠在一起。
		if (this.menu.openPage() != BackpackMenu.PAGE_BACKPACK) {
			return;
		}

		// ⚠️ 标题那行(「怪物背包 · 已长开 84/84 格」)【已删除】(2026-10-15 用户要求):
		// 顶部那条是页签的地方,再居中画一行字就会和页签叠在一起(用户截图里实测到了)。
		// 格数信息玩家在界面上直接数得出来,不需要常驻一行文字。
		// 如果以后觉得缺,正确的位置是【物品栏标签那行】或者 tooltip,不是标题条。
		//
		// ⚠️ 顺带:原来那两行 `container` / `unlocked` 也跟着删了 ——
		// 它们唯一的用途就是拼那行标题,留着是死变量。

		// 侧签上的短标签(按钮上只放得下两个字)
		for (int i = 0; i < BackpackMenu.TAB_COUNT; i++) {
			final boolean active = this.menu.openTab() == i;
			final Component label = Component.translatable(BackpackMenu.TAB_SHORT_KEYS[i]);
			final int tabY = BackpackMenu.TAB_TOP + i * BackpackMenu.TAB_HEIGHT;

			graphics.text(this.font, label,
				BackpackMenu.TAB_STRIP_X + 4, tabY + 4,
				active ? TAB_TEXT : TAB_TEXT_DIM, false);
		}

		// 悬停时浮出说明(照精妙背包的提示,不占常驻空间)。
		//
		// ⚠️ extractLabels 收到的 mouseX/mouseY 【本来就是屏幕绝对】——
		// 这里再加 leftPos/topPos 就永远命中不了(踩过:表现为"悬停没提示")。
		//
		// 🆕 改用原版 tooltip,不再手画黑底 ——
		// 自动拿到九宫格底 + 紫边 + 【屏幕边缘避让】(手画的话贴到屏幕边就看不见了)。
		final int hoveredTab = this.tabAt(mouseX, mouseY);
		if (hoveredTab != NO_TAB_HIT) {
			final Component full = Component.translatable(BackpackMenu.TAB_HINT_KEYS[hoveredTab]);
			graphics.setTooltipForNextFrame(this.font, java.util.List.of(full),
				java.util.Optional.empty(), mouseX, mouseY);
		}

		// 弹窗内容(在面板外,用负坐标)
		if (this.menu.openTab() == BackpackMenu.TAB_FEED) {
			graphics.text(this.font, Component.translatable("gui.ergu_monster_backpack.tab.feed.hint"),
				-BackpackMenu.POPUP_WIDTH + 6, 24, TEXT_DIM, false);
		} else if (this.menu.openTab() == BackpackMenu.TAB_STOMACH) {
			graphics.text(this.font, Component.translatable("gui.ergu_monster_backpack.tab.gizzard.hint"),
				-BackpackMenu.POPUP_WIDTH + 6, 24, TEXT_DIM, false);

			// 在消化 → 显示还要几秒(给个"它真的在动"的反馈)
			final int remaining = this.menu.stomachRemainingTicks();
			if (remaining > 0) {
				graphics.text(this.font,
					Component.translatable("gui.ergu_monster_backpack.tab.gizzard.progress",
						(remaining + 19) / 20),
					-BackpackMenu.POPUP_WIDTH + 6, 92, TEXT_DIM, false);
			}
		} else if (this.menu.openTab() == BackpackMenu.TAB_VOID) {
			// 🆕 虚空:标题 + 一句玩法说明 + 底部开关上的字。
			//
			// ⚠️ 标题用 tab.void.title,【不要】用 tab.void.hint ——
			// 后者是"鼠标悬停在侧签上浮出来的那行"(「→ 过滤掉杂物」),
			// 两者语气不同(一个是提示、一个是标题),共用一个键会两边都不对。
			graphics.text(this.font, Component.translatable("gui.ergu_monster_backpack.tab.void.title"),
				-BackpackMenu.POPUP_WIDTH + 6, 22, TEXT_NORMAL, false);

			// 🆕 一级都没解锁 ⇒ 弹窗里【没有格子】,所以要告诉玩家"去哪解锁"。
			//
			// ⚠️ 2026-10-15 接线之后才可能出现这个状态:
			// 以前 `voidUnlockedSlots()` 是 `stage >= 3 ? 25 : 9` —— **默认就白送 9 格**。
			// 现在必须真的解锁 `voiding_1`(阶段 2 + 材料),所以"点开是空的"是新情况,
			// 不提示的话玩家会以为功能坏了。
			if (!this.menu.voidUnlocked()) {
				graphics.text(this.font,
					Component.translatable("gui.ergu_monster_backpack.tab.void.locked"),
					-BackpackMenu.POPUP_WIDTH + 6, 44, TEXT_DIM, false);
			} else {
				// ⚠️ 这行说明【必须换行】—— 弹窗只有 104px 宽,而一个汉字约 9px,
				// 一整句("放一样东西进去,以后这种东西全销毁" = 18 字 ≈ 162px)会【溢出弹窗】
				// 画到面板上,和「物品栏」标签叠在一起(2026-10-15 用户截图里实测到了)。
				graphics.text(this.font, Component.translatable("gui.ergu_monster_backpack.tab.void.tip1"),
					-BackpackMenu.POPUP_WIDTH + 6,
					BackpackMenu.voidTipLine1Y(this.menu.voidRows()), TEXT_DIM, false);
				graphics.text(this.font, Component.translatable("gui.ergu_monster_backpack.tab.void.tip2"),
					-BackpackMenu.POPUP_WIDTH + 6,
					BackpackMenu.voidTipLine2Y(this.menu.voidRows()), TEXT_DIM, false);

				final Component voidText = Component.translatable(
					this.menu.voidEnabled()
						? "gui.ergu_monster_backpack.void.on"
						: "gui.ergu_monster_backpack.void.off");
				graphics.text(this.font, voidText,
					BackpackMenu.voidButtonX()
						+ (BackpackMenu.VOID_BUTTON_WIDTH - this.font.width(voidText)) / 2,
					BackpackMenu.voidButtonY(this.menu.voidRows()) + 3,
					TEXT_NORMAL, false);
			}
		}

		// 饱食度数字(画在条下面)
		final Component satietyText = Component.literal(
			this.menu.satiety() + "/" + BackpackGrowth.MAX_SATIETY);
		graphics.text(this.font, satietyText, INFO_X + 2, 82, TEXT_DIM, false);

		// 自动收纳按钮上的字:「自动收纳」+ 开/关
		final Component autoText = Component.translatable(
			this.menu.autoCollect()
				? "gui.ergu_monster_backpack.auto_collect.on"
				: "gui.ergu_monster_backpack.auto_collect.off");
		graphics.text(this.font, autoText,
			INFO_X + 2 + (AUTO_COLLECT_BUTTON_WIDTH - this.font.width(autoText)) / 2,
			AUTO_COLLECT_BUTTON_Y + 3,
			TEXT_NORMAL, false);

		// 按钮上方的说明字
		graphics.text(this.font,
			Component.translatable("gui.ergu_monster_backpack.auto_collect"),
			INFO_X + 2, AUTO_COLLECT_LABEL_Y, TEXT_DIM, false);

		// 🆕 「整理」按钮 —— 标签 + 按钮上的字。
		//
		// ⚠️ 未解锁时**两行都画成暗色** —— 这是"这里有个功能但还不能用"的唯一信号。
		// 不隐藏的理由:玩家要能看见"以后有这个",而不是"这功能不存在"。
		final boolean sortUnlocked = this.menu.sortUnlocked();
		final int sortLabelColor = sortUnlocked ? TEXT_DIM : 0xFFA8A8A8;

		graphics.text(this.font,
			Component.translatable("gui.ergu_monster_backpack.sort"),
			INFO_X + 2, SORT_LABEL_Y, sortLabelColor, false);

		final Component sortText = Component.translatable("gui.ergu_monster_backpack.sort.button");
		graphics.text(this.font, sortText,
			INFO_X + 2 + (SORT_BUTTON_WIDTH - this.font.width(sortText)) / 2,
			SORT_BUTTON_Y + 3,
			sortUnlocked ? TEXT_NORMAL : 0xFF707070, false);

		// 玩家物品栏标签(照原版的坐标)
		graphics.text(this.font, this.playerInventoryTitle,
			this.inventoryLabelX, this.inventoryLabelY, TEXT_NORMAL, false);
	}

	/**
	 * ⚠️⚠️ <b>必须重写:弹窗在面板【左边】,原版会把它当成"面板外"。</b>
	 *
	 * <h2>不重写的后果</h2>
	 * 原版 {@code AbstractContainerScreen.mouseClicked} 里:
	 * <pre>
	 *   boolean clickedOutside = this.hasClickedOutside(mouseX, mouseY, leftPos, topPos);
	 *   int slotId = slot.index;
	 *   if (clickedOutside) {
	 *       slotId = -999;      // ← 就算鼠标正点在槽上,也强制改成 -999
	 *   }
	 * </pre>
	 * 而默认的 {@code hasClickedOutside} 只要 {@code mouseX < leftPos} 就算"在外面" ——
	 * 我们的弹窗<b>整个在 leftPos 左边</b>(坐标是负的),于是
	 * <b>点弹窗里的任何槽都会被当成"点空处"</b> ⇒ {@code ContainerInput.THROW}
	 * ⇒ <b>手里的东西被丢到地上</b>。
	 *
	 * <p>实测症状(2026-10-15):"往胃里放粗铁,东西直接掉地上了" ——
	 * 而且左右键表现相反(THROW 分支里左键丢整组、右键丢一个),很好认。
	 *
	 * <p>修法:把<b>弹窗那块矩形也算作"面板内"</b>。
	 */
	@Override
	protected boolean hasClickedOutside(
		final double mouseX, final double mouseY,
		final int leftPos, final int topPos
	) {
		// ⚠️ 几何走 BackpackMenu.popupRect() —— 和 extractPopup 的绘制【同一个来源】。
		if (this.menu.openTab() != BackpackMenu.TAB_NONE
			&& BackpackMenu.popupRect(this.menu.openTab(), this.menu.voidRows()).contains(
				(int) mouseX - leftPos, (int) mouseY - topPos)) {
			return false;      // 点在弹窗里 → 不算"面板外"
		}

		// 🆕 顶部页签【不需要】在这里再加几何判断 —— 它画在面板内的标题条里(y+1..y+16),
		// 原版几何本来就认为它"在面板里"。
		//
		// ⚠️ 但 mouseClicked 里【仍然要最先拦它】:不拦的话会落到下面的原版逻辑,
		// 而那片区域底下没有槽,原版会当成"点空处"⇒ 手里的东西被丢到地上。
		return super.hasClickedOutside(mouseX, mouseY, leftPos, topPos);
	}

	// ------------------------------------------------------------ 侧签的命中判定

	// ------------------------------------------------------------ 交互

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		// ---- ⓪ 🆕 顶部主页签(背包 / 成长)----
		//
		// ⚠️ 必须在最前面 —— 它在面板【上方外面】,而下面的判定都是"面板内"的。
		final int pageHit = this.pageAt((int) event.x(), (int) event.y());
		if (pageHit != -1) {
			if (pageHit != this.menu.openPage()) {
				ClientPlayNetworking.send(new SelectPagePayload(pageHit));
			}
			return true;
		}

		// ---- ⓪b 🆕 成长页:拖动画布 + 点技能节点,别的一律不处理 ----
		//
		// ⚠️ 成长页里 84 个背包格 + 物品栏格都被移到了 HIDDEN_SLOT_X(-2000),
		// 所以理论上 slotAt 找不到它们;但 super.mouseClicked 有一堆
		// "点空处就丢东西"的路径(§七 关于 THROW 的讨论)。直接在这里收口最省事。
		if (this.menu.openPage() == BackpackMenu.PAGE_GROWTH) {
			if (event.buttonInfo().button() == LEFT_BUTTON) {
				// 记下按下位置 —— mouseDragged 用它算位移,判断"这是拖还是点"
				this.skillDragAnchorX = event.x();
				this.skillDragAnchorY = event.y();
				this.skillDragging = false;

				final SkillNodes.Node node = this.skillNodeAt((int) event.x(), (int) event.y());
				// ⚠️ 只在"现在就能解锁"时才发包 —— 但【这只是省一次无用往返】,
				// 真正的判定全在服务端(SkillUnlock.unlock 会自己重算一遍)。
				if (node != null && this.menu.skillStatus(node) == SkillUnlock.Status.READY) {
					ClientPlayNetworking.send(new UnlockSkillPayload(node.id()));
				}
			}
			return true;
		}

		// ---- ① 侧签 ----
		final int hit = this.tabAt((int) event.x(), (int) event.y());

		if (hit != NO_TAB_HIT) {
			// 点已经打开的那个 → 关掉它
			final int next = hit == this.menu.openTab() ? BackpackMenu.TAB_NONE : hit;
			ClientPlayNetworking.send(new SelectTabPayload(next));
			return true;
		}

		// ---- ② 自动收纳按钮 ----
		if (this.autoCollectHit((int) event.x(), (int) event.y())) {
			ClientPlayNetworking.send(new ToggleAutoCollectPayload());
			return true;
		}

		// ---- ②b 🆕 虚空开关 ----
		if (this.voidButtonHit((int) event.x(), (int) event.y())) {
			ClientPlayNetworking.send(new ToggleVoidPayload());
			return true;
		}

		// ---- ②c 🆕 「整理」按钮 ----
		//
		// ⚠️ 未解锁时【连包都不发】—— 服务端也会拦,但那样会白跑一趟网络,
		// 而且服务端拦掉之后客户端没有任何反馈(点了像没反应)。
		// 客户端这边本来就知道解锁没解锁,直接不动最干净。
		if (this.sortButtonHit((int) event.x(), (int) event.y())) {
			if (this.menu.sortUnlocked()) {
				ClientPlayNetworking.send(new SortingPayload());
			}
			return true;
		}

		// ---- ②c 🆕 点一下虚空样本格 = 登记主手物品(或空手清除) ----
		//
		// ⚠️ 必须在 super.mouseClicked 之前拦 —— 那条路会去问 mayPlace,
		// 而 VoidSlot.mayPlace 恒为 false(样本槽是只读展示位,不收拖放)。
		//
		// ⚠️ 只拦左键:右键留给原版(和别处一致)。
		if (event.buttonInfo().button() == LEFT_BUTTON) {
			final Slot sample = this.slotAt(event.x(), event.y());

			if (sample instanceof VoidSlot voidSlot && voidSlot.isUnlocked()) {
				ClientPlayNetworking.send(new SetVoidSamplePayload(voidSlot.getContainerSlot()));
				return true;
			}
		}

		// ---- ③ Shift + 左键 → 开始"划动存取" ----
		// 不区分是背包格还是物品栏格:原版的 QUICK_MOVE 自己会按槽位决定方向
		// (背包格 → 取出;物品栏格 → 存入)。所以取和存是同一套代码。
		if (event.buttonInfo().button() == LEFT_BUTTON && event.hasShiftDown()) {
			final Slot slot = this.slotAt(event.x(), event.y());

			if (slot != null && slot.hasItem() && slot.isActive()) {
				this.shiftDragging = true;
				this.dragPicked.clear();
				this.pickOut(slot);
				return true;
			}
		}

		return super.mouseClicked(event, doubleClick);
	}

	/**
	 * 划动 —— 鼠标划过的每一格都被转移出去。
	 *
	 * <p><b>取和存是同一段代码</b>:发的是原版的 {@code QUICK_MOVE},
	 * 它自己会按槽位决定方向 —— 背包格取出、物品栏格存入。
	 *
	 * <p>"物品栏满了就不取 / 背包满了就不存"是<b>自动满足</b>的:
	 * {@code quickMoveStack} 放不下时本来就什么都不做。
	 */
	@Override
	public boolean mouseDragged(final MouseButtonEvent event, final double dragX, final double dragY) {
		// 🆕 成长页:按住左键拖技能树画布(照成就界面的手感)。
		//
		// ⚠️ 这里用的是【位移 delta】而不是"鼠标绝对位置 - 按下时的位置":
		// 后者在拖动过程中一旦被 clamp 夹住,再往回拖会出现"黏住"的感觉 ——
		// 因为锚点没跟着更新。用 delta 就没有这个问题。
		if (this.menu.openPage() == BackpackMenu.PAGE_GROWTH) {
			if (event.buttonInfo().button() != LEFT_BUTTON) {
				return true;      // 成长页不处理别的键
			}

			final double moved = Math.abs(event.x() - this.skillDragAnchorX)
				+ Math.abs(event.y() - this.skillDragAnchorY);
			if (!this.skillDragging && moved >= SKILL_DRAG_THRESHOLD) {
				this.skillDragging = true;
			}
			if (this.skillDragging) {
				this.skillOffsetX += (int) Math.round(dragX);
				this.skillOffsetY += (int) Math.round(dragY);
				this.clampSkillOffset();
			}
			return true;
		}

		if (this.shiftDragging) {
			if (event.buttonInfo().button() != LEFT_BUTTON) {
				this.shiftDragging = false;
				return super.mouseDragged(event, dragX, dragY);
			}

			final Slot slot = this.slotAt(event.x(), event.y());

			// 同一个格子只处理一次(来回蹭不该反复触发)
			if (slot != null && slot.hasItem() && slot.isActive()
				&& this.dragPicked.add(slot.index)) {
				this.pickOut(slot);
			}
			return true;
		}

		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(final MouseButtonEvent event) {
		this.shiftDragging = false;
		this.dragPicked.clear();
		// 🆕 松手就结束技能树拖动 —— 下次按下重新判"是拖还是点"
		this.skillDragging = false;
		return super.mouseReleased(event);
	}

	/**
	 * 🆕 成长页:滚轮推技能树画布(上下都行,左右用 shift + 滚轮)。
	 *
	 * <p>⚠️ 原版 {@code AbstractContainerScreen} 的滚轮默认是"快捷栏换位",
	 * 所以成长页必须<b>先拦掉</b> —— 否则玩家滚一下就把快捷栏的东西换了。
	 */
	@Override
	public boolean mouseScrolled(final double mouseX, final double mouseY,
		final double scrollX, final double scrollY) {
		if (this.menu.openPage() == BackpackMenu.PAGE_GROWTH) {
			// 16px/格 是原版成就界面滚动用的一档,手感比较接近
			final int step = (int) Math.round(scrollY * 16.0);
			if (step == 0) {
				return true;
			}
			this.skillOffsetY += step;
			this.clampSkillOffset();
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	/** 把一个背包格里的东西一键转移出去(交给原版的 QUICK_MOVE)。 */
	private void pickOut(final Slot slot) {
		if (this.minecraft == null || this.minecraft.gameMode == null) {
			return;
		}
		// ⚠️ 26.2 这个方法叫 handleContainerInput(旧名 handleInventoryMouseClick)
		this.minecraft.gameMode.handleContainerInput(
			this.menu.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, this.minecraft.player);
	}

	/** 没点中任何侧签。 */
	private static final int NO_TAB_HIT = Integer.MIN_VALUE;
	private static final int LEFT_BUTTON = 0;

	/** 点在哪个侧签上(屏幕坐标);没点中返回 {@link #NO_TAB_HIT}。 */
	private int tabAt(final int mouseX, final int mouseY) {
		final int stripX = this.leftPos + BackpackMenu.TAB_STRIP_X;
		final int relY = mouseY - this.topPos;

		if (mouseX < stripX || mouseX >= stripX + BackpackMenu.TAB_STRIP_WIDTH) {
			return NO_TAB_HIT;
		}

		for (int i = 0; i < BackpackMenu.TAB_COUNT; i++) {
			final int tabY = BackpackMenu.TAB_TOP + i * BackpackMenu.TAB_HEIGHT;

			if (relY >= tabY && relY < tabY + BackpackMenu.TAB_BUTTON_HEIGHT) {
				return i;
			}
		}

		return NO_TAB_HIT;
	}

	// ------------------------------------------------------------ 小工具

	/** 鼠标下的槽(没有就返回 null)。 */
	private Slot slotAt(final double mouseX, final double mouseY) {
		final int relX = (int) mouseX - this.leftPos;
		final int relY = (int) mouseY - this.topPos;

		// ⚠️ 虚空样本格【不在 menu.slots 里】,得单独找一遍 ——
		// 否则点击命中不到它们(表现为"点了没反应")。
		if (this.menu.openTab() == BackpackMenu.TAB_VOID) {
			for (final VoidSlot slot : this.menu.voidSlots) {
				if (slot.x != BackpackMenu.HIDDEN_SLOT_X
					&& relX >= slot.x && relX < slot.x + 16
					&& relY >= slot.y && relY < slot.y + 16) {
					return slot;
				}
			}
		}

		for (Slot slot : this.menu.slots) {
			if (!slot.isActive()) {
				continue;
			}
			if (relX >= slot.x && relX < slot.x + 16 && relY >= slot.y && relY < slot.y + 16) {
				return slot;
			}
		}
		return null;
	}

	private static boolean isShiftDown() {
		return false;
	}

	// ────────────────────────────────────────────────────────────────
	// ⚠️ 已回退:原版 GUI 精灵图(界面美化第二批,2026-10-15 当天回退)
	// ────────────────────────────────────────────────────────────────
	//
	// 试过把槽位框 / 按钮 / 弹窗底换成原版精灵图(`container/slot` / `widget/button` /
	// `popup/background`),**用户看截图后判定比原来更糟**,当天回退。
	//
	// ⚠️⚠️ 失败的原因(记下来,别再走一遍):
	//
	//   1. **槽位是一格一格贴的** ⇒ 格子之间有缝、边框线条重复 ⇒
	//      **拼不出"一整块网格"的观感**。
	//      两个参考 mod 都是【贴一整块网格贴图】(旅行者的 `slots.png`),
	//      超过 9 行就【重复贴中间那段】—— 见 D:\MC\refs\travelers-backpack\
	//      `AbstractBackpackScreen.renderSlots`。
	//   2. **原版 `container/crafter/disabled_slot` 比我们调的暗色【浅】** ⇒
	//      "未解锁"的观感被削弱(用户截图里格子明显变浅)。
	//   3. **风格的统一性** —— 三处换精灵、其余仍是手绘 ⇒ **两种风格打架**,
	//      比"全部手绘但不精致"更难看。
	//
	// 📌 **结论:界面美观靠【画好的贴图】,不靠拼接原版小精灵。**
	// 我们没有自己的 GUI 贴图时,**手绘一套统一风格的 `fill` 反而是更稳的选择** ——
	// 朴素但一致,不会像现在这样"东一块西一块"。
	//
	// 📌 精妙的做法(供以后真要画贴图时参考):
	//   `StorageGuiHelper.renderStorageBackground` —— 背景分**三段**取,
	//   中段从 `v=17` 起**可重复**,所以槽区行数任意都不变形。

	/**
	 * 画一个槽位框(手绘,原版容器那种凹陷感)。
	 *
	 * <p>⚠️ 参数保留 {@code locked} 而不是颜色 —— 调用点已经写好了,换成颜色要改 6 处。
	 */
	private static void slotFrame(
		final GuiGraphicsExtractor graphics,
		final int x, final int y, final boolean locked
	) {
		bevel(graphics, x, y, 18, 18,
			locked ? SLOT_LOCKED : SLOT_BG, SLOT_SHADOW, SLOT_HIGHLIGHT);
	}

	/** 原版风格的立体方框:凸起 = 白高光 + 深阴影;凹陷就把两者对调。 */
	private static void bevel(
		final GuiGraphicsExtractor graphics,
		final int x, final int y, final int width, final int height,
		final int fill, final int highlight, final int shadow
	) {
		final int right = x + width;
		final int bottom = y + height;

		graphics.fill(x, y, right, bottom, fill);
		graphics.fill(x, y, right - 1, y + 1, highlight);
		graphics.fill(x, y, x + 1, bottom - 1, highlight);
		graphics.fill(x + 1, bottom - 1, right, bottom, shadow);
		graphics.fill(right - 1, y + 1, right, bottom, shadow);
	}
}

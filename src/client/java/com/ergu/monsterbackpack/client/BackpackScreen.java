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
import com.ergu.monsterbackpack.net.SelectTabPayload;

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

		// ★屏幕绝对坐标
		final int x = this.leftPos;
		final int y = this.topPos;

		extractPopup(graphics, x, y, mouseX, mouseY);

		// 面板底
		bevel(graphics, x, y, this.imageWidth, this.imageHeight,
			PANEL_BG, PANEL_HIGHLIGHT, PANEL_SHADOW);

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
				bevel(graphics, x + slot.x - 1, y + slot.y - 1, 18, 18,
					backpackSlot.isUnlocked() ? SLOT_BG : SLOT_LOCKED,
					SLOT_SHADOW, SLOT_HIGHLIGHT);
				continue;
			}

			if (slot instanceof FeedSlot) {
				if (this.menu.openTab() != BackpackMenu.TAB_FEED) {
					continue;
				}
				bevel(graphics, x + slot.x - 1, y + slot.y - 1, 18, 18,
					SLOT_BG, SLOT_SHADOW, SLOT_HIGHLIGHT);
				continue;
			}

			if (slot instanceof CharmSlot charmSlot) {
				if (this.menu.openTab() != BackpackMenu.TAB_CHARM) {
					continue;
				}
				bevel(graphics, x + slot.x - 1, y + slot.y - 1, 18, 18,
					charmSlot.isUnlocked() ? SLOT_BG : SLOT_LOCKED,
					SLOT_SHADOW, SLOT_HIGHLIGHT);
				continue;
			}

			// 玩家物品栏(普通 Slot)
			bevel(graphics, x + slot.x - 1, y + slot.y - 1, 18, 18,
				SLOT_BG, SLOT_SHADOW, SLOT_HIGHLIGHT);
		}

		extractSideTabs(graphics, x, y, mouseX, mouseY);
		extractSatietyBar(graphics, x, y);
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

		bevel(graphics, x - BackpackMenu.POPUP_WIDTH, y + 18,
			BackpackMenu.POPUP_WIDTH, 110,
			POPUP_BG, PANEL_HIGHLIGHT, PANEL_SHADOW);
	}

	/** 侧签条 —— 固定在面板内左边缘(照精妙背包:一列小按钮 + 悬停提示)。 */
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

			bevel(graphics, stripX, tabY, BackpackMenu.TAB_STRIP_WIDTH,
				BackpackMenu.TAB_BUTTON_HEIGHT,
				active || hovered ? TAB_ACTIVE_BG : TAB_BG, PANEL_HIGHLIGHT, PANEL_SHADOW);
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

		// 竖向填充:从下往上
		final int satiety = this.menu.satiety();
		final int filled = (int) Math.round((double) barHeight * satiety / BackpackGrowth.MAX_SATIETY);
		if (filled > 0) {
			graphics.fill(barX, barY + barHeight - filled, barX + barWidth, barY + barHeight, BAR_FILL);
		}
	}

	/** ★相对坐标系 —— 不要再加 leftPos / topPos。 */
	@Override
	protected void extractLabels(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY) {
		final BackpackContainer container = this.menu.backpackContainer();
		final int unlocked = container.unlockedSlots();

		// 标题 + 格数进度合成一行,居中画在面板内顶部
		final Component line = Component.translatable(
			"gui.ergu_monster_backpack.progress", unlocked, BackpackContainer.SIZE);
		graphics.text(this.font, line,
			(this.imageWidth - this.font.width(line)) / 2, this.titleLabelY,
			TEXT_NORMAL, false);

		// 侧签上的短标签(按钮上只放得下两个字)
		for (int i = 0; i < BackpackMenu.TAB_COUNT; i++) {
			final boolean active = this.menu.openTab() == i;
			final Component label = Component.translatable(BackpackMenu.TAB_SHORT_KEYS[i]);
			final int tabY = BackpackMenu.TAB_TOP + i * BackpackMenu.TAB_HEIGHT;

			graphics.text(this.font, label,
				BackpackMenu.TAB_STRIP_X + 4, tabY + 4,
				active ? TAB_TEXT : TAB_TEXT_DIM, false);
		}

		// 悬停时在按钮右边浮出说明(照精妙背包的提示,不占常驻空间)。
		//
		// ⚠️ extractLabels 收到的 mouseX/mouseY 【本来就是屏幕绝对】——
		// 这里再加 leftPos/topPos 就永远命中不了(踩过:表现为"悬停没提示")。
		final int hoveredTab = this.tabAt(mouseX, mouseY);
		if (hoveredTab != NO_TAB_HIT) {
			final Component full = Component.translatable(BackpackMenu.TAB_HINT_KEYS[hoveredTab]);
			final int hintY = BackpackMenu.TAB_TOP + hoveredTab * BackpackMenu.TAB_HEIGHT;
			final int hintX = BackpackMenu.TAB_STRIP_X + BackpackMenu.TAB_STRIP_WIDTH + 4;

			// 一圈深色底,保证在背包格上也看得清
			graphics.fill(hintX - 2, hintY - 1,
				hintX + this.font.width(full) + 2, hintY + 11, 0xF0000000);
			graphics.text(this.font, full, hintX, hintY, 0xFFFFFFFF, false);
		}

		// 弹窗内容(在面板外,用负坐标)
		if (this.menu.openTab() == BackpackMenu.TAB_FEED) {
			graphics.text(this.font, Component.translatable("gui.ergu_monster_backpack.tab.feed.hint"),
				-BackpackMenu.POPUP_WIDTH + 6, 24, TEXT_DIM, false);
		}

		// 饱食度数字(画在条下面)
		final Component satietyText = Component.literal(
			this.menu.satiety() + "/" + BackpackGrowth.MAX_SATIETY);
		graphics.text(this.font, satietyText, INFO_X + 2, 82, TEXT_DIM, false);

		// 玩家物品栏标签(照原版的坐标)
		graphics.text(this.font, this.playerInventoryTitle,
			this.inventoryLabelX, this.inventoryLabelY, TEXT_NORMAL, false);
	}

	// ------------------------------------------------------------ 侧签的命中判定

	// ------------------------------------------------------------ 交互

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		// ---- ① 侧签 ----
		final int hit = this.tabAt((int) event.x(), (int) event.y());

		if (hit != NO_TAB_HIT) {
			// 点已经打开的那个 → 关掉它
			final int next = hit == this.menu.openTab() ? BackpackMenu.TAB_NONE : hit;
			ClientPlayNetworking.send(new SelectTabPayload(next));
			return true;
		}

		// ---- ② Shift + 左键 → 开始"划动存取" ----
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
		return super.mouseReleased(event);
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

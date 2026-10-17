package com.ergu.monsterbackpack.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import com.ergu.monsterbackpack.item.BackpackTooltipComponent;

/**
 * 背包 tooltip 的客户端渲染(见 DESIGN.md §七)。
 *
 * <h2>⭐ 核心:按住 Ctrl 才展开</h2>
 * 我们背包最多 84 格 —— <b>全展开的 tooltip 会盖满整个屏幕</b>。
 * 所以照旅行者背包的做法:
 * <pre>
 *   平时   → 只显示一行「内有 N 件 · 已长开 X 格」
 *   按住Ctrl → 展开成物品网格
 * </pre>
 * <b>四处(高/宽/文字/图像)都查同一个 {@link #expanded()}</b>,
 * 所以尺寸和内容<b>永远一致</b> —— 这是它这个设计最关键的一点。
 *
 * <h2>为什么不用 {@code CONTAINER} 的自动 tooltip</h2>
 * 那样没法控制"按住才展开"(见 {@code BackpackTooltipComponent} 的类注释,坑 17)。
 */
public class ClientBackpackTooltipComponent implements ClientTooltipComponent {

	/** 网格每行几格(和背包的 12 列不同 —— tooltip 窄一点更好看)。 */
	private static final int COLUMNS = 9;

	/** 每格的绘制尺寸(原版物品是 16,这里留 2px 间隙)。 */
	private static final int CELL = 18;

	private static final int HEADER_HEIGHT = 10;

	private final BackpackTooltipComponent component;

	public ClientBackpackTooltipComponent(final BackpackTooltipComponent component) {
		this.component = component;
	}

	/** ⭐ 唯一的开关 —— 四个尺寸/绘制方法都查它。 */
	private boolean expanded() {
		// ⚠️ 26.2 里【没有】Screen.hasControlDown() 这种静态方法
		// (和 Screen.hasShiftDown() 一样 —— 工作区约定里记过这个坑)。
		// 判断修饰键只有两条路:从事件上取(event.hasControlDown()),
		// 或者直接问输入系统。这里没有事件,所以用后者。
		final var window = Minecraft.getInstance().getWindow();

		return com.mojang.blaze3d.platform.InputConstants.isKeyDown(
				window, com.mojang.blaze3d.platform.InputConstants.KEY_LCONTROL)
			|| com.mojang.blaze3d.platform.InputConstants.isKeyDown(
				window, com.mojang.blaze3d.platform.InputConstants.KEY_RCONTROL);
	}

	private int rows() {
		return (this.component.items().size() + COLUMNS - 1) / COLUMNS;
	}

	@Override
	public int getHeight(final Font font) {
		if (!this.expanded() || this.component.items().isEmpty()) {
			return HEADER_HEIGHT;
		}
		return HEADER_HEIGHT + this.rows() * CELL;
	}

	@Override
	public int getWidth(final Font font) {
		if (!this.expanded() || this.component.items().isEmpty()) {
			return font.width(this.header());
		}
		return Math.min(this.component.items().size(), COLUMNS) * CELL;
	}

	@Override
	public void extractText(final GuiGraphicsExtractor graphics, final Font font, final int x, final int y) {
		graphics.text(font, this.header(), x, y, 0xFFAAAAAA, false);
	}

	@Override
	public void extractImage(
		final Font font, final int x, final int y,
		final int width, final int height,
		final GuiGraphicsExtractor graphics
	) {
		if (!this.expanded() || this.component.items().isEmpty()) {
			return;
		}

		final List<ItemStack> items = this.component.items();

		for (int i = 0; i < items.size(); i++) {
			final int col = i % COLUMNS;
			final int row = i / COLUMNS;
			final int itemX = x + col * CELL;
			final int itemY = y + HEADER_HEIGHT + row * CELL;

			// fakeItem:画一个"不存在的物品"(26.2 的 GuiGraphicsExtractor 原语)
			graphics.fakeItem(items.get(i), itemX, itemY);
			graphics.itemDecorations(font, items.get(i), itemX, itemY);
		}
	}

	/** 那一行摘要文字。 */
	private Component header() {
		final int count = this.component.totalCount();

		if (count == 0) {
			return Component.translatable("gui.ergu_monster_backpack.tooltip.empty");
		}

		final var player = Minecraft.getInstance().player;
		final ItemStack held = player == null ? ItemStack.EMPTY : player.getMainHandItem();

		return Component.translatable(
			"gui.ergu_monster_backpack.tooltip.summary",
			count,
			BackpackTooltipComponent.unlockedOf(held));
	}
}

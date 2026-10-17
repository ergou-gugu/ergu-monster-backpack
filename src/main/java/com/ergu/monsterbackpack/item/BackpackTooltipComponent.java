package com.ergu.monsterbackpack.item;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import com.ergu.monsterbackpack.component.ModComponents;

/**
 * 背包的 tooltip 内容(见 DESIGN.md §七)。
 *
 * <h2>它只搬数据,不画东西</h2>
 * 这是<b>双端配对</b>的一半:这个 record 是纯数据(两端都有),
 * 渲染在客户端的 {@code ClientBackpackTooltipComponent} 里。
 * 分工照旅行者背包的做法(见 {@code refs/背包界面研究报告.md} §5.4)。
 *
 * <h2>⚠️ 为什么不用 {@code CONTAINER} 组件的自动 tooltip</h2>
 * {@code ItemContainerContents} 自己实现了 {@code TooltipProvider},
 * 只要物品有 {@code CONTAINER} 就会<b>自动</b>往 tooltip 塞"内含物品"(工作区约定坑 17)。
 * 那样我们<b>没法控制</b>"按住 Ctrl 才展开"这件事。
 *
 * <p>所以走<b>自己实现 {@code getTooltipImage}</b> 这条路 ——
 * 和旅行者背包选择的一样,是完全绕开坑 17 的另一条路。
 */
public record BackpackTooltipComponent(List<ItemStack> items) implements TooltipComponent {

	/** 从背包物品里读出非空的格子。 */
	public static BackpackTooltipComponent of(final ItemStack backpack) {
		final ItemContainerContents contents = backpack.get(
			net.minecraft.core.component.DataComponents.CONTAINER);

		if (contents == null) {
			return new BackpackTooltipComponent(List.of());
		}

		final List<ItemStack> result = new ArrayList<>();
		contents.nonEmptyItemCopyStream().forEach(result::add);
		return new BackpackTooltipComponent(result);
	}

	/** 里面一共有多少件(按堆叠算,不是按格的种类数)。 */
	public int totalCount() {
		int total = 0;
		for (ItemStack stack : this.items) {
			total += stack.getCount();
		}
		return total;
	}

	/** 已解锁格数 —— 用来显示"装满程度"。 */
	public static int unlockedOf(final ItemStack backpack) {
		return backpack.getOrDefault(ModComponents.UNLOCKED_SLOTS, 0);
	}
}

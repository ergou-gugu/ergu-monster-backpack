package com.ergu.monsterbackpack.menu;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.ergu.monsterbackpack.growth.BackpackGrowth;

/**
 * 「喂食」侧签里那个格子。
 *
 * <h2>它是"瞬间消耗"型,不是储物格</h2>
 * 玩家把食物放进去 → <b>当场被吃掉</b> → 加饱食度 → 格子立刻空回来。
 * 所以它<b>不需要持久化</b>(用普通的 {@code SimpleContainer} 就行,
 * 关界面时本来就是空的)。
 *
 * <h2>为什么重写 {@code setByPlayer}</h2>
 * 这是<b>玩家操作</b>的入口,正好是"吃掉"的时机。
 * 而 {@code set(...)}(同步路径)不能碰 —— 碰了会让客户端回写同步数据时被误吃
 * (工作区约定坑 23 的同一个道理:两条写入路径性质不同)。
 */
public class FeedSlot extends Slot {

	/** 从这里实时取背包物品 —— 不缓存引用(坑 22)。 */
	private final java.util.function.Supplier<ItemStack> backpackGetter;

	/** 这个槽现在活不活 —— 由菜单的侧签状态决定。 */
	private final java.util.function.BooleanSupplier active;

	public FeedSlot(
		final net.minecraft.world.Container container,
		final int slot,
		final int x,
		final int y,
		final java.util.function.Supplier<ItemStack> backpackGetter,
		final java.util.function.BooleanSupplier active
	) {
		super(container, slot, x, y);
		this.backpackGetter = backpackGetter;
		this.active = active;
	}

	/** 侧签没打开时,这个格子是关的。 */
	@Override
	public boolean isActive() {
		return this.active.getAsBoolean();
	}

	/**
	 * 只收能吃的,而且<b>饱食度满了就不再收</b>。
	 *
	 * <p>满了还放行的话,玩家会眼睁睁看着一个金苹果被"吃掉"却一点用没有 ——
	 * 那是白白扔食物,属于设计红线。所以这里直接拒绝,
	 * 食物会留在鼠标上(原版对"放不进去"就是这个反馈)。
	 */
	@Override
	public boolean mayPlace(final ItemStack stack) {
		if (!stack.has(net.minecraft.core.component.DataComponents.FOOD)) {
			return false;
		}

		final ItemStack backpack = this.backpackGetter.get();
		return backpack.getOrDefault(com.ergu.monsterbackpack.component.ModComponents.SATIETY, 0)
			< BackpackGrowth.MAX_SATIETY;
	}

	/**
	 * 玩家把东西放进来的那一刻 —— <b>吃掉</b>。
	 *
	 * <p>加多少饱食度按食物的营养值换算,见 {@link #satietyOf}。
	 */
	@Override
	public void setByPlayer(final ItemStack stack) {
		if (stack.isEmpty()) {
			// 清空(取出/换位)时不消耗
			super.set(ItemStack.EMPTY);
			return;
		}

		BackpackGrowth.feed(this.backpackGetter.get(), satietyOf(stack));

		// 吃完就空 —— 这个格子不留东西
		super.set(ItemStack.EMPTY);
	}

	@Override
	public void setByPlayer(final ItemStack stack, final ItemStack previous) {
		this.setByPlayer(stack);
	}

	/** 这个格子永远不该"装住"东西。 */
	@Override
	public int getMaxStackSize() {
		return 1;
	}

	/**
	 * 一份食物值多少饱食度。
	 *
	 * <p>换算成 <b>1 ~ 6</b> 点,以免一个金苹果就把 20 点上限顶满。
	 * <ul>
	 *   <li>金苹果这类"特殊食物"给满档 6 点</li>
	 *   <li>普通食物按原版营养值(nutrition,1~8)折算</li>
	 * </ul>
	 */
	private static int satietyOf(final ItemStack food) {
		if (food.is(Items.GOLDEN_APPLE) || food.is(Items.ENCHANTED_GOLDEN_APPLE)) {
			return 6;
		}

		final var foodData = food.get(net.minecraft.core.component.DataComponents.FOOD);
		if (foodData == null) {
			return 1;
		}

		// 原版 nutrition 大概是 1~8;压到 1~6
		return Math.clamp(foodData.nutrition() / 2 + 1, 1, 6);
	}

	/** 供界面用:这个槽现在是不是活的(由菜单的侧签状态决定)。 */
}

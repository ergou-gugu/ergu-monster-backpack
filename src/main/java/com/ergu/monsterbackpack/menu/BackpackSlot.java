package com.ergu.monsterbackpack.menu;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 背包里的一个格子(共 {@value BackpackContainer#SIZE} 个)。
 *
 * <h2>未解锁的格子 = 直接锁死</h2>
 * 2026-10-15 的机制改版之后,<b>格子不再接收材料</b>(材料全部转入技能树),
 * 所以未解锁格<b>拒绝一切交互</b>就行 —— 比原方案简单得多,不再需要"只接受某种材料"的守卫。
 *
 * <h2>⚠️ 为什么 {@code set(...)} 不能写成无条件空实现</h2>
 * <b>客户端同步 ItemStack 走的正是 {@code Slot.set(...)}</b>
 * (见 {@code AbstractContainerMenu.setItem})。无条件掐掉它,服务端数据正常但
 * <b>客户端永远收不到更新</b>,表现为"物品在减少,但界面上依赖其它组件的地方纹丝不动"(坑 21)。
 *
 * <p>正确做法是<b>加守卫</b>:放行"空栈"(那是同步用的),挡住往里塞东西。
 */
public class BackpackSlot extends Slot {

	private final BackpackContainer container;
	private final int index;

	public BackpackSlot(final BackpackContainer container, final int index, final int x, final int y) {
		super(container, index, x, y);
		this.container = container;
		this.index = index;
	}

	/** 本格是否已解锁。 */
	public boolean isUnlocked() {
		return this.index < this.container.unlockedSlots();
	}

	/**
	 * 本格是否<b>正在生长</b>。
	 *
	 * <p>因为一次只解锁一格,「正在生长的那一格」<b>恒等于</b>「已解锁格数」——
	 * 见 DESIGN.md §六 的数据结构说明。
	 */
	public boolean isGrowing() {
		return this.index == this.container.unlockedSlots();
	}

	/** 未解锁的格子不响应任何交互。 */
	@Override
	public boolean isActive() {
		return this.isUnlocked();
	}

	/**
	 * 未解锁的格子不放行。
	 *
	 * <p>第二项检查是<b>容器互斥</b>(见 DESIGN.md §十一):
	 * 凡是自己声明"不能放进容器"的东西(潜影盒、收纳袋、<b>另一个怪物背包</b>),
	 * 一律进不来。原版就靠 {@code Item.canFitInsideContainerItems()} 这一个钩子管控全部路径,
	 * 所以这里跟着问一次就够,不必为每种容器各写一遍。
	 */
	@Override
	public boolean mayPlace(final ItemStack stack) {
		return this.isUnlocked() && stack.getItem().canFitInsideContainerItems();
	}

	@Override
	public boolean mayPickup(final Player player) {
		return this.isUnlocked();
	}

	/** 原版的"禁止修改"钩子,{@code tryRemove} 等会查它。 */
	@Override
	public boolean allowModification(final Player player) {
		return this.isUnlocked();
	}

	/** 挡住 Q 键丢弃等直接取走的路径。 */
	@Override
	public ItemStack remove(final int amount) {
		return this.isUnlocked() ? super.remove(amount) : ItemStack.EMPTY;
	}

	/** 守卫式写入:解锁的格子照常,锁着的只放行空栈(同步要用)。 */
	@Override
	public void set(final ItemStack stack) {
		if (this.isUnlocked() || stack.isEmpty()) {
			super.set(stack);
		}
	}

	/** 让玩家动作路径也走上面这个带守卫的 set,避免另开一个绕过守卫的写入口。 */
	@Override
	public void setByPlayer(final ItemStack stack) {
		this.set(stack);
	}

	@Override
	public void setByPlayer(final ItemStack stack, final ItemStack previous) {
		this.set(stack);
	}
}

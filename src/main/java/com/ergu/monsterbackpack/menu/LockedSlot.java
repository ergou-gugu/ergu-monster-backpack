package com.ergu.monsterbackpack.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.ergu.monsterbackpack.item.ModItems;

/**
 * 锁住的槽位 —— 用来钉住<b>正被打开的那个背包</b>所在的物品栏格。
 *
 * <h2>为什么必须锁</h2>
 * 开界面时记录的是"<b>第 N 格那个背包</b>"。如果玩家在界面里把它拖到别的格子,
 * 那个位置就换成别的东西了,界面会去读一个不相干的物品。
 *
 * <h2>⚠️ 最大的坑:不要把 {@code set(...)} 写成无条件空实现</h2>
 * <b>客户端同步 ItemStack 走的正是 {@code Slot.set(...)}</b>
 * (见 {@code AbstractContainerMenu.setItem})。无条件掐掉它,服务端数据正常但
 * <b>客户端永远收不到更新</b>,表现为"依赖组件的地方纹丝不动"(工作区约定坑 21)。
 *
 * <p>所以这里是<b>守卫式</b>的:只放行"空栈"(同步要用)与"背包物品"本身
 * (它本来就该待在这一格),挡住换成别的东西。
 */
public class LockedSlot extends Slot {

	public LockedSlot(final Container container, final int slot, final int x, final int y) {
		super(container, slot, x, y);
	}

	@Override
	public boolean mayPlace(final ItemStack stack) {
		return false;
	}

	@Override
	public boolean mayPickup(final Player player) {
		return false;
	}

	/** 原版的"禁止修改"钩子,{@code tryRemove} 等会查它。 */
	@Override
	public boolean allowModification(final Player player) {
		return false;
	}

	/** 挡住 Q 键丢弃等直接取走的路径。 */
	@Override
	public ItemStack remove(final int amount) {
		return ItemStack.EMPTY;
	}

	/**
	 * 守卫式写入(这条是<b>客户端同步</b>走的路径,必须放行空栈)。
	 *
	 * <p>为什么放行空栈:同步可能用它来清一个槽位,堵死会导致界面不更新(坑 21)。
	 */
	@Override
	public void set(final ItemStack stack) {
		if (stack.isEmpty() || stack.getItem() == ModItems.MONSTER_BACKPACK) {
			super.set(stack);
		}
	}

	/**
	 * ⚠️⚠️ 玩家操作路径 —— <b>必须严格,不能转发给 {@link #set}</b>。
	 *
	 * <p>这是实测踩到的漏斗:原版数字键 SWAP 的实现
	 * ({@code AbstractContainerMenu.java:480} 起)是
	 * <pre>
	 *   target.setByPlayer(ItemStack.EMPTY);   // 先把被点的槽位清空
	 *   target.setByPlayer(source);            // 再放新的
	 * </pre>
	 * 第一句传的是<b>空栈</b>。如果这里转发给 {@code set},而 {@code set} 又放行空栈
	 * (那是给同步用的),<b>数字键就能把锁住的背包直接切走</b>。
	 *
	 * <p>所以这条路径只放行"本来就该待在这一格的东西" ——
	 * 空栈不算、别的东西也不算。
	 */
	@Override
	public void setByPlayer(final ItemStack stack) {
		if (stack.getItem() == ModItems.MONSTER_BACKPACK) {
			super.set(stack);
		}
	}

	@Override
	public void setByPlayer(final ItemStack stack, final ItemStack previous) {
		this.setByPlayer(stack);
	}
}

package com.ergu.monsterbackpack.menu;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.ergu.monsterbackpack.growth.GrowthRules;

/**
 * 一个挂饰槽。
 *
 * <h2>两道门</h2>
 * <ol>
 *   <li><b>侧签打开了吗</b> —— 「挂饰」签没开就不可用(和其他侧签槽一样)</li>
 *   <li><b>这个槽解锁了吗</b> —— 按 DESIGN.md §八:
 *       <b>第 1 个槽要阶段 2、第 2 个槽要阶段 3</b>,一个阶段解锁一个。
 *       阶段由 {@code unlocked_slots} 推导,不需要额外存。</li>
 * </ol>
 *
 * <h2>挂饰不限类型</h2>
 * 任何物品都能插(效果由挂饰自己决定,见 §八)。所以这里<b>不限制</b> {@code mayPlace} ——
 * 以后加了真正的挂饰物品,也不需要改这个类。
 */
public class CharmSlot extends Slot {

	private final BooleanSupplier tabOpen;
	/** 当前阶段 —— <b>从菜单的同步通道取</b>(独立存的组件,不从格数算)。 */
	private final java.util.function.IntSupplier stageGetter;
	/** 这是第几个槽(0 起)。 */
	private final int slotIndex;

	public CharmSlot(
		final Container container,
		final int index,
		final int x,
		final int y,
		final java.util.function.IntSupplier stageGetter,
		final BooleanSupplier tabOpen
	) {
		super(container, index, x, y);
		this.stageGetter = stageGetter;
		this.tabOpen = tabOpen;
		this.slotIndex = index;
	}

	/** 这个槽本身解锁了没(阶段够不够)。 */
	public boolean isUnlocked() {
		// ⚠️ 阶段是【独立存的组件】,不从格数算 —— 每行格子数会变(见 ModComponents.STAGE)
		final int stage = this.stageGetter.getAsInt();

		// 槽 0 要阶段 2,槽 1 要阶段 3
		return stage >= this.slotIndex + 2;
	}

	@Override
	public boolean isActive() {
		return this.tabOpen.getAsBoolean() && this.isUnlocked();
	}

	@Override
	public boolean mayPlace(final ItemStack stack) {
		return this.isActive();
	}

	@Override
	public boolean mayPickup(final Player player) {
		return this.isActive();
	}

	@Override
	public boolean allowModification(final Player player) {
		return this.isActive();
	}

	@Override
	public ItemStack remove(final int amount) {
		return this.isActive() ? super.remove(amount) : ItemStack.EMPTY;
	}

	/** 守卫式写入:同步要能进来(空栈),没解锁时挡住玩家塞东西。 */
	@Override
	public void set(final ItemStack stack) {
		if (this.isActive() || stack.isEmpty()) {
			super.set(stack);
		}
	}

	/**
	 * 玩家操作路径 —— <b>严格</b>。
	 *
	 * <p>见工作区约定坑 23:数字键 SWAP 用 {@code setByPlayer(EMPTY)} 把东西"拿走",
	 * 所以这条路径不能像 {@code set} 那样放行空栈。
	 */
	@Override
	public void setByPlayer(final ItemStack stack) {
		if (this.isActive()) {
			super.set(stack);
		}
	}

	@Override
	public void setByPlayer(final ItemStack stack, final ItemStack previous) {
		this.setByPlayer(stack);
	}
}

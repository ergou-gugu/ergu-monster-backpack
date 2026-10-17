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
public class CharmSlot extends Slot implements SideTabSlot {

	/** 属于哪个侧签。 */
	private final int tabId;
	/** 它"正常"时的 x(因为 {@code slot.x} 会被挪去隐藏位)。 */
	private final int homeX;
	/**
	 * 这个槽解锁了没 —— **由菜单提供,读技能树节点**(`charm_slot_1` / `charm_slot_2`)。
	 *
	 * <p>入参是**槽索引**(0 / 1),返回该槽解锁没。
	 *
	 * <h2>⚠️ 2026-10-15:从 "阶段 ≥ N" 换成技能树节点</h2>
	 * 以前这里是 {@code IntSupplier stageGetter},判断 {@code stage >= slotIndex + 2} ——
	 * 也就是"槽 0 要阶段 2、槽 1 要阶段 3"。
	 *
	 * <p>那和虚空的 {@code stage >= 3} 是同一类占位:
	 * **阶段是"它长了多久",而挂饰槽是"玩家花材料解锁的能力"** —— 两者不该绑定。
	 * (§六 早就把这两个节点标成「🔹 到时自动解锁」,现在真的接上了。)
	 */
	private final java.util.function.IntPredicate unlockGetter;
	/** 这是第几个槽(0 起)。 */
	private final int slotIndex;

	public CharmSlot(
		final Container container,
		final int index,
		final int x,
		final int y,
		final java.util.function.IntPredicate unlockGetter,
		final int tabId
	) {
		super(container, index, x, y);
		this.unlockGetter = unlockGetter;
		this.tabId = tabId;
		this.homeX = x;
		this.slotIndex = index;
	}

	@Override
	public int tabId() {
		return this.tabId;
	}

	@Override
	public int homeX() {
		return this.homeX;
	}

	/** 这个槽本身解锁了没(对应的技能树节点解锁了吗)。 */
	public boolean isUnlocked() {
		// ⚠️ 用 slotIndex 而不是 index —— 后者会被 addSlot 覆盖成【菜单全局】索引(坑 31)。
		// 这里 slotIndex 是构造时自己存的那个(0 / 1),不受影响。
		return this.unlockGetter.test(this.slotIndex);
	}

	/**
	 * 现在能用吗 —— <b>只看"这个槽解锁了没"</b>。
	 *
	 * <p>⚠️ "它那个签开没开"<b>不在这里管</b> —— 那由坐标决定
	 * (签没开时整个槽被挪到 {@link BackpackMenu#HIDDEN_SLOT_X},见 {@link SideTabSlot})。
	 * 分成两件事以后,就不存在"签关了但槽还在原位"的隐患。
	 */
	@Override
	public boolean isActive() {
		return this.isUnlocked();
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

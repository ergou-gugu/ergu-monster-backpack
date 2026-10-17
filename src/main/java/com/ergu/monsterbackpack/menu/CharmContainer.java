package com.ergu.monsterbackpack.menu;

import java.util.function.Supplier;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import com.ergu.monsterbackpack.component.ModComponents;

/**
 * 两个挂饰槽的内容视图 —— 读写背包的 {@code charms} 组件(见 DESIGN.md §八)。
 *
 * <h2>和 {@link BackpackContainer} 是一套思路</h2>
 * 同样<b>不持有 {@code ItemStack} 引用</b>(原版同步会换实例,见工作区约定坑 22),
 * 同样在 {@code setChanged()} 时写回组件。
 *
 * <h2>为什么不复用 BackpackContainer</h2>
 * 它是 84 格 + 容量自己管;挂饰只有固定 2 格,而且用<b>另一个组件</b>存。
 * 硬塞进去会让那个类同时管两件事(而且 {@code SIZE} 是编译期常量),
 * 所以这里单开一个 —— 代码很短,读起来也更清楚。
 */
public class CharmContainer implements Container {

	/** 挂饰槽数量。 */
	public static final int SIZE = 2;

	private final Supplier<ItemStack> backpackGetter;
	private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

	/** 上一次看到的那个对象 —— 用来发现"被同步换过"。 */
	private ItemStack cachedStack;

	public CharmContainer(final Supplier<ItemStack> backpackGetter) {
		this.backpackGetter = backpackGetter;
		this.syncFromStack();
	}

	private ItemStack backpack() {
		return this.backpackGetter.get();
	}

	private void syncFromStack() {
		final ItemStack current = this.backpack();
		if (current == this.cachedStack) {
			return;
		}
		this.cachedStack = current;

		for (int i = 0; i < SIZE; i++) {
			this.items.set(i, ItemStack.EMPTY);
		}

		final ItemContainerContents contents = current.get(ModComponents.CHARMS);
		if (contents != null) {
			contents.copyInto(this.items);
		}
	}

	private void flush() {
		this.backpack().set(ModComponents.CHARMS, ItemContainerContents.fromItems(this.items));
	}

	// ------------------------------------------------------------------ Container

	@Override
	public int getContainerSize() {
		return SIZE;
	}

	@Override
	public boolean isEmpty() {
		this.syncFromStack();
		return this.items.get(0).isEmpty() && this.items.get(1).isEmpty();
	}

	@Override
	public ItemStack getItem(final int slot) {
		this.syncFromStack();
		return this.items.get(slot);
	}

	@Override
	public ItemStack removeItem(final int slot, final int count) {
		this.syncFromStack();
		final ItemStack result = ContainerHelper.removeItem(this.items, slot, count);
		if (!result.isEmpty()) {
			this.setChanged();
		}
		return result;
	}

	@Override
	public ItemStack removeItemNoUpdate(final int slot) {
		this.syncFromStack();
		return ContainerHelper.takeItem(this.items, slot);
	}

	@Override
	public void setItem(final int slot, final ItemStack stack) {
		this.syncFromStack();
		this.items.set(slot, stack);
		this.setChanged();
	}

	@Override
	public void setChanged() {
		this.flush();
		// 🆕 挂饰变了 → 重算属性加成(幸运符这类)。
		// ⚠️ 只有这里(事件驱动),绝不在每 tick 的地方调 —— 见工作区约定坑 20。
		com.ergu.monsterbackpack.charm.CharmEffects.recalculate(this.backpack());
	}

	@Override
	public boolean stillValid(final Player player) {
		return true;
	}

	@Override
	public void clearContent() {
		for (int i = 0; i < SIZE; i++) {
			this.items.set(i, ItemStack.EMPTY);
		}
		this.setChanged();
	}
}

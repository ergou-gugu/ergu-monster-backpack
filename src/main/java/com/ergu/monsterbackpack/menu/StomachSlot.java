package com.ergu.monsterbackpack.menu;

import java.util.function.BooleanSupplier;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.ergu.monsterbackpack.growth.StomachSmelter;

/**
 * 「胃」的输入格。
 *
 * <h2>两道门</h2>
 * <ol>
/**
 * 「砂囊」的输入格。
 *
 * <h2>两道门</h2>
 * <ol>
 *   <li><b>它那个侧签打开了吗</b> —— 由<b>坐标</b>决定(签没开时被挪出屏幕,见 {@link SideTabSlot})</li>
 *   <li><b>是不是粗矿</b> —— 只有粗铜 / 粗铁 / 粗金能进去</li>
 * </ol>
 *
 * <p>对比「喂食」格:{@code FeedSlot} 是<b>瞬间消耗</b>(放进去当场吃掉),
 * 而这个是<b>慢慢消化</b>(放进去会留着,过一会儿才变成锭)。
 */
public class StomachSlot extends Slot implements SideTabSlot {

	/** 属于哪个侧签。 */
	private final int tabId;
	/** 它"正常"时的 x(因为 {@code slot.x} 会被挪去隐藏位)。 */
	private final int homeX;

	public StomachSlot(
		final Container container,
		final int index,
		final int x,
		final int y,
		final int tabId
	) {
		super(container, index, x, y);
		this.tabId = tabId;
		this.homeX = x;
	}

	@Override
	public int tabId() {
		return this.tabId;
	}

	@Override
	public int homeX() {
		return this.homeX;
	}

	/** 只收能烧的粗矿。 */
	@Override
	public boolean mayPlace(final ItemStack stack) {
		return StomachSmelter.isSmeltable(stack);
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

	/**
	 * 守卫式写入。
	 *
	 * <p>{@code set}(同步路径)放行空栈;{@code setByPlayer}(玩家操作路径)严格 ——
	 * 两条路径性质不同,见工作区约定坑 23。
	 */
	@Override
	public void set(final ItemStack stack) {
		if (this.isActive() || stack.isEmpty()) {
			super.set(stack);
		}
	}

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

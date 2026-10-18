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
 * 「胃」里那一格的视图 —— 读写背包的 {@code stomach} 组件(见 DESIGN.md §十二 F16)。
 *
 * <h2>⚠️ 为什么必须"缓存 + 失效重读",不能每次现读组件</h2>
 * 这是 2026-10-15 实测踩到的坑:
 * 最初图省事,让 {@code getItem()} 每次都去组件里现解一个 {@code ItemStack} 出来 ——
 * 结果表现为<b>"放进去的东西会漏出来,而且一次漏一个"</b>。
 *
 * <p>原因:原版的 {@code AbstractContainerMenu.clicked} 会<b>多次</b>调
 * {@code getItem()} / {@code removeItem()} 来做判断,
 * 它默认"同一个槽位读到的是<b>同一个对象</b>"。
 * 每次现读会让这个假设失效,点击逻辑于是自己乱掉。
 *
 * <p>所以这里和 {@link BackpackContainer} / {@link CharmContainer} <b>完全一致</b>:
 * 用一份 {@code NonNullList} 做缓存,只在{@code setChanged()} 时写回组件。
 * <b>三个容器一套写法</b> —— 不要再有第二种。
 */
public class StomachContainer implements Container {

	/** 「胃」只有一格。 */
	public static final int SIZE = 1;

	private final Supplier<ItemStack> backpackGetter;
	private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

	/** 上一次看到的那个对象 —— 用来发现"被同步换过"(坑 22)。 */
	private ItemStack cachedStack;

	public StomachContainer(final Supplier<ItemStack> backpackGetter) {
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
		this.items.set(0, ItemStack.EMPTY);

		final ItemContainerContents contents = current.get(ModComponents.STOMACH);
		if (contents != null) {
			contents.copyInto(this.items);
		}
	}

	private void flush() {
		final ItemStack owner = this.backpack();
		// ⚠️ 背包没了就绝不写(否则写进全局空栈,砂囊里的东西静默消失)
		if (owner.isEmpty()) {
			this.cachedStack = null;
			return;
		}
		owner.set(ModComponents.STOMACH, ItemContainerContents.fromItems(this.items));
	}

	/**
	 * 🆕 <b>强制下次访问时重读组件</b> —— 与 {@link BackpackContainer#invalidate()} 同理。
	 *
	 * <p>⚠️ 这个类以前<b>没有</b>它,于是"砂囊在后台烧完、产物进背包"这条路径
	 * 改的是同一个 {@code ItemStack} 对象:对象身份没变 ⇒ 容器永远不重读 ⇒
	 * 客户端还显示旧输入、服务端却能再放一份 —— 表现为<b>烧一个复制一个</b>。
	 */
	public void invalidate() {
		this.cachedStack = null;
	}

	// ------------------------------------------------------------------ Container

	@Override
	public int getContainerSize() {
		return SIZE;
	}

	@Override
	public boolean isEmpty() {
		this.syncFromStack();
		return this.items.get(0).isEmpty();
	}

	@Override
	public ItemStack getItem(final int slot) {
		this.syncFromStack();
		return this.items.get(0);
	}

	@Override
	public ItemStack removeItem(final int slot, final int count) {
		this.syncFromStack();
		final ItemStack result = ContainerHelper.removeItem(this.items, 0, count);

		if (!result.isEmpty()) {
			this.setChanged();
		}
		return result;
	}

	@Override
	public ItemStack removeItemNoUpdate(final int slot) {
		this.syncFromStack();
		return ContainerHelper.takeItem(this.items, 0);
	}

	/**
	 * 放东西进来 —— 换内容就意味着<b>重新开始消化</b>,所以顺手清掉计时。
	 *
	 * <p>(计时是"起点时间戳",不清的话新放进去的会被当成"已经烧了一会儿"。)
	 */
	@Override
	public void setItem(final int slot, final ItemStack stack) {
		this.syncFromStack();
		final ItemStack owner = this.backpack();
		this.items.set(0, stack);
		this.setChanged();

		// 内容变了 → 计时重来
		if (!owner.isEmpty()) {
			owner.set(ModComponents.STOMACH_START, -1);
		}
	}

	@Override
	public void setChanged() {
		this.flush();
	}

	@Override
	public boolean stillValid(final Player player) {
		return true;
	}

	@Override
	public void clearContent() {
		final ItemStack owner = this.backpack();
		this.items.set(0, ItemStack.EMPTY);
		this.setChanged();
		if (!owner.isEmpty()) {
			owner.set(ModComponents.STOMACH_START, -1);
		}
	}
}

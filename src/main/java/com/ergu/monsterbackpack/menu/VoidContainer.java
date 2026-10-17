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
 * 「虚空」样本槽白名单的视图 —— 读写背包的 {@code void_filter} 组件(见 DESIGN.md §十二 F17)。
 *
 * <h2>玩法</h2>
 * 丢一个【圆石】进样本槽 ⇒ 之后拾取到的所有圆石会被销毁。
 *
 * <h2>⚠️ 为什么 {@link #getContainerSize()} 是【固定 27】,而不是按等级返回 9 / 27</h2>
 * 这是刻意的选择,理由有两条:
 *
 * <ol>
 *   <li><b>原版点击逻辑会反复问 {@code getContainerSize()}</b> ——
 *       尺寸随等级变化,等于让"容器的形状"在运行中改变,
 *       很容易和原版的 slot 索引假设打架。</li>
 *   <li><b>和背包格的做法保持一致</b>:背包永远是 84 格,未解锁的走
 *       {@code BackpackSlot.isUnlocked()},而不是"把格数改小"。
 *       <b>容器形状恒定,可用性由槽位自己表达</b> —— 本工程统一按这个来。</li>
 * </ol>
 *
 * <p>所以等级只影响{@code VoidSlot} 的 {@code isUnlocked()}(9 → 27),
 * 以及界面画不画"未解锁"的暗色框。
 *
 * <h2>⚠️ 缓存 + 失效重读(和另外三个容器【完全一致】)</h2>
 * 见 {@link StomachContainer} 的类注释 —— 每次现读组件会导致
 * <b>"放进去的东西漏出来,一次漏一个"</b>(坑 26)。
 * <b>本工程的四个容器现在是一套写法,新增容器照抄,不要另发明。</b>
 */
public class VoidContainer implements Container {

	/** 虚拟容量 —— 固定给足,等级只影响可见性(见类注释)。 */
	public static final int SIZE = ModComponents.VOID_FILTER_SIZE;

	private final Supplier<ItemStack> backpackGetter;
	private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

	/** 上一次看到的那个对象 —— 用来发现"被同步换过"(坑 22)。 */
	private ItemStack cachedStack;

	public VoidContainer(final Supplier<ItemStack> backpackGetter) {
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
		this.items.clear();

		final ItemContainerContents contents = current.get(ModComponents.VOID_FILTER);
		if (contents != null) {
			contents.copyInto(this.items);
		}
	}

	private void flush() {
		this.backpack().set(ModComponents.VOID_FILTER,
			ItemContainerContents.fromItems(this.items));
	}

	/**
	 * 强制下次访问时<b>重读组件</b>。
	 *
	 * <h2>⚠️ 为什么"服务端改组件"需要显式通知,而"容器同步"不需要</h2>
	 * {@link #syncFromStack()} 是按<b>对象身份</b>判断要不要重读的(坑 22 的修法):
	 * {@code current != cachedStack} 才重读。
	 *
	 * <ul>
	 *   <li><b>容器同步</b>会把槽里的 {@code ItemStack} 实例<b>换掉</b> ⇒ 身份变了 ⇒ 自动重读 ✓</li>
	 *   <li><b>服务端自己 {@code set(...)} 组件</b>改的是<b>同一个对象</b> ⇒ 身份没变 ⇒
	 *       <b>缓存不会失效</b> ⚠️</li>
	 * </ul>
	 *
	 * <p>⇒ 凡是"服务端绕过本容器直接改 {@code void_filter}"的地方
	 * (目前只有 {@code BackpackMenu.setVoidSample}),都要调一次本方法。
	 *
	 * <p>⚠️ 这也是坑 22 的另一面:<b>失效检查只认对象身份,就必然有"同对象改内容"漏网</b>。
	 * 补的办法就是这个显式失效口。
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
		return this.items.stream().allMatch(ItemStack::isEmpty);
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

		// ⚠️ 样本槽里只留样本的"种类",数量意义不大 ——
		// 但**不能**在这里改成 1:玩家一次拖 64 个圆石进来,
		// 原版会按槽位容量自动分配。我们只在【销毁判定】时忽略数量。
		this.items.set(slot, stack);
		this.setChanged();
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
		this.syncFromStack();
		this.items.clear();
		this.setChanged();
	}

	// ⚠️ 刻意【不在这里】再写一个 shouldVoid(...)
	//
	// 我第一版写过一个,后来把规则抽到 voiding/VoidRules 时忘了删 ——
	// 结果同一套判定有两份实现(2026-10-15 由源码复核发现)。
	// 服务端只用 VoidRules,界面侧也【不需要】再判一次 ——
	// 判定统一走 VoidRules.shouldVoid(...) / matchesIn(...),唯一规则源。
}

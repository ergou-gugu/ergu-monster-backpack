package com.ergu.monsterbackpack.menu;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import com.ergu.monsterbackpack.component.ModComponents;

/**
 * 背包内容的容器视图 —— <b>直接读写物品的 {@code CONTAINER} 组件</b>。
 *
 * <h2>为什么不用 {@code SimpleContainer}</h2>
 * 菜单里的临时容器<b>关界面就把东西还给玩家了</b>。本 mod 的内容必须存在
 * <b>物品自己的组件</b>上(工作区约定坑 19),所以这里做一个直接读写组件的 {@link Container}。
 *
 * <h2>⚠️⚠️ 为什么不持有那个 {@code ItemStack}</h2>
 * 这是 V0.1 实测踩到的坑:<b>原版容器同步会把槽位里的 {@code ItemStack} 换成新实例</b>。
 * 如果在构造时把引用存下来,客户端就会一直拿着<b>旧对象</b> ——
 * 表现为:<b>服务端明明在长,界面上却纹丝不动;关掉界面再打开(重建 Menu)才看到变化</b>。
 *
 * <p>所以这里改成:每次访问前先比较一次<b>对象身份</b>({@code !=} 极便宜),
 * 发现被换过就重新读入。<b>服务端</b>自己改的组件不会换对象,所以不会被这次重读覆盖。
 *
 * <h2>为什么容量要自己管</h2>
 * {@code ItemContainerContents} <b>不记录容量</b> —— {@code fromItems} 只列表到
 * <b>最后一个非空槽</b>,所以"3 格全空"和"63 格全空"在数据上完全一样。
 * 容量由 {@code unlocked_slots} 组件表达,见 {@link ModComponents}。
 *
 * <h2>写回时机</h2>
 * 只在 {@link #setChanged()} 里写回,而且<b>只在服务端写</b>(客户端等同步)。
 * <b>绝不每 tick 写组件</b>(坑 20)。
 */
public class BackpackContainer implements Container {

	/** 最大格数:7 行 × 12 列 = 84(2026-10-15 用户实测后从 63 上调,见 DESIGN.md §七)。 */
	public static final int SIZE = 84;

	private final java.util.function.Supplier<ItemStack> stackGetter;

	/** 已解锁格数 —— 从菜单的同步通道取(见构造器说明)。 */
	private final java.util.function.IntSupplier unlockedGetter;

	private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

	/** 上一次看到的那个 {@code ItemStack} 对象 —— 用来发现"被同步换过"。 */
	private ItemStack cachedStack;

	/**
	 * @param stackGetter    <b>每次调用都要返回"现在那个"背包物品</b> —— 不要传已经取好的 {@code ItemStack}
	 *                       (原版同步会换实例,传进来的会变成永远不更新的旧对象)
	 * @param unlockedGetter 已解锁格数。<b>走菜单的同步通道</b>,不要自己去读物品 ——
	 *                       方块形态下客户端那份物品拿不到真实格数。
	 */
	public BackpackContainer(
		final java.util.function.Supplier<ItemStack> stackGetter,
		final java.util.function.IntSupplier unlockedGetter
	) {
		this.stackGetter = stackGetter;
		this.unlockedGetter = unlockedGetter;
		this.syncFromStack();
	}

	/** 当前那一份背包物品(每次都重新取,不缓存引用)。 */
	private ItemStack backpack() {
		return this.stackGetter.get();
	}

	/**
	 * 若物品对象被替换过(说明收到了服务端同步),就把内容重新读一遍。
	 *
	 * <p>对象没换时几乎零成本 —— 一次引用比较。
	 */
	private void syncFromStack() {
		final ItemStack current = this.backpack();
		if (current == this.cachedStack) {
			return;
		}

		this.cachedStack = current;

		for (int i = 0; i < SIZE; i++) {
			this.items.set(i, ItemStack.EMPTY);
		}

		final ItemContainerContents contents = current.get(DataComponents.CONTAINER);
		if (contents != null) {
			contents.copyInto(this.items);
		}
	}

	/**
	 * ⚠️⚠️ <b>强制下次访问时重读组件。</b>
	 *
	 * <h2>为什么需要它,以及为什么"对象身份"不够</h2>
	 * {@link #syncFromStack()} 判断"要不要重读"用的是<b>对象身份</b> ——
	 * 这在"服务端同步过来 ⇒ 换了新对象"那条路上是够的(那也是它本来的目的)。
	 *
	 * <p>但<b>服务端自己改组件的时候,对象【不会换】</b>:
	 * <pre>
	 *   backpack.set(DataComponents.CONTAINER, ...);   // 同一个 ItemStack 对象
	 * </pre>
	 * ⇒ {@code current == cachedStack} 恒为真 ⇒ <b>容器永远不重读</b>。
	 *
	 * <h2>本工程踩点(2026-10-15,前后踩了两次)</h2>
	 * <ul>
	 *   <li>{@code VoidContainer} —— 登记虚空样本后,格子里看不到东西</li>
	 *   <li>{@code BackpackContainer} —— <b>虚空把背包里的东西销毁了,但界面上还画着</b>;
	 *       玩家的描述是"<b>放进去物品后背包内的对应物品会失去显示(左键能拿到但是不显示)</b>"</li>
	 * </ul>
	 * 两次是同一个根因。⇒ <b>凡是"绕过容器直接改组件"的地方,都要回来调一次这个方法。</b>
	 *
	 * <p>⚠️ 置 {@code null} 是安全的:下一次 {@code current == cachedStack} 必然为假
	 * ({@code stackGetter} 至少返回 {@code ItemStack.EMPTY},不会是 null)。
	 */
	public void invalidate() {
		this.cachedStack = null;
	}

	/**
	 * 把当前内容写回物品组件。
	 *
	 * <p>两端都写:<b>服务端</b>写的是真身;<b>客户端</b>写的是本地副本,
	 * 之后会被服务端的同步覆盖掉,所以不会打架。
	 * (这里刻意不去判断"是不是客户端" —— 那需要拿到 {@code Level},会把这个类绑死在游戏环境上。)
	 */
	private void flush() {
		this.backpack().set(DataComponents.CONTAINER, ItemContainerContents.fromItems(this.items));
	}

	// ---------------------------------------------------------------- 成长状态

	/**
	 * 已解锁格数。
	 *
	 * <p>⚠️ <b>实时读,不缓存</b> —— 这个值会随同步变化,缓存了界面就不会自己更新。
	 */
	public int unlockedSlots() {
		return this.unlockedGetter.getAsInt();
	}

	/** 当前这格的「开始计时」时间戳;-1 = 未开始。 */
	public int unlockStart() {
		return this.backpack().getOrDefault(ModComponents.UNLOCK_START, -1);
	}

	/** 供界面标题等处使用。 */
	public ItemStack backpackStack() {
		return this.backpack();
	}

	// ------------------------------------------------------------------ Container

	@Override
	public int getContainerSize() {
		return SIZE;
	}

	@Override
	public boolean isEmpty() {
		this.syncFromStack();
		for (ItemStack stack : this.items) {
			if (!stack.isEmpty()) {
				return false;
			}
		}
		return true;
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

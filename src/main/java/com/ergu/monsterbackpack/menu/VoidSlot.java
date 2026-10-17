package com.ergu.monsterbackpack.menu;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.ergu.monsterbackpack.component.ModComponents;

/**
 * 「虚空」的<b>样本槽</b>(见 DESIGN.md §十二 F17)。
 *
 * <h2>玩法</h2>
 * <pre>
 * 丢一个【圆石】进来  →  之后拾取到的圆石会被销毁
 * </pre>
 *
 * <h2>两道门</h2>
 * <ol>
 *   <li><b>它那个侧签打开了吗</b> —— 由<b>坐标</b>决定(签没开时被挪到 {@code HIDDEN_SLOT_X},
 *       见 {@link SideTabSlot})。<b>不靠 {@code isActive()}</b> ——
 *       那样会出现"看不见但能点到"的陷阱。</li>
 *   <li><b>这个格子解锁了吗</b> —— 虚空 I 级 9 格、II 级 27 格
 *       ({@link #isUnlocked()})。未解锁的格子画暗色,而且<b>收不了东西也拿不走</b>。</li>
 * </ol>
 *
 * <h2>⚠️ 与 {@code StomachSlot} 的区别:这里没有 {@code mayPlace} 限制</h2>
 * 砂囊只收粗矿,而虚空的样本<b>可以是任何东西</b> ——
 * 但<b>不能收背包自己</b>(否则会出现"用背包过滤背包"的怪事,而且潜影盒那类会有递归风险)。
 */
public class VoidSlot extends Slot implements SideTabSlot {

	/** 属于哪个侧签。 */
	private final int tabId;
	/** 它"正常"时的 x(因为 {@code slot.x} 会被挪去隐藏位)。 */
	private final int homeX;
	/** 现在解锁到第几格 —— 由 {@code BackpackMenu} 按阶段/技能树传进来。 */
	private final IntSupplier unlockedCount;

	public VoidSlot(
		final Container container,
		final int index,
		final int x,
		final int y,
		final int tabId,
		final IntSupplier unlockedCount
	) {
		super(container, index, x, y);
		this.tabId = tabId;
		this.homeX = x;
		this.unlockedCount = unlockedCount;
	}

	@Override
	public int tabId() {
		return this.tabId;
	}

	@Override
	public int homeX() {
		return this.homeX;
	}

	/**
	 * 这一格解锁了吗(I 级前 9 格 / II 级全部 27 格)。
	 *
	 * <h2>⚠️⚠️ 必须用 {@code getContainerSlot()},【不能】用 {@code index}</h2>
	 * {@code Slot} 里有两个"索引",含义完全不同({@code Slot.java:11-13}):
	 * <pre>
	 *   private final int slot;   ← 构造时传的【容器内】索引(我们传 0..26)
	 *   public  int index;        ← 【菜单内全局】索引
	 * </pre>
	 * 而 {@code AbstractContainerMenu.addSlot(...)} 会把 {@code index} 覆盖成
	 * {@code this.slots.size()} —— 虚空的槽在菜单里排在背包 84 + 物品栏 36 + 侧签 4 之后,
	 * 于是 {@code index ≈ 124}。
	 *
	 * <p>我第一版写成了 {@code this.index < unlockedCount},结果
	 * <b>{@code 124 < 27} 恒为假 ⇒ 所有样本槽都是"未解锁" ⇒ {@code mayPlace} 返回 false
	 * ⇒ 【什么都放不进去】</b>。
	 *
	 * <p>实测症状(2026-10-15 用户截图):"虚空过滤放不进东西"。
	 * ⚠️ 这个 bug <b>编译不会报、运行也不崩</b> —— 只是静默地"全锁着"。
	 */
	public boolean isUnlocked() {
		return this.getContainerSlot() < this.unlockedCount.getAsInt();
	}

	/**
	 * 🆕 <b>样本槽不收拖进来的东西</b>(用户 2026-10-15 定的方案 B)。
	 *
	 * <h2>玩法</h2>
	 * <pre>
	 *   主手拿着【圆石】,点一下样本格  →  这一格登记"圆石"(主手那份【不消耗】)
	 *   空手点一下样本格                →  清掉这一格的登记
	 * </pre>
	 * 所以样本格是<b>只读展示位</b> —— 玩家操作走的是"点击"这条路,不是"拖放"。
	 *
	 * <h2>⚠️ 为什么要改成这样(原来允许拖入)</h2>
	 * 用户原话:"**我希望虚空过滤不需要放一个实际东西进去**"。
	 * 拖放要求"手上正好有圆石,而且要在格子里占一份" ——
	 * 想过滤一个<b>还没捡到</b>的东西就配不了,配错了还得再掏一份出来。
	 *
	 * <p>改成"点一下登记"之后:
	 * <ul>
	 *   <li>主手那份<b>不被消耗</b>(只是读它的种类)</li>
	 *   <li>同一份东西可以连着登记好几格</li>
	 *   <li>不需要为了配置而特地去弄一个样品</li>
	 * </ul>
	 *
	 * <p>⚠️ 拖放被禁用的实现点是这里({@code mayPlace} 返回 false)+
	 * {@code AbstractContainerMenu.clicked} 会问它;玩家"点击"的意图由
	 * {@code BackpackScreen.mouseClicked} 发 {@code SetVoidSamplePayload} 表达。
	 */
	@Override
	public boolean mayPlace(final ItemStack stack) {
		return false;
	}

	@Override
	public boolean mayPickup(final Player player) {
		return this.isActive() && this.isUnlocked();
	}

	@Override
	public boolean allowModification(final Player player) {
		return this.isActive() && this.isUnlocked();
	}

	@Override
	public ItemStack remove(final int amount) {
		return this.isActive() && this.isUnlocked() ? super.remove(amount) : ItemStack.EMPTY;
	}

	/**
	 * 守卫式写入。
	 *
	 * <p>{@code set}(同步路径)放行空栈;{@code setByPlayer}(玩家操作路径)严格 ——
	 * 两条路径性质不同,见工作区约定坑 23。
	 */
	@Override
	public void set(final ItemStack stack) {
		if (this.isUnlocked() && (this.isActive() || stack.isEmpty())) {
			super.set(stack);
		}
	}

	@Override
	public void setByPlayer(final ItemStack stack) {
		if (this.isUnlocked() && this.isActive()) {
			super.set(stack);
		}
	}

	@Override
	public void setByPlayer(final ItemStack stack, final ItemStack previous) {
		this.setByPlayer(stack);
	}
}

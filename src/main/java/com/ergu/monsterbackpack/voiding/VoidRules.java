package com.ergu.monsterbackpack.voiding;

import java.util.List;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import com.ergu.monsterbackpack.component.ModComponents;

/**
 * 虚空过滤的<b>纯规则</b>(见 DESIGN.md §十二 F17)。
 *
 * <h2>为什么单独一个类,而不是写进 {@code VoidContainer}</h2>
 * <b>服务端要用它,但服务端不需要「容器」那套东西</b> ——
 * {@code VoidContainer} 是为了让菜单能显示/编辑样本槽而存在的<b>视图</b>,
 * 而"这一份东西该不该销毁"是个<b>纯判断</b>,和菜单无关。
 *
 * <p>⇒ 拆开之后:
 * <ul>
 *   <li>{@code VoidContainer}(menu 包)—— 只在<b>界面打开时</b>存在</li>
 *   <li>{@code VoidRules}(本类)—— <b>随时可用</b>,不依赖菜单</li>
 * </ul>
 *
 * <h2>玩法(方案 B:拾取后销毁 —— 用户 2026-10-15 选定)</h2>
 * <pre>
 * ① 拾取时:符合白名单的掉落物【根本不进背包】,直接销毁
 * ② 周期清理:已经在背包里的,下一拍被吃掉   ← 这条是选 B 的理由
 * </pre>
 * 方案 A(拾取时过滤)只能防新的进来,<b>清不掉已经存满的垃圾</b>。
 */
public final class VoidRules {

	private VoidRules() {
	}

	/** 这个背包开着虚空吗。 */
	public static boolean isEnabled(final ItemStack backpack) {
		return backpack.getOrDefault(ModComponents.VOID_ENABLED, Boolean.FALSE);
	}

	/**
	 * 这一份东西在白名单里吗。
	 *
	 * <p>⚠️ <b>只比"种类",不比数量</b> —— 样本槽里放 1 个圆石,
	 * 要能销毁拾取到的 64 个圆石。
	 *
	 * <p>⚠️ <b>用 {@code ItemStack.isSameItem} 而不是 {@code ItemStack.matches}</b>:
	 * 后者会比<b>全部组件</b>(耐久 / 附魔 / 自定义名),
	 * 那样"样本是普通铁镐"就销毁不了"用过的铁镐"。
	 * 对"过滤垃圾"这个用途,<b>按物品种类</b>才是玩家期待的。
	 *
	 * <p>⚠️ 另外<b>刻意不管数量</b>:样本槽里是 64 个圆石时,
	 * 仍然按"圆石"这一个种类算 —— 否则玩家会以为"必须凑够 64 个才生效"。
	 */
	public static boolean matches(final ItemStack backpack, final ItemStack candidate) {
		return matchesIn(samplesOf(backpack), candidate);
	}

	/**
	 * 取出白名单样本 —— <b>供"循环外取一次、循环内反复用"</b>。
	 *
	 * <h2>⚠️ 26.2 的两个坑(都是编译器抓出来的,2026-10-15)</h2>
	 * <ol>
	 *   <li>{@code ItemContainerContents.nonEmptyItems()} 返回的是
	 *       <b>{@code Iterable<ItemStackTemplate>}</b>,【不是】{@code Iterable<ItemStack>} ——
	 *       直接 for-each 成 {@code ItemStack} 会报
	 *       "不兼容的类型: ItemStackTemplate无法转换为ItemStack"。
	 *       <b>必须先 {@code copyInto}</b>。</li>
	 *   <li>{@code ItemContainerContents} <b>没有 {@code size()}</b> ——
	 *       报"找不到符号: 方法 size()"。它只是"一袋东西",不承诺容量;
	 *       容量语义归我们自己的组件常量管。</li>
	 * </ol>
	 */
	public static NonNullList<ItemStack> samplesOf(final ItemStack backpack) {
		final NonNullList<ItemStack> list = NonNullList.withSize(
			ModComponents.VOID_FILTER_SIZE, ItemStack.EMPTY);

		final ItemContainerContents samples = backpack.get(ModComponents.VOID_FILTER);

		if (samples != null) {
			samples.copyInto(list);
		}
		return list;
	}

	/**
	 * 用<b>已经取好的</b>样本列表判定。
	 *
	 * <p>⚠️ <b>循环里必须用这个</b>,不要在循环里调 {@link #matches} ——
	 * 后者每次都会解一份 27 元素的列表,"遍历背包 84 格逐个判定"就变成 84 次分配。
	 */
	public static boolean matchesIn(final List<ItemStack> samples, final ItemStack candidate) {
		if (candidate.isEmpty()) {
			return false;
		}

		for (final ItemStack sample : samples) {
			if (!sample.isEmpty() && ItemStack.isSameItem(sample, candidate)) {
				return true;
			}
		}
		return false;
	}

	/** 该不该销毁这一份。 */
	public static boolean shouldVoid(final ItemStack backpack, final ItemStack candidate) {
		return isEnabled(backpack) && matches(backpack, candidate);
	}
}

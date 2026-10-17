package com.ergu.monsterbackpack.storage;

import java.util.function.Predicate;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import com.ergu.monsterbackpack.growth.GrowthRules;

/**
 * **背包内容格(CONTAINER 组件)的统一读写入口。**
 *
 * <h2>⚠️ 为什么要有这个类</h2>
 * 内容存在物品组件 {@code minecraft:container}({@link ItemContainerContents})里,而它:
 * <ul>
 *   <li><b>不可变</b> —— 改一个格子要整个重建({@code fromItems}),没有 {@code set(i, x)}</li>
 *   <li><b>不记容量</b> —— {@code fromItems} 只截到<b>最后一个非空格</b>,
 *       所以"全空"就是 {@code EMPTY}。容量得我们自己知道({@link GrowthRules#TOTAL_SLOTS})</li>
 * </ul>
 * ⇒ 每次读写都是「<b>建一个满容量的空表 → copyInto → 改 → fromItems → set 回去</b>」这套四步。
 *
 * <p>这套样板以前散在 {@code BackpackGrowth} 的四个方法里(还有即将出现的技能树扣材料),
 * <b>散着写迟早会有人漏掉某一步</b>(最典型的是忘了从 {@code CONTAINER} 里 copyInto,
 * 结果一改就把整包东西清空)。所以收口到这里。
 *
 * <h2>⚠️ 用完之后必须 {@link #commit} 或走 {@link #update}</h2>
 * {@link #read} 返回的是<b>一份拷贝</b>,改它<b>不会</b>影响背包。忘记写回 = 改动凭空消失,
 * 而且不报错 —— 这类"改了没生效"最难查,所以本类只提供"读了就一定会写回"的走法给常用场景。
 */
public final class BackpackStorage {

	/**
	 * 读出一份**拷贝** —— 满容量({@link GrowthRules#TOTAL_SLOTS})、空位是 {@link ItemStack#EMPTY}。
	 *
	 * <p>⚠️ 返回的列表<b>和背包没有关联</b>:改它不会影响背包,要用 {@link #commit} 写回。
	 * 这是刻意的 —— 组件不可变,本来就只能这样。
	 */
	public static NonNullList<ItemStack> read(final ItemStack backpack) {
		final NonNullList<ItemStack> items =
			NonNullList.withSize(GrowthRules.TOTAL_SLOTS, ItemStack.EMPTY);

		final ItemContainerContents contents = backpack.get(DataComponents.CONTAINER);
		if (contents != null) {
			contents.copyInto(items);
		}
		return items;
	}

	/**
	 * 写回。
	 *
	 * <p>⚠️ 传进来的列表**长度必须**是 {@link GrowthRules#TOTAL_SLOTS} ——
	 * 短了会让尾部格子静默消失(组件那边只认实际给的内容)。
	 */
	public static void commit(final ItemStack backpack, final NonNullList<ItemStack> items) {
		backpack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
	}

	/**
	 * 「读 → 改 → 写回」一次做完 —— **常用场景都走这个,不要手写三步**。
	 *
	 * <pre>
	 *   BackpackStorage.update(backpack, items -&gt; {
	 *       items.set(0, ItemStack.EMPTY);
	 *   });
	 * </pre>
	 *
	 * @param editor 在拷贝上做修改;返回无意义(直接改 {@code items})
	 */
	public static void update(final ItemStack backpack, final java.util.function.Consumer<NonNullList<ItemStack>> editor) {
		final NonNullList<ItemStack> items = read(backpack);
		editor.accept(items);
		commit(backpack, items);
	}

	/** 满足条件的物品**一共有多少个**(按数量累加,不是"占了几格")。 */
	public static int count(final ItemStack backpack, final Predicate<ItemStack> which) {
		int total = 0;
		for (final ItemStack stack : read(backpack)) {
			if (which.test(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	/** 背包里某种物品一共有多少个(只比**种类**,不看 NBT —— 同 §六 F17 的语义)。 */
	public static int countItem(final ItemStack backpack, final Item item) {
		return count(backpack, stack -> !stack.isEmpty() && stack.getItem() == item);
	}

	/**
	 * 从背包里扣掉至多 {@code amount} 个某种物品,**返回实际扣掉的数量**。
	 *
	 * <p>⚠️ <b>返回值和 {@code amount} 不一样是正常的</b> —— 调用方必须自己判断"够不够"
	 * (通常是先 {@link #countItem} 数一遍,确认够了再扣)。
	 * 返回实际值是为了让"两边一起扣"那套逻辑(背包 + 玩家物品栏)能知道背包这边出了多少。
	 *
	 * <p>⚠️ 一格扣不完就继续扣下一格 —— **不要只扣第一格**(会把"分散在多个格子里"当成不够)。
	 */
	public static int consume(final ItemStack backpack, final Item item, final int amount) {
		if (amount <= 0) {
			return 0;
		}

		// ⚠️ 用一个数组接返回值 —— lambda 里改不了外面的局部变量
		final int[] taken = {0};

		update(backpack, items -> {
			for (int i = 0; i < items.size() && taken[0] < amount; i++) {
				final ItemStack stack = items.get(i);
				if (stack.isEmpty() || stack.getItem() != item) {
					continue;
				}

				final int want = amount - taken[0];
				final int take = Math.min(want, stack.getCount());
				stack.shrink(take);
				taken[0] += take;

				// ⚠️ 扣到 0 就置成 EMPTY —— 留一个 0 数量的栈会让组件那边认为"这一格有东西",
				// 而且原版多处代码假设"空 = isEmpty()",不清掉会出怪事。
				if (stack.isEmpty()) {
					items.set(i, ItemStack.EMPTY);
				}
			}
		});

		return taken[0];
	}

	private BackpackStorage() {
	}
}

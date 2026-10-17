package com.ergu.monsterbackpack.storage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import com.ergu.monsterbackpack.growth.GrowthRules;

/**
 * **一键整理(F4)的排序算法。**
 *
 * <h2>算法三步</h2>
 * <pre>
 *   ① 从【已解锁格】里收集所有非空物品,顺便合并同类
 *   ② 排序(按注册名)
 *   ③ 从第 0 格开始填回,剩下的格子置空
 * </pre>
 *
 * <p>⚠️ **未解锁的格子【完全不碰】** —— 不收也不填。
 * 理由:那些格子对玩家来说"还不存在",而它们的坐标被挪到了屏幕外
 * ({@code HIDDEN_SLOT_X});如果排序往里塞东西,玩家会看到"东西凭空消失"。
 *
 * <h2>⚠️ 三家(精妙 / 旅行者 / 我们)的算法是同一个形状</h2>
 * 研究过两家的实现,都是"收集 → 排序 → 填回 + 清空多余":
 * <ul>
 *   <li><b>精妙</b> {@code InventorySorter.sortHandler} —— 压成 {@code Map<ItemStackKey,Integer>},
 *       先填"记忆槽"再填其余</li>
 *   <li><b>旅行者</b> {@code ContainerSorter.sortBackpack} —— {@code addStackWithMerge} 边收边合,
 *       再 {@code sort},再填回</li>
 * </ul>
 * <b>两家独立做出同一件事 ⇒ 这是这个问题域的正解。</b>
 *
 * <h2>⚠️ 为什么不用 "按显示名" 排</h2>
 * 显示名会随<b>语言</b>变("Diamond" / "钻石"),中文玩家和英文玩家排序结果不同;
 * 还会被<b>附魔 / 药水</b>前缀污染("锋利的下界合金剑")。
 * <b>注册名(`Identifier`)永远不变、与语言无关</b> ⇒ 可复现。
 * 附带好处:同 mod 的物品天然聚堆,这正是玩家要的"整理"。
 */
public final class BackpackSorter {

	/**
	 * 排序依据:**注册名的完整字符串**(`命名空间:路径`)。
	 *
	 * <p>⚠️ 用 {@code toString()} 而不是分开比 namespace / path ——
	 * 我们<b>不需要</b>"先按 mod 分组再按名字"那种精细控制,
	 * 而且 `minecraft:` 会排在所有 mod 前面,视觉上正好是"原版的东西在最前"。
	 */
	private static final Comparator<ItemStack> BY_REGISTRY_NAME =
		Comparator.comparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());

	private BackpackSorter() {
	}

	/**
	 * 整理背包内容。
	 *
	 * <p>⚠️ <b>一次 {@code update} 写完整个组件</b> —— 不是逐槽改。
	 * 逐槽改会触发十几次容器同步(而且 `ItemContainerContents` 本来就只能整体重建),
	 * 见 DESIGN.md §十三 R7。
	 *
	 * @param backpack      背包物品(方法内部改它的 `CONTAINER` 组件)
	 * @param unlockedSlots 当前解锁了几格 —— 只排 `[0, unlockedSlots)` 这一段
	 * @return 实际动了几个格子(0 = 没什么可整理的,调用方可以据此决定要不要给反馈)
	 */
	public static int sort(final ItemStack backpack, final int unlockedSlots) {
		// ⚠️ 夹一下:unlockedSlots 是组件里读出来的,理论上不会越界,
		// 但越界的话下面的循环会写出界(组件那边直接抛),不如在这里挡住。
		final int limit = Math.min(unlockedSlots, GrowthRules.TOTAL_SLOTS);

		if (limit <= 0) {
			return 0;      // 一格都没解锁 —— 没什么可整理的
		}

		// ⚠️ 用一个数组接返回值 —— lambda 里改不了外面的局部变量(同 BackpackStorage.consume)
		final int[] moved = {0};

		BackpackStorage.update(backpack, items -> moved[0] = sortInPlace(items, limit));

		return moved[0];
	}

	/**
	 * 在给定的列表上做"收集 → 排序 → 填回",返回动了几格。
	 *
	 * <p>⚠️ **只碰 `[0, limit)`**,`limit` 之后的格子原样不动。
	 */
	private static int sortInPlace(final NonNullList<ItemStack> items, final int limit) {
		// ---- ① 收集 + 合并 ----
		//
		// ⚠️ 边收边清:先把这一格设成 EMPTY,后面统一填回。
		// 这样"多余格子置空"就【自动】成立了 —— 不用再单独扫一遍去清尾巴。
		final List<ItemStack> collected = new ArrayList<>();

		for (int i = 0; i < limit; i++) {
			final ItemStack stack = items.get(i);

			if (stack.isEmpty()) {
				continue;
			}

			// ⚠️ copy() —— 不 copy 的话我们拿到的是列表里那个实例,
			// 而列表马上要被改(下面 set(i, EMPTY)),拿着旧引用会出怪事。
			mergeInto(collected, stack.copy());
			items.set(i, ItemStack.EMPTY);
		}

		if (collected.isEmpty()) {
			return 0;      // 全空 —— 没什么可做(而且此时已经全清干净了)
		}

		// ---- ② 排序 ----
		collected.sort(BY_REGISTRY_NAME);

		// ---- ③ 填回 ----
		//
		// ⚠️ 装不下时**不能丢**:退出循环,把剩下的塞回【列表尾部】。
		// 什么时候会装不下?—— `mergeInto` 会把每个栈压到 `maxStackSize`,
		// 所以"总件数"理论上不会变多。但**如果某个物品单格上限被改小过**
		// (比如以后做 F5、或者别 mod 改了),就可能溢出。**宁可留在包里也不要丢**。
		int slot = 0;
		int index = 0;

		for (; index < collected.size() && slot < limit; index++) {
			final ItemStack stack = collected.get(index);
			if (stack.isEmpty()) {
				continue;
			}
			items.set(slot++, stack);
		}

		// 溢出保护 —— 把没放下的塞进剩下的空格(理论上不会走到这里)
		for (; index < collected.size(); index++) {
			final ItemStack stack = collected.get(index);
			if (stack.isEmpty()) {
				continue;
			}
			for (int j = 0; j < limit; j++) {
				if (items.get(j).isEmpty()) {
					items.set(j, stack);
					break;
				}
			}
		}

		return slot;
	}

	/**
	 * 把一个栈并进收集列表 —— **能并就并,并不下就新开一格**。
	 *
	 * <p>⚠️ 合并的判据是 {@link ItemStack#isSameItemSameComponents} ——
	 * **不是** {@code getItem() == getItem()}。
	 * 两把附魔不同的剑是**不同**的东西,不能叠在一起
	 * (旅行者的 {@code canMergeItems} 也是这个判据,见 `ContainerSorter:162-176`)。
	 */
	private static void mergeInto(final List<ItemStack> collected, final ItemStack incoming) {
		int remaining = incoming.getCount();

		// 先试着塞进已有的同类栈(从后往前 —— 让前面的"满栈"留在原位)
		for (int i = collected.size() - 1; i >= 0 && remaining > 0; i--) {
			final ItemStack existing = collected.get(i);

			if (!ItemStack.isSameItemSameComponents(existing, incoming)) {
				continue;
			}

			final int room = existing.getMaxStackSize() - existing.getCount();
			if (room <= 0) {
				continue;
			}

			final int put = Math.min(room, remaining);
			existing.grow(put);
			remaining -= put;
		}

		// 还有剩 ⇒ 按上限切成若干个新栈
		while (remaining > 0) {
			final int size = Math.min(remaining, incoming.getMaxStackSize());
			final ItemStack piece = incoming.copyWithCount(size);
			collected.add(piece);
			remaining -= size;
		}
	}
}

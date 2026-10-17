package com.ergu.monsterbackpack.charm;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemContainerContents;

import com.ergu.monsterbackpack.ErguMonsterBackpack;
import com.ergu.monsterbackpack.component.ModComponents;
import com.ergu.monsterbackpack.item.ModCharmItems;

/**
 * 挂饰的效果(见 DESIGN.md §八)。
 *
 * <h2>⚠️ 属性加成必须【运行时重算】,而且必须【事件驱动】</h2>
 * 属性修饰符是写在物品组件里的固定值,但挂饰是玩家**动态插拔**的 ——
 * 换了挂饰,加成就要变。所以:
 * <ul>
 *   <li><b>只在挂饰变化时重算一次</b>({@link #recalculate})</li>
 *   <li>⚠️ <b>绝不能每 tick 重算</b> —— 每 tick 写组件会让手持物品在第一人称隐形(坑 20)</li>
 * </ul>
 *
 * <h2>磁石为什么不在这里</h2>
 * 用户 2026-10-15 的拆分:「磁石」<b>只扩大吸取范围</b>,它<b>不是属性</b>,
 * 而是被 {@code MagnetCollector} 直接读的一个数字。
 * 而"进不进背包"是<b>背包自身的开关</b>({@link CharmComponents#AUTO_COLLECT}),与挂饰无关。
 */
public final class CharmEffects {

	/** 幸运符给多少幸运值。 */
	private static final double LUCK_AMOUNT = 2.0;

	/** 磁石扩大多少格吸取范围。 */
	public static final double MAGNET_RANGE_BONUS = 3.0;

	/** 默认(没有磁石时)的吸取范围 —— 大致就是原版玩家能捡到的距离。 */
	public static final double BASE_RANGE = 1.0;

	private CharmEffects() {
	}

	/** 背包里插着的挂饰(最多 2 个,可能有空的)。 */
	public static List<ItemStack> charmsOf(final ItemStack backpack) {
		final ItemContainerContents contents = backpack.get(ModComponents.CHARMS);

		if (contents == null) {
			return List.of();
		}

		final var list = net.minecraft.core.NonNullList.withSize(2, ItemStack.EMPTY);
		contents.copyInto(list);

		final List<ItemStack> result = new ArrayList<>(2);
		for (ItemStack charm : list) {
			if (!charm.isEmpty()) {
				result.add(charm);
			}
		}
		return result;
	}

	/** 插了磁石吗。 */
	public static boolean hasMagnet(final ItemStack backpack) {
		for (ItemStack charm : charmsOf(backpack)) {
			if (charm.is(ModCharmItems.MAGNET)) {
				return true;
			}
		}
		return false;
	}

	/** 当前的吸取范围(格)。磁石会把 {@link #BASE_RANGE} 撑大 {@link #MAGNET_RANGE_BONUS}。 */
	public static double collectRange(final ItemStack backpack) {
		return hasMagnet(backpack) ? BASE_RANGE + MAGNET_RANGE_BONUS : BASE_RANGE;
	}

	/**
	 * 重算背包提供的属性加成。
	 *
	 * <p>调用时机:<b>挂饰变化时</b>(插入 / 拔出 / 界面关闭时的兜底)。
	 * <b>不要每 tick 调</b>。
	 */
	public static void recalculate(final ItemStack backpack) {
		final ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();

		for (ItemStack charm : charmsOf(backpack)) {
			if (charm.is(ModCharmItems.LUCK_CHARM)) {
				builder.add(
					Attributes.LUCK,
					new AttributeModifier(
						ErguMonsterBackpack.id("luck_charm"),
						LUCK_AMOUNT,
						AttributeModifier.Operation.ADD_VALUE),
					// 背包穿在胸甲栏,但效果应当【总是生效】,所以用 ANY
					EquipmentSlotGroup.ANY);
			}
		}

		backpack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
	}
}

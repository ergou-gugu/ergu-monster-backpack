package com.ergu.monsterbackpack.item;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

import com.ergu.monsterbackpack.ErguMonsterBackpack;

/**
 * 挂饰物品(见 DESIGN.md §八)。
 *
 * <h2>它们就是最普通的 Item</h2>
 * 挂饰自己<b>不带任何效果组件</b> —— 效果是"插在背包上"时由
 * {@code CharmEffects} <b>读到它再作用到背包</b>的。
 *
 * <p>这样设计的好处:
 * <ul>
 *   <li>挂饰掉落在地上、放在箱子里时<b>不会有任何副作用</b></li>
 *   <li>加新挂饰 = <b>加一个物品 + 在 CharmEffects 里加一个分支</b>,不需要动别的</li>
 * </ul>
 *
 * <h2>暂时无合成(用户 2026-10-15 定)</h2>
 * 先走创造栏验证效果,"怎么获得"以后再说(可能合成、也可能战利品)。
 */
public final class ModCharmItems {

	/** 幸运符:{@code ergu_monster_backpack:luck_charm}。 */
	public static final ResourceKey<Item> LUCK_CHARM_KEY = ResourceKey.create(
		Registries.ITEM,
		ErguMonsterBackpack.id("luck_charm")
	);

	/** 磁石:{@code ergu_monster_backpack:magnet}。 */
	public static final ResourceKey<Item> MAGNET_KEY = ResourceKey.create(
		Registries.ITEM,
		ErguMonsterBackpack.id("magnet")
	);

	/** 幸运符 —— 提升幸运值(影响战利品品质)。 */
	public static final Item LUCK_CHARM = register(LUCK_CHARM_KEY);

	/**
	 * 磁石 —— <b>只扩大吸取范围</b>。
	 *
	 * <p>⚠️ "掉落物进不进背包"是<b>背包自身的开关</b>(技能树能力),
	 * 和这块磁石没关系(用户 2026-10-15 明确拆分)。
	 */
	public static final Item MAGNET = register(MAGNET_KEY);

	private static Item register(final ResourceKey<Item> key) {
		return Registry.register(
			BuiltInRegistries.ITEM,
			key,
			new Item(new Item.Properties()
				.setId(key)
				// 挂饰不叠加 —— 每个槽插一个
				.stacksTo(1))
		);
	}

	private ModCharmItems() {
	}

	/** 空方法,唯一作用是触发本类的静态初始化。 */
	public static void bootstrap() {
	}
}

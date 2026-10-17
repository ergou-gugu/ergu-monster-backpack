package com.ergu.monsterbackpack.charm;

import com.mojang.serialization.Codec;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

import com.ergu.monsterbackpack.ErguMonsterBackpack;

/**
 * 挂饰相关的组件(见 DESIGN.md §八)。
 */
public final class CharmComponents {

	/**
	 * 背包的<b>自动收纳开关</b> —— 掉落物要不要直接进背包。
	 *
	 * <h2>⚠️ 这不是挂饰的效果</h2>
	 * 用户 2026-10-15 明确拆分过:
	 * <ul>
	 *   <li><b>"进不进背包" = 背包自身的初始能力</b> —— 归技能树,并且在界面上给个开关</li>
	 *   <li><b>「磁石」挂饰只负责"扩大吸取范围"</b> —— 它不管进不进背包</li>
	 * </ul>
	 * 所以这个开关属于背包本体,和插了什么挂饰无关。
	 *
	 * <p>默认<b>开</b>(True)—— 收纳是这背包的本能。
	 */
	public static final DataComponentType<Boolean> AUTO_COLLECT = toggle("auto_collect");

	/** 布尔组件的统一工厂。 */
	private static DataComponentType<Boolean> toggle(final String name) {
		return Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			ErguMonsterBackpack.id(name),
			DataComponentType.<Boolean>builder()
				.persistent(Codec.BOOL)
				.networkSynchronized(ByteBufCodecs.BOOL)
				// ⚠️ 必须加,理由见 ModComponents 的类注释(坑 20)
				.ignoreSwapAnimation()
				.build()
		);
	}

	private CharmComponents() {
	}

	/** 空方法,唯一作用是触发本类的静态初始化。 */
	public static void bootstrap() {
	}
}

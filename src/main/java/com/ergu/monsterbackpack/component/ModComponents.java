package com.ergu.monsterbackpack.component;

import com.mojang.serialization.Codec;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

import com.ergu.monsterbackpack.ErguMonsterBackpack;

/**
 * 本 mod 的自定义数据组件。
 *
 * <h2>V0.1 只用两个</h2>
 * 见 {@code DESIGN.md} §四 与 §六:
 * <ul>
 *   <li>{@link #UNLOCKED_SLOTS} —— 已解锁格数(0 ~ 63)。<b>0 = 原型背包(未唤醒)</b>。</li>
 *   <li>{@link #UNLOCK_START} —— 当前这格「开始计时」的游戏时间戳;<b>-1 = 未开始</b>(等饱食度)。</li>
 * </ul>
 *
 * <p>后续会补 {@code satiety}(饱食度)与 {@code skills}(技能树),见 §四。
 *
 * <h2>为什么两个组件就够</h2>
 * 因为**一次只能解锁一格**,解锁顺序严格线性 ——
 * 「正在解锁的那一格」恒等于「已解锁格数」,所以不需要位掩码、不需要每格状态。
 * 其余的(阶段 / 外观 / 该画什么)全部**从格数推导**,不存。
 *
 * <h2>⚠️⚠️ 为什么每个组件都必须 {@code .ignoreSwapAnimation()}</h2>
 * 客户端判断"玩家是不是换了手持物品"用的是
 * {@code ItemStack.matchesIgnoringComponents(current, expected, DataComponentType::ignoreSwapAnimation)}
 * ({@code client/renderer/ItemInHandRenderer.java:609-613})——
 * <b>带这个标记的组件,它的变化会被忽略,不算"换了物品"</b>。
 *
 * <p>不加标记的后果很严重:组件一变,客户端就当成"换了手持物品",
 * {@code ItemInHandRenderer.tick()} 里的
 * {@code mainHandTargetHeight = this.mainHandItem != nextMainHand ? 0.0F : attackAnim³}
 * 就会走 {@code 0.0F} 分支,把 {@code mainHandHeight} 压到 0 ——
 * <b>物品被"放下"到画面外,第一人称完全看不见</b>,而 GUI / 物品栏里一切正常。
 * 详见工作区约定的坑 20。
 *
 * <p>另有配套措施:物品定义 JSON 里的 {@code "hand_animation_on_swap": false}
 * (本项目两个物品都已加上)。
 */
public final class ModComponents {

	/** 已解锁格数(0 ~ 63)。0 = 原型背包(未唤醒)。 */
	public static final DataComponentType<Integer> UNLOCKED_SLOTS = intComponent("unlocked_slots");

	/** 当前这格「开始计时」的游戏时间戳;-1 = 未开始(等饱食度)。 */
	public static final DataComponentType<Integer> UNLOCK_START = intComponent("unlock_start");

	/**
	 * 饱食度(0 ~ 20)。
	 *
	 * <p>它吃<b>食物</b>攒出来的 —— 见 DESIGN.md §六 的 F15。
	 * 驱动三条线:<b>格子生长</b>(生长要烧它)/ <b>它的胃 F16</b> / 吃饱给 buff。
	 *
	 * <p>⚠️ <b>只吃食物,不吃成长材料</b> —— 两套输入必须分得清(材料全部走技能树)。
	 */
	public static final DataComponentType<Integer> SATIETY = intComponent("satiety");

	/**
	 * 当前阶段(1 ~ 4)。
	 *
	 * <h2>⚠️ 为什么不从 {@code unlocked_slots} 算</h2>
	 * (2026-10-15 用户指出)
	 * "第 19 格 = 阶段 2"这种算法**把阶段绑死在格数上**,而
	 * <b>每行的格子数是随时会变的</b>(9 列 → 12 列 → 以后可能更多)。
	 * 列数一变,所有用格数算出来的阶段就全错。
	 *
	 * <p>所以阶段<b>独立存</b>,只在"刚好跨进阶段首行"那一刻写一次
	 * (见 {@code BackpackGrowth.tick})。
	 *
	 * <p>这样还有个好处:以后<b>突破仪式</b>做好了,只是<b>换个人来写这个组件</b>
	 * (仪式写、而不是生长逻辑写),读的地方一行都不用改。
	 */
	public static final DataComponentType<Integer> STAGE = intComponent("stage");

	/**
	 * 挂饰槽里的两个挂饰(见 DESIGN.md §八)。
	 *
	 * <p>容量固定 2,用 {@code ItemContainerContents} 装 —— 和背包内容同样的存法,
	 * 好处是<b>插上就跟着背包走</b>(物品形态 ⇄ 方块形态搬运时不用单独处理)。
	 *
	 * <p>⚠️ 槽<b>存在</b>不等于能用:槽 1 要阶段 2、槽 2 要阶段 3 才解锁
	 * (由 {@code unlocked_slots} 推导,见 {@code BackpackMenu})。
	 */
	public static final DataComponentType<net.minecraft.world.item.component.ItemContainerContents> CHARMS =
		Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			ErguMonsterBackpack.id("charms"),
			DataComponentType.<net.minecraft.world.item.component.ItemContainerContents>builder()
				.persistent(net.minecraft.world.item.component.ItemContainerContents.CODEC)
				.networkSynchronized(net.minecraft.world.item.component.ItemContainerContents.STREAM_CODEC)
				.ignoreSwapAnimation()
				.build()
		);

	/**
	 * 整数组件的统一工厂。
	 *
	 * <p>用 {@link Codec#INT} 而不是 {@code ExtraCodecs.NON_NEGATIVE_INT} ——
	 * 因为 {@code unlock_start} 要用 <b>-1</b> 表示"未开始"。
	 */
	private static DataComponentType<Integer> intComponent(final String name) {
		return Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			ErguMonsterBackpack.id(name),
			DataComponentType.<Integer>builder()
				.persistent(Codec.INT)
				.networkSynchronized(ByteBufCodecs.VAR_INT)
				// ⚠️⚠️ 必须加,理由见类注释
				.ignoreSwapAnimation()
				.build()
		);
	}

	private ModComponents() {
	}

	/** 空方法,唯一作用是触发本类的静态初始化;注册写在静态字段上。 */
	public static void bootstrap() {
	}
}

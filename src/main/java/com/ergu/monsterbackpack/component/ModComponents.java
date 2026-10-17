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
	 * 「胃」里正在消化的那一份(1 格,见 DESIGN.md §十二 F16)。
	 *
	 * <p>只放<b>粗铜 / 粗铁 / 粗金</b>,烧成锭之后<b>产物直接进背包</b> ——
	 * 所以这个组件里永远最多只有一样东西。
	 *
	 * <p>⚠️ 存在组件里而不是菜单的临时容器里,是为了<b>关掉界面它也继续消化</b>
	 * (和背包内容同样的道理,坑 19)。
	 */
	public static final DataComponentType<net.minecraft.world.item.component.ItemContainerContents> STOMACH =
		Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			ErguMonsterBackpack.id("stomach"),
			DataComponentType.<net.minecraft.world.item.component.ItemContainerContents>builder()
				.persistent(net.minecraft.world.item.component.ItemContainerContents.CODEC)
				.networkSynchronized(net.minecraft.world.item.component.ItemContainerContents.STREAM_CODEC)
				.ignoreSwapAnimation()
				.build()
		);

	/**
	 * 「胃」开始消化那一份东西的游戏时间戳;{@code -1} = 还没开始。
	 *
	 * <p>和 {@code unlock_start} 一个套路:<b>存起点,不存进度</b> ——
	 * 进度由"现在 - 起点"现算,少一个要同步的字段。
	 */
	public static final DataComponentType<Integer> STOMACH_START = intComponent("stomach_start");

	/**
	 * 🆕 「护主」的**保护冷却**起点(游戏时间戳);{@code -1} = 没在冷却。
	 *
	 * <h2>⚠️ 它存的是"什么时候触发过",不是"还剩多久"</h2>
	 * 和 {@link #STOMACH_START} 一个套路 —— **存起点,不存剩余量**。
	 * "现在还在不在冷却"由 `now - start < 冷却时长` 现算。
	 *
	 * <p>好处:少一个每 tick 要改的字段(⚠️ 每 tick 写组件会让手持物品隐形,见坑 20)。
	 *
	 * <h2>⚠️ 为什么放在【背包组件】上,而不是玩家身上</h2>
	 * 「护主」是**背包自身的能力**(技能树解锁的),所以冷却也该跟着**这个背包**走 ——
	 * 换一个背包就是"另一个它",冷却不该继承。
	 *
	 * <p>⚠️ **⚠️ 但这也意味着:玩家可以【换个背包】来绕过冷却。**
	 * 这是已知的、**当前接受**的漏洞 —— 因为:
	 * <ul>
	 *   <li>冷却是**每个背包各自 3 分钟**(DESIGN.md §十二 F1 定的就是"它缓一缓",不是"你不能死")</li>
	 *   <li>玩家要绕过它得**多养一个开了护主的背包** —— 成本远大于收益</li>
	 * </ul>
	 */
	public static final DataComponentType<Integer> GUARDIAN_COOLDOWN = intComponent("guardian_cooldown");

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
	 * 虚空的<b>样本槽白名单</b>(见 DESIGN.md §十二 F17)。
	 *
	 * <h2>玩法</h2>
	 * <pre>
	 * 玩家丢一个【圆石】进样本槽
	 * → 之后拾取到的所有圆石,进背包后立刻被销毁
	 * </pre>
	 * <b>零配置 UI</b>、所见即所得 —— 就是普通槽位,拖进去即可。
	 *
	 * <h2>⚠️ 为什么容量固定 {@link #VOID_FILTER_SIZE} 而不是按等级变</h2>
	 * 组件是持久化数据,<b>容量一变就得迁移存档</b>。
	 * 所以这里**一次性给足 27 格**,I 级只是<b>只暴露前 9 个</b> ——
	 * 升到 II 级时<span>不需要动组件</span>,只是界面多显示 18 格。
	 *
	 * <p>这和多页签"加列能扩容量"是同一个思路:**把可变的东西放在读取端,不放存储端**。
	 */
	public static final DataComponentType<net.minecraft.world.item.component.ItemContainerContents> VOID_FILTER =
		Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			ErguMonsterBackpack.id("void_filter"),
			DataComponentType.<net.minecraft.world.item.component.ItemContainerContents>builder()
				.persistent(net.minecraft.world.item.component.ItemContainerContents.CODEC)
				.networkSynchronized(net.minecraft.world.item.component.ItemContainerContents.STREAM_CODEC)
				.ignoreSwapAnimation()
				.build()
		);

	/**
	 * 样本槽的物理容量。<b>= 25,正好是 II 级的 5×5</b>。
	 *
	 * <p>⚠️ 一次给足,等级只改<b>解锁几个 + 排成几列</b> ——
	 * 容量一变就得迁移存档,所以物理容量按最高等级定。
	 */
	public static final int VOID_FILTER_SIZE = 25;

	/** 虚空 I 级:3×3 = 9 格(和精妙普通虚空 / 旅行者虚空的默认值一致)。 */
	public static final int VOID_FILTER_TIER1 = 9;

	/** 虚空 II 级:5×5 = 25 格。 */
	public static final int VOID_FILTER_TIER2 = 25;

	/** I 级的列数。 */
	public static final int VOID_COLS_TIER1 = 3;

	/** II 级的列数。 */
	public static final int VOID_COLS_TIER2 = 5;

	/** 虚空开关(关掉之后样本槽留着,但不再销毁任何东西)。 */
	public static final DataComponentType<Boolean> VOID_ENABLED = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		ErguMonsterBackpack.id("void_enabled"),
		DataComponentType.<Boolean>builder()
			.persistent(Codec.BOOL)
			.networkSynchronized(ByteBufCodecs.BOOL)
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

	/**
	 * 🆕 技能树各节点的等级 —— **键 = 节点 id**(如 {@code "voiding_1"}),**值 = 等级**。
	 *
	 * <p>缺省(没有这个组件 / 表里没有这个键)= <b>未解锁</b>。
	 * 节点 id 与完整清单见 {@code skill/SkillNodes} 与 {@code DESIGN.md} §六。
	 *
	 * <h2>⚠️ 为什么是 {@code Map} 而不是 {@code List}</h2>
	 * 用索引的话,**往树里插一个新节点就会让它后面所有节点的等级串位** ——
	 * 而技能树必然会增删节点(超大堆叠还没做完,终局能力也待定)。
	 * 用 id 当键就完全没有这个问题。
	 *
	 * <h2>⚠️ 两个 Codec 的细节</h2>
	 * <ul>
	 *   <li>{@code persistent} 用 {@link Codec#unboundedMap} —— 存档里是正常的
	 *       {@code {"voiding_1": 1}} 形式,人也能读。</li>
	 *   <li>{@code networkSynchronized} 用 {@link ByteBufCodecs#map} ——
	 *       ⚠️ <b>必须给 maxSize</b>:它内部是 {@code writeCount(size, maxSize)},
	 *       不设上限就是 {@code Integer.MAX_VALUE},等于让对端随便声称多大。
	 *       我们用 {@link #MAX_SKILL_NODES}(节点总数 + 余量),正常永远够。</li>
	 * </ul>
	 *
	 * <p>⚠️ 同样必须 {@code .ignoreSwapAnimation()} —— 理由见类注释(坑 20)。
	 */
	/**
	 * {@link #SKILLS} 的网络同步上限。
	 *
	 * <p>⚠️ 它<b>不是</b>"最多能有多少节点"的业务约束,只是<b>防对端撒谎的哨兵</b> ——
	 * 正常存档里键数远小于它(键只在真正解锁时才出现)。
	 *
	 * <p>⚠️ 声明在 {@link #SKILLS} <b>之前</b> —— 虽然它是编译期常量、会内联,
	 * 但"用到的常量写在前面"读起来才是对的。
	 */
	public static final int MAX_SKILL_NODES = 64;

	public static final DataComponentType<java.util.Map<String, Integer>> SKILLS = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		ErguMonsterBackpack.id("skills"),
		DataComponentType.<java.util.Map<String, Integer>>builder()
			.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT))
			.networkSynchronized(ByteBufCodecs.map(
				java.util.HashMap::new,
				ByteBufCodecs.STRING_UTF8,
				ByteBufCodecs.VAR_INT,
				MAX_SKILL_NODES))
			.ignoreSwapAnimation()
			.build()
	);

	/** 空方法,唯一作用是触发本类的静态初始化;注册写在静态字段上。 */
	public static void bootstrap() {
	}
}

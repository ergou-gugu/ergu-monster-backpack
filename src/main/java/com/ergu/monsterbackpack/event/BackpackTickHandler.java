package com.ergu.monsterbackpack.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import com.ergu.monsterbackpack.component.ModComponents;
import com.ergu.monsterbackpack.growth.BackpackGrowth;
import com.ergu.monsterbackpack.growth.GrowthRules;
import com.ergu.monsterbackpack.item.ModItems;
import com.ergu.monsterbackpack.menu.BackpackMenu;

/**
 * 背包的成长计时(<b>服务端</b>)。
 *
 * <h2>为什么每 5 tick 而不是每 tick</h2>
 * <b>⚠️ 绝不能每 tick 写物品组件</b> —— 每次库存同步都会给客户端一个全新的 {@code ItemStack} 实例,
 * 而 {@code ItemInHandRenderer} 用<b>对象身份</b>判断"玩家是不是换了手持物品",
 * 每 tick 写会让 {@code mainHandHeight} 恒为 0 ⇒ <b>手持物品在第一人称里彻底看不见</b>(坑 20)。
 *
 * <p>本项目有三重防护:
 * <ol>
 *   <li>检查间隔 <b>5 tick</b>(不是每 tick)</li>
 *   <li><b>只在状态真的变了才写</b> —— 开始计时写一次,长开一格写一次</li>
 *   <li>物品 JSON 有 {@code "hand_animation_on_swap": false};
 *       组件都带 {@code .ignoreSwapAnimation()}</li>
 * </ol>
 *
 * <h2>为什么不需要"存进度"</h2>
 * 只需要两个组件(见 {@link ModComponents}):
 * {@code unlocked_slots}(已长开几格)+ {@code unlock_start}(这一格从哪 tick 开始算)。
 * 因为<b>一次只长一格</b>,"正在长的那一格"恒等于"已长开的格数"。
 */
public final class BackpackTickHandler {

	/** 隔多少 tick 检查一次。 */
	private static final int CHECK_INTERVAL = 5;

	private static int counter = 0;

	private BackpackTickHandler() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (++counter < CHECK_INTERVAL) {
				return;
			}
			counter = 0;

			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tickPlayer(player);
			}
		});
	}

	/** V0.1 只检查【玩家身上】的背包(物品栏 + 胸甲 + 主手)。方块形态那一批再补方块实体上的。 */
	private static void tickPlayer(final ServerPlayer player) {
		tickStack(player, player.getMainHandItem());
		tickStack(player, player.getItemBySlot(EquipmentSlot.CHEST));

		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			tickStack(player, player.getInventory().getItem(i));
		}
	}

	private static void tickStack(final ServerPlayer player, final ItemStack stack) {
		if (stack.getItem() != ModItems.MONSTER_BACKPACK) {
			return;
		}

		// 规则本体在 BackpackGrowth —— 方块形态也用同一份(见 MonsterBackpackBlockEntity)
		BackpackGrowth.tick(stack, player.level().getGameTime());

		// ⚠️⚠️ 这里【曾经】调 BackpackGrowth.purgeVoid(清掉背包里已在白名单上的东西),
		// 2026-10-15 用户明确要求【去掉】:
		//
		//   原话:"我要的虚空升级是【捡起来的】被销毁,背包里的东西不应该被同步销毁"
		//
		// ⇒ 虚空只作用于"**新进入背包的物品**",对**已经躺在背包里的**一律不碰。
		// 这是参考 mod 里的两种做法之一(旅行者背包就是这种;精妙背包会连已存的也清掉),
		// **用户要的是前者**。
		//
		// ⚠️ 这个选择有个很好的副作用:玩家手动把东西放进背包【不会被吃掉】——
		// "我把它放进去"永远是一个安全动作,只有"地上捡的/磁力吸的"才会被销毁。
		//
		// ⚠️ 判定点现在只剩【一处】:MagnetCollector 在 tryInsert 之前问 VoidRules.shouldVoid。
		// 以后如果加了别的"自动拾取"入口(漏斗、管道),要在【那些入口】各自补一次 ——
		// 不要退回到"每 tick 扫全包"那种做法。

		// 🆕 磁力吸取:掉落物进不进背包(开关)+ 吸取范围(磁石撑大)
		com.ergu.monsterbackpack.charm.MagnetCollector.tick(player, stack);

		// ⚠️⚠️ 兜底:只要这个背包正开着,就【无条件】让界面的容器下一帧重读一次。
		//
		// <h3>为什么不"谁改组件谁负责失效"</h3>
		// 我第一版就是这么写的,结果一查发现有几处会绕过容器直接改 CONTAINER 组件:
		// <pre>
		//   StomachSmelter           砂囊产物进背包
		//   BackpackStorage.consume  技能树扣材料
		//   (曾经还有 BackpackGrowth.purgeVoid —— 已于 2026-10-15 按用户要求移除)
		// </pre>
		// 修一个、漏一个,以后写新功能还会漏 —— <b>"记得调用"是不可靠的约束</b>
		// (这正是坑 32 的教训:能靠结构就别靠自觉)。
		//
		// <p>所以改成在这里无条件失效:<b>覆盖现在和将来所有"绕过容器改组件"的路径</b>。
		//
		// <h3>代价</h3>
		// 每 5 tick 重读一次 84 格 = 4 次/秒 × 84 = 约 340 次读/秒,
		// 而且只在<b>界面开着</b>的时候。可以接受 —— 换掉一整类"数据对但界面不刷新"的 bug。
		if (player.containerMenu instanceof BackpackMenu menu) {
			menu.invalidateBackpackCache();
		}
	}
}

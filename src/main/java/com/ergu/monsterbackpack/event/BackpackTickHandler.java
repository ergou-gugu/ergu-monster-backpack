package com.ergu.monsterbackpack.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import com.ergu.monsterbackpack.component.ModComponents;
import com.ergu.monsterbackpack.growth.BackpackGrowth;
import com.ergu.monsterbackpack.growth.GrowthRules;
import com.ergu.monsterbackpack.item.ModItems;

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
		// 规则本体在 BackpackGrowth —— 方块形态也用同一份(见 MonsterBackpackBlockEntity)
		BackpackGrowth.tick(stack, player.level().getGameTime());
	}
}

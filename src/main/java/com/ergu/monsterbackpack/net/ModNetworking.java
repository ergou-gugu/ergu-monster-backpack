package com.ergu.monsterbackpack.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.PlayerEnderChestContainer;

import com.ergu.monsterbackpack.item.ModItems;
import com.ergu.monsterbackpack.menu.BackpackMenu;
import com.ergu.monsterbackpack.menu.BackpackMenuProvider;
import com.ergu.monsterbackpack.menu.BackpackOpenData;

/**
 * 网络注册(<b>双端</b>都要调 —— 放 {@code onInitialize()} 里正好满足,
 * 因为发送方要编码、接收方要解码)。
 *
 * <h2>26.2 的写法</h2>
 * <ul>
 *   <li>包类型注册用 <b>{@code PayloadTypeRegistry.serverboundPlay()}</b>
 *       (旧名 {@code playC2S()} 已不存在)。</li>
 *   <li>接收器用 {@code ServerPlayNetworking.registerGlobalReceiver(Type, (payload, ctx) -> ...)},
 *       {@code ctx.player()} 取玩家。</li>
 * </ul>
 *
 * <p><b>⚠️ 客户端不可信</b>:客户端已经拦过一次条件,但客户端说了不算 ——
 * 服务端必须自己再找一遍背包,找不到就直接返回。
 */
public final class ModNetworking {

	/** 找不到背包时的哨兵值。 */
	private static final int NO_BACKPACK = Integer.MIN_VALUE;

	private ModNetworking() {
	}

	/** 在 {@code ModInitializer.onInitialize()} 里调用。 */
	public static void registerCommon() {
		PayloadTypeRegistry.serverboundPlay().register(
			OpenBackpackPayload.TYPE,
			OpenBackpackPayload.STREAM_CODEC
		);

		ServerPlayNetworking.registerGlobalReceiver(OpenBackpackPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();

			int source = findBackpack(player);
			if (source == NO_BACKPACK) {
				return;
			}

			player.openMenu(new BackpackMenuProvider(BackpackOpenData.ofSource(source)));
		});

		// ---- 切换侧签 ----
		PayloadTypeRegistry.serverboundPlay().register(
			SelectTabPayload.TYPE,
			SelectTabPayload.STREAM_CODEC
		);

		ServerPlayNetworking.registerGlobalReceiver(SelectTabPayload.TYPE, (payload, context) -> {
			// 只有"当前正开着背包界面"时才认这个包 ——
			// 否则玩家可以用它去改一个没打开的菜单的状态。
			if (context.player().containerMenu instanceof BackpackMenu menu) {
				menu.setTab(payload.tab());
			}
		});
	}

	/**
	 * 按优先级找一个背包,返回它的位置编码(见 {@link BackpackOpenData})。
	 *
	 * <p>优先级:<b>手上 → 胸甲 → 物品栏第一个</b>(与 DESIGN.md §七 的打开优先级一致)。
	 *
	 * <p>⚠️ 手上那个返回的是<b>选中的槽位索引</b>,不是 {@code SOURCE_MAIN_HAND} ——
	 * 界面开着时玩家能按数字键切换,只有"具体格子"才是稳定的。
	 */
	private static int findBackpack(final ServerPlayer player) {
		if (player.getMainHandItem().getItem() == ModItems.MONSTER_BACKPACK) {
			return player.getInventory().getSelectedSlot();
		}

		if (player.getItemBySlot(EquipmentSlot.CHEST).getItem() == ModItems.MONSTER_BACKPACK) {
			return BackpackOpenData.SOURCE_CHEST;
		}

		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).getItem() == ModItems.MONSTER_BACKPACK) {
				return i;
			}
		}

		return NO_BACKPACK;
	}
}

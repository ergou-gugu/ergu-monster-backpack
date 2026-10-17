package com.ergu.monsterbackpack.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;

import com.ergu.monsterbackpack.component.ModComponents;
import com.ergu.monsterbackpack.item.ModItems;
import com.ergu.monsterbackpack.menu.BackpackMenu;
import com.ergu.monsterbackpack.menu.BackpackMenuProvider;
import com.ergu.monsterbackpack.menu.BackpackOpenData;
import com.ergu.monsterbackpack.skill.SkillNodes;
import com.ergu.monsterbackpack.skill.SkillUnlock;
import com.ergu.monsterbackpack.storage.BackpackSorter;

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

		// ---- 切换自动收纳开关 ----
		PayloadTypeRegistry.serverboundPlay().register(
			ToggleAutoCollectPayload.TYPE,
			ToggleAutoCollectPayload.STREAM_CODEC
		);

		ServerPlayNetworking.registerGlobalReceiver(ToggleAutoCollectPayload.TYPE, (payload, context) -> {
			// 同样只认"当前开着的那个背包界面";取反由服务端决定(客户端说了不算)。
			if (context.player().containerMenu instanceof BackpackMenu menu) {
				menu.toggleAutoCollect();
			}
		});

		// ---- 🆕 切换虚空开关(F17) ----
		PayloadTypeRegistry.serverboundPlay().register(
			ToggleVoidPayload.TYPE,
			ToggleVoidPayload.STREAM_CODEC
		);

		ServerPlayNetworking.registerGlobalReceiver(ToggleVoidPayload.TYPE, (payload, context) -> {
			if (context.player().containerMenu instanceof BackpackMenu menu) {
				menu.toggleVoid();
			}
		});

		// ---- 🆕 点一下虚空样本格(登记主手物品 / 空手清除) ----
		PayloadTypeRegistry.serverboundPlay().register(
			SetVoidSamplePayload.TYPE,
			SetVoidSamplePayload.STREAM_CODEC
		);

		ServerPlayNetworking.registerGlobalReceiver(SetVoidSamplePayload.TYPE, (payload, context) -> {
			// ⚠️ 客户端只说"点了第几格",登记【什么】由服务端自己读 `getCarried()` 决定 ——
			// 客户端完全可以谎报"我手里有下界之星"。
			// (⚠️ 读的是【鼠标光标】而不是主手 —— 见 BackpackMenu.setVoidSample 的注释)
			if (context.player().containerMenu instanceof BackpackMenu menu) {
				menu.setVoidSample(payload.index());
			}
		});

		// ---- 🆕 切换主页签(背包 / 成长) ----
		PayloadTypeRegistry.serverboundPlay().register(
			SelectPagePayload.TYPE,
			SelectPagePayload.STREAM_CODEC
		);

		ServerPlayNetworking.registerGlobalReceiver(SelectPagePayload.TYPE, (payload, context) -> {
			if (context.player().containerMenu instanceof BackpackMenu menu) {
				menu.setPage(payload.page());
			}
		});

		// ---- 🆕 点技能树节点 → 请求解锁 ----
		PayloadTypeRegistry.serverboundPlay().register(
			UnlockSkillPayload.TYPE,
			UnlockSkillPayload.STREAM_CODEC
		);

		ServerPlayNetworking.registerGlobalReceiver(UnlockSkillPayload.TYPE, (payload, context) -> {
			if (context.player().containerMenu instanceof BackpackMenu menu) {
				// ⚠️⚠️ 包里那个 id 是【不可信输入】—— 查表由菜单内部做,
				// 而且阶段 / 材料 / 扣除全部服务端自己重算(见 SkillUnlock.unlock)。
				// 客户端在这里【只有一个发言权】:"我想解锁这个 id"。
				menu.tryUnlockSkill(payload.nodeId());
			}
		});
		// ---- 🆕 一键整理(F4) ----
		PayloadTypeRegistry.serverboundPlay().register(
			SortingPayload.TYPE,
			SortingPayload.STREAM_CODEC
		);

		ServerPlayNetworking.registerGlobalReceiver(SortingPayload.TYPE, (payload, context) -> {
			final ServerPlayer player = context.player();

			// ① 界面开着 ⇒ 交给菜单 —— 它知道"现在是物品形态还是方块形态",
			//    而且整理完之后能用 ContainerData 立刻把新格数推给客户端。
			if (player.containerMenu instanceof BackpackMenu menu) {
				menu.sortBackpack();
				return;
			}

			// ② 界面关着也允许整理(这是刻意的 —— 见 SortingPayload 的注释)。
			//    ⚠️ 此时只能处理【物品形态】:方块形态的背包不在玩家身上,
			//    而"附近哪个方块是背包"没有可靠的判定方式(可能有好几个)。
			final int source = findBackpack(player);
			if (source == NO_BACKPACK) {
				return;
			}

			final ItemStack backpack = BackpackMenu.resolveFromPlayer(player, source);
			if (backpack.isEmpty()) {
				return;
			}

			// ⚠️ 技能树没解锁就不认(客户端已经拦过一次,服务端必须自己再拦)。
			if (!SkillUnlock.isUnlocked(backpack, SkillNodes.SORTING)) {
				return;
			}

			BackpackSorter.sort(backpack,
				backpack.getOrDefault(ModComponents.UNLOCKED_SLOTS, 0));
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

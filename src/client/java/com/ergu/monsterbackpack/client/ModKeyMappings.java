package com.ergu.monsterbackpack.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

import org.lwjgl.glfw.GLFW;

import com.ergu.monsterbackpack.item.ModItems;
import com.ergu.monsterbackpack.net.OpenBackpackPayload;

/**
 * 快捷键注册(<b>纯客户端</b>,放 {@code src/client})。
 *
 * <p>类名是 <b>{@code KeyMappingHelper}</b>(旧名 {@code KeyBindingHelper} 已不存在 ——
 * 跟随原版把 Binding 改成了 Mapping)。第 3 个参数直接传 GLFW 键码。
 *
 * <h2>为什么选 B</h2>
 * 原版对 {@code B} 的绑定仅在<b>调试分类</b>({@code key.debug.showHitboxes}),
 * 正常游戏要按住 F3 组合才触发,<b>单按空闲</b>。
 * 若实测有冲突,改 {@code Z}(见 DESIGN.md §七)。
 */
public final class ModKeyMappings {

	public static final KeyMapping OPEN_BACKPACK = KeyMappingHelper.registerKeyMapping(
		new KeyMapping(
			"key.ergu_monster_backpack.open_backpack",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_B,
			KeyMapping.Category.MISC
		)
	);

	private ModKeyMappings() {
	}

	/** 在 {@code ClientModInitializer.onInitializeClient()} 里调用。 */
	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_BACKPACK.consumeClick()) {
				if (client.player == null) {
					continue;
				}

				// 身上一个背包都没有就别发包(服务端还会再校验一次)
				if (hasBackpack(client.player)) {
					ClientPlayNetworking.send(new OpenBackpackPayload());
				}
			}
		});
	}

	/** 身上有没有背包 —— 手上、胸甲、物品栏都算。 */
	public static boolean hasBackpack(final Player player) {
		if (player.getMainHandItem().getItem() == ModItems.MONSTER_BACKPACK) {
			return true;
		}
		if (player.getItemBySlot(EquipmentSlot.CHEST).getItem() == ModItems.MONSTER_BACKPACK) {
			return true;
		}
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).getItem() == ModItems.MONSTER_BACKPACK) {
				return true;
			}
		}
		return false;
	}
}

package com.ergu.monsterbackpack.menu;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/**
 * 开背包界面用的 provider。
 *
 * <p>⚠️ Fabric 的硬性约束:用 {@code ExtendedMenuType} 注册的容器
 * <b>必须</b>用 {@link ExtendedMenuProvider} 开;非扩展容器<b>不能</b>用它开。
 * 混用会在<b>打开界面时</b>抛 {@code IllegalArgumentException}(不是编译期报错)。
 */
public class BackpackMenuProvider implements ExtendedMenuProvider<BackpackOpenData> {

	/** 背包在哪(玩家身上 或 世界里的方块)。 */
	private final BackpackOpenData data;

	public BackpackMenuProvider(final BackpackOpenData data) {
		this.data = data;
	}

	@Override
	public BackpackOpenData getScreenOpeningData(final ServerPlayer player) {
		return this.data;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("container.ergu_monster_backpack.backpack");
	}

	@Override
	public BackpackMenu createMenu(final int containerId, final Inventory inventory, final Player player) {
		return new BackpackMenu(containerId, inventory, this.data);
	}
}

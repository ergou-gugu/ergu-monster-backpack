package com.ergu.monsterbackpack.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.ergu.monsterbackpack.ErguMonsterBackpack;

/**
 * 客户端 → 服务端:切换背包界面上打开的那个<b>侧签</b>。
 *
 * <h2>为什么侧签要同步到服务端</h2>
 * 侧签决定的是"哪些功能槽是活的" —— 而槽位的可用性由
 * {@code Slot.isActive()} / {@code mayPlace(...)} 判定,
 * 这些判定<b>服务端也要做一遍</b>。
 * 如果只在客户端切,两边对"哪些槽能用"的理解就会不一致:
 * 玩家能往一个服务端认为是关闭的槽里放东西。
 *
 * @param tab 侧签编号,见 {@code BackpackMenu} 的 {@code TAB_*} 常量;{@code -1} = 全关
 */
public record SelectTabPayload(int tab) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<SelectTabPayload> TYPE =
		new CustomPacketPayload.Type<>(ErguMonsterBackpack.id("select_tab"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SelectTabPayload> STREAM_CODEC =
		StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SelectTabPayload::tab,
			SelectTabPayload::new
		);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

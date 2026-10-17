package com.ergu.monsterbackpack.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.ergu.monsterbackpack.ErguMonsterBackpack;

/**
 * 客户端 → 服务端:切换背包的<b>自动收纳开关</b>。
 *
 * <h2>为什么要发包</h2>
 * 开关是<b>物品组件</b>({@code auto_collect}),写它必须在服务端做 ——
 * 否则两端数据不一致,客户端改了也会被下一次同步覆盖回去。
 *
 * <p>无字段:服务端收到后把当前值取反(客户端说了不算)。
 */
public record ToggleAutoCollectPayload() implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<ToggleAutoCollectPayload> TYPE =
		new CustomPacketPayload.Type<>(ErguMonsterBackpack.id("toggle_auto_collect"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ToggleAutoCollectPayload> STREAM_CODEC =
		StreamCodec.unit(new ToggleAutoCollectPayload());

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

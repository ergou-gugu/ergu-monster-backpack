package com.ergu.monsterbackpack.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.ergu.monsterbackpack.ErguMonsterBackpack;

/**
 * 客户端 → 服务端:请求打开背包界面。
 *
 * <p><b>无字段</b> —— 用 {@link StreamCodec#unit} 打包,不占一个字节。
 *
 * <p>为什么不让客户端说"打开哪一个":<b>客户端不可信</b>。
 * 服务端收到后自己按优先级找背包(主手 → 胸甲 → 物品栏),找不到就当没收到。
 */
public record OpenBackpackPayload() implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<OpenBackpackPayload> TYPE =
		new CustomPacketPayload.Type<>(ErguMonsterBackpack.id("open_backpack"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OpenBackpackPayload> STREAM_CODEC =
		StreamCodec.unit(new OpenBackpackPayload());

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

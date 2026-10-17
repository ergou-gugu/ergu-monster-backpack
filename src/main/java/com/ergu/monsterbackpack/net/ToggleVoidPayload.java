package com.ergu.monsterbackpack.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.ergu.monsterbackpack.ErguMonsterBackpack;

/**
 * 客户端 → 服务端:切换背包的<b>虚空开关</b>(F17)。
 *
 * <h2>为什么要发包</h2>
 * 开关是<b>物品组件</b>({@code void_enabled}),写它必须在服务端做 ——
 * 否则两端数据不一致,客户端改了也会被下一次同步覆盖回去。
 *
 * <p>无字段:服务端收到后把当前值取反(客户端说了不算)。
 *
 * <p>⚠️ <b>注意开关和样本槽是两件事</b>:
 * 关掉开关<b>不会清空样本槽</b> —— 玩家配好的白名单留着,
 * 下次打开接着用。这符合"开关"的直觉(而不是"开关=重置")。
 */
public record ToggleVoidPayload() implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<ToggleVoidPayload> TYPE =
		new CustomPacketPayload.Type<>(ErguMonsterBackpack.id("toggle_void"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ToggleVoidPayload> STREAM_CODEC =
		StreamCodec.unit(new ToggleVoidPayload());

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

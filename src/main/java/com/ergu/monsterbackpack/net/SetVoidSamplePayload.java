package com.ergu.monsterbackpack.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.ergu.monsterbackpack.ErguMonsterBackpack;

/**
 * 客户端 → 服务端:<b>点一下虚空样本格</b>(见 DESIGN.md §十二 F17)。
 *
 * <h2>为什么需要这个包(而不是让玩家直接拖)</h2>
 * 用户 2026-10-15 原话:"**我希望虚空过滤不需要放一个实际东西进去**"。
 *
 * <p>拖放要求"手上正好有圆石,而且要占一格" —— 想过滤一个<b>还没捡到</b>的东西就配不了。
 * 改成"点一下登记"之后,<b>主手那份不被消耗</b>,只是读它的种类。
 *
 * <h2>语义</h2>
 * <pre>
 *   主手有东西 → 把那一格的样本登记成【主手物品的种类】
 *   主手空着   → 清掉那一格的登记
 * </pre>
 *
 * <p>⚠️ 客户端只说"我点了第几格",<b>登记什么由服务端读主手决定</b>
 * (客户端不可信 —— 它完全可以谎报"我手里有下界之星")。
 *
 * @param index 第几格样本(容器内索引,0 ~ {@code VOID_FILTER_SIZE - 1})
 */
public record SetVoidSamplePayload(int index) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<SetVoidSamplePayload> TYPE =
		new CustomPacketPayload.Type<>(ErguMonsterBackpack.id("set_void_sample"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SetVoidSamplePayload> STREAM_CODEC =
		StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SetVoidSamplePayload::index,
			SetVoidSamplePayload::new
		);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

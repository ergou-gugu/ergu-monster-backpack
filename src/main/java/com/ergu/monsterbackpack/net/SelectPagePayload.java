package com.ergu.monsterbackpack.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.ergu.monsterbackpack.ErguMonsterBackpack;

/**
 * 客户端 → 服务端:切换背包界面的**主页签**(背包 / 成长)。
 *
 * <h2>为什么主页签也要同步</h2>
 * 和 {@link SelectTabPayload 侧签} 是同一个理由,而且更硬 ——
 * 主页签决定的是<b>"84 个背包格在不在"</b>:
 *
 * <pre>
 *   只在客户端切 → 客户端把格子踢出屏幕,但服务端仍认为它们在原位
 *                ⇒ 玩家能 Shift + 左键"划"到一个看不见的格子里,东西凭空进背包
 * </pre>
 *
 * <p>另外 {@code BackpackMenu.layoutGridSlots()} 在<b>两端</b>都会被调到,
 * 而槽位坐标是"它可不可用"的唯一表达({@code SideTabSlot} 的注释里写了这个约定)——
 * 两端必须对"现在是哪一页"有同一个认知。
 *
 * @param page 页签编号,见 {@code BackpackMenu} 的 {@code PAGE_*} 常量
 */
public record SelectPagePayload(int page) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<SelectPagePayload> TYPE =
		new CustomPacketPayload.Type<>(ErguMonsterBackpack.id("select_page"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SelectPagePayload> STREAM_CODEC =
		StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SelectPagePayload::page,
			SelectPagePayload::new
		);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

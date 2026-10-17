package com.ergu.monsterbackpack.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.ergu.monsterbackpack.ErguMonsterBackpack;

/**
 * 客户端 → 服务端:**一键整理背包(F4)**。
 *
 * <h2>⚠️ 这个包不带任何数据 —— 故意的</h2>
 * "整理哪个包"由<b>服务端自己判断</b>(找玩家身上的背包),
 * 而不是客户端告诉它。这样:
 * <ul>
 *   <li>客户端没法指定"整理别人身上的某个背包"</li>
 *   <li>服务端只需要校验一次"你身上真的有背包吗"</li>
 * </ul>
 * ⇒ 和 {@code OpenBackpackPayload} 是同一个思路。
 *
 * <p>所以用 {@link StreamCodec#unit} 而不是 {@code composite} ——
 * 空记录不需要编解码任何字段。
 *
 * <h2>⚠️ 不会实时同步的坑</h2>
 * 整理是**一次性批量写组件**(整个 `CONTAINER` 重建一次)。
 * 服务端改完之后:
 * <ul>
 *   <li><b>界面开着</b> —— 靠每 5 tick 的 {@code invalidateBackpackCache()} 兜底
 *       (见 {@code BackpackTickHandler}),玩家下一帧就看到新排列</li>
 *   <li><b>界面关着</b> —— 反正没人在看,下次打开读到的就是新的</li>
 * </ul>
 */
public record SortingPayload() implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<SortingPayload> TYPE =
		new CustomPacketPayload.Type<>(ErguMonsterBackpack.id("sorting"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SortingPayload> STREAM_CODEC =
		StreamCodec.unit(new SortingPayload());

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

package com.ergu.monsterbackpack.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.ergu.monsterbackpack.ErguMonsterBackpack;

/**
 * 客户端 → 服务端:请求解锁一个技能树节点。
 *
 * <h2>⚠️⚠️ 包里只带 id,别的什么都不带</h2>
 * 这是个刻意的安全设计。客户端<b>只负责说"我想解锁这个"</b>,
 * 至于:
 * <ul>
 *   <li>这个节点存不存在</li>
 *   <li>阶段够不够</li>
 *   <li>材料够不够</li>
 *   <li>该扣多少</li>
 * </ul>
 * <b>全部由服务端自己重新算一遍</b>({@code SkillUnlock.unlock} 内部会再跑一次 {@code check})。
 *
 * <p>反过来,如果包能带"材料清单"或者"客户端算出来的状态",就等于把判定权交给了客户端 ——
 * 那是个能被随便伪造的洞。
 *
 * @param nodeId 节点 id(见 {@code SkillNodes})。⚠️ <b>不可信输入</b> ——
 *               服务端必须走 {@code SkillNodes.byId(...)} 查表,查不到就当没收到
 */
public record UnlockSkillPayload(String nodeId) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<UnlockSkillPayload> TYPE =
		new CustomPacketPayload.Type<>(ErguMonsterBackpack.id("unlock_skill"));

	public static final StreamCodec<RegistryFriendlyByteBuf, UnlockSkillPayload> STREAM_CODEC =
		StreamCodec.composite(
			// ⚠️ 用 STRING_UTF8 —— 它自带长度前缀,不会像 readUtf 那样在超长时静默截断。
			// 节点 id 全是 ASCII 短串,但用错 codec 的代价是"对端能塞任意长度的字符串进来"。
			ByteBufCodecs.STRING_UTF8, UnlockSkillPayload::nodeId,
			UnlockSkillPayload::new
		);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

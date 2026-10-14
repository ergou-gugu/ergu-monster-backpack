package com.ergu.monsterbackpack.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 开界面时【服务端 → 客户端】传的数据:<b>背包在哪</b>。
 *
 * <h2>为什么传位置而不是背包本身</h2>
 * 传 {@code ItemStack} 的话客户端拿到的是<b>副本</b> —— 之后格数变了客户端也不知道。
 * 传"位置"则两端各自从自己那边取,拿到的是<b>会随同步更新的</b>那一份。
 *
 * <h2>两种宿主</h2>
 * <ul>
 *   <li><b>玩家身上</b>(物品形态)—— 靠原版库存同步</li>
 *   <li><b>世界里某个方块</b>(方块形态)—— ⚠️ 方块实体<b>不会自动同步内容</b>,
 *       所以格数另走菜单的 {@code ContainerData} 通道(见 {@link BackpackMenu})</li>
 * </ul>
 *
 * @param source 背包在哪,见下面的常量
 * @param pos    {@link #SOURCE_BLOCK} 时有效;其他情况传 {@link BlockPos#ZERO}
 */
public record BackpackOpenData(int source, BlockPos pos) {

	/** 胸甲栏。 */
	public static final int SOURCE_CHEST = -1;

	/** 主手。 */
	public static final int SOURCE_MAIN_HAND = -2;

	/** 世界里的一个背包方块({@code pos} 有效)。 */
	public static final int SOURCE_BLOCK = -3;

	/** 按一个"玩家身上的位置编码"构造(主手 / 胸甲 / 物品栏索引)。 */
	public static BackpackOpenData ofSource(final int source) {
		return new BackpackOpenData(source, BlockPos.ZERO);
	}

	public static final StreamCodec<RegistryFriendlyByteBuf, BackpackOpenData> STREAM_CODEC =
		StreamCodec.composite(
			ByteBufCodecs.VAR_INT, BackpackOpenData::source,
			BlockPos.STREAM_CODEC, BackpackOpenData::pos,
			BackpackOpenData::new
		);

	public static BackpackOpenData ofChest() {
		return new BackpackOpenData(SOURCE_CHEST, BlockPos.ZERO);
	}

	public static BackpackOpenData ofMainHand() {
		return new BackpackOpenData(SOURCE_MAIN_HAND, BlockPos.ZERO);
	}

	public static BackpackOpenData ofBlock(final BlockPos pos) {
		return new BackpackOpenData(SOURCE_BLOCK, pos);
	}

	/** 是不是"世界里的方块"。 */
	public boolean isBlock() {
		return this.source == SOURCE_BLOCK;
	}
}

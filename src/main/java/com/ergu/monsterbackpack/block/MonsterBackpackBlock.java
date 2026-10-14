package com.ergu.monsterbackpack.block;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

import com.ergu.monsterbackpack.block.entity.ModBlockEntities;
import com.ergu.monsterbackpack.block.entity.MonsterBackpackBlockEntity;
import com.ergu.monsterbackpack.item.ModItems;
import com.ergu.monsterbackpack.menu.BackpackMenuProvider;
import com.ergu.monsterbackpack.menu.BackpackOpenData;
import com.ergu.monsterbackpack.ritual.WakeRitual;

/**
 * 怪物背包的<b>方块形态</b>。
 *
 * <h2>放下 / 打开 / 破坏</h2>
 * <ul>
 *   <li><b>Shift + 右键</b>(手持背包)→ 放置(见 {@code MonsterBackpackItem.useOn})</li>
 *   <li><b>右键方块</b> → 打开界面(V0.1 还没做,下一批)</li>
 *   <li><b>破坏</b> → {@link #getDrops} 把方块实体里的背包物品<b>原样掉出来</b></li>
 * </ul>
 *
 * <h2>为什么内容不会丢</h2>
 * 因为方块实体里存的就是<b>那个背包物品本身</b>(见 {@link MonsterBackpackBlockEntity}),
 * 掉落时把它整个交出去就行 —— 不需要"把 84 个格子搬进掉落物"这种操作。
 */
public class MonsterBackpackBlock extends BaseEntityBlock {

	public static final MapCodec<MonsterBackpackBlock> CODEC =
		simpleCodec(MonsterBackpackBlock::new);

	public MonsterBackpackBlock(final Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	// ------------------------------------------------------------ 方块实体

	@Override
	public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
		return new MonsterBackpackBlockEntity(pos, state);
	}

	/**
	 * 方块形态的生长拍子。
	 *
	 * <p>{@code createTickerHelper} 会做一次类型安全检查,并对不上就返回 {@code null}。
	 * <b>只给服务端</b> —— 客户端不需要也不该跑生长。
	 */
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
		final Level level,
		final BlockState state,
		final BlockEntityType<T> type
	) {
		if (level.isClientSide()) {
			return null;
		}
		return createTickerHelper(type, ModBlockEntities.MONSTER_BACKPACK,
			MonsterBackpackBlockEntity::serverTick);
	}

	// ------------------------------------------------------------ 交互

	/**
	 * 空手右键方块 → 打开<b>它自己</b>的背包界面。
	 *
	 * <p>方块的格数与内容走菜单的 {@code ContainerData} 通道到客户端
	 * (方块实体不像玩家库存那样自动同步),见 {@link com.ergu.monsterbackpack.menu.BackpackMenu}。
	 *
	 * <p>⚠️ <b>原型态(0 格)打不开</b> —— 与拿在手上时的行为保持一致:
	 * 一个还没醒的背包,不管在哪都不该能打开。要先用仪式匕首唤醒它。
	 */
	@Override
	protected InteractionResult useWithoutItem(
		final BlockState state,
		final Level level,
		final BlockPos pos,
		final Player player,
		final BlockHitResult hitResult
	) {
		if (!(level.getBlockEntity(pos) instanceof MonsterBackpackBlockEntity backpackEntity)) {
			return InteractionResult.PASS;
		}

		if (WakeRitual.isDormant(backpackEntity.backpack())) {
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendSystemMessage(
					Component.translatable("ritual.ergu_monster_backpack.dormant"), true);
			}
			return InteractionResult.FAIL;
		}

		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
			serverPlayer.openMenu(new BackpackMenuProvider(BackpackOpenData.ofBlock(pos)));
		}
		return InteractionResult.SUCCESS;
	}

	/**
	 * 手持物品右键方块 —— <b>唤醒仪式的入口</b>。
	 *
	 * <p>主手拿「仪式匕首」右键<b>原型背包</b>(0 格的)时触发,见 {@link WakeRitual}。
	 * 其他情况交回原版处理(免得挡掉正常的放置等行为)。
	 */
	@Override
	protected InteractionResult useItemOn(
		final ItemStack stack,
		final BlockState state,
		final Level level,
		final BlockPos pos,
		final Player player,
		final InteractionHand hand,
		final BlockHitResult hitResult
	) {
		if (WakeRitual.isDagger(stack)
			&& level.getBlockEntity(pos) instanceof MonsterBackpackBlockEntity backpack) {
			return WakeRitual.tryWake(level, pos, player, backpack.backpack());
		}
		return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
	}

	// ------------------------------------------------------------ 掉落

	/**
	 * 掉落:<b>把方块实体里那个背包物品原样交出去</b>。
	 *
	 * <p>这样内容、已解锁格数、正在生长的计时<b>全都跟着走</b>,不需要任何搬运代码。
	 */
	@Override
	public List<ItemStack> getDrops(final BlockState state, final LootParams.Builder params) {
		final BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
		if (be instanceof MonsterBackpackBlockEntity backpack) {
			return List.of(backpack.backpack());
		}
		return List.of(new ItemStack(ModItems.MONSTER_BACKPACK));
	}
}

package com.ergu.monsterbackpack.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import com.ergu.monsterbackpack.block.ModBlocks;
import com.ergu.monsterbackpack.block.entity.MonsterBackpackBlockEntity;
import com.ergu.monsterbackpack.component.ModComponents;
import com.ergu.monsterbackpack.menu.BackpackMenuProvider;
import com.ergu.monsterbackpack.menu.BackpackOpenData;
import com.ergu.monsterbackpack.ritual.WakeRitual;

/**
 * 怪物背包(物品形态)。
 *
 * <h2>两个右键入口的分工(见 DESIGN.md §七)</h2>
 * <pre>
 *   右键空气        → 打开界面
 *   右键方块        → 打开界面
 *   Shift + 右键方块 → 放置成方块
 * </pre>
 *
 * <p>它 extends {@link BlockItem},所以"放置"用的是原版那一整套逻辑
 * (朝向、碰撞、可替换判定都不必自己写)。
 */
public class MonsterBackpackItem extends BlockItem {

	public MonsterBackpackItem(final Block block, final Properties properties) {
		super(block, properties);
	}

	/**
	 * 背包<b>不能放进任何容器</b> —— 潜影盒、收纳袋、箱子,以及另一个背包。
	 *
	 * <p>原版就靠这一个钩子管控全部这些路径
	 * (见 DESIGN.md 的"互斥"决定),所以不需要在每个容器类里各堵一次。
	 */
	@Override
	public boolean canFitInsideContainerItems() {
		return false;
	}

	/**
	 * 右键空气 → 打开界面。
	 *
	 * <p>⚠️ <b>原型背包(0 格)打不开</b> —— 它还没醒。
	 * 这也是"一个物品表达两种状态"的落点(见 DESIGN.md §六):
	 * <b>不额外做一个物品</b>,只靠 {@code unlocked_slots} 区分。
	 */
	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		if (WakeRitual.isDormant(player.getItemInHand(hand))) {
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendSystemMessage(
					Component.translatable("ritual.ergu_monster_backpack.dormant"), true);
			}
			return InteractionResult.FAIL;
		}

		openBackpack(level, player);
		return InteractionResult.SUCCESS;
	}

	/** 右键方块:Shift 是放置,否则是打开。 */
	@Override
	public InteractionResult useOn(final UseOnContext context) {
		final Player player = context.getPlayer();

		if (player != null && player.isSecondaryUseActive()) {
			// ---- Shift + 右键 = 放置 ----
			// 先复制一份:super.useOn 成功后手上那个会被扣掉,数据就没了
			final ItemStack source = context.getItemInHand().copy();

			final InteractionResult result = super.useOn(context);

			if (result.consumesAction()) {
				// 放置成功后,把背包数据(内容 / 格数 / 计时)搬进新的方块实体
				final BlockPos placed = context.getClickedPos().relative(context.getClickedFace());
				if (context.getLevel().getBlockEntity(placed) instanceof MonsterBackpackBlockEntity backpack) {
					backpack.setBackpack(source);
				}
			}

			return result;
		}

		// ---- 普通右键 = 打开界面 ----
		// ⚠️ 同样要挡住原型态 —— 否则"拿在手上打不开、对着地面却能开",行为不一致。
		if (WakeRitual.isDormant(context.getItemInHand())) {
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendSystemMessage(
					Component.translatable("ritual.ergu_monster_backpack.dormant"), true);
			}
			return InteractionResult.FAIL;
		}

		openBackpack(context.getLevel(), player);
		return InteractionResult.SUCCESS;
	}

	/**
	 * 只在服务端开容器(见 {@code BackpackMenuProvider} 的说明)。
	 *
	 * <p>⚠️ 传的是<b>具体槽位索引</b>,不是"主手"这个抽象位置 ——
	 * 因为玩家可以在界面开着的时候按数字键切槽,那样"主手"就指向别的东西了。
	 * 记成具体格子之后,界面始终读同一格,并且那一格会被锁住。
	 */
	private void openBackpack(final Level level, final Player player) {
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
			serverPlayer.openMenu(new BackpackMenuProvider(
				BackpackOpenData.ofSource(serverPlayer.getInventory().getSelectedSlot())));
		}
	}

	/** 供方块注册引用,免得两处写死。 */
	public static Block block() {
		return ModBlocks.MONSTER_BACKPACK;
	}
}

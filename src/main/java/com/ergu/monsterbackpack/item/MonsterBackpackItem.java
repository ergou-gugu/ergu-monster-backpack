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
	 * 名字<b>跟着状态走</b>:没醒的显示「原型背包」,醒了才是「怪物背包」。
	 *
	 * <h2>为什么不写 {@code CUSTOM_NAME}</h2>
	 * 早先的做法是给原型态塞一个 {@code CUSTOM_NAME = "原型背包"},
	 * 结果**唤醒之后名字还留着** —— 玩家看到"已经醒了却还叫原型背包"(实测反馈过)。
	 *
	 * <p>改成这里动态算:状态就在 {@code unlocked_slots} 里,
	 * <b>唤醒那一刻名字自动变回去</b>,不需要任何"清理"代码,
	 * 而且玩家自己用铁砧改的名也不会被弄丢。
	 */
	@Override
	public Component getName(final ItemStack stack) {
		if (stack.getOrDefault(ModComponents.UNLOCKED_SLOTS, 0) <= 0) {
			return Component.translatable(
				"item.ergu_monster_backpack.monster_backpack.prototype");
		}
		return super.getName(stack);
	}

	/**
	 * 背包自己的 tooltip —— <b>接管</b>原版自动塞"内含物品"的行为。
	 *
	 * <p>⚠️ {@code ItemContainerContents} 自己实现了 {@code TooltipProvider},
	 * 只要物品有 {@code CONTAINER} 就会自动往 tooltip 里塞内容(工作区约定坑 17)。
	 * 那样我们<b>没法控制</b>"按住 Ctrl 才展开"这件事,而 84 格全展开会盖满整个屏幕。
	 *
	 * <p>所以走<b>自己实现这个钩子</b>这条路(旅行者背包的选择),
	 * 渲染交给客户端的 {@code ClientBackpackTooltipComponent}。
	 */
	@Override
	public java.util.Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> getTooltipImage(
		final ItemStack stack
	) {
		return java.util.Optional.of(BackpackTooltipComponent.of(stack));
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

		openBackpack(level, player, hand);
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
				// ⚠️⚠️ 只在【物品真的被消耗】时才搬数据。
				//
				// 创造模式 super.useOn 不扣物品(ItemStack.shrink 在 hasInfiniteMaterials 时跳过),
				// 这时若还搬数据,就等于【凭空复制一份带内容的背包】。
				// 物品还在 ⇒ 视为"没消耗" ⇒ 放下去的方块保持默认原型态(空包)。
				if (context.getItemInHand().isEmpty()) {
					// ⚠️ 落点不能只按 relative(face) 猜:
					// 目标是可替换方块(草/雪/花/水)时,原版把方块放在 clickedPos 本身
					// (BlockPlaceContext.getClickedPos(): replaceClicked ? clickedPos : relativePos)。
					// 只查 relative(face) 会查错位置 ⇒ setBackpack 不执行 ⇒ 84 格内容全丢。
					final BlockPos primary = context.getClickedPos();
					final BlockPos secondary = primary.relative(context.getClickedFace());
					final MonsterBackpackBlockEntity placed =
						findPlacedBackpack(context.getLevel(), primary, secondary);
					if (placed != null) {
						placed.setBackpack(source);
					}
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

		openBackpack(context.getLevel(), player, context.getHand());
		return InteractionResult.SUCCESS;
	}

	/**
	 * 放置之后,在【两个候选位置】里找出刚生成的那个背包方块实体。
	 *
	 * <p>为什么要两个:原版 {@code BlockPlaceContext.getClickedPos()} 是
	 * {@code replaceClicked ? clickedPos : relativePos} —— 点在草/雪/花/水这类
	 * <b>可替换方块</b>上时,方块落在 {@code clickedPos} 本身;点在石头之类上面时,
	 * 落在 {@code clickedPos.relative(face)}。只查后者会让"对着草皮放置"丢内容。
	 */
	private static MonsterBackpackBlockEntity findPlacedBackpack(
		final Level level,
		final BlockPos primary,
		final BlockPos secondary
	) {
		if (level.getBlockEntity(secondary) instanceof MonsterBackpackBlockEntity backpack) {
			return backpack;
		}
		if (level.getBlockEntity(primary) instanceof MonsterBackpackBlockEntity backpack) {
			return backpack;
		}
		return null;
	}

	/**
	 * 只在服务端开容器(见 {@code BackpackMenuProvider} 的说明)。
	 *
	 * <p>⚠️ 传的是<b>具体槽位索引</b>,不是"主手"这个抽象位置 ——
	 * 因为玩家可以在界面开着的时候按数字键切槽,那样"主手"就指向别的东西了。
	 * 记成具体格子之后,界面始终读同一格,并且那一格会被锁住。
	 *
	 * <p>⚠️⚠️ <b>必须按"实际用的那只手"解析</b>。一律用 {@code getSelectedSlot()} 会错:
	 * 副手拿背包、主手空时,右键走到这里,而它指的是<b>主手那一格</b> ——
	 * 于是打开的是别的格子(严重时把主手那件东西当成背包去写组件)。
	 */
	private void openBackpack(final Level level, final Player player, final InteractionHand hand) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return;
		}

		final int source = hand == InteractionHand.OFF_HAND
			? BackpackOpenData.SOURCE_OFF_HAND
			: serverPlayer.getInventory().getSelectedSlot();

		serverPlayer.openMenu(new BackpackMenuProvider(BackpackOpenData.ofSource(source)));
	}

	/** 供方块注册引用,免得两处写死。 */
	public static Block block() {
		return ModBlocks.MONSTER_BACKPACK;
	}
}

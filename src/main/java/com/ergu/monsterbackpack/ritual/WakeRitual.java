package com.ergu.monsterbackpack.ritual;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.ergu.monsterbackpack.component.ModComponents;
import com.ergu.monsterbackpack.item.ModItems;

/**
 * 唤醒仪式 —— 把<b>原型背包</b>(0 格)变成<b>初始背包</b>(3 格)。
 *
 * <h2>基调是「吞噬」,不是「献祭」</h2>
 * (用户明确纠正过)
 * <pre>
 *   主手拿「仪式匕首」+ 右键原型背包的方块形态
 *        ↓ 挥匕割手
 *   玩家扣半格血(1 HP)
 *        ↓ 背包伸出触手
 *   抓住 3 格内【最近且可见】的一只羊,吞掉(无掉落物)
 *        ↓
 *   unlocked_slots: 0 → 3
 * </pre>
 *
 * <h2>三条硬约束(见 DESIGN.md §六)</h2>
 * <ol>
 *   <li>放血后血量不足 1 颗心(<b>2 点</b>)→ <b>不触发</b></li>
 *   <li><b>创造模式不扣血,但照样触发</b></li>
 *   <li>⚠️ <b>必须用 {@code setHealth()} 而不是 {@code hurt()}</b> ——
 *       {@code hurt()} 会被护甲 / 抗性 / 无敌帧吞掉,而"割手放掉的血必须是个定值"</li>
 * </ol>
 *
 * <p>条件不满足时用<b>动作栏</b>给隐晦反馈(呼应 F11 的含蓄风格),而不是弹一句大失败。
 */
public final class WakeRitual {

	/** 吞噬半径(格)。 */
	private static final double PREY_RADIUS = 3.0;

	/** 唤醒后送出的格数。 */
	public static final int STARTING_SLOTS = 3;

	/** 割手放掉的血量。 */
	private static final float BLOOD_COST = 1.0F;

	/** 放血之后至少要剩这么多血(1 颗心 = 2 点)。 */
	private static final float MIN_HEALTH_AFTER = 2.0F;

	private WakeRitual() {
	}

	/**
	 * 尝试唤醒放在 {@code pos} 的那个背包方块。
	 *
	 * @param backpack 方块实体里那个背包物品(原位修改)
	 */
	public static InteractionResult tryWake(
		final Level level,
		final BlockPos pos,
		final Player player,
		final ItemStack backpack
	) {
		if (level.isClientSide()) {
			// 客户端只负责"手挥了"这个反馈;真正的判定在服务端
			return InteractionResult.SUCCESS;
		}

		final int unlocked = backpack.getOrDefault(ModComponents.UNLOCKED_SLOTS, 0);

		if (unlocked > 0) {
			return whisper(player, "already_awake");
		}

		final boolean creative = player.isCreative();

		if (!creative && player.getHealth() - BLOOD_COST < MIN_HEALTH_AFTER) {
			return whisper(player, "too_weak");
		}

		final Sheep prey = findPrey(level, pos, player);

		if (prey == null) {
			return whisper(player, "no_prey");
		}

		// ---- 执行 ----

		if (!creative) {
			// ⚠️ 定值放血,不能用 hurt()
			player.setHealth(player.getHealth() - BLOOD_COST);
		}

		// 吞掉 —— 不掉落物
		prey.discard();

		if (level instanceof ServerLevel serverLevel) {
			// 血雾:割手的位置(玩家胸口高度)
			serverLevel.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
				player.getX(), player.getY() + 1.2, player.getZ(),
				8, 0.2, 0.2, 0.2, 0.05);

			// 触手吞食:背包方块上方冒一片"消化"的粒子
			serverLevel.sendParticles(ParticleTypes.ITEM_SLIME,
				pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
				18, 0.35, 0.35, 0.35, 0.02);
		}

		level.playSound(null, pos, SoundEvents.FROG_EAT, SoundSource.BLOCKS, 1.0F, 0.8F);

		// ---- 唤醒 ----
		backpack.set(ModComponents.UNLOCKED_SLOTS, STARTING_SLOTS);
		backpack.set(ModComponents.UNLOCK_START, -1);
		clearPrototypeName(backpack);

		if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
			// ⚠️ 带 overlay 的那个重载只有 ServerPlayer 有
			serverPlayer.sendSystemMessage(
				Component.translatable("ritual.ergu_monster_backpack.awakened"), true);
		}

		return InteractionResult.SUCCESS;
	}

	/**
	 * 醒过来的背包不该再挂着「原型背包」这个名字。
	 *
	 * <p>那个名字是<b>创造栏为了区分两个格子</b>临时加的(见 {@code ModItemGroups}),
	 * 不是物品的正式名称 —— 唤醒后必须摘掉,否则玩家会看到
	 * "已经醒了却还叫原型背包"这种自相矛盾的东西。
	 *
	 * <p>⚠️ 只摘<b>我们自己设的那一个</b>:以后玩家用铁砧给背包起的名,
	 * 不能因为做了个仪式就被抹掉。
	 */
	private static void clearPrototypeName(final ItemStack backpack) {
		final Component name = backpack.get(DataComponents.CUSTOM_NAME);
		if (name == null) {
			return;
		}
		if (name.equals(Component.translatable(
			"item.ergu_monster_backpack.monster_backpack.prototype"))) {
			backpack.remove(DataComponents.CUSTOM_NAME);
		}
	}

	/**
	 * 找 3 格内<b>最近且可见</b>的一只羊。
	 *
	 * <p>"可见"用 {@code hasLineOfSight} 判断(有方块挡着就不算)——
	 * 免得隔着一堵墙把羊吞了,看着莫名其妙。
	 */
	private static Sheep findPrey(final Level level, final BlockPos pos, final Player player) {
		final Vec3 center = Vec3.atCenterOf(pos);
		final List<Sheep> nearby = level.getEntitiesOfClass(
			Sheep.class, new AABB(pos).inflate(PREY_RADIUS));

		Sheep best = null;
		double bestDistance = Double.MAX_VALUE;

		for (Sheep candidate : nearby) {
			if (!candidate.isAlive() || !player.hasLineOfSight(candidate)) {
				continue;
			}
			final double distance = candidate.position().distanceToSqr(center);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = candidate;
			}
		}

		return best;
	}

	/**
	 * 条件不满足:动作栏给一句隐晦的话,不弹大提示。
	 *
	 * <p>⚠️ 动作栏那个重载({@code sendSystemMessage(Component, boolean)})
	 * <b>只有 {@link net.minecraft.server.level.ServerPlayer} 有</b> ——
	 * {@code Player} 上只有一个参数的版本(工作区约定坑 9 的同类问题)。
	 */
	private static InteractionResult whisper(final Player player, final String reason) {
		if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
			serverPlayer.sendSystemMessage(Component.translatable(
				"ritual.ergu_monster_backpack.wake." + reason), true);
		}
		return InteractionResult.FAIL;
	}

	/** 手上那把东西是不是仪式匕首。 */
	public static boolean isDagger(final ItemStack stack) {
		return stack.getItem() == ModItems.RITUAL_DAGGER;
	}

	/** 方块实体里那个背包是不是"还没醒"(0 格)。 */
	public static boolean isDormant(final ItemStack backpack) {
		return backpack.getOrDefault(ModComponents.UNLOCKED_SLOTS, 0) <= 0;
	}

	/** 供别的类判断"这个实体配不配被吃"——V0.1 只吃羊,先留着。 */
	public static boolean isEdible(final Entity entity) {
		return entity instanceof Sheep;
	}
}

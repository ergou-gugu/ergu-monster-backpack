package com.ergu.monsterbackpack.charm;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.ergu.monsterbackpack.growth.BackpackGrowth;

/**
 * 磁力吸取 —— 把附近的掉落物<b>吸向玩家</b>(见 DESIGN.md §八 与 §十二 F2)。
 *
 * <h2>⚠️ 两件事必须分开(用户 2026-10-15 定的拆分,我第一版理解错了)</h2>
 * <pre>
 *   「磁石」挂饰  = 【永远】把范围内的掉落物吸过来   ← 这是它的本事,和开关无关
 *   「自动收纳」开关 = 只决定【吸过来之后】怎么处理:
 *                      开 → 直接进背包
 *                      关 → 落在脚边,玩家自己捡
 * </pre>
 * <b>开关关掉不该让磁石失效</b> —— 那时磁石仍然在拉东西,只是拉过来之后归原版拾取管。
 *
 * <p>(第一版写成"开关关就整个 return",实测被用户指出:
 * "自动收纳关了以后磁石也不生效了"。)
 *
 * <h2>节奏</h2>
 * 调用方<b>每 5 tick</b> 来一次(和其它背包逻辑同一拍)。
 * 这是"扫描附近实体"的活,<b>绝不能每 tick 全图扫</b>。
 */
public final class MagnetCollector {

	/** 拉动的强度 —— 每 5 tick 给一次速度,靠它克服物品自身的摩擦。 */
	private static final double PULL_SPEED = 0.55;

	private MagnetCollector() {
	}

	/**
	 * 给一个玩家跑一拍吸取。
	 *
	 * @param backpack 他身上那个背包(内容可能被本方法改写)
	 */
	public static void tick(final ServerPlayer player, final ItemStack backpack) {
		final boolean autoCollect = backpack.getOrDefault(CharmComponents.AUTO_COLLECT, true);
		final double range = CharmEffects.collectRange(backpack);
		final AABB box = player.getBoundingBox().inflate(range);

		for (ItemEntity drop : player.level().getEntitiesOfClass(ItemEntity.class, box)) {
			if (!drop.isAlive() || drop.hasPickUpDelay()) {
				continue;
			}

			final ItemStack stack = drop.getItem();

			if (stack.isEmpty()) {
				continue;
			}

			if (autoCollect) {
				// 🆕 虚空 F17:白名单里的东西【根本不进背包】,直接销毁。
				//
				// ⚠️ 判定放在 tryInsert【之前】——
				// 否则会先占一格、下一拍才被清掉,背包会出现"闪一下"的现象。
				if (com.ergu.monsterbackpack.voiding.VoidRules.shouldVoid(backpack, stack)) {
					drop.discard();
					continue;
				}

				// ⚠️ 背包满了就【不吸】—— 东西留在原地,而不是凭空消失
				if (BackpackGrowth.tryInsert(backpack, stack.copy())) {
					drop.discard();
				}
			} else {
				// 开关关着:磁石【照样拉】,只是拉过来之后归原版拾取管
				pullTowards(drop, player);
			}
		}
	}

	/** 把掉落物朝玩家拉一把。 */
	private static void pullTowards(final ItemEntity drop, final ServerPlayer player) {
		final Vec3 direction = player.position()
			.add(0.0, player.getBbHeight() * 0.5, 0.0)
			.subtract(drop.position());

		if (direction.lengthSqr() < 0.04) {
			return;      // 已经很近了,交给原版拾取
		}

		drop.setDeltaMovement(direction.normalize().scale(PULL_SPEED));
		// ⚠️ 必须标记:否则服务端改了速度但客户端看不到,东西会"原地抽搐"
		drop.hurtMarked = true;
	}
}

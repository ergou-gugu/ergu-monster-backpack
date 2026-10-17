package com.ergu.monsterbackpack.voiding;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.ergu.monsterbackpack.item.ModItems;

/**
 * 虚空的**拾取入口判定** —— "这个东西被捡起来的时候,该不该直接销毁?"
 *
 * <h2>⚠️ 为什么需要这个类(而不是直接在磁力那里判断)</h2>
 * 物品进玩家手里其实有<b>两条</b>路:
 * <pre>
 *   ① 磁力吸取   MagnetCollector → 进【背包内容格】   ← 早就接了虚空判定
 *   ② 原版拾取   ItemEntity.playerTouch → player.getInventory().add(...)   ← ⚠️ 绕过了!
 * </pre>
 * ②是玩家走到掉落物旁边(原版拾取范围内)时触发的 ——
 * 它<b>直接调 {@code getInventory().add(...)}</b>,不经过任何我们的事件。
 * 2026-10-15 用户实测:"被磁力吸起来的确实消失了,但是扔在原拾取范围内的
 * 会直接进物品栏,**并不会被虚空**"。
 *
 * <h2>⚠️ 为什么不"每 tick 扫玩家物品栏"</h2>
 * 那样<b>分不清"刚捡的"和"玩家自己放进去的"</b> —— 会误伤后者,
 * 而那正是用户明确否掉的语义(见 {@code DESIGN.md} §十二 F17 的"作用范围")。
 * ⇒ 必须<b>在拾取那一刻</b>判断,所以只能 mixin {@code ItemEntity.playerTouch}。
 *
 * <h2>⚠️ 为什么判定要遍历【所有】背包</h2>
 * 玩家可能同时带着好几个背包(主手一个、胸甲一个、物品栏里一堆)。
 * 只要**任何一个**开着虚空且白名单匹配,这东西就该被销毁 ——
 * 否则"我明明配了虚空它还是进来了"。
 */
public final class VoidPickup {

	/**
	 * 这个掉落物被 {@code player} 拾取时,该不该直接销毁?
	 *
	 * <p>⚠️ <b>只判断,不改任何东西</b> —— 销毁由调用方做(它才知道该怎么 discard)。
	 */
	public static boolean shouldVoidOnPickup(final Player player, final ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}

		// 主手
		if (VoidRules.shouldVoid(player.getMainHandItem(), stack)) {
			return true;
		}

		// 胸甲(背包的主要佩戴位)
		if (VoidRules.shouldVoid(player.getItemBySlot(EquipmentSlot.CHEST), stack)) {
			return true;
		}

		// 物品栏 36 格(含快捷栏)
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (VoidRules.shouldVoid(player.getInventory().getItem(i), stack)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * 这个物品是不是"本 mod 的背包"。
	 *
	 * <p>⚠️ 抽出来是为了让"遍历玩家身上所有背包"那类逻辑有<b>同一个判据</b> ——
	 * 直接写 {@code stack.getItem() == ModItems.MONSTER_BACKPACK} 的地方多了,
	 * 以后加第二种背包(比如"方块形态的镜像物品")就会漏改。
	 */
	public static boolean isBackpack(final ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() == ModItems.MONSTER_BACKPACK;
	}

	private VoidPickup() {
	}
}

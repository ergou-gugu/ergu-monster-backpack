package com.ergu.monsterbackpack.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.ergu.monsterbackpack.voiding.VoidPickup;

/**
 * 虚空过滤 F17 —— <b>接管【原版近距离拾取】这条入口。</b>
 *
 * <h2>⚠️⚠️ 为什么必须 mixin</h2>
 * 物品进玩家手里有两条完全不同的路:
 * <pre>
 *   ① 磁力吸取    MagnetCollector.tick → BackpackGrowth.tryInsert → 进【背包内容格】
 *                 ✅ 这条早就接了虚空判定
 *
 *   ② 原版拾取    ItemEntity.playerTouch →
 *                     player.getInventory().add(itemStack)      ← ⚠️ 直接进【玩家物品栏】
 *                 ❌ 这条【不经过任何 mod 事件】,虚空完全看不到它
 * </pre>
 *
 * <p>原版 {@code playerTouch}({@code ItemEntity.java:332-348})的判断是:
 * <pre>
 *   if (this.pickupDelay == 0 && ... && player.getInventory().add(itemStack)) {
 *       player.take(this, orgCount);
 *       ...
 *   }
 * </pre>
 * 所以玩家<b>走到掉落物旁边</b>(原版拾取范围内)时,东西会被原版直接捡进物品栏,
 * <b>磁力根本没机会插手</b>。
 *
 * <p>2026-10-15 用户实测:"被磁力吸起来的确实消失了,但是扔在原拾取范围内的
 * 会直接进物品栏(已开启自动收纳)**并不会被虚空**"。
 *
 * <h2>⚠️ 为什么不在 tick 里"扫玩家物品栏"来补</h2>
 * 那<b>分不清"刚捡的"和"玩家自己放进去的"</b> ⇒ 会误伤后者 ——
 * 而"玩家手动放进去的不该被吃掉"是用户明确要的语义。
 * <b>必须在拾取那一刻判断</b>,这就是本 mixin 存在的唯一理由。
 *
 * <h2>⚠️ 注入点选 HEAD 而不是 TAIL</h2>
 * HEAD 里东西还<b>没进物品栏</b> ⇒ {@code discard()} 掉它、再 {@code cancel()} 掉原方法,
 * 那一份物品<b>从头到尾没有进过任何容器</b> —— 不存在"先进去再拿出来"的中间态
 * (那种写法会在快捷栏闪一下,而且可能和别人写的 mixin 打架)。
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityPickupMixin {

	/**
	 * 在 {@code playerTouch} 真正发物品之前拦一道。
	 *
	 * <p>⚠️ {@code cancellable = true} 是必须的 —— 我们要让原方法整个不执行。
	 */
	@Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
	private void ergu$voidOnPickup(final Player player, final CallbackInfo ci) {
		// ⚠️ 原方法自己会判断 isClientSide,但我们在它【之前】就得判断 ——
		// 不然客户端会本地预测"物品消失了",然后被服务端纠正回来,闪一下。
		if (player.level().isClientSide()) {
			return;
		}

		final ItemEntity self = (ItemEntity) (Object) this;

		// ⚠️⚠️ 【必须尊重 pickupDelay】—— 这是我第一版漏掉的,用户实测到的回归:
		//
		//   原话:"在虚空里的丢,连丢出去的动画都没了,只有个丢的动作,
		//         物品并不会飞出去然后被我捡到然后消失"
		//
		// 根因:玩家按 Q 丢东西时,物品实体是【生成在玩家脚下】的,
		// 而原版给丢出的物品设了 40 tick 的 pickupDelay(就是为了让它先飞出去、别马上被捡回来)。
		//
		// ⚠️ 原版 playerTouch 自己第一句就是 `this.pickupDelay == 0 && ...` ——
		// 我们注入在 HEAD,【跑在它前面】,所以如果不自己检查这一条,
		// 就等于**把原版这个保护整个绕过去了**:东西刚生成就被吃掉,连飞出去的动画都没有。
		//
		// ⇒ 加上这一条之后,流程变成玩家期待的样子:
		//   Q 丢出 → 飞出去落地(动画完整)→ pickupDelay 归零 →
		//   玩家（或磁力）靠近 → 被销毁。
		//
		// ⚠️ MagnetCollector 那边【也有】同样的检查(drop.hasPickUpDelay())——
		// 两处必须都有,因为它们管的是两条不同的路径。
		if (self.hasPickUpDelay()) {
			return;
		}

		final ItemStack stack = self.getItem();

		if (!VoidPickup.shouldVoidOnPickup(player, stack)) {
			return;
		}

		// 销毁 + 掐掉原方法。⚠️ 顺序无所谓(原方法已经被 cancel),但先 discard 更直观。
		self.discard();
		ci.cancel();
	}
}

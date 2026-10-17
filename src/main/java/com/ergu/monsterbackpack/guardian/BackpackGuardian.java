package com.ergu.monsterbackpack.guardian;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;

import com.ergu.monsterbackpack.ErguMonsterBackpack;
import com.ergu.monsterbackpack.component.ModComponents;
import com.ergu.monsterbackpack.item.ModItems;
import com.ergu.monsterbackpack.skill.SkillNodes;
import com.ergu.monsterbackpack.skill.SkillUnlock;

/**
 * **「护主」—— 死亡时背包（连同内容）不掉落**（DESIGN.md §十二 F1）。
 *
 * <h2>⚠️⚠️ 为什么用 {@code ALLOW_DEATH} 而不是 {@code AFTER_DEATH}</h2>
 * 这是 2026-10-15 核实源码之后**改掉的一个错误**（原设计写的 `AFTER_DEATH`）：
 *
 * <pre>
 * // ServerPlayerMixin.java:74 —— ⚠️ 注入在 die() 的【末尾】
 * {@literal @}Inject(method = "die", at = {@literal @}At("TAIL"))
 * private void notifyDeath(...) { AFTER_DEATH.invoker().afterDeath(...); }
 *
 * // 而 Player.die() 的形状:
 * public void die(DamageSource source) {
 *     super.die(source);                      // ← LivingEntity.die → dropAllDeathLoot
 *     this.reapplyPosition();
 *     this.dropAllDeathLoot(level, source);   // ← 掉落在这里
 *     ...
 * }                                           // ← TAIL 在这之后
 * </pre>
 *
 * ⇒ **`AFTER_DEATH` 触发时东西已经掉在地上了**,在里面"摘出来"拦不住。
 *
 * <p>而 **`ALLOW_DEATH`**（`LivingEntityMixin.java:75`）注入在 `hurtServer` 里判断死亡那一刻,
 * **比 `die()` 更早** ⇒ **那时背包还在身上**。
 *
 * <blockquote>📌 **教训**:**事件名字里带 AFTER 的,要看它 AFTER 的是哪一步。**</blockquote>
 *
 * <h2>⚠️⚠️ 为什么"摘出来的背包"要放进 Attachment</h2>
 * 死亡到重生之间,背包**不能留在物品栏里** ——
 * **原版 `dropAllDeathLoot` 会把物品栏清空并全部掉出来**。
 *
 * <p>所以流程是:
 * <ol>
 *   <li><b>死亡前</b>({@code ALLOW_DEATH}) —— 把背包从物品栏**移除**,存进 Attachment</li>
 *   <li><b>重生后</b>({@code AFTER_RESPAWN}) —— 从 Attachment 取出,装备回新玩家</li>
 * </ol>
 *
 * <p>⚠️ Attachment 必须声明 **{@code copyOnDeath()}** ——
 * 那是"把数据复制到重生后的新玩家身上"的原版机制。
 * **不声明的话,第 2 步拿不到东西,背包凭空消失。**
 * (旅行者也是这么做的:`ModAttachmentTypes.java:14`)
 *
 * <h2>⚠️ 三级的行为差异(§十二 F1)</h2>
 * <ul>
 *   <li><b>护主 I</b> —— 触发后进入 {@link #COOLDOWN_TICKS 3 分钟冷却};冷却期内再死 ⇒ **不保**</li>
 *   <li><b>护主 II</b> —— **冷却取消**,每次死亡都保</li>
 *   <li><b>护主 III</b> —— ⬜ 免死一次(还没做)</li>
 * </ul>
 */
public final class BackpackGuardian {

	/** 护主 I 的保护冷却 —— 3 分钟。 */
	public static final int COOLDOWN_TICKS = 3 * 60 * 20;

	/**
	 * 死亡时"摘出来"的背包暂存在这里。
	 *
	 * <p>⚠️ **两个 builder 调用缺一不可**:
	 * <ul>
	 *   <li>{@code initializer} —— 没值时的默认(空栈)。
	 *       ⚠️ 收的是 **Supplier**,不是值本身(编译时踩到了)</li>
	 *   <li>{@code persistent} —— 存进存档(玩家中途退出也不能丢)</li>
	 * </ul>
	 *
	 * <h2>⚠️⚠️ 刻意【不用】{@code copyOnDeath()}</h2>
	 * 2026-10-15 用户实测:**用了 {@code copyOnDeath()} 之后背包直接消失了**。
	 *
	 * <p>那个 API 的机制是(`AttachmentTargetImpl.transfer`):
	 * <pre>
	 * if (!isDeath || type.copyOnDeath()) {
	 *     target.setAttached(type, entry.getValue());   // 复制值
	 * }
	 * </pre>
	 * **逻辑上该生效**,但它依赖"原版在重生时调用 {@code transfer}"这个**时机** ——
	 * 而那个时机我们**没法从代码里看出来**(只能实测)。
	 *
	 * <p>⇒ **改成自己手动交接**(见 {@code COPY_FROM} 那段):
	 * 时机明确、失败可查、不依赖原版内部流程。
	 *
	 * <blockquote>📌 **判据**:能自己控制时机的交接,不要托付给"原版大概会在某处处理"。</blockquote>
	 */
	public static final AttachmentType<ItemStack> STASH = AttachmentRegistry.create(
		ErguMonsterBackpack.id("guardian_stash"),
		builder -> builder
			// ⚠️ initializer 收的是 Supplier,不是值本身
			.initializer(() -> ItemStack.EMPTY)
			.persistent(ItemStack.CODEC)
	);

	private BackpackGuardian() {
	}

	/** 在 {@code ModInitializer.onInitialize()} 里调用(服务端事件,双端注册也无害)。 */
	public static void register() {
		// ---- ① 死亡【之前】:把背包摘出来 ----
		//
		// ⚠️ 返回值【永远是 true】—— 我们只是"顺手把背包收好",不阻止死亡。
		// (返回 false 是"免死",那是护主 III 的事,和这里混在一起会很难查。)
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player) {
				stashBeforeDeath(player);
			}
			return true;
		});

		// ---- ② 重生时:手动交接(⚠️ 不用 copyOnDeath,见 STASH 的注释)----
		//
		// ⚠️ 用 COPY_FROM 而不是 AFTER_RESPAWN:
		//   · COPY_FROM 注入在 `ServerPlayer.restoreFrom` 的 TAIL
		//     (ServerPlayerMixin.java:87)⇒ **物品栏已经恢复完了**,可以直接 add
		//   · 而且它同时给了【新旧两个玩家】,交接一步到位
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			final ItemStack stashed = oldPlayer.getAttachedOrElse(STASH, ItemStack.EMPTY);

			if (stashed.isEmpty()) {
				return;
			}

			// ⚠️ 先从【旧玩家】清空 —— 否则万一这个实例被别的逻辑再读一次,
			// 会变成"地上一个 + 身上一个"的复制 bug。
			oldPlayer.setAttached(STASH, ItemStack.EMPTY);

			restore(newPlayer, stashed);
		});
	}

	/**
	 * 死亡前把背包收好。
	 *
	 * <p>⚠️ **四道门,顺序有讲究**:
	 * <ol>
	 *   <li><b>{@code keepInventory}</b> —— ⚠️ **最优先,而且无条件**。
	 *       原版规则开着时背包本来就不会掉,**我们一行都不该做**
	 *       (用户 2026-10-15 明确:"不论是否冷却不论背包等级")</li>
	 *   <li><b>身上有没有背包</b> —— 没有就没事可做</li>
	 *   <li><b>技能树解锁了没</b> —— 没解锁就当没有这个能力</li>
	 *   <li><b>在不在冷却</b> —— ⚠️ **护主 II 没有冷却**,所以要先查级别</li>
	 * </ol>
	 */
	private static void stashBeforeDeath(final ServerPlayer player) {
		// ① ⚠️ 原版规则优先 —— 不论冷却、不论等级,一律不插手
		if (player.level().getGameRules().get(GameRules.KEEP_INVENTORY)) {
			return;
		}

		// ② 找背包(主手 → 胸甲 → 物品栏)
		final ItemStack backpack = findBackpack(player);
		if (backpack.isEmpty()) {
			return;
		}

		// ③ 技能树
		if (!SkillUnlock.isUnlocked(backpack, SkillNodes.GUARDIAN_1)) {
			return;
		}

		// ④ 冷却 —— ⚠️ 护主 II 取消了冷却,所以只对 I 生效
		final boolean noCooldown = SkillUnlock.isUnlocked(backpack, SkillNodes.GUARDIAN_2);
		final long now = player.level().getGameTime();
		final int startedAt = backpack.getOrDefault(ModComponents.GUARDIAN_COOLDOWN, -1);

		if (!noCooldown && startedAt >= 0 && now - startedAt < COOLDOWN_TICKS) {
			// ⚠️ 必须给反馈 —— 否则玩家分不清"没解锁"和"在冷却"
			// (和「R 键按了没反应」是同一类问题)
			//
			// ⚠️ `true` 是【动作栏】—— Player 上只有一个参数的版本,
			// 两个参数的重载只有 ServerPlayer 有(坑 9 的同类,见 WakeRitual.whisper)。
			final int left = (int) ((COOLDOWN_TICKS - (now - startedAt)) / 20);
			player.sendSystemMessage(Component.translatable(
				"guardian.ergu_monster_backpack.cooldown", left), true);
			return;
		}

		// ---- 收好 ----

		// ⚠️ 先记冷却,再移除 —— 顺序反了的话,如果下面抛异常,背包就没了还没冷却
		backpack.set(ModComponents.GUARDIAN_COOLDOWN, (int) now);

		// 从物品栏里【真正移除】(不能只 copy —— 原版等下会把物品栏清空并掉出来)
		removeBackpack(player, backpack);

		// 存进 Attachment,重生后取
		player.setAttached(STASH, backpack);

		// ⚠️ 动作栏 —— 死亡时聊天栏会被死亡消息刷掉
		player.sendSystemMessage(Component.translatable(
			"guardian.ergu_monster_backpack.saved"), true);
	}

	/**
	 * 把背包还给重生后的玩家。
	 *
	 * <p>⚠️ <b>接受的是【值】而不是玩家身上读</b> —— 交接已经在上面的
	 * {@code COPY_FROM} 里做完了(从旧玩家读、清空旧玩家、传进来)。
	 * 这里只负责"放回物品栏"。
	 *
	 * <p>⚠️ <b>装不下时不能丢</b>:重生后物品栏几乎一定是空的,
	 * 但万一被别的 mod 占了,<b>宁可掉在脚下也不要吞掉</b>。
	 */
	private static void restore(final ServerPlayer player, final ItemStack backpack) {
		if (backpack.isEmpty()) {
			return;
		}

		if (!player.getInventory().add(backpack)) {
			player.drop(backpack, false);
		}
	}

	// ────────────────────────────────────────────────────────────
	// 找 / 移除背包
	// ────────────────────────────────────────────────────────────

	/**
	 * 找玩家身上的背包(**返回物品栏里那个实例本身**,不是拷贝)。
	 *
	 * <p>⚠️ 优先级和打开背包一致(§七):**主手 → 胸甲 → 物品栏**。
	 *
	 * <p>⚠️ **必须是实例本身** —— 因为 {@link #removeBackpack} 要拿它去比对身份。
	 * 返回拷贝的话移除会失败,而**失败是静默的**(背包照掉)。
	 */
	private static ItemStack findBackpack(final ServerPlayer player) {
		if (player.getMainHandItem().getItem() == ModItems.MONSTER_BACKPACK) {
			return player.getMainHandItem();
		}

		final ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		if (chest.getItem() == ModItems.MONSTER_BACKPACK) {
			return chest;
		}

		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).getItem() == ModItems.MONSTER_BACKPACK) {
				return player.getInventory().getItem(i);
			}
		}

		return ItemStack.EMPTY;
	}

	/**
	 * 把背包从玩家身上拿掉。
	 *
	 * <p>⚠️ 三个位置都要试 —— 主手、胸甲、物品栏。
	 * **用对象身份比对**(`==`)而不是 `is(...)`:
	 * 物品栏里可能有两个背包,身份比对才能拿掉<b>我们找到的那一个</b>。
	 */
	private static void removeBackpack(final ServerPlayer player, final ItemStack backpack) {
		// 主手
		if (player.getMainHandItem() == backpack) {
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		}

		// 副手(⚠️ findBackpack 没找副手,但万一主手和副手是同一个实例……那不可能。
		// 这里只是防御性写法,实际不会命中)
		if (player.getOffhandItem() == backpack) {
			player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
		}

		// 胸甲
		if (player.getItemBySlot(EquipmentSlot.CHEST) == backpack) {
			player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		}

		// 物品栏
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i) == backpack) {
				player.getInventory().setItem(i, ItemStack.EMPTY);
				break;      // ⚠️ break —— 同一个实例只可能在一个格子里
			}
		}
	}
}

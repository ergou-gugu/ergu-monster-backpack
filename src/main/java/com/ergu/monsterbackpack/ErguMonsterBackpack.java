package com.ergu.monsterbackpack;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ergu.monsterbackpack.block.ModBlocks;
import com.ergu.monsterbackpack.block.entity.ModBlockEntities;
import com.ergu.monsterbackpack.component.ModComponents;
import com.ergu.monsterbackpack.event.BackpackTickHandler;
import com.ergu.monsterbackpack.item.ModCharmItems;
import com.ergu.monsterbackpack.item.ModItemGroups;
import com.ergu.monsterbackpack.item.ModItems;
import com.ergu.monsterbackpack.menu.ModMenus;
import com.ergu.monsterbackpack.guardian.BackpackGuardian;
import com.ergu.monsterbackpack.net.ModNetworking;
/**
 * 王二咕的怪物背包 / Ergu's Monster Backpack。
 *
 * <p>modid {@code ergu_monster_backpack}。设计文档见工程根目录的 {@code DESIGN.md}。
 *
 * <p><b>当前状态:V0.1</b> —— 组件 / 物品 / 容器 / 界面 / 打开链路已通;
 * 方块形态、唤醒仪式、成长计时随后补。
 *
 * <p><b>注册顺序要紧</b>:自定义组件必须最先注册,物品的 {@code Properties} 才会引用到它。
 */
public class ErguMonsterBackpack implements ModInitializer {
	public static final String MOD_ID = "ergu_monster_backpack";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// 调用这些 bootstrap() 只为触发各注册类的静态初始化,注册就发生在类加载时。
		// 顺序要紧:组件 → 方块 → 方块实体 → 物品(物品要引用方块)→ 栏位 → 容器。
		ModComponents.bootstrap();
		com.ergu.monsterbackpack.charm.CharmComponents.bootstrap();
		ModBlocks.bootstrap();
		ModBlockEntities.bootstrap();
		ModItems.bootstrap();
		ModCharmItems.bootstrap();
		ModItemGroups.bootstrap();
		ModMenus.bootstrap();

		// 网络:包类型必须双端注册(发送方要编码、接收方要解码)
		ModNetworking.registerCommon();

		// 事件:成长计时(服务端,每 5 tick 一拍)
		BackpackTickHandler.register();

		// 🆕 事件:「护主」—— 死亡时背包不掉落(§十二 F1)
		//
		// ⚠️ 它注册的是 ServerLivingEntityEvents / ServerPlayerEvents —— 都是【服务端】事件。
		// 在 onInitialize(双端都跑)里注册无害:客户端那边这些事件根本不会触发。
		BackpackGuardian.register();

		LOGGER.info("Ergu's Monster Backpack initialized.");
	}

	/** 拼一个本 mod 命名空间下的 {@link Identifier}。 */
	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}

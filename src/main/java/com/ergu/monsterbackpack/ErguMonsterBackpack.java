package com.ergu.monsterbackpack;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 王二咕的怪物背包 / Ergu's Monster Backpack。
 *
 * <p>modid {@code ergu_monster_backpack}。设计文档见工程根目录的 {@code DESIGN.md}。
 *
 * <p><b>当前状态:空骨架</b> —— 尚未注册任何游戏内容。
 */
public class ErguMonsterBackpack implements ModInitializer {
	public static final String MOD_ID = "ergu_monster_backpack";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Ergu's Monster Backpack initialized.");
	}

	/** 拼一个本 mod 命名空间下的 {@link Identifier}。 */
	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}

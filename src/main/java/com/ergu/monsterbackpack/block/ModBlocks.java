package com.ergu.monsterbackpack.block;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import com.ergu.monsterbackpack.ErguMonsterBackpack;

/**
 * 方块注册。
 *
 * <p>26.2 的写法与物品一致:{@code Registry.register(Registry, ResourceKey, T)},
 * 而且 {@code Properties} 也要先 {@code setId(...)}
 * (与物品的坑 1 同源 —— 方块构造器同样会去读它)。
 */
public final class ModBlocks {

	/** 方块 ID:{@code ergu_monster_backpack:monster_backpack}(与物品同 ID,这是刻意的)。 */
	public static final ResourceKey<Block> MONSTER_BACKPACK_KEY = ResourceKey.create(
		Registries.BLOCK,
		ErguMonsterBackpack.id("monster_backpack")
	);

	public static final Block MONSTER_BACKPACK = Registry.register(
		BuiltInRegistries.BLOCK,
		MONSTER_BACKPACK_KEY,
		new MonsterBackpackBlock(BlockBehaviour.Properties.of()
			.setId(MONSTER_BACKPACK_KEY)
			// 好挖、不好炸 —— 它就是玩家的储物家具
			.strength(1.0F)
			.sound(SoundType.WOOL))
	);

	private ModBlocks() {
	}

	/** 空方法,唯一作用是触发本类的静态初始化。 */
	public static void bootstrap() {
	}
}

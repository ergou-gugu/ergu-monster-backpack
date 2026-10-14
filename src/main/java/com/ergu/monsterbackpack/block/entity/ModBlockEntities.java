package com.ergu.monsterbackpack.block.entity;

import java.util.Set;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import com.ergu.monsterbackpack.ErguMonsterBackpack;
import com.ergu.monsterbackpack.block.ModBlocks;

/**
 * 方块实体类型注册(<b>双端</b>)。
 *
 * <p>26.2 的 {@code BlockEntityType} <b>没有 Builder</b>,直接构造:
 * {@code new BlockEntityType<>(工厂, 有效方块集合)}({@code BlockEntityType.java:18})。
 * 工厂的签名是 {@code (BlockPos, BlockState) -> T}。
 */
public final class ModBlockEntities {

	public static final BlockEntityType<MonsterBackpackBlockEntity> MONSTER_BACKPACK =
		Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			ErguMonsterBackpack.id("monster_backpack"),
			new BlockEntityType<>(
				MonsterBackpackBlockEntity::new,
				Set.of(ModBlocks.MONSTER_BACKPACK)
			)
		);

	private ModBlockEntities() {
	}

	/** 空方法,唯一作用是触发本类的静态初始化。 */
	public static void bootstrap() {
	}
}

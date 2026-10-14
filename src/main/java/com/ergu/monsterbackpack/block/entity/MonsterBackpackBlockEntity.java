package com.ergu.monsterbackpack.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.ergu.monsterbackpack.growth.BackpackGrowth;
import com.ergu.monsterbackpack.item.ModItems;

/**
 * 背包方块(方块形态)的方块实体。
 *
 * <h2>它到底存了什么</h2>
 * 存<b>一个 {@code ItemStack}</b> —— 也就是"这个方块对应的那个背包物品"。
 *
 * <p>这样设计的好处很大:
 * <ul>
 *   <li>内容组件、{@code unlocked_slots}、{@code unlock_start} <b>全都不用搬</b> —— 它们就在那个物品里</li>
 *   <li><b>生长逻辑零成本复用</b>:{@link BackpackGrowth#tick} 直接吃 {@code ItemStack}</li>
 *   <li>破坏时<b>把那个物品原样掉出来</b>就行,内容天然不丢</li>
 * </ul>
 *
 * <h2>26.2 的存档 API</h2>
 * 用 {@link ValueOutput#store}/{@link ValueInput#read} 配 {@code Codec}
 * (旧的 {@code CompoundTag} 写法已经不通了)。
 * {@code ItemStack.CODEC} 会把<b>全部组件</b>一起序列化,正好是我们想要的。
 */
public class MonsterBackpackBlockEntity extends BlockEntity {

	/** 方块里那个背包物品。空方块实体(理论上不会出现)时是空栈。 */
	private ItemStack backpack = new ItemStack(ModItems.MONSTER_BACKPACK);

	public MonsterBackpackBlockEntity(final BlockPos pos, final BlockState state) {
		super(ModBlockEntities.MONSTER_BACKPACK, pos, state);
	}

	/** 供界面/生长读取。 */
	public ItemStack backpack() {
		return this.backpack;
	}

	/** 放下去的时候把物品的数据搬进来。 */
	public void setBackpack(final ItemStack stack) {
		this.backpack = stack.copyWithCount(1);
		this.setChanged();
	}

	// ------------------------------------------------------------ 每 tick 生长

	/**
	 * 方块形态的生长拍子。
	 *
	 * <p>由 {@code MonsterBackpackBlock.getTicker} 注册,<b>只有服务端</b>会被调。
	 */
	public static void serverTick(
		final net.minecraft.world.level.Level level,
		final BlockPos pos,
		final BlockState state,
		final MonsterBackpackBlockEntity entity
	) {
		BackpackGrowth.tick(entity.backpack, level.getGameTime());
	}

	// ---------------------------------------------------------------- 存档

	@Override
	protected void saveAdditional(final ValueOutput output) {
		super.saveAdditional(output);
		// 连组件一起存 —— 内容、格数、计时全在里面
		output.store("backpack", ItemStack.CODEC, this.backpack);
	}

	@Override
	protected void loadAdditional(final ValueInput input) {
		super.loadAdditional(input);
		this.backpack = input.read("backpack", ItemStack.CODEC)
			.orElseGet(() -> new ItemStack(ModItems.MONSTER_BACKPACK));
	}
}

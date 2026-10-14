package com.ergu.monsterbackpack.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.ergu.monsterbackpack.block.entity.MonsterBackpackBlockEntity;
import com.ergu.monsterbackpack.component.ModComponents;

/**
 * 背包界面(物品形态 + 方块形态共用一个)的容器。
 *
 * <h2>布局</h2>
 * <pre>
 *   7 行 × 12 列 = 84 格(背包内容)   ← 贴左边
 *   右边 6 列宽                      ← 预留给挂饰槽与后续功能
 *   + 玩家 3 行主背包 + 1 行快捷栏
 * </pre>
 *
 * <h2>⚠️ 为什么需要 {@link ContainerData}</h2>
 * 物品形态的格数靠<b>原版库存同步</b>就能到客户端;
 * 但<b>方块形态的方块实体不会自动同步内容</b> —— 客户端那边那份永远是刚放下时的样子。
 *
 * <p>所以格数与计时额外走一条菜单数据通道:{@code addDataSlots(...)}
 * (原版用它同步熔炉的燃烧进度,机制完全一样)。
 * 服务端读真实值,客户端收到后<b>写回自己那份物品</b>,
 * 于是界面继续安心地从物品读,不必区分两种形态。
 */
public class BackpackMenu extends AbstractContainerMenu {

	// ---------------------------------------------------------------- 面板几何
	/**
	 * 面板宽:<b>18 列宽</b> ≈ 340 px(2026-10-15 用户实测后定,见 §七)。
	 *
	 * <p>背包占左边 12 列(216 px),右边留 <b>约 116 px</b> 给挂饰槽与后续额外功能。
	 * 逻辑宽 480 px(1920×1080 + GUI scale 4)下占 71%,放得下。
	 */
	public static final int PANEL_WIDTH = 340;

	/** 面板高:7 行背包 + 玩家物品栏(与原版公式 {@code 114 + 行数×18} 一致)。 */
	public static final int PANEL_HEIGHT = 240;

	/** 背包格区。<b>右移</b>给左侧的侧签条让位。 */
	public static final int GRID_X = 80;
	public static final int GRID_Y = 18;
	public static final int GRID_ROWS = 7;
	public static final int GRID_COLS = 12;

	/** 背包格总数。 */
	public static final int GRID_SLOTS = GRID_ROWS * GRID_COLS;

	/** 玩家物品栏的标签行 y(照原版 {@code imageHeight - 94})。 */
	public static final int INVENTORY_LABEL_Y = PANEL_HEIGHT - 94;

	/**
	 * 玩家物品栏那 9 列在面板内<b>居中</b>。
	 *
	 * <p>它不属于背包区,所以不跟 {@link #GRID_X} 一起靠左 ——
	 * 原版也是这么算的({@code (imageWidth - 9 * 18) / 2})。
	 */
	private static final int INVENTORY_X = (PANEL_WIDTH - 9 * 18) / 2;
	private static final int INVENTORY_MAIN_Y = PANEL_HEIGHT - 82;
	private static final int INVENTORY_HOTBAR_Y = PANEL_HEIGHT - 24;

	// ---------------------------------------------------------------- 侧签
	/**
	 * 侧签条 —— <b>贴面板左边缘</b>(用户 2026-10-15 定,参考精妙背包的版面)。
	 *
	 * <p>点开之后内容<b>向左展开到面板外</b>(见 {@link #POPUP_WIDTH}),
	 * 所以 {@code BackpackScreen} 会把整个面板<b>右移</b>给弹窗腾地方。
	 */
	public static final int TAB_STRIP_X = 2;

	/** 侧签按钮的高度(紧凑,照精妙背包那种一列小按钮)。 */
	public static final int TAB_BUTTON_HEIGHT = 18;

	/** 侧签条宽度(竖排的那一列)。 */
	public static final int TAB_STRIP_WIDTH = 36;

	/** 侧签条的起点 y 与每个签的高度。 */
	public static final int TAB_TOP = 22;
	public static final int TAB_HEIGHT = 22;

	/**
	 * 侧签按钮上的短标签。
	 *
	 * <p>按钮宽 36px,放得下两个字,所以短标签<b>本身就是完整的名字</b>。
	 */
	public static final String[] TAB_SHORT_KEYS = {
		"gui.ergu_monster_backpack.tab.feed.short",
		"gui.ergu_monster_backpack.tab.charm.short",
		"gui.ergu_monster_backpack.tab.stomach.short",
		"gui.ergu_monster_backpack.tab.void.short",
	};

	/**
	 * 悬停时浮出的<b>说明</b>(不是重复名字 —— 名字按钮上已经写着了)。
	 *
	 * <p>2026-10-15 用户实测反馈:"浮出全名那个没法测,短标签就显示完了" ——
	 * 说明浮出内容跟按钮重复是没意义的,所以改成<b>这个功能是干什么的</b>。
	 */
	public static final String[] TAB_HINT_KEYS = {
		"gui.ergu_monster_backpack.tab.feed.hint",
		"gui.ergu_monster_backpack.tab.charm.hint",
		"gui.ergu_monster_backpack.tab.stomach.hint",
		"gui.ergu_monster_backpack.tab.void.hint",
	};

	/** 侧签弹窗的宽度 —— 它在面板<b>左侧外面</b>,所以坐标是负的。 */
	public static final int POPUP_WIDTH = 104;

	/** 弹窗贴面板左边缘的那个相对 x(也就是 0),内容从这个 x 往左排。 */
	public static final int POPUP_RIGHT = 0;

	/** 全关。 */
	public static final int TAB_NONE = -1;
	public static final int TAB_FEED = 0;
	public static final int TAB_CHARM = 1;
	public static final int TAB_STOMACH = 2;
	public static final int TAB_VOID = 3;
	/** 侧签总数。 */
	public static final int TAB_COUNT = 4;

	public static final String[] TAB_KEYS = {
		"gui.ergu_monster_backpack.tab.feed",
		"gui.ergu_monster_backpack.tab.charm",
		"gui.ergu_monster_backpack.tab.stomach",
		"gui.ergu_monster_backpack.tab.void",
	};

	/** 喂食格的位置 —— 在左侧弹窗里居中(坐标是负的,因为弹窗在面板外)。 */
	private static final int FEED_SLOT_X = POPUP_RIGHT - (POPUP_WIDTH + 18) / 2;
	private static final int FEED_SLOT_Y = 46;

	// ---------------------------------------------------------------- 挂饰槽
	/** 挂饰槽数量(见 §八:一个阶段解锁一个)。 */
	public static final int CHARM_SLOTS = 2;

	/** 两个挂饰槽在弹窗里的位置 —— 竖着排。 */
	private static final int CHARM_SLOT_X = POPUP_RIGHT - (POPUP_WIDTH + 18) / 2;
	private static final int CHARM_SLOT_Y = 40;
	/** 两个槽之间的间距(留一行写字)。 */
	private static final int CHARM_SLOT_GAP = 26;

	// ------------------------------------------------------------ 同步数据索引
	private static final int DATA_UNLOCKED = 0;
	private static final int DATA_START = 1;
	/** 🆕 饱食度走同一条通道 —— 方块形态那边同样不会自动同步。 */
	private static final int DATA_SATIETY = 2;

	/**
	 * 🆕 <b>当前打开的侧签</b>也要走这条通道。
	 *
	 * <p>它是菜单自己的字段,既不是组件、也不在槽位里,所以<b>原版容器同步根本不会管它</b>。
	 * 实测症状(2026-10-15):点侧签后服务端改了,但<b>客户端界面纹丝不动</b> ——
	 * 看起来就像"点了没反应"。
	 *
	 * <p>⚠️ 它<b>不写回物品</b>(只是菜单状态),所以 {@code set} 里直接改字段。
	 */
	private static final int DATA_TAB = 3;

	/** 🆕 阶段 —— 独立存的组件,同样要走同步通道(方块形态下客户端物品不准)。 */
	private static final int DATA_STAGE = 4;
	private static final int DATA_COUNT = 5;

	// ---------------------------------------------------------------- 实例
	private final BackpackContainer container;
	private final Inventory playerInventory;
	/** 背包在哪(两端一致)。 */
	private final BackpackOpenData openData;

	/**
	 * 玩家物品栏里<b>要锁住</b>的那一格(原版索引)。{@code -1} = 不用锁。
	 *
	 * <p>只有物品形态需要:它的背包就待在玩家物品栏里,能被拖走。
	 * 方块形态的背包在世界里,物品栏中没有对应格子。
	 */
	private final int lockedInventoryIndex;

	/** 格数与计时的那条通道。 */
	private final ContainerData state;

	/** 当前打开的侧签。**两端一致** —— 它决定哪些功能槽是活的。 */
	private int openTab = TAB_NONE;

	/**
	 * 喂食格用的临时容器。
	 *
	 * <p>它<b>不需要持久化</b>:东西放进去当场就被吃掉,格子立刻空回来,
	 * 所以关界面时它本来就是空的。
	 */
	private final net.minecraft.world.SimpleContainer feedContainer =
		new net.minecraft.world.SimpleContainer(1);

	/**
	 * 两个挂饰槽的内容 —— 存在背包的 {@code charms} 组件上(插上就跟着背包走)。
	 *
	 * <p>⚠️ <b>必须在构造器体里赋值,不能写成字段初始化器</b>:
	 * 字段初始化器在<b>构造器体之前</b>执行,而它构造时要立刻读一次背包,
	 * 那时 {@link #openData} 还是 {@code null} ⇒ 一开界面就 NPE。
	 * (2026-10-15 实测踩到:"背包直接打不开了"。)
	 */
	private final CharmContainer charmContainer;

	public BackpackMenu(final int containerId, final Inventory inventory, final BackpackOpenData data) {
		super(ModMenus.BACKPACK, containerId);

		this.playerInventory = inventory;
		this.openData = data;
		this.lockedInventoryIndex = computeLockedIndex(inventory, data);

		// ⚠️ 顺序要紧:它构造时会立刻读一次背包,所以必须在 openData 赋值【之后】
		this.charmContainer = new CharmContainer(this::currentBackpack);

		this.state = new ContainerData() {
			@Override
			public int get(final int index) {
				if (index == DATA_TAB) {
					return BackpackMenu.this.openTab;
				}

				final ItemStack backpack = BackpackMenu.this.currentBackpack();
				return switch (index) {
					case DATA_UNLOCKED -> backpack.getOrDefault(ModComponents.UNLOCKED_SLOTS, 0);
					case DATA_SATIETY -> backpack.getOrDefault(ModComponents.SATIETY, 0);
					case DATA_STAGE -> backpack.getOrDefault(ModComponents.STAGE, 1);
					default -> backpack.getOrDefault(ModComponents.UNLOCK_START, -1);
				};
			}

			@Override
			public void set(final int index, final int value) {
				// 侧签是【菜单自己的状态】,不写回物品
				if (index == DATA_TAB) {
					BackpackMenu.this.openTab = value;
					return;
				}

				// 其余三项:客户端把服务端同步来的值写回【自己那份】物品,
				// 这样界面照旧从物品读就行,不必为方块形态写两套逻辑。
				final ItemStack backpack = BackpackMenu.this.currentBackpack();
				switch (index) {
					case DATA_UNLOCKED -> backpack.set(ModComponents.UNLOCKED_SLOTS, value);
					case DATA_SATIETY -> backpack.set(ModComponents.SATIETY, value);
					case DATA_STAGE -> backpack.set(ModComponents.STAGE, value);
					default -> backpack.set(ModComponents.UNLOCK_START, value);
				}
			}

			@Override
			public int getCount() {
				return DATA_COUNT;
			}
		};
		this.addDataSlots(this.state);

		// ⚠️ 传"取值方式"而不是 ItemStack ——
		// 原版容器同步会把实例换掉,抓着旧对象界面就永远不更新(工作区约定坑 22)。
		// ⚠️ 格数也走同步通道(方块形态下客户端物品里的格数不准)。
		this.container = new BackpackContainer(this::currentBackpack, this::unlockedSlots);

		this.addBackpackGrid();
		this.addPlayerInventory();
		this.addSideTabs();
	}

	/**
	 * 侧签里的功能槽。
	 *
	 * <p>它们<b>始终存在于菜单里</b>,只是根据 {@link #openTab} 决定活不活 ——
	 * 用 {@code Slot.isActive()} 控制。这样切换侧签<b>不需要重建菜单</b>,
	 * 容器也不用重开(与 §七「一个 Menu + 一个 Screen,页签切换不重开容器」一致)。
	 */
	private void addSideTabs() {
		this.addSlot(new FeedSlot(this.feedContainer, 0, FEED_SLOT_X, FEED_SLOT_Y,
			this::currentBackpack,
			() -> this.openTab == TAB_FEED));

		// 两个挂饰槽 —— 除了"签开着",还各有一道【阶段】门(见 CharmSlot)。
		// 阶段读的是独立存的组件(走同步通道),不是从格数算的。
		for (int i = 0; i < CHARM_SLOTS; i++) {
			this.addSlot(new CharmSlot(this.charmContainer, i,
				CHARM_SLOT_X, CHARM_SLOT_Y + i * CHARM_SLOT_GAP,
				this::stage,
				() -> this.openTab == TAB_CHARM));
		}
	}

	/** 当前打开的侧签。 */
	public int openTab() {
		return this.openTab;
	}

	/** 切换侧签(服务端收到包后调用;两端会因此保持一致)。 */
	public void setTab(final int tab) {
		this.openTab = tab >= 0 && tab < TAB_COUNT ? tab : TAB_NONE;
	}

	/**
	 * 现在那个背包物品。
	 *
	 * <p>两端各自解析:物品形态从玩家身上取,方块形态从方块实体取。
	 * 客户端那边方块实体虽然不同步内容,但格数已由 {@link #state} 补上。
	 */
	private ItemStack currentBackpack() {
		if (this.openData.isBlock()) {
			final BlockEntity be = this.playerInventory.player.level()
				.getBlockEntity(this.openData.pos());
			if (be instanceof MonsterBackpackBlockEntity backpack) {
				return backpack.backpack();
			}
			return ItemStack.EMPTY;
		}
		return resolveFromPlayer(this.playerInventory.player, this.openData.source());
	}

	/** 按位置在玩家身上找背包(物品形态)。 */
	public static ItemStack resolveFromPlayer(final Player player, final int source) {
		if (source == BackpackOpenData.SOURCE_CHEST) {
			return player.getItemBySlot(EquipmentSlot.CHEST);
		}
		if (source == BackpackOpenData.SOURCE_MAIN_HAND) {
			return player.getMainHandItem();
		}
		return player.getInventory().getItem(source);
	}

	/** 老的调用名,留着免得别处引用要改。 */
	public static ItemStack resolveBackpack(final Player player, final int source) {
		return resolveFromPlayer(player, source);
	}

	/** 这个世界坐标上是不是真的有一个背包方块(方块形态开界面前的校验)。 */
	public static boolean isBackpackBlockAt(final Player player, final BlockPos pos) {
		return player.level().getBlockEntity(pos) instanceof MonsterBackpackBlockEntity;
	}

	private void addBackpackGrid() {
		for (int row = 0; row < GRID_ROWS; row++) {
			for (int col = 0; col < GRID_COLS; col++) {
				final int index = row * GRID_COLS + col;
				this.addSlot(new BackpackSlot(this.container, index,
					GRID_X + col * 18, GRID_Y + row * 18));
			}
		}
	}

	private void addPlayerInventory() {
		// 主背包 3 行(原版索引 9..35)
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				final int index = col + row * 9 + 9;
				this.addSlot(this.playerSlotAt(index,
					INVENTORY_X + col * 18, INVENTORY_MAIN_Y + row * 18));
			}
		}

		// 快捷栏 1 行(原版索引 0..8)
		for (int col = 0; col < 9; col++) {
			this.addSlot(this.playerSlotAt(col,
				INVENTORY_X + col * 18, INVENTORY_HOTBAR_Y));
		}
	}

	/**
	 * 玩家物品栏的槽位 —— <b>如果是"正被打开的那个背包"所在格,就换成锁住的</b>。
	 *
	 * <p>不锁的后果:玩家能把它拖到别的格子,而界面记的还是原来那一格,
	 * 于是读到不相干的物品(内容、格数全乱)。
	 */
	private Slot playerSlotAt(final int inventoryIndex, final int x, final int y) {
		if (inventoryIndex == this.lockedInventoryIndex) {
			return new LockedSlot(this.playerInventory, inventoryIndex, x, y);
		}
		return new Slot(this.playerInventory, inventoryIndex, x, y);
	}

	/** 算出要锁哪一格。见 {@link #lockedInventoryIndex}。 */
	private static int computeLockedIndex(final Inventory inventory, final BackpackOpenData data) {
		if (data.isBlock()) {
			return -1;      // 方块形态:背包不在物品栏里
		}

		final int source = data.source();
		if (source == BackpackOpenData.SOURCE_CHEST) {
			return -1;      // 胸甲栏不显示在这个界面里,也拖不走
		}
		if (source == BackpackOpenData.SOURCE_MAIN_HAND) {
			// 兜底:万一有人还按"主手"开界面,就锁打开时选中的那一格
			return inventory.getSelectedSlot();
		}
		// 其余就是物品栏索引本身
		return source >= 0 && source < inventory.getContainerSize() ? source : -1;
	}

	/** 供界面读取的背包容器。 */
	public BackpackContainer backpackContainer() {
		return this.container;
	}

	/** 背包内容对应的那个物品。 */
	public ItemStack backpackStack() {
		return this.currentBackpack();
	}

	/**
	 * 已解锁格数 —— <b>走同步通道读,不从物品读</b>。
	 *
	 * <p>⚠️ 这一点对<b>方块形态</b>是必须的:客户端那份方块实体里的背包物品
	 * 拿不到服务端的真实格数,只有 {@code ContainerData} 是准的。
	 * 渲染判定(哪些格解锁、挂饰槽开不开)都要走这里。
	 */
	public int unlockedSlots() {
		return this.state.get(DATA_UNLOCKED);
	}

	/** 当前饱食度(0 ~ {@link BackpackGrowth#MAX_SATIETY})。 */
	public int satiety() {
		return this.currentBackpack().getOrDefault(ModComponents.SATIETY, 0);
	}

	/**
	 * 当前阶段(1 ~ 4) —— <b>读那个独立存的组件,不从格数算</b>。
	 *
	 * <p>(用户 2026-10-15 指出:用格数算阶段会在"每行格子数变了"之后全错。)
	 */
	public int stage() {
		return this.state.get(DATA_STAGE);
	}

	/**
	 * Shift + 左键 = 一键转移(背包 ↔ 玩家物品栏)。
	 *
	 * <h2>两处"不用自己写"的安全保障</h2>
	 * <ul>
	 *   <li><b>未解锁的格子自动被跳过</b> —— {@code moveItemStackTo} 会逐个问
	 *       {@code slot.mayPlace(...)},而 {@link BackpackSlot#mayPlace} 对锁着的格子返回 false。</li>
	 *   <li><b>背包/潜影盒之类进不去</b> —— 同一个钩子还会问
	 *       {@code Item.canFitInsideContainerItems()},本 mod 的背包对它返回 false。</li>
	 * </ul>
	 */
	@Override
	public ItemStack quickMoveStack(final Player player, final int slotIndex) {
		final Slot slot = this.slots.get(slotIndex);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}

		final ItemStack stack = slot.getItem();
		final ItemStack original = stack.copy();

		if (slotIndex < GRID_SLOTS) {
			// 背包 → 玩家物品栏(从后往前找空位,先塞快捷栏)
			if (!this.moveItemStackTo(stack, GRID_SLOTS, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else {
			// 玩家物品栏 → 背包(只进已解锁的格,由 mayPlace 兜底)
			if (!this.moveItemStackTo(stack, 0, GRID_SLOTS, false)) {
				return ItemStack.EMPTY;
			}
		}

		if (stack.isEmpty()) {
			slot.set(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}

		return original;
	}

	/**
	 * 背包界面里<b>禁用数字键换位</b>({@link ContainerInput#SWAP})。
	 *
	 * <h2>为什么整个禁掉,而不是逐条堵</h2>
	 * 数字键(1~9,以及 40 = 副手)的语义是"把鼠标悬停的槽位与快捷栏某一格交换"。
	 * 它走的是 {@code Slot.setByPlayer(...)},而这条路径<b>不查 {@code mayPlace}</b> ——
	 * 想靠重写槽位方法堵住它,得逐条列举,既容易漏(实测已经漏过两次:
	 * 先是"能切走",再是"别的物品能切进来"),代码也难读。
	 *
	 * <p>而它在背包界面里的<b>价值本来就低</b>:有 <b>Shift + 左键</b>一键转移,
	 * 来回搬东西比数字键顺手得多。所以一刀切更可靠。
	 *
	 * <p>⚠️ 只拦 SWAP —— 拿取、放下、Shift 转移、Q 丢弃、拖拽都照常。
	 */
	@Override
	public void clicked(
		final int slotIndex,
		final int buttonNum,
		final ContainerInput input,
		final Player player
	) {
		if (input == ContainerInput.SWAP) {
			return;
		}
		super.clicked(slotIndex, buttonNum, input, player);
	}

	@Override
	public boolean stillValid(final Player player) {
		return true;
	}

	// ⚠️ 刻意【不覆写】removed():
	// 内容存在物品/方块实体的组件上,关界面就该留着(工作区约定坑 19)。
}

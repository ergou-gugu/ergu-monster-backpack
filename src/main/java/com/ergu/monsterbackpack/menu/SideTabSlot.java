package com.ergu.monsterbackpack.menu;

/**
 * 侧签弹窗里的功能槽(喂食 / 挂饰 / 砂囊 …)。
 *
 * <h2>⚠️ 为什么用接口 + 坐标隐藏,而不是 {@code isActive()}</h2>
 * 这是参考两个成熟 mod 后的结论(见 {@code refs/背包界面研究报告.md} §3、§7):
 * <b>精妙背包</b>用 {@code slot.x = -2000}、<b>旅行者背包</b>用 {@code slot.y = -1000}
 * —— <b>两家都不靠 {@code isActive()} 表示"隐藏"</b>,而是把坐标直接踢出屏幕。
 *
 * <p>原来我们用 {@code isActive()} 门控,有两个隐患:
 * <ol>
 *   <li>绘制时要为每种槽写一个 {@code instanceof} 分支("这个槽该不该画"),很啰嗦</li>
 *   <li>⚠️ <b>更危险的</b>:一旦哪天把 {@code isActive()} 改成"服务端恒真"
 *       (DESIGN.md 里曾有个待验证项 V1 就是这么想的),
 *       <b>关闭的签槽就会立刻变成"看不见但能点到"的陷阱</b> —— 手里的东西会掉进去。
 *       坐标隐藏没有这个隐患:<b>坐标出屏 = 原版自己也判不到</b>。</li>
 * </ol>
 *
 * <p>所以现在:<b>"这个槽在哪"是一个可查询的显式状态</b>,绘制和命中都只看坐标。
 */
public interface SideTabSlot {

	/** 这个槽属于哪个侧签(取 {@link BackpackMenu} 的 {@code TAB_*} 常量)。 */
	int tabId();

	/**
	 * 它"正常"时的 x(相对面板)。
	 *
	 * <p>打开对应的签时会被挪回这里;关上时被挪到 {@link BackpackMenu#HIDDEN_SLOT_X}。
	 */
	int homeX();
}

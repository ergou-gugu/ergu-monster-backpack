package com.ergu.monsterbackpack.menu;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

import com.ergu.monsterbackpack.ErguMonsterBackpack;

/**
 * 容器类型注册(<b>双端</b>都要跑)。
 *
 * <h2>26.2 的两处要点(勿套旧经验)</h2>
 * <ul>
 *   <li>容器注册表是 <b>{@code BuiltInRegistries.MENU}</b>(不是 {@code Registries.MENU})。</li>
 *   <li>Fabric 的模块与类名都变了:模块是 <b>{@code fabric-menu-api-v1}</b>(不是 screen),
 *       类在包 <b>{@code net.fabricmc.fabric.api.menu.v1}</b>,
 *       旧名 {@code ExtendedScreenHandlerFactory} 现在叫
 *       {@link net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider}。</li>
 * </ul>
 *
 * <p>本类型的 {@code D} = {@link BackpackOpenData} —— 开界面时告诉客户端"背包在哪"。
 */
public final class ModMenus {

	public static final ExtendedMenuType<BackpackMenu, BackpackOpenData> BACKPACK = Registry.register(
		BuiltInRegistries.MENU,
		ErguMonsterBackpack.id("backpack"),
		new ExtendedMenuType<BackpackMenu, BackpackOpenData>(
			BackpackMenu::new,
			BackpackOpenData.STREAM_CODEC
		)
	);

	private ModMenus() {
	}

	/** 空方法,唯一作用是触发本类的静态初始化;注册写在这个类的静态字段上。 */
	public static void bootstrap() {
	}
}

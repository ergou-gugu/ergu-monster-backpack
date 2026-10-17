package com.ergu.monsterbackpack.client;

import net.fabricmc.api.ClientModInitializer;

import net.minecraft.client.gui.screens.MenuScreens;

import com.ergu.monsterbackpack.ErguMonsterBackpack;
import com.ergu.monsterbackpack.menu.ModMenus;

/**
 * 客户端入口。
 *
 * <p>只有这里能碰纯客户端的类:{@code Screen}(界面)、{@code KeyMapping}(按键)、
 * {@code MenuScreens}(界面注册)。两端共用的代码一律放 {@code src/main}。
 *
 * <p><b>⚠️ 分工</b>:容器<b>类型</b>在双端注册({@code ModMenus},放 {@code src/main});
 * 容器对应的<b>界面</b>只能在这里注册 —— {@code MenuScreens} 是纯客户端类。
 */
public class ErguMonsterBackpackClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// 容器 → 界面。缺这一句的表现是:能打开容器但没有任何界面(或者直接崩)。
		MenuScreens.register(ModMenus.BACKPACK, BackpackScreen::new);

		ModKeyMappings.register();

		registerTooltipComponent();

		ErguMonsterBackpack.LOGGER.info("Ergu's Monster Backpack client initialized.");
	}

	/**
	 * 把背包的 tooltip 数据组件接到渲染组件上。
	 *
	 * <p><b>双端配对</b>:数据在 {@code BackpackTooltipComponent}(src/main),
	 * 渲染在 {@code ClientBackpackTooltipComponent}(src/client)——
	 * 这里是把两者连起来的地方。缺这一句的表现是 tooltip 里什么都不显示。
	 */
	private static void registerTooltipComponent() {
		net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback.EVENT.register(
			data -> data instanceof com.ergu.monsterbackpack.item.BackpackTooltipComponent backpack
				? new ClientBackpackTooltipComponent(backpack)
				: null);
	}
}

package com.shouyun.lastbet.client;

import net.fabricmc.api.ClientModInitializer;
import com.shouyun.lastbet.client.screen.BankScreen;
import com.shouyun.lastbet.registry.BankRegistry;
import net.minecraft.client.gui.screens.MenuScreens;

public class TheLastBetClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(BankRegistry.BANK_MENU, BankScreen::new);
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				com.shouyun.lastbet.menu.BankNetworking.Snapshot.TYPE, (payload, context) -> {
			if (context.player().containerMenu instanceof com.shouyun.lastbet.menu.BankMenu menu
					&& menu.containerId == payload.menuId()) menu.acceptSnapshot(payload.data());
		});
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
	}
}

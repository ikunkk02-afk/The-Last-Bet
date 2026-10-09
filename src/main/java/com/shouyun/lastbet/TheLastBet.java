package com.shouyun.lastbet;

import net.fabricmc.api.ModInitializer;
import com.shouyun.lastbet.bank.BankManager;
import com.shouyun.lastbet.registry.BankRegistry;

import net.minecraft.resources.ResourceLocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TheLastBet implements ModInitializer {
	public static final String MOD_ID = "lastbet";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		BankRegistry.initialize();
		BankManager.initialize();
		LOGGER.info("The Last Bet: bank registration initialized");
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}

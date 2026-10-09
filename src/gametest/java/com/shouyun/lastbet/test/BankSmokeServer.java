// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.test;

import com.shouyun.lastbet.bank.BankManager;
import com.shouyun.lastbet.registry.BankRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Local-only test fixture, excluded from the distributable JAR. */
public final class BankSmokeServer implements ModInitializer {
    @Override public void onInitialize() {
        if (!Boolean.getBoolean("lastbet.smoke.server")) return;
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            if (!player.getGameProfile().getName().startsWith("BankSmoke")) return;
            server.getPlayerList().op(player.getGameProfile());
            position(player);
            if (player.getGameProfile().getName().equals("BankSmokeB")
                    && BankManager.get(server).findByOwner(player.getUUID()).isEmpty()) {
                for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            }
            player.inventoryMenu.broadcastChanges();
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, player, alive) -> position(player));
    }

    private static void position(ServerPlayer player) {
        var level = player.server.overworld();
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 4; z++) {
            level.setBlockAndUpdate(new BlockPos(x, 79, z), Blocks.STONE.defaultBlockState());
            for (int y = 80; y <= 83; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
        }
        level.setBlockAndUpdate(new BlockPos(0, 80, 0), BankRegistry.BANK_COUNTER.defaultBlockState());
        player.teleportTo(level, 0.5, 80, 2.5, 180, 0);
    }
}

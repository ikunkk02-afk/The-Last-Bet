// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.test;

import com.shouyun.lastbet.bank.*;
import com.shouyun.lastbet.client.screen.BankScreen;
import com.shouyun.lastbet.menu.*;
import com.shouyun.lastbet.registry.BankRegistry;
import java.nio.file.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

final class SingleplayerBankSmoke {
    private int step, age;
    private volatile CompoundTag before;
    private String account;
    private String card;
    private static final String WORLD = "phase2-singleplayer";
    private void next(int value) { step = value; age = 0; }
    void tick(Minecraft c) throws Exception {
        age++;
        if (age > 2400) throw new IllegalStateException("Singleplayer test timed out at " + step);
        if (c.getOverlay() != null) return;
        if (step == 0 && c.screen instanceof TitleScreen && age > 60) {
            c.createWorldOpenFlows().openWorld(WORLD, () -> c.setScreen(new TitleScreen())); next(1);
        } else if ((step == 1 || step == 8) && c.player != null && c.getSingleplayerServer() != null && age > 40) {
            boolean initial = step == 1; var server = c.getSingleplayerServer(); var id = c.player.getUUID();
            server.execute(() -> {
                var p = server.getPlayerList().getPlayer(id); var level = p.serverLevel();
                for (int x = -2; x <= 2; x++) for (int z = -2; z <= 4; z++) {
                    level.setBlockAndUpdate(new BlockPos(x, 79, z), Blocks.STONE.defaultBlockState());
                    for (int y = 80; y <= 83; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
                level.setBlockAndUpdate(new BlockPos(0, 80, 0), BankRegistry.BANK_COUNTER.defaultBlockState());
                p.teleportTo(level, 0.5, 80, 2.5, 180, 0);
                if (initial) { p.getInventory().clearContent(); p.getInventory().setItem(1, new ItemStack(Items.EMERALD, 64)); }
                BankMenu.open(p, new BlockPos(0, 80, 0));
            }); next(initial ? 2 : 9);
        } else if (step == 2 && age > 20 && c.screen instanceof BankScreen s) {
            check(!s.getMenu().data().hasAccount(), "Fixture owner already has account"); send(c, BankAction.REGISTER); next(3);
        } else if (step == 3 && age > 20 && c.screen instanceof BankScreen s && s.getMenu().data().hasAccount()) {
            account = s.getMenu().data().accountId();
            for (int i = 0; i < 36; i++) if (c.player.getInventory().getItem(i).is(BankRegistry.BANK_CARD)) card = c.player.getInventory().getItem(i).get(BankRegistry.CARD_DATA).cardId().toString();
            var server = c.getSingleplayerServer(); var id = c.player.getUUID(); server.execute(() -> before = server.getPlayerList().getPlayer(id).saveWithoutId(new CompoundTag())); next(4);
        } else if (step == 4 && before != null && age > 10) { send(c, BankAction.DEPOSIT_PAGE); next(5); }
        else if (step == 5 && age > 10 && c.screen instanceof BankScreen s && s.getMenu().data().page() == BankMenuData.DEPOSIT) {
            send(c, BankAction.DEPOSIT_16); next(6);
        } else if (step == 6 && age > 20 && c.screen instanceof BankScreen s && s.getMenu().data().balance() == 16) {
            check(s.getMenu().data().emeralds() == 48 && s.getMenu().data().historySize() == 1, "Initial singleplayer transaction mismatch");
            step = 98; c.level.disconnect(); c.disconnect(new TitleScreen()); next(7);
        } else if (step == 7 && age > 20 && c.screen instanceof TitleScreen && !c.hasSingleplayerServer()) {
            Path level = c.gameDirectory.toPath().resolve("saves").resolve(WORLD).resolve("level.dat");
            CompoundTag root = BankPersistence.read(level); root.getCompound("Data").put("Player", before.copy()); BankPersistence.writeAtomic(level, root);
            c.createWorldOpenFlows().openWorld(WORLD, () -> c.setScreen(new TitleScreen())); next(8);
        } else if (step == 9 && age > 20 && c.screen instanceof BankScreen s) {
            var d = s.getMenu().data(); check(d.accountId().equals(account) && d.balance() == 16 && d.emeralds() == 48 && d.historySize() == 1, "Stale level.dat overrode canonical player data");
            boolean sameCard = false;
            for (int i = 0; i < 36; i++) if (c.player.getInventory().getItem(i).is(BankRegistry.BANK_CARD)) sameCard |= card.equals(c.player.getInventory().getItem(i).get(BankRegistry.CARD_DATA).cardId().toString());
            check(sameCard, "Singleplayer reload changed card UUID");
            step = 98; c.level.disconnect(); c.disconnect(new TitleScreen());
            Files.writeString(c.gameDirectory.toPath().resolve("singleplayer-passed.json"), "{\"passed\":true,\"stale_level_player_verified\":true,\"balance\":16,\"emeralds\":48,\"history\":1}");
            Files.writeString(c.gameDirectory.toPath().resolve("smoke-completed.txt"), "singleplayer canonical player data verified"); next(99); c.stop();
        }
    }
    private static void send(Minecraft c, BankAction action) {
        var menu = ((BankScreen) c.screen).getMenu(); var d = menu.data();
        ClientPlayNetworking.send(new BankNetworking.Request(menu.containerId, d.session(), d.token(), action, 0));
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}

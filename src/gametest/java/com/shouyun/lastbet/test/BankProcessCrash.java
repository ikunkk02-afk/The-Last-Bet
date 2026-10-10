// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.test;

import com.mojang.authlib.GameProfile;
import com.shouyun.lastbet.bank.*;
import com.shouyun.lastbet.menu.BankMenu;
import com.shouyun.lastbet.registry.BankRegistry;
import java.nio.file.Files;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;

/** Abrupt process termination and fresh-JVM recovery fixture, never shipped. */
public final class BankProcessCrash {
    private static final UUID OWNER = UUID.fromString("6216957a-a057-4be9-b6af-875ce89b6a96");
    public static void install() {
        String mode = System.getProperty("lastbet.test.crashMode");
        if (mode == null) return;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                var profile = new GameProfile(OWNER, "CrashFixture");
                var p = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
                p.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), p, CommonListenerCookie.createInitial(profile, false));
                var manager = BankManager.get(server);
                if (mode.equals("crash")) {
                    BlockPos pos = new BlockPos(0, 80, 0); server.overworld().setBlockAndUpdate(pos, BankRegistry.BANK_COUNTER.defaultBlockState());
                    p.setPos(0.5, 81, 0.5); BankMenu.open(p, pos);
                    if (manager.register(p) != BankManager.Result.OPENED) throw new IllegalStateException("Crash fixture could not open account");
                    p.getInventory().setItem(1, new ItemStack(Items.EMERALD, 64));
                    manager.transact(p, BankTransaction.Type.DEPOSIT, 16);
                    throw new IllegalStateException("Requested halt point was not reached");
                } else {
                    var file = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(OWNER + ".dat");
                    var canonical = manager.transactions().admission(OWNER);
                    p.load(canonical == null ? BankPersistence.read(file) : canonical);
                    boolean beforePrepare = System.getProperty("lastbet.test.crashPoint").equals("PREPARE_BEFORE");
                    long expectedBalance = beforePrepare ? 0 : 16, expectedEmeralds = beforePrepare ? 64 : 48;
                    var account = manager.findByOwner(OWNER).orElseThrow();
                    if (account.balance() != expectedBalance || EmeraldInventory.count(p) != expectedEmeralds
                            || manager.history(OWNER).size() != (beforePrepare ? 0 : 1)) throw new IllegalStateException("Crash recovery did not conserve assets");
                    var result = new com.google.gson.JsonObject();
                    result.addProperty("point", System.getProperty("lastbet.test.crashPoint")); result.addProperty("balance", account.balance());
                    result.addProperty("emeralds", EmeraldInventory.count(p)); result.addProperty("history", manager.history(OWNER).size());
                    result.addProperty("account", account.accountId().toString()); result.addProperty("passed", true);
                    Files.writeString(server.getWorldPath(LevelResource.ROOT).resolve("../crash-recovered.json").normalize(), result.toString());
                    System.out.println("LASTBET_PROCESS_RECOVERY_PASSED " + result);
                    server.execute(() -> server.halt(false));
                }
            } catch (Exception failure) {
                failure.printStackTrace(); Runtime.getRuntime().halt(87);
            }
        });
    }
}

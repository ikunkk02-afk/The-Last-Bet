// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.bank;

import com.shouyun.lastbet.item.BankCardData;
import com.shouyun.lastbet.registry.BankRegistry;
import com.shouyun.lastbet.test.BankGameTests;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;

public final class BankRecoveryGameTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void recoversPendingCardWithoutCreatingAnotherAccount(GameTestHelper helper) throws Exception {
        var player = BankGameTests.player(helper);
        BankGameTests.menu(helper, player);
        var server = player.server;
        var manager = BankManager.get(server);
        var data = server.overworld().getDataStorage().get(BankSavedData.FACTORY, BankSavedData.NAME);
        var account = data.create(player.getUUID());
        data.saveChecked(server.getWorldPath(LevelResource.ROOT).resolve("data/lastbet_bank.dat"), server.registryAccess());
        helper.assertTrue(manager.register(player) == BankManager.Result.OPENED, "Pending issuance resumes");
        helper.assertTrue(manager.findByOwner(player.getUUID()).orElseThrow().accountId().equals(account.accountId()), "Same account ID retained");
        helper.assertTrue(player.getInventory().getItem(0).get(BankRegistry.CARD_DATA).cardId().equals(account.cardId()), "Same card ID retained");
        helper.assertTrue(manager.register(player) == BankManager.Result.ALREADY_EXISTS, "Completed issuance cannot repeat");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void recoversAlreadyDeliveredPendingCardEvenWithFullInventory(GameTestHelper helper) throws Exception {
        var player = BankGameTests.player(helper);
        BankGameTests.menu(helper, player);
        var server = player.server;
        var manager = BankManager.get(server);
        var data = server.overworld().getDataStorage().get(BankSavedData.FACTORY, BankSavedData.NAME);
        var account = data.create(player.getUUID());
        var card = new ItemStack(BankRegistry.BANK_CARD);
        card.set(BankRegistry.CARD_DATA, new BankCardData(account.ownerId(), account.accountId(), account.cardId(), "BankTest"));
        for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        player.getInventory().setItem(0, card);
        data.saveChecked(server.getWorldPath(LevelResource.ROOT).resolve("data/lastbet_bank.dat"), server.registryAccess());
        helper.assertTrue(manager.register(player) == BankManager.Result.OPENED, "Existing issued card completes pending delivery");
        helper.assertTrue(!manager.findByOwner(player.getUUID()).orElseThrow().deliveryPending(), "Pending cleared");
        helper.assertTrue(player.getInventory().getItem(0).getCount() == 1 && player.getInventory().getItem(1).is(Items.STONE), "No duplicate or replaced item");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void rejectsSpectatorAndDeadPlayer(GameTestHelper helper) {
        var player = BankGameTests.player(helper);
        BankGameTests.menu(helper, player);
        var manager = BankManager.get(player.server);
        player.setGameMode(GameType.SPECTATOR);
        helper.assertTrue(manager.register(player) == BankManager.Result.INVALID_REQUEST, "Spectator rejected");
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(0);
        helper.assertTrue(manager.register(player) == BankManager.Result.INVALID_REQUEST, "Dead player rejected");
        helper.assertTrue(manager.findByOwner(player.getUUID()).isEmpty(), "No account created");
        helper.succeed();
    }
}

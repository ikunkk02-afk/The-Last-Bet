// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.test;

import com.mojang.authlib.GameProfile;
import com.shouyun.lastbet.bank.BankManager;
import com.shouyun.lastbet.bank.BankPersistence;
import com.shouyun.lastbet.bank.BankSavedData;
import com.shouyun.lastbet.item.BankCardData;
import com.shouyun.lastbet.menu.BankMenu;
import com.shouyun.lastbet.menu.BankMenuData;
import com.shouyun.lastbet.registry.BankRegistry;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class BankGameTests implements FabricGameTest {
    private static final BlockPos COUNTER = new BlockPos(2, 2, 2);

    @GameTest(template = EMPTY_STRUCTURE)
    public void opensAccountIssuesOneCardAndRejectsDuplicate(GameTestHelper helper) throws Exception {
        ServerPlayer player = player(helper);
        BankManager manager = BankManager.get(helper.getLevel().getServer());
        helper.assertTrue(!BankMenuData.snapshot(player, BankManager.Result.NONE).hasAccount(), "New player has no account");
        menu(helper, player);
        helper.assertTrue(player.containerMenu.clickMenuButton(player, BankMenu.REGISTER_BUTTON), "Button handled");
        var account = manager.findByOwner(player.getUUID()).orElseThrow();
        helper.assertTrue(account.balance() == 0 && !account.deliveryPending(), "Zero balance and card delivered");
        helper.assertTrue(cards(player) == 1 && manager.isValidCard(player, player.getInventory().getItem(0)), "Exactly one valid card");
        var id = account.accountId();
        player.containerMenu.clickMenuButton(player, BankMenu.REGISTER_BUTTON);
        helper.assertTrue(manager.findByOwner(player.getUUID()).orElseThrow().accountId().equals(id), "Duplicate preserves account ID");
        helper.assertTrue(cards(player) == 1, "Duplicate does not issue another card");
        var path = player.server.getWorldPath(LevelResource.ROOT).resolve("data/lastbet_bank.dat");
        var reloaded = BankSavedData.load(BankPersistence.read(path).getCompound("data"), player.server.registryAccess());
        helper.assertTrue(reloaded.findByOwner(player.getUUID()).orElseThrow().equals(account), "Actual disk readback matches");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void fullInventoryDoesNotCreateAccount(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        menu(helper, player);
        for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        BankManager manager = BankManager.get(player.server);
        helper.assertTrue(manager.register(player) == BankManager.Result.INVENTORY_FULL, "Full inventory rejected");
        helper.assertTrue(manager.findByOwner(player.getUUID()).isEmpty(), "Full inventory creates no account");
        player.getInventory().setItem(5, ItemStack.EMPTY);
        helper.assertTrue(manager.register(player) == BankManager.Result.OPENED, "Retry after freeing a slot succeeds");
        helper.assertTrue(manager.isValidCard(player, player.getInventory().getItem(5)), "Card occupies free slot");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void invalidMenusDistanceAndRemovedCounterRejectRequests(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        BankManager manager = BankManager.get(player.server);
        helper.assertTrue(manager.register(player) == BankManager.Result.INVALID_REQUEST, "No menu rejected");
        menu(helper, player);
        BankMenu original = (BankMenu) player.containerMenu;
        helper.assertTrue(!original.clickMenuButton(player, 999), "Unknown operation rejected");
        player.setPos(player.getX() + 12, player.getY(), player.getZ());
        helper.assertTrue(manager.register(player) == BankManager.Result.INVALID_REQUEST, "Distant request rejected");
        menu(helper, player);
        helper.setBlock(COUNTER, Blocks.AIR);
        helper.assertTrue(manager.register(player) == BankManager.Result.INVALID_REQUEST, "Removed counter rejected");
        menu(helper, player);
        player.containerMenu = player.inventoryMenu;
        helper.assertTrue(!original.clickMenuButton(player, 0), "Stale menu rejected");
        helper.assertTrue(manager.findByOwner(player.getUUID()).isEmpty(), "Invalid requests created no account");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void separatePlayersCannotUseEachOthersCards(GameTestHelper helper) {
        ServerPlayer first = player(helper);
        ServerPlayer second = player(helper);
        menu(helper, first);
        menu(helper, second);
        BankManager manager = BankManager.get(first.server);
        helper.assertTrue(manager.register(first) == BankManager.Result.OPENED, "First opened");
        helper.assertTrue(manager.register(second) == BankManager.Result.OPENED, "Second opened");
        var a = manager.findByOwner(first.getUUID()).orElseThrow();
        var b = manager.findByOwner(second.getUUID()).orElseThrow();
        helper.assertTrue(!a.accountId().equals(b.accountId()) && !a.cardId().equals(b.cardId()), "Players have distinct IDs");
        ItemStack card = first.getInventory().getItem(0);
        helper.assertTrue(!manager.isValidCard(second, card), "Wrong holder rejected");
        helper.assertTrue(!manager.isValidCard(first, new ItemStack(BankRegistry.BANK_CARD)), "Unbound card rejected");
        ItemStack forged = card.copy();
        forged.set(BankRegistry.CARD_DATA, new BankCardData(a.ownerId(), a.accountId(), UUID.randomUUID(), "Forged"));
        helper.assertTrue(!manager.isValidCard(first, forged), "Unregistered card UUID rejected");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void counterHasFourDirectionsAndDropsItself(GameTestHelper helper) {
        var state = BankRegistry.BANK_COUNTER.defaultBlockState();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            state = state.setValue(HorizontalDirectionalBlock.FACING, direction);
            helper.setBlock(COUNTER, state);
            helper.assertTrue(helper.getBlockState(COUNTER).getValue(HorizontalDirectionalBlock.FACING) == direction, "Facing retained");
            helper.assertTrue(state.rotate(Rotation.CLOCKWISE_90).getValue(HorizontalDirectionalBlock.FACING) == direction.getClockWise(), "Rotation correct");
        }
        var drops = Block.getDrops(state, helper.getLevel(), helper.absolutePos(COUNTER), null);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().is(BankRegistry.BANK_COUNTER_ITEM), "Counter drops itself");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void deathAndReloadDoNotDeleteAccount(GameTestHelper helper) throws Exception {
        ServerPlayer player = player(helper);
        menu(helper, player);
        BankManager manager = BankManager.get(player.server);
        helper.assertTrue(manager.register(player) == BankManager.Result.OPENED, "Opened before death");
        var account = manager.findByOwner(player.getUUID()).orElseThrow();
        player.setHealth(0);
        player.die(player.damageSources().generic());
        helper.assertTrue(manager.findByOwner(player.getUUID()).orElseThrow().equals(account), "Death preserves account");
        var path = player.server.getWorldPath(LevelResource.ROOT).resolve("data/lastbet_bank.dat");
        var loaded = BankSavedData.load(BankPersistence.read(path).getCompound("data"), player.server.registryAccess());
        helper.assertTrue(loaded.findByOwner(player.getUUID()).orElseThrow().equals(account), "Disk reload preserves account");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void placesCounterFacingPlayerInAllFourDirections(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        player.setGameMode(GameType.CREATIVE);
        BlockPos absolute = helper.absolutePos(COUNTER);
        helper.setBlock(COUNTER.below(), Blocks.STONE);
        player.setPos(absolute.getX() + 0.5, absolute.getY() + 1, absolute.getZ() + 0.5);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            helper.setBlock(COUNTER, Blocks.AIR);
            player.setYRot(direction.toYRot());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BankRegistry.BANK_COUNTER_ITEM));
            var hit = new BlockHitResult(Vec3.atBottomCenterOf(absolute), Direction.UP, absolute.below(), false);
            BankRegistry.BANK_COUNTER_ITEM.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            var placed = helper.getBlockState(COUNTER);
            helper.assertTrue(placed.is(BankRegistry.BANK_COUNTER), "Bank counter placed by BlockItem");
            helper.assertTrue(placed.getValue(HorizontalDirectionalBlock.FACING) == direction.getOpposite(), "Counter faces placing player");
        }
        helper.succeed();
    }

    public static ServerPlayer player(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "BankTest");
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), profile, ClientInformation.createDefault());
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(profile, false));
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    public static void menu(GameTestHelper helper, ServerPlayer player) {
        helper.setBlock(COUNTER, BankRegistry.BANK_COUNTER);
        BlockPos pos = helper.absolutePos(COUNTER);
        player.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        player.containerMenu = new BankMenu(1, BankMenuData.snapshot(player, BankManager.Result.NONE), helper.getLevel(), pos);
    }

    private static int cards(ServerPlayer player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(BankRegistry.BANK_CARD)) count += stack.getCount();
        }
        return count;
    }
}

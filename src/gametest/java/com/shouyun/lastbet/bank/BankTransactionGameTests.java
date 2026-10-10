// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.bank;

import com.shouyun.lastbet.menu.*;
import com.shouyun.lastbet.test.BankGameTests;
import com.shouyun.lastbet.test.TransactionFaults;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;

public final class BankTransactionGameTests implements FabricGameTest {
    private static BankMenu menu(ServerPlayer p) { return (BankMenu) p.containerMenu; }
    private static BankNetworking.Request request(ServerPlayer p, BankAction action, int page) {
        var m = menu(p); return new BankNetworking.Request(m.containerId, m.data().session(), m.data().token(), action, page);
    }
    private static void action(ServerPlayer p, BankAction action) { menu(p).handle(p, request(p, action, 0)); }
    private static ServerPlayer registered(GameTestHelper h) {
        var p = BankGameTests.player(h); BankGameTests.menu(h, p);
        h.assertTrue(BankManager.get(p.server).register(p) == BankManager.Result.OPENED, "Account opened");
        BankGameTests.menu(h, p); return p;
    }
    private static long balance(ServerPlayer p) { return BankManager.get(p.server).findByOwner(p.getUUID()).orElseThrow().balance(); }

    @GameTest(template = EMPTY_STRUCTURE)
    public void allEightAmountsConserveAssetsAndLedger(GameTestHelper h) {
        var p = registered(h);
        p.getInventory().setItem(1, new ItemStack(Items.EMERALD, 64));
        p.getInventory().setItem(2, new ItemStack(Items.EMERALD, 64));
        p.getInventory().setItem(3, new ItemStack(Items.EMERALD, 64));
        for (BankAction a : new BankAction[]{BankAction.DEPOSIT_1, BankAction.DEPOSIT_16, BankAction.DEPOSIT_64, BankAction.DEPOSIT_ALL}) {
            action(p, BankAction.DEPOSIT_PAGE); action(p, a);
            h.assertTrue(menu(p).data().message() == BankManager.Result.DEPOSITED, "Deposit succeeded: " + a);
            h.assertTrue(EmeraldInventory.count(p) + balance(p) == 192, "Deposit conservation");
            h.assertTrue(menu(p).data().emeralds() == EmeraldInventory.count(p), "Immediate count refresh");
        }
        h.assertTrue(balance(p) == 192 && EmeraldInventory.count(p) == 0, "Deposit all exact");
        for (BankAction a : new BankAction[]{BankAction.WITHDRAW_1, BankAction.WITHDRAW_16, BankAction.WITHDRAW_64, BankAction.WITHDRAW_ALL}) {
            action(p, BankAction.WITHDRAWAL_PAGE); action(p, a);
            h.assertTrue(menu(p).data().message() == BankManager.Result.WITHDRAWN, "Withdrawal succeeded: " + a);
            h.assertTrue(EmeraldInventory.count(p) + balance(p) == 192, "Withdrawal conservation");
        }
        h.assertTrue(balance(p) == 0 && BankManager.get(p.server).history(p.getUUID()).size() == 8, "Eight completed ledger entries");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void failuresDoNotMutateAssetsOrRecordSuccess(GameTestHelper h) {
        var p = registered(h); action(p, BankAction.WITHDRAWAL_PAGE); action(p, BankAction.WITHDRAW_1);
        h.assertTrue(menu(p).data().message() == BankManager.Result.INSUFFICIENT_BALANCE, "Insufficient balance");
        action(p, BankAction.WITHDRAW_ALL); h.assertTrue(menu(p).data().message() == BankManager.Result.EMPTY_AMOUNT, "Zero withdrawal refused");
        p.getInventory().setItem(1, new ItemStack(Items.EMERALD, 15));
        action(p, BankAction.DEPOSIT_PAGE); action(p, BankAction.DEPOSIT_16);
        h.assertTrue(menu(p).data().message() == BankManager.Result.NOT_ENOUGH_EMERALDS, "Insufficient emeralds");
        h.assertTrue(EmeraldInventory.count(p) == 15 && balance(p) == 0 && BankManager.get(p.server).history(p.getUUID()).isEmpty(), "Failures unchanged");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void fullInventoryMergesStacksAndAllWithdrawalNeverBecomesPartial(GameTestHelper h) {
        var p = registered(h); p.getInventory().setItem(1, new ItemStack(Items.EMERALD, 64));
        action(p, BankAction.DEPOSIT_PAGE); action(p, BankAction.DEPOSIT_ALL);
        for (int i = 1; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        action(p, BankAction.WITHDRAWAL_PAGE); action(p, BankAction.WITHDRAW_1);
        h.assertTrue(menu(p).data().message() == BankManager.Result.NO_SPACE && balance(p) == 64, "Full inventory rejects");
        var named = new ItemStack(Items.EMERALD, 48);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Named emerald"));
        p.getInventory().setItem(1, named); action(p, BankAction.WITHDRAW_16);
        h.assertTrue(menu(p).data().message() == BankManager.Result.NO_SPACE && named.getCount() == 48 && balance(p) == 64, "Incompatible components cannot merge");
        p.getInventory().setItem(1, new ItemStack(Items.EMERALD, 48));
        action(p, BankAction.WITHDRAW_16);
        h.assertTrue(p.getInventory().getItem(1).getCount() == 64 && balance(p) == 48, "Uses existing stack capacity");
        p.getInventory().setItem(1, new ItemStack(Items.EMERALD, 63));
        action(p, BankAction.WITHDRAW_ALL);
        h.assertTrue(menu(p).data().message() == BankManager.Result.NO_SPACE && balance(p) == 48 && EmeraldInventory.count(p) == 63, "All cannot silently withdraw one");
        h.assertTrue(BankManager.get(p.server).history(p.getUUID()).size() == 2, "Only successful entries"); h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void excludesOffhandArmorBlocksAndNestedContainers(GameTestHelper h) {
        var p = registered(h);
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.EMERALD, 64));
        p.getInventory().setItem(36, new ItemStack(Items.EMERALD, 64));
        p.getInventory().setItem(1, new ItemStack(Items.EMERALD_BLOCK, 64));
        p.getInventory().setItem(2, new ItemStack(Items.DIAMOND, 64));
        var box = new ItemStack(Items.SHULKER_BOX);
        box.set(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(java.util.List.of(new ItemStack(Items.EMERALD, 64))));
        p.getInventory().setItem(3, box);
        p.getInventory().setItem(4, new ItemStack(Items.EMERALD, 16));
        action(p, BankAction.DEPOSIT_PAGE); action(p, BankAction.DEPOSIT_ALL);
        h.assertTrue(balance(p) == 16 && EmeraldInventory.count(p) == 0, "Only main emeralds counted");
        h.assertTrue(p.getOffhandItem().getCount() == 64 && p.getInventory().getItem(36).getCount() == 64
                && p.getInventory().getItem(1).is(Items.EMERALD_BLOCK) && p.getInventory().getItem(2).is(Items.DIAMOND)
                && p.getInventory().getItem(3).get(net.minecraft.core.component.DataComponents.CONTAINER).stream().mapToInt(ItemStack::getCount).sum() == 64, "Excluded slots/items unchanged");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void deadSpectatorAndRemovedCounterCannotTransfer(GameTestHelper h) {
        var p = registered(h); p.getInventory().setItem(1, new ItemStack(Items.EMERALD, 64));
        action(p, BankAction.DEPOSIT_PAGE); var req = request(p, BankAction.DEPOSIT_16, 0);
        p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
        h.assertTrue(!menu(p).handle(p, req), "Spectator cannot transfer");
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); p.setHealth(0);
        h.assertTrue(!menu(p).handle(p, req), "Dead player cannot transfer"); p.setHealth(20);
        h.getLevel().setBlock(h.absolutePos(new net.minecraft.core.BlockPos(2, 2, 2)), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        h.assertTrue(!menu(p).handle(p, req), "Removed counter cannot transfer");
        h.assertTrue(balance(p) == 0 && EmeraldInventory.count(p) == 64 && BankManager.get(p.server).history(p.getUUID()).isEmpty(), "Invalid lifecycle requests preserve assets"); h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void duplicateForgedClosedAndDistantRequestsCannotTransfer(GameTestHelper h) {
        var p = registered(h); var other = registered(h);
        p.getInventory().setItem(1, new ItemStack(Items.EMERALD, 64));
        BankGameTests.menu(h, p); action(p, BankAction.DEPOSIT_PAGE);
        var req = request(p, BankAction.DEPOSIT_16, 0); var m = menu(p);
        h.assertTrue(m.handle(p, req) && !m.handle(p, req), "Duplicate nonce settles once");
        h.assertTrue(balance(p) == 16 && balance(other) == 0, "Player isolation");
        h.assertTrue(!m.handle(p, new BankNetworking.Request(m.containerId, UUID.randomUUID(), m.data().token(), BankAction.DEPOSIT_ALL, 0)), "Forged session refused");
        h.assertTrue(!m.handle(p, new BankNetworking.Request(m.containerId + 1, m.data().session(), m.data().token(), BankAction.DEPOSIT_ALL, 0)), "Forged menu refused");
        h.assertTrue(!m.handle(p, new BankNetworking.Request(m.containerId, m.data().session(), UUID.randomUUID(), BankAction.DEPOSIT_ALL, 0)), "Forged token refused");
        action(p, BankAction.WITHDRAW_16); h.assertTrue(menu(p).data().message() == BankManager.Result.INVALID_REQUEST, "Wrong page refused");
        var stale = request(p, BankAction.DEPOSIT_ALL, 0); p.containerMenu = p.inventoryMenu;
        h.assertTrue(!m.handle(p, stale), "Closed menu refused");
        BankGameTests.menu(h, p); action(p, BankAction.DEPOSIT_PAGE); var far = request(p, BankAction.DEPOSIT_ALL, 0);
        p.setPos(p.getX() + 20, p.getY(), p.getZ()); h.assertTrue(!menu(p).handle(p, far), "Distant refused");
        h.assertTrue(balance(p) == 16 && BankManager.get(p.server).history(p.getUUID()).size() == 1, "No extra transactions"); h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void historyPagesAndDeathCheckpointPersist(GameTestHelper h) throws Exception {
        var p = registered(h); p.getInventory().setItem(1, new ItemStack(Items.EMERALD, 32));
        action(p, BankAction.DEPOSIT_PAGE); for (int i = 0; i < 12; i++) action(p, BankAction.DEPOSIT_1);
        action(p, BankAction.HISTORY_PAGE);
        h.assertTrue(menu(p).data().entries().size() == 10 && menu(p).data().entries().getFirst().sequence() == 12, "Latest ten first");
        menu(p).handle(p, request(p, BankAction.HISTORY_PAGE, 1));
        h.assertTrue(menu(p).data().entries().size() == 2 && menu(p).data().entries().getFirst().sequence() == 2, "Older page retained");
        UUID checkpoint = ((BankPlayerState) p).lastbetCheckpoint();
        var reborn = BankGameTests.player(h); reborn.restoreFrom(p, false);
        h.assertTrue(checkpoint.equals(((BankPlayerState) reborn).lastbetCheckpoint()), "Respawn copies checkpoint");
        p.setHealth(0); p.die(p.damageSources().generic()); h.assertTrue(balance(p) == 12, "Death retains bank balance");
        h.assertTrue(checkpoint.equals(TransactionJournal.checkpoint(p.saveWithoutId(new CompoundTag()))), "Vanilla save retains checkpoint");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void overflowAndNegativeAmountsCannotMutate(GameTestHelper h) throws Exception {
        var p = BankGameTests.player(h); var data = new BankSavedData(); var a = data.create(p.getUUID()); data.finishDelivery(a.ownerId());
        var tag = data.save(new CompoundTag(), p.registryAccess()); tag.putInt("schema_version", 1); tag.remove("transactions");
        tag.getList("accounts", 10).getCompound(0).putLong("balance", Long.MAX_VALUE);
        data = BankSavedData.load(tag, p.registryAccess()); var root = root(h); var file = root.resolve("data/bank.dat"); data.saveChecked(file, p.registryAccess());
        var service = new BankTransactions(file, root.resolve("playerdata"), data, p.registryAccess()); p.getInventory().setItem(0, new ItemStack(Items.EMERALD));
        h.assertTrue(service.transact(p, BankTransaction.Type.DEPOSIT, 1) == BankManager.Result.BALANCE_OVERFLOW, "Overflow refused");
        h.assertTrue(service.transact(p, BankTransaction.Type.DEPOSIT, -1) == BankManager.Result.EMPTY_AMOUNT, "Negative refused");
        h.assertTrue(data.findByOwner(p.getUUID()).orElseThrow().balance() == Long.MAX_VALUE && EmeraldInventory.count(p) == 1 && data.history(p.getUUID()).isEmpty(), "Boundary unchanged"); h.succeed();
    }

    private static Path root(GameTestHelper h) throws Exception {
        return Files.createTempDirectory(h.getLevel().getServer().getWorldPath(LevelResource.ROOT), "transaction-fixture-");
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void everyCommitBoundaryRecoversFromActualDiskExactlyOnce(GameTestHelper h) throws Exception {
        for (String point : TransactionFaults.POINTS) {
            for (BankTransaction.Type type : BankTransaction.Type.values()) {
                var p = BankGameTests.player(h); var data = new BankSavedData(); var a = data.create(p.getUUID()); data.finishDelivery(a.ownerId());
                if (type == BankTransaction.Type.WITHDRAWAL) {
                    var legacy = data.save(new CompoundTag(), p.registryAccess()); legacy.putInt("schema_version", 1); legacy.remove("transactions");
                    legacy.getList("accounts", 10).getCompound(0).putLong("balance", 64); data = BankSavedData.load(legacy, p.registryAccess());
                }
                p.getInventory().setItem(0, new ItemStack(Items.EMERALD, 64));
                Path root = root(h), bank = root.resolve("data/bank.dat"), players = root.resolve("playerdata");
                data.saveChecked(bank, p.registryAccess()); var service = new BankTransactions(bank, players, data, p.registryAccess());
                TransactionFaults.arm(point);
                h.assertTrue(service.transact(p, type, 16) == BankManager.Result.RECOVERY_PENDING, "Fault reached: " + point);
                h.assertTrue(((BankPlayerState) p).lastbetQuarantined(), "Uncertain player quarantined");
                TransactionFaults.clear();
                var reloaded = BankSavedData.load(BankPersistence.read(bank).getCompound("data"), p.registryAccess());
                var recovered = new BankTransactions(bank, players, reloaded, p.registryAccess());
                h.assertTrue(!recovered.locked(p.getUUID()), "Recovery succeeded: " + point);
                long count = point.equals("PREPARE_BEFORE") ? 0 : 1;
                h.assertTrue(reloaded.history(p.getUUID()).size() == count, "One completed ledger entry at most: " + point);
                CompoundTag savedPlayer = BankPersistence.read(players.resolve(p.getStringUUID() + ".dat"));
                var restored = BankGameTests.player(h); restored.load(savedPlayer);
                long total = EmeraldInventory.count(restored) + reloaded.findByOwner(p.getUUID()).orElseThrow().balance();
                h.assertTrue(total == (type == BankTransaction.Type.DEPOSIT ? 64 : 128), "Conservation after recovery: " + point);
                var again = BankSavedData.load(BankPersistence.read(bank).getCompound("data"), p.registryAccess());
                new BankTransactions(bank, players, again, p.registryAccess());
                h.assertTrue(again.history(p.getUUID()).size() == count, "Second restart does not duplicate: " + point);
            }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void conflictAndCorruptJournalLockWithoutOverwritingEvidence(GameTestHelper h) throws Exception {
        for (boolean corrupt : new boolean[]{false, true}) {
            var p = BankGameTests.player(h); var data = new BankSavedData(); var a = data.create(p.getUUID()); data.finishDelivery(a.ownerId());
            p.getInventory().setItem(0, new ItemStack(Items.EMERALD, 32)); Path root = root(h), bank = root.resolve("data/bank.dat"), players = root.resolve("playerdata");
            data.saveChecked(bank, p.registryAccess()); var service = new BankTransactions(bank, players, data, p.registryAccess());
            TransactionFaults.arm("PREPARE_AFTER"); service.transact(p, BankTransaction.Type.DEPOSIT, 16); TransactionFaults.clear();
            Path journal;
            try (var files = Files.list(bank.getParent().resolve("lastbet_transactions").resolve(p.getStringUUID()))) { journal = files.filter(f -> f.toString().endsWith(".dat")).findFirst().orElseThrow(); }
            if (corrupt) Files.write(journal, new byte[]{31, -117, 8, 0});
            else {
                var player = BankPersistence.read(players.resolve(p.getStringUUID() + ".dat")); player.putUUID(TransactionJournal.CHECKPOINT, UUID.randomUUID());
                BankPersistence.writeAtomic(players.resolve(p.getStringUUID() + ".dat"), player);
            }
            byte[] evidence = Files.readAllBytes(journal), bankBefore = Files.readAllBytes(bank);
            var loaded = BankSavedData.load(BankPersistence.read(bank).getCompound("data"), p.registryAccess());
            var recovered = new BankTransactions(bank, players, loaded, p.registryAccess());
            h.assertTrue(recovered.locked(p.getUUID()), "Corruption/conflict locks owner");
            h.assertTrue(java.util.Arrays.equals(evidence, Files.readAllBytes(journal)) && java.util.Arrays.equals(bankBefore, Files.readAllBytes(bank)), "Evidence and bank unchanged");
            boolean refused = false; try { recovered.admission(p.getUUID()); } catch (java.io.IOException expected) { refused = true; }
            h.assertTrue(refused, "Unresolved owner cannot enter world");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void bankWriteIOExceptionDisablesStorageAndRecoversWithoutLoss(GameTestHelper h) throws Exception {
        var p = BankGameTests.player(h); var data = new BankSavedData(); var a = data.create(p.getUUID()); data.finishDelivery(a.ownerId());
        p.getInventory().setItem(0, new ItemStack(Items.EMERALD, 64)); Path root = root(h), bank = root.resolve("data/bank.dat"), players = root.resolve("playerdata");
        data.saveChecked(bank, p.registryAccess()); byte[] original = Files.readAllBytes(bank);
        var service = new BankTransactions(bank, players, data, p.registryAccess());
        Path blocker = bank.resolveSibling("bank.dat_old"); Files.createDirectory(blocker); Files.writeString(blocker.resolve("evidence"), "controlled I/O failure");
        h.assertTrue(service.transact(p, BankTransaction.Type.DEPOSIT, 16) == BankManager.Result.RECOVERY_PENDING, "Actual bank write fails");
        h.assertTrue(!data.isAvailable() && service.locked(p.getUUID()), "Bank disabled and player locked");
        h.assertTrue(java.util.Arrays.equals(original, Files.readAllBytes(bank)), "Original bank bytes retained");
        data.save(bank.toFile(), p.registryAccess()); h.assertTrue(java.util.Arrays.equals(original, Files.readAllBytes(bank)), "Autosave cannot overwrite failed bank");
        Files.delete(blocker.resolve("evidence")); Files.delete(blocker);
        var loaded = BankSavedData.load(BankPersistence.read(bank).getCompound("data"), p.registryAccess());
        var recovered = new BankTransactions(bank, players, loaded, p.registryAccess());
        var saved = BankPersistence.read(players.resolve(p.getStringUUID() + ".dat")); var restored = BankGameTests.player(h); restored.load(saved);
        h.assertTrue(!recovered.locked(p.getUUID()) && loaded.findByOwner(p.getUUID()).orElseThrow().balance() == 16
                && EmeraldInventory.count(restored) == 48 && loaded.history(p.getUUID()).size() == 1, "Recovered persisted player debit exactly once"); h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void missingPrimaryPlayerLocksRatherThanLoadingBackup(GameTestHelper h) throws Exception {
        var p = BankGameTests.player(h); var data = new BankSavedData(); var a = data.create(p.getUUID()); data.finishDelivery(a.ownerId());
        p.getInventory().setItem(0, new ItemStack(Items.EMERALD, 64)); Path root = root(h), bank = root.resolve("data/bank.dat"), players = root.resolve("playerdata");
        data.saveChecked(bank, p.registryAccess()); var service = new BankTransactions(bank, players, data, p.registryAccess());
        TransactionFaults.arm("PREPARE_AFTER"); service.transact(p, BankTransaction.Type.DEPOSIT, 16); TransactionFaults.clear();
        Path primary = players.resolve(p.getStringUUID() + ".dat"), backup = primary.resolveSibling(primary.getFileName() + "_old");
        Files.copy(primary, backup); Files.delete(primary); byte[] bankBefore = Files.readAllBytes(bank), backupBefore = Files.readAllBytes(backup);
        var loaded = BankSavedData.load(BankPersistence.read(bank).getCompound("data"), p.registryAccess());
        var recovered = new BankTransactions(bank, players, loaded, p.registryAccess());
        h.assertTrue(recovered.locked(p.getUUID()) && !Files.exists(primary), "Missing primary cannot fall back to stale backup");
        h.assertTrue(java.util.Arrays.equals(bankBefore, Files.readAllBytes(bank)) && java.util.Arrays.equals(backupBefore, Files.readAllBytes(backup)), "All recovery evidence retained"); h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void recoveryCanBeInterruptedAndRepeatedWithoutDuplicating(GameTestHelper h) throws Exception {
        var p = BankGameTests.player(h); var data = new BankSavedData(); var a = data.create(p.getUUID()); data.finishDelivery(a.ownerId());
        p.getInventory().setItem(0, new ItemStack(Items.EMERALD, 64)); Path root = root(h), bank = root.resolve("data/bank.dat"), players = root.resolve("playerdata");
        data.saveChecked(bank, p.registryAccess()); var service = new BankTransactions(bank, players, data, p.registryAccess());
        TransactionFaults.arm("PREPARE_AFTER"); service.transact(p, BankTransaction.Type.DEPOSIT, 16); TransactionFaults.clear();
        var loaded = BankSavedData.load(BankPersistence.read(bank).getCompound("data"), p.registryAccess());
        TransactionFaults.arm("BANK_AFTER"); var interrupted = new BankTransactions(bank, players, loaded, p.registryAccess()); TransactionFaults.clear();
        h.assertTrue(interrupted.locked(p.getUUID()), "Recovery fault locks account");
        var again = BankSavedData.load(BankPersistence.read(bank).getCompound("data"), p.registryAccess());
        var recovered = new BankTransactions(bank, players, again, p.registryAccess());
        h.assertTrue(!recovered.locked(p.getUUID()) && again.findByOwner(p.getUUID()).orElseThrow().balance() == 16 && again.history(p.getUUID()).size() == 1, "Next restart finishes same transaction once"); h.succeed();
    }
}

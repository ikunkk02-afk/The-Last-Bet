// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.bank;

import com.shouyun.lastbet.TheLastBet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Synchronous write-ahead coordinator. Vanilla autosaves cannot run between these main-thread steps. */
public final class BankTransactions {
    private final Path bankFile;
    private final Path players;
    private final Path journals;
    private final BankSavedData data;
    private final HolderLookup.Provider registries;
    private final Set<UUID> locked = new HashSet<>();
    private boolean scanFailed;

    BankTransactions(Path bankFile, Path players, BankSavedData data, HolderLookup.Provider registries) {
        this.bankFile = bankFile;
        this.players = players;
        this.journals = bankFile.getParent().resolve("lastbet_transactions");
        this.data = data;
        this.registries = registries;
        scanAndRecover();
    }

    public boolean locked(UUID owner) { return scanFailed || locked.contains(owner); }
    private boolean available() { return data != null && data.isAvailable(); }
    private Path playerFile(UUID owner) { return players.resolve(owner + ".dat"); }
    private Path journalFile(BankTransaction t) { return journals.resolve(t.ownerId().toString()).resolve(t.id() + ".dat"); }

    private void lock(UUID owner, Path evidence, Exception failure) {
        locked.add(owner);
        TheLastBet.LOGGER.error("Bank account locked: owner={}, evidence={}. Preserve bank, player and journal files; repair together and restart. No balance was reset.", owner, evidence, failure);
    }

    private CompoundTag readPlayer(UUID owner) throws IOException {
        Path file = playerFile(owner);
        if (!Files.isRegularFile(file)) throw new IOException("Missing primary player file; refusing backup fallback: " + file);
        CompoundTag tag = BankPersistence.read(file);
        if (!tag.hasUUID("UUID") || !tag.getUUID("UUID").equals(owner)) throw new IOException("Player file UUID mismatch: " + file);
        EmeraldInventory.main(tag);
        TransactionJournal.checkpoint(tag);
        return tag;
    }

    private void scanAndRecover() {
        if (!Files.exists(journals)) return;
        try (var directories = Files.list(journals)) {
            for (Path directory : directories.toList()) {
                UUID owner;
                try { owner = UUID.fromString(directory.getFileName().toString()); }
                catch (IllegalArgumentException failure) { throw new IOException("Unidentified journal evidence: " + directory, failure); }
                List<TransactionJournal> pending = new ArrayList<>();
                try (var entries = Files.list(directory)) {
                    for (Path file : entries.toList()) {
                        String name = file.getFileName().toString();
                        if (name.length() < 40) throw new IOException("Unexpected journal evidence: " + file);
                        UUID id = UUID.fromString(name.substring(0, 36));
                        Path primary = directory.resolve(id + ".dat");
                        if (!Files.isRegularFile(primary)) throw new IOException("Missing journal primary; evidence retained: " + file);
                        if (!file.equals(primary)) {
                            if (!(name.endsWith(".dat_old") || name.endsWith(".tmp"))) throw new IOException("Unexpected journal file: " + file);
                            continue;
                        }
                        TransactionJournal journal = TransactionJournal.load(BankPersistence.read(file), registries);
                        if (!journal.transaction().ownerId().equals(owner) || !journal.transaction().id().equals(id)) throw new IOException("Journal path identity mismatch");
                        if (journal.committed()) {
                            if (available() && !data.transaction(id).filter(journal.transaction()::equals).isPresent()) throw new IOException("Committed journal missing from bank ledger");
                        } else pending.add(journal);
                    }
                    if (pending.size() > 1) throw new IOException("Multiple unfinished transactions for one account");
                    if (!pending.isEmpty()) recover(pending.getFirst());
                } catch (IOException | RuntimeException failure) { lock(owner, directory, failure); }
            }
        } catch (IOException | RuntimeException failure) {
            scanFailed = true;
            if (data != null) data.disable();
            TheLastBet.LOGGER.error("Bank journal scan failed for {}. Banking and admission are stopped until evidence can be identified.", journals, failure);
        }
    }

    /** Return checked canonical data before vanilla can load a stale singleplayer or backup snapshot. */
    public CompoundTag admission(UUID owner) throws IOException {
        if (locked(owner)) throw new IOException("Account recovery is unresolved for " + owner);
        if (!available()) return null; // Existing corruption protection keeps the rest of the world playable.
        UUID expected = data.checkpoint(owner);
        if (expected == null) {
            if (Files.isRegularFile(playerFile(owner)) && TransactionJournal.checkpoint(BankPersistence.read(playerFile(owner))) != null) {
                IOException failure = new IOException("Player checkpoint exists without bank history");
                lock(owner, playerFile(owner), failure);
                throw failure;
            }
            return null;
        }
        try {
            CompoundTag tag = readPlayer(owner);
            if (!expected.equals(TransactionJournal.checkpoint(tag))) throw new IOException("Player/bank checkpoint mismatch; refusing stale inventory");
            return tag;
        } catch (IOException | RuntimeException failure) {
            lock(owner, playerFile(owner), failure);
            throw new IOException("Unresolved player checkpoint", failure);
        }
    }

    BankManager.Result transact(ServerPlayer player, BankTransaction.Type type, long amount) {
        if (!player.server.isSameThread()) throw new IllegalStateException("Transaction accessed off server thread");
        UUID owner = player.getUUID();
        if (!available()) return BankManager.Result.UNAVAILABLE;
        if (locked(owner)) return BankManager.Result.RECOVERY_PENDING;
        BankAccount account = data.findByOwner(owner).orElse(null);
        if (account == null || account.deliveryPending()) return BankManager.Result.INVALID_REQUEST;
        if (amount <= 0) return BankManager.Result.EMPTY_AMOUNT;
        if (type == BankTransaction.Type.DEPOSIT && EmeraldInventory.count(player) < amount) return BankManager.Result.NOT_ENOUGH_EMERALDS;
        if (type == BankTransaction.Type.WITHDRAWAL && account.balance() < amount) return BankManager.Result.INSUFFICIENT_BALANCE;
        if (type == BankTransaction.Type.WITHDRAWAL && amount > 36L * 64) return BankManager.Result.NO_SPACE;
        long afterBalance;
        try { afterBalance = type == BankTransaction.Type.DEPOSIT ? Math.addExact(account.balance(), amount) : Math.subtractExact(account.balance(), amount); }
        catch (ArithmeticException failure) { return BankManager.Result.BALANCE_OVERFLOW; }
        var before = EmeraldInventory.snapshot(player);
        net.minecraft.nbt.ListTag after;
        try { after = EmeraldInventory.plan(before, type, amount, registries); }
        catch (IllegalArgumentException failure) { return type == BankTransaction.Type.DEPOSIT ? BankManager.Result.NOT_ENOUGH_EMERALDS : BankManager.Result.NO_SPACE; }
        UUID transactionId;
        do { transactionId = UUID.randomUUID(); }
        while (data.transaction(transactionId).isPresent() || Files.exists(journals.resolve(owner.toString()).resolve(transactionId + ".dat")));
        var transaction = new BankTransaction(transactionId, account.accountId(), owner, type, amount,
                afterBalance, System.currentTimeMillis(), (long) data.history(owner).size() + 1);
        var journal = new TransactionJournal(transaction, data.checkpoint(owner), before, after, false);
        try { data.preflight(transaction, registries); BankPersistence.verifyReadable(journal.save()); }
        catch (IOException | RuntimeException failure) {
            TheLastBet.LOGGER.error("Bank storage preflight refused transaction {} before changing assets", transaction.id(), failure);
            return BankManager.Result.STORAGE_LIMIT;
        }
        Path evidence = journalFile(transaction);
        try {
            BankPlayerState state = (BankPlayerState) player;
            if (!Objects.equals(state.lastbetCheckpoint(), journal.previousCheckpoint())) throw new IOException("Live player checkpoint mismatch");
            savePlayerState(owner, player.saveWithoutId(new CompoundTag()));
            prepareJournal(journal);
            EmeraldInventory.apply(player, after);
            state.lastbetCheckpoint(transaction.id());
            savePlayerState(owner, player.saveWithoutId(new CompoundTag()));
            commitBank(transaction);
            completeJournal(journal);
            player.inventoryMenu.broadcastChanges();
            return type == BankTransaction.Type.DEPOSIT ? BankManager.Result.DEPOSITED : BankManager.Result.WITHDRAWN;
        } catch (IOException | RuntimeException failure) {
            lock(owner, evidence, failure);
            ((BankPlayerState) player).lastbetQuarantined(true);
            player.connection.disconnect(Component.translatable("message.lastbet.bank.recovery_disconnect"));
            return BankManager.Result.RECOVERY_PENDING;
        }
    }

    private void prepareJournal(TransactionJournal journal) throws IOException { BankPersistence.writeAtomic(journalFile(journal.transaction()), journal.save()); }
    private void savePlayerState(UUID owner, CompoundTag tag) throws IOException { BankPersistence.writeAtomic(playerFile(owner), tag); }
    private void commitBank(BankTransaction t) throws IOException {
        data.commit(t);
        data.saveChecked(bankFile, registries);
    }
    private void completeJournal(TransactionJournal journal) throws IOException { BankPersistence.writeAtomic(journalFile(journal.transaction()), journal.completed().save()); }

    private void recover(TransactionJournal journal) throws IOException {
        var t = journal.transaction();
        if (!available()) throw new IOException("Bank unavailable; unfinished journal must remain locked");
        BankAccount account = data.findByOwner(t.ownerId()).orElseThrow(() -> new IOException("Journal account is missing"));
        if (!account.accountId().equals(t.accountId())) throw new IOException("Journal account identity mismatch");
        CompoundTag player = readPlayer(t.ownerId());
        UUID marker = TransactionJournal.checkpoint(player);
        if (data.transaction(t.id()).isPresent()) {
            if (!data.transaction(t.id()).orElseThrow().equals(t) || !t.id().equals(data.checkpoint(t.ownerId()))
                    || !t.id().equals(marker)) throw new IOException("Committed recovery checkpoint conflict");
        } else {
            if (account.balance() != t.balanceBefore() || !Objects.equals(data.checkpoint(t.ownerId()), journal.previousCheckpoint())) throw new IOException("Prepared bank state conflicts with journal");
            if (Objects.equals(marker, journal.previousCheckpoint()) && EmeraldInventory.main(player).equals(journal.before())) {
                EmeraldInventory.apply(player, journal.after());
                player.putUUID(TransactionJournal.CHECKPOINT, t.id());
                savePlayerState(t.ownerId(), player);
            } else if (!t.id().equals(marker) || !EmeraldInventory.main(player).equals(journal.after())) {
                throw new IOException("Prepared player state conflicts with both journal snapshots");
            }
            commitBank(t);
        }
        completeJournal(journal);
        TheLastBet.LOGGER.info("Recovered bank transaction {} for {} exactly once", t.id(), t.ownerId());
    }
}

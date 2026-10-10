// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.bank;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BankSavedDataTest {
    @TempDir Path directory;
    @BeforeAll static void version() { SharedConstants.tryDetectVersion(); }

    @Test void accountStartsAtZeroAndCannotBeOpenedTwice() {
        BankSavedData data = new BankSavedData();
        UUID owner = UUID.randomUUID();
        BankAccount account = data.create(owner);
        assertEquals(owner, account.ownerId());
        assertEquals(0, account.balance());
        assertTrue(account.deliveryPending());
        assertThrows(IllegalStateException.class, () -> data.create(owner));
        assertEquals(1, data.accounts().size());
    }

    @Test void playersHaveIndependentAccountsAndCards() {
        BankSavedData data = new BankSavedData();
        BankAccount first = data.create(UUID.randomUUID());
        BankAccount second = data.create(UUID.randomUUID());
        assertNotEquals(first.accountId(), second.accountId());
        assertNotEquals(first.cardId(), second.cardId());
        assertEquals(first, data.findByAccount(first.accountId()).orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> data.accounts().clear());
    }

    @Test void credentialRequiresMatchingOwnerAccountAndCard() {
        BankAccount a = new BankSavedData().create(UUID.randomUUID());
        assertTrue(a.matches(a.ownerId(), a.accountId(), a.cardId()));
        assertFalse(a.matches(UUID.randomUUID(), a.accountId(), a.cardId()));
        assertFalse(a.matches(a.ownerId(), UUID.randomUUID(), a.cardId()));
        assertFalse(a.matches(a.ownerId(), a.accountId(), UUID.randomUUID()));
    }

    @Test void completedDeliveryRetainsAccountAndCardIdentifiers() {
        BankSavedData data = new BankSavedData();
        BankAccount before = data.create(UUID.randomUUID());
        data.finishDelivery(before.ownerId());
        BankAccount after = data.findByOwner(before.ownerId()).orElseThrow();
        assertFalse(after.deliveryPending());
        assertEquals(before.accountId(), after.accountId());
        assertEquals(before.cardId(), after.cardId());
    }

    @Test void compressedFileRoundTripsWithBackup() throws IOException {
        BankSavedData data = new BankSavedData();
        BankAccount account = data.create(UUID.randomUUID());
        Path path = directory.resolve("lastbet_bank.dat");
        data.saveChecked(path, null);
        assertFalse(data.isDirty());
        byte[] first = Files.readAllBytes(path);
        data.finishDelivery(account.ownerId());
        data.saveChecked(path, null);
        assertArrayEquals(first, Files.readAllBytes(directory.resolve("lastbet_bank.dat_old")));
        BankSavedData loaded = BankSavedData.load(BankPersistence.read(path).getCompound("data"), null);
        BankAccount reopened = loaded.findByOwner(account.ownerId()).orElseThrow();
        assertEquals(account.accountId(), reopened.accountId());
        assertEquals(account.cardId(), reopened.cardId());
        assertEquals(0, reopened.balance());
        assertFalse(reopened.deliveryPending());
    }

    @Test void pendingDeliverySurvivesReload() {
        BankSavedData data = new BankSavedData();
        BankAccount account = data.create(UUID.randomUUID());
        BankSavedData loaded = BankSavedData.load(data.save(new CompoundTag(), null), null);
        assertEquals(account, loaded.findByOwner(account.ownerId()).orElseThrow());
    }

    @Test void unknownSchemaIsRejected() {
        CompoundTag tag = serialized();
        tag.putInt("schema_version", 99);
        assertThrows(IllegalArgumentException.class, () -> BankSavedData.load(tag, null));
        assertThrows(IllegalArgumentException.class, () -> BankSavedData.load(new CompoundTag(), null));
    }

    @Test void malformedListIsRejectedRatherThanIgnored() {
        CompoundTag tag = serialized();
        ListTag invalid = new ListTag();
        invalid.add(StringTag.valueOf("broken"));
        tag.put("accounts", invalid);
        assertThrows(IllegalArgumentException.class, () -> BankSavedData.load(tag, null));
    }

    @Test void missingUuidAndWrongBalanceTypeAreRejected() {
        CompoundTag tag = serialized();
        entry(tag).remove("owner");
        assertThrows(IllegalArgumentException.class, () -> BankSavedData.load(tag, null));
        CompoundTag wrongType = serialized();
        entry(wrongType).putInt("balance", 0);
        assertThrows(IllegalArgumentException.class, () -> BankSavedData.load(wrongType, null));
    }

    @Test void negativeBalanceAndInvalidDeliveryFlagAreRejected() {
        CompoundTag tag = serialized();
        entry(tag).putLong("balance", -1);
        assertThrows(IllegalArgumentException.class, () -> BankSavedData.load(tag, null));
        CompoundTag invalid = serialized();
        entry(invalid).putByte("delivery_pending", (byte) 2);
        assertThrows(IllegalArgumentException.class, () -> BankSavedData.load(invalid, null));
    }

    @Test void duplicateOwnerAccountOrCardRejectsEntireFile() {
        for (String key : new String[]{"owner", "account", "card"}) {
            BankSavedData data = new BankSavedData();
            data.create(UUID.randomUUID());
            data.create(UUID.randomUUID());
            CompoundTag tag = data.save(new CompoundTag(), null);
            ListTag accounts = tag.getList("accounts", 10);
            accounts.getCompound(1).putUUID(key, accounts.getCompound(0).getUUID(key));
            assertThrows(IllegalArgumentException.class, () -> BankSavedData.load(tag, null), key);
        }
    }

    @Test void writeFailureDisablesStorageAndPreservesPreviousBytes() throws IOException {
        Path parent = directory.resolve("blocked");
        byte[] marker = "original data".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(parent, marker);
        BankSavedData data = new BankSavedData();
        data.create(UUID.randomUUID());
        assertThrows(IOException.class, () -> data.saveChecked(parent.resolve("lastbet_bank.dat"), null));
        assertFalse(data.isAvailable());
        assertTrue(data.isDirty());
        assertArrayEquals(marker, Files.readAllBytes(parent));
        assertThrows(IllegalStateException.class, () -> data.create(UUID.randomUUID()));
        // Vanilla autosave must not overwrite a disabled bank.
        data.save(parent.toFile(), null);
        assertArrayEquals(marker, Files.readAllBytes(parent));
    }

    @Test void truncatedNbtCannotBeReadAndRemainsUnchanged() throws IOException {
        Path file = directory.resolve("lastbet_bank.dat");
        byte[] broken = new byte[]{31, -117, 8, 0, 1};
        Files.write(file, broken);
        assertThrows(IOException.class, () -> BankPersistence.read(file));
        assertArrayEquals(broken, Files.readAllBytes(file));
    }

    @Test void missingPrimaryWithExistingBackupIsNotTreatedAsNewWorld() throws IOException {
        Path file = directory.resolve("lastbet_bank.dat");
        assertFalse(BankManager.storageFileExists(file));
        Path backup = directory.resolve("lastbet_bank.dat_old");
        byte[] bytes = "previous bank".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(backup, bytes);
        assertThrows(IOException.class, () -> BankManager.storageFileExists(file));
        assertFalse(Files.exists(file));
        assertArrayEquals(bytes, Files.readAllBytes(backup));
    }

    @Test void invalidStoragePathIsRejected() throws IOException {
        Path file = directory.resolve("lastbet_bank.dat");
        Files.createDirectory(file);
        assertThrows(IOException.class, () -> BankManager.storageFileExists(file));
    }

    private static CompoundTag serialized() {
        BankSavedData data = new BankSavedData();
        data.create(UUID.randomUUID());
        return data.save(new CompoundTag(), null);
    }
    private static CompoundTag entry(CompoundTag root) { return root.getList("accounts", 10).getCompound(0); }
}

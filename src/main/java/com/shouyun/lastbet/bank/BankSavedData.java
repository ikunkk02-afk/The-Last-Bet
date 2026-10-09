// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.bank;

import com.shouyun.lastbet.TheLastBet;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

public final class BankSavedData extends SavedData {
    public static final String NAME = "lastbet_bank";
    public static final Factory<BankSavedData> FACTORY = new Factory<>(BankSavedData::new, BankSavedData::load, null);
    private final Map<UUID, BankAccount> accounts = new LinkedHashMap<>();
    private boolean available = true;

    public boolean isAvailable() { return available; }
    public void disable() { available = false; }

    private void requireAvailable() {
        if (!available) throw new IllegalStateException("Bank storage is disabled for this world");
    }

    public Optional<BankAccount> findByOwner(UUID owner) {
        requireAvailable();
        return Optional.ofNullable(accounts.get(owner));
    }

    public Optional<BankAccount> findByAccount(UUID accountId) {
        requireAvailable();
        return accounts.values().stream().filter(a -> a.accountId().equals(accountId)).findFirst();
    }

    public Collection<BankAccount> accounts() {
        requireAvailable();
        return java.util.List.copyOf(accounts.values());
    }

    BankAccount create(UUID owner) {
        requireAvailable();
        if (accounts.containsKey(owner)) throw new IllegalStateException("Owner already has a bank account");
        UUID id;
        do { id = UUID.randomUUID(); } while (findByAccount(id).isPresent());
        UUID card;
        do { card = UUID.randomUUID(); } while (containsCard(card));
        BankAccount account = new BankAccount(owner, id, 0L, card, true);
        accounts.put(owner, account);
        setDirty();
        return account;
    }

    private boolean containsCard(UUID card) {
        return accounts.values().stream().anyMatch(a -> a.cardId().equals(card));
    }

    void finishDelivery(UUID owner) {
        requireAvailable();
        BankAccount account = accounts.get(owner);
        if (account == null) throw new IllegalStateException("Missing account for card delivery");
        accounts.put(owner, account.delivered());
        setDirty();
    }

    public static BankSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains("schema_version", Tag.TAG_INT) || tag.getInt("schema_version") != 1
                || !tag.contains("accounts", Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Invalid or unsupported lastbet bank schema");
        }
        ListTag list = (ListTag) tag.get("accounts");
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("Bank accounts must be a compound list");
        }
        BankSavedData data = new BankSavedData();
        var accountIds = new java.util.HashSet<UUID>();
        var cardIds = new java.util.HashSet<UUID>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("owner") || !entry.hasUUID("account") || !entry.hasUUID("card")
                    || !entry.contains("balance", Tag.TAG_LONG) || !entry.contains("delivery_pending", Tag.TAG_BYTE)
                    || (entry.getByte("delivery_pending") != 0 && entry.getByte("delivery_pending") != 1)) {
                throw new IllegalArgumentException("Invalid bank account entry at index " + i);
            }
            BankAccount account = new BankAccount(entry.getUUID("owner"), entry.getUUID("account"),
                    entry.getLong("balance"), entry.getUUID("card"), entry.getBoolean("delivery_pending"));
            if (data.accounts.putIfAbsent(account.ownerId(), account) != null
                    || !accountIds.add(account.accountId()) || !cardIds.add(account.cardId())) {
                throw new IllegalArgumentException("Duplicate bank owner, account or card at index " + i);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        requireAvailable();
        tag.putInt("schema_version", 1);
        ListTag list = new ListTag();
        for (BankAccount account : accounts.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("owner", account.ownerId());
            entry.putUUID("account", account.accountId());
            entry.putUUID("card", account.cardId());
            entry.putLong("balance", account.balance());
            entry.putBoolean("delivery_pending", account.deliveryPending());
            list.add(entry);
        }
        tag.put("accounts", list);
        return tag;
    }

    public void saveChecked(Path file, HolderLookup.Provider registries) throws IOException {
        requireAvailable();
        CompoundTag root = new CompoundTag();
        root.put("data", save(new CompoundTag(), registries));
        NbtUtils.addCurrentDataVersion(root);
        try {
            BankPersistence.write(file, root);
            setDirty(false);
        } catch (IOException | RuntimeException failure) {
            disable();
            throw failure;
        }
    }

    @Override
    public void save(File file, HolderLookup.Provider registries) {
        if (!available || !isDirty()) return;
        try {
            saveChecked(file.toPath(), registries);
        } catch (IOException | RuntimeException failure) {
            TheLastBet.LOGGER.error("Bank disabled: could not save {}. Existing data is retained; restore and reload the world.", file, failure);
        }
    }
}

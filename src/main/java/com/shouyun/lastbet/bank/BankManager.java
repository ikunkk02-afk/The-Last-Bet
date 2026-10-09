// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.bank;

import com.shouyun.lastbet.TheLastBet;
import com.shouyun.lastbet.item.BankCardData;
import com.shouyun.lastbet.menu.BankMenu;
import com.shouyun.lastbet.registry.BankRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

public final class BankManager {
    private static final Map<MinecraftServer, BankManager> INSTANCES = new IdentityHashMap<>();
    private final MinecraftServer server;
    private final Path file;
    private BankSavedData data;

    public enum Result {
        NONE, OPENED, ALREADY_EXISTS, INVENTORY_FULL, UNAVAILABLE, INVALID_REQUEST;
        public String translationKey() { return "message.lastbet.bank." + name().toLowerCase(java.util.Locale.ROOT); }
    }

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(BankManager::get);
        ServerLifecycleEvents.SERVER_STOPPED.register(INSTANCES::remove);
    }

    public static BankManager get(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Bank accessed off server thread");
        return INSTANCES.computeIfAbsent(server, BankManager::new);
    }

    private BankManager(MinecraftServer server) {
        this.server = server;
        this.file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(BankSavedData.NAME + ".dat").toAbsolutePath().normalize();
        try {
            boolean exists = storageFileExists(file);
            var storage = server.overworld().getDataStorage();
            if (exists) {
                data = storage.get(BankSavedData.FACTORY, BankSavedData.NAME);
                if (data == null) throw new IOException("Existing bank file could not be decoded; refusing empty replacement");
            } else {
                data = new BankSavedData();
                storage.set(BankSavedData.NAME, data);
            }
        } catch (IOException | RuntimeException failure) {
            disable(failure);
        }
    }

    public boolean isAvailable() { return data != null && data.isAvailable(); }

    static boolean storageFileExists(Path file) throws IOException {
        if (regularFileExists(file)) return true;
        if (regularFileExists(file.resolveSibling(file.getFileName() + "_old"))) {
            throw new IOException("Primary bank file is missing but its backup exists; refusing empty replacement: " + file);
        }
        return false;
    }

    private static boolean regularFileExists(Path file) throws IOException {
        try {
            if (!Files.readAttributes(file, BasicFileAttributes.class).isRegularFile()) {
                throw new IOException("Bank path is not a regular file: " + file);
            }
            return true;
        } catch (NoSuchFileException missing) {
            return false;
        }
    }

    public Optional<BankAccount> findByOwner(UUID owner) {
        checkThread();
        requireAvailable();
        return data.findByOwner(owner);
    }

    public Optional<BankAccount> findByAccount(UUID accountId) {
        checkThread();
        requireAvailable();
        return data.findByAccount(accountId);
    }

    public boolean isValidCard(ServerPlayer holder, ItemStack stack) {
        checkThread();
        if (!isAvailable() || holder.server != server || !stack.is(BankRegistry.BANK_CARD)) return false;
        BankCardData card = stack.get(BankRegistry.CARD_DATA);
        return card != null && holder.getUUID().equals(card.ownerId())
                && data.findByOwner(holder.getUUID()).filter(a -> a.matches(card.ownerId(), card.accountId(), card.cardId())).isPresent();
    }

    /** Called only for the player's currently open and still-valid bank menu. */
    public Result register(ServerPlayer player) {
        checkThread();
        if (player.server != server || !(player.containerMenu instanceof BankMenu menu) || !menu.stillValid(player)) {
            return Result.INVALID_REQUEST;
        }
        if (!isAvailable()) return Result.UNAVAILABLE;
        BankAccount account = data.findByOwner(player.getUUID()).orElse(null);
        if (account != null && !account.deliveryPending()) return Result.ALREADY_EXISTS;
        // Recovery checks the actual server inventory before attempting another insertion.
        boolean hasCard = account != null && hasCard(player);
        int slot = player.getInventory().getFreeSlot();
        if (!hasCard && slot < 0) return Result.INVENTORY_FULL;
        try {
            if (account == null) {
                account = data.create(player.getUUID());
                data.saveChecked(file, server.registryAccess());
            }
            if (!hasCard) {
                ItemStack card = new ItemStack(BankRegistry.BANK_CARD);
                card.set(BankRegistry.CARD_DATA, new BankCardData(account.ownerId(), account.accountId(),
                        account.cardId(), player.getGameProfile().getName()));
                player.getInventory().setItem(slot, card);
                player.getInventory().setChanged();
            }
            // Same compressed NBT and UUID file as vanilla PlayerDataStorage, with checked errors.
            // Keep delivery_pending on disk until the inventory is durably readable.
            CompoundTag playerTag = player.saveWithoutId(new CompoundTag());
            Path playerFile = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player.getStringUUID() + ".dat");
            BankPersistence.write(playerFile, playerTag);
            data.finishDelivery(player.getUUID());
            data.saveChecked(file, server.registryAccess());
            player.inventoryMenu.broadcastChanges();
            return Result.OPENED;
        } catch (IOException | RuntimeException failure) {
            disable(failure);
            return Result.UNAVAILABLE;
        }
    }

    private boolean hasCard(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (isValidCard(player, player.getInventory().getItem(i))) return true;
        }
        return false;
    }

    private void disable(Exception failure) {
        if (data != null) data.disable();
        TheLastBet.LOGGER.error("Bank disabled for {}. No empty replacement will be written. Restore the bank file if needed and reload the world.", file, failure);
    }

    private void checkThread() {
        if (!server.isSameThread()) throw new IllegalStateException("Bank accessed off server thread");
    }

    private void requireAvailable() {
        if (!isAvailable()) throw new IllegalStateException("Bank is unavailable for " + file);
    }
}

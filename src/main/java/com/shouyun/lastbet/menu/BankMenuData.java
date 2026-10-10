// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.menu;

import com.shouyun.lastbet.bank.BankAccount;
import com.shouyun.lastbet.bank.BankManager;
import com.shouyun.lastbet.bank.BankTransaction;
import com.shouyun.lastbet.bank.EmeraldInventory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

/** Only a display snapshot for this menu owner; never client authority. */
public record BankMenuData(boolean available, String playerName, String accountId, long balance,
                           boolean deliveryPending, BankManager.Result message, long emeralds,
                           UUID session, UUID token, long revision, int page, int historyPage,
                           int historySize, boolean locked, List<BankTransaction> entries) {
    public BankMenuData { entries = List.copyOf(entries); }
    public static final int HOME = 0, DEPOSIT = 1, WITHDRAWAL = 2, HISTORY = 3;
    public static final StreamCodec<RegistryFriendlyByteBuf, BankMenuData> STREAM_CODEC = StreamCodec.of(
            (b, d) -> {
                b.writeBoolean(d.available()); b.writeUtf(d.playerName(), 64); b.writeUtf(d.accountId(), 36);
                b.writeLong(d.balance()); b.writeBoolean(d.deliveryPending()); b.writeEnum(d.message());
                b.writeLong(d.emeralds()); b.writeUUID(d.session()); b.writeUUID(d.token()); b.writeLong(d.revision());
                b.writeVarInt(d.page()); b.writeVarInt(d.historyPage()); b.writeVarInt(d.historySize()); b.writeBoolean(d.locked());
                b.writeVarInt(d.entries().size());
                for (BankTransaction t : d.entries()) {
                    b.writeUUID(t.id()); b.writeUUID(t.accountId()); b.writeUUID(t.ownerId()); b.writeEnum(t.type());
                    b.writeLong(t.amount()); b.writeLong(t.balanceAfter()); b.writeLong(t.timestamp()); b.writeLong(t.sequence());
                }
            }, b -> {
                boolean available = b.readBoolean(); String name = b.readUtf(64), account = b.readUtf(36);
                long balance = b.readLong(); boolean pending = b.readBoolean(); var message = b.readEnum(BankManager.Result.class);
                long emeralds = b.readLong(); UUID session = b.readUUID(), token = b.readUUID(); long revision = b.readLong();
                int page = b.readVarInt(), historyPage = b.readVarInt(), size = b.readVarInt(); boolean locked = b.readBoolean();
                int count = b.readVarInt();
                if (count < 0 || count > 10) throw new IllegalArgumentException("Invalid history page size");
                List<BankTransaction> entries = new ArrayList<>();
                for (int i = 0; i < count; i++) entries.add(new BankTransaction(b.readUUID(), b.readUUID(), b.readUUID(),
                        b.readEnum(BankTransaction.Type.class), b.readLong(), b.readLong(), b.readLong(), b.readLong()));
                return new BankMenuData(available, name, account, balance, pending, message, emeralds,
                        session, token, revision, page, historyPage, size, locked, entries);
            });

    public static BankMenuData snapshot(ServerPlayer player, BankManager.Result result) {
        return snapshot(player, result, UUID.randomUUID(), UUID.randomUUID(), 0, HOME, 0);
    }

    public static BankMenuData snapshot(ServerPlayer player, BankManager.Result result, UUID session, UUID token,
                                        long revision, int page, int historyPage) {
        BankManager manager = BankManager.get(player.server);
        BankAccount account = manager.isAvailable() ? manager.findByOwner(player.getUUID()).orElse(null) : null;
        List<BankTransaction> history = manager.history(player.getUUID());
        historyPage = Math.max(0, Math.min(historyPage, Math.max(0, (history.size() - 1) / 10)));
        List<BankTransaction> entries = new ArrayList<>();
        if (page == HISTORY) for (int i = history.size() - 1 - historyPage * 10; i >= 0 && entries.size() < 10; i--) entries.add(history.get(i));
        return new BankMenuData(manager.isAvailable(), player.getGameProfile().getName(),
                account == null ? "" : account.accountId().toString(), account == null ? 0 : account.balance(),
                account != null && account.deliveryPending(), result, EmeraldInventory.count(player),
                session, token, revision, page, historyPage, history.size(), manager.transactions().locked(player.getUUID()), entries);
    }
    public boolean hasAccount() { return !accountId.isEmpty(); }
}

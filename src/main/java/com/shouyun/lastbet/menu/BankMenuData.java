// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.menu;

import com.shouyun.lastbet.bank.BankAccount;
import com.shouyun.lastbet.bank.BankManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

/** Only a display snapshot for the requesting player, never client authority. */
public record BankMenuData(boolean available, String playerName, String accountId, long balance,
                           boolean deliveryPending, BankManager.Result message) {
    public static final StreamCodec<RegistryFriendlyByteBuf, BankMenuData> STREAM_CODEC = StreamCodec.of(
            (buffer, data) -> {
                buffer.writeBoolean(data.available());
                buffer.writeUtf(data.playerName(), 64);
                buffer.writeUtf(data.accountId(), 36);
                buffer.writeLong(data.balance());
                buffer.writeBoolean(data.deliveryPending());
                buffer.writeEnum(data.message());
            }, buffer -> new BankMenuData(buffer.readBoolean(), buffer.readUtf(64), buffer.readUtf(36),
                    buffer.readLong(), buffer.readBoolean(), buffer.readEnum(BankManager.Result.class)));

    public static BankMenuData snapshot(ServerPlayer player, BankManager.Result result) {
        BankManager manager = BankManager.get(player.server);
        BankAccount account = manager.isAvailable() ? manager.findByOwner(player.getUUID()).orElse(null) : null;
        return new BankMenuData(manager.isAvailable(), player.getGameProfile().getName(),
                account == null ? "" : account.accountId().toString(), account == null ? 0L : account.balance(),
                account != null && account.deliveryPending(), result);
    }

    public boolean hasAccount() { return !accountId.isEmpty(); }
}

// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.menu;

import com.shouyun.lastbet.TheLastBet;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class BankNetworking {
    private BankNetworking() {}
    public record Request(int menuId, UUID session, UUID token, BankAction action, int page) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(TheLastBet.id("bank_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of(
                (b, p) -> { b.writeVarInt(p.menuId); b.writeUUID(p.session); b.writeUUID(p.token); b.writeEnum(p.action); b.writeVarInt(p.page); },
                b -> new Request(b.readVarInt(), b.readUUID(), b.readUUID(), b.readEnum(BankAction.class), b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Snapshot(int menuId, BankMenuData data) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(TheLastBet.id("bank_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.of(
                (b, p) -> { b.writeVarInt(p.menuId); BankMenuData.STREAM_CODEC.encode(b, p.data); },
                b -> new Snapshot(b.readVarInt(), BankMenuData.STREAM_CODEC.decode(b)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static void initialize() {
        PayloadTypeRegistry.playC2S().register(Request.TYPE, Request.CODEC);
        PayloadTypeRegistry.playS2C().register(Snapshot.TYPE, Snapshot.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Request.TYPE, (request, context) -> {
            if (context.player().containerMenu instanceof BankMenu menu) menu.handle(context.player(), request);
        });
    }
}

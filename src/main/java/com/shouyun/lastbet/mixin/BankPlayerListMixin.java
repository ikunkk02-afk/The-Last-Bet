// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.mixin;

import com.shouyun.lastbet.TheLastBet;
import com.shouyun.lastbet.bank.BankManager;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerList.class)
public abstract class BankPlayerListMixin {
    @Unique private final Map<UUID, CompoundTag> lastbet$checkedLoads = new HashMap<>();
    @Inject(method = "placeNewPlayer", at = @At("HEAD"), cancellable = true)
    private void lastbet$admission(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        try {
            CompoundTag tag = BankManager.get(player.server).transactions().admission(player.getUUID());
            if (tag != null) lastbet$checkedLoads.put(player.getUUID(), tag);
        } catch (IOException | RuntimeException failure) {
            TheLastBet.LOGGER.error("Player {} refused entry until bank recovery is resolved", player.getUUID(), failure);
            connection.disconnect(Component.translatable("message.lastbet.bank.recovery_disconnect"));
            ci.cancel();
        }
    }
    @Inject(method = "load", at = @At("HEAD"), cancellable = true)
    private void lastbet$loadCanonical(ServerPlayer player, CallbackInfoReturnable<Optional<CompoundTag>> ci) {
        CompoundTag tag = lastbet$checkedLoads.remove(player.getUUID());
        if (tag != null) {
            player.load(tag);
            ci.setReturnValue(Optional.of(tag));
        }
    }
}

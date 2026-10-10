// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.mixin;

import com.shouyun.lastbet.bank.BankPlayerState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class BankConnectionMixin {
    @Shadow public ServerPlayer player;
    @Inject(method = "isAcceptingMessages", at = @At("HEAD"), cancellable = true)
    private void lastbet$quarantine(CallbackInfoReturnable<Boolean> ci) {
        if (((BankPlayerState) player).lastbetQuarantined()) ci.setReturnValue(false);
    }
}

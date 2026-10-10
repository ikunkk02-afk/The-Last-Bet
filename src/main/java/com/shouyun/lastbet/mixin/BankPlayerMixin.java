// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.mixin;

import com.shouyun.lastbet.bank.BankPlayerState;
import com.shouyun.lastbet.bank.TransactionJournal;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class BankPlayerMixin implements BankPlayerState {
    @Unique private UUID lastbet$checkpoint;
    @Unique private volatile boolean lastbet$quarantined;
    public UUID lastbetCheckpoint() { return lastbet$checkpoint; }
    public void lastbetCheckpoint(UUID id) { lastbet$checkpoint = id; }
    public boolean lastbetQuarantined() { return lastbet$quarantined; }
    public void lastbetQuarantined(boolean value) { lastbet$quarantined = value; }
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void lastbet$save(CompoundTag tag, CallbackInfo ci) {
        if (lastbet$checkpoint != null) tag.putUUID(TransactionJournal.CHECKPOINT, lastbet$checkpoint);
    }
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void lastbet$load(CompoundTag tag, CallbackInfo ci) { lastbet$checkpoint = TransactionJournal.checkpoint(tag); }
    @Inject(method = {"tick", "doTick"}, at = @At("HEAD"), cancellable = true)
    private void lastbet$freeze(CallbackInfo ci) { if (lastbet$quarantined) ci.cancel(); }
    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void lastbet$freezeDeath(DamageSource source, CallbackInfo ci) { if (lastbet$quarantined) ci.cancel(); }
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void lastbet$freezeDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> ci) {
        if (lastbet$quarantined) ci.setReturnValue(false);
    }
}

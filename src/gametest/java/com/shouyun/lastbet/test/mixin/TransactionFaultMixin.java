// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.test.mixin;

import com.shouyun.lastbet.bank.BankTransactions;
import com.shouyun.lastbet.test.TransactionFaults;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BankTransactions.class, remap = false)
public abstract class TransactionFaultMixin {
    @Inject(method = "prepareJournal", at = @At("HEAD")) private void prepareBefore(CallbackInfo ci) { TransactionFaults.hit("PREPARE_BEFORE"); }
    @Inject(method = "prepareJournal", at = @At("RETURN")) private void prepareAfter(CallbackInfo ci) { TransactionFaults.hit("PREPARE_AFTER"); }
    @Inject(method = "savePlayerState", at = @At("HEAD")) private void playerBefore(CallbackInfo ci) { TransactionFaults.player(true); }
    @Inject(method = "savePlayerState", at = @At("RETURN")) private void playerAfter(CallbackInfo ci) { TransactionFaults.player(false); }
    @Inject(method = "commitBank", at = @At("HEAD")) private void bankBefore(CallbackInfo ci) { TransactionFaults.hit("BANK_BEFORE"); }
    @Inject(method = "commitBank", at = @At("RETURN")) private void bankAfter(CallbackInfo ci) { TransactionFaults.hit("BANK_AFTER"); }
    @Inject(method = "completeJournal", at = @At("HEAD")) private void completeBefore(CallbackInfo ci) { TransactionFaults.hit("COMPLETE_BEFORE"); }
    @Inject(method = "completeJournal", at = @At("RETURN")) private void completeAfter(CallbackInfo ci) { TransactionFaults.hit("COMPLETE_AFTER"); }
}

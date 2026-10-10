// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.bank;

import java.util.UUID;

/** Implemented on ServerPlayer; included in every vanilla save and copied on respawn. */
public interface BankPlayerState {
    UUID lastbetCheckpoint();
    void lastbetCheckpoint(UUID id);
    boolean lastbetQuarantined();
    void lastbetQuarantined(boolean value);
}

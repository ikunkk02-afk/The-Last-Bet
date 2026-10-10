// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.test;

/** Test-mod-only fault controls. Never present in the release JAR. */
public final class TransactionFaults {
    public static final String[] POINTS = {"PREPARE_BEFORE", "PREPARE_AFTER", "PLAYER_BEFORE", "PLAYER_AFTER", "BANK_BEFORE", "BANK_AFTER", "COMPLETE_BEFORE", "COMPLETE_AFTER"};
    private static String armed;
    private static int playerWrites;
    public static void arm(String point) { armed = point; playerWrites = 0; }
    public static void clear() { armed = null; playerWrites = 0; }
    public static void player(boolean before) {
        if (before) playerWrites++;
        if (playerWrites > 1) hit(before ? "PLAYER_BEFORE" : "PLAYER_AFTER");
    }
    public static void hit(String point) {
        if (point.equals(System.getProperty("lastbet.test.halt"))) {
            System.out.println("LASTBET_CONTROLLED_HALT " + point);
            Runtime.getRuntime().halt(86);
        }
        if (point.equals(armed)) { armed = null; throw new IllegalStateException("Controlled transaction fault: " + point); }
    }
}

// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.test;

import com.google.gson.*;
import com.shouyun.lastbet.TheLastBet;
import com.shouyun.lastbet.bank.*;
import com.shouyun.lastbet.client.screen.BankScreen;
import com.shouyun.lastbet.menu.*;
import com.shouyun.lastbet.registry.BankRegistry;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.*;

/** Real vanilla connection, actual bank widgets and custom payloads. Test source set only. */
public final class BankClientSmoke implements ClientModInitializer {
    private int ticks, step, age, index, pageIndex, scaleIndex;
    private long sentRevision;
    private String accountId, cardId;
    private boolean reload, fullRetried;
    private JsonObject previous;
    private final List<Operation> operations = new ArrayList<>();
    private final Set<String> historyIds = new LinkedHashSet<>();
    private final SingleplayerBankSmoke singleplayer = new SingleplayerBankSmoke();
    private record Operation(BankAction action, int page, long balance, long emeralds, BankManager.Result result) {}
    private static boolean second(Minecraft c) { return c.getUser().getName().equals("BankSmokeB"); }
    private static boolean unavailable() { return Boolean.getBoolean("lastbet.smoke.expectUnavailable"); }
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            try { if (Boolean.getBoolean("lastbet.test.singleplayer")) singleplayer.tick(c); else tick(c); }
            catch (Exception failure) {
                TheLastBet.LOGGER.error("BANK_PHASE2_CLIENT_FAILED step=" + step + " operation=" + index, failure);
                try { Files.writeString(c.gameDirectory.toPath().resolve("smoke-failed.txt"), failure.toString()); } catch (Exception ignored) {}
                c.stop();
            }
        });
    }
    private void tick(Minecraft c) throws Exception {
        ticks++; age++; c.getToasts().clear();
        if (ticks > 8000) throw new IllegalStateException("Timed out step=" + step + " screen=" + c.screen);
        if (c.getOverlay() != null) return;
        if (step == 0 && c.screen instanceof TitleScreen && ticks > 60) {
            Path initial = c.gameDirectory.toPath().resolve("phase2-initial.json"); reload = Files.exists(initial);
            if (reload) previous = JsonParser.parseString(Files.readString(initial)).getAsJsonObject();
            ConnectScreen.startConnecting(new TitleScreen(), c, new ServerAddress("127.0.0.1", 25589),
                    new ServerData("Bank phase 2", "127.0.0.1:25589", ServerData.Type.OTHER), false, null);
            next(1);
        } else if (step == 1 && c.player != null && c.level != null && age > 40) {
            useCounter(c); next(2);
        } else if (step == 2 && c.screen instanceof BankScreen s && age > 20) {
            var d = s.getMenu().data();
            if (unavailable()) {
                check(!d.available(), "Corrupt bank available");
                ClientPlayNetworking.send(new BankNetworking.Request(s.getMenu().containerId, d.session(), d.token(), BankAction.DEPOSIT_ALL, 0)); next(90);
            } else if (reload) {
                accountId = previous.get("account_id").getAsString(); cardId = previous.get("card_id").getAsString();
                check(d.accountId().equals(accountId) && d.balance() == previous.get("balance").getAsLong(), "Restart changed account/balance");
                check(d.emeralds() == previous.get("emeralds").getAsLong() && d.historySize() == 20, "Restart changed inventory/history");
                if (!second(c)) check(card(c) != null && card(c).cardId().toString().equals(cardId), "Restart changed card");
                operations.add(new Operation(BankAction.HISTORY_PAGE, 0, 12, second(c) ? 0 : 180, BankManager.Result.NONE));
                operations.add(new Operation(BankAction.HISTORY_PAGE, 1, 12, second(c) ? 0 : 180, BankManager.Result.NONE));
                operations.add(new Operation(BankAction.HOME, 0, 12, second(c) ? 0 : 180, BankManager.Result.NONE)); next(10);
            } else if (!d.hasAccount()) {
                if (d.message() == BankManager.Result.INVENTORY_FULL) {
                    check(second(c) && !fullRetried, "Unexpected full inventory"); fullRetried = true;
                    c.player.connection.sendCommand("clear @s minecraft:cobblestone"); next(3);
                } else { press(c, "register"); next(4); }
            } else {
                check(d.balance() == 0 && d.historySize() == 0, "Initial fixture already has transactions");
                accountId = d.accountId(); check(card(c) != null, "Bound card missing"); cardId = card(c).cardId().toString();
                c.player.connection.sendCommand("give @s minecraft:emerald 192"); prepareOperations(); next(5);
            }
        } else if (step == 3 && age > 30) { press(c, "register"); next(4); }
        else if (step == 4 && age > 30 && c.screen instanceof BankScreen) next(2);
        else if (step == 5 && age > 30) next(10);
        else if (step == 10 && age > 6 && c.screen instanceof BankScreen s) {
            if (index >= operations.size()) { pageIndex = 0; scaleIndex = 0; next(20); return; }
            Operation operation = operations.get(index); var d = s.getMenu().data(); sentRevision = d.revision();
            if (operation.action == BankAction.HISTORY_PAGE && operation.page != d.historyPage() && d.page() == BankMenuData.HISTORY) {
                press(c, operation.page > d.historyPage() ? "next" : "previous");
            } else press(c, key(operation.action));
            if (!reload && index == 2) {
                // A second widget press and an identical raw payload must not settle twice.
                press(c, key(operation.action));
                ClientPlayNetworking.send(new BankNetworking.Request(s.getMenu().containerId, d.session(), d.token(), operation.action, operation.page));
            }
            next(11);
        } else if (step == 11 && age > 10 && c.screen instanceof BankScreen s && s.getMenu().data().revision() > sentRevision) {
            var d = s.getMenu().data(); var expected = operations.get(index);
            check(d.balance() == expected.balance && d.emeralds() == expected.emeralds && d.message() == expected.result,
                    "Operation mismatch " + expected + " actual=" + d);
            if (expected.action == BankAction.HISTORY_PAGE) {
                check(d.entries().size() == 10 && d.entries().getFirst().sequence() == (expected.page == 0 ? 20 : 10), "History order/page mismatch");
                d.entries().forEach(t -> historyIds.add(t.id().toString()));
            }
            index++; next(10);
        } else if (step == 20 && age > 10 && c.screen instanceof BankScreen s) {
            if (pageIndex >= 4) {
                c.options.guiScale().set(2); c.resizeDisplay(); press(c, "close"); next(30); return;
            }
            BankAction a = new BankAction[]{BankAction.HOME, BankAction.DEPOSIT_PAGE, BankAction.WITHDRAWAL_PAGE, BankAction.HISTORY_PAGE}[pageIndex];
            if (s.getMenu().data().page() == pageIndex) {
                c.options.guiScale().set(2 + scaleIndex); c.resizeDisplay(); next(22); return;
            }
            if (s.getMenu().data().page() != BankMenuData.HOME) {
                sentRevision = s.getMenu().data().revision(); press(c, "back"); next(24); return;
            }
            sentRevision = s.getMenu().data().revision(); press(c, key(a)); next(21);
        } else if (step == 24 && age > 8 && c.screen instanceof BankScreen s && s.getMenu().data().revision() > sentRevision) {
            next(20);
        } else if (step == 21 && age > 8 && c.screen instanceof BankScreen s && s.getMenu().data().revision() > sentRevision) {
            c.options.guiScale().set(2 + scaleIndex); c.resizeDisplay(); next(22);
        } else if (step == 22 && age > 12 && c.screen instanceof BankScreen s) {
            capture(c, "page" + pageIndex + "-scale" + (2 + scaleIndex));
            if (pageIndex == 3 && scaleIndex == 2) { s.mouseScrolled(0, 0, 0, -20); next(23); }
            else advanceCapture(c);
        } else if (step == 23 && age > 12) { capture(c, "history-scrolled-scale4"); advanceCapture(c); }
        else if (step == 30 && age > 20) {
            check(!(c.screen instanceof BankScreen), "Close did not close server menu");
            if (second(c) && !reload) { c.player.connection.sendCommand("kill"); next(31); }
            else { useCounter(c); next(34); }
        } else if (step == 31 && age > 30 && c.player.isDeadOrDying()) { c.player.respawn(); next(32); }
        else if (step == 32 && age > 40 && c.player.isAlive()) { useCounter(c); next(34); }
        else if (step == 34 && age > 20 && c.screen instanceof BankScreen s) {
            var d = s.getMenu().data(); check(d.accountId().equals(accountId) && d.balance() == 12 && d.historySize() == 20, "Close/death/rejoin changed account or history");
            check(d.emeralds() == (second(c) ? 0 : 180), "Unexpected final inventory");
            check(historyIds.size() == 20, "Complete paginated history not read");
            if (reload) {
                Set<String> original = new HashSet<>(); previous.getAsJsonArray("history_ids").forEach(v -> original.add(v.getAsString()));
                check(original.equals(historyIds), "Restart changed transaction UUIDs");
            }
            JsonObject report = new JsonObject(); report.addProperty("player", c.getUser().getName()); report.addProperty("account_id", accountId);
            report.addProperty("card_id", cardId); report.addProperty("balance", d.balance()); report.addProperty("emeralds", d.emeralds());
            report.addProperty("history_count", d.historySize()); report.addProperty("restart_verified", reload); report.addProperty("passed", true);
            JsonArray ids = new JsonArray(); historyIds.forEach(ids::add); report.add("history_ids", ids);
            Files.writeString(c.gameDirectory.toPath().resolve(reload ? "phase2-reloaded.json" : "phase2-initial.json"), report.toString());
            finish(c, report.toString());
        } else if (step == 90 && age > 30 && c.screen instanceof BankScreen s) {
            check(!s.getMenu().data().available() && s.children().stream().filter(Button.class::isInstance).count() == 1, "Disabled bank accepted forged request");
            capture(c, "bank-disabled");
            if (Boolean.getBoolean("lastbet.smoke.stopServer")) { c.player.connection.sendCommand("stop"); next(91); }
            else finish(c, "corrupt bank disabled; world playable; deposit refused");
        } else if (step == 91 && age > 30 && c.screen instanceof DisconnectedScreen) {
            finish(c, "corrupt bank disabled; world playable; deposit refused; server stopped normally");
        }
    }
    private void advanceCapture(Minecraft c) {
        scaleIndex++;
        if (scaleIndex == 3) { scaleIndex = 0; pageIndex++; next(20); }
        else { c.options.guiScale().set(2 + scaleIndex); c.resizeDisplay(); next(22); }
    }
    private void prepareOperations() {
        add(BankAction.DEPOSIT_PAGE, 0, 192, BankManager.Result.NONE);
        add(BankAction.DEPOSIT_1, 1, 191, BankManager.Result.DEPOSITED);
        add(BankAction.DEPOSIT_16, 17, 175, BankManager.Result.DEPOSITED);
        add(BankAction.DEPOSIT_64, 81, 111, BankManager.Result.DEPOSITED);
        add(BankAction.DEPOSIT_ALL, 192, 0, BankManager.Result.DEPOSITED);
        add(BankAction.DEPOSIT_1, 192, 0, BankManager.Result.NOT_ENOUGH_EMERALDS);
        add(BankAction.HOME, 192, 0, BankManager.Result.NONE);
        add(BankAction.WITHDRAWAL_PAGE, 192, 0, BankManager.Result.NONE);
        add(BankAction.WITHDRAW_1, 191, 1, BankManager.Result.WITHDRAWN);
        add(BankAction.WITHDRAW_16, 175, 17, BankManager.Result.WITHDRAWN);
        add(BankAction.WITHDRAW_64, 111, 81, BankManager.Result.WITHDRAWN);
        add(BankAction.WITHDRAW_ALL, 0, 192, BankManager.Result.WITHDRAWN);
        add(BankAction.WITHDRAW_1, 0, 192, BankManager.Result.INSUFFICIENT_BALANCE);
        add(BankAction.HOME, 0, 192, BankManager.Result.NONE);
        add(BankAction.DEPOSIT_PAGE, 0, 192, BankManager.Result.NONE);
        for (int i = 1; i <= 12; i++) add(BankAction.DEPOSIT_1, i, 192 - i, BankManager.Result.DEPOSITED);
        add(BankAction.HOME, 12, 180, BankManager.Result.NONE);
        operations.add(new Operation(BankAction.HISTORY_PAGE, 0, 12, 180, BankManager.Result.NONE));
        operations.add(new Operation(BankAction.HISTORY_PAGE, 1, 12, 180, BankManager.Result.NONE));
        operations.add(new Operation(BankAction.HISTORY_PAGE, 0, 12, 180, BankManager.Result.NONE));
        add(BankAction.HOME, 12, 180, BankManager.Result.NONE);
    }
    private void add(BankAction a, long b, long e, BankManager.Result r) { operations.add(new Operation(a, 0, b, e, r)); }
    private static String key(BankAction action) { return switch (action) {
        case HOME -> "back"; case REGISTER -> "register"; case DEPOSIT_PAGE -> "deposit"; case WITHDRAWAL_PAGE -> "withdraw";
        case HISTORY_PAGE -> "history"; default -> action.name().toLowerCase(Locale.ROOT);
    }; }
    private static void press(Minecraft c, String key) {
        BankScreen s = (BankScreen) c.screen;
        String text = Component.translatable("screen.lastbet.bank." + key).getString();
        // HOME when already on HOME needs no network navigation; use the deposit page first.
        s.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> b.getMessage().getString().equals(text)).findFirst().orElseThrow(() -> new IllegalStateException("Missing widget " + key)).onPress();
    }
    private static com.shouyun.lastbet.item.BankCardData card(Minecraft c) {
        for (int i = 0; i < 36; i++) { var stack = c.player.getInventory().getItem(i); if (stack.is(BankRegistry.BANK_CARD)) return stack.get(BankRegistry.CARD_DATA); }
        return null;
    }
    private static void useCounter(Minecraft c) {
        int x = second(c) ? 10 : 0;
        c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(x + 0.5, 80.5, 1), Direction.SOUTH, new BlockPos(x, 80, 0), false));
    }
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private void next(int value) { step = value; age = 0; }
    private void finish(Minecraft c, String report) throws Exception {
        Files.writeString(c.gameDirectory.toPath().resolve("smoke-completed.txt"), report);
        TheLastBet.LOGGER.info("BANK_PHASE2_CLIENT_PASSED {}", report); next(99); c.stop();
    }
    private static void capture(Minecraft c, String name) {
        for (var child : c.screen.children()) if (child instanceof AbstractWidget widget) check(widget.getX() >= 0 && widget.getY() >= 0
                && widget.getRight() <= c.getWindow().getGuiScaledWidth() && widget.getBottom() <= c.getWindow().getGuiScaledHeight(), "Widget outside scaled viewport");
        Screenshot.grab(c.gameDirectory, (c.getUser().getName() + "-" + name + ".png"), c.getMainRenderTarget(),
                result -> TheLastBet.LOGGER.info("BANK_PHASE2_SCREENSHOT {}", result.getString()));
    }
}

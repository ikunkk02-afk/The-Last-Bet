// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.shouyun.lastbet.TheLastBet;
import com.shouyun.lastbet.bank.BankManager;
import com.shouyun.lastbet.client.screen.BankScreen;
import com.shouyun.lastbet.item.BankCardData;
import com.shouyun.lastbet.registry.BankRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Drives real vanilla packets against the isolated loopback test server. */
public final class BankClientSmoke implements ClientModInitializer {
    private int ticks;
    private int step;
    private int stageTicks;
    private String accountId;
    private String cardId;
    private boolean reconnect;
    private JsonObject previous;

    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                tick(client);
            } catch (Exception failure) {
                TheLastBet.LOGGER.error("BANK_CLIENT_SMOKE_FAILED at step " + step, failure);
                try { Files.writeString(client.gameDirectory.toPath().resolve("smoke-failed.txt"), failure.toString()); }
                catch (Exception ignored) { TheLastBet.LOGGER.error("Could not write smoke failure report"); }
                client.stop();
            }
        });
    }

    private void tick(Minecraft client) throws Exception {
        client.getToasts().clear();
        ticks++;
        stageTicks++;
        if (ticks > 2400) throw new IllegalStateException("Timed out at step " + step + " screen=" + client.screen);
        if (client.getOverlay() != null) return;
        if (step == 0 && client.screen instanceof TitleScreen && ticks > 60) {
            Path report = client.gameDirectory.toPath().resolve("smoke-initial.json");
            reconnect = Files.exists(report);
            if (reconnect) previous = JsonParser.parseString(Files.readString(report)).getAsJsonObject();
            ConnectScreen.startConnecting(new TitleScreen(), client, new ServerAddress("127.0.0.1", 25589),
                    new ServerData("Bank smoke server", "127.0.0.1:25589", ServerData.Type.OTHER), false, null);
            next(1);
        } else if (step == 1 && client.player != null && client.level != null && stageTicks > 40) {
            client.player.connection.sendCommand("gamemode survival");
            useCounter(client);
            next(2);
        } else if (step == 2 && client.screen instanceof BankScreen screen && stageTicks > 20) {
            var data = screen.getMenu().data();
            if (Boolean.getBoolean("lastbet.smoke.expectUnavailable")) {
                check(!data.available(), "Corrupt bank was unexpectedly available");
                client.gameMode.handleInventoryButtonClick(screen.getMenu().containerId, 0);
                next(16);
            } else if (reconnect) {
                check(data.hasAccount() && data.accountId().equals(previous.get("account_id").getAsString()), "Account ID changed after restart");
                check(data.balance() == 0, "Balance changed after restart");
                accountId = data.accountId();
                cardId = previous.get("card_id").getAsString();
                if (client.getUser().getName().equals("BankSmokeA")) check(findCard(client) != null, "Card missing after restart");
                capture(client, "reloaded-scale2");
                if (client.getUser().getName().equals("BankSmokeA")) {
                    client.gameMode.handleInventoryButtonClick(screen.getMenu().containerId, 0);
                    next(5);
                } else next(12);
            } else {
                check(!data.hasAccount(), "Unexpected existing account");
                capture(client, "unregistered-scale2");
                pressRegister(screen);
                next(3);
            }
        } else if (step == 3 && client.screen instanceof BankScreen screen && stageTicks > 20) {
            var data = screen.getMenu().data();
            if (client.getUser().getName().equals("BankSmokeB") && data.message() == BankManager.Result.INVENTORY_FULL) {
                check(!data.hasAccount(), "Full inventory created an account");
                capture(client, "full-inventory-scale2");
                client.player.connection.sendCommand("clear @s minecraft:cobblestone");
                next(4);
            } else if (data.hasAccount()) {
                check(data.balance() == 0 && !data.deliveryPending(), "Incorrect initial balance or pending delivery");
                BankCardData card = findCard(client);
                check(card != null && card.ownerId().equals(client.player.getUUID()) && card.accountId().toString().equals(data.accountId()), "Card binding mismatch");
                accountId = data.accountId();
                cardId = card.cardId().toString();
                capture(client, "registered-scale2");
                client.gameMode.handleInventoryButtonClick(screen.getMenu().containerId, 0);
                next(5);
            }
        } else if (step == 4 && stageTicks > 30 && client.screen instanceof BankScreen screen) {
            pressRegister(screen);
            next(3);
        } else if (step == 5 && stageTicks > 20 && client.screen instanceof BankScreen screen) {
            check(screen.getMenu().data().accountId().equals(accountId), "Repeated request changed account");
            check(cardCount(client) == 1, "Repeated request duplicated card");
            check(screen.getMenu().data().message() == BankManager.Result.ALREADY_EXISTS, "Duplicate was not rejected");
            client.options.guiScale().set(3);
            client.resizeDisplay();
            next(6);
        } else if (step == 6 && stageTicks > 20) {
            capture(client, "registered-scale3");
            client.options.guiScale().set(4);
            client.resizeDisplay();
            next(7);
        } else if (step == 7 && stageTicks > 20) {
            capture(client, "registered-scale4");
            client.options.guiScale().set(2);
            client.resizeDisplay();
            // Exercise the actual close button and vanilla container close packet.
            BankScreen screen = (BankScreen) client.screen;
            List<Button> buttons = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).toList();
            check(buttons.size() == 1, "Registered account should have only Close");
            buttons.getFirst().onPress();
            next(8);
        } else if (step == 8 && stageTicks > 20) {
            check(!(client.screen instanceof BankScreen), "Close button did not close the menu");
            client.setScreen(new InventoryScreen(client.player));
            int x = (client.getWindow().getGuiScaledWidth() - 176) / 2 + 16;
            int y = (client.getWindow().getGuiScaledHeight() - 166) / 2 + 150;
            GLFW.glfwSetCursorPos(client.getWindow().getWindow(), x * client.getWindow().getGuiScale(), y * client.getWindow().getGuiScale());
            next(14);
        } else if (step == 14 && stageTicks > 30) {
            Screenshot.grab(client.gameDirectory, "card-tooltip.png", client.getMainRenderTarget(),
                    result -> TheLastBet.LOGGER.info("BANK_SMOKE_CARD_TOOLTIP {}", result.getString()));
            client.player.closeContainer();
            if (client.getUser().getName().equals("BankSmokeB")) {
                client.player.connection.sendCommand("kill");
                next(9);
            } else {
                useCounter(client);
                next(11);
            }
        } else if (step == 9 && stageTicks > 30 && client.player != null && client.player.isDeadOrDying()) {
            client.player.respawn();
            next(10);
        } else if (step == 10 && stageTicks > 40 && client.player != null && client.player.isAlive()) {
            useCounter(client);
            next(11);
        } else if (step == 11 && stageTicks > 20 && client.screen instanceof BankScreen screen) {
            check(screen.getMenu().data().accountId().equals(accountId), "Reopen/death changed account ID");
            check(screen.getMenu().data().balance() == 0, "Reopen/death changed balance");
            capture(client, "reopened-scale2");
            next(12);
        } else if (step == 12 && stageTicks > (reconnect ? 200 : 40)) {
            JsonObject report = new JsonObject();
            report.addProperty("player", client.getUser().getName());
            report.addProperty("owner_uuid", client.player.getUUID().toString());
            report.addProperty("account_id", accountId);
            report.addProperty("card_id", cardId);
            report.addProperty("balance", 0);
            report.addProperty("restart_verified", reconnect);
            report.addProperty("death_verified", client.getUser().getName().equals("BankSmokeB"));
            report.addProperty("passed", true);
            Files.writeString(client.gameDirectory.toPath().resolve(reconnect ? "smoke-reloaded.json" : "smoke-initial.json"), report.toString());
            TheLastBet.LOGGER.info("BANK_CLIENT_SMOKE_PASSED {}", report);
            Files.writeString(client.gameDirectory.toPath().resolve("smoke-completed.txt"), "passed");
            next(13);
            client.stop();
        } else if (step == 16 && stageTicks > 30 && client.screen instanceof BankScreen screen) {
            check(!screen.getMenu().data().available(), "Corrupt bank became available after request");
            check(screen.children().stream().filter(Button.class::isInstance).count() == 1, "Disabled bank must only offer Close");
            capture(client, "bank-disabled");
            Files.writeString(client.gameDirectory.toPath().resolve("smoke-completed.txt"), "corrupt bank disabled; world playable; request rejected");
            TheLastBet.LOGGER.info("BANK_CORRUPTION_CLIENT_PASSED");
            next(13);
            client.stop();
        }
    }

    private void next(int value) { step = value; stageTicks = 0; }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static void useCounter(Minecraft client) {
        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(0.5, 80.5, 1), Direction.SOUTH, new BlockPos(0, 80, 0), false));
    }
    private static void pressRegister(BankScreen screen) {
        screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).findFirst().orElseThrow().onPress();
    }
    private static int cardCount(Minecraft client) {
        int count = 0;
        for (int i = 0; i < client.player.getInventory().getContainerSize(); i++) {
            var stack = client.player.getInventory().getItem(i);
            if (stack.is(BankRegistry.BANK_CARD)) count += stack.getCount();
        }
        return count;
    }
    private static BankCardData findCard(Minecraft client) {
        for (int i = 0; i < client.player.getInventory().getContainerSize(); i++) {
            var stack = client.player.getInventory().getItem(i);
            BankCardData data = stack.get(BankRegistry.CARD_DATA);
            if (stack.is(BankRegistry.BANK_CARD) && data != null) return data;
        }
        return null;
    }
    private static void capture(Minecraft client, String name) {
        check(client.screen instanceof BankScreen, "Expected bank screen for screenshot");
        var widgets = client.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).toList();
        for (var widget : widgets) {
            check(widget.getX() >= 0 && widget.getY() >= 0 && widget.getRight() <= client.getWindow().getGuiScaledWidth()
                    && widget.getBottom() <= client.getWindow().getGuiScaledHeight(), "Widget outside scaled window");
        }
        Screenshot.grab(client.gameDirectory, name + ".png", client.getMainRenderTarget(),
                result -> TheLastBet.LOGGER.info("BANK_SMOKE_SCREENSHOT {} {}", name, result.getString()));
    }
}

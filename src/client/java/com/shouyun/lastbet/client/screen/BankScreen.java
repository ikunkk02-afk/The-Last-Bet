// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.client.screen;

import com.shouyun.lastbet.bank.BankManager;
import com.shouyun.lastbet.bank.BankTransaction;
import com.shouyun.lastbet.menu.BankAction;
import com.shouyun.lastbet.menu.BankMenu;
import com.shouyun.lastbet.menu.BankMenuData;
import com.shouyun.lastbet.menu.BankNetworking;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class BankScreen extends AbstractContainerScreen<BankMenu> {
    private long shownRevision = -1;
    private UUID submitted;
    private int shownPage = -1, shownHistoryPage = -1;
    private int scroll;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public BankScreen(BankMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 308;
        imageHeight = 228;
    }

    @Override protected void init() {
        imageWidth = Math.min(340, width - 12);
        imageHeight = Math.min(286, height - 12);
        super.init();
        BankMenuData d = menu.data();
        if (d.page() != shownPage || d.historyPage() != shownHistoryPage) scroll = 0;
        shownPage = d.page(); shownHistoryPage = d.historyPage(); shownRevision = d.revision();
        if (submitted != null && !submitted.equals(d.token())) submitted = null;
        boolean ready = d.available() && !d.locked();
        if (ready && d.page() == BankMenuData.HOME) {
            if (!d.hasAccount() || d.deliveryPending()) {
                button(d.deliveryPending() ? "collect_card" : "register", 14, imageHeight - 64, imageWidth - 28, BankAction.REGISTER, 0);
            } else {
                int size = (imageWidth - 36) / 3;
                button("deposit", 12, imageHeight - 64, size, BankAction.DEPOSIT_PAGE, 0);
                button("withdraw", 18 + size, imageHeight - 64, size, BankAction.WITHDRAWAL_PAGE, 0);
                button("history", 24 + 2 * size, imageHeight - 64, size, BankAction.HISTORY_PAGE, 0);
            }
        } else if (ready && (d.page() == BankMenuData.DEPOSIT || d.page() == BankMenuData.WITHDRAWAL)) {
            boolean deposit = d.page() == BankMenuData.DEPOSIT;
            BankAction[] actions = deposit
                    ? new BankAction[]{BankAction.DEPOSIT_1, BankAction.DEPOSIT_16, BankAction.DEPOSIT_64, BankAction.DEPOSIT_ALL}
                    : new BankAction[]{BankAction.WITHDRAW_1, BankAction.WITHDRAW_16, BankAction.WITHDRAW_64, BankAction.WITHDRAW_ALL};
            String[] quantities = {"1", "16", "64", "all"};
            int size = (imageWidth - 36) / 2;
            for (int i = 0; i < 4; i++) button((deposit ? "deposit_" : "withdraw_") + quantities[i],
                    12 + i % 2 * (size + 12), 96 + i / 2 * 28, size, actions[i], 0);
        } else if (ready && d.page() == BankMenuData.HISTORY) {
            Button previous = button("previous", 12, imageHeight - 58, 74, BankAction.HISTORY_PAGE, Math.max(0, d.historyPage() - 1));
            previous.active &= d.historyPage() > 0;
            Button next = button("next", imageWidth - 86, imageHeight - 58, 74, BankAction.HISTORY_PAGE, d.historyPage() + 1);
            next.active &= (long) (d.historyPage() + 1) * 10 < d.historySize();
        }
        if (d.page() != BankMenuData.HOME && ready) button("back", 12, imageHeight - 30, (imageWidth - 36) / 2, BankAction.HOME, 0);
        int closeX = d.page() == BankMenuData.HOME || !ready ? (imageWidth - 120) / 2 : imageWidth / 2 + 6;
        int closeWidth = d.page() == BankMenuData.HOME || !ready ? 120 : (imageWidth - 36) / 2;
        addRenderableWidget(Button.builder(Component.translatable("screen.lastbet.bank.close"), b -> onClose())
                .bounds(leftPos + closeX, topPos + imageHeight - 30, closeWidth, 20).build());
    }

    private Button button(String key, int x, int y, int size, BankAction action, int page) {
        Button button = addRenderableWidget(Button.builder(Component.translatable("screen.lastbet.bank." + key), b -> request(action, page))
                .bounds(leftPos + x, topPos + y, size, 20).build());
        button.active = submitted == null;
        return button;
    }

    private void request(BankAction action, int page) {
        if (submitted != null) return;
        BankMenuData d = menu.data();
        submitted = d.token();
        children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> !b.getMessage().getString().equals(Component.translatable("screen.lastbet.bank.close").getString()))
                .forEach(b -> b.active = false);
        ClientPlayNetworking.send(new BankNetworking.Request(menu.containerId, d.session(), d.token(), action, page));
    }

    @Override protected void containerTick() {
        super.containerTick();
        if (shownRevision != menu.data().revision()) init(minecraft, width, height);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (menu.data().page() == BankMenuData.HISTORY) {
            int visibleHeight = imageHeight - 118;
            scroll = Math.max(0, Math.min(Math.max(0, menu.data().entries().size() * 27 - visibleHeight), scroll - (int) (vertical * 27)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override protected void renderBg(GuiGraphics g, float delta, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF373737);
        g.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFFFFFFFF);
        g.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xFFC6C6C6);
        g.fill(leftPos + 3, topPos + imageHeight - 3, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFF555555);
        g.fill(leftPos + imageWidth - 3, topPos + 3, leftPos + imageWidth - 1, topPos + imageHeight - 3, 0xFF555555);
        BankMenuData d = menu.data();
        if (d.available() && d.page() == BankMenuData.HISTORY) {
            int bottom = topPos + imageHeight - 66;
            g.enableScissor(leftPos + 12, topPos + 52, leftPos + imageWidth - 12, bottom);
            if (d.entries().isEmpty()) g.drawString(font, Component.translatable("screen.lastbet.bank.no_history"), leftPos + 14, topPos + 60, 0x404040, false);
            for (int i = 0; i < d.entries().size(); i++) {
                BankTransaction t = d.entries().get(i);
                int y = topPos + 54 + i * 27 - scroll;
                g.drawString(font, TIME.format(Instant.ofEpochMilli(t.timestamp()).atZone(ZoneId.systemDefault())), leftPos + 14, y, 0x404040, false);
                String detail = Component.translatable("screen.lastbet.bank.entry", Component.translatable("screen.lastbet.bank." + (t.type() == BankTransaction.Type.DEPOSIT ? "deposit" : "withdraw")), t.amount(), t.balanceAfter()).getString();
                g.drawString(font, font.plainSubstrByWidth(detail, imageWidth - 28), leftPos + 14, y + 11, 0x185C27, false);
            }
            g.disableScissor();
        }
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        BankMenuData d = menu.data();
        Component heading = d.page() == BankMenuData.HOME ? title : Component.translatable("screen.lastbet.bank." + switch (d.page()) {
            case BankMenuData.DEPOSIT -> "deposit"; case BankMenuData.WITHDRAWAL -> "withdraw"; default -> "history";
        });
        g.drawString(font, heading, (imageWidth - font.width(heading)) / 2, 15, 0x404040, false);
        if (!d.available()) {
            line(g, Component.translatable("screen.lastbet.bank.unavailable"), 50, 0xA02020);
            wrapped(g, Component.translatable("screen.lastbet.bank.unavailable_help"), 78, 0xA02020);
        } else if (d.locked()) {
            wrapped(g, Component.translatable("message.lastbet.bank.recovery_pending"), 50, 0xA02020);
        } else if (!d.hasAccount()) {
            line(g, Component.translatable("screen.lastbet.bank.welcome"), 44, 0x404040);
            line(g, Component.translatable("screen.lastbet.bank.unregistered"), 72, 0x404040);
        } else if (d.page() == BankMenuData.HOME) {
            line(g, Component.translatable("screen.lastbet.bank.player", d.playerName()), 34, 0x404040);
            line(g, Component.translatable("screen.lastbet.bank.account"), 50, 0x404040);
            line(g, Component.literal(d.accountId().substring(0, 18)), 64, 0x404040);
            line(g, Component.literal(d.accountId().substring(18)), 75, 0x404040);
            line(g, Component.translatable("screen.lastbet.bank.balance", d.balance()), 94, 0x404040);
            line(g, Component.translatable("screen.lastbet.bank.emeralds", d.emeralds()), 110, 0x404040);
            if (d.deliveryPending()) line(g, Component.translatable("screen.lastbet.bank.pending"), 126, 0xA02020);
        } else if (d.page() == BankMenuData.HISTORY) {
            line(g, Component.translatable("screen.lastbet.bank.timezone", ZoneId.systemDefault().toString()), 34, 0x404040);
            Component count = Component.translatable("screen.lastbet.bank.page", d.historyPage() + 1, Math.max(1, (d.historySize() + 9) / 10));
            g.drawString(font, count, (imageWidth - font.width(count)) / 2, imageHeight - 52, 0x404040, false);
        } else {
            line(g, Component.translatable("screen.lastbet.bank.balance", d.balance()), 39, 0x404040);
            line(g, Component.translatable("screen.lastbet.bank.emeralds", d.emeralds()), 56, 0x404040);
            line(g, Component.translatable("screen.lastbet.bank.rate"), 76, 0x404040);
        }
        if (d.available() && d.page() != BankMenuData.HISTORY && d.message() != BankManager.Result.NONE) {
            boolean success = d.message() == BankManager.Result.OPENED || d.message() == BankManager.Result.DEPOSITED || d.message() == BankManager.Result.WITHDRAWN;
            wrapped(g, Component.translatable(d.message().translationKey()), d.page() == BankMenuData.HOME ? 142 : 156, success ? 0x185C27 : 0xA02020);
        }
    }
    private void line(GuiGraphics g, Component text, int y, int color) {
        g.drawString(font, font.plainSubstrByWidth(text.getString(), imageWidth - 28), 14, y, color, false);
    }
    private void wrapped(GuiGraphics g, Component text, int y, int color) {
        for (var part : font.split(text, imageWidth - 28)) {
            if (y > imageHeight - 74 && menu.data().page() == BankMenuData.HOME) break;
            if (y > imageHeight - 42) break;
            g.drawString(font, part, 14, y, color, false); y += 10;
        }
    }
}

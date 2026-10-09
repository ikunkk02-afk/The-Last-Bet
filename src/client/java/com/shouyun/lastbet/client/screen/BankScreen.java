// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.client.screen;

import com.shouyun.lastbet.bank.BankManager;
import com.shouyun.lastbet.menu.BankMenu;
import com.shouyun.lastbet.menu.BankMenuData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class BankScreen extends AbstractContainerScreen<BankMenu> {
    public BankScreen(BankMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 280;
        imageHeight = 228;
    }

    @Override
    protected void init() {
        super.init();
        BankMenuData data = menu.data();
        boolean canRegister = data.available() && (!data.hasAccount() || data.deliveryPending());
        if (canRegister) {
            addRenderableWidget(Button.builder(Component.translatable(data.deliveryPending()
                    ? "screen.lastbet.bank.collect_card" : "screen.lastbet.bank.register"), button -> {
                button.active = false;
                if (minecraft != null && minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, BankMenu.REGISTER_BUTTON);
                }
            }).bounds(leftPos + 16, topPos + 196, 120, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.lastbet.bank.close"), button -> onClose())
                .bounds(leftPos + (canRegister ? 144 : 80), topPos + 196, 120, 20).build());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float delta, int mouseX, int mouseY) {
        // Vanilla-style bevel; no renderer or GUI dependencies on the dedicated server.
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF373737);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFFFFFFFF);
        graphics.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xFFC6C6C6);
        graphics.fill(leftPos + 3, topPos + imageHeight - 3, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFF555555);
        graphics.fill(leftPos + imageWidth - 3, topPos + 3, leftPos + imageWidth - 1, topPos + imageHeight - 3, 0xFF555555);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        BankMenuData data = menu.data();
        graphics.drawString(font, title, (imageWidth - font.width(title)) / 2, 15, 0x404040, false);
        if (!data.available()) {
            line(graphics, Component.translatable("screen.lastbet.bank.welcome"), 44, 0x404040);
            line(graphics, Component.translatable("screen.lastbet.bank.unavailable"), 72, 0xA02020);
            wrapped(graphics, Component.translatable("screen.lastbet.bank.unavailable_help"), 104, 0xA02020);
        } else if (!data.hasAccount()) {
            line(graphics, Component.translatable("screen.lastbet.bank.welcome"), 44, 0x404040);
            line(graphics, Component.translatable("screen.lastbet.bank.unregistered"), 72, 0x404040);
        } else {
            line(graphics, Component.translatable("screen.lastbet.bank.player", data.playerName()), 42, 0x404040);
            line(graphics, Component.translatable("screen.lastbet.bank.account"), 64, 0x404040);
            String id = data.accountId();
            line(graphics, Component.literal(id.substring(0, 18)), 80, 0x404040);
            line(graphics, Component.literal(id.substring(18)), 91, 0x404040);
            line(graphics, Component.translatable("screen.lastbet.bank.balance", data.balance()), 113, 0x404040);
            line(graphics, Component.translatable(data.deliveryPending()
                    ? "screen.lastbet.bank.pending" : "screen.lastbet.bank.normal"), 133, 0x404040);
        }
        if (data.available() && data.message() != BankManager.Result.NONE) {
            wrapped(graphics, Component.translatable(data.message().translationKey()), 155,
                    data.message() == BankManager.Result.OPENED ? 0x185C27 : 0xA02020);
        }
    }

    private void line(GuiGraphics graphics, Component text, int y, int color) {
        graphics.drawString(font, text, 16, y, color, false);
    }

    private void wrapped(GuiGraphics graphics, Component text, int y, int color) {
        for (var part : font.split(text, imageWidth - 32)) {
            graphics.drawString(font, part, 16, y, color, false);
            y += 10;
        }
    }
}

// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.menu;

import com.shouyun.lastbet.bank.BankManager;
import com.shouyun.lastbet.registry.BankRegistry;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public final class BankMenu extends AbstractContainerMenu {
    public static final int REGISTER_BUTTON = 0;
    private final BankMenuData data;
    private final ServerLevel level;
    private final BlockPos counter;

    public BankMenu(int id, Inventory inventory, BankMenuData data) {
        this(id, data, null, BlockPos.ZERO);
    }

    public BankMenu(int id, BankMenuData data, ServerLevel level, BlockPos counter) {
        super(BankRegistry.BANK_MENU, id);
        this.data = data;
        this.level = level;
        this.counter = counter.immutable();
    }

    public BankMenuData data() { return data; }

    public static void open(ServerPlayer player, BlockPos counter) {
        openView(player, counter, BankManager.Result.NONE);
        BankManager manager = BankManager.get(player.server);
        if (manager.isAvailable() && manager.findByOwner(player.getUUID()).filter(a -> a.deliveryPending()).isPresent()) {
            BankManager.Result result = manager.register(player);
            openView(player, counter, result);
        }
    }

    private static void openView(ServerPlayer player, BlockPos counter, BankManager.Result result) {
        BankMenuData snapshot = BankMenuData.snapshot(player, result);
        ServerLevel level = player.serverLevel();
        player.openMenu(new ExtendedScreenHandlerFactory<BankMenuData>() {
            @Override public BankMenuData getScreenOpeningData(ServerPlayer viewer) { return snapshot; }
            @Override public Component getDisplayName() { return Component.translatable("screen.lastbet.bank.title"); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player viewer) {
                return new BankMenu(id, snapshot, level, counter);
            }
        });
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer serverPlayer) || player.containerMenu != this
                || button != REGISTER_BUTTON || !stillValid(player)) return false;
        BankManager.Result result = BankManager.get(serverPlayer.server).register(serverPlayer);
        openView(serverPlayer, counter, result);
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        if (level == null) return player.level().isClientSide;
        return player.isAlive() && !player.isSpectator() && player.level() == level && level.hasChunkAt(counter)
                && level.getBlockState(counter).is(BankRegistry.BANK_COUNTER)
                && player.distanceToSqr(counter.getX() + 0.5, counter.getY() + 0.5, counter.getZ() + 0.5) <= 64.0;
    }

    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}

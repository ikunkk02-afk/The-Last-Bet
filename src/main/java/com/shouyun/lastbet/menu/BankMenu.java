// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.menu;

import com.shouyun.lastbet.bank.BankManager;
import com.shouyun.lastbet.bank.BankTransaction;
import com.shouyun.lastbet.bank.EmeraldInventory;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
    private BankMenuData data;
    private UUID previousToken;
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

    public void acceptSnapshot(BankMenuData incoming) {
        if (data.session().equals(incoming.session()) && incoming.revision() > data.revision()) data = incoming;
    }

    public boolean handle(ServerPlayer player, BankNetworking.Request request) {
        if (player.containerMenu != this || request.menuId() != containerId || !stillValid(player)
                || !request.session().equals(data.session())) return false;
        if (!request.token().equals(data.token())) {
            if (request.token().equals(previousToken)) send(player);
            return false;
        }
        previousToken = data.token();
        UUID next = UUID.randomUUID();
        BankManager manager = BankManager.get(player.server);
        BankManager.Result result = BankManager.Result.NONE;
        int page = data.page(), historyPage = data.historyPage();
        boolean accountReady = data.hasAccount() && !data.deliveryPending();
        if (!manager.isAvailable()) result = BankManager.Result.UNAVAILABLE;
        else if (data.locked()) result = BankManager.Result.RECOVERY_PENDING;
        else switch (request.action()) {
            case REGISTER -> { return clickMenuButton(player, REGISTER_BUTTON); }
            case HOME -> page = BankMenuData.HOME;
            case DEPOSIT_PAGE -> { if (accountReady) page = BankMenuData.DEPOSIT; else result = BankManager.Result.INVALID_REQUEST; }
            case WITHDRAWAL_PAGE -> { if (accountReady) page = BankMenuData.WITHDRAWAL; else result = BankManager.Result.INVALID_REQUEST; }
            case HISTORY_PAGE -> {
                if (!accountReady || request.page() < 0 || request.page() > Math.max(0, (data.historySize() - 1) / 10)) result = BankManager.Result.INVALID_REQUEST;
                else { page = BankMenuData.HISTORY; historyPage = request.page(); }
            }
            default -> {
                boolean deposit = request.action().name().startsWith("DEPOSIT_");
                if (!accountReady || page != (deposit ? BankMenuData.DEPOSIT : BankMenuData.WITHDRAWAL)) result = BankManager.Result.INVALID_REQUEST;
                else {
                    long amount = switch (request.action()) {
                        case DEPOSIT_1, WITHDRAW_1 -> 1;
                        case DEPOSIT_16, WITHDRAW_16 -> 16;
                        case DEPOSIT_64, WITHDRAW_64 -> 64;
                        case DEPOSIT_ALL -> EmeraldInventory.count(player);
                        case WITHDRAW_ALL -> manager.findByOwner(player.getUUID()).orElseThrow().balance();
                        default -> throw new IllegalStateException("Unknown bank action");
                    };
                    result = manager.transact(player, deposit ? BankTransaction.Type.DEPOSIT : BankTransaction.Type.WITHDRAWAL, amount);
                }
            }
        }
        data = BankMenuData.snapshot(player, result, data.session(), next, data.revision() + 1, page, historyPage);
        if (player.connection.isAcceptingMessages()) send(player);
        return true;
    }

    private void send(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, BankNetworking.Snapshot.TYPE)) ServerPlayNetworking.send(player, new BankNetworking.Snapshot(containerId, data));
    }

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

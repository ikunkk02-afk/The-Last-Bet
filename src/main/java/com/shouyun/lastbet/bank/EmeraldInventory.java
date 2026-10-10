// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.bank;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Plans changes to the 36 main slots only. Never uses Inventory.add (which can drop items). */
public final class EmeraldInventory {
    private EmeraldInventory() {}

    public static long count(ServerPlayer player) {
        long total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Items.EMERALD)) total = Math.addExact(total, stack.getCount());
        }
        return total;
    }

    public static ListTag snapshot(ServerPlayer player) {
        return main(player.getInventory().save(new ListTag()));
    }

    public static ListTag main(ListTag inventory) {
        if (!inventory.isEmpty() && inventory.getElementType() != Tag.TAG_COMPOUND) throw new IllegalArgumentException("Invalid inventory list");
        CompoundTag[] slots = new CompoundTag[36];
        for (Tag value : inventory) {
            CompoundTag entry = (CompoundTag) value;
            if (!entry.contains("Slot", Tag.TAG_BYTE)) throw new IllegalArgumentException("Missing inventory slot");
            int slot = entry.getByte("Slot") & 255;
            if (slot < 36) {
                if (slots[slot] != null) throw new IllegalArgumentException("Duplicate inventory slot");
                slots[slot] = entry.copy();
            }
        }
        ListTag result = new ListTag();
        for (CompoundTag slot : slots) if (slot != null) result.add(slot);
        return result;
    }

    public static ListTag main(CompoundTag playerTag) {
        if (!playerTag.contains("Inventory", Tag.TAG_LIST)) throw new IllegalArgumentException("Missing player inventory");
        return main((ListTag) playerTag.get("Inventory"));
    }

    private static List<ItemStack> decode(ListTag main, HolderLookup.Provider registries) {
        if (!main.equals(main(main))) throw new IllegalArgumentException("Noncanonical main inventory snapshot");
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < 36; i++) result.add(ItemStack.EMPTY);
        for (Tag value : main) {
            CompoundTag entry = (CompoundTag) value;
            ItemStack stack = ItemStack.parse(registries, entry).orElseThrow(() -> new IllegalArgumentException("Invalid inventory item"));
            if (stack.isEmpty() || stack.getCount() > Math.min(64, stack.getMaxStackSize())) throw new IllegalArgumentException("Invalid inventory stack count");
            result.set(entry.getByte("Slot") & 255, stack);
        }
        return result;
    }

    public static ListTag plan(ListTag before, BankTransaction.Type type, long amount, HolderLookup.Provider registries) {
        if (amount <= 0 || amount > 36L * 64) throw new IllegalArgumentException("Amount cannot fit main inventory");
        var slots = decode(before, registries);
        long remaining = amount;
        if (type == BankTransaction.Type.DEPOSIT) {
            for (ItemStack stack : slots) if (stack.is(Items.EMERALD)) {
                int remove = (int) Math.min(remaining, stack.getCount());
                stack.shrink(remove);
                remaining -= remove;
            }
        } else {
            ItemStack emerald = new ItemStack(Items.EMERALD);
            for (ItemStack stack : slots) if (ItemStack.isSameItemSameComponents(stack, emerald)) {
                int add = (int) Math.min(remaining, Math.max(0, Math.min(64, stack.getMaxStackSize()) - stack.getCount()));
                stack.grow(add);
                remaining -= add;
            }
            for (int i = 0; i < 36 && remaining > 0; i++) if (slots.get(i).isEmpty()) {
                int add = (int) Math.min(remaining, emerald.getMaxStackSize());
                slots.set(i, new ItemStack(Items.EMERALD, add));
                remaining -= add;
            }
        }
        if (remaining != 0) throw new IllegalArgumentException("Insufficient emeralds or main inventory capacity");
        ListTag after = new ListTag();
        for (int i = 0; i < 36; i++) if (!slots.get(i).isEmpty()) {
            CompoundTag entry = (CompoundTag) slots.get(i).save(registries, new CompoundTag());
            entry.putByte("Slot", (byte) i);
            after.add(entry);
        }
        return after;
    }

    public static void apply(ServerPlayer player, ListTag snapshot) {
        var slots = decode(snapshot, player.registryAccess());
        for (int i = 0; i < 36; i++) player.getInventory().setItem(i, slots.get(i));
        player.getInventory().setChanged();
    }

    public static void apply(CompoundTag playerTag, ListTag snapshot) {
        ListTag inventory = playerTag.getList("Inventory", Tag.TAG_COMPOUND);
        ListTag result = snapshot.copy();
        for (Tag value : inventory) {
            CompoundTag entry = (CompoundTag) value;
            if ((entry.getByte("Slot") & 255) >= 36) result.add(entry.copy());
        }
        playerTag.put("Inventory", result);
    }
}

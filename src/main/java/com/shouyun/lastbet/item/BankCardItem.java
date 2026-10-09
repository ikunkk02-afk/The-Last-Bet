// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.item;

import com.shouyun.lastbet.registry.BankRegistry;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class BankCardItem extends Item {
    public BankCardItem(Properties properties) { super(properties.stacksTo(1)); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        BankCardData data = stack.get(BankRegistry.CARD_DATA);
        if (data == null) {
            tooltip.add(Component.translatable("tooltip.lastbet.bank_card.unbound").withStyle(ChatFormatting.RED));
        } else {
            tooltip.add(Component.translatable("tooltip.lastbet.bank_card.owner", data.ownerName()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.lastbet.bank_card.bound").withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.translatable("tooltip.lastbet.bank_card.verification").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}

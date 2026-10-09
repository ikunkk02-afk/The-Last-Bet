// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.registry;

import com.shouyun.lastbet.TheLastBet;
import com.shouyun.lastbet.block.BankCounterBlock;
import com.shouyun.lastbet.item.BankCardData;
import com.shouyun.lastbet.item.BankCardItem;
import com.shouyun.lastbet.menu.BankMenu;
import com.shouyun.lastbet.menu.BankMenuData;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class BankRegistry {
    public static final DataComponentType<BankCardData> CARD_DATA = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            TheLastBet.id("bank_card_data"), DataComponentType.<BankCardData>builder()
                    .persistent(BankCardData.CODEC).networkSynchronized(BankCardData.STREAM_CODEC).build());
    public static final BankCounterBlock BANK_COUNTER = Registry.register(BuiltInRegistries.BLOCK,
            TheLastBet.id("bank_counter"), new BankCounterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD)));
    public static final Item BANK_COUNTER_ITEM = Registry.register(BuiltInRegistries.ITEM,
            TheLastBet.id("bank_counter"), new BlockItem(BANK_COUNTER, new Item.Properties()));
    public static final BankCardItem BANK_CARD = Registry.register(BuiltInRegistries.ITEM,
            TheLastBet.id("bank_card"), new BankCardItem(new Item.Properties()));
    public static final ExtendedScreenHandlerType<BankMenu, BankMenuData> BANK_MENU = Registry.register(BuiltInRegistries.MENU,
            TheLastBet.id("bank"), new ExtendedScreenHandlerType<>(BankMenu::new, BankMenuData.STREAM_CODEC));

    private BankRegistry() {}
    public static void initialize() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> entries.accept(BANK_COUNTER_ITEM));
    }
}

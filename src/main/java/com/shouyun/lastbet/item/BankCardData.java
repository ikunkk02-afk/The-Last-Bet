// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record BankCardData(UUID ownerId, UUID accountId, UUID cardId, String ownerName) {
    public static final Codec<BankCardData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("owner").forGetter(BankCardData::ownerId),
            UUIDUtil.CODEC.fieldOf("account").forGetter(BankCardData::accountId),
            UUIDUtil.CODEC.fieldOf("card").forGetter(BankCardData::cardId),
            Codec.STRING.fieldOf("owner_name").forGetter(BankCardData::ownerName)
    ).apply(instance, BankCardData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BankCardData> STREAM_CODEC = StreamCodec.of(
            (buffer, data) -> {
                buffer.writeUUID(data.ownerId());
                buffer.writeUUID(data.accountId());
                buffer.writeUUID(data.cardId());
                buffer.writeUtf(data.ownerName(), 64);
            }, buffer -> new BankCardData(buffer.readUUID(), buffer.readUUID(), buffer.readUUID(), buffer.readUtf(64)));
}

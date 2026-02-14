package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> Server packet: request setting a villager's hunger to a specific value (debug editor).
 */
public record HungerSetPayload(int entityId, int hunger) implements CustomPacketPayload {

    public static final Type<HungerSetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "hunger_set"));

    public static final StreamCodec<FriendlyByteBuf, HungerSetPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, HungerSetPayload::entityId,
                    ByteBufCodecs.VAR_INT, HungerSetPayload::hunger,
                    HungerSetPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

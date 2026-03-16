package com.aetherianartificer.townstead.profession;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> Server query for available professions in the player's village.
 */
//? if neoforge {
public record ProfessionQueryPayload() implements CustomPacketPayload {
//?} else {
/*public record ProfessionQueryPayload() {
*///?}

    //? if neoforge {
    public static final Type<ProfessionQueryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "profession_query"));

    public static final StreamCodec<FriendlyByteBuf, ProfessionQueryPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ProfessionQueryPayload decode(FriendlyByteBuf buf) {
                    return new ProfessionQueryPayload();
                }

                @Override
                public void encode(FriendlyByteBuf buf, ProfessionQueryPayload payload) {
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "profession_query");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "profession_query");
    *///?}

    //? if forge {
    /*public void write(FriendlyByteBuf buf) {
    }

    public static ProfessionQueryPayload read(FriendlyByteBuf buf) {
        return new ProfessionQueryPayload();
    }
    *///?}
}

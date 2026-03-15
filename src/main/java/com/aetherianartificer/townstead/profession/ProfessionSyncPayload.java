package com.aetherianartificer.townstead.profession;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client sync of available profession IDs in the player's village.
 */
//? if neoforge {
public record ProfessionSyncPayload(List<String> professionIds) implements CustomPacketPayload {
//?} else {
/*public record ProfessionSyncPayload(List<String> professionIds) {
*///?}

    //? if neoforge {
    public static final Type<ProfessionSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "profession_sync"));

    public static final StreamCodec<FriendlyByteBuf, ProfessionSyncPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ProfessionSyncPayload decode(FriendlyByteBuf buf) {
                    int size = buf.readVarInt();
                    List<String> ids = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) ids.add(buf.readUtf());
                    return new ProfessionSyncPayload(ids);
                }

                @Override
                public void encode(FriendlyByteBuf buf, ProfessionSyncPayload payload) {
                    buf.writeVarInt(payload.professionIds().size());
                    for (String id : payload.professionIds()) buf.writeUtf(id);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "profession_sync");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "profession_sync");
    *///?}

    //? if forge {
    /*public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(professionIds.size());
        for (String id : professionIds) buf.writeUtf(id);
    }

    public static ProfessionSyncPayload read(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<String> ids = new ArrayList<>(size);
        for (int i = 0; i < size; i++) ids.add(buf.readUtf());
        return new ProfessionSyncPayload(ids);
    }
    *///?}
}

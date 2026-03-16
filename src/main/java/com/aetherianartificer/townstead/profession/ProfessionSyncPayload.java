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
 * Server -> Client sync of available profession IDs with slot info.
 * usedSlots and maxSlots are parallel lists to professionIds.
 * maxSlots of -1 means unlimited (no slot cap).
 */
//? if neoforge {
public record ProfessionSyncPayload(List<String> professionIds, List<Integer> usedSlots, List<Integer> maxSlots) implements CustomPacketPayload {
//?} else {
/*public record ProfessionSyncPayload(List<String> professionIds, List<Integer> usedSlots, List<Integer> maxSlots) {
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
                    List<Integer> used = new ArrayList<>(size);
                    List<Integer> max = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        ids.add(buf.readUtf());
                        used.add(buf.readVarInt());
                        max.add(buf.readVarInt());
                    }
                    return new ProfessionSyncPayload(ids, used, max);
                }

                @Override
                public void encode(FriendlyByteBuf buf, ProfessionSyncPayload payload) {
                    buf.writeVarInt(payload.professionIds().size());
                    for (int i = 0; i < payload.professionIds().size(); i++) {
                        buf.writeUtf(payload.professionIds().get(i));
                        buf.writeVarInt(payload.usedSlots().get(i));
                        buf.writeVarInt(payload.maxSlots().get(i));
                    }
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
        for (int i = 0; i < professionIds.size(); i++) {
            buf.writeUtf(professionIds.get(i));
            buf.writeVarInt(usedSlots.get(i));
            buf.writeVarInt(maxSlots.get(i));
        }
    }

    public static ProfessionSyncPayload read(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<String> ids = new ArrayList<>(size);
        List<Integer> used = new ArrayList<>(size);
        List<Integer> max = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ids.add(buf.readUtf());
            used.add(buf.readVarInt());
            max.add(buf.readVarInt());
        }
        return new ProfessionSyncPayload(ids, used, max);
    }
    *///?}
}

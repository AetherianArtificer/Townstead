package com.aetherianartificer.townstead.village;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

//? if neoforge {
public record VillageResidentsSyncPayload(List<VillageResidentClientStore.Resident> residents) implements CustomPacketPayload {
//?} else {
/*public record VillageResidentsSyncPayload(List<VillageResidentClientStore.Resident> residents) {
*///?}

    //? if neoforge {
    public static final Type<VillageResidentsSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "village_residents_sync"));

    public static final StreamCodec<FriendlyByteBuf, VillageResidentsSyncPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public VillageResidentsSyncPayload decode(FriendlyByteBuf buf) {
                    int size = buf.readVarInt();
                    List<VillageResidentClientStore.Resident> residents = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        UUID villagerUuid = new UUID(buf.readLong(), buf.readLong());
                        String name = buf.readUtf();
                        String professionId = buf.readUtf();
                        int professionLevel = buf.readVarInt();
                        int shiftLength = buf.readVarInt();
                        int[] shifts = new int[shiftLength];
                        for (int shiftIndex = 0; shiftIndex < shiftLength; shiftIndex++) {
                            shifts[shiftIndex] = buf.readVarInt();
                        }
                        residents.add(new VillageResidentClientStore.Resident(
                                villagerUuid, name, professionId, professionLevel, shifts));
                    }
                    return new VillageResidentsSyncPayload(residents);
                }

                @Override
                public void encode(FriendlyByteBuf buf, VillageResidentsSyncPayload payload) {
                    buf.writeVarInt(payload.residents().size());
                    for (VillageResidentClientStore.Resident resident : payload.residents()) {
                        buf.writeLong(resident.villagerUuid().getMostSignificantBits());
                        buf.writeLong(resident.villagerUuid().getLeastSignificantBits());
                        buf.writeUtf(resident.name());
                        buf.writeUtf(resident.professionId());
                        buf.writeVarInt(resident.professionLevel());
                        buf.writeVarInt(resident.shifts().length);
                        for (int shift : resident.shifts()) {
                            buf.writeVarInt(shift);
                        }
                    }
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "village_residents_sync");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "village_residents_sync");
    *///?}

    //? if forge {
    /*public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(residents.size());
        for (VillageResidentClientStore.Resident resident : residents) {
            buf.writeLong(resident.villagerUuid().getMostSignificantBits());
            buf.writeLong(resident.villagerUuid().getLeastSignificantBits());
            buf.writeUtf(resident.name());
            buf.writeUtf(resident.professionId());
            buf.writeVarInt(resident.professionLevel());
            buf.writeVarInt(resident.shifts().length);
            for (int shift : resident.shifts()) {
                buf.writeVarInt(shift);
            }
        }
    }

    public static VillageResidentsSyncPayload read(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<VillageResidentClientStore.Resident> residents = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            UUID villagerUuid = new UUID(buf.readLong(), buf.readLong());
            String name = buf.readUtf();
            String professionId = buf.readUtf();
            int professionLevel = buf.readVarInt();
            int shiftLength = buf.readVarInt();
            int[] shifts = new int[shiftLength];
            for (int shiftIndex = 0; shiftIndex < shiftLength; shiftIndex++) {
                shifts[shiftIndex] = buf.readVarInt();
            }
            residents.add(new VillageResidentClientStore.Resident(
                    villagerUuid, name, professionId, professionLevel, shifts));
        }
        return new VillageResidentsSyncPayload(residents);
    }
    *///?}
}

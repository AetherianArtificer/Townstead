package com.aetherianartificer.townstead.profession;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Client -> Server request to change a villager's profession.
 */
//? if neoforge {
public record ProfessionSetPayload(UUID villagerUuid, String professionId) implements CustomPacketPayload {
//?} else {
/*public record ProfessionSetPayload(UUID villagerUuid, String professionId) {
*///?}

    //? if neoforge {
    public static final Type<ProfessionSetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "profession_set"));

    public static final StreamCodec<FriendlyByteBuf, ProfessionSetPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ProfessionSetPayload decode(FriendlyByteBuf buf) {
                    UUID uuid = new UUID(buf.readLong(), buf.readLong());
                    String profId = buf.readUtf();
                    return new ProfessionSetPayload(uuid, profId);
                }

                @Override
                public void encode(FriendlyByteBuf buf, ProfessionSetPayload payload) {
                    buf.writeLong(payload.villagerUuid().getMostSignificantBits());
                    buf.writeLong(payload.villagerUuid().getLeastSignificantBits());
                    buf.writeUtf(payload.professionId());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "profession_set");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "profession_set");
    *///?}

    //? if forge {
    /*public void write(FriendlyByteBuf buf) {
        buf.writeLong(villagerUuid.getMostSignificantBits());
        buf.writeLong(villagerUuid.getLeastSignificantBits());
        buf.writeUtf(professionId);
    }

    public static ProfessionSetPayload read(FriendlyByteBuf buf) {
        UUID uuid = new UUID(buf.readLong(), buf.readLong());
        String profId = buf.readUtf();
        return new ProfessionSetPayload(uuid, profId);
    }
    *///?}
}

package com.aetherianartificer.townstead.compat.travelerstitles;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

//? if neoforge {
public record VillageEnterTitlePayload(String title, int population, String subtitleKey) implements CustomPacketPayload {
//?} else {
/*public record VillageEnterTitlePayload(String title, int population, String subtitleKey) {
*///?}

    //? if neoforge {
    public static final Type<VillageEnterTitlePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "village_enter_title"));

    public static final StreamCodec<FriendlyByteBuf, VillageEnterTitlePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, VillageEnterTitlePayload::title,
                    ByteBufCodecs.VAR_INT, VillageEnterTitlePayload::population,
                    ByteBufCodecs.STRING_UTF8, VillageEnterTitlePayload::subtitleKey,
                    VillageEnterTitlePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "village_enter_title");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "village_enter_title");
    *///?}

    //? if forge {
    /*public void write(FriendlyByteBuf buf) {
        buf.writeUtf(title);
        buf.writeVarInt(population);
        buf.writeUtf(subtitleKey == null ? "" : subtitleKey);
    }

    public static VillageEnterTitlePayload read(FriendlyByteBuf buf) {
        String title = buf.readUtf();
        int population = buf.readVarInt();
        String subtitleKey = buf.readUtf();
        return new VillageEnterTitlePayload(title, population, subtitleKey);
    }
    *///?}
}

package com.aetherianartificer.townstead.pheno.particle;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

import java.util.LinkedHashMap;
import java.util.Map;

/** Every data-pack particle definition, sent on join and after each data reload. */
//? if neoforge {
public record DataParticlesS2CPayload(Map<String, String> definitions) implements CustomPacketPayload {
//?} else {
/*public record DataParticlesS2CPayload(Map<String, String> definitions) {
*///?}

    //? if neoforge {
    public static final Type<DataParticlesS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "data_particles"));

    public static final StreamCodec<FriendlyByteBuf, DataParticlesS2CPayload> STREAM_CODEC =
            StreamCodec.of(DataParticlesS2CPayload::write, DataParticlesS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    public static DataParticlesS2CPayload snapshot() {
        return new DataParticlesS2CPayload(DataParticles.sources());
    }

    public static void write(FriendlyByteBuf buf, DataParticlesS2CPayload payload) {
        buf.writeVarInt(payload.definitions.size());
        for (Map.Entry<String, String> entry : payload.definitions.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeUtf(entry.getValue(), 32767);
        }
    }

    public static DataParticlesS2CPayload read(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<String, String> definitions = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) definitions.put(buf.readUtf(), buf.readUtf(32767));
        return new DataParticlesS2CPayload(definitions);
    }

    public static void accept(DataParticlesS2CPayload payload) {
        DataParticles.accept(payload.definitions);
    }
}

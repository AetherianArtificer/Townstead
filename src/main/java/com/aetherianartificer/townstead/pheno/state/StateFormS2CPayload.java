package com.aetherianartificer.townstead.pheno.state;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/** Server to client: the form an entity's states give it (see {@link StateForms}); an empty rig is none. */
//? if neoforge {
public record StateFormS2CPayload(int entityId, String rig, boolean talk, java.util.Map<String, Integer> variants) implements CustomPacketPayload {
//?} else {
/*public record StateFormS2CPayload(int entityId, String rig, boolean talk, java.util.Map<String, Integer> variants) {
*///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUtf(rig, 256);
        buf.writeBoolean(talk);
        buf.writeVarInt(variants.size());
        variants.forEach((name, value) -> {
            buf.writeUtf(name, 64);
            buf.writeVarInt(value);
        });
    }

    public static StateFormS2CPayload read(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        String rig = buf.readUtf(256);
        boolean talk = buf.readBoolean();
        int count = Math.min(buf.readVarInt(), 16);
        java.util.Map<String, Integer> variants = new java.util.LinkedHashMap<>();
        for (int i = 0; i < count; i++) variants.put(buf.readUtf(64), buf.readVarInt());
        return new StateFormS2CPayload(id, rig, talk, java.util.Map.copyOf(variants));
    }

    //? if neoforge {
    public static final Type<StateFormS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "state_form"));
    public static final StreamCodec<FriendlyByteBuf, StateFormS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), StateFormS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}
}

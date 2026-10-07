package com.aetherianartificer.townstead.persona;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/** Server to client: whether {@code entityId} is a Persona, for their interaction screen. */
//? if neoforge {
public record PersonaMenuS2CPayload(int entityId, boolean persona) implements CustomPacketPayload {
//?} else {
/*public record PersonaMenuS2CPayload(int entityId, boolean persona) {
*///?}

    //? if neoforge {
    public static final Type<PersonaMenuS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "persona_menu_s2c"));
    public static final StreamCodec<FriendlyByteBuf, PersonaMenuS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), PersonaMenuS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeBoolean(persona);
    }

    public static PersonaMenuS2CPayload read(FriendlyByteBuf buf) {
        return new PersonaMenuS2CPayload(buf.readInt(), buf.readBoolean());
    }
}

package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server: set a villager's personality from the editor's dynamic picker. {@code ref} is a
 * personality reference from the villager's origin pool (a custom {@code PersonalityDef} id, or a
 * bare base-enum name). The server stores it on the Life and sets the MCA brain personality to the
 * base enum it maps to, then re-syncs.
 */
//? if neoforge {
public record SetPersonalityC2SPayload(int entityId, String ref) implements CustomPacketPayload {
//?} else {
/*public record SetPersonalityC2SPayload(int entityId, String ref) {
*///?}

    //? if neoforge {
    public static final Type<SetPersonalityC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "set_personality_c2s"));

    public static final StreamCodec<FriendlyByteBuf, SetPersonalityC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), SetPersonalityC2SPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "set_personality_c2s");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "set_personality_c2s");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeUtf(ref);
    }

    public static SetPersonalityC2SPayload read(FriendlyByteBuf buf) {
        return new SetPersonalityC2SPayload(buf.readInt(), buf.readUtf());
    }
}

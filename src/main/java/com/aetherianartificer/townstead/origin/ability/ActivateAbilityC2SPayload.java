package com.aetherianartificer.townstead.origin.ability;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server: the player pressed the "Origin Ability {@code slot}" key. The
 * server resolves the player's active ability bound to that slot and fires it
 * (cooldown and condition checked server-side; the client never decides).
 */
//? if neoforge {
public record ActivateAbilityC2SPayload(int slot) implements CustomPacketPayload {
//?} else {
/*public record ActivateAbilityC2SPayload(int slot) {
*///?}

    //? if neoforge {
    public static final Type<ActivateAbilityC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "activate_ability_c2s"));

    public static final StreamCodec<FriendlyByteBuf, ActivateAbilityC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), ActivateAbilityC2SPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "activate_ability_c2s");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "activate_ability_c2s");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(slot);
    }

    public static ActivateAbilityC2SPayload read(FriendlyByteBuf buf) {
        return new ActivateAbilityC2SPayload(buf.readVarInt());
    }
}

package com.aetherianartificer.townstead.persona;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server, from a villager's interaction screen: with {@code leave} false, asks whether
 * {@code entityId} is a Persona (answered with a {@link PersonaMenuS2CPayload}); with {@code leave}
 * true, the player has confirmed asking that Persona to leave town.
 */
//? if neoforge {
public record PersonaMenuC2SPayload(int entityId, boolean leave) implements CustomPacketPayload {
//?} else {
/*public record PersonaMenuC2SPayload(int entityId, boolean leave) {
*///?}

    //? if neoforge {
    public static final Type<PersonaMenuC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "persona_menu_c2s"));
    public static final StreamCodec<FriendlyByteBuf, PersonaMenuC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), PersonaMenuC2SPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeBoolean(leave);
    }

    public static PersonaMenuC2SPayload read(FriendlyByteBuf buf) {
        return new PersonaMenuC2SPayload(buf.readInt(), buf.readBoolean());
    }

    /** Answers on the server thread. */
    public static void handle(net.minecraft.server.level.ServerPlayer sp, PersonaMenuC2SPayload payload) {
        if (payload.leave()) {
            PersonaLeave.ask(sp, payload.entityId());
            return;
        }
        PersonaMenuS2CPayload reply = new PersonaMenuS2CPayload(payload.entityId(), PersonaLeave.isPersona(sp, payload.entityId()));
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, reply);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(sp, reply);
        *///?}
    }
}

package com.aetherianartificer.townstead.replace;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether a villager is still acting as the mob it replaced. Its name is hidden while it is, so it
 * reads as a creature rather than a neighbor. The name shows once it is cured or settles.
 */
//? if neoforge {
public record WildNameS2CPayload(int entityId, boolean wild) implements CustomPacketPayload {
//?} else {
/*public record WildNameS2CPayload(int entityId, boolean wild) {
*///?}
    private static final Set<Integer> HIDDEN = ConcurrentHashMap.newKeySet();

    //? if neoforge {
    public static final Type<WildNameS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "wild_name"));

    public static final StreamCodec<FriendlyByteBuf, WildNameS2CPayload> STREAM_CODEC =
            StreamCodec.of(WildNameS2CPayload::write, WildNameS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    public static void write(FriendlyByteBuf buf, WildNameS2CPayload payload) {
        buf.writeVarInt(payload.entityId);
        buf.writeBoolean(payload.wild);
    }

    public static WildNameS2CPayload read(FriendlyByteBuf buf) {
        return new WildNameS2CPayload(buf.readVarInt(), buf.readBoolean());
    }

    /** Client side: records the flag. */
    public static void accept(WildNameS2CPayload payload) {
        if (payload.wild) HIDDEN.add(payload.entityId);
        else HIDDEN.remove(payload.entityId);
    }

    /** Client side: whether this entity's name stays hidden. */
    public static boolean hidden(int entityId) {
        return HIDDEN.contains(entityId);
    }

    public static void clear() {
        HIDDEN.clear();
    }
}

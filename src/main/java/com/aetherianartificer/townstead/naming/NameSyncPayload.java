package com.aetherianartificer.townstead.naming;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * A villager's family name and culture, for the screens that draw them.
 *
 * <p>Both live in server-side villager state, and every surface that shows a name is on the client.
 * Only the parts the client cannot work out for itself travel: the given name is already the
 * entity's own name and syncs with it, so this carries the family name, culture id, and resolved name order. Sent when a player starts tracking a villager and again whenever the name settles.</p>
 */
//? if neoforge {
public record NameSyncPayload(int entityId, String familyName, String culture, NamingTradition.Order order) implements CustomPacketPayload {
//?} else {
/*public record NameSyncPayload(int entityId, String familyName, String culture, NamingTradition.Order order) {
*///?}

    /** Generous, but bounded: a name arriving from a datapack should never be able to bloat a packet. */
    public static final int MAX_LENGTH = 256;

    public NameSyncPayload {
        familyName = familyName == null ? "" : familyName;
        culture = culture == null ? "" : culture;
        order = order == null ? NamingTradition.Order.GIVEN_FIRST : order;
    }

    //? if neoforge {
    public static final Type<NameSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "name_sync"));

    public static final StreamCodec<FriendlyByteBuf, NameSyncPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> encode(payload, buf), NameSyncPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    public static void encode(NameSyncPayload payload, FriendlyByteBuf buf) {
        buf.writeVarInt(payload.entityId());
        buf.writeUtf(payload.familyName(), MAX_LENGTH);
        buf.writeUtf(payload.culture(), MAX_LENGTH);
        buf.writeEnum(payload.order());
    }

    public static NameSyncPayload decode(FriendlyByteBuf buf) {
        return new NameSyncPayload(buf.readVarInt(), buf.readUtf(MAX_LENGTH), buf.readUtf(MAX_LENGTH), buf.readEnum(NamingTradition.Order.class));
    }
}

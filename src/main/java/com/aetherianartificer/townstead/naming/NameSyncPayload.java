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
 * entity's own name and syncs with it, so this carries the family name, culture id, resolved name
 * order, and the family-name rule the villager's tradition uses. Sent when a player starts tracking
 * a villager and again whenever the name settles.</p>
 *
 * <p>The rule travels because a screen offering to edit a surname has to say what editing it will
 * do. Under an inherited rule a child born later takes the new name; under a patronymic one the
 * child's name is built from the parent's given name, so the edit reaches nobody else.</p>
 */
//? if neoforge {
public record NameSyncPayload(int entityId, String familyName, String culture, NamingTradition.Order order, NamingTradition.FamilyType familyType, String tradition) implements CustomPacketPayload {
//?} else {
/*public record NameSyncPayload(int entityId, String familyName, String culture, NamingTradition.Order order, NamingTradition.FamilyType familyType, String tradition) {
*///?}

    /** Generous, but bounded: a name arriving from a datapack should never be able to bloat a packet. */
    public static final int MAX_LENGTH = 256;

    public NameSyncPayload {
        familyName = familyName == null ? "" : familyName;
        culture = culture == null ? "" : culture;
        order = order == null ? NamingTradition.Order.GIVEN_FIRST : order;
        familyType = familyType == null ? NamingTradition.FamilyType.NONE : familyType;
        tradition = tradition == null ? "" : tradition;
    }

    /** The same payload aimed at another entity id, for the editor's throwaway preview villager. */
    public NameSyncPayload withEntityId(int id) {
        return new NameSyncPayload(id, familyName, culture, order, familyType, tradition);
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
        buf.writeEnum(payload.familyType());
        buf.writeUtf(payload.tradition(), MAX_LENGTH);
    }

    public static NameSyncPayload decode(FriendlyByteBuf buf) {
        return new NameSyncPayload(buf.readVarInt(), buf.readUtf(MAX_LENGTH), buf.readUtf(MAX_LENGTH),
                buf.readEnum(NamingTradition.Order.class), buf.readEnum(NamingTradition.FamilyType.class),
                buf.readUtf(MAX_LENGTH));
    }
}

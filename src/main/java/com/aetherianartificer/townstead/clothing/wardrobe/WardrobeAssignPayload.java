package com.aetherianartificer.townstead.clothing.wardrobe;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Client -> Server: set one cell of the wardrobe grid, or ask for the grid.
 *
 * @param villager the row, or null for the Village row
 * @param day      the weekday, {@link #ALL_DAYS} for the whole row, or {@link #QUERY} to only
 *                 ask for a sync
 * @param policy   the template id, or empty to clear the cell
 */
//? if neoforge {
public record WardrobeAssignPayload(@Nullable UUID villager, int day, String policy) implements CustomPacketPayload {
//?} else {
/*public record WardrobeAssignPayload(@Nullable UUID villager, int day, String policy) {
*///?}

    public static final int ALL_DAYS = -1;
    public static final int QUERY = -2;

    public static WardrobeAssignPayload query() {
        return new WardrobeAssignPayload(null, QUERY, "");
    }

    //? if neoforge {
    public static final Type<WardrobeAssignPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "wardrobe_assign"));

    public static final StreamCodec<FriendlyByteBuf, WardrobeAssignPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public WardrobeAssignPayload decode(FriendlyByteBuf buf) { return read(buf); }

                @Override
                public void encode(FriendlyByteBuf buf, WardrobeAssignPayload payload) { payload.write(buf); }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "wardrobe_assign");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "wardrobe_assign");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(villager != null);
        if (villager != null) buf.writeUUID(villager);
        buf.writeVarInt(day);
        buf.writeUtf(policy == null ? "" : policy);
    }

    public static WardrobeAssignPayload read(FriendlyByteBuf buf) {
        UUID villager = buf.readBoolean() ? buf.readUUID() : null;
        int day = buf.readVarInt();
        String policy = buf.readUtf();
        return new WardrobeAssignPayload(villager, day, policy);
    }
}

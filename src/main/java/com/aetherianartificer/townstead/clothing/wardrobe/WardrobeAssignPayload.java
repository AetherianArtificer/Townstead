package com.aetherianartificer.townstead.clothing.wardrobe;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client -> Server: one Wardrobe edit, or a request for the grid.
 *
 * @param op       what to do
 * @param villager the row, or null for the Village row
 * @param day      the weekday, {@link #ALL_DAYS} for every day; for {@link Op#WEATHER} 0 is the
 *                 warm layer and 1 the light layer; for {@link Op#STAR} 1 stars and 0 unstars
 * @param value    a skin id, empty to clear; for {@link Op#WEATHER} the override state
 * @param targets  the rows a paste or clear applies to, or the residents a query asks about
 * @param row      the day cells a paste writes
 */
//? if neoforge {
public record WardrobeAssignPayload(Op op, @Nullable UUID villager, int day, String value, List<UUID> targets,
                                   List<String> row) implements CustomPacketPayload {
//?} else {
/*public record WardrobeAssignPayload(Op op, @Nullable UUID villager, int day, String value, List<UUID> targets,
                                   List<String> row) {
*///?}

    public static final int ALL_DAYS = -1;

    public enum Op { QUERY, CELL, WORK, WEATHER, STAR, PASTE }

    public WardrobeAssignPayload {
        value = value == null ? "" : value;
        targets = targets == null ? List.of() : List.copyOf(targets);
        row = row == null ? List.of() : List.copyOf(row);
    }

    public static WardrobeAssignPayload query(List<UUID> residents) {
        return new WardrobeAssignPayload(Op.QUERY, null, 0, "", residents, List.of());
    }

    public static WardrobeAssignPayload cell(UUID villager, int day, String skin) {
        return new WardrobeAssignPayload(Op.CELL, villager, day, skin, List.of(), List.of());
    }

    public static WardrobeAssignPayload work(UUID villager, String skin) {
        return new WardrobeAssignPayload(Op.WORK, villager, 0, skin, List.of(), List.of());
    }

    public static WardrobeAssignPayload weather(@Nullable UUID villager, boolean warmLayer, byte state) {
        return new WardrobeAssignPayload(Op.WEATHER, villager, warmLayer ? 0 : 1, Byte.toString(state), List.of(), List.of());
    }

    public static WardrobeAssignPayload star(UUID villager, String skin, boolean starred) {
        return new WardrobeAssignPayload(Op.STAR, villager, starred ? 1 : 0, skin, List.of(), List.of());
    }

    /** Writes the row onto every target; an empty row clears them. */
    public static WardrobeAssignPayload paste(List<UUID> targets, List<String> row) {
        return new WardrobeAssignPayload(Op.PASTE, null, 0, "", targets, row);
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
        buf.writeEnum(op);
        buf.writeBoolean(villager != null);
        if (villager != null) buf.writeUUID(villager);
        buf.writeVarInt(day);
        buf.writeUtf(value);
        buf.writeVarInt(targets.size());
        for (UUID target : targets) buf.writeUUID(target);
        buf.writeVarInt(row.size());
        for (String cell : row) buf.writeUtf(cell == null ? "" : cell);
    }

    public static WardrobeAssignPayload read(FriendlyByteBuf buf) {
        Op op = buf.readEnum(Op.class);
        UUID villager = buf.readBoolean() ? buf.readUUID() : null;
        int day = buf.readVarInt();
        String value = buf.readUtf();
        int n = Math.min(buf.readVarInt(), 4096);
        List<UUID> targets = new ArrayList<>(n);
        for (int i = 0; i < n; i++) targets.add(buf.readUUID());
        int m = Math.min(buf.readVarInt(), 64);
        List<String> row = new ArrayList<>(m);
        for (int i = 0; i < m; i++) row.add(buf.readUtf());
        return new WardrobeAssignPayload(op, villager, day, value, targets, row);
    }
}

package com.aetherianartificer.townstead.root.ability;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client → server: the player's prepared abilities, keyed by the slot they chose.
 *
 * <p>The whole arrangement travels rather than a "put X in slot 3" delta, because reordering is the
 * common edit and a sequence of deltas can leave the server holding an arrangement that never
 * existed on anybody's screen. The server re-validates every id against what the player actually
 * owns and clamps the slots itself; nothing here is trusted.</p>
 *
 * <p>Keyed by slot rather than sent as a list so an EMPTY slot is expressible. A list would compact,
 * and clearing one ability would slide every ability below it onto a different key.</p>
 */
//? if neoforge {
public record AbilityLoadoutC2SPayload(Map<Integer, ResourceLocation> bySlot)
        implements CustomPacketPayload {
//?} else {
/*public record AbilityLoadoutC2SPayload(Map<Integer, ResourceLocation> bySlot) {
*///?}

    /** Guards a hostile client; the server clamps to the ability pool anyway. */
    private static final int MAX_ENTRIES = 64;

    public void write(FriendlyByteBuf buf) {
        int size = Math.min(bySlot.size(), MAX_ENTRIES);
        buf.writeVarInt(size);
        int written = 0;
        for (Map.Entry<Integer, ResourceLocation> entry : bySlot.entrySet()) {
            if (written++ >= size) break;
            buf.writeVarInt(Math.max(0, entry.getKey()));
            buf.writeUtf(entry.getValue().toString());
        }
    }

    public static AbilityLoadoutC2SPayload read(FriendlyByteBuf buf) {
        int size = Math.min(buf.readVarInt(), MAX_ENTRIES);
        Map<Integer, ResourceLocation> bySlot = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            int slot = buf.readVarInt();
            ResourceLocation id = ResourceLocation.tryParse(buf.readUtf());
            if (id != null) bySlot.put(slot, id);
        }
        return new AbilityLoadoutC2SPayload(Map.copyOf(bySlot));
    }

    //? if neoforge {
    public static final Type<AbilityLoadoutC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "ability_loadout_c2s"));

    public static final StreamCodec<FriendlyByteBuf, AbilityLoadoutC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), AbilityLoadoutC2SPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}
}

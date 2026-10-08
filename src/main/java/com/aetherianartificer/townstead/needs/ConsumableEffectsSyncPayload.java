package com.aetherianartificer.townstead.needs;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Server-authoritative, item-expanded consumable effects for client tooltips and compat proxies. */
//? if neoforge {
public record ConsumableEffectsSyncPayload(List<Row> rows) implements CustomPacketPayload {
//?} else {
/*public record ConsumableEffectsSyncPayload(List<Row> rows) {
*///?}
    private static final int MAX_ROWS = 16_384;

    public record Row(ResourceLocation item, int immediateHydration, int lastingHydration, int energy,
                      boolean fallback) {
        public NeedEffectProjection projection() {
            return new NeedEffectProjection(immediateHydration, lastingHydration, energy);
        }

        public Consumables.ResolvedEffect resolvedEffect() {
            return new Consumables.ResolvedEffect(projection(), fallback);
        }
    }

    public ConsumableEffectsSyncPayload {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }

    public static ConsumableEffectsSyncPayload snapshot() {
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Consumables.ResolvedEffect> entry : Consumables.resolvedEffects().entrySet()) {
            Consumables.ResolvedEffect resolved = entry.getValue();
            NeedEffectProjection effect = resolved.projection();
            rows.add(new Row(entry.getKey(), effect.immediateHydration(), effect.lastingHydration(),
                    effect.energy(), resolved.fallback()));
        }
        return new ConsumableEffectsSyncPayload(rows);
    }

    public void write(FriendlyByteBuf buf) {
        int size = Math.min(MAX_ROWS, rows.size());
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            Row row = rows.get(i);
            buf.writeResourceLocation(row.item());
            buf.writeVarInt(row.immediateHydration());
            buf.writeVarInt(row.lastingHydration());
            buf.writeVarInt(row.energy());
            buf.writeBoolean(row.fallback());
        }
    }

    public static ConsumableEffectsSyncPayload read(FriendlyByteBuf buf) {
        int encodedSize = buf.readVarInt();
        int size = Math.min(MAX_ROWS, encodedSize);
        List<Row> rows = new ArrayList<>(size);
        for (int i = 0; i < encodedSize; i++) {
            Row row = new Row(buf.readResourceLocation(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readBoolean());
            if (i < size) rows.add(row);
        }
        return new ConsumableEffectsSyncPayload(rows);
    }

    //? if neoforge {
    public static final Type<ConsumableEffectsSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "consumable_effects_sync"));
    public static final StreamCodec<FriendlyByteBuf, ConsumableEffectsSyncPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), ConsumableEffectsSyncPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}
}

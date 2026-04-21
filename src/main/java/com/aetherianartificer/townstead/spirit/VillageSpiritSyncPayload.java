package com.aetherianartificer.townstead.spirit;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Server → client snapshot of a village's spirit state. Carries everything
 * the client needs to render the blueprint Spirit page without recomputing:
 * raw per-spirit point totals, the total, the contributing-building count,
 * and the structural readout (classification + tier + up to two involved
 * spirit ids). Client rebuilds a {@link SpiritReadout} from those fields.
 *
 * Sent from the {@code GetVillageRequest} server handler so the payload
 * arrives alongside MCA's own {@code GetVillageResponse}; the Spirit page
 * reads from {@code ClientVillageSpiritStore} which this payload populates.
 */
//? if neoforge {
public record VillageSpiritSyncPayload(
        int villageId,
        Map<String, Integer> perSpirit,
        int total,
        int contributingBuildings,
        String classificationName,
        int tierIndex,
        Optional<String> primarySpiritId,
        Optional<String> secondarySpiritId) implements CustomPacketPayload {
//?} else {
/*public record VillageSpiritSyncPayload(
        int villageId,
        Map<String, Integer> perSpirit,
        int total,
        int contributingBuildings,
        String classificationName,
        int tierIndex,
        Optional<String> primarySpiritId,
        Optional<String> secondarySpiritId) {
*///?}

    //? if neoforge {
    public static final Type<VillageSpiritSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "village_spirit_sync"));

    public static final StreamCodec<FriendlyByteBuf, VillageSpiritSyncPayload> STREAM_CODEC =
            StreamCodec.of(VillageSpiritSyncPayload::write, VillageSpiritSyncPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "village_spirit_sync");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "village_spirit_sync");
    *///?}

    public static void write(FriendlyByteBuf buf, VillageSpiritSyncPayload p) {
        buf.writeVarInt(p.villageId);
        buf.writeVarInt(p.total);
        buf.writeVarInt(p.contributingBuildings);
        buf.writeVarInt(p.perSpirit.size());
        // LinkedHashMap on the server side preserves SpiritRegistry order; iteration
        // over a HashMap on the client loses it but the UI re-orders by registry anyway.
        for (Map.Entry<String, Integer> e : p.perSpirit.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeVarInt(e.getValue());
        }
        buf.writeUtf(p.classificationName);
        buf.writeVarInt(p.tierIndex);
        buf.writeBoolean(p.primarySpiritId.isPresent());
        p.primarySpiritId.ifPresent(buf::writeUtf);
        buf.writeBoolean(p.secondarySpiritId.isPresent());
        p.secondarySpiritId.ifPresent(buf::writeUtf);
    }

    public static VillageSpiritSyncPayload read(FriendlyByteBuf buf) {
        int villageId = buf.readVarInt();
        int total = buf.readVarInt();
        int contrib = buf.readVarInt();
        int n = buf.readVarInt();
        Map<String, Integer> perSpirit = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            String id = buf.readUtf();
            int pts = buf.readVarInt();
            perSpirit.put(id, pts);
        }
        String clsName = buf.readUtf();
        int tier = buf.readVarInt();
        Optional<String> p1 = buf.readBoolean() ? Optional.of(buf.readUtf()) : Optional.empty();
        Optional<String> p2 = buf.readBoolean() ? Optional.of(buf.readUtf()) : Optional.empty();
        return new VillageSpiritSyncPayload(villageId, Map.copyOf(perSpirit), total, contrib, clsName, tier, p1, p2);
    }

    /** Build a client-side payload from a server cache entry. */
    public static VillageSpiritSyncPayload fromCache(int villageId, VillageSpiritCache.Entry entry) {
        SpiritTotals totals = entry.totals();
        SpiritReadout readout = entry.readout();
        Map<String, Integer> ordered = new LinkedHashMap<>();
        // Emit in registry order so the wire format is stable across JVMs.
        for (SpiritRegistry.Spirit s : SpiritRegistry.ordered()) {
            int pts = totals.pointsFor(s.id());
            if (pts > 0) ordered.put(s.id(), pts);
        }
        return new VillageSpiritSyncPayload(
                villageId,
                Map.copyOf(ordered),
                totals.total(),
                totals.contributingBuildings(),
                readout.classification().name(),
                readout.tierIndex(),
                Optional.ofNullable(readout.primarySpiritId()),
                Optional.ofNullable(readout.secondarySpiritId()));
    }

    /** Reconstruct a SpiritReadout from the wire fields (client side). */
    public SpiritReadout toReadout() {
        SpiritReadout.Classification cls;
        try {
            cls = SpiritReadout.Classification.valueOf(classificationName);
        } catch (IllegalArgumentException ex) {
            cls = SpiritReadout.Classification.SETTLEMENT;
        }
        return new SpiritReadout(cls, tierIndex,
                primarySpiritId.orElse(null),
                secondarySpiritId.orElse(null));
    }

    public SpiritTotals toTotals() {
        return new SpiritTotals(perSpirit, total, contributingBuildings);
    }
}

package com.aetherianartificer.townstead.politics.state;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * The one building a faction governs from. MCA owns the building's geometry; the record
 * holds only its id and the Charter lectern that designated it. A damaged Seat ({@code damage}
 * names why, empty when intact) keeps its place and is repaired by rebuilding what broke.
 */
public record SeatInstance(ResourceLocation faction,
                           SettlementRef settlement,
                           BlockPos lectern,
                           int buildingId,
                           long designatedAt,
                           String damage,
                           long damagedAt) {
    public SeatInstance {
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(settlement, "settlement");
        Objects.requireNonNull(lectern, "lectern");
        damage = damage == null ? "" : damage;
    }

    public SeatInstance(ResourceLocation faction, SettlementRef settlement, BlockPos lectern,
                        int buildingId, long designatedAt) {
        this(faction, settlement, lectern, buildingId, designatedAt, "", 0L);
    }

    public boolean damaged() {
        return !damage.isEmpty();
    }

    public SeatInstance withDamage(String reason, long now) {
        return new SeatInstance(faction, settlement, lectern, buildingId, designatedAt, reason, now);
    }

    public SeatInstance repaired() {
        return new SeatInstance(faction, settlement, lectern, buildingId, designatedAt, "", 0L);
    }

    public SeatInstance withBuilding(int id) {
        return new SeatInstance(faction, settlement, lectern, id, designatedAt, damage, damagedAt);
    }

    /** A Seat's place is its lectern; MCA can re-recognize the same room under a new building id. */
    public boolean sameHost(SeatInstance other) {
        return other != null && settlement.equals(other.settlement()) && lectern.equals(other.lectern());
    }
}

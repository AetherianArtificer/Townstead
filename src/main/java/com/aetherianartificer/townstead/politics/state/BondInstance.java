package com.aetherianartificer.townstead.politics.state;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * A live or ended bond between parties. An ended bond is kept, because "former councilor" and
 * "never a councilor" are different histories.
 */
public record BondInstance(ResourceLocation id,
                           ResourceLocation kind,
                           List<Side> sides,
                           long startedAt,
                           long endedAt,
                           ResourceLocation provenance,
                           String endedBy) {
    public static final long ONGOING = Long.MIN_VALUE;

    public record Side(String role, Party party) {
        public Side {
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(party, "party");
        }
    }

    public BondInstance {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(provenance, "provenance");
        sides = List.copyOf(sides);
        if (sides.size() != 2) throw new IllegalArgumentException("A bond joins exactly two parties");
        if (sides.get(0).party().equals(sides.get(1).party())) throw new IllegalArgumentException("A bond joins two different parties");
        endedBy = endedBy == null ? "" : endedBy;
    }

    public boolean active() {
        return endedAt == ONGOING;
    }

    public boolean involves(Party party) {
        return sides.get(0).party().equals(party) || sides.get(1).party().equals(party);
    }

    /** The party in this role; for a symmetric bond, the first one. */
    public @Nullable Party party(String role) {
        for (Side side : sides) if (side.role().equals(role)) return side.party();
        return null;
    }

    public @Nullable String roleOf(Party party) {
        for (Side side : sides) if (side.party().equals(party)) return side.role();
        return null;
    }

    public @Nullable Party other(Party party) {
        if (sides.get(0).party().equals(party)) return sides.get(1).party();
        if (sides.get(1).party().equals(party)) return sides.get(0).party();
        return null;
    }

    public BondInstance ended(long now, String reason) {
        return new BondInstance(id, kind, sides, startedAt, now, provenance, reason);
    }
}

package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.OptionalLong;

/**
 * A bond between two parties, such as citizenship or an office. {@code kind} is the bond kind;
 * each side names its role in that kind. An ended bond is kept with its {@code endedAt} and
 * {@code endedBy}, such as {@code left}, {@code resigned} or {@code dissolved}.
 */
public record BondSnapshot(ResourceLocation id,
                           ResourceLocation kind,
                           List<Side> sides,
                           long startedAt,
                           OptionalLong endedAt,
                           String endedBy,
                           ResourceLocation provenance) {
    public BondSnapshot {
        sides = List.copyOf(sides);
    }

    public boolean active() {
        return endedAt.isEmpty();
    }

    public record Side(String role, PartyRef party) {}
}

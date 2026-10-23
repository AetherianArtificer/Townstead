package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** One side of a bond: a person, by UUID, or a faction, by id. {@code kind} is {@code person} or {@code faction}. */
public record PartyRef(String kind, String id) {
    public static final String PERSON = "person";
    public static final String FACTION = "faction";

    public PartyRef {
        kind = Objects.requireNonNull(kind, "kind").toLowerCase(Locale.ROOT);
        Objects.requireNonNull(id, "id");
    }

    public static PartyRef person(UUID person) {
        return new PartyRef(PERSON, person.toString());
    }

    public static PartyRef faction(ResourceLocation faction) {
        return new PartyRef(FACTION, faction.toString());
    }

    public Optional<UUID> asPerson() {
        if (!PERSON.equals(kind)) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException error) {
            return Optional.empty();
        }
    }

    public Optional<ResourceLocation> asFaction() {
        return FACTION.equals(kind) ? Optional.ofNullable(ResourceLocation.tryParse(id)) : Optional.empty();
    }
}

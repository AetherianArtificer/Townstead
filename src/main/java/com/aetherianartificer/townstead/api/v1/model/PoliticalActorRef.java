package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Objects;

/** A stable reference to one world organization or polity. */
public record PoliticalActorRef(String kind, ResourceLocation id) {
    public static final String ORGANIZATION = "organization";
    public static final String POLITY = "polity";

    public PoliticalActorRef {
        kind = Objects.requireNonNull(kind, "kind").toLowerCase(Locale.ROOT);
        Objects.requireNonNull(id, "id");
    }

    public static PoliticalActorRef organization(ResourceLocation id) {
        return new PoliticalActorRef(ORGANIZATION, id);
    }

    public static PoliticalActorRef polity(ResourceLocation id) {
        return new PoliticalActorRef(POLITY, id);
    }
}

package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.resources.ResourceLocation;

import java.util.OptionalLong;
import java.util.UUID;

/** One durable, historically retained relationship between a person and a political actor. */
public record AffiliationSnapshot(ResourceLocation id,
                                  UUID person,
                                  PoliticalActorRef actor,
                                  ResourceLocation kind,
                                  String status,
                                  long startedAt,
                                  OptionalLong endedAt,
                                  ResourceLocation provenance,
                                  String visibility,
                                  boolean membership) {
}

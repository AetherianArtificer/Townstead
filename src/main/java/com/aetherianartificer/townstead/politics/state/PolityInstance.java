package com.aetherianartificer.townstead.politics.state;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Persistent public and diplomatic identity; claim geometry deliberately lives in Warstead. */
public record PolityInstance(ResourceLocation id,
                            String name,
                            int color,
                            @Nullable ResourceLocation emblem,
                            long createdAt,
                            ResourceLocation provenance,
                            PoliticalStatus.Polity status,
                            List<SettlementRef> settlements,
                            @Nullable ResourceLocation governmentOrganization) {
    public PolityInstance {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(status, "status");
        name = name.trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Polity name cannot be empty");
        settlements = List.copyOf(new LinkedHashSet<>(settlements));
    }

    public PoliticalActorRef actor() {
        return new PoliticalActorRef(PoliticalActorRef.Kind.POLITY, id);
    }
}

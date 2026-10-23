package com.aetherianartificer.townstead.politics.state;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Any political body: a village, a player's realm, a guild. Its kind decides whether it holds land,
 * how people join and which offices it has. {@code settlements} are the places whose town range it
 * holds; {@code home} is where a landless faction is based.
 */
public record Faction(ResourceLocation id,
                      ResourceLocation kind,
                      String name,
                      int color,
                      @Nullable ResourceLocation emblem,
                      long createdAt,
                      ResourceLocation provenance,
                      Status status,
                      List<SettlementRef> settlements,
                      @Nullable SettlementRef home) {
    public enum Status {
        ACTIVE, DORMANT, DISSOLVED;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Status parse(String value) {
            try {
                return valueOf(value.toUpperCase(Locale.ROOT));
            } catch (RuntimeException error) {
                return DORMANT;
            }
        }
    }

    public Faction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(status, "status");
        name = name.trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Faction name cannot be empty");
        settlements = List.copyOf(new LinkedHashSet<>(settlements));
    }

    public Party party() {
        return Party.faction(id);
    }

    public boolean active() {
        return status == Status.ACTIVE;
    }

    /** The settlement it is centred on: its first settlement, else its home. */
    public @Nullable SettlementRef seatSettlement() {
        return settlements.isEmpty() ? home : settlements.get(0);
    }

    public Faction withName(String value) {
        return new Faction(id, kind, value, color, emblem, createdAt, provenance, status, settlements, home);
    }

    public Faction withKind(ResourceLocation value) {
        return new Faction(id, value, name, color, emblem, createdAt, provenance, status, settlements, home);
    }

    public Faction withStatus(Status value) {
        return new Faction(id, kind, name, color, emblem, createdAt, provenance, value, settlements, home);
    }

    public Faction withSettlements(List<SettlementRef> value) {
        return new Faction(id, kind, name, color, emblem, createdAt, provenance, status, value, home);
    }
}

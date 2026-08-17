package com.aetherianartificer.townstead.chronicle.model;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * A reference to anything that can participate in a chronicle event. Display
 * names are captured at record time so history stays readable after the
 * referent dies, despawns, or never existed as an entity at all (concepts).
 *
 * <p>Field usage by kind:</p>
 * <ul>
 *   <li>VILLAGER / PLAYER — {@code uuid}</li>
 *   <li>ANIMAL — {@code uuid}, {@code str} = entity type id</li>
 *   <li>BUILDING — {@code intA} = villageId, {@code intB} = buildingId</li>
 *   <li>VILLAGE — {@code intA} = villageId</li>
 *   <li>CONCEPT — {@code str} = namespaced concept id (e.g. {@code ancestor:<uuid>}, {@code road:riverside})</li>
 *   <li>ITEM — {@code str} = item id, {@code uuid} = optional tracked-instance id</li>
 * </ul>
 */
public record ChronicleRef(Kind kind, @Nullable UUID uuid, int intA, int intB,
                           @Nullable String str, String displayName) {

    public enum Kind {
        VILLAGER, PLAYER, ANIMAL, BUILDING, VILLAGE, CONCEPT, ITEM;

        public static Kind byOrdinal(int ordinal) {
            Kind[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : CONCEPT;
        }

        /** People, whoever is playing them: a role written for a villager fits a player. */
        public boolean isPerson() {
            return this == VILLAGER || this == PLAYER;
        }
    }

    public ChronicleRef {
        Objects.requireNonNull(kind, "kind");
        displayName = displayName == null ? "" : displayName;
    }

    public static ChronicleRef villager(UUID uuid, String displayName) {
        return new ChronicleRef(Kind.VILLAGER, uuid, 0, 0, null, displayName);
    }

    public static ChronicleRef player(UUID uuid, String displayName) {
        return new ChronicleRef(Kind.PLAYER, uuid, 0, 0, null, displayName);
    }

    public static ChronicleRef animal(UUID uuid, String entityTypeId, String displayName) {
        return new ChronicleRef(Kind.ANIMAL, uuid, 0, 0, entityTypeId, displayName);
    }

    public static ChronicleRef building(int villageId, int buildingId, String displayName) {
        return new ChronicleRef(Kind.BUILDING, null, villageId, buildingId, null, displayName);
    }

    public static ChronicleRef village(int villageId, String displayName) {
        return new ChronicleRef(Kind.VILLAGE, null, villageId, 0, null, displayName);
    }

    public static ChronicleRef concept(String conceptId, String displayName) {
        return new ChronicleRef(Kind.CONCEPT, null, 0, 0, conceptId, displayName);
    }

    public static ChronicleRef item(String itemId, @Nullable UUID instanceId, String displayName) {
        return new ChronicleRef(Kind.ITEM, instanceId, 0, 0, itemId, displayName);
    }

    /** True for kinds whose identity is a live-entity UUID (knower-capable). */
    public boolean isEntityBacked() {
        return kind == Kind.VILLAGER || kind == Kind.PLAYER || kind == Kind.ANIMAL;
    }
}

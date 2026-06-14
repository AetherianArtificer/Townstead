package com.aetherianartificer.townstead.origin.disposition;

import net.minecraft.world.entity.EntityType;

import java.util.Map;
import java.util.Set;

/**
 * The loaded, data-pack-defined disposition groups and their relations (who a group regards as
 * friendly or hostile), plus the entity-type -> group membership index built from each group's
 * {@code members} list. Authored under {@code data/<ns>/disposition/<group>.json}. Replaced on
 * datapack reload by {@link DispositionRelationsLoader}; read by {@link DataDispositionSource}.
 */
public final class DispositionRelations {

    /** One group's relations: the group names it treats as friendly / hostile. */
    public record GroupDef(Set<String> friendly, Set<String> hostile) {}

    private static volatile Map<String, GroupDef> groups = Map.of();
    private static volatile Map<EntityType<?>, String> members = Map.of();

    private DispositionRelations() {}

    public static void replaceAll(Map<String, GroupDef> newGroups, Map<EntityType<?>, String> newMembers) {
        groups = Map.copyOf(newGroups);
        members = Map.copyOf(newMembers);
    }

    /** The group an entity type belongs to via a group's {@code members} list, or null. */
    public static String groupOf(EntityType<?> type) {
        return type == null ? null : members.get(type);
    }

    /** A group's relations, or null when the group declares none. */
    public static GroupDef relations(String group) {
        return group == null ? null : groups.get(group);
    }
}

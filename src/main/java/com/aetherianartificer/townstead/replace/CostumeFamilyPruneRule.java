package com.aetherianartificer.townstead.replace;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Which family tree nodes a costume left behind. A person's node qualifies only when nothing ties it
 * to anyone real: no children, no partner, not known to be alive, and parents that exist only for
 * people who also qualify. A real villager caught by this only loses placeholder parents, since MCA
 * remakes their own node when next asked.
 */
public final class CostumeFamilyPruneRule {
    public record Node(UUID id, boolean player, boolean deceased, boolean partnered,
                       Set<UUID> parents, Set<UUID> children) {}

    private CostumeFamilyPruneRule() {}

    public static Set<UUID> removable(Collection<Node> nodes, Set<UUID> known) {
        Map<UUID, Node> byId = new HashMap<>();
        Set<UUID> people = new HashSet<>();
        for (Node node : nodes) {
            byId.put(node.id(), node);
            if (!node.player() && !node.deceased() && !node.partnered() && node.children().isEmpty()
                    && !known.contains(node.id())) {
                people.add(node.id());
            }
        }
        // Dropping a person can stop a shared parent qualifying, which can drop that parent's other children.
        boolean changed = true;
        while (changed) {
            changed = people.removeIf(id -> {
                for (UUID parent : byId.get(id).parents()) {
                    Node node = byId.get(parent);
                    if (node != null && !placeholder(node, people, known)) return true;
                }
                return false;
            });
        }
        Set<UUID> removable = new HashSet<>(people);
        for (UUID id : people) {
            for (UUID parent : byId.get(id).parents()) {
                if (byId.containsKey(parent)) removable.add(parent);
            }
        }
        return removable;
    }

    private static boolean placeholder(Node node, Set<UUID> people, Set<UUID> known) {
        return !node.player() && node.deceased() && !node.partnered() && node.parents().isEmpty()
                && !known.contains(node.id()) && people.containsAll(node.children());
    }
}

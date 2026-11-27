package com.aetherianartificer.townstead.replace;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CostumeFamilyPruneRuleTest {
    private final List<CostumeFamilyPruneRule.Node> nodes = new ArrayList<>();

    private UUID node(boolean player, boolean deceased, boolean partnered, Set<UUID> parents, Set<UUID> children) {
        UUID id = UUID.randomUUID();
        nodes.add(new CostumeFamilyPruneRule.Node(id, player, deceased, partnered, parents, children));
        return id;
    }

    /** A person and their two placeholder parents, as a costume spawn left them. */
    private UUID[] ghost() {
        UUID self = UUID.randomUUID();
        UUID father = node(false, true, false, Set.of(), Set.of(self));
        UUID mother = node(false, true, false, Set.of(), Set.of(self));
        nodes.add(new CostumeFamilyPruneRule.Node(self, false, false, false, Set.of(father, mother), Set.of()));
        return new UUID[]{self, father, mother};
    }

    private Set<UUID> removable(UUID... known) {
        return CostumeFamilyPruneRule.removable(nodes, Set.of(known));
    }

    @Test
    void removesALeftBehindPersonAndTheirPlaceholderParents() {
        UUID[] ghost = ghost();
        assertEquals(Set.of(ghost[0], ghost[1], ghost[2]), removable());
    }

    @Test
    void keepsAnyoneKnownToBeAlive() {
        UUID[] ghost = ghost();
        assertTrue(removable(ghost[0]).isEmpty());
    }

    @Test
    void keepsPlayers() {
        UUID player = node(true, false, false, Set.of(), Set.of());
        assertTrue(!removable().contains(player));
    }

    @Test
    void keepsPeopleWithFamilyOfTheirOwn() {
        UUID child = UUID.randomUUID();
        UUID parent = node(false, false, false, Set.of(), Set.of(child));
        UUID spouse = node(false, false, true, Set.of(), Set.of());
        assertTrue(!removable().contains(parent));
        assertTrue(!removable().contains(spouse));
    }

    @Test
    void keepsTheChildOfALivingParent() {
        UUID child = UUID.randomUUID();
        UUID parent = node(false, false, false, Set.of(), Set.of(child));
        nodes.add(new CostumeFamilyPruneRule.Node(child, false, false, false, Set.of(parent), Set.of()));
        assertTrue(removable().isEmpty());
    }

    @Test
    void keepsAPlaceholderWithAnotherChildAndEveryChildOfIt() {
        UUID kept = UUID.randomUUID();
        UUID loose = UUID.randomUUID();
        UUID parent = node(false, true, false, Set.of(), Set.of(kept, loose));
        nodes.add(new CostumeFamilyPruneRule.Node(kept, false, false, false, Set.of(parent), Set.of()));
        nodes.add(new CostumeFamilyPruneRule.Node(loose, false, false, false, Set.of(parent), Set.of()));
        assertTrue(removable(kept).isEmpty());
    }

    @Test
    void keepsTheDeadAndTheirParents() {
        UUID dead = UUID.randomUUID();
        UUID parent = node(false, true, false, Set.of(), Set.of(dead));
        nodes.add(new CostumeFamilyPruneRule.Node(dead, false, true, false, Set.of(parent), Set.of()));
        assertTrue(removable().isEmpty());
    }

    @Test
    void keepsADeadParentWithAncestorsOfTheirOwn() {
        UUID self = UUID.randomUUID();
        UUID grandparent = node(false, true, false, Set.of(), Set.of());
        UUID parent = node(false, true, false, Set.of(grandparent), Set.of(self));
        nodes.add(new CostumeFamilyPruneRule.Node(self, false, false, false, Set.of(parent), Set.of()));
        assertTrue(removable().isEmpty());
    }

    @Test
    void removesAPersonWhoseParentsAreAlreadyGone() {
        UUID self = node(false, false, false, Set.of(UUID.randomUUID()), Set.of());
        assertEquals(Set.of(self), removable());
    }
}

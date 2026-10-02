package com.aetherianartificer.townstead.rebirth;

import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Moves a player's place in MCA's family tree onto a memorial entry for the life that ended. The
 * memorial keeps the parents, children and spouse, and is marked deceased; a married spouse is
 * widowed. The player then gets a fresh entry under their new name.
 */
final class FamilySplit {
    private FamilySplit() {}

    static void split(ServerLevel level, UUID player, UUID memorial, String oldName, String newName) {
        FamilyTree tree = FamilyTree.get(level);
        Optional<FamilyTreeNode> found = tree.getOrEmpty(player);
        if (found.isEmpty()) return;
        FamilyTreeNode old = found.get();
        FamilyTreeNode past = tree.getOrCreate(memorial, oldName, old.gender(), true);
        past.setDeceased(true);

        tree.getOrEmpty(old.father()).ifPresent(father -> {
            father.children().remove(player);
            past.setFather(father);
        });
        tree.getOrEmpty(old.mother()).ifPresent(mother -> {
            mother.children().remove(player);
            past.setMother(mother);
        });
        for (UUID childId : Set.copyOf(old.children())) {
            tree.getOrEmpty(childId).ifPresent(child -> {
                if (player.equals(child.father())) child.setFather(past);
                else if (player.equals(child.mother())) child.setMother(past);
            });
        }

        if (FamilyTreeNode.isValid(old.partner())) {
            boolean married = old.getRelationshipState().isMarried();
            tree.getOrEmpty(old.partner()).ifPresent(spouse -> {
                if (married) {
                    past.updatePartner(spouse);
                    spouse.updatePartner(past);
                    past.setRelationshipState(RelationshipState.WIDOW);
                    spouse.setRelationshipState(RelationshipState.WIDOW);
                } else {
                    spouse.updatePartner((net.minecraft.world.entity.Entity) null, RelationshipState.SINGLE);
                }
            });
        }

        tree.remove(player);
        tree.getOrCreate(player, newName, old.gender(), true);
        tree.setDirty();
    }
}

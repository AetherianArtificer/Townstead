package com.aetherianartificer.townstead.replace;

import com.aetherianartificer.townstead.mixin.compat.mca.FamilyTreeAccess;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.Names;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.Util;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Keeps a costumed mob's person out of MCA's family tree. Building or loading one runs through
 * {@link #outside}, and a person a mob is showing stays out for as long as they are shown.
 */
public final class CostumeFamily {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    // Nodes need a tree to answer lookups. This one is never saved and never holds them.
    private static final class Scratch {
        static final FamilyTree TREE = FamilyTreeAccess.townstead$create(null);
    }

    private CostumeFamily() {}

    /** Runs {@code work} with every family tree write it reaches kept out of the real tree. */
    public static <T> T outside(Supplier<T> work) {
        DEPTH.set(DEPTH.get() + 1);
        try {
            return work.get();
        } finally {
            DEPTH.set(DEPTH.get() - 1);
        }
    }

    public static boolean active() {
        return DEPTH.get() > 0;
    }

    public static boolean keptOut(Entity entity) {
        return !(entity instanceof Player) && (active() || WildCostume.isShownPerson(entity));
    }

    public static FamilyTreeNode node(UUID id, String name, Gender gender) {
        return new FamilyTreeNode(Scratch.TREE, id, name, false, gender, Util.NIL_UUID, Util.NIL_UUID);
    }

    /** A person made real by a cure enters the tree as MCA's own spawns do, with late parents. */
    static void found(VillagerEntityMCA person) {
        if (!(person.level() instanceof ServerLevel level)) return;
        FamilyTreeNode entry = person.getRelationships().getFamilyEntry();
        if (FamilyTreeNode.isValid(entry.father()) || FamilyTreeNode.isValid(entry.mother())) return;
        FamilyTree tree = FamilyTree.get(level);
        FamilyTreeNode father = tree.getOrCreate(UUID.randomUUID(), Names.pickCitizenName(Gender.MALE), Gender.MALE);
        FamilyTreeNode mother = tree.getOrCreate(UUID.randomUUID(), Names.pickCitizenName(Gender.FEMALE), Gender.FEMALE);
        father.setDeceased(true);
        mother.setDeceased(true);
        entry.setFather(father);
        entry.setMother(mother);
    }
}

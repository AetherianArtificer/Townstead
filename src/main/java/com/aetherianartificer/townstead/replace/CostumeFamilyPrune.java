package com.aetherianartificer.townstead.replace;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.mixin.compat.mca.FamilyTreeAccess;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Clears what costumes left in MCA's family tree before they stopped writing to it. Runs once per
 * world, after copying the tree's file aside.
 */
public final class CostumeFamilyPrune {
    private static final String TREE_FILE = "MCA-FamilyTree.dat";
    private static final String BACKUP_FILE = "MCA-FamilyTree.dat.before-costume-prune";

    private CostumeFamilyPrune() {}

    public static void run(MinecraftServer server) {
        Done done = Done.get(server);
        if (done.value) return;
        if (MobReplacements.any() && !prune(server)) return;
        done.value = true;
        done.setDirty();
    }

    /** False when the backup could not be made; nothing is changed and the next start tries again. */
    private static boolean prune(MinecraftServer server) {
        FamilyTree tree = FamilyTree.get(server.overworld());
        Map<UUID, FamilyTreeNode> entries = ((FamilyTreeAccess) tree).townstead$entries();
        List<CostumeFamilyPruneRule.Node> nodes = new ArrayList<>(entries.size());
        for (FamilyTreeNode node : entries.values()) {
            Set<UUID> parents = new LinkedHashSet<>();
            if (FamilyTreeNode.isValid(node.father())) parents.add(node.father());
            if (FamilyTreeNode.isValid(node.mother())) parents.add(node.mother());
            nodes.add(new CostumeFamilyPruneRule.Node(node.id(), node.isPlayer(), node.isDeceased(),
                    FamilyTreeNode.isValid(node.partner()), parents, node.children()));
        }
        Set<UUID> removable = CostumeFamilyPruneRule.removable(nodes, knownAlive(server));
        if (removable.isEmpty()) return true;
        Path data = server.getWorldPath(LevelResource.ROOT).resolve("data");
        try {
            if (Files.exists(data.resolve(TREE_FILE))) {
                Files.copy(data.resolve(TREE_FILE), data.resolve(BACKUP_FILE), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Townstead.LOGGER.warn("Could not back up {}; leaving the family tree as it is", TREE_FILE, e);
            return false;
        }
        removable.forEach(tree::remove);
        Townstead.LOGGER.info("Removed {} of {} family tree nodes left behind by costumed mobs; the old tree is at data/{}",
                removable.size(), nodes.size(), BACKUP_FILE);
        return true;
    }

    private static Set<UUID> knownAlive(MinecraftServer server) {
        Set<UUID> known = new HashSet<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Village village : VillageManager.get(level)) {
                village.getResidentsUUIDs().forEach(known::add);
            }
            for (Entity entity : level.getAllEntities()) {
                known.add(entity.getUUID());
            }
        }
        return known;
    }

    private static final class Done extends SavedData {
        private static final String FILE_ID = "townstead_costume_family_prune";
        private static final String KEY = "done";
        private boolean value;

        static Done get(MinecraftServer server) {
            //? if >=1.21 {
            return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(Done::new, Done::load), FILE_ID);
            //?} else {
            /*return server.overworld().getDataStorage().computeIfAbsent(Done::load, Done::new, FILE_ID);
            *///?}
        }

        //? if >=1.21 {
        static Done load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        //?} else {
        /*static Done load(CompoundTag tag) {
        *///?}
            Done done = new Done();
            done.value = tag.getBoolean(KEY);
            return done;
        }

        //? if >=1.21 {
        @Override
        public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        //?} else {
        /*@Override
        public CompoundTag save(CompoundTag tag) {
        *///?}
            tag.putBoolean(KEY, value);
            return tag;
        }
    }
}

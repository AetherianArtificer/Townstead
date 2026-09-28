package com.aetherianartificer.townstead.politics.relations;

import com.aetherianartificer.townstead.root.disposition.Disposition;
import com.aetherianartificer.townstead.root.disposition.DispositionGroups;
import com.aetherianartificer.townstead.root.disposition.DispositionRelations;
import com.aetherianartificer.townstead.root.disposition.DispositionSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Factions over natural groups. The people of a faction that welcomes a group are friends to that
 * group and to every group it counts as friendly, both ways: wild vampires spare a town that
 * welcomes vampires, and its guards spare them. Members of allied factions are friends. Only ever
 * answers friendly, so hunters, who welcome no vampire, stay as vicious as their group says.
 */
public final class FactionDispositionSource implements DispositionSource {

    @Override
    public @Nullable Disposition between(LivingEntity viewer, LivingEntity other) {
        MinecraftServer server = viewer.getServer();
        if (server == null) return null;
        Set<ResourceLocation> viewerFactions = FactionMembership.of(viewer);
        Set<ResourceLocation> otherFactions = FactionMembership.of(other);
        if (viewerFactions.isEmpty() && otherFactions.isEmpty()) return null;
        if (welcomed(server, otherFactions, DispositionGroups.of(viewer))
                || welcomed(server, viewerFactions, DispositionGroups.of(other))) {
            return Disposition.FRIENDLY;
        }
        for (ResourceLocation a : viewerFactions) {
            for (ResourceLocation b : otherFactions) {
                if (FactionRelations.allied(server, a, b)) return Disposition.FRIENDLY;
            }
        }
        return null;
    }

    private static boolean welcomed(MinecraftServer server, Set<ResourceLocation> factions, String group) {
        for (ResourceLocation faction : factions) {
            for (String welcome : FactionRelations.welcomes(server, faction)) {
                if (kin(group, welcome)) return true;
            }
        }
        return false;
    }

    /** A group is kin to a welcome when it is that group or counts it friendly. */
    public static boolean kin(String group, String welcome) {
        if (group.equals(welcome)) return true;
        DispositionRelations.GroupDef def = DispositionRelations.relations(group);
        return def != null && def.friendly().contains(welcome);
    }
}

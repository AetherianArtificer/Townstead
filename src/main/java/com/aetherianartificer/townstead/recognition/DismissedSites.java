package com.aetherianartificer.townstead.recognition;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * Open-air sites, such as pens and squares, that a player removed from their village. Automatic
 * recognition leaves them alone until the player works on them again: for an enclosed site any
 * block on or in it, for an unenclosed one a block that kind of site is made of (its bell, say), so
 * building a house where a square once was does not bring the square back. Adding the site by hand
 * still works, and clears it from here.
 */
public final class DismissedSites extends SavedData {
    private static final String FILE_ID = "townstead_dismissed_sites";

    private record Site(String dimension, BlockPos min, BlockPos max, String type) {
        boolean touches(String dim, BlockPos pos) {
            return dimension.equals(dim)
                    && pos.getX() >= min.getX() - 1 && pos.getX() <= max.getX() + 1
                    && pos.getY() >= min.getY() - 1 && pos.getY() <= max.getY() + 1
                    && pos.getZ() >= min.getZ() - 1 && pos.getZ() <= max.getZ() + 1;
        }
    }

    private final List<Site> sites = new ArrayList<>();

    public static DismissedSites get(MinecraftServer server) {
        //? if >=1.21 {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(DismissedSites::new, DismissedSites::load), FILE_ID);
        //?} else {
        /*return server.overworld().getDataStorage().computeIfAbsent(DismissedSites::load, DismissedSites::new, FILE_ID);
        *///?}
    }

    public static void dismiss(ServerLevel level, BlockPos min, BlockPos max, String type) {
        DismissedSites data = get(level.getServer());
        data.sites.add(new Site(level.dimension().location().toString(), min.immutable(), max.immutable(),
                type == null ? "" : type));
        data.setDirty();
    }

    /** Whether a site centred at {@code center} was removed by a player. */
    public static boolean dismissed(ServerLevel level, BlockPos center) {
        String dim = level.dimension().location().toString();
        for (Site site : get(level.getServer()).sites) {
            if (site.touches(dim, center)) return true;
        }
        return false;
    }

    /** A block changed from {@code before} to {@code after}: reopens the sites that change works on. */
    public static void changed(ServerLevel level, BlockPos pos,
                               net.minecraft.world.level.block.state.BlockState before,
                               net.minecraft.world.level.block.state.BlockState after) {
        DismissedSites data = get(level.getServer());
        if (data.sites.isEmpty()) return;
        String dim = level.dimension().location().toString();
        if (data.sites.removeIf(site -> site.touches(dim, pos) && reopens(site, before, after))) data.setDirty();
    }

    private static boolean reopens(Site site, net.minecraft.world.level.block.state.BlockState before,
                                   net.minecraft.world.level.block.state.BlockState after) {
        if (site.type().isEmpty() || SiteRequirements.isEnclosed(site.type())) return true;
        var type = net.conczin.mca.resources.BuildingTypes.getInstance().getBuildingTypes().get(site.type());
        return type == null || OptionalBuildingRecognition.matchesBlock(type, before)
                || OptionalBuildingRecognition.matchesBlock(type, after);
    }

    /** A block on or in a removed site changed, or the site was added by hand: the player is working on it again. */
    public static void changed(ServerLevel level, BlockPos pos) {
        DismissedSites data = get(level.getServer());
        if (data.sites.isEmpty()) return;
        String dim = level.dimension().location().toString();
        if (data.sites.removeIf(site -> site.touches(dim, pos))) data.setDirty();
    }

    //? if >=1.21 {
    private static DismissedSites load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
    //?} else {
    /*private static DismissedSites load(CompoundTag tag) {
    *///?}
        DismissedSites data = new DismissedSites();
        ListTag list = tag.getList("sites", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            data.sites.add(new Site(entry.getString("dimension"),
                    BlockPos.of(entry.getLong("min")), BlockPos.of(entry.getLong("max")), entry.getString("type")));
        }
        return data;
    }

    @Override
    //? if >=1.21 {
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
    //?} else {
    /*public CompoundTag save(CompoundTag tag) {
    *///?}
        ListTag list = new ListTag();
        for (Site site : sites) {
            CompoundTag entry = new CompoundTag();
            entry.putString("dimension", site.dimension());
            entry.putLong("min", site.min().asLong());
            entry.putLong("max", site.max().asLong());
            entry.putString("type", site.type());
            list.add(entry);
        }
        tag.put("sites", list);
        return tag;
    }
}

package com.aetherianartificer.townstead.api.v1;

import com.aetherianartificer.townstead.api.v1.model.OrderSnapshot;
import com.aetherianartificer.townstead.api.v1.model.StorageRoleSnapshot;
import com.aetherianartificer.townstead.api.v1.model.VillageId;
import com.aetherianartificer.townstead.api.v1.model.WorksiteSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Worksites, their production orders, and the storage the work engine reads. */
public interface WorkApi {

    List<WorksiteSnapshot> worksites(MinecraftServer server, VillageId village);

    Optional<WorksiteSnapshot> worksite(MinecraftServer server, long worksiteId);

    /** The worksite whose anchor sits at {@code pos}, whatever binding registered it. */
    Optional<WorksiteSnapshot> worksiteAt(ServerLevel level, BlockPos pos);

    /** A worksite's order list, top priority first. */
    List<OrderSnapshot> orders(MinecraftServer server, long worksiteId);

    /** Items of {@code item} counted across a village's recognised storage. */
    int countItem(ServerLevel level, VillageId village, ResourceLocation item);

    /** Items in tag {@code tag} counted across a village's recognised storage. */
    int countTag(ServerLevel level, VillageId village, ResourceLocation tag);

    /** The storage roles the block at {@code pos} carries, as role ids; empty when it is not storage. */
    Set<String> storageRolesAt(ServerLevel level, BlockPos pos);

    /** Every registered storage role definition. */
    List<StorageRoleSnapshot> storageRoles();
}

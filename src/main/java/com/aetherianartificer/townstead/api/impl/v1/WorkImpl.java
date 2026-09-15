package com.aetherianartificer.townstead.api.impl.v1;

import com.aetherianartificer.townstead.api.v1.WorkApi;
import com.aetherianartificer.townstead.api.v1.model.OrderSnapshot;
import com.aetherianartificer.townstead.api.v1.model.StorageRoleSnapshot;
import com.aetherianartificer.townstead.api.v1.model.VillageId;
import com.aetherianartificer.townstead.api.v1.model.WorksiteSnapshot;
import com.aetherianartificer.townstead.storage.StorageRoleDef;
import com.aetherianartificer.townstead.storage.StorageRoles;
import com.aetherianartificer.townstead.work.order.Order;
import com.aetherianartificer.townstead.work.order.VillageStores;
import com.aetherianartificer.townstead.work.site.Worksite;
import com.aetherianartificer.townstead.work.site.WorksiteRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

final class WorkImpl implements WorkApi {

    @Override
    public List<WorksiteSnapshot> worksites(MinecraftServer server, VillageId village) {
        List<WorksiteSnapshot> out = new ArrayList<>();
        try {
            if (server == null || village == null) return out;
            for (Worksite site : WorksiteRegister.get(server).all()) {
                if (site.villageId() != village.villageId()) continue;
                if (!site.key().dimension().equals(village.dimension())) continue;
                out.add(snapshot(site));
            }
        } catch (Throwable t) {
            ApiSupport.swallow("work.worksites", t);
        }
        return out;
    }

    @Override
    public Optional<WorksiteSnapshot> worksite(MinecraftServer server, long worksiteId) {
        try {
            if (server == null) return Optional.empty();
            Worksite site = WorksiteRegister.get(server).byId(worksiteId);
            return site == null ? Optional.empty() : Optional.of(snapshot(site));
        } catch (Throwable t) {
            ApiSupport.swallow("work.worksite", t);
            return Optional.empty();
        }
    }

    @Override
    public Optional<WorksiteSnapshot> worksiteAt(ServerLevel level, BlockPos pos) {
        try {
            if (level == null || pos == null) return Optional.empty();
            ResourceLocation dimension = level.dimension().location();
            for (Worksite site : WorksiteRegister.get(level.getServer()).all()) {
                if (site.key().dimension().equals(dimension) && site.key().pos().equals(pos)) {
                    return Optional.of(snapshot(site));
                }
            }
            return Optional.empty();
        } catch (Throwable t) {
            ApiSupport.swallow("work.worksiteAt", t);
            return Optional.empty();
        }
    }

    @Override
    public List<OrderSnapshot> orders(MinecraftServer server, long worksiteId) {
        List<OrderSnapshot> out = new ArrayList<>();
        try {
            if (server == null) return out;
            Worksite site = WorksiteRegister.get(server).byId(worksiteId);
            if (site == null) return out;
            int index = 0;
            for (Order order : site.orders().orders()) {
                out.add(new OrderSnapshot(site.id(), index++, order.output(), Optional.ofNullable(order.product()),
                        order.productName(), lower(order.kind()), lower(order.mode()), order.target(),
                        lower(order.scope()), order.paused(), Optional.ofNullable(order.profession()), order.minRank(),
                        Optional.ofNullable(order.villager()), lower(order.operation())));
            }
        } catch (Throwable t) {
            ApiSupport.swallow("work.orders", t);
        }
        return out;
    }

    @Override
    public int countItem(ServerLevel level, VillageId village, ResourceLocation item) {
        try {
            if (level == null || village == null || item == null) return 0;
            return VillageStores.count(level, village.villageId(), item, new HashSet<>());
        } catch (Throwable t) {
            ApiSupport.swallow("work.countItem", t);
            return 0;
        }
    }

    @Override
    public int countTag(ServerLevel level, VillageId village, ResourceLocation tag) {
        try {
            if (level == null || village == null || tag == null) return 0;
            return VillageStores.countTag(level, village.villageId(), tag, new HashSet<>());
        } catch (Throwable t) {
            ApiSupport.swallow("work.countTag", t);
            return 0;
        }
    }

    @Override
    public Set<String> storageRolesAt(ServerLevel level, BlockPos pos) {
        try {
            if (level == null || pos == null) return Set.of();
            Set<String> out = new HashSet<>();
            for (StorageRoleDef.Role role : StorageRoles.semanticRoles(level.getBlockState(pos))) {
                out.add(role.name().toLowerCase(Locale.ROOT));
            }
            return out;
        } catch (Throwable t) {
            ApiSupport.swallow("work.storageRolesAt", t);
            return Set.of();
        }
    }

    @Override
    public List<StorageRoleSnapshot> storageRoles() {
        List<StorageRoleSnapshot> out = new ArrayList<>();
        try {
            for (StorageRoleDef def : StorageRoles.all()) {
                out.add(new StorageRoleSnapshot(def.id(), def.role().name().toLowerCase(Locale.ROOT), def.blocks(),
                        def.blockTags(), def.namespaces()));
            }
        } catch (Throwable t) {
            ApiSupport.swallow("work.storageRoles", t);
        }
        return out;
    }

    static WorksiteSnapshot snapshot(Worksite site) {
        Optional<VillageId> village = site.villageId() == Worksite.NO_VILLAGE
                ? Optional.empty() : Optional.of(new VillageId(site.key().dimension(), site.villageId()));
        return new WorksiteSnapshot(site.id(), site.key().binding(), site.key().dimension(), site.key().pos(),
                site.name() == null ? "" : site.name(), site.nameCustom(), village, Optional.ofNullable(site.driver()),
                site.createdGameTime(), site.lastSeenGameTime(), site.orders().size());
    }

    private static String lower(Enum<?> value) {
        return value == null ? "" : value.name().toLowerCase(Locale.ROOT);
    }
}

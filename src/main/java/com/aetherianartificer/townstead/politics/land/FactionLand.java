package com.aetherianartificer.townstead.politics.land;

import com.aetherianartificer.townstead.compat.mca.McaBuildingCompat;
import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.politics.state.Faction;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.SeatInstance;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import com.aetherianartificer.townstead.village.TownRange;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/**
 * Who holds the land at a position. Townstead's own answer: a faction holds its settlements' town
 * range, and the building of its Seat inside another faction's town is its own. Another mod, such
 * as Warstead with claim points, may replace this with {@link #register}.
 */
public final class FactionLand {
    public interface Provider {
        String id();

        /** The deepest faction holding this position, or empty. */
        Optional<ResourceLocation> holder(MinecraftServer server, ResourceLocation dimension, BlockPos pos);
    }

    private static final Provider TOWN_RANGE = new Provider() {
        @Override public String id() { return "townstead:town_range"; }

        @Override
        public Optional<ResourceLocation> holder(MinecraftServer server, ResourceLocation dimension, BlockPos pos) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            if (level == null) return Optional.empty();
            Village village = TownRange.at(level, pos).orElse(null);
            if (village == null) return Optional.empty();
            SettlementRef settlement = new SettlementRef(dimension, village.getId());
            PoliticalSavedData data = PoliticalSavedData.get(server);
            Faction local = data.faction(settlement);
            for (SeatInstance seat : data.seats()) {
                if (!seat.settlement().equals(settlement) || seat.damaged()) continue;
                if (local != null && seat.faction().equals(local.id())) continue;
                Faction owner = data.faction(seat.faction());
                Building building = McaBuildings.byId(village, seat.buildingId());
                if (owner != null && owner.active() && building != null
                        && McaBuildingCompat.contains(level, village, building, pos)) return Optional.of(owner.id());
            }
            return local == null || !local.active() ? Optional.empty() : Optional.of(local.id());
        }
    };

    private static volatile Provider provider = TOWN_RANGE;

    private FactionLand() {}

    public static synchronized void register(Provider value) {
        provider = value == null ? TOWN_RANGE : value;
    }

    public static Optional<ResourceLocation> holder(MinecraftServer server, ResourceLocation dimension, BlockPos pos) {
        if (server == null || dimension == null || pos == null) return Optional.empty();
        return provider.holder(server, dimension, pos);
    }
}

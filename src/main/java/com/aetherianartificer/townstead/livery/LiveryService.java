package com.aetherianartificer.townstead.livery;

import com.aetherianartificer.townstead.culture.Culture;
import com.aetherianartificer.townstead.culture.Cultures;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import com.aetherianartificer.townstead.politics.definition.FactionKind;
import com.aetherianartificer.townstead.politics.definition.PoliticalDefinitions;
import com.aetherianartificer.townstead.politics.heraldry.HeraldryService;
import com.aetherianartificer.townstead.politics.state.BondInstance;
import com.aetherianartificer.townstead.politics.state.Faction;
import com.aetherianartificer.townstead.politics.state.FactionBonds;
import com.aetherianartificer.townstead.politics.state.Party;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Who wears livery and which one.
 *
 * <p>Villagers wear it when their profession is tagged {@code townstead:wears_livery} (guards and
 * archers ship tagged; later martial classes add themselves) or when they hold an office. Players
 * wear it when they chose to. The livery is the lowest body's: the settlement's proclaimed one, then
 * its faction's, then each faction above up to the sovereign, and failing all of those, the style of
 * the settlement's predominant culture.</p>
 */
public final class LiveryService {
    public static final TagKey<VillagerProfession> WEARS_LIVERY = TagKey.create(Registries.VILLAGER_PROFESSION, id("townstead:wears_livery"));
    /** A settlement's predominant culture is recounted at most once a day. */
    private static final long CULTURE_TTL = 24000L;
    private static final Map<SettlementRef, CultureCount> CULTURES = new HashMap<>();

    private record CultureCount(@Nullable ResourceLocation culture, long at) {}

    private LiveryService() {}

    public static @Nullable LiveryView resolve(ServerLevel level, Entity entity) {
        if (entity instanceof ServerPlayer player) return forPlayer(level, player);
        if (entity instanceof VillagerEntityMCA villager) return forVillager(level, villager);
        return null;
    }

    private static @Nullable LiveryView forVillager(ServerLevel level, VillagerEntityMCA villager) {
        PoliticalSavedData politics = PoliticalSavedData.get(level.getServer());
        if (!wears(villager, politics)) return null;
        Village home = villager.getResidency().getHomeVillage().orElse(null);
        if (home == null) return null;
        SettlementRef settlement = new SettlementRef(level.dimension().location(), home.getId());
        Faction faction = politics.faction(settlement);
        return chain(level, politics, settlement, faction == null || !faction.active() ? null : faction.id());
    }

    private static boolean wears(VillagerEntityMCA villager, PoliticalSavedData politics) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(profession).is(WEARS_LIVERY)) return true;
        return !offices(politics, villager.getUUID()).isEmpty();
    }

    /** The factions this person holds an office in. */
    private static List<ResourceLocation> offices(PoliticalSavedData politics, UUID person) {
        List<ResourceLocation> out = new ArrayList<>();
        Party self = Party.person(person);
        for (BondInstance bond : politics.bonds(self)) {
            if (!bond.active()) continue;
            Party other = bond.other(self);
            ResourceLocation factionId = other == null ? null : other.faction();
            Faction faction = factionId == null ? null : politics.faction(factionId);
            FactionKind kind = faction == null ? null : PoliticalDefinitions.snapshot().kind(faction.kind());
            if (kind != null && kind.office(bond.kind()) != null) out.add(factionId);
        }
        return out;
    }

    private static @Nullable LiveryView forPlayer(ServerLevel level, ServerPlayer player) {
        if (!LiverySavedData.get(level.getServer()).wears(player.getUUID())) return null;
        PoliticalSavedData politics = PoliticalSavedData.get(level.getServer());
        ResourceLocation lowest = lowestFaction(politics, player.getUUID());
        if (lowest == null) return null;
        Faction faction = politics.faction(lowest);
        return chain(level, politics, faction == null ? null : faction.seatSettlement(), lowest);
    }

    /** Of the factions a person belongs to, the one no other of theirs sits beneath. */
    static @Nullable ResourceLocation lowestFaction(PoliticalSavedData politics, UUID person) {
        Set<ResourceLocation> mine = new HashSet<>();
        Party self = Party.person(person);
        for (BondInstance bond : politics.bonds(self)) {
            if (!bond.active()) continue;
            Party other = bond.other(self);
            ResourceLocation factionId = other == null ? null : other.faction();
            Faction faction = factionId == null ? null : politics.faction(factionId);
            if (faction != null && faction.active()) mine.add(factionId);
        }
        Set<ResourceLocation> parents = new HashSet<>();
        for (ResourceLocation faction : mine) {
            ResourceLocation parent = FactionBonds.parent(politics, faction);
            if (parent != null) parents.add(parent);
        }
        for (ResourceLocation faction : mine) if (!parents.contains(faction)) return faction;
        return mine.isEmpty() ? null : mine.iterator().next();
    }

    private static @Nullable LiveryView chain(ServerLevel level, PoliticalSavedData politics,
                                              @Nullable SettlementRef settlement, @Nullable ResourceLocation faction) {
        if (settlement != null) {
            LiveryView view = proclaimed(LiverySavedData.get(level.getServer()).get(HeraldryService.settlement(settlement)));
            if (view != null) return view;
        }
        LiveryView view = factions(level, politics, faction);
        return view != null ? view : cultureDefault(level, settlement);
    }

    /** The first proclaimed livery from this faction upward. */
    private static @Nullable LiveryView factions(ServerLevel level, PoliticalSavedData politics, @Nullable ResourceLocation faction) {
        LiverySavedData livery = LiverySavedData.get(level.getServer());
        Set<ResourceLocation> seen = new HashSet<>();
        for (ResourceLocation current = faction; current != null && seen.add(current); current = FactionBonds.parent(politics, current)) {
            LiveryView view = proclaimed(livery.get(HeraldryService.faction(current)));
            if (view != null) return view;
        }
        return null;
    }

    private static @Nullable LiveryView cultureDefault(ServerLevel level, @Nullable SettlementRef settlement) {
        if (settlement == null) return null;
        Culture culture = Cultures.get(predominantCulture(level, settlement));
        LiveryStyle style = culture == null ? null : LiveryStyles.get(culture.clothing().livery());
        return style == null ? null : LiveryView.of(style, style.primary(), style.secondary());
    }

    /**
     * What a body wears when it proclaims nothing of its own: the livery of the faction {@code above}
     * or higher, else its settlement's predominant culture's, else plain gear.
     */
    public static Component inherited(ServerLevel level, @Nullable SettlementRef settlement, @Nullable ResourceLocation above) {
        LiveryView view = inheritedView(level, settlement, above);
        LiveryStyle style = view == null ? null : LiveryStyles.get(view.style());
        return style == null ? Component.translatable("charter.townstead.heraldry.livery_plain") : style.name();
    }

    /** The livery {@link #inherited} names, or null for plain gear. */
    public static @Nullable LiveryView inheritedView(ServerLevel level, @Nullable SettlementRef settlement, @Nullable ResourceLocation above) {
        LiveryView view = factions(level, PoliticalSavedData.get(level.getServer()), above);
        return view != null ? view : cultureDefault(level, settlement);
    }

    private static @Nullable LiveryView proclaimed(@Nullable LiverySavedData.Entry entry) {
        LiveryStyle style = entry == null ? null : LiveryStyles.get(entry.style());
        return style == null ? null : LiveryView.of(style, entry.primary(), entry.secondary());
    }

    /** The culture most of a settlement's loaded residents keep, counted at most daily. */
    public static @Nullable ResourceLocation predominantCulture(ServerLevel level, SettlementRef settlement) {
        long now = level.getGameTime();
        CultureCount cached = CULTURES.get(settlement);
        if (cached != null && now - cached.at() < CULTURE_TTL) return cached.culture();
        ServerLevel source = level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, settlement.dimension()));
        Village village = source == null ? null : VillageManager.get(source).getOrEmpty(settlement.villageId()).orElse(null);
        ResourceLocation best = null;
        if (village != null) {
            // Each resident counts by their blend, so a town of half-assimilated newcomers is half theirs.
            Map<String, Float> counts = new LinkedHashMap<>();
            village.getResidentsUUIDs().forEach(person -> {
                if (person != null && source.getEntity(person) instanceof VillagerEntityMCA resident) {
                    TownsteadVillagers.get(resident).life().cultureBlend().forEach((culture, share) -> counts.merge(culture, share, Float::sum));
                }
            });
            best = counts.entrySet().stream().max(Map.Entry.comparingByValue())
                    .map(entry -> ResourceLocation.tryParse(entry.getKey())).orElse(null);
        }
        CULTURES.put(settlement, new CultureCount(best, now));
        return best;
    }

    private static ResourceLocation id(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) throw new IllegalStateException(value);
        return id;
    }
}

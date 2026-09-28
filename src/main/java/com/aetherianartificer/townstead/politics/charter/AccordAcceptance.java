package com.aetherianartificer.townstead.politics.charter;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.aetherianartificer.townstead.politics.relations.FactionDispositionSource;
import com.aetherianartificer.townstead.politics.relations.FactionRelations;
import com.aetherianartificer.townstead.politics.standing.StandingService;
import com.aetherianartificer.townstead.politics.state.Faction;
import com.aetherianartificer.townstead.politics.state.FactionBonds;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import com.aetherianartificer.townstead.root.disposition.DispositionGroups;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Memories;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Whether a faction's representative accepts an accord, as a sum of visible reasons: how they feel
 * about the envoy and about the proposing faction's leader, whether the two peoples get along, and
 * how the envoy stands in their town. Above zero, they accept. The weights are data
 * ({@code data/<ns>/diplomacy/accord.json}).
 */
public final class AccordAcceptance {
    public static final String SCHEMA = "townstead:accord_acceptance/v1";

    /** One reason and what it adds; {@code key} names it for the reader. */
    public record Line(String key, int value) {}

    public record Result(List<Line> lines, int total) {
        public boolean accepted() {
            return total > 0;
        }
    }

    record Weights(int base, int envoyPerPoint, int envoyMax, int leaderPerPoint, int leaderMax,
                   int standingPerPoint, int standingMax, int clash, int sharedWelcome, int mutualAlly) {
        static final Weights DEFAULT = new Weights(-25, 2, 40, 4, 20, 1, 15, -20, 10, 5);
    }

    private static volatile Weights weights = Weights.DEFAULT;

    private AccordAcceptance() {}

    /**
     * Evaluates an offer from {@code proposer} brought by {@code envoy} to {@code representative}
     * of {@code recipient}. Either entity may be null when not loaded; the lines needing them drop.
     */
    public static Result evaluate(MinecraftServer server, UUID envoy, @Nullable LivingEntity envoyEntity,
                                  Faction proposer, Faction recipient, UUID representative,
                                  @Nullable LivingEntity representativeEntity) {
        Weights w = weights;
        PoliticalSavedData data = PoliticalSavedData.get(server);
        SettlementRef home = recipient.seatSettlement();
        List<Line> lines = new ArrayList<>();
        lines.add(new Line("base", w.base()));

        int envoyHearts = hearts(server, home, representative, representativeEntity, envoy);
        lines.add(new Line("envoy_opinion", scaled(envoyHearts, w.envoyPerPoint(), w.envoyMax())));

        UUID head = head(data, proposer);
        if (head != null && !head.equals(envoy) && !head.equals(representative)) {
            int leaderHearts = hearts(server, home, representative, representativeEntity, head);
            lines.add(new Line("leader_opinion", scaled(leaderHearts, w.leaderPerPoint(), w.leaderMax())));
        }

        if (envoyEntity != null && representativeEntity != null) {
            String theirs = DispositionGroups.of(representativeEntity);
            String ours = DispositionGroups.of(envoyEntity);
            boolean welcomed = FactionRelations.welcomes(server, recipient.id()).stream()
                    .anyMatch(welcome -> FactionDispositionSource.kin(ours, welcome));
            if (!welcomed && DispositionGroups.clash(theirs, ours)) lines.add(new Line("group_clash", w.clash()));
        }

        Set<String> shared = new HashSet<>(FactionRelations.welcomes(server, proposer.id()));
        shared.retainAll(FactionRelations.welcomes(server, recipient.id()));
        if (!shared.isEmpty()) lines.add(new Line("shared_welcome", w.sharedWelcome()));

        Set<ResourceLocation> mutual = new HashSet<>(CharterAccords.allies(data, proposer));
        mutual.retainAll(CharterAccords.allies(data, recipient));
        if (!mutual.isEmpty()) lines.add(new Line("mutual_ally", w.mutualAlly()));

        if (home != null) {
            int standing = StandingService.of(server, envoy, home).total();
            if (standing != 0) lines.add(new Line("standing", Mth.clamp(standing * w.standingPerPoint(), -w.standingMax(), w.standingMax())));
        }

        int total = 0;
        for (Line line : lines) total += line.value();
        return new Result(List.copyOf(lines), total);
    }

    /** The representative's hearts toward someone: their own memory when loaded, else the village record. */
    private static int hearts(MinecraftServer server, @Nullable SettlementRef home, UUID representative,
                              @Nullable LivingEntity representativeEntity, UUID toward) {
        if (representativeEntity instanceof VillagerEntityMCA villager) {
            Memories memories = villager.getVillagerBrain().getMemories().get(toward);
            return memories == null ? 0 : memories.getHearts();
        }
        return StandingService.residentHearts(server, toward, home, representative);
    }

    private static @Nullable UUID head(PoliticalSavedData data, Faction faction) {
        ResourceLocation office = CharterDrafts.headOffice(faction);
        if (office == null) return null;
        List<UUID> holders = FactionBonds.holders(data, faction.id(), office);
        return holders.isEmpty() ? null : holders.get(0);
    }

    private static int scaled(int value, int perPoint, int max) {
        return Mth.clamp(perPoint <= 0 ? 0 : value / perPoint, -max, max);
    }

    /** A loaded entity by UUID in any level, or null. */
    static @Nullable LivingEntity find(MinecraftServer server, UUID id) {
        for (var level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof LivingEntity living) return living;
        }
        return null;
    }

    public static final class Loader extends SimpleJsonResourceReloadListener {
        public Loader() { super(new Gson(), "diplomacy"); }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
            Weights next = Weights.DEFAULT;
            for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
                if (!entry.getKey().getPath().equals("accord")) continue;
                try {
                    JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey().toString());
                    TownsteadSchema.validateRequired(json, SCHEMA);
                    JsonObject envoy = GsonHelper.getAsJsonObject(json, "envoy_opinion", new JsonObject());
                    JsonObject leader = GsonHelper.getAsJsonObject(json, "leader_opinion", new JsonObject());
                    JsonObject standing = GsonHelper.getAsJsonObject(json, "standing", new JsonObject());
                    Weights d = Weights.DEFAULT;
                    next = new Weights(GsonHelper.getAsInt(json, "base", d.base()),
                            GsonHelper.getAsInt(envoy, "hearts_per_point", d.envoyPerPoint()), GsonHelper.getAsInt(envoy, "max", d.envoyMax()),
                            GsonHelper.getAsInt(leader, "hearts_per_point", d.leaderPerPoint()), GsonHelper.getAsInt(leader, "max", d.leaderMax()),
                            GsonHelper.getAsInt(standing, "per_point", d.standingPerPoint()), GsonHelper.getAsInt(standing, "max", d.standingMax()),
                            GsonHelper.getAsInt(json, "group_clash", d.clash()), GsonHelper.getAsInt(json, "shared_welcome", d.sharedWelcome()),
                            GsonHelper.getAsInt(json, "mutual_ally", d.mutualAlly()));
                } catch (Exception exception) {
                    Townstead.LOGGER.warn("Accord acceptance {} rejected: {}", entry.getKey(), exception.getMessage());
                }
            }
            weights = next;
        }
    }
}

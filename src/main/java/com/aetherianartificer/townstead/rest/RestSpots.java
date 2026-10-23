package com.aetherianartificer.townstead.rest;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.ModGate;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Loaded rest spots ({@code data/<ns>/rest_spot/}) and the questions asked of them. */
public final class RestSpots {
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/RestSpots");
    private static volatile List<RestSpot> loaded = List.of();

    private RestSpots() {}

    public static List<RestSpot> all() {
        return loaded;
    }

    /** The spots this block state is, highest {@code prefer} first. */
    public static List<RestSpot> at(BlockState state) {
        List<RestSpot> out = new ArrayList<>(2);
        for (RestSpot spot : loaded) if (spot.matches(state)) out.add(spot);
        if (out.size() > 1) out.sort(Comparator.comparingInt(RestSpot::prefer).reversed());
        return out;
    }

    public static boolean isHome(BlockState state) {
        for (RestSpot spot : loaded) if (spot.home() && spot.matches(state)) return true;
        return false;
    }

    public static boolean isHomeBlock(Block block) {
        for (RestSpot spot : loaded) if (spot.home() && spot.matchesBlock(block)) return true;
        return false;
    }

    /** The {@code only} spots {@code entity} is bound to: it sleeps in nothing else. Empty for most. */
    public static List<RestSpot> boundTo(LivingEntity entity) {
        List<RestSpot> out = null;
        for (RestSpot spot : loaded) {
            if (!spot.only() || !spot.admits(entity)) continue;
            if (out == null) out = new ArrayList<>(1);
            out.add(spot);
        }
        return out == null ? List.of() : out;
    }

    /**
     * Whether {@code entity} may sleep in or claim this block: one of its bound spots if it has any,
     * else any spot here that admits it. A block no rest spot names is left to its own rules.
     */
    public static boolean permits(LivingEntity entity, BlockState state) {
        List<RestSpot> bound = boundTo(entity);
        if (!bound.isEmpty()) {
            for (RestSpot spot : bound) if (spot.matches(state)) return true;
            return false;
        }
        List<RestSpot> here = at(state);
        if (here.isEmpty()) return true;
        for (RestSpot spot : here) if (spot.admits(entity)) return true;
        return false;
    }

    /** A {@code pheno:rest} gene preference outranks any spot's own {@code prefer}. */
    private static final int GENE_RANK = 1000;

    /**
     * How much {@code entity} wants this block as a home: the best {@code prefer} among the spots
     * here that admit it, raised by a gene preference. A block no spot names ranks 0, as a bed does.
     */
    public static int rank(LivingEntity entity, BlockState state) {
        List<RestSpot> here = at(state);
        if (here.isEmpty()) return 0;
        return rank(entity, here, RestGenes.prefers(entity));
    }

    private static int rank(LivingEntity entity, List<RestSpot> spots, List<ResourceLocation> genes) {
        int best = Integer.MIN_VALUE;
        for (RestSpot spot : spots) {
            if (!spot.admits(entity)) continue;
            best = Math.max(best, spot.prefer() + (genes.contains(spot.id()) ? GENE_RANK : 0));
        }
        return best == Integer.MIN_VALUE ? 0 : best;
    }

    /** The highest rank any home could have for {@code entity}; a home below it may be passed over. */
    public static int bestRank(LivingEntity entity) {
        List<RestSpot> homes = new ArrayList<>();
        for (RestSpot spot : loaded) if (spot.home()) homes.add(spot);
        return Math.max(0, rank(entity, homes, RestGenes.prefers(entity)));
    }

    /** The spot {@code entity} uses at this block, or null when none admits it. */
    public static @Nullable RestSpot used(LivingEntity entity, BlockState state) {
        for (RestSpot spot : at(state)) if (spot.admits(entity)) return spot;
        return null;
    }

    public static final class Loader extends SimpleJsonResourceReloadListener {
        public Loader() { super(new Gson(), "rest_spot"); }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
            List<RestSpot> parsed = new ArrayList<>();
            for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
                try {
                    JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey().toString());
                    if (json.has("mods") && ModGate.evaluate(json.get("mods")) == null) {
                        throw new IllegalArgumentException("'mods' is malformed");
                    }
                    if (!ModGate.allows(json)) continue;
                    parsed.add(RestSpot.parse(entry.getKey(), json));
                } catch (Exception exception) {
                    LOGGER.warn("Rest spot {} rejected: {}", entry.getKey(), exception.getMessage());
                }
            }
            loaded = List.copyOf(parsed);
            HomePoints.markDirty();
            LOGGER.info("Loaded {} rest spots", parsed.size());
        }
    }
}

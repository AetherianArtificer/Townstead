package com.aetherianartificer.townstead.profession.def;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * One way a profession's job becomes available in the world, declared in the def's {@code poi}
 * list. Providers are data: the engine derives slot policy and (in later passes) assignment and
 * job-change surfaces from them, instead of hardcoding which profession needs which site.
 *
 * <p>Built-in types: {@code townstead:job_block} (a vanilla-style job-site block, e.g. the
 * composter enabling Farmer), {@code townstead:building} (a Townstead/MCA building type by id
 * prefix, e.g. kitchens enabling Cook), and {@code townstead:always} (no site required, e.g.
 * martial roles). New types register through {@link JobSiteProviders}.</p>
 */
public interface JobSiteProvider {

    String typeKey();

    /**
     * A vanilla-style job-site block. The def's {@code poi} list is an ordered acquisition
     * hierarchy: the first entry is the primary surface, and a subordinate job-block entry may
     * name {@code via} — the (alias) profession whose vanilla POI claim manifests this surface
     * (e.g. a compatibility forge manifests through another mod's smith). Claiming such a POI
     * acquires the canonical career, gated by the village's total capacity. Several matching
     * sites may form one worker's workload through {@code sitesPerWorker}; the default of one
     * preserves vanilla's one-workstation, one-worker rule.
     */
    record JobBlock(Set<ResourceLocation> blocks, @Nullable ResourceLocation via,
                    int sitesPerWorker) implements JobSiteProvider {
        public JobBlock {
            blocks = Set.copyOf(blocks);
            sitesPerWorker = Math.max(1, sitesPerWorker);
        }
        public JobBlock(Set<ResourceLocation> blocks, @Nullable ResourceLocation via) {
            this(blocks, via, 1);
        }
        public JobBlock(Set<ResourceLocation> blocks) { this(blocks, null, 1); }
        public static final String KEY = "townstead:job_block";
        @Override public String typeKey() { return KEY; }

        /** One through {@code sitesPerWorker} sites seat one worker; the next starts another. */
        public int slotsForSites(int siteCount) {
            return siteCount <= 0 ? 0 : (siteCount + sitesPerWorker - 1) / sitesPerWorker;
        }
    }

    /**
     * A building whose type id starts with one of the given prefixes.
     *
     * <p>{@code slotsPerTier} seats more workers in a better building: the entry after the
     * matched prefix is read as the tier ({@code compat/…/kitchen_l} + {@code 3}), and the list
     * gives one count per tier. Empty means one worker per building, which is what an untiered
     * workplace wants.</p>
     *
     * <p>{@code pathAffinities} relates this building family to specialization Paths without
     * making either exclusive: working here steers an unspecialized villager toward those Paths,
     * while villagers already on one of them prefer this family's available seats. It does not
     * replace a Path's station-level worksite preferences.</p>
     *
     * <p>{@code proprietor} reserves the first declared number of seats in every matching
     * building for listed raw villager-profession ids. Those ids may be semantic Career aliases;
     * keeping the raw id is what preserves a provider mod's trades, clothing, and identity.</p>
     */
    record Building(List<String> typePrefixes, List<Integer> slotsPerTier,
                    List<String> pathAffinities, Proprietor proprietor) implements JobSiteProvider {
        public Building {
            typePrefixes = List.copyOf(typePrefixes);
            slotsPerTier = List.copyOf(slotsPerTier);
            pathAffinities = List.copyOf(pathAffinities);
            proprietor = proprietor == null ? Proprietor.NONE : proprietor;
        }

        public Building(List<String> typePrefixes, List<Integer> slotsPerTier,
                        List<String> pathAffinities) {
            this(typePrefixes, slotsPerTier, pathAffinities, Proprietor.NONE);
        }

        public Building(List<String> typePrefixes, List<Integer> slotsPerTier) {
            this(typePrefixes, slotsPerTier, List.of());
        }

        public Building(List<String> typePrefixes) {
            this(typePrefixes, List.of(), List.of(), Proprietor.NONE);
        }

        public static final String KEY = "townstead:building";
        @Override public String typeKey() { return KEY; }

        /** Whether this building type is one this entry speaks for. */
        public boolean matches(@Nullable String buildingTypeId) {
            if (buildingTypeId == null) return false;
            for (String prefix : typePrefixes) {
                if (matchesPrefix(buildingTypeId, prefix)) return true;
            }
            return false;
        }

        /** Whether this building family and the named local Path favour one another. */
        public boolean hasPathAffinity(@Nullable String pathId) {
            return pathId != null && pathAffinities.contains(pathId);
        }

        /** Professions permitted to occupy the given zero-based seat; empty means ordinary staff. */
        public Set<ResourceLocation> requiredProfessionsForSeat(
                @Nullable String buildingTypeId, int seatIndex) {
            int seats = slotsFor(buildingTypeId);
            if (seatIndex < 0 || seatIndex >= seats) return Set.of();
            return seatIndex < Math.min(seats, proprietor.slots())
                    ? proprietor.professions() : Set.of();
        }

        /** How many workers this building seats; 0 when it is not one of ours. */
        public int slotsFor(@Nullable String buildingTypeId) {
            if (buildingTypeId == null) return 0;
            for (String prefix : typePrefixes) {
                String runtimePrefix = runtimePrefix(prefix);
                if (!buildingTypeId.startsWith(runtimePrefix)) continue;
                if (slotsPerTier.isEmpty()) return 1;
                int tier = tierOf(buildingTypeId, runtimePrefix);
                // An unnumbered or out-of-range building is still a workplace, just an untiered
                // one: seating nobody would retire a whole building over a naming slip.
                return tier >= 1 && tier <= slotsPerTier.size() ? slotsPerTier.get(tier - 1) : 1;
            }
            return 0;
        }

        /**
         * The tier this building type declares, read as whatever follows the matched prefix
         * ({@code …/kitchen_l} + {@code 3}). Zero when it is not ours or carries no number,
         * which is the right answer for an untiered workplace.
         */
        public int tierOf(@Nullable String buildingTypeId) {
            if (buildingTypeId == null) return 0;
            for (String prefix : typePrefixes) {
                String runtimePrefix = runtimePrefix(prefix);
                if (buildingTypeId.startsWith(runtimePrefix)) {
                    return tierOf(buildingTypeId, runtimePrefix);
                }
            }
            return 0;
        }

        /** MCA flattens a namespaced building resource to {@code namespace/path} at runtime. */
        private static boolean matchesPrefix(String buildingTypeId, String authoredPrefix) {
            return buildingTypeId.startsWith(runtimePrefix(authoredPrefix));
        }

        private static String runtimePrefix(String authoredPrefix) {
            if (authoredPrefix == null) return "";
            int namespace = authoredPrefix.indexOf(':');
            return namespace < 0 ? authoredPrefix
                    : authoredPrefix.substring(0, namespace) + "/"
                    + authoredPrefix.substring(namespace + 1);
        }

        private static int tierOf(String buildingTypeId, String prefix) {
            try {
                return Integer.parseInt(buildingTypeId.substring(prefix.length()));
            } catch (NumberFormatException | IndexOutOfBoundsException ignored) {
                return 0;
            }
        }

        /**
         * Reserved ownership seats for raw/native villager professions. These ids are deliberately
         * not canonicalized: retaining the foreign profession is what retains its trades, clothes,
         * and POI behavior while Townstead interprets it as a Career or Path.
         */
        public record Proprietor(Set<ResourceLocation> professions, int slots) {
            public static final Proprietor NONE = new Proprietor(Set.of(), 0);

            public Proprietor {
                professions = Set.copyOf(professions);
                slots = professions.isEmpty() ? 0 : Math.max(0, slots);
            }
        }
    }

    /**
     * A declared workstation block standing on its own, outside every building — a pot in a
     * courtyard. Distinct from {@link JobBlock}, which borrows another mod's vanilla POI claim
     * and so only exists where that mod does.
     *
     * <p>A station INSIDE a building is never one of these: a building's own entry already
     * priced the room, and letting stations stack on top of it would make the tier ladder
     * bypassable by placing pots. A station inside someone's house is not a workplace at all.</p>
     */
    record StationPost(Set<ResourceLocation> blocks, List<ResourceLocation> blockTags, int slots)
            implements JobSiteProvider {
        public StationPost {
            blocks = Set.copyOf(blocks);
            blockTags = List.copyOf(blockTags);
        }
        public static final String KEY = "townstead:station_post";
        @Override public String typeKey() { return KEY; }
    }

    /** No physical site required. */
    record Always() implements JobSiteProvider {
        public static final String KEY = "townstead:always";
        @Override public String typeKey() { return KEY; }
    }
}

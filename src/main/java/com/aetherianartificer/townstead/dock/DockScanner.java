package com.aetherianartificer.townstead.dock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects a dock/wharf structure around a given position. Results cached per
 * scan origin with a short TTL. Callers include the fisherman AI (scans from
 * its barrel) and the player's building-report flow (scans from the player's
 * feet) — both just hand in a position; the dock itself doesn't require any
 * particular block at the origin.
 *
 * Tier ladder (each tier fully includes the prior — pure structure, no
 * fisherman-specific requirements):
 *
 *   Tier 1 — Landing
 *     - 5+ horizontally connected crafted deck blocks at the same Y-level
 *       (wooden surfaces, stone/brick surfaces, or similar built dock flooring)
 *     - at least 1 deck block beside water or over/near water within a short drop
 *     - Perimeter deck blocks cannot be almost fully walled; a solid block above
 *       most perimeter blocks reads as a house wall, not a landing.
 *     - 30%+ of deck blocks have open sky (no solid roof within 6 above), so
 *       small roofed shoreline landings still count.
 *
 *   Tier 2 — Pier
 *     - 24+ deck blocks
 *     - 3+ light sources inside the bounds (lanterns or torches)
 *     - 6+ railing blocks inside the bounds ({@code #minecraft:fences} or
 *       {@code #minecraft:walls})
 *
 *   Tier 3 — Wharf
 *     - 48+ deck blocks
 *     - 6+ light sources
 *     - 12+ railing blocks
 *
 * Tier thresholds mirror the dock_l2 / dock_l3 JSON block recipes exactly so
 * the Add / Refresh / Upgrade paths (which consult DockScanner and the JSON
 * separately) agree on the tier a given structure qualifies for.
 *
 * Bounds used for scanning are the plank component's axis-aligned box expanded
 * by 1 block horizontally and 2 blocks vertically, so decorations sitting just
 * next to or above the deck count.
 */
public final class DockScanner {
    private static final Logger LOG = LoggerFactory.getLogger("Townstead/DockScanner");
    //? if >=1.21 {
    private static final TagKey<Block> DOCK_SURFACES =
            TagKey.create(Registries.BLOCK, ResourceLocation.parse("townstead:dock_surfaces"));
    //?} else {
    /*private static final TagKey<Block> DOCK_SURFACES =
            TagKey.create(Registries.BLOCK, new ResourceLocation("townstead", "dock_surfaces"));
    *///?}

    private static final Set<String> WOOD_FAMILIES = Set.of(
            "oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
            "mangrove", "cherry", "bamboo", "crimson", "warped");
    private static final Set<String> MASONRY_SURFACES = Set.of(
            "stone", "cobblestone", "mossy_cobblestone", "smooth_stone",
            "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks",
            "bricks", "mud_bricks",
            "andesite", "polished_andesite", "diorite", "polished_diorite",
            "granite", "polished_granite",
            "sandstone", "smooth_sandstone", "cut_sandstone", "chiseled_sandstone",
            "red_sandstone", "smooth_red_sandstone", "cut_red_sandstone", "chiseled_red_sandstone",
            "deepslate", "cobbled_deepslate", "polished_deepslate",
            "deepslate_bricks", "cracked_deepslate_bricks",
            "deepslate_tiles", "cracked_deepslate_tiles",
            "blackstone", "polished_blackstone", "polished_blackstone_bricks", "cracked_polished_blackstone_bricks",
            "prismarine", "prismarine_bricks", "dark_prismarine",
            "quartz_block", "smooth_quartz", "quartz_bricks",
            "nether_bricks", "cracked_nether_bricks", "chiseled_nether_bricks",
            "red_nether_bricks", "end_stone_bricks");

    private static final int HORIZONTAL_RADIUS = 12;
    private static final int VERTICAL_RADIUS = 4;

    private static final int T1_MIN_PLANKS = 5;
    private static final int T1_MIN_WATER_TOUCH = 1;
    // Minimum fraction of deck planks that must have open sky overhead.
    // 0.3 keeps fully enclosed rooms out while allowing small roofed landings
    // and shoreline decks that players naturally read as docks.
    private static final float T1_MIN_OPEN_SKY_RATIO = 0.3f;
    private static final int ROOF_SCAN_HEIGHT = 6;
    private static final int WATER_SCAN_DEPTH = 6;
    private static final int REPORT_MAX_COMPONENT_DISTANCE = 1;
    // Landing recognition should be player-friendly: a small shoreline deck
    // beside water is enough. Higher tiers still reward larger, dressed docks.
    private static final int T1_MIN_PLANKS_OVER_WATER = 0;
    // The decisive house-vs-dock filter: on a house the perimeter planks have
    // walls directly above (the outside walls sitting on the floor). A dock
    // has at most a few fence/wall railings, which aren't counted as walls.
    // Reject if more than this fraction of perimeter planks have a wall above.
    private static final float T1_MAX_WALLED_PERIMETER_RATIO = 0.70f;

    // Tier thresholds — deliberately mirror the dock_l2 / dock_l3 JSON recipes
    // block-for-block. Keeping DockScanner and the JSON requirements in lockstep
    // means Add Building, Refresh, and Upgrade Building all reach the same
    // conclusion about which tier the dock qualifies for.
    private static final int T2_MIN_PLANKS = 24;
    private static final int T2_MIN_LIGHTS = 3;
    private static final int T2_MIN_RAILING = 6;

    private static final int T3_MIN_PLANKS = 48;
    private static final int T3_MIN_LIGHTS = 6;
    private static final int T3_MIN_RAILING = 12;

    private static final long CACHE_TTL_TICKS = 200L;
    private static final long EMPTY_CACHE_TTL_TICKS = 400L;

    private static final Map<Key, Entry> CACHE = new ConcurrentHashMap<>();

    private DockScanner() {}

    public static @Nullable Dock scan(ServerLevel level, BlockPos near) {
        return scan(level, near, HORIZONTAL_RADIUS);
    }

    /**
     * Scan variant with an override for how far out from {@code near} to
     * search. The default radius ({@value #HORIZONTAL_RADIUS}) is tuned for
     * fisherman-barrel scans, which sit close to the dock by construction.
     * Player-triggered scans (report-building, refresh) can fire from anywhere
     * on a large deck, so those callers pass a larger radius to guarantee
     * the full plank component gets captured — otherwise scanning from an
     * edge would yield a partial dock and incorrectly downgrade the tier.
     */
    public static @Nullable Dock scan(ServerLevel level, BlockPos near, int horizontalRadius) {
        if (near == null) return null;
        // Cache key includes the radius so mixed callers don't step on each
        // other's TTL windows.
        Key key = new Key(level.dimension().location().toString(), near.asLong(), horizontalRadius);
        long now = level.getGameTime();
        Entry cached = CACHE.get(key);
        if (cached != null && now <= cached.expiresAt) {
            return cached.dock;
        }
        Dock dock = scanUncached(level, near, horizontalRadius, false);
        long ttl = dock == null ? EMPTY_CACHE_TTL_TICKS : CACHE_TTL_TICKS;
        CACHE.put(key, new Entry(dock, now + ttl));
        return dock;
    }

    public static @Nullable Dock scanForReport(ServerLevel level, BlockPos near, int horizontalRadius) {
        if (near == null) return null;
        return scanUncached(level, near, horizontalRadius, true);
    }

    public static void invalidate(ServerLevel level, BlockPos near) {
        if (near == null) return;
        String dim = level.dimension().location().toString();
        long posKey = near.asLong();
        CACHE.keySet().removeIf(k -> k.dim.equals(dim) && k.posKey == posKey);
    }

    private static @Nullable Dock scanUncached(ServerLevel level, BlockPos near, int horizontalRadius, boolean diagnostics) {
        Set<Long> planks = new HashSet<>();
        for (BlockPos p : BlockPos.betweenClosed(
                near.offset(-horizontalRadius, -VERTICAL_RADIUS, -horizontalRadius),
                near.offset(horizontalRadius, VERTICAL_RADIUS, horizontalRadius))) {
            BlockState s = level.getBlockState(p);
            if (isDockSurface(s)) {
                planks.add(p.asLong());
            }
        }
        if (planks.size() < T1_MIN_PLANKS) {
            if (diagnostics) logReject("not enough dock surfaces in scan box", near, horizontalRadius, planks.size(), 0, 0, 0, 0.0f, 0.0f, null);
            return null;
        }

        List<Set<Long>> components = horizontalComponents(planks);
        components.removeIf(c -> c.size() < T1_MIN_PLANKS);
        if (diagnostics) {
            int maxDistanceSqr = REPORT_MAX_COMPONENT_DISTANCE * REPORT_MAX_COMPONENT_DISTANCE;
            components.removeIf(c -> distanceToBoundsSqr(near, computeBounds(c)) > maxDistanceSqr);
        }
        components.sort(Comparator
                .comparingDouble((Set<Long> c) -> distanceToBoundsSqr(near, computeBounds(c)))
                .thenComparing(Comparator.comparingInt(Set<Long>::size).reversed()));
        if (components.isEmpty()) {
            if (diagnostics) logReject("no nearby connected dock surface large enough", near, horizontalRadius, planks.size(), 0, 0, 0, 0.0f, 0.0f, null);
            return null;
        }

        Set<Long> best = null;
        BoundingBox bounds = null;
        WaterCounts water = null;
        float walledRatio = 0.0f;
        float openSkyRatio = 0.0f;
        String closestReject = "no qualifying component";
        BoundingBox closestBounds = null;
        int closestSize = 0;
        int closestBeside = 0;
        int closestOver = 0;
        float closestWalled = 0.0f;
        float closestOpenSky = 0.0f;

        for (Set<Long> component : components) {
            BoundingBox componentBounds = computeBounds(component);
            WaterCounts componentWater = countWaterContacts(level, component);
            float componentWalled = walledPerimeterRatio(level, component);
            float componentOpenSky = openSkyRatio(level, component);
            if (closestBounds == null) {
                closestBounds = componentBounds;
                closestSize = component.size();
                closestBeside = componentWater.beside();
                closestOver = componentWater.over();
                closestWalled = componentWalled;
                closestOpenSky = componentOpenSky;
            }
            if (!meetsWaterContact(componentWater, T1_MIN_WATER_TOUCH, T1_MIN_PLANKS_OVER_WATER)) {
                if (closestBounds == componentBounds) closestReject = "no qualifying water contact";
                continue;
            }
            if (componentWalled > T1_MAX_WALLED_PERIMETER_RATIO) {
                if (closestBounds == componentBounds) closestReject = "too much walled perimeter";
                continue;
            }
            if (componentOpenSky < T1_MIN_OPEN_SKY_RATIO) {
                if (closestBounds == componentBounds) closestReject = "not enough open sky";
                continue;
            }
            best = component;
            bounds = componentBounds;
            water = componentWater;
            walledRatio = componentWalled;
            openSkyRatio = componentOpenSky;
            break;
        }

        if (best == null) {
            if (diagnostics) {
                logReject(closestReject, near, horizontalRadius, planks.size(), closestSize,
                        closestBeside, closestOver, closestWalled, closestOpenSky, closestBounds);
            }
            return null;
        }

        int tier = 1;
        if (best.size() >= T2_MIN_PLANKS && pierQualifies(level, best, bounds)) {
            tier = 2;
            if (best.size() >= T3_MIN_PLANKS && wharfQualifies(level, best, bounds)) {
                tier = 3;
            }
        }

        if (diagnostics) {
            LOG.info("[DockScan] detected tier={} near={} radius={} surfaces={} component={} waterBeside={} waterOver={} walledRatio={} openSkyRatio={} bounds=[{},{},{}]..[{},{},{}]",
                    tier, near, horizontalRadius, planks.size(), best.size(), water.beside(), water.over(), walledRatio, openSkyRatio,
                    bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ());
        }
        return new Dock(bounds, best.size(), tier);
    }

    private static void logReject(String reason, BlockPos near, int radius,
                                  int surfaces, int component, int waterBeside, int waterOver,
                                  float walledRatio, float openSkyRatio, @Nullable BoundingBox bounds) {
        if (bounds == null) {
            LOG.info("[DockScan] reject near={} radius={} reason='{}' surfaces={} component={} waterBeside={} waterOver={} walledRatio={} openSkyRatio={} bounds=<none>",
                    near, radius, reason, surfaces, component, waterBeside, waterOver, walledRatio, openSkyRatio);
        } else {
            LOG.info("[DockScan] reject near={} radius={} reason='{}' surfaces={} component={} waterBeside={} waterOver={} walledRatio={} openSkyRatio={} bounds=[{},{},{}]..[{},{},{}]",
                    near, radius, reason, surfaces, component, waterBeside, waterOver, walledRatio, openSkyRatio,
                    bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ());
        }
    }

    // ── Structural scan ──

    private static Set<Long> largestHorizontalComponent(Set<Long> planks) {
        List<Set<Long>> components = horizontalComponents(planks);
        Set<Long> best = Collections.emptySet();
        for (Set<Long> component : components) {
            if (component.size() > best.size()) best = component;
        }
        return best;
    }

    private static List<Set<Long>> horizontalComponents(Set<Long> planks) {
        Set<Long> visited = new HashSet<>();
        List<Set<Long>> components = new ArrayList<>();
        for (long seedKey : planks) {
            if (visited.contains(seedKey)) continue;
            Set<Long> component = new HashSet<>();
            Deque<BlockPos> queue = new ArrayDeque<>();
            BlockPos seed = BlockPos.of(seedKey);
            queue.add(seed);
            component.add(seedKey);
            visited.add(seedKey);
            while (!queue.isEmpty()) {
                BlockPos cur = queue.poll();
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos n = cur.relative(d);
                    long nkey = n.asLong();
                    if (!planks.contains(nkey) || component.contains(nkey)) continue;
                    component.add(nkey);
                    visited.add(nkey);
                    queue.add(n);
                }
            }
            components.add(component);
        }
        return components;
    }

    private static double distanceToBoundsSqr(BlockPos pos, BoundingBox bb) {
        double dx = axisDistance(pos.getX(), bb.minX(), bb.maxX());
        double dy = axisDistance(pos.getY(), bb.minY(), bb.maxY());
        double dz = axisDistance(pos.getZ(), bb.minZ(), bb.maxZ());
        return dx * dx + dy * dy + dz * dz;
    }

    private static double axisDistance(int value, int min, int max) {
        if (value < min) return min - value;
        if (value > max) return value - max;
        return 0.0;
    }

    /**
     * Roof rejection. For each plank, look up to {@link #ROOF_SCAN_HEIGHT}
     * blocks above for anything that reads as a ceiling (planks, logs, stone,
     * slabs, stairs — basically any block with collision that isn't explicit
     * pier furniture). Fences, walls, lanterns, torches and leaves don't
     * count as roof, so a post with a hanging lantern over a plank is fine.
     * A dock qualifies when at least {@code minRatio} of its planks have
     * clear sky by this definition.
     */
    private static boolean meetsOpenSkyRatio(ServerLevel level, Set<Long> deck, float minRatio) {
        return openSkyRatio(level, deck) >= minRatio;
    }

    private static float openSkyRatio(ServerLevel level, Set<Long> deck) {
        if (deck.isEmpty()) return 0.0f;
        int openCount = 0;
        for (long k : deck) {
            BlockPos p = BlockPos.of(k);
            boolean roofed = false;
            for (int i = 1; i <= ROOF_SCAN_HEIGHT; i++) {
                BlockPos above = p.above(i);
                if (isRoofBlock(level.getBlockState(above), level, above)) {
                    roofed = true;
                    break;
                }
            }
            if (!roofed) openCount++;
        }
        return (float) openCount / deck.size();
    }

    private static boolean isRoofBlock(BlockState state, ServerLevel level, BlockPos pos) {
        if (state.isAir()) return false;
        if (state.getFluidState().isSource()) return false;
        if (state.is(BlockTags.LEAVES)) return false;
        if (state.is(BlockTags.FENCES)) return false;
        if (state.is(BlockTags.WALLS)) return false;
        if (state.is(Blocks.LANTERN) || state.is(Blocks.SOUL_LANTERN)) return false;
        if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
                || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH)) return false;
        // Anything else with a non-empty collision shape is treated as a
        // ceiling — covers full blocks, slabs, stairs, half-blocks, etc.
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    /**
     * The decisive house-vs-dock test. A perimeter plank is one with at least
     * one horizontal neighbor that isn't itself in the plank component — the
     * outer ring of the deck footprint. On a house, each perimeter plank has
     * an outside wall sitting directly on it ({@link #isRoofBlock} returns
     * true for solid structural blocks, excluding fence/wall railings and
     * pier furniture). On a dock, perimeter planks have open air above, or
     * at most the occasional fence post / lantern — none of which count.
     *
     * Accept the component as a dock only when the fraction of perimeter
     * planks with a wall block directly above stays below {@code maxRatio}.
     */
    private static boolean hasLowWalledPerimeter(ServerLevel level, Set<Long> deck, float maxRatio) {
        return walledPerimeterRatio(level, deck) <= maxRatio;
    }

    private static float walledPerimeterRatio(ServerLevel level, Set<Long> deck) {
        int perimeterCount = 0;
        int walledCount = 0;
        for (long k : deck) {
            BlockPos p = BlockPos.of(k);
            boolean onPerimeter = false;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (!deck.contains(p.relative(d).asLong())) {
                    onPerimeter = true;
                    break;
                }
            }
            if (!onPerimeter) continue;
            perimeterCount++;
            BlockPos above = p.above();
            if (isRoofBlock(level.getBlockState(above), level, above)) {
                walledCount++;
            }
        }
        if (perimeterCount == 0) return 0.0f;
        return (float) walledCount / perimeterCount;
    }

    private static boolean meetsWaterContact(ServerLevel level, Set<Long> deck, int requiredBeside, int requiredOver) {
        return meetsWaterContact(countWaterContacts(level, deck), requiredBeside, requiredOver);
    }

    private static boolean meetsWaterContact(WaterCounts counts, int requiredBeside, int requiredOver) {
        return (requiredBeside > 0 && counts.beside() >= requiredBeside)
                || (requiredOver > 0 && counts.over() >= requiredOver);
    }

    private static WaterCounts countWaterContacts(ServerLevel level, Set<Long> deck) {
        int beside = 0;
        int over = 0;
        for (long k : deck) {
            BlockPos p = BlockPos.of(k);
            WaterContact contact = waterContactBelowOrBeside(level, p);
            if (contact.over) over++;
            if (contact.beside) beside++;
        }
        return new WaterCounts(beside, over);
    }

    private static WaterContact waterContactBelowOrBeside(ServerLevel level, BlockPos deckPos) {
        boolean over = false;
        boolean beside = false;
        for (int dy = 0; dy <= WATER_SCAN_DEPTH; dy++) {
            BlockPos base = deckPos.below(dy);
            if (dy > 0 && isWaterSource(level, base)) {
                over = true;
            }
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (isWaterSource(level, base.relative(d))) {
                    beside = true;
                    break;
                }
            }
            if (over || beside) return new WaterContact(over, beside);
        }
        return new WaterContact(false, false);
    }

    private static boolean isWaterSource(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().isSource() && state.getFluidState().is(FluidTags.WATER);
    }

    public static boolean isDockSurface(BlockState state) {
        if (state.is(DOCK_SURFACES)) return true;
        if (state.is(BlockTags.PLANKS)) return true;
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (key == null || !"minecraft".equals(key.getNamespace())) return false;
        String path = key.getPath();
        for (String family : WOOD_FAMILIES) {
            if (path.equals(family + "_slab")
                    || path.equals(family + "_stairs")
                    || path.equals(family + "_trapdoor")) {
                return true;
            }
        }
        if (MASONRY_SURFACES.contains(path)) return true;
        for (String material : MASONRY_SURFACES) {
            if (path.equals(material + "_slab")
                    || path.equals(material + "_stairs")) {
                return true;
            }
        }
        return false;
    }

    private static BoundingBox computeBounds(Set<Long> deck) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long k : deck) {
            BlockPos p = BlockPos.of(k);
            if (p.getX() < minX) minX = p.getX();
            if (p.getY() < minY) minY = p.getY();
            if (p.getZ() < minZ) minZ = p.getZ();
            if (p.getX() > maxX) maxX = p.getX();
            if (p.getY() > maxY) maxY = p.getY();
            if (p.getZ() > maxZ) maxZ = p.getZ();
        }
        return new BoundingBox(
                minX - 1, minY - 1, minZ - 1,
                maxX + 1, maxY + 2, maxZ + 1);
    }

    // ── Pier / Wharf evaluators ──

    private static boolean pierQualifies(ServerLevel level, Set<Long> deck, BoundingBox bounds) {
        return countLights(level, bounds) >= T2_MIN_LIGHTS
                && countRailings(level, bounds) >= T2_MIN_RAILING;
    }

    private static boolean wharfQualifies(ServerLevel level, Set<Long> deck, BoundingBox bounds) {
        return countLights(level, bounds) >= T3_MIN_LIGHTS
                && countRailings(level, bounds) >= T3_MIN_RAILING;
    }

    private static int countLights(ServerLevel level, BoundingBox bb) {
        int count = 0;
        for (BlockPos p : boundsPositions(bb)) {
            if (isLight(level.getBlockState(p))) count++;
        }
        return count;
    }

    /**
     * What counts as a dock light. Campfires were excluded — a firepit on a
     * wooden pier reads as a fire hazard rather than wayfinding. Lanterns and
     * torches fit the maritime vocabulary: posts with lanterns, wall torches
     * hung off railings.
     */
    private static boolean isLight(BlockState s) {
        return s.is(Blocks.LANTERN) || s.is(Blocks.SOUL_LANTERN)
                || s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH)
                || s.is(Blocks.SOUL_TORCH) || s.is(Blocks.SOUL_WALL_TORCH);
    }

    private static int countRailings(ServerLevel level, BoundingBox bb) {
        int count = 0;
        for (BlockPos p : boundsPositions(bb)) {
            BlockState s = level.getBlockState(p);
            if (s.is(BlockTags.FENCES) || s.is(BlockTags.WALLS)) count++;
        }
        return count;
    }

    private static Iterable<BlockPos> boundsPositions(BoundingBox bb) {
        return BlockPos.betweenClosed(
                new BlockPos(bb.minX(), bb.minY(), bb.minZ()),
                new BlockPos(bb.maxX(), bb.maxY(), bb.maxZ()));
    }

    private record Key(String dim, long posKey, int radius) {}
    private record Entry(@Nullable Dock dock, long expiresAt) {}
    private record WaterContact(boolean over, boolean beside) {}
    private record WaterCounts(int beside, int over) {}
}

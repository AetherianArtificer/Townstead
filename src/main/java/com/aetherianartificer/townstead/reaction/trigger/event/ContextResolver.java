package com.aetherianartificer.townstead.reaction.trigger.event;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import com.aetherianartificer.townstead.fatigue.FatigueData;
import com.aetherianartificer.townstead.hunger.HungerData;
import com.aetherianartificer.townstead.reaction.ReactionLockTracker;
import com.aetherianartificer.townstead.reaction.ReactionRegistry;
import com.aetherianartificer.townstead.shift.ShiftData;
import com.aetherianartificer.townstead.thirst.ThirstData;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Memories;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.EntityRelationship;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.GraveyardManager;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Computes the set of context tags applicable to a villager at the
 * current moment. Tags drive {@code context_enter} triggers and
 * {@code required_tags} gating on bindings.
 *
 * <p>Tag catalogue (see {@code docs/REACTIONS.md} for pack-author copy):</p>
 *
 * <p><b>Time:</b> {@code day}, {@code night}, {@code dawn}, {@code dusk},
 * {@code hour:0..23} (display hours, 6 = sunrise / 12 = noon / 18 = sunset /
 * 0 = midnight), named periods {@code early_morning} (5-7),
 * {@code morning} (8-11), {@code noon} (12-13), {@code afternoon} (14-16),
 * {@code evening} (17-20), {@code late_night} (21-4), and {@code day:N}
 * for the 1-based game-day counter.</p>
 *
 * <p><b>Shift (Townstead, per villager):</b> {@code on_shift:idle},
 * {@code on_shift:work}, {@code on_shift:meet}, {@code on_shift:rest}
 * based on the villager's own schedule for the current hour;
 * {@code shift_custom} when the player has overridden the default
 * shift table.</p>
 *
 * <p><b>Activity proximity:</b> {@code near_working_villager},
 * {@code near_resting_villager}, {@code near_meeting_villager} — at
 * least one other MCA villager within ~12 blocks is currently scheduled
 * for that activity.</p>
 *
 * <p><b>Reaction proximity:</b> {@code near_reacting:<id>} and
 * {@code near_reacting_tag:<tag>} for each reaction currently locking
 * a nearby villager. Tags come from the playing reaction's own
 * {@code tags} list, so a dance reaction tagged {@code ["dancing"]}
 * surfaces as {@code near_reacting_tag:dancing} for everyone in range.</p>
 *
 * <p><b>Crowd and age:</b> {@code alone}, {@code in_crowd} (≥3 villagers),
 * {@code near_baby_villager} (BABY or TODDLER).</p>
 *
 * <p><b>MCA relationships:</b> {@code is_married}, {@code is_engaged},
 * {@code is_promised}, {@code near_spouse}, {@code near_parent},
 * {@code near_family}.</p>
 *
 * <p><b>Player relationships:</b> {@code near_player_friend},
 * {@code near_player_stranger}, {@code near_player_disliked},
 * {@code near_player_spouse}, {@code being_watched_by_player}.</p>
 *
 * <p><b>Player-held items:</b> {@code player_holding:<item-path>} for
 * each nearby player's main-hand item, plus {@code player_holding_tag:<tag>}
 * for each item-tag the stack belongs to (uses vanilla item tags so
 * pack authors can define their own categories without Java).</p>
 *
 * <p><b>Threats:</b> {@code near_zombie_villager}, {@code near_mob_threat},
 * {@code outsider_present} (illagers specifically), {@code raid_active}.</p>
 *
 * <p><b>Profession / state:</b> {@code hungry} / {@code peckish},
 * {@code thirsty} / {@code parched}, {@code tired} / {@code drowsy} /
 * {@code exhausted}, {@code unemployed}, {@code at_workstation},
 * {@code pregnant}. Thresholds align with each Townstead system's own
 * conventions (FatigueData, HungerData, ThirstData).</p>
 *
 * <p><b>Events:</b> {@code near_grave} — MCA tombstone within 16
 * blocks. Wedding / funeral / birthday tags are deferred until those
 * events have server-side state to query.</p>
 *
 * <p><b>Interaction state:</b> {@code in_dialogue_with_player} while
 * the RPG dialogue screen is open on the villager;
 * {@code dialogue_just_ended} for ~3 seconds after close.</p>
 *
 * <p><b>Environmental:</b> {@code raining}, {@code thundering},
 * {@code under_open_sky}, {@code under_roof}, {@code in_dark},
 * {@code near_water}, {@code near_lava}.</p>
 *
 * <p><b>Biome / dimension:</b> {@code freezing}, {@code cold},
 * {@code temperate}, {@code hot}, {@code biome:<namespace>:<path>},
 * {@code dimension:<namespace>:<path>}.</p>
 *
 * <p><b>Location:</b> {@code in_building:<type>}. The slug after the
 * colon is the building type minus its namespace, so a vanilla
 * {@code mca:tavern} surfaces as {@code in_building:tavern}.</p>
 *
 * <p><b>Music:</b> {@code near_music} — any registered
 * {@link MusicSourceProvider} reports an active source within 12
 * blocks. The built-in jukebox provider is always registered; future
 * mod compat (Immersive Melodies, etc.) plugs in additional providers.</p>
 */
public final class ContextResolver {
    private ContextResolver() {}

    public static Set<String> tagsFor(ServerLevel level, VillagerEntityMCA villager) {
        Set<String> tags = new HashSet<>(16);
        if (level == null || villager == null) return tags;
        BlockPos pos = villager.blockPosition();
        ContextScanCache cache = new ContextScanCache(level, villager);

        addTimeTags(level, tags);
        addShiftTags(level, villager, tags);
        addEnvironmentTags(level, pos, tags);
        addBiomeAndDimensionTags(level, pos, tags);
        addBuildingTags(level, villager, tags);
        addActivityProximityTags(level, cache, tags);
        addReactionProximityTags(level, cache, tags);
        addCrowdAndAgeTags(cache, tags);
        addMcaRelationshipTags(villager, cache, tags);
        addPlayerRelationshipTags(villager, cache, tags);
        addPlayerHeldItemTags(cache, tags);
        addThreatTags(level, villager, cache, tags);
        addProfessionStateTags(villager, tags);
        addEventTags(level, villager, tags);
        addInteractionStateTags(level, villager, tags);
        if (MusicSourceProviders.anyMusicNear(level, pos, 12.0)) {
            tags.add("near_music");
        }
        return tags;
    }

    /**
     * Major-event tags. Currently emits {@code near_grave} when MCA's
     * {@code GraveyardManager} reports a tombstone within 16 blocks.
     * Wedding / funeral / birthday tags wait on Townstead-side event
     * tracking that doesn't exist yet.
     */
    private static void addEventTags(ServerLevel level, VillagerEntityMCA villager, Set<String> tags) {
        try {
            GraveyardManager gm = GraveyardManager.get(level);
            if (gm != null && !gm.findAll(villager.getBoundingBox().inflate(16), false, false).isEmpty()) {
                tags.add("near_grave");
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Interaction-state tags driven by {@link DialogueStateTracker}.
     * {@code in_dialogue_with_player} fires while the RPG dialogue
     * screen is open on the villager. {@code dialogue_just_ended} fires
     * for a short window after close so authors can sequence farewell
     * reactions.
     */
    private static void addInteractionStateTags(ServerLevel level, VillagerEntityMCA villager, Set<String> tags) {
        if (DialogueStateTracker.activePartner(villager) != null) {
            tags.add("in_dialogue_with_player");
        }
        if (DialogueStateTracker.dialogueJustEnded(villager, level.getGameTime())) {
            tags.add("dialogue_just_ended");
        }
    }

    /**
     * Townstead state-system tags driven by typed hunger / thirst / fatigue
     * state, the villager's vanilla profession + job-site memory,
     * and MCA's pregnancy state. Each block is wrapped in a try/catch
     * so a missing or malformed attachment skips the tag rather than
     * killing the whole stride.
     */
    private static void addProfessionStateTags(VillagerEntityMCA villager, Set<String> tags) {
        try {
            int h = TownsteadVillagers.get(villager).needs().hunger();
            if (h < HungerData.EMERGENCY_THRESHOLD) tags.add("hungry");
            else if (h < HungerData.ADEQUATE_THRESHOLD) tags.add("peckish");
        } catch (Throwable ignored) {}

        try {
            if (ThirstBridgeResolver.isActive()) {
                int t = TownsteadVillagers.get(villager).needs().thirst();
                if (t <= ThirstData.EMERGENCY_THRESHOLD) tags.add("thirsty");
                else if (t < ThirstData.ADEQUATE_THRESHOLD) tags.add("parched");
            }
        } catch (Throwable ignored) {}

        try {
            int f = TownsteadVillagers.get(villager).needs().fatigue();
            if (f >= FatigueData.COLLAPSE_THRESHOLD) tags.add("exhausted");
            else if (f >= FatigueData.DROWSY_THRESHOLD) tags.add("drowsy");
            else if (f >= FatigueData.TIRED_THRESHOLD) tags.add("tired");
        } catch (Throwable ignored) {}

        try {
            VillagerProfession profession = villager.getVillagerData().getProfession();
            if (profession == VillagerProfession.NONE) {
                tags.add("unemployed");
            }
            Optional<net.minecraft.core.GlobalPos> jobSite = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
            if (jobSite.isPresent()
                    && jobSite.get().dimension() == villager.level().dimension()
                    && jobSite.get().pos().distSqr(villager.blockPosition()) <= 16) {
                tags.add("at_workstation");
            }
        } catch (Throwable ignored) {}

        try {
            if (villager.getRelationships().getPregnancy().isPregnant()) {
                tags.add("pregnant");
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Crowd-size and age-presence tags. Doesn't gender-break the crowd
     * (we explicitly skipped {@code mostly_men/women}).
     */
    private static void addCrowdAndAgeTags(ContextScanCache cache, Set<String> tags) {
        int count = cache.nearbyVillagers().size();
        if (count == 0) tags.add("alone");
        else if (count >= 3) tags.add("in_crowd");
        for (VillagerEntityMCA neighbor : cache.nearbyVillagers()) {
            AgeState age;
            try {
                age = AgeState.byCurrentAge(neighbor.getAge());
            } catch (Throwable t) {
                continue;
            }
            if (age == AgeState.BABY || age == AgeState.TODDLER) {
                tags.add("near_baby_villager");
                break;
            }
        }
    }

    /**
     * MCA family / partnership ties. Each tag fires when at least one
     * nearby villager fits the slot, evaluated via
     * {@link EntityRelationship#of(Entity)}.
     */
    private static void addMcaRelationshipTags(VillagerEntityMCA villager, ContextScanCache cache, Set<String> tags) {
        EntityRelationship.of(villager).ifPresent(rel -> {
            if (rel.isMarried()) tags.add("is_married");
            if (rel.isEngaged()) tags.add("is_engaged");
            if (rel.isPromised()) tags.add("is_promised");

            Optional<UUID> partnerUuid = rel.getPartnerUUID();
            Set<UUID> parentUuids = new HashSet<>();
            rel.getParents().forEach(p -> parentUuids.add(p.getUUID()));
            Set<UUID> familyUuids = new HashSet<>();
            rel.getFamily(2, 1).forEach(f -> familyUuids.add(f.getUUID()));

            for (VillagerEntityMCA neighbor : cache.nearbyVillagers()) {
                UUID nu = neighbor.getUUID();
                if (partnerUuid.isPresent() && partnerUuid.get().equals(nu)) {
                    tags.add("near_spouse");
                }
                if (parentUuids.contains(nu)) tags.add("near_parent");
                if (familyUuids.contains(nu)) tags.add("near_family");
            }
        });
    }

    /**
     * Per-villager × per-nearby-player tags driven by the MCA hearts
     * memory. Thresholds align with MCA's own conventions: positive is
     * warm, zero is unknown, negative is disliked. {@code being_watched_by_player}
     * uses a look-vec dot product so any nearby player whose crosshair
     * is roughly on this villager surfaces the tag.
     */
    private static void addPlayerRelationshipTags(VillagerEntityMCA villager, ContextScanCache cache, Set<String> tags) {
        if (cache.nearbyPlayers().isEmpty()) return;
        Optional<EntityRelationship> villagerRel = EntityRelationship.of(villager);
        for (Player player : cache.nearbyPlayers()) {
            try {
                Memories memories = villager.getVillagerBrain().getMemoriesForPlayer(player);
                int hearts = memories.getHearts();
                if (hearts >= 30) tags.add("near_player_friend");
                else if (hearts <= -10) tags.add("near_player_disliked");
                else tags.add("near_player_stranger");
            } catch (Throwable ignored) {}

            if (villagerRel.isPresent() && villagerRel.get().isMarriedTo(player.getUUID())) {
                tags.add("near_player_spouse");
            }
            if (isLookingAt(player, villager)) {
                tags.add("being_watched_by_player");
            }
        }
    }

    private static boolean isLookingAt(Player player, VillagerEntityMCA villager) {
        try {
            Vec3 toVillager = villager.position().subtract(player.getEyePosition()).normalize();
            Vec3 look = player.getLookAngle().normalize();
            return look.dot(toVillager) > 0.95;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Two surfaces per nearby player's main-hand item:
     * {@code player_holding:<item-path>} for the registry-id slug, and
     * {@code player_holding_tag:<full-tag-path>} for each vanilla item
     * tag the stack belongs to. Pack authors can define their own item
     * tag (e.g. {@code mypack:treats}) and react to it without writing
     * any Java.
     */
    private static void addPlayerHeldItemTags(ContextScanCache cache, Set<String> tags) {
        for (Player player : cache.nearbyPlayers()) {
            ItemStack stack = player.getMainHandItem();
            if (stack.isEmpty()) continue;
            try {
                ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (itemId != null) {
                    tags.add("player_holding:" + itemId.getPath());
                }
                stack.getTags().forEach((TagKey<Item> tagKey) ->
                        tags.add("player_holding_tag:" + tagKey.location()));
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Threat-presence tags. Splits hostile mobs into broad and specific
     * buckets so authors can scope reactions (illagers are different
     * from zombies). {@code raid_active} reads the vanilla raid manager.
     */
    private static void addThreatTags(ServerLevel level, VillagerEntityMCA villager, ContextScanCache cache,
            Set<String> tags) {
        if (!cache.nearbyZombieVillagers().isEmpty()) tags.add("near_zombie_villager");
        boolean anyHostile = false;
        boolean illager = false;
        for (Monster m : cache.nearbyHostileMobs()) {
            anyHostile = true;
            if (m instanceof AbstractIllager) {
                illager = true;
                break;
            }
        }
        if (anyHostile) tags.add("near_mob_threat");
        if (illager) tags.add("outsider_present");
        try {
            if (level.getRaids().getNearbyRaid(villager.blockPosition(), 96 * 96) != null) {
                tags.add("raid_active");
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Tags describing reactions currently playing on nearby villagers.
     * Two surfaces per active neighbor reaction:
     * <ul>
     *   <li>{@code near_reacting:<reaction-id>} for the exact reaction
     *   <li>{@code near_reacting_tag:<tag>} for each entry in the
     *       reaction's own {@code tags} list, so pack authors can match
     *       by category (e.g. add {@code "dancing"} to a dance reaction
     *       and match {@code near_reacting_tag:dancing}).
     * </ul>
     */
    private static void addReactionProximityTags(ServerLevel level, ContextScanCache cache, Set<String> tags) {
        long gameTime = level.getGameTime();
        Set<net.minecraft.resources.ResourceLocation> seen = new HashSet<>();
        for (VillagerEntityMCA neighbor : cache.nearbyVillagers()) {
            net.minecraft.resources.ResourceLocation id = ReactionLockTracker.activeReaction(neighbor, gameTime);
            if (id == null) continue;
            if (!seen.add(id)) continue;
            tags.add("near_reacting:" + id);
            ReactionRegistry.get(id).ifPresent(reaction -> {
                for (String t : reaction.tags()) {
                    tags.add("near_reacting_tag:" + t);
                }
            });
        }
    }

    /**
     * Tags describing what other villagers near this one are currently
     * doing. Driven by the same shift table that powers {@code on_shift:*}
     * on the villager themselves, so a {@code WORK} hour for a neighbor
     * surfaces here as {@code near_working_villager}.
     */
    private static void addActivityProximityTags(ServerLevel level, ContextScanCache cache, Set<String> tags) {
        boolean working = false, resting = false, meeting = false;
        int tickHour = (int) ((level.getDayTime() % 24000L) / 1000L);
        for (VillagerEntityMCA neighbor : cache.nearbyVillagers()) {
            if (working && resting && meeting) break;
            int ord = TownsteadVillagers.get(neighbor).schedule().currentShift(tickHour);
            switch (ord) {
                case ShiftData.ORD_WORK -> working = true;
                case ShiftData.ORD_REST -> resting = true;
                case ShiftData.ORD_MEET -> meeting = true;
                default -> {}
            }
        }
        if (working) tags.add("near_working_villager");
        if (resting) tags.add("near_resting_villager");
        if (meeting) tags.add("near_meeting_villager");
    }

    private static void addTimeTags(ServerLevel level, Set<String> tags) {
        // Daylight cycle is 24000 ticks: 0..12000 day, 12000..24000 night.
        // Dawn ~ 22500..24000, dusk ~ 12000..13500 for some narrative slack.
        long dayTime = level.getDayTime() % 24000L;
        if (dayTime < 12000L) {
            tags.add("day");
            if (dayTime < 1500L) tags.add("dawn");
        } else {
            tags.add("night");
            if (dayTime >= 22500L) tags.add("dawn");
            if (dayTime < 13500L) tags.add("dusk");
        }

        // Exact and named display hours. Townstead's tick-hour 0 = display 6,
        // matching vanilla's dayTime=0 = sunrise.
        int tickHour = (int) (dayTime / 1000L);
        int displayHour = ShiftData.toDisplayHour(tickHour);
        tags.add("hour:" + displayHour);
        String period = namedPeriod(displayHour);
        if (period != null) tags.add(period);

        // 1-based day counter so day:1 is the first day of the world.
        long dayNumber = (level.getGameTime() / 24000L) + 1L;
        tags.add("day:" + dayNumber);
    }

    private static String namedPeriod(int displayHour) {
        if (displayHour >= 5 && displayHour <= 7) return "early_morning";
        if (displayHour >= 8 && displayHour <= 11) return "morning";
        if (displayHour >= 12 && displayHour <= 13) return "noon";
        if (displayHour >= 14 && displayHour <= 16) return "afternoon";
        if (displayHour >= 17 && displayHour <= 20) return "evening";
        // 21..23 and 0..4 wrap into one band; using a single tag keeps it
        // simple and avoids overlapping with the coarse "night" tag.
        return "late_night";
    }

    private static void addShiftTags(ServerLevel level, VillagerEntityMCA villager, Set<String> tags) {
        int tickHour = (int) ((level.getDayTime() % 24000L) / 1000L);
        TownsteadVillager.ScheduleState schedule = TownsteadVillagers.get(villager).schedule();
        int ord = schedule.currentShift(tickHour);
        String name = shiftName(ord);
        if (name != null) tags.add("on_shift:" + name);
        if (schedule.hasNonDefaultCustomShifts()) {
            tags.add("shift_custom");
        }
    }

    private static String shiftName(int ord) {
        return switch (ord) {
            case ShiftData.ORD_IDLE -> "idle";
            case ShiftData.ORD_WORK -> "work";
            case ShiftData.ORD_MEET -> "meet";
            case ShiftData.ORD_REST -> "rest";
            default -> null;
        };
    }

    private static void addEnvironmentTags(ServerLevel level, BlockPos pos, Set<String> tags) {
        if (level.isRaining()) tags.add("raining");
        if (level.isThundering()) tags.add("thundering");

        // canSeeSky walks straight up; a villager standing on a roof block
        // tests fine because we offset above the feet position.
        if (level.canSeeSky(pos.above())) {
            tags.add("under_open_sky");
        } else {
            tags.add("under_roof");
        }

        // Block-light only (not sky). Threshold 4 matches vanilla mob spawn
        // gates — a useful eyeball for "this feels dim to a person".
        int blockLight = level.getBrightness(LightLayer.BLOCK, pos);
        int skyLight = level.getBrightness(LightLayer.SKY, pos);
        if (blockLight < 4 && skyLight < 4) tags.add("in_dark");

        if (anyFluidWithin(level, pos, Fluids.WATER.getSource(), Fluids.WATER.getFlowing(), 3)) {
            tags.add("near_water");
        }
        if (anyFluidWithin(level, pos, Fluids.LAVA.getSource(), Fluids.LAVA.getFlowing(), 3)) {
            tags.add("near_lava");
        }
    }

    private static void addBiomeAndDimensionTags(ServerLevel level, BlockPos pos, Set<String> tags) {
        try {
            Holder<Biome> biomeHolder = level.getBiome(pos);
            Optional<ResourceKey<Biome>> keyOpt = biomeHolder.unwrapKey();
            keyOpt.ifPresent(key -> tags.add("biome:" + key.location()));

            float temp = biomeHolder.value().getBaseTemperature();
            if (temp < 0.15F) tags.add("freezing");
            else if (temp < 0.5F) tags.add("cold");
            else if (temp < 1.0F) tags.add("temperate");
            else tags.add("hot");
        } catch (Throwable ignored) {}

        try {
            tags.add("dimension:" + level.dimension().location());
        } catch (Throwable ignored) {}
    }

    private static boolean anyFluidWithin(ServerLevel level, BlockPos center,
            net.minecraft.world.level.material.Fluid source,
            net.minecraft.world.level.material.Fluid flowing,
            int radius) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int rSq = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    if (dx * dx + dz * dz > rSq) continue;
                    cursor.setWithOffset(center, dx, dy, dz);
                    var fluid = level.getFluidState(cursor).getType();
                    if (fluid == source || fluid == flowing) return true;
                }
            }
        }
        return false;
    }

    private static void addBuildingTags(ServerLevel level, VillagerEntityMCA villager, Set<String> tags) {
        try {
            BlockPos pos = villager.blockPosition();
            VillageManager manager = VillageManager.get(level);
            if (manager == null) return;
            Optional<Village> villageOpt = manager.findNearestVillage(pos, Village.MERGE_MARGIN);
            if (villageOpt.isEmpty()) return;
            Village village = villageOpt.get();
            for (Building building : village.getBuildings().values()) {
                String type = building.getType();
                if (type == null || type.isBlank()) continue;
                BlockPos p0 = building.getPos0();
                BlockPos p1 = building.getPos1();
                if (p0 == null || p1 == null) continue;
                if (containsXZ(p0, p1, pos)) {
                    int slug = type.indexOf(':');
                    String typeName = slug >= 0 ? type.substring(slug + 1) : type;
                    tags.add("in_building:" + typeName);
                }
            }
        } catch (Throwable ignored) {}
    }

    private static boolean containsXZ(BlockPos a, BlockPos b, BlockPos p) {
        int minX = Math.min(a.getX(), b.getX());
        int maxX = Math.max(a.getX(), b.getX());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxZ = Math.max(a.getZ(), b.getZ());
        int minY = Math.min(a.getY(), b.getY()) - 1;
        int maxY = Math.max(a.getY(), b.getY()) + 4;
        return p.getX() >= minX && p.getX() <= maxX
                && p.getZ() >= minZ && p.getZ() <= maxZ
                && p.getY() >= minY && p.getY() <= maxY;
    }
}

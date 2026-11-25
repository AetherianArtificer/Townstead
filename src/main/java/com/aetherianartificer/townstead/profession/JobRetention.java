package com.aetherianartificer.townstead.profession;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * When a villager may lose a job.
 *
 * <p>MCA takes the profession from a novice the tick its job-site memory is absent. That memory
 * goes absent for reasons that are not unemployment: the walk to the block failed, two villagers
 * held one block and this one lost the tie, or the block was replaced. The villager then took
 * whatever block was nearest and came back as something else.</p>
 *
 * <p>The rules here: a villager keeps the profession while the village has a job block of its
 * type, and looks for one to claim. With no such block left, the profession lasts one more day,
 * so a block that is broken and put back is claimed again by the same worker.</p>
 */
public final class JobRetention {

    /** How long a profession outlives the last job block of its type. */
    static final long NO_SITE_GRACE_TICKS = 24000L;
    /** How long a seated worker waits for a missing seat to come back. */
    static final long SEAT_GRACE_TICKS = 3600L;

    private static final String LOST_AT = "townstead:job_site_lost_at";
    private static final String SEAT_MISSING_AT = "townstead:seat_missing_at";
    private static final int RECHECK_TICKS = 100;
    private static final int VILLAGE_SEARCH_RADIUS = 128;
    private static final int HOMELESS_SEARCH_RADIUS = 48;

    private record Check(long at, VillagerProfession profession, boolean siteExists) {}

    private static final Map<Villager, Check> CHECKS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private JobRetention() {}

    static boolean mayLose(boolean siteExists, long lostAt, long now) {
        return !siteExists && now - lostAt >= NO_SITE_GRACE_TICKS;
    }

    static boolean seatGraceOver(long missingAt, long now) {
        return now - missingAt >= SEAT_GRACE_TICKS;
    }

    /** Whether this profession is a job, which guard drafting must leave alone. */
    public static boolean employed(VillagerProfession profession) {
        return profession != VillagerProfession.NONE && profession != VillagerProfession.NITWIT;
    }

    /** Whether MCA may take this villager's profession for having no job-site memory. */
    public static boolean mayLoseJob(ServerLevel level, Villager villager) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (!employed(profession) || profession.heldJobSite() == PoiType.NONE) return true;
        // MCA only takes the job from a novice; anyone else needs no answer.
        if (villager.getVillagerXp() != 0 || villager.getVillagerData().getLevel() > 1) return true;
        long now = level.getGameTime();
        CompoundTag data = villager.getPersistentData();
        if (!data.contains(LOST_AT)) data.putLong(LOST_AT, now);
        return mayLose(siteExists(level, villager, profession, now), data.getLong(LOST_AT), now);
    }

    /** A villager with a job site again starts a new count the next time it is lost. */
    public static void tick(VillagerEntityMCA villager) {
        if (!villager.getBrain().hasMemoryValue(MemoryModuleType.JOB_SITE)) return;
        CompoundTag data = villager.getPersistentData();
        if (data.contains(LOST_AT)) data.remove(LOST_AT);
    }

    /** Whether a seated worker's seat has been missing long enough to release them. */
    public static boolean seatMissingTooLong(VillagerEntityMCA villager, long now) {
        CompoundTag data = villager.getPersistentData();
        if (!data.contains(SEAT_MISSING_AT)) data.putLong(SEAT_MISSING_AT, now);
        return seatGraceOver(data.getLong(SEAT_MISSING_AT), now);
    }

    public static void seatFound(VillagerEntityMCA villager) {
        CompoundTag data = villager.getPersistentData();
        if (data.contains(SEAT_MISSING_AT)) data.remove(SEAT_MISSING_AT);
    }

    // ---- what the Professions page shows ----

    public static final int WORKING = 0;
    /** Employed with no job block or seat, and not about to lose the job. */
    public static final int LOOKING = 1;
    /** The job ends when the count reaches zero. */
    public static final int ENDING = 2;

    /** Where a worker stands. {@code endsInTicks} is -1 unless the job is ending. */
    public record Status(int state, int endsInTicks, boolean seat) {
        public static final Status WORKING_NOW = new Status(WORKING, -1, false);
    }

    static Status seatStatus(boolean missing, long missingAt, long now) {
        if (!missing) return Status.WORKING_NOW;
        return new Status(ENDING, remaining(SEAT_GRACE_TICKS, missingAt, now), true);
    }

    static Status siteStatus(boolean hasSite, boolean canLose, boolean siteExists,
                             long lostAt, long now) {
        if (hasSite) return Status.WORKING_NOW;
        if (!canLose || siteExists) return new Status(LOOKING, -1, false);
        return new Status(ENDING, remaining(NO_SITE_GRACE_TICKS, lostAt, now), false);
    }

    private static int remaining(long grace, long since, long now) {
        return (int) Math.max(0L, Math.min(grace, grace - (now - since)));
    }

    public static Status status(ServerLevel level, VillagerEntityMCA villager) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (!employed(profession)) return Status.WORKING_NOW;
        long now = level.getGameTime();
        CompoundTag data = villager.getPersistentData();
        if (ProfessionCarriers.seatHired(profession)) {
            return seatStatus(data.contains(SEAT_MISSING_AT), data.getLong(SEAT_MISSING_AT), now);
        }
        if (!ProfessionSlotRules.requiresJobSite(profession)) return Status.WORKING_NOW;
        boolean hasSite = villager.getBrain().hasMemoryValue(MemoryModuleType.JOB_SITE);
        boolean canLose = villager.getVillagerXp() == 0 && villager.getVillagerData().getLevel() <= 1
                && !villager.isProfessionImportant();
        return siteStatus(hasSite, canLose,
                hasSite || siteExists(level, villager, profession, now),
                data.contains(LOST_AT) ? data.getLong(LOST_AT) : now, now);
    }

    /** The block this profession works at, or null when it has none or several kinds. */
    @org.jetbrains.annotations.Nullable
    public static net.minecraft.resources.ResourceLocation workstation(VillagerProfession profession) {
        if (profession.heldJobSite() == PoiType.NONE) return null;
        return WORKSTATIONS.computeIfAbsent(profession, key -> {
            java.util.Set<net.minecraft.world.level.block.Block> blocks = new java.util.HashSet<>();
            net.minecraft.core.registries.BuiltInRegistries.POINT_OF_INTEREST_TYPE.holders()
                    .filter(holder -> key.heldJobSite().test(holder))
                    .forEach(holder -> holder.value().matchingStates()
                            .forEach(state -> blocks.add(state.getBlock())));
            return blocks.size() == 1 ? Optional.of(net.minecraft.core.registries.BuiltInRegistries
                    .BLOCK.getKey(blocks.iterator().next())) : Optional.empty();
        }).orElse(null);
    }

    private static final Map<VillagerProfession, Optional<net.minecraft.resources.ResourceLocation>>
            WORKSTATIONS = new java.util.concurrent.ConcurrentHashMap<>();

    private static boolean siteExists(ServerLevel level, Villager villager,
                                      VillagerProfession profession, long now) {
        Check cached = CHECKS.get(villager);
        if (cached != null && cached.profession() == profession
                && now - cached.at() < RECHECK_TICKS && now >= cached.at()) {
            return cached.siteExists();
        }
        boolean exists = findSite(level, villager, profession);
        CHECKS.put(villager, new Check(now, profession, exists));
        return exists;
    }

    private static boolean findSite(ServerLevel level, Villager villager,
                                    VillagerProfession profession) {
        PoiManager pois = level.getPoiManager();
        Optional<Village> village = villager instanceof VillagerEntityMCA mca
                ? mca.getResidency().getHomeVillage() : Optional.empty();
        if (village.isPresent()) {
            Village home = village.get();
            return pois.findAll(profession.heldJobSite(),
                    pos -> home.isWithinBorder(pos, Village.BORDER_MARGIN),
                    new BlockPos(home.getCenter()), VILLAGE_SEARCH_RADIUS,
                    PoiManager.Occupancy.ANY).findAny().isPresent();
        }
        return pois.findAll(profession.heldJobSite(), pos -> true, villager.blockPosition(),
                HOMELESS_SEARCH_RADIUS, PoiManager.Occupancy.ANY).findAny().isPresent();
    }
}

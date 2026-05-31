package com.aetherianartificer.townstead.calendar;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;

/**
 * Catches up villager and animal aging when {@link WorldCalendarSavedData#worldDayCounter}
 * advances by more than one day — typically because the {@code real_clock}
 * time mode added real-world days elapsed since the last save. Without this
 * the calendar would show "today is 7 days later" while villagers and baby
 * animals would still be the age they were before you logged off.
 *
 * <h2>Storage</h2>
 *
 * Per-AgeableMob stamp lives in Forge/NeoForge persistent NBT under
 * {@code townstead.last_seen_world_day}. Single long per entity, distributed
 * naturally with chunk saves.
 *
 * <h2>When the stamp updates</h2>
 *
 * <ul>
 *   <li>{@link #onEntityJoin} — when an entity joins the level (chunk load
 *       or fresh spawn). If a prior stamp exists and is older than the
 *       current worldDayCounter, the missed aging is applied first. Then
 *       the stamp is refreshed.</li>
 *   <li>{@link #refreshLastSeenStamps} — called on every MC-day rollover.
 *       Keeps in-memory entity stamps current so the next autosave captures
 *       the latest value; without this, a long session would leave the
 *       persisted stamp behind by however many in-game days passed.</li>
 *   <li>{@link #applyBulkCatchup} — called once after real_clock catch-up
 *       bumps the world day counter. Iterates currently-loaded ageables and
 *       advances each one's age directly (those entities had already joined
 *       and stamped the *pre-bump* day; without this they'd never notice
 *       the jump). Entities loaded after the bump are handled by
 *       {@code onEntityJoin}.</li>
 * </ul>
 *
 * <h2>What gets aged</h2>
 *
 * Any {@link AgeableMob} with negative {@code age} (vanilla baby state).
 * Adults (age &gt;= 0) are skipped — vanilla uses positive age for the
 * love-mode countdown, which we mustn't extend. MCA's villager AgeState
 * transitions (BABY → TODDLER → CHILD → TEEN → ADULT) ride
 * {@link AgeableMob#setAge}, so this naturally handles villager growth too.
 *
 * <h2>Performance</h2>
 *
 * Zero per-tick cost. Per-entity-join is a few ops. Day-rollover and bulk
 * passes are {@code O(loaded ageables)} — typically tens to hundreds, rarely
 * thousands. Sub-millisecond in normal worlds, single-digit millis on
 * megafarms. Both are one-shot operations, not per-tick.
 */
public final class AgeableCatchup {

    static final String NBT_KEY_LAST_SEEN_WORLD_DAY = "townstead.last_seen_world_day";
    static final int TICKS_PER_MC_DAY = 24000;

    private AgeableCatchup() {}

    /**
     * Apply per-entity catch-up on entity-join-level. New entities (no prior
     * stamp) get stamped without aging. Safe to call for any Entity; no-ops
     * unless the entity is an AgeableMob.
     */
    public static void onEntityJoin(Entity entity, MinecraftServer server) {
        if (server == null) return;
        if (!(entity instanceof AgeableMob mob)) return;
        long currentDay = TownsteadCalendar.lifeDay(server);
        CompoundTag nbt = mob.getPersistentData();
        if (nbt.contains(NBT_KEY_LAST_SEEN_WORLD_DAY)) {
            long stored = nbt.getLong(NBT_KEY_LAST_SEEN_WORLD_DAY);
            long deltaDays = currentDay - stored;
            if (deltaDays > 0L) {
                advanceBaby(mob, deltaDays * TICKS_PER_MC_DAY);
            }
        }
        nbt.putLong(NBT_KEY_LAST_SEEN_WORLD_DAY, currentDay);
    }

    /**
     * Iterate every loaded AgeableMob across every server level and advance
     * its age by {@code catchupDays} worth of ticks. Called immediately after
     * the real_clock catch-up bumps the world day counter, so entities that
     * had already joined (and stamped the pre-bump day) get their aging
     * applied directly. Also refreshes their stamps to the new value so they
     * don't double-apply via {@link #onEntityJoin} on the next save/load.
     */
    public static void applyBulkCatchup(MinecraftServer server, int catchupDays) {
        if (server == null || catchupDays <= 0) return;
        long deltaTicks = (long) catchupDays * TICKS_PER_MC_DAY;
        long currentDay = TownsteadCalendar.lifeDay(server);
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof AgeableMob mob) {
                    advanceBaby(mob, deltaTicks);
                    mob.getPersistentData().putLong(NBT_KEY_LAST_SEEN_WORLD_DAY, currentDay);
                }
            }
        }
    }

    /**
     * Refresh the in-memory {@code lastSeenWorldDay} stamp on every loaded
     * AgeableMob to the current world day counter. Called on every MC-day
     * rollover so autosaves between sessions persist a current value.
     */
    public static void refreshLastSeenStamps(MinecraftServer server) {
        if (server == null) return;
        long currentDay = TownsteadCalendar.lifeDay(server);
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof AgeableMob mob) {
                    mob.getPersistentData().putLong(NBT_KEY_LAST_SEEN_WORLD_DAY, currentDay);
                }
            }
        }
    }

    /**
     * Advance an ageable's vanilla age field by {@code deltaTicks}. Adults
     * (age &gt;= 0) skip — vanilla uses positive age for the love-mode
     * countdown, which we mustn't extend. Babies (age &lt; 0) move toward 0;
     * clamp at 0 so we don't accidentally trip love-mode for a freshly-
     * matured baby.
     */
    private static void advanceBaby(AgeableMob mob, long deltaTicks) {
        int currentAge = mob.getAge();
        if (currentAge >= 0) return;
        long newAgeL = (long) currentAge + deltaTicks;
        int newAge = newAgeL >= 0L ? 0 : (int) newAgeL;
        mob.setAge(newAge);
    }
}

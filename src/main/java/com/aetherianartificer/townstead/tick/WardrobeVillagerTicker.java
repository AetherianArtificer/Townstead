package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.clothing.ClothingLayer;
import com.aetherianartificer.townstead.clothing.dress.OuterwearDoffDon;
import com.aetherianartificer.townstead.clothing.policy.SkinPicker;
import com.aetherianartificer.townstead.clothing.policy.WardrobePolicy;
import com.aetherianartificer.townstead.clothing.policy.WardrobeResolver;
import com.aetherianartificer.townstead.clothing.wardrobe.WardrobeAssignments;
import com.aetherianartificer.townstead.clothing.wardrobe.WardrobeServer;
import com.aetherianartificer.townstead.pheno.condition.types.ShiftStateConditionType;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Evaluates wardrobe policies for a villager on the same cadence as ambient temperature sampling,
 * staggered by entity id so a village does not re-dress on one tick.
 *
 * <p>The base layer changes here, from the Wardrobe's picks, because a skin costs nothing to
 * change. The other layers' unmet rules are kept in the plan for the dress behaviour to act on. Clothing locks always
 * win, and a villager with no matching policy dresses for the weather. The same cadence runs
 * the doff-and-don rule that takes a coat off indoors and puts it back on outside.</p>
 */
public final class WardrobeVillagerTicker {

    public static final long INTERVAL_TICKS = 200L;

    private static final Map<Integer, State> STATE = new ConcurrentHashMap<>();

    private static final class State {
        long nextTick;
        WardrobeResolver.Plan plan = WardrobeResolver.Plan.EMPTY;
        long planDay = Long.MIN_VALUE;
        /** The skin the profession chain or MCA gave the villager, kept while the wardrobe overrides it. */
        @Nullable String workSkin;
        /** The skin the wardrobe last put on, or null when the villager wears their work skin. */
        @Nullable String applied;
        final OuterwearDoffDon.Dwell dwell = new OuterwearDoffDon.Dwell();
    }

    private WardrobeVillagerTicker() {}

    public static void clear() {
        STATE.clear();
    }

    public static void forget(int entityId) {
        STATE.remove(entityId);
    }

    /** The plan last resolved for this villager, for the dress behaviour. Empty until the first tick. */
    public static WardrobeResolver.Plan planOf(@Nullable VillagerEntityMCA villager) {
        if (villager == null) return WardrobeResolver.Plan.EMPTY;
        State state = STATE.get(villager.getId());
        return state == null ? WardrobeResolver.Plan.EMPTY : state.plan;
    }

    /** Re-dresses the villager now, after a Wardrobe edit, instead of at their next turn. */
    public static void refresh(VillagerEntityMCA self) {
        if (self == null || !(self.level() instanceof ServerLevel)) return;
        STATE.computeIfAbsent(self.getId(), id -> new State()).nextTick = 0;
        tick(self);
    }

    public static void tick(VillagerEntityMCA self) {
        if (self == null || !(self.level() instanceof ServerLevel level)) return;
        long gameTime = level.getGameTime();
        State state = STATE.computeIfAbsent(self.getId(), id -> {
            State fresh = new State();
            fresh.nextTick = gameTime + Math.floorMod(id, (int) INTERVAL_TICKS);
            return fresh;
        });
        if (gameTime < state.nextTick) return;
        state.nextTick = gameTime + INTERVAL_TICKS;

        MinecraftServer server = level.getServer();
        long day = server == null ? level.getDayTime() / 24000L : TownsteadCalendar.worldDay(server);
        WardrobeResolver.Plan plan = WardrobeResolver.resolve(self);
        state.plan = plan;
        state.planDay = day;
        applyBase(self, state, plan, day);
        OuterwearDoffDon.tick(level, self, state.dwell, gameTime);
    }

    /**
     * On shift the villager wears the Work pick, else the skin the profession chain or MCA gave
     * them. Off shift they wear the day's picked skin, else their own choice: a pack or template
     * rule's draw, or one of their favourites, weighted toward favourites either way. Anything
     * else that changes the skin (a profession change, the editor) is taken as the new work skin.
     */
    static void applyBase(VillagerEntityMCA villager, State state, WardrobeResolver.Plan plan, long day) {
        if (villager.isClothingLocked()) return;
        // A rig villager wearing a skin painted for another body (MCA's spawn or profession pick) gets a
        // fitted work skin first.
        com.aetherianartificer.townstead.clothing.policy.RigSkinPicker.ensureFitted(villager);
        String current = villager.getClothes();
        if (state.applied != null && !state.applied.equals(current)) state.applied = null;
        if (state.applied == null) state.workSkin = current;

        boolean onShift = ShiftStateConditionType.stateOf(villager) == ShiftStateConditionType.State.ON_SHIFT;
        String desired = desiredSkin(villager, plan, day, onShift);
        if (desired == null || desired.equals(state.workSkin)) {
            if (state.applied != null) {
                if (state.workSkin != null && !state.workSkin.equals(current)) villager.setClothes(state.workSkin);
                state.applied = null;
            }
            return;
        }
        if (!desired.equals(current)) villager.setClothes(desired);
        state.applied = desired;
    }

    static @Nullable String desiredSkin(VillagerEntityMCA villager, WardrobeResolver.Plan plan, long day,
                                        boolean onShift) {
        MinecraftServer server = villager.getServer();
        if (server == null) return null;
        String work = WardrobeServer.workSkin(server, villager);
        String fallback = work.isEmpty() ? null : work;
        if (onShift) return fallback;

        WardrobeAssignments assignments = WardrobeAssignments.get(server);
        String cell = assignments.villager(villager.getUUID(), WardrobeServer.today(server));
        if (!cell.isEmpty() && WardrobeServer.templateOf(cell) == null) {
            return WardrobeServer.skinExists(cell) ? cell : fallback;
        }
        WardrobeAssignments.Entry entry = assignments.entry(villager.getUUID());
        SkinPicker.Favourites favourites = entry == null ? SkinPicker.Favourites.NONE
                : new SkinPicker.Favourites(entry.starred(), entry.picks());
        WardrobePolicy.LayerRule rule = plan.rule(ClothingLayer.BASE);
        Optional<String> chosen;
        if (rule != null && rule.requirement() != WardrobePolicy.Requirement.NONE) {
            chosen = SkinPicker.pick(villager, rule.selector(), day, favourites);
        } else {
            chosen = SkinPicker.pickFavourite(villager, favourites, day, WardrobeServer::skinExists);
        }
        return chosen.orElse(fallback);
    }
}

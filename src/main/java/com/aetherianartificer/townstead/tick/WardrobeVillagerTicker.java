package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.clothing.ClothingLayer;
import com.aetherianartificer.townstead.clothing.dress.OuterwearDoffDon;
import com.aetherianartificer.townstead.clothing.policy.SkinPicker;
import com.aetherianartificer.townstead.clothing.policy.WardrobePolicy;
import com.aetherianartificer.townstead.clothing.policy.WardrobeResolver;
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
 * <p>The base layer changes here, because a skin costs nothing to change. The other layers'
 * unmet rules are kept in the plan for the dress behaviour to act on. Clothing locks always
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
        /** The skin the profession chain or MCA gave the villager, kept while a policy overrides it. */
        @Nullable String workSkin;
        boolean overridden;
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
     * Work clothes win on shift: a policy only changes the base layer off shift, and the skin
     * the profession chain gave the villager comes back the moment they clock in. Anything that
     * changes the skin while it is not overridden (a profession change, the editor) is taken as
     * the new work skin.
     */
    static void applyBase(VillagerEntityMCA villager, State state, WardrobeResolver.Plan plan, long day) {
        if (villager.isClothingLocked()) return;
        String current = villager.getClothes();
        boolean onShift = ShiftStateConditionType.stateOf(villager) == ShiftStateConditionType.State.ON_SHIFT;
        WardrobePolicy.LayerRule rule = plan.rule(ClothingLayer.BASE);
        boolean wanted = rule != null && rule.requirement() != WardrobePolicy.Requirement.NONE && !onShift;

        if (!wanted) {
            if (state.overridden) {
                if (state.workSkin != null && !state.workSkin.equals(current)) villager.setClothes(state.workSkin);
                state.overridden = false;
            }
            state.workSkin = villager.getClothes();
            return;
        }

        if (!state.overridden) state.workSkin = current;
        Optional<String> chosen = SkinPicker.pick(villager, rule.selector(), day);
        if (chosen.isEmpty()) return;
        if (!chosen.get().equals(current)) villager.setClothes(chosen.get());
        state.overridden = true;
    }
}

package com.aetherianartificer.townstead.aspect;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.chronicle.Chronicles;
import com.aetherianartificer.townstead.compat.vampirism.VampirismStateProviders;
import com.aetherianartificer.townstead.pheno.state.EntityStateDefinition;
import com.aetherianartificer.townstead.pheno.state.EntityStates;
import com.aetherianartificer.townstead.root.RootSetC2SPayload;
import com.aetherianartificer.townstead.switchboard.Switchboard;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The editor's Aspects page: what someone can be (no aspect, or any state whose aspect is
 * {@code pickable}), what they are now, and the pick itself. A player's Vampirism-faction aspect
 * (vampire, werewolf) joins the mod's faction at level 1, and the mod runs it from there; anything
 * else is the state set directly. Operators and creative players may pick for anyone, any time.
 * Other players pick their own once, as a start, when the server allows it and they are in no
 * faction yet.
 */
public final class AspectPicks {
    private static final String PICKED = "townstead:picked_aspect";

    private AspectPicks() {}

    /** The aspects on offer, in definition order. */
    public static List<EntityStateDefinition> pickable() {
        List<EntityStateDefinition> out = new ArrayList<>();
        for (EntityStateDefinition definition : EntityStates.definitions().values()) {
            EntityStateDefinition.Aspect aspect = definition.aspect();
            if (aspect != null && aspect.pickable()) out.add(definition);
        }
        return out;
    }

    /** The pickable aspect {@code entity} carries now, or null for none. */
    public static @Nullable ResourceLocation current(LivingEntity entity) {
        for (EntityStateDefinition definition : pickable()) {
            if (EntityStates.resolve(entity, definition.id()).active()) return definition.id();
        }
        return null;
    }

    /** The entity the editor means: the player themself, or a villager near them. */
    public static @Nullable LivingEntity target(ServerPlayer sp, int entityId) {
        if (entityId == RootSetC2SPayload.SELF) return sp;
        Entity entity = sp.serverLevel().getEntity(entityId);
        return entity instanceof VillagerEntityMCA villager && villager.isAlive() && sp.distanceToSqr(villager) <= 64 * 64
                ? villager : null;
    }

    /** Whether {@code sp} may change {@code target}'s aspect right now. */
    public static boolean mayPick(ServerPlayer sp, LivingEntity target) {
        return lockReason(sp, target).isEmpty();
    }

    /** Why {@code sp} may not change {@code target}'s aspect right now, as a lang key; empty when they may. */
    public static String lockReason(ServerPlayer sp, LivingEntity target) {
        if (sp.hasPermissions(2) || sp.isCreative()) return "";
        if (target != sp) return "townstead.aspect.locked.operator";
        if (!Switchboard.get(TownsteadConfig.ALLOW_ASPECT_START)) return "townstead.aspect.locked.off";
        if (Chronicles.count(sp.server, sp.getUUID(), PICKED) > 0) return "townstead.aspect.locked.picked";
        if (current(sp) != null) return "townstead.aspect.locked.has";
        if (VampirismStateProviders.factionOf(sp) != null) return "townstead.aspect.locked.faction";
        return "";
    }

    /** The definition of the pickable aspect {@code target} has now, or null. */
    public static @Nullable EntityStateDefinition currentDefinition(LivingEntity target) {
        ResourceLocation id = current(target);
        return id == null ? null : EntityStates.definition(id);
    }

    /** Whether {@code sp} may change how {@code target}'s aspect looks: their own always, anyone's as an operator. */
    public static boolean mayStyle(ServerPlayer sp, LivingEntity target) {
        return target == sp || sp.hasPermissions(2) || sp.isCreative();
    }

    /** The value an aspect option has on {@code target} now, 0 to count-1. */
    public static int optionValue(LivingEntity target, EntityStateDefinition.Option option) {
        if (target instanceof net.minecraft.world.entity.player.Player player && option.player() != null) {
            AspectOptionSetters.Setter setter = AspectOptionSetters.get(option.player());
            if (setter != null) return Math.floorMod(setter.get().applyAsInt(player), option.count());
        }
        EntityStates.Resolved chosen = EntityStates.resolve(target, option.state());
        if (chosen.active() && chosen.amount() >= 1) return Math.floorMod((int) chosen.amount() - 1, option.count());
        // Unchosen: the same one a state form falls back to, so the page shows what is drawn.
        return Math.floorMod(com.aetherianartificer.townstead.pheno.state.StateForms.fallback(target, option.id()), option.count());
    }

    /** Sets an option of {@code target}'s current aspect. False when it is not theirs to set. */
    public static boolean setOption(ServerPlayer sp, LivingEntity target, String optionId, int value) {
        EntityStateDefinition definition = currentDefinition(target);
        if (definition == null || !mayStyle(sp, target)) return false;
        return applyOption(target, definition, optionId, value);
    }

    /** Sets an option of {@code definition}'s aspect on {@code target}, 0 to count-1, with no checks. */
    public static boolean applyOption(LivingEntity target, EntityStateDefinition definition, String optionId, int value) {
        for (EntityStateDefinition.Option option : definition.aspect().options()) {
            if (!option.id().equals(optionId)) continue;
            int v = Math.floorMod(value, option.count());
            if (target instanceof net.minecraft.world.entity.player.Player player && option.player() != null
                    && AspectOptionSetters.get(option.player()) != null) {
                AspectOptionSetters.get(option.player()).set().accept(player, v);
            } else {
                EntityStates.set(target, option.state(), v + 1, 0, null);
            }
            return true;
        }
        return false;
    }

    /** Makes {@code target} {@code aspect} (null for none). False when nothing changed. */
    public static boolean pick(ServerPlayer sp, LivingEntity target, @Nullable ResourceLocation aspect) {
        if (!mayPick(sp, target)) return false;
        EntityStateDefinition chosen = null;
        if (aspect != null) {
            for (EntityStateDefinition definition : pickable()) if (definition.id().equals(aspect)) chosen = definition;
            if (chosen == null || !EntityStates.supports(target, chosen.id())) return false;
        }
        boolean done = become(target, chosen, chosen == null ? 0 : chosen.aspect().pick(), true);
        if (done && target == sp && !sp.hasPermissions(2) && !sp.isCreative()) Chronicles.addCounter(sp.server, sp.getUUID(), PICKED, 1);
        return done;
    }

    /**
     * Makes {@code target} {@code chosen} (null for none) at {@code level}, clearing every other
     * pickable aspect, with no checks. A player's faction aspect joins the mod's faction; with
     * {@code keepLevel}, one already in it stays at their level.
     */
    public static boolean become(LivingEntity target, @Nullable EntityStateDefinition chosen, double level, boolean keepLevel) {
        for (EntityStateDefinition definition : pickable()) {
            if (chosen != null && definition.id().equals(chosen.id())) continue;
            ResourceLocation faction = definition.aspect().faction();
            if (target instanceof Player player && faction != null) {
                if (faction.equals(VampirismStateProviders.factionOf(player))) VampirismStateProviders.leaveFaction(player);
            } else {
                EntityStates.clear(target, definition.id(), null);
            }
        }
        if (chosen == null) return true;
        ResourceLocation faction = chosen.aspect().faction();
        if (target instanceof Player player && faction != null) {
            return keepLevel && faction.equals(VampirismStateProviders.factionOf(player))
                    || VampirismStateProviders.joinFaction(player, faction, (int) Math.max(1, level));
        }
        return EntityStates.set(target, chosen.id(), level, 0, null);
    }
}

package com.aetherianartificer.townstead.pheno.state;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a state tier makes of someone's body and day: the rig they wear ({@code "rig"}), and
 * whether they can be talked to ({@code "talk"}) or sleep ({@code "sleep"}). A werewolf's beast
 * tier is the first user. The first active state that sets any of these decides; worked out as
 * states tick, and sent to everyone who can see them.
 */
public final class StateForms {
    /** {@code variants} fill the placeholders in the rig's textures, such as {@code {coat:11}}. */
    public record Form(String rig, boolean talk, boolean sleep, Map<String, Integer> variants) {
        public static final Form NONE = new Form("", true, true, Map.of());
    }

    private static final Map<UUID, Form> CURRENT = new ConcurrentHashMap<>();

    private StateForms() {}

    public static Form of(Entity entity) {
        return CURRENT.getOrDefault(entity.getUUID(), Form.NONE);
    }

    public static @Nullable String rig(Entity entity) {
        String rig = of(entity).rig();
        return rig.isEmpty() ? null : rig;
    }

    public static boolean canSleep(Entity entity) {
        return of(entity).sleep();
    }

    public static boolean canTalk(Entity entity) {
        return of(entity).talk();
    }

    /** Works out the form from the entity's states now, and tells everyone watching when it changed. */
    static void update(LivingEntity entity) {
        Form form = compute(entity);
        Form before = form.equals(Form.NONE) ? CURRENT.remove(entity.getUUID()) : CURRENT.put(entity.getUUID(), form);
        if (form.equals(before == null ? Form.NONE : before)) return;
        entity.refreshDimensions();
        send(entity, form);
    }

    private static Form compute(LivingEntity entity) {
        for (EntityStateDefinition definition : EntityStates.definitions().values()) {
            EntityStates.Resolved state = EntityStates.resolve(entity, definition.id());
            if (!state.active() || state.tierIndex() < 0 || state.tierIndex() >= definition.tiers().size()) continue;
            EntityStateDefinition.Tier tier = definition.tiers().get(state.tierIndex());
            if (tier.rig().isEmpty() && tier.talk() && tier.sleep()) continue;
            Map<String, Integer> variants = new java.util.LinkedHashMap<>();
            tier.variants().forEach((name, chosen) -> variants.put(name, variant(entity, name, chosen)));
            return new Form(tier.rig(), tier.talk(), tier.sleep(), Map.copyOf(variants));
        }
        return Form.NONE;
    }

    /** The state-chosen variant (its amount less one), else one fixed by who they are. */
    public static int variant(LivingEntity entity, String name, ResourceLocation state) {
        EntityStates.Resolved chosen = EntityStates.resolve(entity, state);
        if (chosen.active() && chosen.amount() >= 1) return (int) chosen.amount() - 1;
        return fallback(entity, name);
    }

    /** The variant someone has before anyone chooses: fixed by who they are, different per placeholder. */
    public static int fallback(LivingEntity entity, String name) {
        return Math.floorMod(entity.getUUID().hashCode() * 31 + name.hashCode(), 1 << 16);
    }

    /** Tells a player who starts seeing {@code entity} what form it is in. */
    public static void syncTo(ServerPlayer player, Entity entity) {
        Form form = of(entity);
        if (form.equals(Form.NONE)) return;
        StateFormS2CPayload payload = new StateFormS2CPayload(entity.getId(), form.rig(), form.talk(), form.variants());
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        *///?}
    }

    private static void send(LivingEntity entity, Form form) {
        StateFormS2CPayload payload = new StateFormS2CPayload(entity.getId(), form.rig(), form.talk(), form.variants());
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToTrackingEntity(entity, payload);
        if (entity instanceof ServerPlayer self) com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(self, payload);
        *///?}
    }

    public static void forget(UUID entity) {
        CURRENT.remove(entity);
    }
}

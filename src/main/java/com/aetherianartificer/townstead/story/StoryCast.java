package com.aetherianartificer.townstead.story;

import com.aetherianartificer.townstead.persona.PersonaInstances;
import com.aetherianartificer.townstead.persona.Personas;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The other villagers in a story scene. A line tagged {@code # who: <role>} is said by the villager
 * that role names, found near the player the first time it is needed and kept for the rest of the
 * conversation. A role is a condition the story names, a Persona id (a bare path means the story's
 * own namespace), {@code visitor:<role>}, or a profession.
 */
final class StoryCast {
    /** How far from the player a villager can be and still join the scene. */
    private static final double RANGE = 12;
    /** Past this, a cast villager walks back toward the player. */
    private static final double HOLD_SQR = 4.5 * 4.5;
    /** Past this, a cast villager has left the scene and the role is found again. */
    private static final double LOST_SQR = 24 * 24;

    private final StorySession session;
    private final Map<String, VillagerEntityMCA> members = new HashMap<>();

    StoryCast(StorySession session) {
        this.session = session;
    }

    /** The villager who says a line with this {@code who} value, or null when nobody fits nearby. */
    @Nullable VillagerEntityMCA find(String role) {
        String key = role.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty() || key.equals("me")) return session.villager;
        VillagerEntityMCA member = members.get(key);
        if (member != null && member.isAlive() && member.level() == session.player.level()
                && member.distanceToSqr(session.player) <= LOST_SQR) return member;
        member = resolve(key);
        if (member == null) members.remove(key);
        else members.put(key, member);
        return member;
    }

    private @Nullable VillagerEntityMCA resolve(String role) {
        ServerPlayer player = session.player;
        Condition named = session.definition.conditions().get(role);
        if (named != null) return nearest(v -> named.test(new ConditionContext(v, player)));
        if (role.startsWith("visitor:")) {
            VillagerEntityMCA visitor = com.aetherianartificer.townstead.visitor.Visitors.near(
                    player, role.substring("visitor:".length()), RANGE);
            return visitor == session.villager ? null : visitor;
        }
        ResourceLocation persona = ResourceLocation.tryParse(role.contains(":") ? role
                : session.definition.id().getNamespace() + ":" + role);
        if (persona != null && Personas.byId(persona) != null) {
            PersonaInstances instances = PersonaInstances.get(player.server);
            PersonaInstances.Instance own = instances.of(session.villager.getUUID());
            if (own != null && own.persona().equals(persona)) return session.villager;
            return nearest(v -> {
                PersonaInstances.Instance instance = instances.of(v.getUUID());
                return instance != null && instance.persona().equals(persona);
            });
        }
        JsonObject json = new JsonObject();
        json.addProperty("type", "pheno:profession");
        json.addProperty("profession", role.contains(":") ? role : "minecraft:" + role);
        Condition profession = Conditions.parse(json);
        return profession == null ? null : nearest(v -> profession.test(new ConditionContext(v)));
    }

    private @Nullable VillagerEntityMCA nearest(Predicate<VillagerEntityMCA> test) {
        ServerPlayer player = session.player;
        return player.level().getEntitiesOfClass(VillagerEntityMCA.class, player.getBoundingBox().inflate(RANGE),
                        v -> v.isAlive() && v != session.villager && !members.containsValue(v) && test.test(v))
                .stream()
                .min(Comparator.comparingDouble(v -> v.distanceToSqr(player)))
                .orElse(null);
    }

    /**
     * Keeps the cast in the scene: each one stays near the player and faces whoever is talking. The
     * villager the player is talking to is held by MCA already.
     */
    void hold(LivingEntity speaker) {
        ServerPlayer player = session.player;
        for (VillagerEntityMCA member : members.values()) {
            if (!member.isAlive() || member.level() != player.level()) continue;
            if (member.distanceToSqr(player) > HOLD_SQR) {
                member.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(player, 0.5f, 3));
            } else {
                member.getNavigation().stop();
                member.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            }
            LivingEntity face = speaker == member ? player : speaker;
            member.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(face, true));
        }
    }

    /**
     * For a scene played in chat: everyone stops where they are and faces whoever is talking, and
     * the speaker faces whoever else is in the scene.
     */
    void holdInPlace(VillagerEntityMCA speaker) {
        java.util.List<VillagerEntityMCA> everyone = new java.util.ArrayList<>(members.values());
        everyone.add(session.villager);
        VillagerEntityMCA other = everyone.stream().filter(v -> v != speaker && v.isAlive()).findFirst().orElse(null);
        for (VillagerEntityMCA villager : everyone) {
            if (!villager.isAlive()) continue;
            villager.getNavigation().stop();
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            LivingEntity face = villager == speaker ? other : speaker;
            if (face != null) villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(face, true));
        }
    }

    void release() {
        session.villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        for (VillagerEntityMCA member : members.values()) {
            member.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            member.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        }
        members.clear();
    }
}

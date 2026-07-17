package com.aetherianartificer.townstead.chronicle.emit;

import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.calendar.WorldCalendarSavedData.VillageKey;
import com.aetherianartificer.townstead.chronicle.Chronicles;
import com.aetherianartificer.townstead.chronicle.model.ChronicleEvent;
import com.aetherianartificer.townstead.chronicle.model.ChronicleRef;
import com.aetherianartificer.townstead.chronicle.model.Participation;
import com.aetherianartificer.townstead.chronicle.model.VillageHistory;
import com.aetherianartificer.townstead.chronicle.store.ChronicleSavedData;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventRegistry;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate.TriggerKey;
import com.aetherianartificer.townstead.chronicle.template.ChronicleTriggerIndex;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.reaction.WeightedPicker;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Turns a semantic trigger into a recorded ground-truth event: candidate
 * lookup, eligibility, cooldown, weighted pick, witness gathering, counters,
 * digest entry, and generic pheno side effects. Impact application (mood,
 * sentiment, memories) belongs to the knowledge engine, which hooks in on
 * record.
 */
public final class ChronicleEmitter {

    private ChronicleEmitter() {}

    /** Tap entry point for single-subject events. No-match cost is one map miss. */
    public static OptionalLong emit(ServerLevel level, TriggerKey trigger, LivingEntity actor,
                                    float magnitude, Map<String, String> params) {
        return emit(level, trigger, actor, null, magnitude, params);
    }

    /** Tap entry point; {@code other} binds the template's second role when present. */
    public static OptionalLong emit(ServerLevel level, TriggerKey trigger, LivingEntity actor,
                                    @Nullable LivingEntity other, float magnitude,
                                    Map<String, String> params) {
        List<ChronicleEventTemplate> candidates = ChronicleTriggerIndex.candidates(trigger);
        if (candidates.isEmpty()) return OptionalLong.empty();
        MinecraftServer server = level.getServer();
        long today = TownsteadCalendar.worldDay(server);
        ChronicleSavedData data = ChronicleSavedData.get(server);

        List<ChronicleEventTemplate> eligible = new ArrayList<>(candidates.size());
        for (ChronicleEventTemplate template : candidates) {
            if (!template.contexts().contains(ChronicleEventTemplate.Context.LIVE)) continue;
            if (onCooldown(data, actor, template, today)) continue;
            ChronicleEventTemplate.RoleSpec primary = template.primaryRole();
            if (primary.when() != null && !primary.when().test(new ConditionContext(actor))) continue;
            if (template.roles().size() > 1 && other != null) {
                ChronicleEventTemplate.RoleSpec second = template.roles().get(1);
                if (second.when() != null && !second.when().test(new ConditionContext(other))) continue;
            }
            eligible.add(template);
        }
        if (eligible.isEmpty()) return OptionalLong.empty();

        Optional<ChronicleEventTemplate> picked =
                WeightedPicker.pick(eligible, ChronicleEventTemplate::pickWeight, level.random);
        return picked.map(t -> emitTemplate(level, t, actor, other, magnitude, params))
                .orElse(OptionalLong.empty());
    }

    /** Direct emission of a specific template (command/debug path skips cooldown). */
    public static OptionalLong emitTemplate(ServerLevel level, ChronicleEventTemplate template,
                                            LivingEntity actor, float magnitude,
                                            Map<String, String> extraParams) {
        return emitTemplate(level, template, actor, null, magnitude, extraParams);
    }

    public static OptionalLong emitTemplate(ServerLevel level, ChronicleEventTemplate template,
                                            LivingEntity actor, @Nullable LivingEntity other,
                                            float magnitude, Map<String, String> extraParams) {
        MinecraftServer server = level.getServer();
        long today = TownsteadCalendar.worldDay(server);
        ChronicleSavedData data = ChronicleSavedData.get(server);

        int villageId = resolveVillageId(actor);
        List<LivingEntity> witnesses = template.witnesses().max() > 0
                ? gatherWitnesses(level, actor, template.witnesses()) : List.of();

        String primaryRole = template.primaryRole().id();
        String secondRole = template.roles().size() > 1 ? template.roles().get(1).id() : null;
        List<Participation> participations = new ArrayList<>(witnesses.size() + 2);
        participations.add(new Participation(primaryRole, refFor(actor)));
        if (other != null && secondRole != null) {
            participations.add(new Participation(secondRole, refFor(other)));
        }
        for (LivingEntity witness : witnesses) {
            if (witness == other) continue;
            participations.add(new Participation(Participation.ROLE_WITNESS, refFor(witness)));
        }

        Map<String, String> params = new HashMap<>(extraParams);
        params.putIfAbsent(primaryRole, actor.getName().getString());
        if (other != null && secondRole != null) {
            params.putIfAbsent(secondRole, other.getName().getString());
        }

        ChronicleEvent draft = new ChronicleEvent(
                0L, template.id(), today, level.getGameTime(),
                level.dimension().location(), actor.blockPosition().asLong(),
                villageId, template.category(), magnitude, template.reach(),
                ChronicleEvent.NONE, ChronicleEvent.NONE, template.keep(),
                participations, params);
        long eventId = Chronicles.record(server, draft);

        for (ChronicleEventTemplate.CounterGrant grant : template.counters()) {
            if (grant.role().equals(primaryRole)) {
                data.addCounter(actor.getUUID(), grant.key(), grant.amount());
            } else if (Participation.ROLE_WITNESS.equals(grant.role())) {
                for (LivingEntity witness : witnesses) {
                    data.addCounter(witness.getUUID(), grant.key(), grant.amount());
                }
            }
        }

        if (template.cooldownDays() > 0) {
            data.putCounterRaw(actor.getUUID(), cooldownKey(template), (int) today);
        }

        if (template.keep() && villageId != ChronicleEvent.VILLAGE_NONE) {
            Chronicles.recordDigestEntry(server,
                    new VillageKey(level.dimension().location(), villageId),
                    new VillageHistory.Entry(today, eventId, template.id().toString(),
                            template.display().headlineLiteral(),
                            template.display().headlineLangKey(), params));
        }

        Action primaryEffect = template.effects().get(primaryRole);
        if (primaryEffect != null) {
            primaryEffect.run(new ActionContext(actor));
        }
        if (other != null && secondRole != null) {
            Action otherEffect = template.effects().get(secondRole);
            if (otherEffect != null) {
                otherEffect.run(new ActionContext(other, actor, other));
            }
        }
        Action witnessEffect = template.effects().get(Participation.ROLE_WITNESS);
        if (witnessEffect != null) {
            for (LivingEntity witness : witnesses) {
                witnessEffect.run(new ActionContext(witness, actor, witness));
            }
        }

        // Knowledge handoff: participants and witnesses learn first-hand,
        // which is where role/witness impacts (mood, sentiment, memories) apply.
        List<LivingEntity> knownBy = new ArrayList<>(witnesses.size() + 2);
        knownBy.add(actor);
        if (other != null) knownBy.add(other);
        knownBy.addAll(witnesses);
        com.aetherianartificer.townstead.chronicle.knowledge.AccountLedger.onRecorded(
                server, template, draft.withId(eventId), knownBy);
        return OptionalLong.of(eventId);
    }

    public static @Nullable ChronicleEventTemplate template(net.minecraft.resources.ResourceLocation id) {
        return ChronicleEventRegistry.byId(id);
    }

    private static boolean onCooldown(ChronicleSavedData data, LivingEntity actor,
                                      ChronicleEventTemplate template, long today) {
        if (template.cooldownDays() <= 0) return false;
        int last = data.counter(actor.getUUID(), cooldownKey(template));
        return last > 0 && today - last < template.cooldownDays();
    }

    private static String cooldownKey(ChronicleEventTemplate template) {
        return "cd:" + template.id();
    }

    private static List<LivingEntity> gatherWitnesses(ServerLevel level, LivingEntity actor,
                                                      ChronicleEventTemplate.WitnessSpec spec) {
        AABB box = actor.getBoundingBox().inflate(spec.radius());
        List<LivingEntity> witnesses = new ArrayList<>();
        for (VillagerEntityMCA villager : level.getEntitiesOfClass(VillagerEntityMCA.class, box)) {
            if (villager == actor) continue;
            witnesses.add(villager);
            if (witnesses.size() >= spec.max()) return witnesses;
        }
        for (ServerPlayer player : level.players()) {
            if (player == actor || !box.contains(player.position())) continue;
            witnesses.add(player);
            if (witnesses.size() >= spec.max()) break;
        }
        return witnesses;
    }

    private static ChronicleRef refFor(LivingEntity entity) {
        String name = entity.getName().getString();
        if (entity instanceof ServerPlayer player) {
            return ChronicleRef.player(player.getUUID(), name);
        }
        if (entity instanceof VillagerEntityMCA villager) {
            return ChronicleRef.villager(villager.getUUID(), name);
        }
        return ChronicleRef.animal(entity.getUUID(),
                entity.getType().builtInRegistryHolder().key().location().toString(), name);
    }

    public static int resolveVillageId(LivingEntity actor) {
        if (!(actor instanceof VillagerEntityMCA villager)) return ChronicleEvent.VILLAGE_NONE;
        try {
            Optional<Village> home = villager.getResidency().getHomeVillage();
            if (home.isPresent()) return home.get().getId();
            return Village.findNearest(villager).map(Village::getId)
                    .orElse(ChronicleEvent.VILLAGE_NONE);
        } catch (Throwable t) {
            return ChronicleEvent.VILLAGE_NONE;
        }
    }
}

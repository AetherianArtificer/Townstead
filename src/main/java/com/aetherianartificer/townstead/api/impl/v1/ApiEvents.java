package com.aetherianartificer.townstead.api.impl.v1;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.api.v1.event.BuildingEstablishedEvent;
import com.aetherianartificer.townstead.api.v1.event.BuildingRemovedEvent;
import com.aetherianartificer.townstead.api.v1.event.BuildingUpgradedEvent;
import com.aetherianartificer.townstead.api.v1.event.CalendarRolloverEvent;
import com.aetherianartificer.townstead.api.v1.event.ChronicleEventRecordedEvent;
import com.aetherianartificer.townstead.api.v1.event.ConversationHeldEvent;
import com.aetherianartificer.townstead.api.v1.event.HangoutEndedEvent;
import com.aetherianartificer.townstead.api.v1.event.HangoutStartedEvent;
import com.aetherianartificer.townstead.api.v1.event.VillagerProfessionChangedEvent;
import com.aetherianartificer.townstead.api.v1.event.VillagerRefueledEvent;
import com.aetherianartificer.townstead.api.v1.event.VillagerVillageChangedEvent;
import com.aetherianartificer.townstead.api.v1.event.WorkCompletedEvent;
import com.aetherianartificer.townstead.api.v1.event.WorksiteRegisteredEvent;
import com.aetherianartificer.townstead.api.v1.event.WorksiteRemovedEvent;
import com.aetherianartificer.townstead.hangout.HangoutVisit;
import com.aetherianartificer.townstead.work.site.Worksite;
import com.aetherianartificer.townstead.api.v1.event.DialogueClosedEvent;
import com.aetherianartificer.townstead.api.v1.event.DialogueOpenedEvent;
import com.aetherianartificer.townstead.api.v1.event.Subscription;
import com.aetherianartificer.townstead.api.v1.event.TownsteadEvent;
import com.aetherianartificer.townstead.api.v1.event.VillageNeedsBandChangedEvent;
import com.aetherianartificer.townstead.api.v1.event.VillageSpiritChangedEvent;
import com.aetherianartificer.townstead.api.v1.event.VillagerBornEvent;
import com.aetherianartificer.townstead.api.v1.event.VillagerCollapsedEvent;
import com.aetherianartificer.townstead.api.v1.event.VillagerCrisisEvent;
import com.aetherianartificer.townstead.api.v1.event.VillagerDiedEvent;
import com.aetherianartificer.townstead.api.v1.event.VillagerLifeStageChangedEvent;
import com.aetherianartificer.townstead.api.v1.event.VillagerMarriedEvent;
import com.aetherianartificer.townstead.api.v1.event.VillagerRecoveredEvent;
import com.aetherianartificer.townstead.api.v1.event.VillagerSkillForgottenEvent;
import com.aetherianartificer.townstead.api.v1.event.VillagerSkillLearnedEvent;
import com.aetherianartificer.townstead.api.v1.event.VillagerTierChangedEvent;
import com.aetherianartificer.townstead.api.v1.model.CalendarSnapshot;
import com.aetherianartificer.townstead.api.v1.model.ChronicleEventView;
import com.aetherianartificer.townstead.api.v1.model.NeedBand;
import com.aetherianartificer.townstead.api.v1.model.SpiritSnapshot;
import com.aetherianartificer.townstead.api.v1.model.VillageId;
import com.aetherianartificer.townstead.api.v1.model.VillageNeedsSummary;
import com.aetherianartificer.townstead.chronicle.model.ChronicleEvent;
import com.aetherianartificer.townstead.spirit.SpiritReadout;
import com.aetherianartificer.townstead.spirit.SpiritTotals;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The event registry and the one place Townstead's internals post from. Every {@code post} is
 * hardened: a listener that throws is logged once and skipped, and nothing here can break the
 * tick that called it. Events are mirrored onto the loader bus after the API listeners ran.
 */
public final class ApiEvents {

    private static final CopyOnWriteArrayList<Registration<?>> REGISTRATIONS = new CopyOnWriteArrayList<>();
    private static final Set<Registration<?>> REPORTED = new HashSet<>();

    private ApiEvents() {}

    static <E extends TownsteadEvent> Subscription subscribe(Class<E> type, Consumer<E> listener) {
        if (type == null || listener == null) throw new IllegalArgumentException("type and listener are required");
        Registration<E> registration = new Registration<>(type, listener);
        REGISTRATIONS.add(registration);
        return registration;
    }

    public static void post(TownsteadEvent event) {
        if (event == null) return;
        for (Registration<?> registration : REGISTRATIONS) {
            registration.deliver(event);
        }
        TownsteadBusEvent.mirror(event);
    }

    private static void report(Registration<?> registration, Throwable t) {
        boolean first;
        synchronized (REPORTED) {
            first = REPORTED.add(registration);
        }
        if (first) {
            Townstead.LOGGER.warn("[TownsteadApi] listener for {} threw; further failures from it are logged at debug",
                    registration.type.getSimpleName(), t);
        } else {
            Townstead.LOGGER.debug("[TownsteadApi] listener for {} threw again: {}",
                    registration.type.getSimpleName(), t.toString());
        }
    }

    private static final class Registration<E extends TownsteadEvent> implements Subscription {
        private final Class<E> type;
        private final Consumer<E> listener;
        private volatile boolean active = true;

        Registration(Class<E> type, Consumer<E> listener) {
            this.type = type;
            this.listener = listener;
        }

        void deliver(TownsteadEvent event) {
            if (!active || !type.isInstance(event)) return;
            try {
                listener.accept(type.cast(event));
            } catch (Throwable t) {
                report(this, t);
            }
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public void close() {
            active = false;
            REGISTRATIONS.remove(this);
        }
    }

    // ---- posting helpers for Townstead's own hook points; each is a no-throw one-liner ----

    public static void collapsed(LivingEntity villager, int energy) {
        safe(() -> post(new VillagerCollapsedEvent(villager, villager.getUUID(), energy)));
    }

    public static void recovered(LivingEntity villager, int energy) {
        safe(() -> post(new VillagerRecoveredEvent(villager, villager.getUUID(), energy)));
    }

    public static void crisis(LivingEntity villager, String kind) {
        safe(() -> post(new VillagerCrisisEvent(villager, villager.getUUID(), kind)));
    }

    public static void tierChanged(LivingEntity worker, ResourceLocation professionId, int before, int after, int applied) {
        safe(() -> post(new VillagerTierChangedEvent(worker, worker.getUUID(), professionId.toString(), before, after, applied)));
    }

    public static void skillLearned(LivingEntity entity, ResourceLocation skillId, boolean forced) {
        safe(() -> post(new VillagerSkillLearnedEvent(entity, entity.getUUID(), skillId, forced)));
    }

    public static void skillForgotten(LivingEntity entity, ResourceLocation skillId, Set<ResourceLocation> removed,
                                      boolean forced) {
        safe(() -> post(new VillagerSkillForgottenEvent(entity, entity.getUUID(), skillId, removed, forced)));
    }

    public static void born(LivingEntity baby) {
        safe(() -> post(new VillagerBornEvent(baby, baby.getUUID())));
    }

    public static void died(LivingEntity villager, String cause) {
        safe(() -> post(new VillagerDiedEvent(villager, villager.getUUID(), cause == null ? "" : cause)));
    }

    public static void married(LivingEntity partner, @Nullable LivingEntity spouse) {
        safe(() -> post(new VillagerMarriedEvent(partner, partner.getUUID(),
                Optional.ofNullable(spouse).map(LivingEntity::getUUID))));
    }

    public static void lifeStageChanged(LivingEntity villager, String before, String after, boolean senior) {
        safe(() -> post(new VillagerLifeStageChangedEvent(villager, villager.getUUID(), before, after, senior)));
    }

    public static void buildingEstablished(ServerLevel level, Village village, int buildingId, String type) {
        safe(() -> post(new BuildingEstablishedEvent(level, ApiSupport.villageId(level, village), buildingId,
                type == null ? "" : type, VillagesImpl.family(type), VillagesImpl.tier(type))));
    }

    public static void buildingUpgraded(ServerLevel level, Village village, int buildingId, String before, String after) {
        safe(() -> post(new BuildingUpgradedEvent(level, ApiSupport.villageId(level, village), buildingId,
                before == null ? "" : before, after == null ? "" : after, VillagesImpl.tier(before), VillagesImpl.tier(after))));
    }

    public static void buildingRemoved(ServerLevel level, Village village, int buildingId, String type) {
        safe(() -> post(new BuildingRemovedEvent(level, ApiSupport.villageId(level, village), buildingId,
                type == null ? "" : type)));
    }

    public static void spiritChanged(ServerLevel level, Village village, SpiritTotals beforeTotals, SpiritReadout before,
                                     SpiritTotals afterTotals, SpiritReadout after) {
        safe(() -> {
            VillageId id = ApiSupport.villageId(level, village);
            SpiritSnapshot prev = VillagesImpl.spirit(id, beforeTotals == null ? SpiritTotals.empty() : beforeTotals, before);
            SpiritSnapshot next = VillagesImpl.spirit(id, afterTotals, after);
            post(new VillageSpiritChangedEvent(level, id, prev, next));
        });
    }

    public static void needsBandChanged(MinecraftServer server, VillageId village, String needId, NeedBand before,
                                        NeedBand after, VillageNeedsSummary summary) {
        safe(() -> post(new VillageNeedsBandChangedEvent(server, village, needId, before, after, summary)));
    }

    public static void calendarRollover(MinecraftServer server, List<CalendarRolloverEvent.Kind> kinds,
                                        CalendarSnapshot before, CalendarSnapshot after, int daysAdvanced) {
        safe(() -> {
            for (CalendarRolloverEvent.Kind kind : kinds) {
                post(new CalendarRolloverEvent(server, kind, before, after, daysAdvanced));
            }
        });
    }

    public static void chronicleRecorded(MinecraftServer server, ChronicleEvent event) {
        safe(() -> {
            ChronicleEventView view = ChroniclesImpl.view(event);
            post(new ChronicleEventRecordedEvent(server, view));
        });
    }

    public static void dialogueOpened(LivingEntity villager, ServerPlayer player) {
        safe(() -> post(new DialogueOpenedEvent(villager, villager.getUUID(), player)));
    }

    public static void dialogueClosed(LivingEntity villager, ServerPlayer player, int heartDelta) {
        safe(() -> post(new DialogueClosedEvent(villager, villager.getUUID(), player, heartDelta)));
    }

    public static void workCompleted(LivingEntity worker, ResourceLocation professionId, String verb,
                                     @Nullable ResourceLocation objectId, float magnitude, int appliedXp,
                                     int tierBefore, int tierAfter) {
        safe(() -> post(new WorkCompletedEvent(worker, worker.getUUID(), professionId.toString(), verb == null ? "" : verb,
                Optional.ofNullable(objectId), magnitude, appliedXp, tierBefore, tierAfter)));
    }

    public static void professionChanged(LivingEntity villager, String before, String after) {
        safe(() -> post(new VillagerProfessionChangedEvent(villager, villager.getUUID(), before == null ? "" : before,
                after == null ? "" : after)));
    }

    public static void refueled(LivingEntity villager, ResourceLocation item, int hungerBefore, int hungerAfter,
                                int thirstBefore, int thirstAfter, int fatigueBefore, int fatigueAfter) {
        safe(() -> post(new VillagerRefueledEvent(villager, villager.getUUID(), item, hungerBefore, hungerAfter,
                thirstBefore, thirstAfter, NeedScales.energyOf(fatigueBefore), NeedScales.energyOf(fatigueAfter))));
    }

    public static void conversationHeld(LivingEntity initiator, LivingEntity responder, ResourceLocation topic,
                                        String outcome) {
        safe(() -> post(new ConversationHeldEvent(initiator, initiator.getUUID(), responder, responder.getUUID(), topic,
                outcome == null ? "" : outcome)));
    }

    public static void villageChanged(UUID uuid, String name, @Nullable VillageId before, VillageId after) {
        safe(() -> post(new VillagerVillageChangedEvent(uuid, name == null ? "" : name, Optional.ofNullable(before), after)));
    }

    public static void hangoutStarted(LivingEntity villager, HangoutVisit visit) {
        safe(() -> post(new HangoutStartedEvent(villager, villager.getUUID(), HangoutsImpl.snapshot(visit))));
    }

    public static void hangoutEnded(@Nullable LivingEntity villager, UUID uuid, HangoutVisit visit, boolean success) {
        safe(() -> post(new HangoutEndedEvent(Optional.ofNullable(villager), uuid, HangoutsImpl.snapshot(visit), success)));
    }

    public static void worksiteRegistered(Worksite site) {
        safe(() -> post(new WorksiteRegisteredEvent(ApiSupport.currentServer(), WorkImpl.snapshot(site))));
    }

    public static void worksiteRemoved(Worksite site) {
        safe(() -> post(new WorksiteRemovedEvent(ApiSupport.currentServer(), WorkImpl.snapshot(site))));
    }

    /** True when anyone is listening at all; internals may skip expensive payload building otherwise. */
    public static boolean anyListeners() {
        return !REGISTRATIONS.isEmpty() || TownsteadBusEvent.mirrored();
    }

    private static void safe(Runnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            Townstead.LOGGER.debug("[TownsteadApi] event post failed: {}", t.toString());
        }
    }

    static UUID uuidOf(@Nullable LivingEntity entity) {
        return entity == null ? null : entity.getUUID();
    }
}

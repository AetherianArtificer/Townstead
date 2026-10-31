package com.aetherianartificer.townstead.story.goal;

import com.aetherianartificer.townstead.api.v1.event.TownsteadEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Townstead's public events by id, for goals that count them. The id is the record's name in
 * snake case without {@code Event}: {@code WorkCompletedEvent} is {@code townstead:work_completed}.
 */
public final class GoalEvents {
    private GoalEvents() {}

    private static final List<Class<? extends TownsteadEvent>> TYPES = List.of(
            com.aetherianartificer.townstead.api.v1.event.BondChangedEvent.class,
            com.aetherianartificer.townstead.api.v1.event.BuildingEstablishedEvent.class,
            com.aetherianartificer.townstead.api.v1.event.BuildingRemovedEvent.class,
            com.aetherianartificer.townstead.api.v1.event.BuildingUpgradedEvent.class,
            com.aetherianartificer.townstead.api.v1.event.CalendarRolloverEvent.class,
            com.aetherianartificer.townstead.api.v1.event.ChronicleEventRecordedEvent.class,
            com.aetherianartificer.townstead.api.v1.event.ConversationHeldEvent.class,
            com.aetherianartificer.townstead.api.v1.event.DialogueClosedEvent.class,
            com.aetherianartificer.townstead.api.v1.event.DialogueOpenedEvent.class,
            com.aetherianartificer.townstead.api.v1.event.FactionFoundedEvent.class,
            com.aetherianartificer.townstead.api.v1.event.FactionIdentityChangedEvent.class,
            com.aetherianartificer.townstead.api.v1.event.FactionKindChangedEvent.class,
            com.aetherianartificer.townstead.api.v1.event.FactionStatusChangedEvent.class,
            com.aetherianartificer.townstead.api.v1.event.HangoutEndedEvent.class,
            com.aetherianartificer.townstead.api.v1.event.HangoutStartedEvent.class,
            com.aetherianartificer.townstead.api.v1.event.LegitimacyChangedEvent.class,
            com.aetherianartificer.townstead.api.v1.event.SeatDamagedEvent.class,
            com.aetherianartificer.townstead.api.v1.event.SeatDesignatedEvent.class,
            com.aetherianartificer.townstead.api.v1.event.SeatLostEvent.class,
            com.aetherianartificer.townstead.api.v1.event.SeatMovedEvent.class,
            com.aetherianartificer.townstead.api.v1.event.SeatRepairedEvent.class,
            com.aetherianartificer.townstead.api.v1.event.SettlementFactionChangedEvent.class,
            com.aetherianartificer.townstead.api.v1.event.SettlementFoundedEvent.class,
            com.aetherianartificer.townstead.api.v1.event.VillageNeedsBandChangedEvent.class,
            com.aetherianartificer.townstead.api.v1.event.VillageSpiritChangedEvent.class,
            com.aetherianartificer.townstead.api.v1.event.VillagerBornEvent.class,
            com.aetherianartificer.townstead.api.v1.event.VillagerCollapsedEvent.class,
            com.aetherianartificer.townstead.api.v1.event.VillagerCrisisEvent.class,
            com.aetherianartificer.townstead.api.v1.event.VillagerDiedEvent.class,
            com.aetherianartificer.townstead.api.v1.event.VillagerLifeStageChangedEvent.class,
            com.aetherianartificer.townstead.api.v1.event.VillagerMarriedEvent.class,
            com.aetherianartificer.townstead.api.v1.event.VillagerProfessionChangedEvent.class,
            com.aetherianartificer.townstead.api.v1.event.VillagerRecoveredEvent.class,
            com.aetherianartificer.townstead.api.v1.event.VillagerRefueledEvent.class,
            com.aetherianartificer.townstead.api.v1.event.VillagerRootChangedEvent.class,
            com.aetherianartificer.townstead.api.v1.event.VillagerSkillForgottenEvent.class,
            com.aetherianartificer.townstead.api.v1.event.VillagerSkillLearnedEvent.class,
            com.aetherianartificer.townstead.api.v1.event.VillagerTierChangedEvent.class,
            com.aetherianartificer.townstead.api.v1.event.VillagerVillageChangedEvent.class,
            com.aetherianartificer.townstead.api.v1.event.WorkCompletedEvent.class,
            com.aetherianartificer.townstead.api.v1.event.WorksiteRegisteredEvent.class,
            com.aetherianartificer.townstead.api.v1.event.WorksiteRemovedEvent.class,
            com.aetherianartificer.townstead.api.v1.event.WorldSettingsChangedEvent.class);

    private static final Map<String, Class<? extends TownsteadEvent>> BY_ID = new TreeMap<>();

    static {
        for (Class<? extends TownsteadEvent> type : TYPES) BY_ID.put(id(type), type);
    }

    public static String id(Class<?> type) {
        String name = type.getSimpleName().replaceFirst("Event$", "");
        return "townstead:" + name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    public static @Nullable Class<? extends TownsteadEvent> byId(String id) {
        return BY_ID.get(id.contains(":") ? id : "townstead:" + id);
    }

    public static Iterable<String> ids() {
        return BY_ID.keySet();
    }
}

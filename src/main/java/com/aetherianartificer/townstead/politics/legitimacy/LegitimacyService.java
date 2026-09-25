package com.aetherianartificer.townstead.politics.legitimacy;

import com.aetherianartificer.townstead.api.impl.v1.ApiEvents;
import com.aetherianartificer.townstead.api.v1.TownsteadApiV1;
import com.aetherianartificer.townstead.api.v1.event.CalendarRolloverEvent;
import com.aetherianartificer.townstead.api.v1.event.LegitimacyChangedEvent;
import com.aetherianartificer.townstead.chronicle.store.ChronicleSavedData;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.aetherianartificer.townstead.politics.definition.GovernanceDefinition;
import com.aetherianartificer.townstead.politics.definition.OrganizationKindDefinition;
import com.aetherianartificer.townstead.politics.definition.PoliticalDefinitions;
import com.aetherianartificer.townstead.politics.state.MembershipInstance;
import com.aetherianartificer.townstead.politics.state.OrganizationInstance;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.PoliticalStatus;
import com.aetherianartificer.townstead.politics.state.PolityInstance;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Legitimacy: how much the governed accept their government, 0 to 100. Each day it moves part of
 * the way toward a target, the form's base plus its weighted sources (prosperity, the village
 * spirit, the leader's standing). It colors every resident's mood, and a change of band is
 * announced to members and posted as an event.
 */
public final class LegitimacyService {
    /** How far legitimacy moves toward its target each day. */
    private static final double DAILY_STEP = 0.2;
    /** Each resident's daily mood impact at 0 or 100 legitimacy; the Chronicle mood term decays it. */
    private static final float MOOD_AT_EXTREME = 1.5f;

    private static boolean registered;

    public record Contribution(ResourceLocation label, double amount) {}

    public record Target(double value, List<Contribution> contributions) {}

    private LegitimacyService() {}

    public static synchronized void init() {
        if (registered) return;
        registered = true;
        TownsteadApiV1.get().events().subscribe(CalendarRolloverEvent.class, e -> {
            if (e.kind() == CalendarRolloverEvent.Kind.DAY) daily(e.server());
        });
    }

    /** The stored value, or the form's base before the first daily update. */
    public static double current(PoliticalSavedData data, OrganizationInstance government) {
        Double stored = data.legitimacy(government.id());
        if (stored != null) return stored;
        GovernanceDefinition governance = governance(government);
        return governance == null ? 50.0 : governance.legitimacy().base();
    }

    public static @Nullable GovernanceDefinition governance(OrganizationInstance government) {
        OrganizationKindDefinition kind = PoliticalDefinitions.snapshot().organizationKind(government.kind());
        return kind == null ? null : kind.governance();
    }

    /** Where legitimacy is heading, and why: the base plus each source's weighted contribution. */
    public static Target target(MinecraftServer server, PolityInstance polity, OrganizationInstance government) {
        GovernanceDefinition governance = governance(government);
        if (governance == null) return new Target(50.0, List.of());
        SelectorContext context = context(server, polity, government, governance);
        List<Contribution> contributions = new ArrayList<>();
        double value = governance.legitimacy().base();
        if (context != null) {
            for (GovernanceDefinition.Source source : governance.legitimacy().sources()) {
                double raw;
                try {
                    raw = source.value().get(context);
                } catch (RuntimeException error) {
                    continue;
                }
                if (!Double.isFinite(raw)) continue;
                double amount = raw * source.weight();
                contributions.add(new Contribution(source.label(), amount));
                value += amount;
            }
        }
        return new Target(Math.max(0, Math.min(100, value)), List.copyOf(contributions));
    }

    public static String band(double value) {
        if (value < 20) return "resented";
        if (value < 40) return "uneasy";
        if (value < 60) return "tolerated";
        if (value < 80) return "accepted";
        return "beloved";
    }

    static void daily(MinecraftServer server) {
        PoliticalSavedData data = PoliticalSavedData.get(server);
        for (PolityInstance polity : data.polities()) {
            if (polity.status() == PoliticalStatus.Polity.DISSOLVED || polity.governmentOrganization() == null) continue;
            OrganizationInstance government = data.organization(polity.governmentOrganization());
            if (government == null || governance(government) == null) continue;
            double before = current(data, government);
            double target = target(server, polity, government).value();
            double after = Math.round((before + (target - before) * DAILY_STEP) * 10.0) / 10.0;
            data.setLegitimacy(government.id(), after);
            String bandBefore = band(before), bandAfter = band(after);
            if (!bandBefore.equals(bandAfter)) {
                ApiEvents.post(new LegitimacyChangedEvent(server, polity.id(), government.id(), bandBefore, bandAfter,
                        (int) Math.round(after)));
                notifyMembers(server, data, government,
                        Component.translatable("townstead.legitimacy.notice." + bandAfter, polity.name()));
            }
            colorMood(server, polity, after);
        }
    }

    /** Every resident's mood leans toward how accepted their government is. */
    private static void colorMood(MinecraftServer server, PolityInstance polity, double legitimacy) {
        float impact = (float) ((legitimacy - 50.0) / 50.0) * MOOD_AT_EXTREME;
        if (Math.abs(impact) < 0.05f) return;
        ChronicleSavedData chronicle = ChronicleSavedData.get(server);
        for (SettlementRef settlement : polity.settlements()) {
            Village village = village(server, settlement);
            if (village == null) continue;
            village.getResidentsUUIDs().forEach(resident -> chronicle.addMoodImpact(resident, impact));
        }
    }

    /** Sources read the polity's first settlement, and the leader when they are in the world. */
    private static @Nullable SelectorContext context(MinecraftServer server, PolityInstance polity,
                                                     OrganizationInstance government, GovernanceDefinition governance) {
        if (polity.settlements().isEmpty()) return null;
        SettlementRef settlement = polity.settlements().get(0);
        ServerLevel level = level(server, settlement);
        Village village = level == null ? null : VillageManager.get(level).getOrEmpty(settlement.villageId()).orElse(null);
        if (village == null) return null;
        LivingEntity head = null;
        UUID holder = holder(PoliticalSavedData.get(server), government, governance.head());
        if (holder != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(holder);
            Entity entity = player != null ? player : level.getEntity(holder);
            if (entity instanceof LivingEntity living) head = living;
        }
        return SelectorContext.ofBlock(level, new BlockPos(village.getCenter()), head);
    }

    public static @Nullable UUID holder(PoliticalSavedData data, OrganizationInstance government, ResourceLocation office) {
        for (MembershipInstance membership : data.memberships(government.actor())) {
            if (membership.affiliation().active() && membership.roles().contains(office)) return membership.affiliation().person();
        }
        return null;
    }

    private static void notifyMembers(MinecraftServer server, PoliticalSavedData data, OrganizationInstance government,
                                      Component message) {
        for (MembershipInstance membership : data.memberships(government.actor())) {
            if (!membership.affiliation().active()) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(membership.affiliation().person());
            if (player != null) player.displayClientMessage(message, false);
        }
    }

    private static @Nullable ServerLevel level(MinecraftServer server, SettlementRef settlement) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, settlement.dimension()));
    }

    private static @Nullable Village village(MinecraftServer server, SettlementRef settlement) {
        ServerLevel level = level(server, settlement);
        return level == null ? null : VillageManager.get(level).getOrEmpty(settlement.villageId()).orElse(null);
    }
}

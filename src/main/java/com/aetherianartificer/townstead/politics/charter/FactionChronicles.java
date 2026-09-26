package com.aetherianartificer.townstead.politics.charter;

import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.calendar.WorldCalendarSavedData.VillageKey;
import com.aetherianartificer.townstead.chronicle.Chronicles;
import com.aetherianartificer.townstead.chronicle.model.*;
import com.aetherianartificer.townstead.politics.state.*;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Capture subjects before dissolution ends their relationships; archive only after it succeeds. */
public final class FactionChronicles {
    private FactionChronicles() {}
    public static List<ChronicleEvent> dissolution(ServerPlayer actor, PolityInstance polity, CharterSavedData.Amendment amendment) {
        var server = actor.server; var data = PoliticalSavedData.get(server);
        var template = ResourceLocation.tryParse("townstead:faction_dissolved");
        var out = new ArrayList<ChronicleEvent>();
        for (var settlement : polity.settlements()) {
            var level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, settlement.dimension()));
            var village = level == null ? null : VillageManager.get(level).getOrEmpty(settlement.villageId()).orElse(null);
            String villageName = village == null ? Integer.toString(settlement.villageId()) : village.getName();
            var participants = new ArrayList<Participation>();
            participants.add(new Participation("leader", ChronicleRef.player(actor.getUUID(), actor.getGameProfile().getName())));
            participants.add(new Participation("faction", ChronicleRef.concept("polity:" + polity.id(), polity.name())));
            participants.add(new Participation("settlement", ChronicleRef.village(settlement.villageId(), villageName)));
            var residents = new LinkedHashSet<UUID>();
            if (village != null) residents.addAll(village.getResidentsUUIDs().toList());
            var people = new LinkedHashMap<UUID, String>();
            for (var resident : residents) if (resident != null) people.put(resident, "resident");
            for (var a : data.directAffiliations()) if (a.active() && a.actor().equals(polity.actor())) people.putIfAbsent(a.person(), "affiliate");
            for (var org : data.organizations()) if (org.id().equals(polity.governmentOrganization()) || settlement.equals(org.home())) {
                if (org.status() != PoliticalStatus.Organization.ACTIVE) continue;
                participants.add(new Participation("organization", ChronicleRef.concept("organization:" + org.id(), org.name())));
                for (var member : data.memberships(org.actor())) if (member.affiliation().active()) people.putIfAbsent(member.affiliation().person(), "member");
            }
            for (var request : CharterRequests.get(server).entries()) if (request.open() && request.organization().equals(String.valueOf(polity.governmentOrganization())))
                people.putIfAbsent(request.person(), "applicant");
            people.remove(actor.getUUID());
            for (var person : people.entrySet()) {
                var player = server.getPlayerList().getPlayer(person.getKey());
                var entity = level == null ? null : level.getEntity(person.getKey());
                var profile = server.getProfileCache() == null ? null : server.getProfileCache().get(person.getKey()).orElse(null);
                String name = player != null ? player.getGameProfile().getName() : entity != null ? entity.getDisplayName().getString() : profile != null ? profile.getName() : person.getKey().toString();
                participants.add(new Participation(person.getValue(), (residents.contains(person.getKey()) || entity instanceof net.conczin.mca.entity.VillagerEntityMCA)
                        ? ChronicleRef.villager(person.getKey(), name) : player != null || profile != null ? ChronicleRef.player(person.getKey(), name)
                        : new ChronicleRef(ChronicleRef.Kind.CONCEPT, person.getKey(), 0, 0, "person:" + person.getKey(), name)));
            }
            for (var bell : CharterSavedData.get(server).bindings()) if (bell.polity().equals(polity.id()) && bell.settlement().equals(settlement))
                participants.add(new Participation("charter", ChronicleRef.concept("charter:" + bell.dimension() + "/" + bell.lectern().asLong(), "Charter Bell")));
            out.add(new ChronicleEvent(0, template, TownsteadCalendar.worldDay(server), server.overworld().getGameTime(),
                    settlement.dimension(), settlement.dimension().equals(amendment.dimension()) ? amendment.bell().asLong() : 0,
                    settlement.villageId(), "politics.dissolution", 1, ChronicleEvent.REACH_WORLD,
                    ChronicleEvent.NONE, ChronicleEvent.NONE, true, participants,
                    Map.of("faction", polity.name(), "leader", actor.getGameProfile().getName(), "settlement", villageName)));
        }
        return List.copyOf(out);
    }
    public static void record(ServerPlayer actor, List<ChronicleEvent> events) {
        long cause = ChronicleEvent.NONE;
        for (var e : events) {
            var draft = new ChronicleEvent(0, e.templateId(), e.worldDay(), e.gameTime(), e.dimension(), e.packedPos(), e.villageId(), e.category(), e.magnitude(), e.reach(), cause, e.arcId(), e.keep(), e.participations(), e.params());
            long id = Chronicles.record(actor.server, draft);
            if (cause == ChronicleEvent.NONE) {
                cause = id;
                var template = com.aetherianartificer.townstead.chronicle.template.ChronicleEventRegistry.byId(e.templateId());
                if (template != null) com.aetherianartificer.townstead.chronicle.knowledge.AccountLedger.onRecorded(
                        actor.server, template, draft.withId(id), java.util.List.of(actor));
            }
            Chronicles.recordDigestEntry(actor.server, new VillageKey(e.dimension(), e.villageId()),
                    new VillageHistory.Entry(e.worldDay(), id, e.templateId().toString(), "", "chronicle.townstead.faction_dissolved", e.params()));
        }
    }
}

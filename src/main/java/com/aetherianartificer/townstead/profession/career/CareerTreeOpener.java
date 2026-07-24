package com.aetherianartificer.townstead.profession.career;

import com.aetherianartificer.townstead.profession.ProfessionSlotRules;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Server-side entry points for the Career screen. The diegetic path is the "Careers"
 * conversation option on the Archives' Scribe, available during their scheduled work
 * hours; the server re-validates every request before rendering and sending the tree.
 */
public final class CareerTreeOpener {

    private static final String SCRIBE_ID = "townstead:scribe";
    private static final double CONSULT_RANGE = 8.0;

    private CareerTreeOpener() {}

    public static boolean isScribe(VillagerEntityMCA villager) {
        return SCRIBE_ID.equals(ProfessionSlotRules.professionKey(
                villager.getVillagerData().getProfession()));
    }

    /** Office hours are the counsellor's own schedule saying WORK, via the shared resolver. */
    public static boolean isOnDuty(VillagerEntityMCA villager) {
        if (villager.level().isClientSide) {
            return com.aetherianartificer.townstead.shift.VillagerSchedules.clientMirrorActivity(
                    villager.getUUID(), villager.level().getDayTime())
                    == net.minecraft.world.entity.schedule.Activity.WORK;
        }
        return com.aetherianartificer.townstead.shift.VillagerSchedules.isWorking(villager);
    }

    /**
     * "Take up this work": declare a new primary vocation. Authorized inside an Archives or
     * near an on-duty Scribe; eligible for root careers and acquired specializations; limited
     * to one change per day. The declaration is a truth event, so it enters village history.
     */
    public static void handleVocation(ServerPlayer player, String careerIdRaw) {
        net.minecraft.resources.ResourceLocation parsed =
                net.minecraft.resources.ResourceLocation.tryParse(careerIdRaw);
        net.minecraft.resources.ResourceLocation careerId =
                com.aetherianartificer.townstead.profession.def.ProfessionDefs.canonicalId(parsed);
        com.aetherianartificer.townstead.profession.def.ProfessionDef def =
                com.aetherianartificer.townstead.profession.def.ProfessionDefs.byId(careerId);
        CareerProfile profile = CareerProfiles.of(player);
        if (def == null || profile == null || careerId.equals(profile.primaryVocation())) return;
        if (!def.isRoot() && !profile.acquiredCareers().contains(careerId)) return;

        boolean authorized = com.aetherianartificer.townstead.village.ArchivesBuilding
                .villageIfInside(player, player.blockPosition()).isPresent()
                || nearOnDutyScribe(player);
        if (!authorized) {
            player.displayClientMessage(
                    Component.translatable("townstead.career.vocation.no_archives"), false);
            return;
        }
        long today = player.serverLevel().getDayTime() / 24000L;
        if (profile.primaryVocation() != null && profile.lastVocationChangeDay() == today) {
            player.displayClientMessage(
                    Component.translatable("townstead.career.vocation.daily_limit"), false);
            return;
        }
        PlayerCareers.mutate(player, stored -> {
            stored.setPrimaryVocation(careerId);
            stored.setLastVocationChangeDay(today);
        });
        com.aetherianartificer.townstead.chronicle.emit.ChronicleTaps.work(
                player, "townstead:took_up_work", null, null, 1f,
                java.util.Map.of("career", careerId.toString()));
        player.playNotifySound(net.minecraft.sounds.SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 0.9f);
        player.displayClientMessage(Component.translatable(
                "townstead.career.vocation.taken", def.displayName()), false);
        send(player);
    }

    private static boolean nearOnDutyScribe(ServerPlayer player) {
        return !player.serverLevel().getEntitiesOfClass(VillagerEntityMCA.class,
                player.getBoundingBox().inflate(CONSULT_RANGE),
                villager -> villager.isAlive() && isScribe(villager) && isOnDuty(villager)).isEmpty();
    }

    /** The "Careers" conversation option: validate the Scribe consult, then answer. */
    public static void handleRequest(ServerPlayer player, int villagerId) {
        Entity entity = player.serverLevel().getEntity(villagerId);
        if (!(entity instanceof VillagerEntityMCA villager)
                || !villager.isAlive()
                || player.distanceTo(villager) > CONSULT_RANGE
                || !isScribe(villager)) {
            return;
        }
        if (!isOnDuty(villager)) {
            player.displayClientMessage(
                    Component.translatable("townstead.career.scribe.off_duty"), false);
            return;
        }
        send(player);
    }

    /** The screen's Learn/Equip click: act, then answer with feedback and a fresh record. */
    public static void handleChoose(ServerPlayer player, String skillIdRaw) {
        com.aetherianartificer.townstead.profession.skill.LearnedSkills.Result result =
                com.aetherianartificer.townstead.profession.career.CareerChoices.chooseFromAcquired(
                        player, net.minecraft.resources.ResourceLocation.tryParse(skillIdRaw));
        if (result.ok()) {
            player.playNotifySound(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.0f);
        } else if (result.error() != null) {
            player.displayClientMessage(Component.translatable(
                    "townstead.career.learn.blocked", result.error()), false);
        }
        send(player);
    }

    /** Toggle goal tracking for a specialization; tracked goals notify when within reach. */
    public static void handleTrack(ServerPlayer player, String careerIdRaw) {
        net.minecraft.resources.ResourceLocation careerId =
                com.aetherianartificer.townstead.profession.def.ProfessionDefs.canonicalId(
                        net.minecraft.resources.ResourceLocation.tryParse(careerIdRaw));
        com.aetherianartificer.townstead.profession.def.ProfessionDef def =
                com.aetherianartificer.townstead.profession.def.ProfessionDefs.byId(careerId);
        if (def == null || def.isRoot()) return;
        CareerProfile profile = CareerProfiles.of(player);
        if (profile == null || profile.acquiredCareers().contains(careerId)) return;
        PlayerCareers.mutate(player, stored -> {
            if (!stored.untrack(careerId)) stored.track(careerId);
        });
        send(player);
    }

    public static void send(ServerPlayer player) {
        send(player, player, false);
    }

    /**
     * Renders and sends the registry record of {@code target} to {@code viewer}. Chronicle
     * moments come from an async archive read; everything composes back on the server thread.
     * {@code inspect} marks somebody else's record: the client hides its action buttons, and
     * the server-side choose/vocation handlers only ever act on the sender regardless.
     */
    public static void send(ServerPlayer viewer, net.minecraft.world.entity.LivingEntity target,
                            boolean inspect) {
        net.minecraft.server.MinecraftServer server = viewer.getServer();
        if (server == null) return;
        com.aetherianartificer.townstead.chronicle.Chronicles.bySubject(target.getUUID(), 0L, 64)
                .thenAccept(events -> server.execute(() -> {
                    java.util.Map<String, java.util.List<String>> moments = momentsFor(server, events);
                    CareerGraphS2CPayload payload = new CareerGraphS2CPayload(
                            titleFor(viewer, target), scribeNameFor(viewer), inspect,
                            CareerGraphBuilder.build(server, target, moments));
                    //? if neoforge {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(viewer, payload);
                    //?} else {
                    /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(viewer, payload);
                    *///?}
                }));
    }

    /** A player's record carries their MCA persona name, not their account name. */
    private static String titleFor(ServerPlayer viewer, net.minecraft.world.entity.LivingEntity target) {
        if (target != viewer) return target.getDisplayName().getString();
        try {
            String name = net.conczin.mca.server.world.data.PlayerSaveData.get(viewer)
                    .getFamilyEntry().getName();
            if (name != null && !name.isBlank()) return name;
        } catch (Exception ignored) {
        }
        return viewer.getName().getString();
    }

    /** The village Scribe's name for the page signature, empty when the office is unstaffed. */
    private static String scribeNameFor(ServerPlayer viewer) {
        java.util.Optional<net.conczin.mca.server.world.data.Village> village =
                net.conczin.mca.server.world.data.Village.findNearest(viewer);
        if (village.isEmpty() || !village.get().isWithinBorder(viewer)) return "";
        for (VillagerEntityMCA resident : village.get().getResidents(viewer.serverLevel())) {
            if (resident.isAlive() && isScribe(resident)) {
                return resident.getName().getString();
            }
        }
        return "";
    }

    /**
     * Chronicle moments per career: the subject's archived events whose template trigger verb
     * is one of a career's history counters, rendered as dated headlines, newest first.
     */
    private static java.util.Map<String, java.util.List<String>> momentsFor(
            net.minecraft.server.MinecraftServer server,
            java.util.List<com.aetherianartificer.townstead.chronicle.model.ChronicleEvent> events) {
        java.util.Map<String, java.util.List<String>> byVerb = new java.util.HashMap<>();
        for (com.aetherianartificer.townstead.profession.def.ProfessionDef def
                : com.aetherianartificer.townstead.profession.def.ProfessionDefs.all().values()) {
            for (String counter : def.historyCounters()) {
                byVerb.computeIfAbsent(counter, key -> new java.util.ArrayList<>())
                        .add(def.id().toString());
            }
        }
        java.util.Map<String, java.util.List<String>> moments = new java.util.HashMap<>();
        for (com.aetherianartificer.townstead.chronicle.model.ChronicleEvent event : events) {
            com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate template =
                    com.aetherianartificer.townstead.chronicle.template.ChronicleEventRegistry
                            .byId(event.templateId());
            if (template == null || template.trigger() == null) continue;
            java.util.List<String> careers = byVerb.get(template.trigger().key());
            if (careers == null) continue;
            String line = null;
            for (String careerId : careers) {
                java.util.List<String> list = moments.computeIfAbsent(careerId,
                        key -> new java.util.ArrayList<>());
                if (list.size() >= 3) continue;
                if (line == null) line = momentLine(server, template, event);
                list.add(line);
            }
        }
        return moments;
    }

    private static String momentLine(
            net.minecraft.server.MinecraftServer server,
            com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate template,
            com.aetherianartificer.townstead.chronicle.model.ChronicleEvent event) {
        String date = com.aetherianartificer.townstead.calendar.CalendarDateFormatter
                .format(server, event.worldDay(),
                        com.aetherianartificer.townstead.calendar.CalendarDateFormatter.Style.SHORT)
                .getString();
        String headline;
        String langKey = template.display().headlineLangKey();
        if (langKey != null && !langKey.isEmpty()) {
            java.util.List<String> args = new java.util.ArrayList<>();
            for (String name : template.display().paramNames()) {
                args.add(event.params().getOrDefault(name, "?"));
            }
            headline = Component.translatable(langKey, args.toArray()).getString();
        } else {
            headline = template.display().headlineLiteral();
        }
        if (headline == null || headline.isBlank()) headline = event.category();
        return date + ": " + headline;
    }
}

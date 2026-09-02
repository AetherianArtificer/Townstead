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
        takeUpWork(player, com.aetherianartificer.townstead.profession.def.ProfessionDefs
                .canonicalId(net.minecraft.resources.ResourceLocation.tryParse(careerIdRaw)));
    }

    /**
     * Declares a career as the player's work, refusing with a reason when it cannot be done.
     *
     * <p>Separated from its packet so the stamp press can reach it. Taking up work and learning a
     * skill are the same ceremony from the player's side, so they had better be the same ceremony
     * from the server's: one validation path, one set of refusals, one chronicle entry, whichever
     * way the intent arrived.</p>
     *
     * @return whether the vocation actually changed.
     */
    private static boolean takeUpWork(ServerPlayer player,
                                      net.minecraft.resources.ResourceLocation careerId) {
        com.aetherianartificer.townstead.profession.def.ProfessionDef def =
                com.aetherianartificer.townstead.profession.def.ProfessionDefs.byId(careerId);
        CareerProfile profile = CareerProfiles.of(player);
        if (def == null || profile == null || careerId.equals(profile.primaryVocation())) {
            return false;
        }
        if (!def.isRoot() && !profile.acquiredCareers().contains(careerId)) {
            refuse(player, Component.translatable("townstead.career.vocation.not_yours"));
            return false;
        }

        // This was computed and then never read, so the Archives requirement was documented,
        // localized and completely unenforced: any player could take up any work from anywhere.
        boolean authorized = com.aetherianartificer.townstead.village.ArchivesBuilding
                .villageIfInside(player, player.blockPosition()).isPresent()
                || nearOnDutyScribe(player);
        if (!authorized) {
            refuse(player, Component.translatable("townstead.career.vocation.no_archives"));
            return false;
        }
        // Declare as often as you like. The once-a-day limit was friction with nothing behind it:
        // career XP and learned skills are permanent and per career, so switching costs you the
        // work you are not doing, which is the real price. The stamp is still kept, because the
        // Chronicle wants to know when you last changed your work.
        long today = player.serverLevel().getDayTime() / 24000L;
        PlayerCareers.mutate(player, stored -> {
            stored.setPrimaryVocation(careerId);
            stored.setLastVocationChangeDay(today);
        });
        com.aetherianartificer.townstead.chronicle.emit.ChronicleTaps.work(
                player, "townstead:took_up_work", null, null, 1f,
                java.util.Map.of("career", careerId.toString()));
        player.playNotifySound(net.minecraft.sounds.SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 0.9f);
        notify(player, Component.translatable("townstead.career.vocation.taken",
                def.displayName()));
        return true;
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
        } else {
            refuse(player, result.error() == null
                    ? Component.translatable("townstead.career.learn.blocked_generic")
                    : Component.translatable("townstead.career.learn.blocked", result.error()));
            return;
        }
        send(player);
    }

    /**
     * The stamp press: learn the skill, and if that succeeds record where the mark landed.
     *
     * <p>The order matters. The learn is validated and performed first by exactly the same path the
     * button used, so nothing about what a press COSTS lives on the client; the position is applied
     * only once the server has agreed the press was legal. A refused press leaves no mark, which is
     * why a failed learn returns before the profile is touched.</p>
     */
    public static void handleStamp(ServerPlayer player, String skillIdRaw, int x, int y,
                                    float rotation, String textureId, String sourcePack,
                                    String label) {
        net.minecraft.resources.ResourceLocation parsed =
                net.minecraft.resources.ResourceLocation.tryParse(skillIdRaw);
        // The press is one gesture over two kinds of record. Which one it is comes from the
        // registry, not from the client saying so.
        net.minecraft.resources.ResourceLocation careerId =
                com.aetherianartificer.townstead.profession.def.ProfessionDefs.canonicalId(parsed);
        boolean isCareer = com.aetherianartificer.townstead.profession.def.ProfessionDefs
                .byId(careerId) != null;
        net.minecraft.resources.ResourceLocation canonical;
        if (isCareer) {
            if (!takeUpWork(player, careerId)) return;
            canonical = careerId;
        } else {
            com.aetherianartificer.townstead.profession.skill.LearnedSkills.Result result =
                    com.aetherianartificer.townstead.profession.career.CareerChoices
                            .chooseFromAcquired(player, parsed);
            if (!result.ok()) {
                // Refusals go to the record's own notice band, never to chat: the open screen draws
                // its backdrop over the chat log, so a chat refusal is invisible and a press that
                // was correctly declined is indistinguishable from a broken stamp.
                refuse(player, result.error() == null
                        ? Component.translatable("townstead.career.learn.blocked_generic")
                        : Component.translatable("townstead.career.learn.blocked", result.error()));
                return;
            }
            canonical = com.aetherianartificer.townstead.profession.def.SkillDefs
                    .canonicalId(parsed);
        }
        net.minecraft.server.MinecraftServer server = player.getServer();
        CareerStamp mark = CareerStamp.sanitized(x, y, rotation, authorityFor(player),
                server == null ? "" : todayFor(server, player), textureId, sourcePack, label);
        PlayerCareers.mutate(player, stored -> stored.stamp(canonical, mark));
        player.playNotifySound(net.minecraft.sounds.SoundEvents.WOODEN_BUTTON_CLICK_ON,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 0.7f);
        player.playNotifySound(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.0f);
        send(player);
    }

    public static void send(ServerPlayer player) {
        send(player, player, false, "");
    }

    /**
     * Re-send the record with a line across its foot.
     *
     * <p>ANY answer to something the player did inside the screen has to come back this way. Chat
     * is the wrong channel while the board is open, because the board draws its own backdrop over
     * the chat log: a refusal sent to chat is invisible, and a press that was correctly declined
     * becomes indistinguishable from a broken one. That has now caught the vocation button, the
     * Learn button and the stamp in turn.</p>
     */
    public static void notify(ServerPlayer player, Component message) {
        send(player, player, false, message.getString());
    }

    /** {@link #notify} for the refusal case, so call sites read as what they mean. */
    public static void refuse(ServerPlayer player, Component reason) {
        notify(player, reason);
    }

    /**
     * Renders and sends the registry record of {@code target} to {@code viewer}. Chronicle
     * moments come from an async archive read; everything composes back on the server thread.
     * {@code inspect} marks somebody else's record: the client hides its action buttons, and
     * the server-side choose/vocation handlers only ever act on the sender regardless.
     */
    public static void send(ServerPlayer viewer, net.minecraft.world.entity.LivingEntity target,
                            boolean inspect) {
        send(viewer, target, inspect, "");
    }

    public static void send(ServerPlayer viewer, net.minecraft.world.entity.LivingEntity target,
                            boolean inspect, String notice) {
        net.minecraft.server.MinecraftServer server = viewer.getServer();
        if (server == null) return;
        com.aetherianartificer.townstead.chronicle.Chronicles.bySubject(target.getUUID(), 0L, 64)
                .thenAccept(events -> server.execute(() -> {
                    java.util.Map<String, java.util.List<String>> moments =
                            momentsFor(server, actedBy(events, target.getUUID()));
                    CareerGraphS2CPayload payload = new CareerGraphS2CPayload(
                            titleFor(viewer, target), inspect, notice,
                            authorityFor(viewer), todayFor(server, viewer),
                            CareerGraphBuilder.build(server, target, moments,
                                    //? if >=1.21 {
                                    viewer.clientInformation().language()));
                                    //?} else {
                                    /*viewer.getLanguage()));
                                    *///?}
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

    /**
     * Who registered this record: the village whose Archives you are standing in.
     *
     * <p>Sent as a NAME rather than a village id, and stamped onto the record as it reads today. A
     * village that is later renamed must not silently rewrite the marks pressed under its old name,
     * because a record that edits its own history is worse than one that is out of date. Empty in
     * the wild, where the record falls back to a field registry.</p>
     */
    private static String authorityFor(ServerPlayer viewer) {
        java.util.Optional<net.conczin.mca.server.world.data.Village> village =
                net.conczin.mca.server.world.data.Village.findNearest(viewer);
        if (village.isEmpty() || !village.get().isWithinBorder(viewer)) return "";
        String name = village.get().getName();
        return name == null ? "" : name;
    }

    /** Today, on the world's own calendar, for the date line under the stamp. */
    private static String todayFor(net.minecraft.server.MinecraftServer server, ServerPlayer viewer) {
        return com.aetherianartificer.townstead.calendar.CalendarDateFormatter
                .format(server, viewer.serverLevel().getDayTime() / 24000L,
                        com.aetherianartificer.townstead.calendar.CalendarDateFormatter.Style.SHORT)
                .getString();
    }

    /**
     * Keeps only the events the subject actually DID, discarding the ones they merely saw.
     *
     * <p>{@code Chronicles.bySubject} answers "events this UUID took part in", and taking part
     * includes being a witness: {@code ChronicleEmitter} adds a witness participation for everyone
     * within the template's radius. So standing near a village cook while they worked put "Inesis
     * prepared a feast of Cooked Rice" on YOUR career record, under your own name's page, as
     * though you had cooked it.</p>
     *
     * <p>A career record is a record of your work. Witnessing is real history and belongs in the
     * chronicle, but not in the box that reports what you have done.</p>
     */
    private static java.util.List<com.aetherianartificer.townstead.chronicle.model.ChronicleEvent>
            actedBy(java.util.List<com.aetherianartificer.townstead.chronicle.model.ChronicleEvent> events,
                    java.util.UUID subject) {
        java.util.List<com.aetherianartificer.townstead.chronicle.model.ChronicleEvent> mine =
                new java.util.ArrayList<>(events.size());
        for (com.aetherianartificer.townstead.chronicle.model.ChronicleEvent event : events) {
            for (com.aetherianartificer.townstead.chronicle.model.Participation part
                    : event.participations()) {
                if (part.isWitness()) continue;
                if (subject.equals(part.ref().uuid())) {
                    mine.add(event);
                    break;
                }
            }
        }
        return mine;
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
            for (String counter : CareerActivities.counters(def)) {
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
            // Chronicle templates are data-driven, so their headline strings live in the
            // data/<ns>/lang sidecar rather than in assets. This line is rendered on the SERVER,
            // where the assets language does not carry them, so Component.translatable resolved
            // to the raw key and printed "chronicle.townstead.feast_prepared" onto the page.
            // DataPackLang is the sidecar index and is loaded here, so ask it first.
            String pattern = com.aetherianartificer.townstead.data.DataPackLang
                    .find(langKey, "en_us");
            headline = pattern == null
                    ? Component.translatable(langKey, args.toArray()).getString()
                    : formatPattern(pattern, args.toArray());
        } else {
            headline = template.display().headlineLiteral();
        }
        if (headline == null || headline.isBlank()) headline = event.category();
        return date + ": " + headline;
    }

    /**
     * Substitutes a lang pattern's arguments the way the client would. Pack-authored patterns can
     * carry anything, so a malformed specifier yields the raw pattern rather than throwing and
     * taking the whole record with it.
     */
    private static String formatPattern(String pattern, Object[] args) {
        try {
            return String.format(pattern, args);
        } catch (java.util.IllegalFormatException malformed) {
            return pattern;
        }
    }
}

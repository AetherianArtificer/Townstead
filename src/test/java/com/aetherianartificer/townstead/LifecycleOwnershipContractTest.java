package com.aetherianartificer.townstead;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ordering and ownership rules between lifecycles that only a running game could otherwise show. */
class LifecycleOwnershipContractTest {
    private static final String ROOT = "src/main/java/com/aetherianartificer/townstead/";

    @Test
    void aTalkNeverRemovesTheMobItIsRunningOn() throws IOException {
        String hearts = source("replace/CostumeHearts.java");
        String talked = between(hearts, "public static void talked", "public static void settleDue");
        assertFalse(talked.contains("settleIfReady") || talked.contains("convert("),
                "Settling mid-talk discards the mob, and every later line of the story looks that mob up");

        String story = source("story/StoryService.java");
        String close = between(story, "payload.action() == StoryC2SPayload.CLOSE", "payload.action() == StoryC2SPayload.OFFER");
        assertTrue(close.indexOf("closeSession(player)") >= 0
                        && close.indexOf("closeSession(player)") < close.indexOf("CostumeHearts.settleDue(player)"),
                "Settling comes after the story has saved, once the screen has closed");
    }

    @Test
    void personaDeparturesAreHeardBeforeAnyoneCanLeave() throws IOException {
        String service = source("persona/PersonaService.java");
        int tick = service.indexOf("public static void tick(MinecraftServer server)");
        int gate = service.indexOf("Systems.on(Systems.PERSONAS)", tick);
        int listen = service.indexOf("PersonaMoves.listen()", tick);
        assertTrue(listen > tick && listen < gate,
                "The listener must not wait for the 100-tick arrival pass or depend on Personas being on");

        String moves = source("persona/PersonaMoves.java");
        String moveTick = between(moves, "static void tick(MinecraftServer server)", "VillagerEntityMCA villager = PersonaService.find");
        assertTrue(moveTick.contains("PURPOSE.equals(journey.purpose())") && moveTick.contains("PersonaInstances.TRAVELLING"),
                "A Persona already on the road with a stale record must be repaired");
    }

    @Test
    void emptyTownCountdownSurvivesARestart() throws IOException {
        assertFalse(source("persona/PersonaMoves.java").contains("EMPTY_SINCE"));
        String instances = source("persona/PersonaInstances.java");
        assertTrue(instances.contains("tag.put(\"empty_since\"") && instances.contains("tag.getList(\"empty_since\""),
                "The countdown must be both saved and loaded");
    }

    @Test
    void anIntroductionOutlivesAFailedSpawn() throws IOException {
        String service = source("persona/PersonaService.java");
        String arrive = between(service, "private static void arriveIntroduced", "The name {@code player} knows a Persona");
        assertTrue(arrive.contains("spawn(persona, player, village, false) != null) instances.removePending(entry)"));
    }

    @Test
    void killReachesDownedVillagersAndPets() throws IOException {
        String blocks = between(source("downed/DownedService.java"), "public static boolean blocksDamage", "public static boolean blocksTarget");
        int kill = blocks.indexOf("DamageTypes.GENERIC_KILL");
        assertTrue(kill >= 0 && kill < blocks.indexOf("PetDowned.blocksDamage"),
                "The exception must come before both the pet and the villager refusals");
    }

    @Test
    void downedVillagersLeaveOtherLifecycles() throws IOException {
        String hangout = between(source("hangout/HangoutEngine.java"), "private static boolean safe(", "private static boolean recentlyAttacked");
        assertTrue(hangout.contains("VillagerControl.outranked(villager"), "Hangouts end for anything above them, downed included");
        String conversation = between(source("dialogue/conversation/ConversationEngine.java"), "public static boolean available(", "private static VillagerEntityMCA find");
        assertTrue(conversation.contains("DownedService.isDowned(actor)"));
    }

    @Test
    void attendedSafeStopRunsOnce() throws IOException {
        String protocols = source("work/station/AttendedStationProtocols.java");
        String observe = between(protocols, "public static boolean observe", "public static void end");
        assertTrue(observe.contains("safeStop(level, worker, pos, def, state)"));
        assertFalse(observe.contains("attendance.safeStop()"), "Timed-out stations are polled, so a direct run repeats");
    }

    @Test
    void collapseHoldsUntilTheGate() throws IOException {
        assertTrue(com.aetherianartificer.townstead.fatigue.FatigueData.RECOVERY_GATE == 19,
                "Recover at energy 2, which is fatigue 18: the first value below the gate");
        assertTrue(source("tick/FatigueVillagerTicker.java").contains("needs.collapsed() && currentFatigue < recoveryGate"),
                "Anything looser clears collapse and gate together at 19 and leaves the gate unused");
    }

    @Test
    void aHeldFormIsWrittenOnTheVillager() throws IOException {
        String werewolf = source("compat/werewolves/WerewolfVillagers.java");
        assertTrue(werewolf.contains("getPersistentData().putDouble(HELD, form)"));
        assertFalse(werewolf.contains("Map<UUID, Double> HELD"), "A hold in memory ends on restart");
    }

    @Test
    void aMastersDeathFreesThralls() throws IOException {
        String townstead = source("Townstead.java");
        assertTrue(count(townstead, "Thralls.onDeath(e.getEntity())") == 2, "Both loaders must call the death hook");
    }

    @Test
    void aServingSurvivesAReload() throws IOException {
        String manager = source("hunger/VillagerConsumptionManager.java");
        assertFalse(manager.contains("villager.getId()"), "An id changes across a reload, so state keyed by it is stranded");
        assertTrue(count(source("Townstead.java"), "VillagerConsumptionManager.onJoin(e.getEntity())") == 2,
                "Both loaders must restore it");
        assertTrue(count(manager, "PENDING.put(") == count(manager, "save(villager, started, false)"),
                "Every serving started must be written down");
    }

    @Test
    void wildWithNoReplacementSettlesWithoutAChronicleEvent() throws IOException {
        String replacer = source("replace/MobReplacer.java");
        String tick = between(replacer, "public static void tick(VillagerEntityMCA villager)", "public static boolean isWild");
        assertTrue(tick.contains("leaveTheWilds(villager)"));
        assertFalse(between(replacer, "private static boolean leaveTheWilds", "public static @Nullable MobReplacement replacementOf").contains("ChronicleTaps"),
                "Data going away is not a moment worth a chronicle event");
    }

    @Test
    void workersHoldOrderClaimsByLease() throws IOException {
        for (String task : new String[]{"work/producer/ProducerWorkTask.java", "work/job/BlockInteractionWorkTask.java"}) {
            String source = source(task);
            assertFalse(source.contains(".abandon(claimedAmount)") || source.contains(".abandon(claimedLineAmount)"), task);
            int tick = source.indexOf("protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime)");
            int renew = source.indexOf(".renew(", tick);
            assertTrue(tick >= 0 && renew > tick && renew - tick < 200,
                    task + " must renew its claim at the top of every tick");
        }
    }

    @Test
    void orphanedVisitsAreSweptAndTheEngineClearsAtStop() throws IOException {
        String engine = source("hangout/HangoutEngine.java");
        assertTrue(between(engine, "public static void tick(VillagerEntityMCA villager)", "UUID visitId = BY_VISITOR.get").contains("sweepOrphans(level, now)"),
                "Only the visitor's own tick moves a visit, so one who never returns needs another way out");
        assertTrue(count(source("Townstead.java"), "HangoutEngine.clear()") == 2, "Both loaders must clear at stop");
    }

    @Test
    void oneFailingRebirthStepStillReachesDestiny() throws IOException {
        String rebirth = source("rebirth/Rebirth.java");
        String end = between(rebirth, "public static void endLife", "Changes the name a reborn player goes by");
        for (String name : new String[]{"\"family\"", "\"careers\"", "\"standing\"", "\"memberships\"", "\"aspect\"", "\"lives\"", "\"names\"", "\"sync\""}) {
            assertTrue(end.contains("step(player, " + name), name + " must be guarded");
        }
        assertTrue(between(rebirth, "private static void begin", "Ends the player's current life").contains("openDestiny(player)"));
    }

    @Test
    void rebirthStateSurvivesLeaving() throws IOException {
        String rebirth = source("rebirth/Rebirth.java");
        assertTrue(between(rebirth, "public static void onLogin", "private static void resumeDestiny").contains("resumeDestiny(player)"));
        assertTrue(between(rebirth, "private static void request", "public static void onRespawn").contains("putBoolean(REQUEST"));
        assertTrue(between(rebirth, "private static void begin", "Ends the player's current life").contains("rememberDestiny(player, died)"));
    }

    @Test
    void movementWritersAskThePriorityList() throws IOException {
        for (String writer : new String[]{"journey/Journeys.java", "journey/Companions.java", "persona/PersonaService.java",
                "story/StoryCast.java", "ritual/RitualSession.java", "hangout/HangoutEngine.java", "switchboard/GatedBehavior.java"}) {
            assertTrue(source(writer).contains("VillagerControl"), writer + " must give way to what outranks it");
        }
        String brain = source("mixin/VillagerHungerMixin.java");
        assertTrue(count(brain, "<VillagerEntityMCA>work(") >= 10, "Work behaviors must give way too");
        int martial = brain.indexOf("MartialWorkTask()");
        int lineStart = brain.lastIndexOf('\n', martial);
        assertTrue(brain.substring(lineStart, martial).contains("<VillagerEntityMCA>of("),
                "Combat stays outside the list until the alert ladder exists");
    }

    @Test
    void roughSleepIsSavedAndFallsDoNotReplay() throws IOException {
        String playback = source("performance/CollapsePlayback.java");
        assertTrue(playback.contains("putBoolean(ROUGH, true)") && playback.contains("restoreRough(actor)"));
        assertTrue(playback.contains("continuing ? prior.start"), "A clip taking over from itself keeps its place");
        String downed = source("downed/DownedService.java");
        assertTrue(between(downed, "public static void onJoin", "public static void tick").contains("stayDown(villager)"),
                "Reloading must not replay the fall");
    }

    @Test
    void tickerStateGoesWithTheVillager() throws IOException {
        for (String ticker : new String[]{"tick/HungerVillagerTicker.java", "tick/ThirstVillagerTicker.java",
                "tick/TemperatureVillagerTicker.java", "tick/WardrobeVillagerTicker.java", "tick/LifeStageTicker.java"}) {
            String source = source(ticker);
            assertTrue(source.contains("Map<VillagerEntityMCA,") && source.contains("WeakHashMap"),
                    ticker + ": an id changes on reload and removal never reaches these tickers, so id-keyed state only piles up");
            assertFalse(source.contains("isRemoved()"), ticker + ": the dispatcher returns before a removed villager reaches this");
        }
    }

    @Test
    void homePointsWritesTheForgeTableWithItsOwnValueType() throws IOException {
        String homes = source("rest/HomePoints.java");
        String forge = between(homes, "//?} else {", "*///?}");
        assertTrue(forge.contains("Map<BlockState, PoiType> table") && forge.contains("getBlockStatePointOfInterestTypeMap()"),
                "A Holder written into Forge's table crashes the first read of that block state");
        assertFalse(homes.contains("f_218070_"), "Forge must not reach this table through the 1.21 path");
    }

    @Test
    void releasingAThrallBetweenMastersStillClearsTheState() throws IOException {
        String release = between(source("compat/vampirism/Thralls.java"), "public static boolean release", "Whether {@code thrall} serves");
        assertTrue(release.contains("bond == null && !thrallState"), "No bond alone must not leave the state on");
    }

    @Test
    void werewolfTimersAreClearedAtStop() throws IOException {
        assertTrue(count(source("Townstead.java"), "WerewolfVillagers.clear()") == 2, "Both loaders must clear at stop");
        assertTrue(source("tick/VillagerServerTickDispatcher.java").contains("WerewolfVillagers.forget(villager)"));
    }

    private static int count(String source, String needle) {
        int found = 0;
        for (int at = source.indexOf(needle); at >= 0; at = source.indexOf(needle, at + needle.length())) found++;
        return found;
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        assertTrue(from >= 0 && to > from, "Missing section: " + start + " .. " + end);
        return source.substring(from, to);
    }

    private static String source(String relative) throws IOException {
        Path target = Path.of(ROOT + relative);
        for (Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath(); root != null; root = root.getParent()) {
            Path path = root.resolve(target);
            if (Files.isRegularFile(path)) return Files.readString(path, StandardCharsets.UTF_8);
        }
        throw new IOException("Unable to locate " + target);
    }
}

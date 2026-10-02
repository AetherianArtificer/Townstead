package com.aetherianartificer.townstead.persona;

import com.aetherianartificer.townstead.root.RootAssignment;
import com.aetherianartificer.townstead.story.goal.Goal;
import com.aetherianartificer.townstead.story.goal.GoalContext;
import com.aetherianartificer.townstead.switchboard.Systems;
import com.aetherianartificer.townstead.village.TownRange;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Brings Personas into the world. Every few seconds, for each player standing in a village, each
 * Persona that is not there yet and that the player has not met anywhere checks its arrival
 * goals. When one is met, the Persona is spawned out of sight at the village edge and walks in
 * toward the player, or appears at the centre.
 */
public final class PersonaService {
    private static final Logger LOGGER = LoggerFactory.getLogger("Townstead/Personas");
    private static final int ARRIVAL_TICKS = 100;
    private static final int WALK_TICKS = 20;
    private static final long WALK_LIMIT = 20 * 120;
    private static final double MIN_DISTANCE_FROM_PLAYER = 24;

    private record WalkIn(UUID player, long until) {}

    private static final Map<UUID, WalkIn> WALKING = new HashMap<>();

    private PersonaService() {}

    public static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        if (now % WALK_TICKS == 0 && !WALKING.isEmpty()) walk(server);
        if (now % ARRIVAL_TICKS != 0 || !Systems.on(Systems.PERSONAS) || Personas.all().isEmpty()) return;
        arriveIntroduced(server);
        keepJobs(server);
        PersonaMoves.tick(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = player.serverLevel();
            Village village = TownRange.at(level, player.blockPosition()).orElse(null);
            if (village == null) continue;
            for (PersonaDefinition persona : Personas.all().values()) {
                if (persona.arrives().isEmpty()) continue;
                PersonaInstances.Instance travelling = PersonaInstances.get(server).travelling(persona.id());
                if (travelling != null) {
                    if (readyToArrive(server, travelling, level, village) && arrives(server, player, persona)) {
                        arriveTravelling(persona, travelling, player, village);
                    }
                    continue;
                }
                if (PersonaBonds.has(player, persona.id())) continue;
                if (PersonaInstances.get(server).in(persona.id(), level.dimension().location(), village.getId()) != null) continue;
                if (takenElsewhere(server, persona)) continue;
                if (arrives(server, player, persona)) spawn(persona, player, village, false);
            }
        }
    }

    private static boolean arrives(MinecraftServer server, ServerPlayer player, PersonaDefinition persona) {
        GoalContext ctx = new GoalContext(server, player.getUUID(), player.getUUID(), "", null);
        for (Goal goal : persona.arrives()) {
            long value = goal.read(ctx);
            if (value != Goal.UNKNOWN && value >= goal.total()) return true;
        }
        return false;
    }

    /**
     * Spawns a Persona for this village and bonds the player to them. With {@code nearby}, they
     * appear a few blocks from the player instead of at the edge (for testing). Returns the
     * villager, or null when nothing could be spawned.
     */
    public static @Nullable VillagerEntityMCA spawn(PersonaDefinition persona, ServerPlayer player, Village village, boolean nearby) {
        ServerLevel level = player.serverLevel();
        boolean walkIn = persona.arrival() == PersonaDefinition.Arrival.WALK_IN && !nearby;
        BlockPos at = nearby ? surface(level, player.blockPosition().offset(3, 0, 3))
                : walkIn ? edge(level, village, player) : centre(level, village);
        VillagerFactory factory = VillagerFactory.newVillager(level).withAge(0).withPosition(Vec3.atBottomCenterOf(at));
        Identity identity = identity(persona, player);
        if ("male".equals(identity.gender())) factory.withGender(Gender.MALE);
        else if ("female".equals(identity.gender())) factory.withGender(Gender.FEMALE);
        ResourceLocation profession = profession(persona, player);
        if (profession != null && BuiltInRegistries.VILLAGER_PROFESSION.containsKey(profession)) {
            factory.withProfession(BuiltInRegistries.VILLAGER_PROFESSION.get(profession));
        }
        boolean keepsJob = profession != null;
        VillagerEntityMCA villager = factory.build();
        //? if >=1.21 {
        villager.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
        //?} else {
        /*villager.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null);
        *///?}
        // A Persona's job is part of who they are. Any trade experience stops vanilla resetting
        // a villager with no job site to jobless, which would start a hire-and-fire loop.
        if (keepsJob && villager.getVillagerXp() < 1) villager.setVillagerXp(1);
        if (!level.addFreshEntity(villager)) return null;
        if (persona.root() != null) RootAssignment.assign(villager, persona.root());
        villager.getResidency().seekHome();
        if (persona.schedule() != null) schedule(level.getServer(), villager, persona.schedule());
        dress(villager, outfit(persona, player));
        if (!persona.personalities().isEmpty()) personality(villager, persona);
        name(villager, identity);
        for (Map.Entry<ResourceLocation, Double> state : persona.states().entrySet()) {
            com.aetherianartificer.townstead.pheno.state.EntityStates.set(villager, state.getKey(), state.getValue(), 0, null);
        }
        if (persona.mainhand() != null) {
            BuiltInRegistries.ITEM.getOptional(persona.mainhand()).ifPresent(item ->
                    villager.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(item)));
        }
        PersonaInstances.get(level.getServer()).add(new PersonaInstances.Instance(persona.id(), villager.getUUID(),
                level.dimension().location(), village.getId(), level.getGameTime(),
                com.aetherianartificer.townstead.naming.VillagerNames.display(villager).getString()));
        PersonaBonds.add(player, persona.id());
        if (walkIn) WALKING.put(villager.getUUID(), new WalkIn(player.getUUID(), level.getGameTime() + WALK_LIMIT));
        LOGGER.info("Persona {} arrived in village {} for {}", persona.id(), village.getId(), player.getGameProfile().getName());
        return villager;
    }

    /** Who a Persona is in this world: gender, given and family name, and any extra story names. */
    public record Identity(@Nullable String gender, @Nullable String given, @Nullable String family, Map<String, String> extra) {
        /** The rolled names as Ink variables. */
        public Map<String, Object> vars() {
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            if (gender != null) out.put("gender", gender);
            if (given != null) out.put(PersonaNames.GIVEN, given);
            if (family != null) out.put(PersonaNames.FAMILY, family);
            out.putAll(extra);
            return out;
        }
    }

    /**
     * This world's identity for a Persona, rolling what has not been rolled yet. A gender comes from
     * a roll that sets one, else persona.json; a Persona with names and neither rolls one.
     */
    public static Identity identity(PersonaDefinition persona, ServerPlayer player) {
        PersonaInstances instances = PersonaInstances.get(player.server);
        String gender = persona.gender();
        for (PersonaRoll.Option option : rolls(persona, player).values()) {
            if (option.gender() != null) gender = option.gender();
        }
        PersonaNames names = persona.names();
        if (names == null) return new Identity(gender, null, null, Map.of());
        if (gender == null) {
            gender = instances.rolled(persona.id(), "name.gender");
            if (gender == null) {
                gender = player.getRandom().nextBoolean() ? "female" : "male";
                instances.setRolled(persona.id(), "name.gender", gender);
            }
        }
        String given = rolledName(instances, persona, "name.given", names.pickGiven(gender, player.getRandom()));
        String family = rolledName(instances, persona, "name.family", PersonaNames.pick(names.family(), player.getRandom()));
        Map<String, String> extra = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, java.util.List<String>> pool : names.extra().entrySet()) {
            String value = rolledName(instances, persona, "name.extra." + pool.getKey(), PersonaNames.pick(pool.getValue(), player.getRandom()));
            if (value != null) extra.put(pool.getKey(), value);
        }
        return new Identity(gender, given, family, Map.copyOf(extra));
    }

    private static @Nullable String rolledName(PersonaInstances instances, PersonaDefinition persona, String key, @Nullable String fresh) {
        String value = instances.rolled(persona.id(), key);
        if (value != null) return value;
        if (fresh != null) instances.setRolled(persona.id(), key, fresh);
        return fresh;
    }

    /** Names a new Persona villager from their rolled identity: the given name, and a fixed family name. */
    private static void name(VillagerEntityMCA villager, Identity identity) {
        if (identity.given() == null) return;
        villager.setCustomName(net.minecraft.network.chat.Component.literal(identity.given()));
        if (identity.family() != null) {
            var life = com.aetherianartificer.townstead.villager.TownsteadVillagers.get(villager).life();
            life.setFamilyName(identity.family());
            life.setFamilyNameFixed(true);
            com.aetherianartificer.townstead.villager.TownsteadVillagers.flush(villager);
        }
        com.aetherianartificer.townstead.naming.VillagerNames.publish(villager);
    }

    /** A travelling Persona may arrive here once their road time is over, anywhere but the village they left. */
    private static boolean readyToArrive(MinecraftServer server, PersonaInstances.Instance instance, ServerLevel level, Village village) {
        var journey = com.aetherianartificer.townstead.journey.Journeys.get(server).of(instance.villager());
        if (journey == null) return false;
        if (journey.readyDay() > com.aetherianartificer.townstead.calendar.TownsteadCalendar.worldDay(server)) return false;
        return !(level.dimension().location().toString().equals(journey.data().getString("from_dimension"))
                && journey.data().getInt("from_village") == village.getId());
    }

    /** Brings a travelling Persona in from the road, as themselves, walking toward the player. */
    private static void arriveTravelling(PersonaDefinition persona, PersonaInstances.Instance instance, ServerPlayer player, Village village) {
        ServerLevel level = player.serverLevel();
        VillagerEntityMCA villager = com.aetherianartificer.townstead.journey.Journeys.arrive(level, instance.villager(), edge(level, village, player));
        if (villager == null) return;
        PersonaInstances.get(level.getServer()).move(villager.getUUID(), level.dimension().location(), village.getId());
        PersonaBonds.add(player, persona.id());
        WALKING.put(villager.getUUID(), new WalkIn(player.getUUID(), level.getGameTime() + WALK_LIMIT));
        LOGGER.info("Persona {} came in from the road to village {}", persona.id(), village.getId());
    }

    /**
     * What this world rolled for each of the Persona's rolls, rolling any that have not been
     * rolled yet. A value that the data no longer offers is rolled again.
     */
    public static Map<String, PersonaRoll.Option> rolls(PersonaDefinition persona, ServerPlayer player) {
        Map<String, PersonaRoll.Option> out = new java.util.LinkedHashMap<>();
        if (persona.rolls().isEmpty()) return out;
        PersonaInstances instances = PersonaInstances.get(player.server);
        for (PersonaRoll roll : persona.rolls()) {
            String value = instances.rolled(persona.id(), roll.name());
            PersonaRoll.Option option = value == null ? null : roll.option(value);
            if (option == null) {
                option = roll.pick(player, player.getRandom());
                if (option == null) continue;
                instances.setRolled(persona.id(), roll.name(), option.value());
            }
            out.put(roll.name(), option);
        }
        return out;
    }

    /**
     * Rolls one of the Persona's personalities, the same way a Root's personality policy does.
     * A personality this MCA version does not have is left out of the roll.
     */
    private static void personality(VillagerEntityMCA villager, PersonaDefinition persona) {
        Map<String, Integer> open = new java.util.LinkedHashMap<>();
        int total = 0;
        for (Map.Entry<String, Integer> entry : persona.personalities().entrySet()) {
            if (entry.getValue() <= 0) continue;
            if (com.aetherianartificer.townstead.root.personality.PersonalityResolver.baseOf(entry.getKey()) == null) {
                LOGGER.warn("Persona {}: '{}' is not a personality here; skipping it", persona.id(), entry.getKey());
                continue;
            }
            open.put(entry.getKey(), entry.getValue());
            total += entry.getValue();
        }
        if (total <= 0) return;
        int roll = villager.getRandom().nextInt(total);
        for (Map.Entry<String, Integer> entry : open.entrySet()) {
            roll -= entry.getValue();
            if (roll >= 0) continue;
            com.aetherianartificer.townstead.villager.TownsteadVillagers.get(villager).life().setPersonalityId(entry.getKey());
            villager.getVillagerBrain().setPersonality(
                    com.aetherianartificer.townstead.root.personality.PersonalityResolver.baseOf(entry.getKey()));
            com.aetherianartificer.townstead.villager.TownsteadVillagers.flush(villager);
            return;
        }
    }

    /** Puts a new Persona on a week plan, or on a shift template every day. */
    private static void schedule(net.minecraft.server.MinecraftServer server, VillagerEntityMCA villager, ResourceLocation id) {
        var state = com.aetherianartificer.townstead.villager.TownsteadVillagers.get(villager).schedule();
        var plan = com.aetherianartificer.townstead.shift.weekplan.WeekPlanRegistry.resolve(server, id);
        if (plan.isPresent()) {
            state.setMode(com.aetherianartificer.townstead.shift.ShiftData.MODE_WEEKLY);
            state.setWeekDayTemplates(plan.get().copyDays());
        } else {
            var template = com.aetherianartificer.townstead.shift.template.ShiftTemplateRegistry.resolve(server, id);
            if (template.isEmpty()) {
                LOGGER.warn("Persona schedule {} is neither a week plan nor a shift template", id);
                return;
            }
            state.setMode(com.aetherianartificer.townstead.shift.ShiftData.MODE_DAILY);
            state.setShifts(template.get().copyShifts());
            state.setTemplateId(id.toString());
        }
        com.aetherianartificer.townstead.shift.ShiftScheduleApplier.apply(villager);
        com.aetherianartificer.townstead.villager.TownsteadVillagers.flush(villager);
    }

    /** The job a Persona is meant to have: their rolled one, else the one persona.json pins, or null. */
    static @Nullable ResourceLocation profession(PersonaDefinition persona, ServerPlayer player) {
        ResourceLocation profession = persona.profession();
        for (PersonaRoll.Option option : rolls(persona, player).values()) {
            if (option.profession() != null) profession = option.profession();
        }
        return profession;
    }

    /**
     * Keeps every loaded Persona in the job they are meant to have. Personas that lost it before
     * this rule existed get it back; trade experience of at least 1 keeps vanilla from resetting it.
     */
    private static void keepJobs(MinecraftServer server) {
        for (PersonaInstances.Instance instance : PersonaInstances.get(server).all()) {
            PersonaDefinition persona = Personas.byId(instance.persona());
            if (persona == null) continue;
            VillagerEntityMCA villager = find(server, instance.villager());
            if (villager == null || villager.isBaby()) continue;
            ServerPlayer anyone = server.getPlayerList().getPlayers().isEmpty() ? null : server.getPlayerList().getPlayers().get(0);
            ResourceLocation wanted = pinned(server, persona);
            if (wanted == null || !BuiltInRegistries.VILLAGER_PROFESSION.containsKey(wanted)) continue;
            var job = BuiltInRegistries.VILLAGER_PROFESSION.get(wanted);
            if (villager.getVillagerData().getProfession() != job) {
                villager.setProfession(job);
                LOGGER.info("Persona {} got their job back ({})", instance.persona(), wanted);
            }
            if (villager.getVillagerXp() < 1) villager.setVillagerXp(1);
            if (anyone != null) dress(villager, outfit(persona, anyone));
        }
    }

    /**
     * The profession a Persona is pinned to, or null for anyone else (and for a Persona whose
     * persona.json names no profession). Nothing may move a pinned Persona to another job: not
     * hiring, not order membership, not the job screen.
     */
    public static @Nullable ResourceLocation pinnedProfession(VillagerEntityMCA villager) {
        MinecraftServer server = villager.getServer();
        if (server == null) return null;
        PersonaInstances.Instance instance = PersonaInstances.get(server).of(villager.getUUID());
        PersonaDefinition persona = instance == null ? null : Personas.byId(instance.persona());
        return persona == null ? null : pinned(server, persona);
    }

    /** Whether {@code player} may see this villager's family name: anyone's, a Persona's once told. */
    public static boolean familyNameKnown(VillagerEntityMCA villager, net.minecraft.server.level.ServerPlayer player) {
        PersonaDefinition persona = definition(villager);
        return persona == null || !persona.familyToldOnly()
                || com.aetherianartificer.townstead.chronicle.Chronicles.count(player.server, player.getUUID(), familyKey(persona)) > 0;
    }

    /** The villager tells {@code player} their family name, and the player's screens show it from now on. */
    public static void tellFamilyName(VillagerEntityMCA villager, net.minecraft.server.level.ServerPlayer player) {
        PersonaDefinition persona = definition(villager);
        if (persona == null || !persona.familyToldOnly() || familyNameKnown(villager, player)) return;
        com.aetherianartificer.townstead.chronicle.Chronicles.addCounter(player.server, player.getUUID(), familyKey(persona), 1);
        com.aetherianartificer.townstead.naming.NameSyncTarget.syncToPlayer(player, villager);
    }

    private static String familyKey(PersonaDefinition persona) {
        return "townstead:knows_family/" + persona.id().getNamespace() + "/" + persona.id().getPath();
    }

    /** The Persona this villager is, or null for anyone else. */
    public static @Nullable PersonaDefinition definition(VillagerEntityMCA villager) {
        MinecraftServer server = villager.getServer();
        if (server == null) return null;
        PersonaInstances.Instance instance = PersonaInstances.get(server).of(villager.getUUID());
        return instance == null ? null : Personas.byId(instance.persona());
    }

    /** A Persona's own dialogue theme id, or empty for anyone else. */
    public static String dialogueTheme(VillagerEntityMCA villager) {
        MinecraftServer server = villager.getServer();
        if (server == null) return "";
        PersonaInstances.Instance instance = PersonaInstances.get(server).of(villager.getUUID());
        PersonaDefinition persona = instance == null ? null : Personas.byId(instance.persona());
        return persona == null || persona.dialogueTheme() == null ? "" : persona.dialogueTheme().toString();
    }

    private static @Nullable ResourceLocation pinned(MinecraftServer server, PersonaDefinition persona) {
        ServerPlayer anyone = server.getPlayerList().getPlayers().isEmpty() ? null : server.getPlayerList().getPlayers().get(0);
        return persona.rolls().isEmpty() || anyone == null ? persona.profession() : profession(persona, anyone);
    }

    /** The outfit a Persona is meant to wear: a rolled one, else the one persona.json gives. */
    static Map<String, String> outfit(PersonaDefinition persona, ServerPlayer player) {
        Map<String, String> outfit = persona.outfit();
        for (PersonaRoll.Option option : rolls(persona, player).values()) {
            if (!option.outfit().isEmpty()) outfit = option.outfit();
        }
        return outfit;
    }

    /** Puts a Persona in their outfit and locks it, so a change of job does not re-dress them. */
    private static void dress(VillagerEntityMCA villager, Map<String, String> outfit) {
        if (outfit.isEmpty()) return;
        String gender = villager.getGenetics().getGender().binary() == Gender.FEMALE ? "female" : "male";
        String clothes = outfit.getOrDefault(gender, outfit.get("any"));
        if (clothes == null || clothes.isBlank()) return;
        if (!clothes.equals(villager.getClothes())) villager.setClothes(clothes);
        if (!villager.isClothingLocked()) villager.setClothingLocked(true);
    }

    /** Whether a world-unique Persona already lives somewhere, or is on their way somewhere. */
    public static boolean takenElsewhere(MinecraftServer server, PersonaDefinition persona) {
        if (!persona.worldUnique()) return false;
        PersonaInstances instances = PersonaInstances.get(server);
        return !instances.of(persona.id()).isEmpty() || instances.isPendingAnywhere(persona.id());
    }

    /**
     * Schedules a Persona to arrive in a village after some days, as when one Persona writes to
     * another. Nothing happens when they already live there, are already on their way, or the
     * player has met them before. Returns whether they were scheduled.
     */
    public static boolean introduce(ServerPlayer player, ResourceLocation personaId, ServerLevel level, Village village, int delayDays) {
        PersonaDefinition persona = Personas.byId(personaId);
        if (persona == null || PersonaBonds.has(player, personaId)) return false;
        PersonaInstances instances = PersonaInstances.get(player.server);
        ResourceLocation dimension = level.dimension().location();
        if (instances.in(personaId, dimension, village.getId()) != null || instances.isPending(personaId, dimension, village.getId())) return false;
        if (persona.worldUnique() && (!instances.of(personaId).isEmpty() || instances.isPendingAnywhere(personaId))) return false;
        long due = com.aetherianartificer.townstead.calendar.TownsteadCalendar.worldDay(player.server) + Math.max(0, delayDays);
        instances.addPending(new PersonaInstances.Pending(personaId, dimension, village.getId(), due, player.getUUID()));
        LOGGER.info("Persona {} introduced to village {}, due on day {}", personaId, village.getId(), due);
        return true;
    }

    /** Brings in introduced Personas whose day has come, once their player is online. */
    private static void arriveIntroduced(MinecraftServer server) {
        PersonaInstances instances = PersonaInstances.get(server);
        if (instances.pending().isEmpty()) return;
        long today = com.aetherianartificer.townstead.calendar.TownsteadCalendar.worldDay(server);
        for (PersonaInstances.Pending entry : instances.pending()) {
            if (entry.dueDay() > today) continue;
            PersonaDefinition persona = Personas.byId(entry.persona());
            ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, entry.dimension()));
            if (persona == null || level == null) {
                instances.removePending(entry);
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.player());
            if (player == null || player.level() != level) continue;
            Village village = net.conczin.mca.server.world.data.VillageManager.get(level).getOrEmpty(entry.village()).orElse(null);
            instances.removePending(entry);
            if (village != null && instances.in(persona.id(), entry.dimension(), village.getId()) == null
                    && !(persona.worldUnique() && !instances.of(persona.id()).isEmpty())) {
                spawn(persona, player, village, false);
            }
        }
    }

    /**
     * The name of a Persona living in a village, or in any village when none lives there. Uses
     * the live villager when loaded, else the name they arrived with. Empty when there is none.
     */
    public static String nameOf(MinecraftServer server, ResourceLocation personaId, @Nullable ResourceLocation dimension, int village) {
        PersonaInstances instances = PersonaInstances.get(server);
        PersonaInstances.Instance instance = dimension == null ? null : instances.in(personaId, dimension, village);
        if (instance == null) {
            var all = instances.of(personaId);
            if (all.isEmpty()) return "";
            instance = all.get(0);
        }
        VillagerEntityMCA villager = find(server, instance.villager());
        return villager != null ? com.aetherianartificer.townstead.naming.VillagerNames.display(villager).getString() : instance.name();
    }

    /** A spot just outside {@code village}, out of the player's sight where possible. */
    public static BlockPos arrivalPoint(ServerLevel level, Village village, ServerPlayer player) {
        return edge(level, village, player);
    }

    /** Has {@code villager} walk up to {@code player}, as an arriving Persona does. */
    public static void walkTo(VillagerEntityMCA villager, ServerPlayer player) {
        WALKING.put(villager.getUUID(), new WalkIn(player.getUUID(), villager.level().getGameTime() + WALK_LIMIT));
    }

    /** Keeps walking-in Personas heading for their player until they are close or it takes too long. */
    private static void walk(MinecraftServer server) {
        Iterator<Map.Entry<UUID, WalkIn>> it = WALKING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, WalkIn> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getValue().player());
            VillagerEntityMCA villager = find(server, entry.getKey());
            if (player == null || villager == null || villager.level() != player.level()
                    || villager.level().getGameTime() > entry.getValue().until()) {
                it.remove();
                continue;
            }
            if (villager.distanceToSqr(player) < 16) {
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                villager.getLookControl().setLookAt(player);
                it.remove();
                continue;
            }
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(player, 0.6f, 3));
            villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(player, true));
        }
    }

    /**
     * A Persona is never a stranger: before MCA picks how to open a conversation, they already
     * remember this player, so MCA's first-meeting lines and questions never play.
     */
    public static void knows(ServerPlayer player, VillagerEntityMCA villager) {
        if (PersonaInstances.get(player.server).of(villager.getUUID()) == null) return;
        String seen = "seen." + player.getUUID();
        if (!villager.getLongTermMemory().hasMemory(seen)) villager.getLongTermMemory().remember(seen);
    }

    /** Death hook, for deaths that were not turned into being downed: the Persona is gone. */
    public static void onDeath(LivingEntity entity) {
        MinecraftServer server = entity.getServer();
        if (server == null || !(entity instanceof VillagerEntityMCA)) return;
        PersonaInstances instances = PersonaInstances.get(server);
        PersonaInstances.Instance instance = instances.of(entity.getUUID());
        if (instance == null) return;
        instances.remove(entity.getUUID());
        WALKING.remove(entity.getUUID());
        LOGGER.info("Persona {} died", instance.persona());
    }

    static @Nullable VillagerEntityMCA find(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof VillagerEntityMCA villager && villager.isAlive()) return villager;
        }
        return null;
    }

    private static BlockPos centre(ServerLevel level, Village village) {
        Vec3i c = village.getCenter();
        return surface(level, new BlockPos(c.getX(), 0, c.getZ()));
    }

    /** A spot just outside the village, out of the player's sight where possible. */
    private static BlockPos edge(ServerLevel level, Village village, ServerPlayer player) {
        BoundingBox box = village.getBox();
        Vec3i c = village.getCenter();
        int radius = Math.max(box.getXSpan(), box.getZSpan()) / 2 + 12;
        BlockPos best = null;
        double bestDistance = -1;
        for (int i = 0; i < 12; i++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            BlockPos pos = surface(level, new BlockPos(c.getX() + (int) (Math.cos(angle) * radius), 0,
                    c.getZ() + (int) (Math.sin(angle) * radius)));
            double distance = pos.distSqr(player.blockPosition());
            Vec3 head = new Vec3(pos.getX() + 0.5, pos.getY() + 1.6, pos.getZ() + 0.5);
            boolean seen = level.clip(new net.minecraft.world.level.ClipContext(player.getEyePosition(), head,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE,
                    player)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
            if (distance >= MIN_DISTANCE_FROM_PLAYER * MIN_DISTANCE_FROM_PLAYER && !seen) return pos;
            if (distance > bestDistance) {
                bestDistance = distance;
                best = pos;
            }
        }
        return best != null ? best : centre(level, village);
    }

    private static BlockPos surface(ServerLevel level, BlockPos pos) {
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos);
    }
}

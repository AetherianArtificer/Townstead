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
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = player.serverLevel();
            Village village = TownRange.at(level, player.blockPosition()).orElse(null);
            if (village == null) continue;
            for (PersonaDefinition persona : Personas.all().values()) {
                if (persona.arrives().isEmpty() || PersonaBonds.has(player, persona.id())) continue;
                if (PersonaInstances.get(server).in(persona.id(), level.dimension().location(), village.getId()) != null) continue;
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
        if ("male".equals(persona.gender())) factory.withGender(Gender.MALE);
        else if ("female".equals(persona.gender())) factory.withGender(Gender.FEMALE);
        ResourceLocation profession = persona.profession();
        for (PersonaRoll.Option option : rolls(persona, player).values()) {
            if (option.profession() != null) profession = option.profession();
        }
        if (profession != null && BuiltInRegistries.VILLAGER_PROFESSION.containsKey(profession)) {
            factory.withProfession(BuiltInRegistries.VILLAGER_PROFESSION.get(profession));
        }
        VillagerEntityMCA villager = factory.build();
        //? if >=1.21 {
        villager.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
        //?} else {
        /*villager.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null);
        *///?}
        if (!level.addFreshEntity(villager)) return null;
        if (persona.root() != null) RootAssignment.assign(villager, persona.root());
        villager.getResidency().seekHome();
        if (persona.schedule() != null) schedule(level.getServer(), villager, persona.schedule());
        if (!persona.personalities().isEmpty()) personality(villager, persona);
        PersonaInstances.get(level.getServer()).add(new PersonaInstances.Instance(persona.id(), villager.getUUID(),
                level.dimension().location(), village.getId(), level.getGameTime()));
        PersonaBonds.add(player, persona.id());
        if (walkIn) WALKING.put(villager.getUUID(), new WalkIn(player.getUUID(), level.getGameTime() + WALK_LIMIT));
        LOGGER.info("Persona {} arrived in village {} for {}", persona.id(), village.getId(), player.getGameProfile().getName());
        return villager;
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

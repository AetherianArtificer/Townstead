package com.aetherianartificer.townstead.packcheck;

import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.client.attachment.LiveBoxes;
import com.aetherianartificer.townstead.root.LifeStageProgression;
import com.aetherianartificer.townstead.root.RootAssignment;
import com.aetherianartificer.townstead.root.RootRegistry;
import com.aetherianartificer.townstead.root.attachment.AttachmentDef;
import com.aetherianartificer.townstead.root.attachment.BodyParts;
import com.aetherianartificer.townstead.root.attachment.BodyPose;
import com.aetherianartificer.townstead.root.attachment.BodyValues;
import com.aetherianartificer.townstead.root.attachment.WornAttachments;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Plays a scripted scene on a real client, with no one at the keyboard, and writes down what it finds:
 * the boxes measured from what was drawn beside the pose the server works out, whether a crosshair finds
 * an attachment, and screenshots to look at. Only runs with {@code -Dtownstead.bodyCheck=true}.
 */
@EventBusSubscriber(modid = "townstead_pack_check", value = Dist.CLIENT)
public final class BodyCheckClient {

    private static final boolean ENABLED = Boolean.getBoolean("townstead.bodyCheck");
    private static final int PLATFORM_Y = 150;
    // Each body's platform reaches this far each way, wide enough that nothing walks, flies or is knocked off it, and the bodies
    // are spaced so platforms don't meet.
    private static final int PLATFORM = 12;
    private static final int SPACING = PLATFORM * 2 + 4;
    private static final boolean PROFILED = System.getenv("TOWNSTEAD_BODYCHECK_PROFILE") != null;
    private static final int SAMPLE_SECONDS = Integer.parseInt(System.getenv().getOrDefault("TOWNSTEAD_BODYCHECK_SAMPLE_SECONDS", "10"));

    private record Step(int delay, Runnable action) {}

    private record Subject(String label, String root, int age, BlockPos at) {}

    private static final Deque<Step> STEPS = new ArrayDeque<>();
    private static final List<String> REPORT = new ArrayList<>();
    private static final Map<String, UUID> SPAWNED = new LinkedHashMap<>();
    private static boolean worldAsked, scripted;
    private static int cooldown;
    private static int joinedTicks;

    private BodyCheckClient() {}

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (!worldAsked && mc.getOverlay() == null) {
                if (!(mc.screen instanceof TitleScreen)) {
                    mc.setScreen(new TitleScreen());
                    return;
                }
                worldAsked = true;
                createWorld(mc);
            }
            return;
        }
        // Whatever a mod opens on joining would stop the camera and the attack, so nothing stays open.
        if (mc.screen != null) mc.setScreen(null);
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        if (!scripted) {
            if (++joinedTicks < 60) return;
            scripted = true;
            script(mc);
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        Step step = STEPS.poll();
        if (step == null) return;
        cooldown = step.delay();
        try {
            step.action().run();
        } catch (Throwable t) {
            line("STEP THREW " + t);
            t.printStackTrace();
        }
    }

    private static void createWorld(Minecraft mc) {
        String name = "bodycheck" + System.currentTimeMillis();
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(12345L, false, false),
                registry -> WorldPresets.createNormalWorldDimensions(registry), new TitleScreen());
    }

    private static void script(Minecraft mc) {
        List<Subject> subjects = new ArrayList<>();
        // Adult bodies on each kind of model, then one Root at each age.
        for (String entry : System.getProperty("townstead.bodyCheck.roots", "").split(",")) {
            if (entry.isBlank()) continue;
            String[] parts = entry.split("@");
            int age = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            String label = parts[0].substring(parts[0].indexOf(':') + 1) + (age == 0 ? "" : "_age" + age);
            BlockPos at = new BlockPos(subjects.size() * SPACING, PLATFORM_Y + 1, 0);
            subjects.add(new Subject(label, parts[0], age, at));
        }
        ServerLevel level = mc.getSingleplayerServer().overworld();
        // Long enough for the light on the new platforms to settle before anything is looked at.
        STEPS.add(new Step(200, () -> mc.getSingleplayerServer().execute(() -> build(level, subjects))));
        STEPS.add(new Step(10, () -> mc.getEntityRenderDispatcher().setRenderHitBoxes(true)));
        if (System.getenv("TOWNSTEAD_BODYCHECK_COLOUR") != null) {
            for (Subject subject : subjects) pieceColourScene(mc, level, subject);
            STEPS.add(new Step(5, () -> {
                write();
                mc.stop();
            }));
            return;
        }
        if (System.getenv("TOWNSTEAD_BODYCHECK_PARTS") != null) {
            partsScene(mc, level, subjects);
            STEPS.add(new Step(5, () -> {
                write();
                mc.stop();
            }));
            return;
        }
        if (System.getenv("TOWNSTEAD_BODYCHECK_CROWD") != null) {
            crowdScene(mc, level);
            STEPS.add(new Step(5, () -> {
                write();
                mc.stop();
            }));
            return;
        }
        if (System.getenv("TOWNSTEAD_BODYCHECK_MINE") != null) {
            minedScene(mc, level, subjects);
            STEPS.add(new Step(5, () -> {
                write();
                mc.stop();
            }));
            return;
        }
        if (System.getenv("TOWNSTEAD_BODYCHECK_MEALS") != null) {
            mealsScene(mc, level, subjects);
            STEPS.add(new Step(5, () -> {
                write();
                mc.stop();
            }));
            return;
        }
        if (System.getenv("TOWNSTEAD_BODYCHECK_FLUSH") != null) {
            flushScene(mc, level, subjects);
            STEPS.add(new Step(5, () -> {
                write();
                mc.stop();
            }));
            return;
        }
        if (System.getenv("TOWNSTEAD_BODYCHECK_MESS") != null) {
            messScene(mc, level, subjects);
            STEPS.add(new Step(5, () -> {
                write();
                mc.stop();
            }));
            return;
        }
        for (int s = 0; s < subjects.size(); s++) {
            // The last one plays with parts staying on and no blood.
            if (s == subjects.size() - 1 && subjects.size() > 1) {
                STEPS.add(new Step(10, () -> {
                    line("--- severing and gore off");
                    switchboard(level, "attachments.severing", false);
                    switchboard(level, "attachments.gore", false);
                }));
            }
            scene(mc, level, subjects.get(s));
        }
        if (!subjects.isEmpty()) {
            STEPS.add(new Step(10, () -> {
                switchboard(level, "attachments.severing", true);
                switchboard(level, "attachments.gore", true);
            }));
        }
        STEPS.add(new Step(5, () -> {
            write();
            mc.stop();
        }));
    }

    private static void build(ServerLevel level, List<Subject> subjects) {
        level.setDayTime(6000L);
        level.setWeatherParameters(100000, 0, false, false);
        for (Subject subject : subjects) {
            BlockPos at = subject.at();
            level.setChunkForced(at.getX() >> 4, at.getZ() >> 4, true);
            for (int x = -PLATFORM; x <= PLATFORM; x++) {
                for (int z = -PLATFORM; z <= PLATFORM; z++) {
                    level.setBlock(at.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                    for (int y = 0; y < 5; y++) level.setBlock(at.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
            if (RootRegistry.byId(ResourceLocation.parse(subject.root())) == null) {
                line("MISSING ROOT " + subject.root());
                continue;
            }
            Entity made = EntityType.byString("mca:male_villager").orElseThrow().create(level);
            if (!(made instanceof Mob villager)) continue;
            villager.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
            villager.setYBodyRot(0f);
            villager.setYHeadRot(0f);
            villager.setNoAi(true);
            villager.setPersistenceRequired();
            level.addFreshEntity(villager);
            // Something for what's thrown to land on: a wall behind, with a roof over part of the way.
            for (int x = -3; x <= 3; x++) {
                for (int y = 0; y < 4; y++) level.setBlock(at.offset(x, y, 3), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int z = 1; z < 3; z++) level.setBlock(at.offset(x, 3, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
            }
            RootAssignment.assign(villager, ResourceLocation.parse(subject.root()));
            if (subject.age() != 0) age(level, villager, subject.age());
            SPAWNED.put(subject.label(), villager.getUUID());
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayers().get(0);
        player.setGameMode(GameType.CREATIVE);
    }

    /** Moves a birthday back by whole days, the way the character editor does. */
    private static void age(ServerLevel level, Mob villager, int daysOld) {
        if (!(villager instanceof VillagerEntityMCA mca)) return;
        LifeStageProgression.applyManualAgeEdit(mca, TownsteadCalendar.lifeDay(level.getServer()) - daysOld);
        villager.setNoAi(true);
    }

    private static void scene(Minecraft mc, ServerLevel level, Subject subject) {
        String label = subject.label();
        BlockPos at = subject.at();
        // Aged again here: the life stage isn't stamped yet on the tick a villager spawns.
        if (subject.age() != 0) STEPS.add(new Step(5, () -> serverSubject(level, label, villager -> age(level, (Mob) villager, subject.age()))));
        STEPS.add(new Step(40, () -> mc.getSingleplayerServer().execute(() -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayers().get(0);
            // Behind and to one side of a body facing +z, where what it wears is easy to see.
            player.teleportTo(level, at.getX() + 3.0, at.getY(), at.getZ() - 3.0, 45f, 15f);
            mc.options.hideGui = true;
            UUID id = SPAWNED.get(label);
            if (id != null && level.getEntity(id) instanceof Mob villager) {
                villager.setNoAi(true);
                villager.setDeltaMovement(Vec3.ZERO);
                villager.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                villager.setYBodyRot(0f);
                villager.setYHeadRot(0f);
                villager.setYRot(0f);
            }
        })));
        STEPS.add(new Step(0, () -> measure(mc, level, label, "rest")));
        STEPS.add(new Step(10, () -> shot(mc, label + "_boxes")));
        STEPS.add(new Step(3, () -> mc.getEntityRenderDispatcher().setRenderHitBoxes(false)));
        STEPS.add(new Step(3, () -> shot(mc, label + "_plain")));
        // Close enough to see what's on the head and how the model reads.
        STEPS.add(new Step(5, () -> mc.getSingleplayerServer().execute(() -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayers().get(0);
            player.teleportTo(level, at.getX() + 1.6, at.getY() + 1.0, at.getZ() - 1.4, 50f, 12f);
        })));
        STEPS.add(new Step(5, () -> shot(mc, label + "_close")));
        STEPS.add(new Step(5, () -> mc.getSingleplayerServer().execute(() -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayers().get(0);
            player.teleportTo(level, at.getX() + 3.0, at.getY(), at.getZ() - 3.0, 45f, 15f);
        })));
        STEPS.add(new Step(25, () -> {
            mc.getEntityRenderDispatcher().setRenderHitBoxes(true);
            serverSubject(level, label, villager -> villager.setPose(Pose.CROUCHING));
        }));
        STEPS.add(new Step(5, () -> measure(mc, level, label, "crouch")));
        STEPS.add(new Step(25, () -> serverSubject(level, label, villager -> {
            villager.setPose(Pose.STANDING);
            villager.setYBodyRot(90f);
            villager.setYHeadRot(90f);
            villager.setYRot(90f);
        })));
        STEPS.add(new Step(5, () -> measure(mc, level, label, "turned")));
        STEPS.add(new Step(30, () -> serverSubject(level, label, villager -> {
            villager.setYBodyRot(0f);
            villager.setYHeadRot(0f);
            villager.setYRot(0f);
        })));
        STEPS.add(new Step(5, () -> aim(mc, level, label)));
        // A full swing each time: the cooldown scales damage and could read as a miss.
        STEPS.add(new Step(30, () -> attack(mc, level, label, "part")));
        STEPS.add(new Step(5, () -> {
            LivingEntity client = clientSubject(mc, level, label);
            line(label + " after part swing: " + health(level, label) + "; values "
                    + (client == null ? null : BodyValues.clientValues(client.getId())) + "; parts " + storedParts(level, label));
        }));
        // The same swing at the body, to tell a part that took nothing from a villager that took nothing.
        // The last swing knocked the villager back, out of reach.
        STEPS.add(new Step(10, () -> serverSubject(level, label, villager -> {
            villager.setDeltaMovement(Vec3.ZERO);
            villager.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        })));
        STEPS.add(new Step(5, () -> aimAtBody(mc, level, label)));
        STEPS.add(new Step(30, () -> attack(mc, level, label, "body")));
        STEPS.add(new Step(5, () -> line(label + " after body swing: " + health(level, label))));
        // Every part takes a blow that would end it, to see what a body looks like afterward.
        STEPS.add(new Step(60, () -> serverSubject(level, label, villager -> {
            villager.setDeltaMovement(Vec3.ZERO);
            villager.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            villager.setYBodyRot(0f);
            villager.setYHeadRot(0f);
            villager.setYRot(0f);
            if (villager instanceof Mob mob) line(label + " flight before: " + com.aetherianartificer.townstead.root.locomotion.FlightProfile.of(mob));
            // One piece first: a pair should keep its other half.
            for (AttachmentDef def : WornAttachments.of(villager)) {
                if (def.body().part() != null) line(label + " strike one piece of " + def.id() + ": " + BodyParts.strike(villager, def.id(), 0, 1000f));
            }
        })));
        STEPS.add(new Step(4, () -> {
            mc.getEntityRenderDispatcher().setRenderHitBoxes(false);
            LivingEntity client = clientSubject(mc, level, label);
            if (client != null) mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, client.position().add(0.0, 0.7, 0.0));
        }));
        STEPS.add(new Step(30, () -> shot(mc, label + "_falling")));
        STEPS.add(new Step(10, () -> {
            line(label + " after one piece: " + storedParts(level, label));
            shot(mc, label + "_one_piece");
        }));
        STEPS.add(new Step(60, () -> serverSubject(level, label, villager -> {
            for (AttachmentDef def : WornAttachments.of(villager)) {
                if (def.body().part() != null) line(label + " strike " + def.id() + ": " + BodyParts.strike(villager, def.id(), 1000f));
            }
            if (villager instanceof Mob mob) line(label + " flight after: " + com.aetherianartificer.townstead.root.locomotion.FlightProfile.of(mob));
        })));
        STEPS.add(new Step(3, () -> {
            LivingEntity client = clientSubject(mc, level, label);
            line(label + " after strikes: " + health(level, label) + "; values "
                    + (client == null ? null : BodyValues.clientValues(client.getId())) + "; parts " + storedParts(level, label));
            measure(mc, level, label, "struck");
            mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, client == null ? Vec3.ZERO : client.position().add(0.0, 1.0, 0.0));
            mc.getEntityRenderDispatcher().setRenderHitBoxes(false);
        }));
        STEPS.add(new Step(3, () -> shot(mc, label + "_struck")));
        STEPS.add(new Step(3, () -> mc.getEntityRenderDispatcher().setRenderHitBoxes(true)));
    }

    /** A few hard blows on a body that stands still, then long enough for what it drips to gather. */
    /** The same parts from the same place, on the body and then just off it, held still to compare colours. */
    private static void pieceColourScene(Minecraft mc, ServerLevel level, Subject subject) {
        String label = subject.label();
        BlockPos at = subject.at();
        STEPS.add(new Step(40, () -> mc.getSingleplayerServer().execute(() -> {
            mc.options.hideGui = true;
            mc.getEntityRenderDispatcher().setRenderHitBoxes(false);
            switchboard(level, "attachments.severing", true);
            switchboard(level, "attachments.gore", false);
            level.getServer().getPlayerList().getPlayers().get(0).teleportTo(level, at.getX() + 2.2, at.getY(), at.getZ() - 2.0, 45f, 10f);
        })));
        STEPS.add(new Step(30, () -> {
            LivingEntity client = clientSubject(mc, level, label);
            if (client != null) mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, client.position().add(0.0, 0.9, 0.0));
        }));
        STEPS.add(new Step(10, () -> shot(mc, label + "_colour_on")));
        STEPS.add(new Step(5, () -> serverSubject(level, label, villager -> {
            for (AttachmentDef def : WornAttachments.of(villager)) {
                if (def.body().part() != null) BodyParts.strike(villager, def.id(), 1000f);
            }
        })));
        for (int i = 0; i < 20; i++) STEPS.add(new Step(1, BodyCheckClient::holdPiecesStill));
        STEPS.add(new Step(1, () -> shot(mc, label + "_colour_off")));
        for (int i = 0; i < 5; i++) STEPS.add(new Step(1, BodyCheckClient::holdPiecesStill));
    }

    private static final Map<Object, double[]> HELD = new java.util.IdentityHashMap<>();

    private static void holdPiecesStill() {
        try {
            var list = com.aetherianartificer.townstead.client.attachment.FallingPieces.class.getDeclaredField("PIECES");
            list.setAccessible(true);
            for (Object piece : (List<?>) list.get(null)) {
                double[] held = HELD.computeIfAbsent(piece, p -> new double[]{number(p, "x"), number(p, "y"), number(p, "z")});
                String[] axes = {"x", "y", "z"};
                for (int a = 0; a < 3; a++) {
                    set(piece, axes[a], held[a]);
                    set(piece, "last" + axes[a].toUpperCase(), held[a]);
                    set(piece, "v" + axes[a], 0.0);
                }
                var rate = piece.getClass().getDeclaredField("rate");
                rate.setAccessible(true);
                rate.setFloat(piece, 0f);
                for (String spin : List.of("spin", "lastSpin")) {
                    var field = piece.getClass().getDeclaredField(spin);
                    field.setAccessible(true);
                    ((org.joml.Quaternionf) field.get(piece)).identity();
                }
            }
        } catch (ReflectiveOperationException e) {
            line("hold pieces: " + e);
        }
    }

    private static double number(Object piece, String name) {
        try {
            var field = piece.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.getDouble(piece);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void set(Object piece, String name, double value) throws ReflectiveOperationException {
        var field = piece.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setDouble(piece, value);
    }

    /** Each kind of liquid thrown at a wall, a roof and the floor, to look at how they differ. */
    /**
     * Bit of a Mess on each body, close up, to see that it sits on what it's on: dust and sweat on the skin, clothes and hair, and
     * marks on what it wears, from the front, the side and behind.
     */
    private static void flushScene(Minecraft mc, ServerLevel level, List<Subject> subjects) {
        STEPS.add(new Step(60, () -> mc.getSingleplayerServer().execute(() -> {
            mc.options.hideGui = true;
            mc.getEntityRenderDispatcher().setRenderHitBoxes(false);
            ServerPlayer player = level.getServer().getPlayerList().getPlayers().get(0);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        })));
        for (Subject subject : subjects) {
            BlockPos at = subject.at();
            String label = subject.label();
            STEPS.add(new Step(40, () -> mc.getSingleplayerServer().execute(() -> {
                for (int x = -3; x <= 3; x++) {
                    for (int y = 0; y < 4; y++) {
                        for (int z = -3; z <= 3; z++) if (z != 0 || x != 0) level.setBlock(at.offset(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            })));
            STEPS.add(new Step(30, () -> serverSubject(level, label, villager -> {
                villager.setYBodyRot(0f);
                villager.setYHeadRot(0f);
                villager.setYRot(0f);
                villager.setDeltaMovement(Vec3.ZERO);
                villager.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                if (villager instanceof Mob mob) mob.setNoAi(true);
                // Made without spawning, a villager has no clothes or hair yet: spawned for real, then given its Root again.
                if (com.aetherianartificer.bitofamess.trace.Outfits.of(villager) == null && villager instanceof Mob mob) {
                    mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), net.minecraft.world.entity.MobSpawnType.COMMAND, null);
                    RootAssignment.assign(mob, ResourceLocation.parse(subject.root()));
                    mob.setNoAi(true);
                }
                int which = subjects.indexOf(subject);
                boolean spiderFolk = label.contains("arachnid") || label.contains("webster");
                if (which % 3 == 1 && !spiderFolk) {
                    villager.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
                    villager.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
                } else if (label.contains("arachnid") || label.contains("webster")) {
                    // Spider-folk try on the scarf, by the same rule a player handing one over meets.
                    villager.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_LEGGINGS));
                    net.minecraft.world.item.ItemStack scarf = new net.minecraft.world.item.ItemStack(com.aetherianartificer.townstead.Townstead.SCARF.get());
                    boolean fits = com.aetherianartificer.townstead.item.ScarfEquip.mayWear(villager, scarf);
                    boolean curio = fits && com.aetherianartificer.townstead.compat.curios.CuriosCompat.present()
                            && com.aetherianartificer.townstead.compat.curios.CuriosCompat.equipFirstFree(villager, scarf);
                    line(label + " scarf fits: " + fits + ", in a Curios slot: " + curio + " (Curios " + com.aetherianartificer.townstead.compat.curios.CuriosCompat.present() + ")");
                }
                Object hair;
                try {
                    hair = com.aetherianartificer.bitofamess.trace.Outfits.class.getMethod("hairOf", LivingEntity.class).invoke(null, villager);
                } catch (ReflectiveOperationException e) {
                    hair = "unknown to this Bit of a Mess";
                }
                line(label + " outfit " + com.aetherianartificer.bitofamess.trace.Outfits.of(villager) + " hair " + hair);
            })));
            // Where a body goes, tick by tick, once placed with no AI: it should stay put.
            for (int i = 0; i < 30; i++) {
                final int tick = i * 2;
                STEPS.add(new Step(2, () -> serverSubject(level, label, villager -> {
                    String how = villager instanceof Mob mob ? "noAi " + mob.isNoAi() + " nav " + mob.getNavigation().isInProgress()
                            + " loco " + com.aetherianartificer.townstead.root.locomotion.Locomotion.describe(mob) : "";
                    line(String.format(java.util.Locale.ROOT, "%s move t%d at %.2f,%.2f,%.2f v %.3f,%.3f,%.3f ground %s %s vehicle %s pose %s",
                            label, tick, villager.getX(), villager.getY(), villager.getZ(), villager.getDeltaMovement().x, villager.getDeltaMovement().y,
                            villager.getDeltaMovement().z, villager.onGround(), how, villager.getVehicle(), villager.getPose()));
                })));
            }
            flushShots(mc, level, at, label + "_clean");
            STEPS.add(new Step(10, () -> serverSubject(level, label, villager -> {
                com.aetherianartificer.bitofamess.trace.BodyMarks.settle(villager, "bitofamess:sand", 2.5f, "all");
                com.aetherianartificer.bitofamess.trace.BodyMarks.settle(villager, "bitofamess:flour", 1.5f, "high");
                for (String bit : List.of("petals", "leaf_bits", "spider_silk")) {
                    com.aetherianartificer.bitofamess.trace.BodyMarks.settle(villager, "bitofamess:" + bit, 0.5f, "all");
                    com.aetherianartificer.bitofamess.trace.BodyMarks.settle(villager, "bitofamess:" + bit, 0.3f, "high");
                }
                try {
                    var sweat = com.aetherianartificer.bitofamess.trace.BodyMarks.class.getMethod("sweat", LivingEntity.class, boolean.class);
                    for (int i = 0; i < 40; i++) sweat.invoke(null, villager, false);
                } catch (ReflectiveOperationException e) {
                    line(label + " no sweat in this Bit of a Mess");
                }
                for (AttachmentDef def : WornAttachments.of(villager)) {
                    for (var box : com.aetherianartificer.townstead.root.attachment.HitShapes.boxes(villager, def)) {
                        Vec3 center = box.getCenter().add(villager.position());
                        for (Vec3 side : List.of(new Vec3(0.0, 0.0, -1.6), new Vec3(1.6, 0.0, 0.0), new Vec3(-1.6, 0.0, 0.0), new Vec3(0.0, 1.6, 0.0))) {
                            com.aetherianartificer.bitofamess.world.MessWorld.of(level).spray("bitofamess:ink", center.add(side), side.reverse(), 5f,
                                    com.aetherianartificer.bitofamess.material.MessSpray.get("bitofamess:spurt"));
                        }
                    }
                }
            })));
            STEPS.add(new Step(40, () -> serverSubject(level, label, villager -> {
                var marks = villager.getPersistentData().getCompound("BitOfAMessMarks");
                StringBuilder out = new StringBuilder();
                for (String key : marks.getAllKeys()) out.append(' ').append(key).append('=').append(marks.getIntArray(key).length);
                line(label + " marks:" + out);
            })));
            flushShots(mc, level, at, label + "_marked");
        }
    }

    /** Close up from the front, the side and behind, at head height and at the waist. */
    private static void flushShots(Minecraft mc, ServerLevel level, BlockPos at, String name) {
        // Where the eye goes, from the body's feet, and which way it looks. A player's eye can't be lower than it stands.
        double[][] views = {{0.5, 1.7, 2.1, 180f, 10f}, {2.1, 1.7, 0.5, 90f, 10f}, {0.5, 1.7, -1.6, 0f, 10f}, {0.5, 1.7, 1.5, 180f, 32f}, {0.5, 2.6, 1.3, 180f, 60f}};
        String[] names = {"front", "side", "back", "waist", "above"};
        for (int v = 0; v < views.length; v++) {
            double[] view = views[v];
            String shotName = "flush_" + name + "_" + names[v];
            STEPS.add(new Step(20, () -> mc.getSingleplayerServer().execute(() -> {
                ServerPlayer player = level.getServer().getPlayerList().getPlayers().get(0);
                player.teleportTo(level, at.getX() + view[0], at.getY() + view[1] - 1.62, at.getZ() + view[2], (float) view[3], (float) view[4]);
            })));
            STEPS.add(new Step(5, () -> {
                shot(mc, shotName);
                line(shotName + " particles near: " + particlesNear(mc, 4.0));
                StringBuilder bodies = new StringBuilder();
                for (Map.Entry<String, UUID> entry : SPAWNED.entrySet()) {
                    Entity body = level.getEntity(entry.getValue());
                    bodies.append(' ').append(entry.getKey()).append('=').append(body == null ? "gone" : String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f%s%s",
                            body.getX(), body.getY(), body.getZ(), body.isRemoved() ? " removed" : "", body instanceof LivingEntity living && !living.isAlive() ? " dead" : ""));
                }
                line(shotName + " bodies:" + bodies);
            }));
        }
    }

    /** What kinds of particle are within {@code range} of the camera, and how many of each. */
    private static String particlesNear(Minecraft mc, double range) {
        Map<String, Integer> counts = new java.util.TreeMap<>();
        try {
            var field = net.minecraft.client.particle.ParticleEngine.class.getDeclaredField("particles");
            field.setAccessible(true);
            Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
            for (Object queue : ((Map<?, ?>) field.get(mc.particleEngine)).values()) {
                for (Object particle : (Iterable<?>) queue) {
                    var box = ((net.minecraft.client.particle.Particle) particle).getBoundingBox();
                    if (box.getCenter().distanceTo(eye) <= range) counts.merge(particle.getClass().getName(), 1, Integer::sum);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "unreadable " + e;
        }
        return counts.toString();
    }

    /** Each Root hit and sprayed through Bit of a Mess, close up, then washed. */
    /** Ink on everything a body wears, a blow on it, then each comes off with what's on it, then grows back clean. */
    private static void partsScene(Minecraft mc, ServerLevel level, List<Subject> subjects) {
        STEPS.add(new Step(60, () -> mc.getSingleplayerServer().execute(() -> {
            mc.options.hideGui = true;
            mc.getEntityRenderDispatcher().setRenderHitBoxes(false);
            ServerPlayer player = level.getServer().getPlayerList().getPlayers().get(0);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        })));
        for (Subject subject : subjects) {
            BlockPos at = subject.at();
            String label = subject.label();
            STEPS.add(new Step(40, () -> mc.getSingleplayerServer().execute(() -> {
                ServerPlayer player = level.getServer().getPlayerList().getPlayers().get(0);
                player.teleportTo(level, at.getX() + 5.0, at.getY() + 0.4, at.getZ() - 0.5, 90f, 6f);
                for (int x = -3; x <= 3; x++) {
                    for (int y = 0; y < 4; y++) {
                        for (int z = -3; z <= 3; z++) if (z != 0 || x != 0) level.setBlock(at.offset(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            })));
            STEPS.add(new Step(30, () -> serverSubject(level, label, villager -> {
                villager.setYBodyRot(0f);
                villager.setYHeadRot(0f);
                villager.setYRot(0f);
                villager.setDeltaMovement(Vec3.ZERO);
                villager.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                if (villager instanceof Mob mob) mob.setNoAi(true);
                for (AttachmentDef def : WornAttachments.declared(villager)) {
                    line(label + " wears " + def.id() + " part " + (def.body().part() != null) + " boxes " + com.aetherianartificer.townstead.root.attachment.HitShapes.boxes(villager, def).size());
                }
            })));
            STEPS.add(new Step(5, () -> shot(mc, "parts_" + label + "_clean")));
            STEPS.add(new Step(10, () -> serverSubject(level, label, villager -> {
                ServerPlayer player = level.getServer().getPlayerList().getPlayers().get(0);
                villager.invulnerableTime = 0;
                player.teleportTo(level, at.getX() + 0.5, at.getY() - 0.2, at.getZ() - 1.5, 0f, 0f);
                com.aetherianartificer.bitofamess.world.Blows.onHurt(villager, level.damageSources().playerAttack(player), 3f);
                line(label + " after a hit in front: " + villager.getPersistentData().getCompound("BitOfAMessMarks").getAllKeys());
                for (AttachmentDef def : WornAttachments.of(villager)) {
                    if (def.body().part() == null) continue;
                    for (var box : com.aetherianartificer.townstead.root.attachment.HitShapes.boxes(villager, def)) {
                        com.aetherianartificer.bitofamess.world.Blows.onPartHit(villager, box.getCenter().add(villager.position()), new Vec3(0.0, 0.0, 1.0), 3f);
                        line(label + " after a hit on " + def.id() + " bleeds=" + def.body().part().bleeds() + ": " + villager.getPersistentData().getCompound("BitOfAMessMarks").getAllKeys());
                        break;
                    }
                }
                player.teleportTo(level, at.getX() + 5.0, at.getY() + 0.4, at.getZ() - 0.5, 90f, 6f);
            })));
            STEPS.add(new Step(30, () -> serverSubject(level, label, villager -> {
                for (AttachmentDef def : WornAttachments.of(villager)) {
                    for (var box : com.aetherianartificer.townstead.root.attachment.HitShapes.boxes(villager, def)) {
                        Vec3 center = box.getCenter().add(villager.position());
                        for (int i = 0; i < 1; i++) {
                            for (Vec3 side : List.of(new Vec3(0.0, 0.0, -1.6), new Vec3(1.6, 0.0, 0.0), new Vec3(-1.6, 0.0, 0.0), new Vec3(0.0, 1.6, 0.0))) {
                                com.aetherianartificer.bitofamess.world.MessWorld.of(level).spray("bitofamess:ink", center.add(side), side.reverse(), 5f,
                                        com.aetherianartificer.bitofamess.material.MessSpray.get("bitofamess:spurt"));
                            }
                        }
                        com.aetherianartificer.bitofamess.world.Blows.onPartHit(villager, center, new Vec3(0.0, 0.0, 1.0), 9f);
                    }
                }
            })));
            STEPS.add(new Step(5, () -> serverSubject(level, label, villager -> {
                var marks = villager.getPersistentData().getCompound("BitOfAMessMarks");
                StringBuilder out = new StringBuilder();
                for (String key : marks.getAllKeys()) out.append(' ').append(key).append('=').append(marks.getIntArray(key).length);
                line(label + " marks:" + out);
                mc.execute(() -> shot(mc, "parts_" + label + "_marked"));
            })));
            STEPS.add(new Step(40, () -> {}));
            STEPS.add(new Step(6, () -> serverSubject(level, label, villager -> {
                for (AttachmentDef def : WornAttachments.declared(villager)) {
                    if (def.body().part() != null) line(label + " cut " + def.id() + ": " + BodyParts.strike(villager, def.id(), 9999f));
                }
            })));
            STEPS.add(new Step(14, () -> shot(mc, "parts_" + label + "_falling")));
            STEPS.add(new Step(30, () -> shot(mc, "parts_" + label + "_fallen")));
            STEPS.add(new Step(10, () -> serverSubject(level, label, villager -> {
                BodyParts.restore(villager, "");
                StringBuilder left = new StringBuilder();
                for (String key : villager.getPersistentData().getCompound("BitOfAMessMarks").getAllKeys()) if (key.contains("~")) left.append(' ').append(key);
                line(label + " part marks after regrowing:" + left);
            })));
            STEPS.add(new Step(5, () -> shot(mc, "parts_" + label + "_regrown")));
        }
    }

    /**
     * A thousand real villagers: what they cost a tick while Traces look at each, then with a hundred marked.
     * Packed and still by default; TOWNSTEAD_BODYCHECK_CROWD=spaced stands them 7 blocks apart with AI on and one in seven young.
     */
    private static void crowdScene(Minecraft mc, ServerLevel level) {
        boolean spaced = "spaced".equals(System.getenv("TOWNSTEAD_BODYCHECK_CROWD"));
        int floor = spaced ? 115 : 30;
        // Keeps drawing the packed crowd from competing with the server for the CPU.
        boolean lookAway = "away".equals(System.getenv("TOWNSTEAD_BODYCHECK_CROWD"));
        List<LivingEntity> crowd = new ArrayList<>();
        BlockPos at = BlockPos.containing(mc.player.position()).offset(0, 40, 0);
        com.aetherianartificer.bitofamess.world.MessStats.on = true;
        STEPS.add(new Step(100, () -> mc.getSingleplayerServer().execute(() -> {
            for (int x = -floor; x <= floor; x++) {
                for (int z = -floor; z <= floor; z++) {
                    level.setBlock(at.offset(x, -1, z), net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                    if (spaced && (Math.abs(x) == floor || Math.abs(z) == floor)) {
                        for (int y = 0; y < 2; y++) level.setBlock(at.offset(x, y, z), Blocks.BARRIER.defaultBlockState(), 2);
                    }
                }
            }
            ServerPlayer player = level.getServer().getPlayerList().getPlayers().get(0);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(level, at.getX(), at.getY() + 6.0, at.getZ() - (spaced ? 0.0 : 34.0), lookAway ? 180f : 0f, 20f);
            var type = net.minecraft.world.entity.EntityType.byString("mca:male_villager").orElseThrow();
            for (int i = 0; i < 1000; i++) {
                if (!(type.create(level) instanceof Mob villager)) break;
                if (spaced) {
                    villager.moveTo(at.getX() + i % 32 * 7.0 - 108.5, at.getY(), at.getZ() + i / 32 * 7.0 - 108.5, 0f, 0f);
                } else {
                    villager.moveTo(at.getX() + i % 32 * 1.8 - 28.0, at.getY(), at.getZ() + i / 32 * 1.8 - 28.0, 0f, 0f);
                    villager.setNoAi(true);
                }
                villager.setPersistenceRequired();
                level.addFreshEntity(villager);
                if (COUNTED) {
                    BlockPos cell = villager.blockPosition();
                    level.setBlock(cell.offset(2, 0, 0), Blocks.WHITE_BED.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.EAST), 3);
                    level.setBlock(cell.offset(3, 0, 0), Blocks.WHITE_BED.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.EAST)
                            .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD), 3);
                    if (i % 2 == 0) level.setBlock(cell.offset(-2, 0, 0), COUNT_JOBS.get((i / 2) % COUNT_JOBS.size()).defaultBlockState(), 3);
                }
                if (spaced && i % 7 == 0 && villager instanceof VillagerEntityMCA young) young.setAge(-100000);
                crowd.add(villager);
            }
            line("crowd: " + crowd.size() + " villagers");
        })));
        // Past the spawn tick and the first stamping, and with nothing else sampling: what a settled tick costs.
        STEPS.add(new Step(400, () -> {}));
        // Three windows, each with the server thread's CPU time, which other programs on the PC barely move.
        for (int window = 0; window < 3; window++) {
            int[] timedFrom = new int[1];
            long[] cpuFrom = new long[1];
            STEPS.add(new Step(300, () -> {
                timedFrom[0] = level.getServer().getTickCount();
                cpuFrom[0] = serverCpuNanos(mc);
            }));
            STEPS.add(new Step(0, () -> line(settledTicks(level, timedFrom[0]) + String.format(", cpu %.1f ms/tick",
                    (serverCpuNanos(mc) - cpuFrom[0]) / (double) Math.max(1, level.getServer().getTickCount() - timedFrom[0]) / 1e6))));
        }
        // TOWNSTEAD_BODYCHECK_COUNT asks how often each behavior's question has a new answer, then stops.
        if (COUNTED) {
            STEPS.add(new Step(COUNT_SECONDS * 20, () -> mc.getSingleplayerServer().execute(() -> wrapBehaviors(crowd))));
            STEPS.add(new Step(5, () -> mc.getSingleplayerServer().execute(() -> {
                counting = false;
                line(countReport());
            })));
            return;
        }
        // TOWNSTEAD_BODYCHECK_QUICK stops at the settled numbers.
        if (System.getenv("TOWNSTEAD_BODYCHECK_QUICK") != null) return;
        STEPS.add(new Step(SAMPLE_SECONDS * 20, () -> {
            com.aetherianartificer.bitofamess.world.MessStats.report(1);
            sampleServer(mc);
            if (PROFILED) {
                com.aetherianartificer.townstead.diagnostics.TownsteadProfiler.clear();
                com.aetherianartificer.townstead.diagnostics.TownsteadProfiler.setEnabled(true);
            }
        }));
        STEPS.add(new Step(5, () -> mc.getSingleplayerServer().execute(() -> line(crowdCensus(level, crowd)))));
        if (PROFILED) STEPS.add(new Step(5, BodyCheckClient::reportProfile));
        STEPS.add(new Step(5, () -> line("==== 1000 still villagers: " + mc.getFps() + " fps, whole server " + tickTime(level) + com.aetherianartificer.bitofamess.world.MessStats.report(200))));
        STEPS.add(new Step(100, () -> mc.getSingleplayerServer().execute(() -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayers().get(0);
            for (int i = 0; i < 100 && i < crowd.size(); i++) {
                LivingEntity body = crowd.get(i * 10);
                player.setPos(body.getX(), body.getY() + 1.0, body.getZ() - 2.0);
                com.aetherianartificer.bitofamess.world.Blows.onHurt(body, level.damageSources().playerAttack(player), 4f);
            }
            player.teleportTo(level, at.getX(), at.getY() + 6.0, at.getZ() - 34.0, lookAway ? 180f : 0f, 20f);
        })));
        STEPS.add(new Step(100, () -> {
            line("==== after a hundred of them were hit once: " + mc.getFps() + " fps, whole server " + tickTime(level) + com.aetherianartificer.bitofamess.world.MessStats.report(100));
            shot(mc, "crowd");
        }));
        if (spaced) careCheck(mc, level, crowd);
    }

    /** The villager steps by time spent, with a nanoTime around each: slightly slows the run, so kept apart. */
    private static void reportProfile() {
        var profiler = com.aetherianartificer.townstead.diagnostics.TownsteadProfiler.snapshot();
        com.aetherianartificer.townstead.diagnostics.TownsteadProfiler.setEnabled(false);
        StringBuilder out = new StringBuilder("==== villager steps (with 1000 villagers, us/call is also ms/tick)");
        profiler.rows().stream().limit(45).forEach(row -> out.append(String.format("\n      %6.2f us/call  %8d calls  %s", row.microsPerCall(), row.calls(), row.name())));
        line(out.toString());
    }

    private static final boolean COUNTED = System.getenv("TOWNSTEAD_BODYCHECK_COUNT") != null;
    private static final int COUNT_SECONDS = 60;
    private static final int COUNT_SAMPLE = 100;
    private static final List<net.minecraft.world.level.block.Block> COUNT_JOBS = List.of(Blocks.COMPOSTER, Blocks.LECTERN, Blocks.BARREL,
            Blocks.SMOKER, Blocks.BLAST_FURNACE, Blocks.LOOM, Blocks.CARTOGRAPHY_TABLE, Blocks.FLETCHING_TABLE, Blocks.GRINDSTONE,
            Blocks.STONECUTTER, Blocks.SMITHING_TABLE, Blocks.BREWING_STAND, Blocks.CAULDRON);
    private static final Map<String, Asked> ASKED = new java.util.HashMap<>();
    private static final Map<VillagerEntityMCA, java.util.Set<Object>> LAST_MEMORIES = new java.util.IdentityHashMap<>();
    private static boolean counting;
    private static long countedTicks, memoryChecks, memoryChanges;

    private static final class Asked {
        long stopped, asked, started, blockedByMemory;
    }

    /** A behavior as the brain sees it, counting each time it is asked to start and what it answered. */
    private static final class CountingBehavior implements net.minecraft.world.entity.ai.behavior.BehaviorControl<LivingEntity> {
        private final net.minecraft.world.entity.ai.behavior.BehaviorControl<LivingEntity> real;
        private final net.minecraft.world.entity.ai.behavior.Behavior<?> gated;
        private final Asked asked;
        private final java.lang.reflect.Method required;

        CountingBehavior(net.minecraft.world.entity.ai.behavior.BehaviorControl<LivingEntity> real, Object inner, Asked asked, java.lang.reflect.Method required) {
            this.real = real;
            this.gated = inner instanceof net.minecraft.world.entity.ai.behavior.Behavior<?> behavior ? behavior : null;
            this.asked = asked;
            this.required = required;
        }

        @Override public net.minecraft.world.entity.ai.behavior.Behavior.Status getStatus() { return real.getStatus(); }
        @Override public void tickOrStop(ServerLevel level, LivingEntity entity, long time) { real.tickOrStop(level, entity, time); }
        @Override public void doStop(ServerLevel level, LivingEntity entity, long time) { real.doStop(level, entity, time); }
        @Override public String debugString() { return real.debugString(); }

        @Override
        public boolean tryStart(ServerLevel level, LivingEntity entity, long time) {
            if (!counting) return real.tryStart(level, entity, time);
            asked.asked++;
            if (gated != null) {
                try {
                    if (!(boolean) required.invoke(gated, entity)) asked.blockedByMemory++;
                } catch (ReflectiveOperationException ignored) {
                    // Counts only; the real answer below is untouched.
                }
            }
            boolean started = real.tryStart(level, entity, time);
            if (started) asked.started++;
            return started;
        }
    }

    @SuppressWarnings("unchecked")
    private static void wrapBehaviors(List<LivingEntity> crowd) {
        try {
            var field = net.minecraft.world.entity.ai.Brain.class.getDeclaredField("availableBehaviorsByPriority");
            field.setAccessible(true);
            var gatedField = com.aetherianartificer.townstead.switchboard.GatedBehavior.class.getDeclaredField("delegate");
            gatedField.setAccessible(true);
            var required = net.minecraft.world.entity.ai.behavior.Behavior.class.getDeclaredMethod("hasRequiredMemories", LivingEntity.class);
            required.setAccessible(true);
            int wrapped = 0;
            for (LivingEntity body : crowd) {
                if (wrapped >= COUNT_SAMPLE) break;
                if (!(body instanceof VillagerEntityMCA villager) || !villager.isAlive()) continue;
                wrapped++;
                LAST_MEMORIES.put(villager, null);
                for (Object byActivity : ((Map<?, ?>) field.get(villager.getBrain())).values()) {
                    for (Object set : ((Map<?, ?>) byActivity).values()) {
                        java.util.Set<Object> behaviors = (java.util.Set<Object>) set;
                        List<Object> originals = new ArrayList<>(behaviors);
                        behaviors.clear();
                        for (Object original : originals) {
                            Object inner = original instanceof com.aetherianartificer.townstead.switchboard.GatedBehavior<?> ? gatedField.get(original) : original;
                            String name = inner.getClass().getName();
                            String owner = name.startsWith("com.aetherianartificer.townstead") ? "townstead " : name.startsWith("net.conczin.mca") ? "mca " : "vanilla ";
                            String key = owner + name.substring(name.lastIndexOf('.') + 1);
                            behaviors.add(new CountingBehavior((net.minecraft.world.entity.ai.behavior.BehaviorControl<LivingEntity>) original, inner,
                                    ASKED.computeIfAbsent(key, ignored -> new Asked()), required));
                        }
                    }
                }
            }
            counting = true;
        } catch (ReflectiveOperationException e) {
            line("COUNT FAILED " + e);
        }
    }

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (!counting) return;
        countedTicks++;
        try {
            var field = net.minecraft.world.entity.ai.Brain.class.getDeclaredField("memories");
            field.setAccessible(true);
            for (Map.Entry<VillagerEntityMCA, java.util.Set<Object>> entry : LAST_MEMORIES.entrySet()) {
                java.util.Set<Object> present = new java.util.HashSet<>();
                for (Map.Entry<?, ?> memory : ((Map<?, ?>) field.get(entry.getKey().getBrain())).entrySet()) {
                    if (((java.util.Optional<?>) memory.getValue()).isPresent()) present.add(memory.getKey());
                }
                if (entry.getValue() != null) {
                    memoryChecks++;
                    if (!present.equals(entry.getValue())) memoryChanges++;
                }
                entry.setValue(present);
            }
        } catch (ReflectiveOperationException e) {
            line("COUNT FAILED " + e);
            counting = false;
        }
    }

    private static String countReport() {
        long villagerTicks = countedTicks * LAST_MEMORIES.size();
        long asked = 0, started = 0, blocked = 0;
        for (Asked a : ASKED.values()) {
            asked += a.asked;
            started += a.started;
            blocked += a.blockedByMemory;
        }
        StringBuilder out = new StringBuilder(String.format(
                "==== repeated checks: %d villagers, %d ticks. Start checks per villager per tick %.1f, of which started %.3f%%, stopped by memory alone %.1f%%. Villager ticks where any memory appeared or cleared: %.1f%%",
                LAST_MEMORIES.size(), countedTicks, asked / (double) Math.max(1, villagerTicks), 100.0 * started / Math.max(1, asked),
                100.0 * blocked / Math.max(1, asked), 100.0 * memoryChanges / Math.max(1, memoryChecks)));
        ASKED.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue().asked, x.getValue().asked)).limit(70)
                .forEach(e -> out.append(String.format("\n      %7.3f /villager/tick  started %5.2f%%  memory no %5.1f%%  %s",
                        e.getValue().asked / (double) Math.max(1, villagerTicks), 100.0 * e.getValue().started / Math.max(1, e.getValue().asked),
                        100.0 * e.getValue().blockedByMemory / Math.max(1, e.getValue().asked), e.getKey())));
        return out.toString();
    }

    private static String crowdCensus(ServerLevel level, List<LivingEntity> crowd) {
        int ticking = 0, young = 0;
        for (LivingEntity body : crowd) {
            if (level.isPositionEntityTicking(body.blockPosition())) ticking++;
            if (body instanceof VillagerEntityMCA villager && isYoung(villager)) young++;
        }
        return "census: " + ticking + " of " + crowd.size() + " ticking, " + young + " young" + brainCensus(crowd);
    }

    /** How many behaviors one adult's brain loops over each tick, and whose they are. */
    private static String brainCensus(List<LivingEntity> crowd) {
        for (LivingEntity body : crowd) {
            if (!(body instanceof VillagerEntityMCA villager) || isYoung(villager)) continue;
            try {
                var field = net.minecraft.world.entity.ai.Brain.class.getDeclaredField("availableBehaviorsByPriority");
                field.setAccessible(true);
                var gated = com.aetherianartificer.townstead.switchboard.GatedBehavior.class.getDeclaredField("delegate");
                gated.setAccessible(true);
                Map<String, Integer> owners = new java.util.TreeMap<>();
                Map<String, Integer> activities = new java.util.TreeMap<>();
                int total = 0;
                for (Object byActivity : ((Map<?, ?>) field.get(villager.getBrain())).values()) {
                    for (Map.Entry<?, ?> entry : ((Map<?, ?>) byActivity).entrySet()) {
                        for (Object behavior : (Iterable<?>) entry.getValue()) {
                            Object real = behavior instanceof com.aetherianartificer.townstead.switchboard.GatedBehavior<?> ? gated.get(behavior) : behavior;
                            String name = real.getClass().getName();
                            owners.merge(name.startsWith("com.aetherianartificer.townstead") ? "townstead" : name.startsWith("net.conczin.mca") ? "mca" : "vanilla or wrapper", 1, Integer::sum);
                            activities.merge(entry.getKey().toString(), 1, Integer::sum);
                            total++;
                        }
                    }
                }
                return "\nbrain: " + total + " behaviors " + owners + " by activity " + activities;
            } catch (ReflectiveOperationException e) {
                return "\nbrain: " + e;
            }
        }
        return "";
    }

    private static boolean isYoung(VillagerEntityMCA villager) {
        var age = net.conczin.mca.entity.ai.relationship.AgeState.byCurrentAge(villager.getAge());
        return age == net.conczin.mca.entity.ai.relationship.AgeState.BABY
                || age == net.conczin.mca.entity.ai.relationship.AgeState.TODDLER
                || age == net.conczin.mca.entity.ai.relationship.AgeState.CHILD;
    }

    /** Whether caregivers still feed: twenty young made hungry, with bread on every adult. */
    private static void careCheck(Minecraft mc, ServerLevel level, List<LivingEntity> crowd) {
        List<VillagerEntityMCA> hungry = new ArrayList<>();
        STEPS.add(new Step(5, () -> mc.getSingleplayerServer().execute(() -> {
            for (LivingEntity body : crowd) {
                if (!(body instanceof VillagerEntityMCA villager) || !villager.isAlive()) continue;
                if (!isYoung(villager)) {
                    villager.getInventory().addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 4));
                } else if (hungry.size() < 20) {
                    com.aetherianartificer.townstead.villager.TownsteadVillagers.get(villager).needs().setHunger(10);
                    hungry.add(villager);
                }
            }
        })));
        int[] startTick = new int[1];
        STEPS.add(new Step(0, () -> mc.getSingleplayerServer().execute(() -> startTick[0] = level.getServer().getTickCount())));
        for (int wait = 1; wait <= 18; wait++) {
            int seconds = wait * 5;
            STEPS.add(new Step(100, () -> mc.getSingleplayerServer().execute(() -> {
                int fed = 0;
                for (VillagerEntityMCA villager : hungry) {
                    if (com.aetherianartificer.townstead.villager.TownsteadVillagers.get(villager).needs().hunger() > 10) fed++;
                }
                line("fed after " + seconds + " s (" + (level.getServer().getTickCount() - startTick[0]) + " server ticks): " + fed + " of " + hungry.size());
            })));
        }
    }

    /** Ten seconds of what the server thread is doing, by whose code is on top, and the busiest frames of each. */
    private static void sampleServer(Minecraft mc) {
        Thread server = mc.getSingleplayerServer().getRunningThread();
        Thread sampler = new Thread(() -> {
            jdk.jfr.Recording recording = startRecording();
            Map<String, Integer> owners = new java.util.TreeMap<>();
            Map<String, Integer> frames = new java.util.HashMap<>();
            Map<String, Integer> callers = new java.util.HashMap<>();
            int samples = 0;
            try {
                for (int i = 0; i < SAMPLE_SECONDS * 50; i++) {
                    Thread.sleep(20L);
                    StackTraceElement[] stack = server.getStackTrace();
                    samples++;
                    String owner = "the game";
                    String seen = null;
                    for (StackTraceElement frame : stack) {
                        String name = frame.getClassName();
                        String mod = name.startsWith("com.aetherianartificer.bitofamess") ? "bit of a mess" : name.startsWith("com.aetherianartificer.townstead") ? "townstead"
                                : name.startsWith("net.conczin.mca") ? "mca" : null;
                        if (mod != null) {
                            owner = mod;
                            seen = name.substring(name.lastIndexOf('.') + 1) + "." + frame.getMethodName();
                            break;
                        }
                    }
                    owners.merge(owner, 1, Integer::sum);
                    if (seen != null) frames.merge(owner + ": " + seen, 1, Integer::sum);
                    if ("townstead".equals(owner)) {
                        // The Townstead callers just below the top one, skipping repeats of the same method.
                        List<String> path = new ArrayList<>();
                        for (StackTraceElement frame : stack) {
                            if (!frame.getClassName().startsWith("com.aetherianartificer.townstead")) continue;
                            String at = frame.getClassName().substring(frame.getClassName().lastIndexOf('.') + 1) + "." + frame.getMethodName();
                            if (path.isEmpty() || !path.get(path.size() - 1).equals(at)) path.add(at);
                            if (path.size() == 3) break;
                        }
                        callers.merge(String.join(" < ", path), 1, Integer::sum);
                    }
                    // The game's own, when no mod is on the stack at all: where in it.
                    if (seen == null && stack.length > 0) {
                        for (StackTraceElement frame : stack) {
                            if (frame.getClassName().startsWith("net.minecraft")) {
                                frames.merge("the game: " + frame.getClassName().substring(frame.getClassName().lastIndexOf('.') + 1) + "." + frame.getMethodName(), 1, Integer::sum);
                                break;
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
                return;
            }
            finishRecording(mc, recording);
            StringBuilder out = new StringBuilder("==== where the server's time goes, " + samples + " samples: " + owners);
            frames.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).limit(24).forEach(e -> out.append("\n      ").append(e.getValue()).append("  ").append(e.getKey()));
            out.append("\n==== who calls them");
            callers.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).limit(40).forEach(e -> out.append("\n      ").append(e.getValue()).append("  ").append(e.getKey()));
            line(out.toString());
        });
        sampler.setDaemon(true);
        sampler.start();
    }

    /** TOWNSTEAD_BODYCHECK_JFR=1 also records the sampling window, to crowd.jfr: full stacks, without safepoint bias. */
    private static jdk.jfr.Recording startRecording() {
        if (System.getenv("TOWNSTEAD_BODYCHECK_JFR") == null) return null;
        jdk.jfr.Recording recording = new jdk.jfr.Recording();
        recording.enable("jdk.ExecutionSample").withPeriod(java.time.Duration.ofMillis(5));
        recording.start();
        return recording;
    }

    private static void finishRecording(Minecraft mc, jdk.jfr.Recording recording) {
        if (recording == null) return;
        try {
            recording.stop();
            recording.dump(mc.gameDirectory.toPath().resolve("crowd.jfr"));
            recording.close();
        } catch (IOException e) {
            line("JFR FAILED " + e);
        }
    }

    private static long serverCpuNanos(Minecraft mc) {
        return java.lang.management.ManagementFactory.getThreadMXBean().getThreadCpuTime(mc.getSingleplayerServer().getRunningThread().getId());
    }

    private static String settledTicks(ServerLevel level, int from) {
        int to = level.getServer().getTickCount();
        long[] ticks = level.getServer().getTickTimesNanos();
        int first = Math.max(from, to - ticks.length);
        long sum = 0L, top = 0L;
        for (int i = first; i < to; i++) {
            long tick = ticks[i % ticks.length];
            sum += tick;
            top = Math.max(top, tick);
        }
        return String.format("==== settled: %d ticks in the window, averaging %.1f ms over the last %d, worst %.1f ms",
                to - from, sum / (double) Math.max(1, to - first) / 1e6, to - first, top / 1e6);
    }

    private static String tickTime(ServerLevel level) {
        long[] ticks = level.getServer().getTickTimesNanos();
        long sum = 0L, top = 0L;
        for (long tick : ticks) {
            sum += tick;
            top = Math.max(top, tick);
        }
        return String.format("%.1f ms a tick, worst %.1f ms", sum / (double) ticks.length / 1e6, top / 1e6);
    }

    /** An MCA villager's meals and slow drinks, by Townstead's own consumption, and what each leaves. */
    private static void mealsScene(Minecraft mc, ServerLevel level, List<Subject> subjects) {
        Subject subject = subjects.get(0);
        BlockPos at = subject.at();
        STEPS.add(new Step(100, () -> mc.getSingleplayerServer().execute(() -> {
            mc.options.hideGui = true;
            mc.getEntityRenderDispatcher().setRenderHitBoxes(false);
            ServerPlayer player = level.getServer().getPlayerList().getPlayers().get(0);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(level, at.getX() + 3.5, at.getY() + 1.2, at.getZ() + 0.5, 90f, 20f);
            for (int x = -3; x <= 3; x++) {
                for (int y = 0; y < 4; y++) level.setBlock(at.offset(x, y, 3), Blocks.AIR.defaultBlockState(), 2);
                for (int z = 1; z < 3; z++) level.setBlock(at.offset(x, 3, z), Blocks.AIR.defaultBlockState(), 2);
            }
        })));
        STEPS.add(new Step(20, () -> serverSubject(level, subject.label(), villager -> {
            villager.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            villager.setYBodyRot(0f);
            villager.setYHeadRot(0f);
            villager.setYRot(0f);
        })));
        net.minecraft.world.item.Item[] foods = {net.minecraft.world.item.Items.BREAD, net.minecraft.world.item.Items.COOKED_BEEF, net.minecraft.world.item.Items.BEEF, net.minecraft.world.item.Items.MUSHROOM_STEW, net.minecraft.world.item.Items.APPLE};
        for (var food : foods) {
            for (int meal = 0; meal < 8; meal++) {
                STEPS.add(new Step(50, () -> serverSubject(level, subject.label(), villager -> {
                    boolean began = com.aetherianartificer.townstead.hunger.VillagerConsumptionManager.startConsuming((VillagerEntityMCA) villager, new net.minecraft.world.item.ItemStack(food), null);
                    if (!began) line("meal refused: " + food);
                })));
            }
            STEPS.add(new Step(40, () -> line("after " + food + ":" + messTotals(level))));
        }
        STEPS.add(new Step(5, () -> shot(mc, "meal_side")));
        // A slow drink skips the game's item use, so only the bridge reports it.
        for (int drink = 0; drink < 10; drink++) {
            STEPS.add(new Step(10, () -> serverSubject(level, subject.label(), villager -> {
                boolean began = com.aetherianartificer.townstead.hunger.VillagerConsumptionManager.startRecreationalDrink((VillagerEntityMCA) villager, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.HONEY_BOTTLE), null);
                if (!began) line("drink refused");
            })));
            STEPS.add(new Step(15, () -> serverSubject(level, subject.label(), villager -> com.aetherianartificer.townstead.hunger.VillagerConsumptionManager.finishRecreationalDrink((VillagerEntityMCA) villager))));
        }
        STEPS.add(new Step(40, () -> line("after slow drinks:" + messTotals(level))));
        STEPS.add(new Step(10, () -> mc.getSingleplayerServer().execute(() -> level.getServer().getPlayerList().getPlayers().get(0).teleportTo(level, at.getX() + 0.5, at.getY() + 3.2, at.getZ() - 1.5, 0f, 62f))));
        STEPS.add(new Step(30, () -> shot(mc, "meal_above")));
    }

    /** An MCA villager breaking blocks by the call Townstead's workers use, and standing in what comes off them. */
    private static void minedScene(Minecraft mc, ServerLevel level, List<Subject> subjects) {
        Subject subject = subjects.get(0);
        BlockPos at = subject.at();
        STEPS.add(new Step(100, () -> mc.getSingleplayerServer().execute(() -> {
            mc.options.hideGui = true;
            mc.getEntityRenderDispatcher().setRenderHitBoxes(false);
            ServerPlayer player = level.getServer().getPlayerList().getPlayers().get(0);
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(level, at.getX() + 4.5, at.getY() + 1.2, at.getZ() + 0.5, 90f, 15f);
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    level.setBlock(at.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                    for (int y = 0; y < 4; y++) level.setBlock(at.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        })));
        STEPS.add(new Step(20, () -> serverSubject(level, subject.label(), villager -> {
            villager.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            ((net.minecraft.world.entity.Mob) villager).setNoAi(true);
            for (int z = 1; z <= 2; z++) {
                for (int y = 0; y < 3; y++) level.setBlock(at.offset(0, y, z), Blocks.STONE.defaultBlockState(), 2);
            }
        })));
        STEPS.add(new Step(10, () -> shot(mc, "mine_before")));
        for (int z = 1; z <= 2; z++) {
            for (int y = 0; y < 3; y++) {
                final BlockPos block = at.offset(0, y, z);
                STEPS.add(new Step(16, () -> serverSubject(level, subject.label(), villager -> line("broke " + level.destroyBlock(block, false, villager)))));
            }
        }
        STEPS.add(new Step(4, () -> shot(mc, "mine_flying")));
        STEPS.add(new Step(100, () -> shot(mc, "mine_after")));
        STEPS.add(new Step(5, () -> serverSubject(level, subject.label(), villager -> {
            line("villager marked " + com.aetherianartificer.bitofamess.trace.BodyMarks.has(villager) + " data " + villager.getPersistentData().getCompound("bitofamess_marks").getAllKeys() + ":" + messTotals(level));
            line("pieces " + com.aetherianartificer.bitofamess.client.MessClient.piecesReport());
        })));
    }

    private static String messTotals(ServerLevel level) {
        StringBuilder out = new StringBuilder();
        for (var field : com.aetherianartificer.bitofamess.world.MessWorld.of(level).fields().entrySet()) {
            int shown = 0;
            double total = 0.0;
            for (var face : field.getValue().faces()) {
                total += face.total();
                for (float cell : face.cells) if (cell > 0f) shown++;
            }
            out.append(String.format(" %s %.3f in %d pixels of %d faces;", field.getKey(), total, shown, field.getValue().faces().size()));
        }
        return out.toString();
    }

    private static void messScene(Minecraft mc, ServerLevel level, List<Subject> subjects) {
        STEPS.add(new Step(100, () -> mc.getSingleplayerServer().execute(() -> {
            mc.options.hideGui = true;
            mc.getEntityRenderDispatcher().setRenderHitBoxes(false);
            ServerPlayer player = level.getServer().getPlayerList().getPlayers().get(0);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        })));
        for (Subject subject : subjects) {
            BlockPos at = subject.at();
            STEPS.add(new Step(40, () -> mc.getSingleplayerServer().execute(() -> {
                ServerPlayer player = level.getServer().getPlayerList().getPlayers().get(0);
                player.teleportTo(level, at.getX() + 0.5, at.getY() + 0.8, at.getZ() + 3.2, 180f, 8f);
                // Back it off the wall this scene builds, so the camera has room.
                for (int x = -3; x <= 3; x++) {
                    for (int y = 0; y < 4; y++) level.setBlock(at.offset(x, y, 3), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                    for (int z = 1; z < 3; z++) level.setBlock(at.offset(x, 3, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                }
            })));
            STEPS.add(new Step(30, () -> serverSubject(level, subject.label(), villager -> {
                villager.setYBodyRot(180f);
                villager.setYHeadRot(180f);
                villager.setYRot(180f);
                villager.setDeltaMovement(Vec3.ZERO);
                villager.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            })));
            STEPS.add(new Step(5, () -> shot(mc, "mess_" + subject.label() + "_clean")));
            // Each thing the bridge carries, on this body.
            STEPS.add(new Step(10, () -> serverSubject(level, subject.label(), villager -> {
                com.aetherianartificer.townstead.compat.mess.MessBridge.addTrace(villager, "bitofamess:blood", 0.4f);
                com.aetherianartificer.townstead.compat.mess.MessBridge.addTrace(villager, "bitofamess:scar", 0.4f);
                StringBuilder held = new StringBuilder();
                for (String id : com.aetherianartificer.townstead.compat.mess.MessBridge.traceIds()) {
                    float amount = com.aetherianartificer.townstead.compat.mess.MessBridge.traceAmount(villager, id);
                    if (amount > 0f) held.append(' ').append(id).append('=').append(amount);
                }
                line(subject.label() + " bridge: active " + com.aetherianartificer.townstead.compat.mess.MessBridge.active() + ", traces "
                        + com.aetherianartificer.townstead.compat.mess.MessBridge.traceIds().size() + ", holds" + held);
                com.aetherianartificer.townstead.compat.mess.MessBridge.wash(villager, com.aetherianartificer.townstead.compat.mess.MessBridge.WASH_SOAP, 1f, "");
                com.aetherianartificer.townstead.compat.mess.MessBridge.event(villager, "when_work:till");
                try {
                    var condition = com.aetherianartificer.bitofamess.trace.TraceHooks.class.getDeclaredMethod("condition", com.google.gson.JsonElement.class);
                    condition.setAccessible(true);
                    Object test = condition.invoke(null, com.google.gson.JsonParser.parseString("{\"type\":\"pheno:and\",\"conditions\":[]}"));
                    line(subject.label() + " a pheno condition with nothing to fail: " + ((java.util.function.Predicate<LivingEntity>) test).test(villager));
                } catch (Exception e) {
                    line("condition check failed " + e);
                }
                switchboard(level, "attachments.gore", false);
            })));
            STEPS.add(new Step(10, () -> serverSubject(level, subject.label(), villager -> {
                int before = villager.getPersistentData().getCompound("BitOfAMessMarks").getAllKeys().size();
                villager.invulnerableTime = 0;
                villager.hurt(level.damageSources().playerAttack(level.getServer().getPlayerList().getPlayers().get(0)), 1f);
                line(subject.label() + " gore off: mark kinds " + before + " then " + villager.getPersistentData().getCompound("BitOfAMessMarks").getAllKeys());
                switchboard(level, "attachments.gore", true);
            })));
            STEPS.add(new Step(20, () -> serverSubject(level, subject.label(), villager -> {
                com.aetherianartificer.townstead.compat.mess.MessBridge.addTrace(villager, "bitofamess:flour", 0.7f);
                com.aetherianartificer.townstead.compat.mess.MessBridge.addTrace(villager, "bitofamess:soot", 0.5f);
                com.aetherianartificer.townstead.compat.mess.MessBridge.addTrace(villager, "bitofamess:ink", 0.5f);
                com.aetherianartificer.townstead.compat.mess.MessBridge.addTrace(villager, "bitofamess:mud", 0.5f);
                line(subject.label() + " settled: " + villager.getPersistentData().getCompound("BitOfAMessMarks").getAllKeys());
            })));
            STEPS.add(new Step(15, () -> shot(mc, "mess_" + subject.label() + "_settled")));
            STEPS.add(new Step(15, () -> serverSubject(level, subject.label(), villager -> {
                var marks = villager.getPersistentData().getCompound("BitOfAMessMarks");
                StringBuilder before = new StringBuilder();
                for (String key : marks.getAllKeys()) before.append(' ').append(key.substring(0, Math.min(18, key.length()))).append('=').append(marks.getIntArray(key).length);
                com.aetherianartificer.townstead.compat.mess.MessBridge.wash(villager, com.aetherianartificer.townstead.compat.mess.MessBridge.WASH_RAIN, 1f, "");
                var after = villager.getPersistentData().getCompound("BitOfAMessMarks");
                StringBuilder left = new StringBuilder();
                for (String key : after.getAllKeys()) left.append(' ').append(key.substring(0, Math.min(18, key.length()))).append('=').append(after.getIntArray(key).length);
                line(subject.label() + " rain: before" + before + ", after" + left);
            })));
            STEPS.add(new Step(30, () -> serverSubject(level, subject.label(), villager -> {
                ServerPlayer player = level.getServer().getPlayerList().getPlayers().get(0);
                villager.invulnerableTime = 0;
                villager.hurt(level.damageSources().playerAttack(player), 2f);
                villager.setDeltaMovement(Vec3.ZERO);
                villager.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                for (double y : new double[]{0.6, 1.2, 1.7}) {
                    com.aetherianartificer.bitofamess.world.MessWorld.of(level).spray("bitofamess:blood", villager.position().add(0.0, y, 2.0), new Vec3(0.0, 0.0, -1.0), 10f,
                            com.aetherianartificer.bitofamess.material.MessSpray.get("bitofamess:spurt"));
                }
            })));
            STEPS.add(new Step(5, () -> {
                shot(mc, "mess_" + subject.label() + "_marked");
                serverSubject(level, subject.label(), villager -> line(subject.label() + " marks: " + villager.getPersistentData().getCompound("BitOfAMessMarks").getAllKeys()
                        + ", outfit " + com.aetherianartificer.bitofamess.trace.Outfits.of(villager)));
            }));
            STEPS.add(new Step(100, () -> serverSubject(level, subject.label(), villager -> {
                com.aetherianartificer.bitofamess.trace.BodyMarks.remove(villager, 1f);
                com.aetherianartificer.bitofamess.trace.BodyMarks.launder(villager);
                line(subject.label() + " after washing: " + villager.getPersistentData().getCompound("BitOfAMessMarks").getAllKeys());
            })));
            STEPS.add(new Step(5, () -> {
                shot(mc, "mess_" + subject.label() + "_washed");
                LivingEntity client = clientSubject(mc, level, subject.label());
                if (client == null) return;
                String stamps;
                try {
                    var on = Class.forName("com.aetherianartificer.bitofamess.client.trace.BodyStamps").getDeclaredMethod("on", int.class);
                    on.setAccessible(true);
                    stamps = String.valueOf(((java.util.Map<?, ?>) on.invoke(null, client.getId())).keySet());
                    var values = com.aetherianartificer.bitofamess.client.trace.TraceRender.class.getDeclaredField("VALUES");
                    values.setAccessible(true);
                    stamps += " mess values " + ((java.util.Map<?, ?>) values.get(null)).get(client.getId());
                } catch (Exception e) {
                    stamps = e.toString();
                }
                line(subject.label() + " on the client after washing: stamps " + stamps + ", townstead values " + BodyValues.clientValues(client.getId()));
            }));
        }
    }

    private static String storedParts(ServerLevel level, String label) {
        UUID id = SPAWNED.get(label);
        return id != null && level.getEntity(id) instanceof LivingEntity living ? String.valueOf(living.getPersistentData().get("TownsteadParts")) : "gone";
    }

    /** Sets a world's Switchboard the way an operator would, before the last subject's scene. */
    private static void switchboard(ServerLevel level, String key, boolean value) {
        level.getServer().execute(() -> {
            var source = level.getServer().createCommandSourceStack().withSuppressedOutput();
            level.getServer().getCommands().performPrefixedCommand(source, "townstead switchboard set " + key + " " + value);
        });
    }

    private static void serverSubject(ServerLevel level, String label, java.util.function.Consumer<LivingEntity> action) {
        UUID id = SPAWNED.get(label);
        if (id == null) return;
        level.getServer().execute(() -> {
            if (level.getEntity(id) instanceof LivingEntity living) action.accept(living);
        });
    }

    private static String health(ServerLevel level, String label) {
        UUID id = SPAWNED.get(label);
        return id != null && level.getEntity(id) instanceof LivingEntity living ? "health " + living.getHealth() + "/" + living.getMaxHealth() : "gone";
    }

    private static LivingEntity clientSubject(Minecraft mc, ServerLevel level, String label) {
        UUID id = SPAWNED.get(label);
        if (id == null) return null;
        Entity server = level.getEntity(id);
        return server != null && mc.level.getEntity(server.getId()) instanceof LivingEntity living ? living : null;
    }

    private static void measure(Minecraft mc, ServerLevel level, String label, String state) {
        LivingEntity client = clientSubject(mc, level, label);
        UUID id = SPAWNED.get(label);
        if (client == null || id == null || !(level.getEntity(id) instanceof LivingEntity server)) {
            line(label + " " + state + ": NOT FOUND");
            return;
        }
        Map<String, float[]> drawn = LiveBoxes.measured(client);
        List<AttachmentDef> worn = WornAttachments.of(client);
        line(label + " " + state + ": stage " + (client instanceof VillagerEntityMCA mca ? mca.getAgeState() : "-")
                + " size " + client.getBbWidth() + "x" + client.getBbHeight() + " pos " + client.position()
                + " worn " + worn.stream().map(AttachmentDef::id).toList() + (drawn == null ? " NOT DRAWN" : ""));
        if (drawn == null) return;
        for (AttachmentDef def : worn) {
            float[] seen = drawn.get(def.id());
            float[] clientPose = BodyPose.measure(client, def);
            float[] serverPose = BodyPose.measure(server, def);
            if (seen == null) {
                line("  " + def.id() + ": not drawn; worked out " + clientPose.length / 6 + " boxes");
                continue;
            }
            line("  " + def.id() + ": drawn " + seen.length / 6 + " boxes, client pose " + clientPose.length / 6
                    + ", server pose " + serverPose.length / 6);
            for (int i = 0; i + 5 < seen.length && i + 5 < clientPose.length; i += 6) {
                if (seen[i] > seen[i + 3]) continue;
                line(String.format("    box %d drawn %s | pose %s | centre gap %s | largest %.3f | server gap %.3f", i / 6,
                        box(seen, i), box(clientPose, i), centreGap(seen, clientPose, i), largest(seen, clientPose, i),
                        i + 5 < serverPose.length ? largest(clientPose, serverPose, i) : -1f));
            }
        }
    }

    private static String box(float[] flat, int i) {
        return String.format("[%.2f %.2f %.2f / %.2f %.2f %.2f]", flat[i], flat[i + 1], flat[i + 2], flat[i + 3], flat[i + 4], flat[i + 5]);
    }

    private static String centreGap(float[] a, float[] b, int i) {
        return String.format("(%.2f %.2f %.2f)", (a[i] + a[i + 3] - b[i] - b[i + 3]) / 2, (a[i + 1] + a[i + 4] - b[i + 1] - b[i + 4]) / 2,
                (a[i + 2] + a[i + 5] - b[i + 2] - b[i + 5]) / 2);
    }

    private static float largest(float[] a, float[] b, int i) {
        float gap = 0f;
        for (int c = 0; c < 6; c++) gap = Math.max(gap, Math.abs(a[i + c] - b[i + c]));
        return gap;
    }

    /** Looks at the part of what's worn that stands furthest from the body, as someone aiming at it would. */
    private static void aim(Minecraft mc, ServerLevel level, String label) {
        LivingEntity client = clientSubject(mc, level, label);
        if (client == null) return;
        Map<String, float[]> drawn = LiveBoxes.measured(client);
        if (drawn == null) {
            line(label + " aim: nothing drawn to aim at");
            return;
        }
        Vec3 best = null;
        double furthest = -1.0;
        for (float[] flat : drawn.values()) {
            for (int i = 0; i + 5 < flat.length; i += 6) {
                if (flat[i] > flat[i + 3]) continue;
                double x = (flat[i] + flat[i + 3]) / 2, y = (flat[i + 1] + flat[i + 4]) / 2, z = (flat[i + 2] + flat[i + 5]) / 2;
                double out = x * x + z * z;
                if (out > furthest) {
                    furthest = out;
                    best = new Vec3(x, y, z);
                }
            }
        }
        if (best == null) {
            line(label + " aim: no boxes");
            return;
        }
        Vec3 target = client.position().add(best);
        mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, target);
        line(String.format("%s aim at %s (%.2f out from the body)", label, best, Math.sqrt(furthest)));
    }

    private static void aimAtBody(Minecraft mc, ServerLevel level, String label) {
        LivingEntity client = clientSubject(mc, level, label);
        if (client == null) return;
        Vec3 target = client.position().add(0.0, client.getBbHeight() * 0.5, 0.0);
        mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, target);
        line(String.format("%s body at %s, %.2f from the eye, reach %.2f, player at %s", label, client.position(),
                mc.player.getEyePosition().distanceTo(target), mc.player.entityInteractionRange(), mc.player.position()));
    }

    private static void attack(Minecraft mc, ServerLevel level, String label, String what) {
        var hit = mc.hitResult;
        if (hit instanceof EntityHitResult entityHit) {
            line(label + " " + what + ": crosshair found " + entityHit.getEntity().getClass().getSimpleName() + " at " + entityHit.getLocation()
                    + "; " + health(level, label));
            mc.gameMode.attack(mc.player, entityHit.getEntity());
            mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        } else {
            line(label + " " + what + ": crosshair found " + (hit == null ? "nothing" : hit.getType()));
        }
    }

    private static void shot(Minecraft mc, String name) {
        mc.options.hideGui = true;
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), message -> {});
    }

    private static void line(String text) {
        synchronized (REPORT) {
            REPORT.add(text);
        }
        // Saved as it goes, so a hang still leaves what came before it.
        write();
    }

    private static void write() {
        try {
            Path file = Minecraft.getInstance().gameDirectory.toPath().resolve("body-check-report.txt");
            synchronized (REPORT) {
                Files.write(file, REPORT, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

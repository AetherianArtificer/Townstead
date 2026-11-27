package com.aetherianartificer.townstead.skillcheck;

import com.aetherianartificer.townstead.profession.career.CareerProgression;
import com.aetherianartificer.townstead.profession.career.CareerTreeOpener;
import com.aetherianartificer.townstead.profession.def.SkillDefs;
import com.aetherianartificer.townstead.root.ability.ResourceValues;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Plays each scene on a real client and saves a picture of it, so a person (or a reader of the
 * pictures) can tell whether a tool shows anything when it is cast. Run with
 * {@code gradlew :1.21.1-neoforge:runSkillScene -PskillCheck}. Pictures land in the game folder's
 * {@code screenshots}.
 */
@EventBusSubscriber(modid = "townstead_skill_check", value = Dist.CLIENT)
public final class SkillScenes {

    private static final boolean ENABLED = Boolean.getBoolean("townstead.scene");
    /** Only scenes whose name contains this text run; empty runs all of them. */
    private static final String ONLY = System.getProperty("townstead.scene.only", "");
    /** High above the flat ground, so nothing that lives there walks into a stage. */
    private static final int FLOOR_Y = 100;
    private static final int STAGE_PITCH = 64;

    private record Step(int delay, Runnable action) {}

    private static final Deque<Step> STEPS = new ArrayDeque<>();
    /** Set by a loader that must run the game tests itself: how to start them, and whether they are over. */
    static Consumer<Minecraft> testRun;
    static java.util.function.BooleanSupplier testsDone = () -> true;
    private static boolean worldAsked, scripted, keepScreen, showGui, scaled;
    private static int cooldown, joined, stages;

    private SkillScenes() {}

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
                String name = "scene" + System.currentTimeMillis();
                LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.EASY, true,
                        new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(12345L, false, false),
                        SkillScenes::flat, new TitleScreen());
            }
            return;
        }
        if (mc.screen != null && !keepScreen) mc.setScreen(null);
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        mc.options.hideGui = !showGui;
        mc.options.pauseOnLostFocus = false;
        if (!scaled) {
            scaled = true;
            mc.options.guiScale().set(2);
            mc.resizeDisplay();
        }
        mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        mc.getToasts().clear();
        if (!scripted) {
            if (++joined < 100) return;
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
        } catch (Throwable failure) {
            failure.printStackTrace();
        }
    }

    /** A flat world: no mountain to build a stage inside, and the ground a game-test server has. */
    private static net.minecraft.world.level.levelgen.WorldDimensions flat(net.minecraft.core.RegistryAccess registry) {
        return registry.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions();
    }

    // ── The scenes ─────────────────────────────────────────────────────────

    private static void script(Minecraft mc) {
        // The first stage is far from the spawn point: go there and let it load before anything is built.
        then(100, () -> place(mc, new BlockPos(0, FLOOR_Y, 0), 8.5, 2.5, 0f, 12f));
        then(1, () -> on(mc, level -> {
            for (String rule : new String[] {"doMobSpawning false", "doDaylightCycle false", "doWeatherCycle false",
                    "doMobLoot false"}) {
                command(level, "gamerule " + rule);
            }
            command(level, "time set noon");
            command(level, "weather clear");
        }));

        // One tool for each career: enemies ahead, an ally at the side, the caster seen from behind.
        tool(mc, "armorer_shoulder_barge", "minecraft:armorer/heavyweight/shoulder_barge", "temper", "", 30);
        tool(mc, "weaponsmith_cleave_through", "minecraft:weaponsmith/headsman/cleave_through", "edge",
                "minecraft:iron_axe", 20);
        tool(mc, "toolsmith_firecracker", "minecraft:toolsmith/sapper/firecracker", "knack", "", 30);
        tool(mc, "mason_barricade", "minecraft:mason/rampart/barricade", "mortar", "", 30);
        tool(mc, "butcher_hot_coals", "minecraft:butcher/smokehouse/hot_coals", "cut", "", 40);
        tool(mc, "farmer_fault_line", "minecraft:farmer/tiller/fault_line", "grit", "minecraft:iron_hoe", 30);
        tool(mc, "fisherman_toss_net", "minecraft:fisherman/netter/toss_net", "tide", "", 30);
        tool(mc, "shepherd_signal_flare", "minecraft:shepherd/night_watch/signal_flare", "flock", "", 40);
        tool(mc, "fletcher_signal_arrow", "minecraft:fletcher/marksman/signal_arrow", "draw", "minecraft:bow", 40);
        tool(mc, "cartographer_rockfall", "minecraft:cartographer/surveyor/rockfall", "bearing", "", 30);
        tool(mc, "cleric_turn_undead", "minecraft:cleric/exorcist/turn_undead", "faith", "", 30);
        tool(mc, "leatherworker_whip_crack", "minecraft:leatherworker/tanner/whip_crack", "hide", "", 20);
        tool(mc, "librarian_blackout", "minecraft:librarian/hush/blackout", "focus", "", 30);
        tool(mc, "scribe_watch_post", "townstead:scribe/journalist/watch_post", "scoop", "", 40);
        tool(mc, "scribe_sanctuary", "townstead:scribe/diplomat/sanctuary", "goodwill", "", 40);
        tool(mc, "scribe_ink_splash", "townstead:scribe/archivist/ink_splash", "ink", "", 20);
        tool(mc, "witch_toxic_cloud", "townstead:witch/herbalist/toxic_cloud", "hex", "", 40);
        tool(mc, "witch_mass_hex", "townstead:witch/hexer/mass_hex", "hex", "", 30);
        tool(mc, "tailor_tightrope", "townstead:tailor/weaver/tightrope", "stitch", "", 30);
        tool(mc, "tailor_masquerade", "townstead:tailor/clothier/masquerade", "stitch", "", 30);
        tool(mc, "climatologist_ice_wall", "townstead:climatologist/frostkeeper/ice_wall", "front", "", 30);
        tool(mc, "beverage_spilled_coffee", "townstead:beverage_artisan/barista/spilled_coffee", "buzz", "", 30);
        tool(mc, "cook_kick_the_pot", "townstead:cook/camp_cook/kick_the_pot", "simmer", "", 30);
        tool(mc, "baker_whiteout", "townstead:baker/miller/whiteout", "proof", "", 30);
        tool(mc, "hunter_sunburst", "townstead:hunter/nightward/sunburst", "vigil", "", 30);
        tool(mc, "scribe_notice", "townstead:scribe/notice", "scoop", "", 40);

        // The readouts above the hotbar, seen through the caster's eyes with the interface on.
        readout(mc, "readout_brew_sense", "townstead:witch/brew_sense");
        readout(mc, "readout_size_up", "minecraft:butcher/size_up");
        readout(mc, "readout_look_it_up", "minecraft:librarian/look_it_up");
        readout(mc, "readout_forecast", "townstead:climatologist/forecast");

        careerScreen(mc);
        then(20, () -> System.out.println("[scene] all scenes done"));
        // Where no game-test server can be started, the same tests run here, in this world.
        if (testRun != null) {
            then(60, () -> place(mc, new BlockPos(-400, FLOOR_Y, 0), 0.5, 0.5, 0f, 30f));
            then(1, () -> testRun.accept(mc));
            then(1, () -> waitFor(testsDone, 6000));
        }
        then(20, () -> System.out.println("[scene] run complete"));
        then(20, mc::stop);
    }

    private static void tool(Minecraft mc, String name, String skill, String resource, String held, int wait) {
        if (!ONLY.isEmpty() && !name.contains(ONLY)) return;
        if (unloaded(name, skill)) return;
        BlockPos origin = nextStage();
        then(30, () -> {
            showGui = false;
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            place(mc, origin, 8.5, 2.5, 0f, 12f);
        });
        then(50, () -> on(mc, level -> {
            if (!stage(level, origin, name, skill)) return;
            ServerPlayer player = player(level);
            for (int[] at : new int[][] {{8, 7}, {6, 8}, {10, 8}}) spawn(level, EntityType.HUSK, origin, at[0], at[1]);
            spawn(level, EntityType.COW, origin, 11, 4);
            player.setItemInHand(InteractionHand.MAIN_HAND, held.isEmpty() ? ItemStack.EMPTY
                    : new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(held))));
            SkillCheck.learn(player, skill);
            ResourceValues.set(player, SkillCheck.STAMINA, 100);
            ResourceValues.set(player, ResourceLocation.parse("townstead:" + resource), 100);
        }));
        // Two pictures from behind the caster: just after the cast, and once it has settled.
        then(4, () -> on(mc, level -> fire(level, name, skill)));
        then(Math.max(1, wait - 4), () -> shot(mc, name + "_1"));
        then(2, () -> {
            shot(mc, name + "_2");
            on(mc, level -> report(level, name));
        });
        then(2, () -> clear(mc));
    }

    private static void readout(Minecraft mc, String name, String skill) {
        if (!ONLY.isEmpty() && !name.contains(ONLY)) return;
        if (unloaded(name, skill)) return;
        BlockPos origin = nextStage();
        then(30, () -> {
            showGui = true;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            place(mc, origin, 8.5, 2.5, 0f, 8f);
        });
        then(50, () -> on(mc, level -> {
            if (!stage(level, origin, name, skill)) return;
            ServerPlayer player = player(level);
            Mob husk = spawn(level, EntityType.HUSK, origin, 8, 6);
            husk.addEffect(new MobEffectInstance(MobEffects.POISON, 1200, 1));
            husk.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600));
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            SkillCheck.learn(player, skill);
            ResourceValues.set(player, SkillCheck.STAMINA, 100);
        }));
        then(10, () -> on(mc, level -> fire(level, name, skill)));
        then(4, () -> shot(mc, name));
        then(1, () -> {
            showGui = false;
            clear(mc);
        });
    }

    /** The career screen of a character with progress and a few skills, to see how its marks are drawn. */
    private static void careerScreen(Minecraft mc) {
        if (!ONLY.isEmpty() && !"career_screen".contains(ONLY)) return;
        BlockPos origin = nextStage();
        then(30, () -> {
            showGui = true;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            place(mc, origin, 8.5, 2.5, 0f, 8f);
        });
        then(40, () -> on(mc, level -> {
            ServerPlayer player = player(level);
            ResourceLocation armorer = ResourceLocation.parse("minecraft:armorer");
            CareerProgression.award(player, armorer, 100000, level.getGameTime(), false);
            String path = "minecraft:armorer/heavyweight/";
            String[] grid = {"shoulder_barge", "iron_tread"};
            for (String skill : new String[] {"minecraft:armorer/iron_skin", "minecraft:armorer/patch_up"}) {
                if (SkillDefs.byId(ResourceLocation.parse(skill)) != null) SkillCheck.learn(player, skill);
            }
            for (String skill : grid) {
                if (SkillDefs.byId(ResourceLocation.parse(path + skill)) != null) SkillCheck.learn(player, path + skill);
            }
            keepScreen = true;
            CareerTreeOpener.send(player);
        }));
        // The pointer leaves the board, so no tooltip covers it.
        then(60, () -> org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 2, 2));
        then(10, () -> shot(mc, "career_screen_top"));
        then(10, () -> {
            if (mc.screen != null) {
                for (int i = 0; i < 4; i++) mc.screen.mouseScrolled(mc.getWindow().getGuiScaledWidth() / 4.0,
                        mc.getWindow().getGuiScaledHeight() / 2.0, 0, -1);
            }
        });
        then(10, () -> shot(mc, "career_screen_bottom"));
        then(20, () -> {
            mc.options.guiScale().set(1);
            mc.resizeDisplay();
        });
        then(10, () -> shot(mc, "career_screen_whole"));
        then(5, () -> {
            keepScreen = false;
            showGui = false;
        });
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /** Holds the script until the condition is true, or the ticks run out. */
    private static void waitFor(java.util.function.BooleanSupplier done, int ticks) {
        if (ticks <= 0 || done.getAsBoolean()) return;
        STEPS.addFirst(new Step(1, () -> waitFor(done, ticks - 1)));
    }

    private static void then(int delay, Runnable action) {
        STEPS.add(new Step(delay, action));
    }

    private static void on(Minecraft mc, Consumer<ServerLevel> action) {
        mc.getSingleplayerServer().execute(() -> {
            try {
                action.accept(mc.getSingleplayerServer().overworld());
            } catch (Throwable failure) {
                failure.printStackTrace();
            }
        });
    }

    /** A career that needs another mod is not loaded in this game: its scene is left out. */
    private static boolean unloaded(String name, String skill) {
        if (SkillDefs.byId(ResourceLocation.parse(skill)) != null) return false;
        System.out.println("[scene] " + name + " skipped: " + skill + " is not loaded");
        return true;
    }

    private static BlockPos nextStage() {
        return new BlockPos(STAGE_PITCH * stages++, FLOOR_Y, 0);
    }

    private static ServerPlayer player(ServerLevel level) {
        return level.getServer().getPlayerList().getPlayers().get(0);
    }

    private static void command(ServerLevel level, String command) {
        // As the player, at the player's place: a test run is laid out around whoever asks for it.
        level.getServer().getCommands().performPrefixedCommand(
                player(level).createCommandSourceStack().withPermission(4).withSuppressedOutput(), command);
    }

    private static void place(Minecraft mc, BlockPos origin, double x, double z, float yaw, float pitch) {
        on(mc, level -> {
            ServerPlayer player = player(level);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(level, origin.getX() + x, origin.getY() + 1, origin.getZ() + z, yaw, pitch);
        });
    }

    /** A stone floor in the air with nothing on it. False when the skill is not loaded in this game. */
    private static boolean stage(ServerLevel level, BlockPos origin, String name, String skill) {
        if (SkillDefs.byId(ResourceLocation.parse(skill)) == null) {
            System.out.println("[scene] " + name + " skipped: " + skill + " is not loaded");
            return false;
        }
        for (int x = -4; x < 22; x++) {
            for (int z = -6; z < 22; z++) {
                level.setBlockAndUpdate(origin.offset(x, 0, z), Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
        ServerPlayer player = player(level);
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        player.removeAllEffects();
        return true;
    }

    private static <T extends Mob> T spawn(ServerLevel level, EntityType<T> type, BlockPos origin, int x, int z) {
        T mob = type.create(level);
        mob.moveTo(origin.getX() + x + 0.5, origin.getY() + 1, origin.getZ() + z + 0.5, 180f, 0f);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.COMMAND, null);
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        mob.setBaby(false);
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            mob.setItemSlot(slot, ItemStack.EMPTY);
        }
        level.addFreshEntity(mob);
        return mob;
    }

    private static void fire(ServerLevel level, String name, String skill) {
        if (SkillDefs.byId(ResourceLocation.parse(skill)) == null) return;
        boolean fired;
        try {
            fired = SkillCheck.press(player(level), skill);
        } catch (RuntimeException failure) {
            System.out.println("[scene] " + name + " failed: " + failure.getMessage());
            return;
        }
        System.out.println("[scene] " + name + " fired=" + fired);
    }

    /** What the cast left behind, in numbers, to read beside the pictures. */
    private static void report(ServerLevel level, String name) {
        ServerPlayer player = player(level);
        StringBuilder out = new StringBuilder("[scene] " + name + " after: caster " + player.getHealth()
                + " at " + player.blockPosition().toShortString());
        for (Entity entity : level.getEntities(player, player.getBoundingBox().inflate(16))) {
            if (!(entity instanceof Mob mob)) continue;
            out.append(" | ").append(mob.getType().toShortString()).append(' ').append(mob.getHealth())
                    .append('/').append(mob.getMaxHealth()).append(" fx").append(mob.getActiveEffects().size());
        }
        System.out.println(out);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), message -> {});
    }

    /** The next stage starts clean: nothing from this one follows the caster. */
    private static void clear(Minecraft mc) {
        on(mc, level -> {
            ServerPlayer player = player(level);
            for (Entity entity : level.getEntities(player, player.getBoundingBox().inflate(40))) {
                if (!(entity instanceof net.minecraft.world.entity.player.Player)) entity.discard();
            }
        });
    }
}

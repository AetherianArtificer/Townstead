package com.aetherianartificer.townstead.compat.butchery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Pure helpers around the {@code butchery:skin_rack} block. Mirrors the
 * mod's own state machine (see Butchery's {@code PlaceskinonrackProcedure},
 * {@code ConvertingskinProcedure}, {@code RetrieveskinProcedure}) so a
 * villager can drive it without going through the player click path.
 *
 * <p>The {@code blockstate} integer property covers values 0..31:
 * <ul>
 *   <li>0 — empty rack</li>
 *   <li>1..25, 28, 29, 31 — raw hide draped (id encodes the hide species)</li>
 *   <li>26 — salted (species id is gone after this point)</li>
 *   <li>27 — soaked, mid-cure; mod schedules a 1800-tick flip to 30</li>
 *   <li>30 — cured leather, ready to collect (drops vanilla {@code Items.LEATHER})</li>
 * </ul>
 *
 * <p>Wetness reads/writes for sponge/rag use {@link SpongeRagHelper} so the
 * 1.21+ DataComponents and 1.20.1 NBT paths stay isolated.
 */
public final class SkinRackStateMachine {

    public static final int STATE_EMPTY = 0;
    public static final int STATE_SALTED = 26;
    public static final int STATE_SOAKED = 27;
    public static final int STATE_CURED = 30;

    public static final ResourceLocation SKIN_RACK_ID =
            //? if >=1.21 {
            ResourceLocation.parse("butchery:skin_rack");
            //?} else {
            /*new ResourceLocation("butchery", "skin_rack");
            *///?}

    public static final ResourceLocation SALT_ID =
            //? if >=1.21 {
            ResourceLocation.parse("butchery:salt");
            //?} else {
            /*new ResourceLocation("butchery", "salt");
            *///?}

    public static final TagKey<Item> SALT_TAG_FORGE = TagKey.create(
            Registries.ITEM,
            //? if >=1.21 {
            ResourceLocation.parse("forge:salt"));
            //?} else {
            /*new ResourceLocation("forge", "salt"));
            *///?}

    public static final TagKey<Item> SALT_TAG_C = TagKey.create(
            Registries.ITEM,
            //? if >=1.21 {
            ResourceLocation.parse("c:salt"));
            //?} else {
            /*new ResourceLocation("c", "salt"));
            *///?}

    private static final ResourceLocation SOUND_WOOL_PLACE =
            //? if >=1.21 {
            ResourceLocation.parse("block.wool.place");
            //?} else {
            /*new ResourceLocation("block.wool.place");
            *///?}
    private static final ResourceLocation SOUND_SAND_PLACE =
            //? if >=1.21 {
            ResourceLocation.parse("block.sand.place");
            //?} else {
            /*new ResourceLocation("block.sand.place");
            *///?}
    private static final ResourceLocation SOUND_SPLASH =
            //? if >=1.21 {
            ResourceLocation.parse("entity.fishing_bobber.splash");
            //?} else {
            /*new ResourceLocation("entity.fishing_bobber.splash");
            *///?}
    private static final ResourceLocation SOUND_WOOL_BREAK =
            //? if >=1.21 {
            ResourceLocation.parse("block.wool.break");
            //?} else {
            /*new ResourceLocation("block.wool.break");
            *///?}

    /**
     * Map of {@code butchery:*_skin} item id → blockstate produced by the
     * place procedure. Keep in sync with
     * {@code PlaceskinonrackProcedure.execute}; the mod hard-codes the
     * dispatch with item-equality checks rather than using a tag.
     */
    private static final Map<ResourceLocation, Integer> HIDE_STATE_BY_ITEM_ID;
    static {
        Map<ResourceLocation, Integer> m = new HashMap<>();
        m.put(hideId("bat_skin"), 1);
        m.put(hideId("black_horse_skin"), 2);
        m.put(hideId("brown_mooshroom_skin"), 3);
        m.put(hideId("chestnut_horse_skin"), 4);
        m.put(hideId("creamy_horse_skin"), 5);
        m.put(hideId("creeper_skin"), 6);
        m.put(hideId("dark_brown_horse_skin"), 7);
        m.put(hideId("dolphin_skin"), 8);
        m.put(hideId("donkey_skin"), 9);
        m.put(hideId("fox_skin"), 10);
        m.put(hideId("gray_horse_skin"), 11);
        m.put(hideId("hoglin_skin"), 12);
        m.put(hideId("mule_skin"), 13);
        m.put(hideId("ocelot_skin"), 14);
        m.put(hideId("panda_skin"), 15);
        m.put(hideId("pig_skin"), 16);
        m.put(hideId("red_mooshroom_skin"), 17);
        m.put(hideId("sheep_skin"), 18);
        m.put(hideId("snow_fox_skin"), 19);
        m.put(hideId("white_horse_skin"), 20);
        m.put(hideId("polar_bear_skin"), 21);
        m.put(hideId("brown_llama_skin"), 22);
        m.put(hideId("creamy_llama_skin"), 23);
        m.put(hideId("gray_llama_skin"), 24);
        m.put(hideId("white_llama_skin"), 25);
        m.put(hideId("cow_skin"), 28);
        m.put(hideId("zoglin_skin"), 29);
        m.put(hideId("camel_skin"), 31);
        HIDE_STATE_BY_ITEM_ID = Map.copyOf(m);
    }

    private static ResourceLocation hideId(String path) {
        //? if >=1.21 {
        return ResourceLocation.parse("butchery:" + path);
        //?} else {
        /*return new ResourceLocation("butchery", path);
        *///?}
    }

    private SkinRackStateMachine() {}

    public static boolean isSkinRack(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return SKIN_RACK_ID.equals(id);
    }

    public static int currentState(BlockState state) {
        IntegerProperty prop = blockstateProperty(state);
        if (prop == null) return -1;
        return state.getValue(prop);
    }

    public static boolean isEmpty(BlockState state) {
        return isSkinRack(state) && currentState(state) == STATE_EMPTY;
    }

    public static boolean isRawHide(BlockState state) {
        if (!isSkinRack(state)) return false;
        int s = currentState(state);
        return s >= 1 && s != STATE_SALTED && s != STATE_SOAKED && s != STATE_CURED;
    }

    public static boolean isSalted(BlockState state) {
        return isSkinRack(state) && currentState(state) == STATE_SALTED;
    }

    public static boolean isSoaked(BlockState state) {
        return isSkinRack(state) && currentState(state) == STATE_SOAKED;
    }

    public static boolean isCured(BlockState state) {
        return isSkinRack(state) && currentState(state) == STATE_CURED;
    }

    /** Hide blockstate produced by placing the given stack, or 0 if not a recognized hide. */
    public static int hideStateFor(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Integer mapped = HIDE_STATE_BY_ITEM_ID.get(id);
        return mapped == null ? 0 : mapped;
    }

    public static boolean isHideItem(ItemStack stack) {
        return hideStateFor(stack) > 0;
    }

    public static boolean isSaltItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (SALT_ID.equals(id)) return true;
        return stack.is(SALT_TAG_FORGE) || stack.is(SALT_TAG_C);
    }

    /**
     * Apply the same transitions Butchery's procedures perform when a player
     * right-clicks the rack: shrink the supply stack, swing the villager's
     * main hand, advance the blockstate, and replay the mod's sound. NBT
     * mutations on sponge/rag are delegated to {@link SpongeRagHelper}.
     */
    /**
     * Place a draped hide on an empty rack. Returns true if the rack
     * actually advanced to the hide state, false otherwise (block isn't a
     * rack, doesn't accept the target value, or the held stack isn't a
     * recognized hide). Callers must treat a {@code false} return as
     * "supply was NOT consumed" — the {@link ItemStack#shrink} happens
     * only on success so a missed update can't make the hide vanish.
     */
    public static boolean placeHide(ServerLevel level, BlockPos pos, ItemStack hideStack) {
        int target = hideStateFor(hideStack);
        if (target <= 0) return false;
        if (!setBlockstate(level, pos, target)) return false;
        hideStack.shrink(1);
        playSound(level, pos, SOUND_WOOL_PLACE, 1f);
        return true;
    }

    public static boolean applySalt(ServerLevel level, BlockPos pos, ItemStack saltStack) {
        if (!setBlockstate(level, pos, STATE_SALTED)) return false;
        saltStack.shrink(1);
        playSound(level, pos, SOUND_SAND_PLACE, 0.3f);
        return true;
    }

    public static boolean applySoak(ServerLevel level, BlockPos pos, ItemStack clothStack) {
        if (!setBlockstate(level, pos, STATE_SOAKED)) return false;
        SpongeRagHelper.decrementWetness(clothStack);
        playSound(level, pos, SOUND_SPLASH, 0.3f);
        // The mod's own RightClickBlock handler schedules the 1800-tick flip
        // 27 → 30 via ButcheryMod.queueServerWork. We bypass that path, so
        // re-arm the cure timer here using the mod's own queueing.
        ButcheryServerWork.queueCure(level, pos);
        return true;
    }

    /**
     * Replays {@code WetspongecauldronProcedure} / {@code WetragcauldronProcedure}
     * on the given water cauldron position: refills the cloth's wetness to
     * its species cap (sponge → 10, rag → 5), drains one level of water from
     * the cauldron, and plays the splash sound. No-op if the block isn't a
     * water cauldron or the held stack isn't a cloth.
     */
    public static void wetClothAtCauldron(ServerLevel level, BlockPos cauldronPos, ItemStack clothStack) {
        if (clothStack.isEmpty() || !SpongeRagHelper.isCloth(clothStack)) return;
        BlockState state = level.getBlockState(cauldronPos);
        if (!state.is(net.minecraft.world.level.block.Blocks.WATER_CAULDRON)) return;

        int cap = SpongeRagHelper.isSponge(clothStack) ? 10 : 5;
        SpongeRagHelper.setWetness(clothStack, cap);

        // Drain one level (333mB ≈ one of the three vanilla layers). When
        // the last level is removed the block becomes an empty cauldron.
        var prop = net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL;
        if (state.hasProperty(prop)) {
            int current = state.getValue(prop);
            if (current <= 1) {
                level.setBlock(cauldronPos,
                        net.minecraft.world.level.block.Blocks.CAULDRON.defaultBlockState(), 3);
            } else {
                level.setBlock(cauldronPos, state.setValue(prop, current - 1), 3);
            }
        }
        playSound(level, cauldronPos, SOUND_SPLASH, 0.3f);
    }

    /**
     * Collect the cured leather (or, defensively, a raw hide if the rack
     * regressed). Spawns the drop at the rack's center and resets to empty.
     * Returns the dropped stack so callers can decide whether to also
     * deposit it into the villager's inventory.
     */
    public static ItemStack collectAndClear(ServerLevel level, BlockPos pos, BlockState state) {
        int s = currentState(state);
        ItemStack drop = ItemStack.EMPTY;
        if (s == STATE_CURED) {
            drop = new ItemStack(Items.LEATHER);
        } else if (s >= 1 && s != STATE_SALTED && s != STATE_SOAKED) {
            drop = rawHideDropFor(s);
        }
        setBlockstate(level, pos, STATE_EMPTY);
        playSound(level, pos, SOUND_WOOL_BREAK, 1f);
        return drop;
    }

    /** Spawn a stack at the rack centre as an ItemEntity, mirroring RetrieveskinProcedure. */
    public static void spawnDrop(ServerLevel level, BlockPos pos, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemEntity ie = new ItemEntity(level,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        ie.setPickUpDelay(10);
        level.addFreshEntity(ie);
    }

    private static ItemStack rawHideDropFor(int blockstate) {
        for (Map.Entry<ResourceLocation, Integer> entry : HIDE_STATE_BY_ITEM_ID.entrySet()) {
            if (entry.getValue() == blockstate) {
                Item item = BuiltInRegistries.ITEM.get(entry.getKey());
                if (item != null) return new ItemStack(item);
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Set the rack's {@code blockstate} property to {@code value}. Returns
     * true on success, false when the block at {@code pos} isn't a rack
     * (or, defensively, when its property doesn't accept the requested
     * value). Transition helpers gate supply consumption on this so only a
     * confirmed block update consumes the held item.
     */
    private static boolean setBlockstate(ServerLevel level, BlockPos pos, int value) {
        BlockState bs = level.getBlockState(pos);
        if (!isSkinRack(bs)) return false;
        IntegerProperty prop = blockstateProperty(bs);
        if (prop == null || !prop.getPossibleValues().contains(value)) return false;
        return level.setBlock(pos, bs.setValue(prop, value), 3);
    }

    @Nullable
    private static IntegerProperty blockstateProperty(BlockState state) {
        var raw = state.getBlock().getStateDefinition().getProperty("blockstate");
        return raw instanceof IntegerProperty ip ? ip : null;
    }

    private static void playSound(ServerLevel level, BlockPos pos, ResourceLocation soundId, float volume) {
        var event = BuiltInRegistries.SOUND_EVENT.get(soundId);
        if (event == null) return;
        level.playSound(null, pos, event, SoundSource.NEUTRAL, volume, 1f);
    }

    /** Direction is unused by the cure transitions but kept here so callers don't import block properties directly. */
    @SuppressWarnings("unused")
    private static Direction unusedDirectionPlaceholder() {
        return Direction.NORTH;
    }
}

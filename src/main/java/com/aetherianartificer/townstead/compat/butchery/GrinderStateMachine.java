package com.aetherianartificer.townstead.compat.butchery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Mirrors {@code MeatgrinderrecipeprocedureProcedure} from Butchery so a
 * villager can drive the meat grinder the same way a player does through the
 * mod's own GUI: load the correct slots, wait for the 150-tick craft cycle
 * the mod's own scheduled tick increments, then collect the output.
 *
 * <p>Slot layout on {@code butchery:meat_grinder} (block entity has 7 slots):
 * <ul>
 *   <li>Slot 0 — input (raw meat or scrappable item)</li>
 *   <li>Slot 1 — intestines</li>
 *   <li>Slot 2 — sausage attachment</li>
 *   <li>Slot 3 — blood bottle (blood sausage only)</li>
 *   <li>Slot 4 — output (mince / sausage / meat scraps)</li>
 *   <li>Slot 5 — glass bottle return (from blood sausage)</li>
 *   <li>Slot 6 — spare / unused by the mod's recipes we care about</li>
 * </ul>
 *
 * <p>Progress NBT on the block entity: {@code craftingProgress} counts up
 * from 0 to 150; {@code craftingTime} is set to 150 while a recipe is active
 * and 0 when slot 0 is empty. Mod writes these every tick via its own
 * scheduled-tick loop, so villager code never advances them manually.
 */
public final class GrinderStateMachine {
    // Slot layout.
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_INTESTINES = 1;
    public static final int SLOT_ATTACHMENT = 2;
    public static final int SLOT_BLOOD = 3;
    public static final int SLOT_OUTPUT = 4;
    public static final int SLOT_BOTTLE_RETURN = 5;

    // NBT keys written by the mod.
    public static final String PROGRESS_KEY = "craftingProgress";
    public static final String TIME_KEY = "craftingTime";
    public static final int PROGRESS_COMPLETE = 150;

    // Block IDs.
    public static final ResourceLocation MEAT_GRINDER_ID =
            //? if >=1.21 {
            ResourceLocation.parse("butchery:meat_grinder");
            //?} else {
            /*new ResourceLocation("butchery", "meat_grinder");
            *///?}

    // Item IDs (fixed / non-tag items the mod's procedure checks directly).
    public static final ResourceLocation INTESTINES_ID =
            //? if >=1.21 {
            ResourceLocation.parse("butchery:intestines");
            //?} else {
            /*new ResourceLocation("butchery", "intestines");
            *///?}
    public static final ResourceLocation SAUSAGE_ATTACHMENT_ID =
            //? if >=1.21 {
            ResourceLocation.parse("butchery:sausage_attachment");
            //?} else {
            /*new ResourceLocation("butchery", "sausage_attachment");
            *///?}
    public static final ResourceLocation BOTTLE_OF_BLOOD_ID =
            //? if >=1.21 {
            ResourceLocation.parse("butchery:bottle_of_blood");
            //?} else {
            /*new ResourceLocation("butchery", "bottle_of_blood");
            *///?}

    // Output item IDs.
    public static final ResourceLocation RAW_SAUSAGE_ID =
            //? if >=1.21 {
            ResourceLocation.parse("butchery:raw_sausage");
            //?} else {
            /*new ResourceLocation("butchery", "raw_sausage");
            *///?}
    public static final ResourceLocation RAW_BLOOD_SAUSAGE_ID =
            //? if >=1.21 {
            ResourceLocation.parse("butchery:raw_blood_sausage");
            //?} else {
            /*new ResourceLocation("butchery", "raw_blood_sausage");
            *///?}
    public static final ResourceLocation RAW_LAMB_MINCE_ID =
            //? if >=1.21 {
            ResourceLocation.parse("butchery:raw_lamb_mince");
            //?} else {
            /*new ResourceLocation("butchery", "raw_lamb_mince");
            *///?}
    public static final ResourceLocation RAW_BEEF_MINCE_ID =
            //? if >=1.21 {
            ResourceLocation.parse("butchery:raw_beef_mince");
            //?} else {
            /*new ResourceLocation("butchery", "raw_beef_mince");
            *///?}
    public static final ResourceLocation MEAT_SCRAPS_ID =
            //? if >=1.21 {
            ResourceLocation.parse("butchery:meat_scraps");
            //?} else {
            /*new ResourceLocation("butchery", "meat_scraps");
            *///?}

    // Tags the mod's procedure consults.
    public static final TagKey<Item> RAW_PORK_FORGE = tag("forge:raw_pork");
    public static final TagKey<Item> RAW_PORK_C = tag("c:raw_pork");
    public static final TagKey<Item> RAW_MUTTON_FORGE = tag("forge:raw_mutton");
    public static final TagKey<Item> RAW_MUTTON_C = tag("c:raw_mutton");
    public static final TagKey<Item> RAW_BEEF_FORGE = tag("forge:raw_beef");
    public static final TagKey<Item> RAW_BEEF_C = tag("c:raw_beef");
    public static final TagKey<Item> BLOOD_BOTTLE_FORGE = tag("forge:blood_bottle");
    public static final TagKey<Item> BLOOD_BOTTLE_C = tag("c:blood_bottle");
    public static final TagKey<Item> SCRAPPABLE_ITEM_TAG = tag("butchery:scrappable");
    public static final TagKey<Block> SCRAPPABLE_BLOCK_TAG = blockTag("butchery:scrappable");

    private static TagKey<Item> tag(String id) {
        //? if >=1.21 {
        return TagKey.create(Registries.ITEM, ResourceLocation.parse(id));
        //?} else {
        /*int split = id.indexOf(':');
        return TagKey.create(Registries.ITEM, new ResourceLocation(id.substring(0, split), id.substring(split + 1)));
        *///?}
    }

    private static TagKey<Block> blockTag(String id) {
        //? if >=1.21 {
        return TagKey.create(Registries.BLOCK, ResourceLocation.parse(id));
        //?} else {
        /*int split = id.indexOf(':');
        return TagKey.create(Registries.BLOCK, new ResourceLocation(id.substring(0, split), id.substring(split + 1)));
        *///?}
    }

    private GrinderStateMachine() {}

    /**
     * Recipe the villager intends to run. Ordering here is the pick-priority
     * we use when deciding what to stock the grinder with; the mod's own
     * procedure then matches on slot contents and runs whatever fits. Blood
     * sausage beats plain sausage so scarce blood bottles get used rather
     * than sitting in storage.
     */
    public enum Recipe {
        BLOOD_SAUSAGE(RAW_BLOOD_SAUSAGE_ID, true),
        SAUSAGE(RAW_SAUSAGE_ID, true),
        LAMB_MINCE(RAW_LAMB_MINCE_ID, false),
        BEEF_MINCE(RAW_BEEF_MINCE_ID, false),
        SCRAPS(MEAT_SCRAPS_ID, false);

        public final ResourceLocation outputId;
        /** True if the recipe consumes intestines + sausage attachment in slots 1/2. */
        public final boolean requiresCasings;

        Recipe(ResourceLocation outputId, boolean requiresCasings) {
            this.outputId = outputId;
            this.requiresCasings = requiresCasings;
        }
    }

    // ── Predicates ──

    public static boolean isIntestines(ItemStack stack) {
        return !stack.isEmpty() && idOf(stack).equals(INTESTINES_ID);
    }

    public static boolean isSausageAttachment(ItemStack stack) {
        return !stack.isEmpty() && idOf(stack).equals(SAUSAGE_ATTACHMENT_ID);
    }

    public static boolean isBloodBottle(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (idOf(stack).equals(BOTTLE_OF_BLOOD_ID)) return true;
        return stack.is(BLOOD_BOTTLE_FORGE) || stack.is(BLOOD_BOTTLE_C);
    }

    public static boolean isRawPork(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.is(RAW_PORK_FORGE) || stack.is(RAW_PORK_C);
    }

    public static boolean isRawMutton(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.is(RAW_MUTTON_FORGE) || stack.is(RAW_MUTTON_C);
    }

    public static boolean isRawBeef(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.is(RAW_BEEF_FORGE) || stack.is(RAW_BEEF_C);
    }

    /**
     * Matches the mod's scrappable check: the item itself tagged {@code
     * butchery:scrappable}, or a block item whose block state carries the
     * corresponding block tag (carcasses qualify, which is how "grind a
     * spare carcass into meat scraps" works).
     */
    public static boolean isScrappable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(SCRAPPABLE_ITEM_TAG)) return true;
        if (stack.getItem() instanceof BlockItem bi) {
            BlockState bs = bi.getBlock().defaultBlockState();
            return bs.is(SCRAPPABLE_BLOCK_TAG);
        }
        return false;
    }

    /** True for anything the grinder can produce (final output slot or the glass-bottle return). */
    public static boolean isGrinderOutput(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation id = idOf(stack);
        return id.equals(RAW_SAUSAGE_ID)
                || id.equals(RAW_BLOOD_SAUSAGE_ID)
                || id.equals(RAW_LAMB_MINCE_ID)
                || id.equals(RAW_BEEF_MINCE_ID)
                || id.equals(MEAT_SCRAPS_ID);
    }

    /**
     * True for items that would legitimately go into slot 0 for the given
     * recipe. Used by the work task to filter inventory when staging.
     */
    public static boolean isInputForRecipe(ItemStack stack, Recipe recipe) {
        return switch (recipe) {
            case BLOOD_SAUSAGE, SAUSAGE -> isRawPork(stack);
            case LAMB_MINCE -> isRawMutton(stack);
            case BEEF_MINCE -> isRawBeef(stack);
            case SCRAPS -> isScrappable(stack);
        };
    }

    // ── NBT read helpers ──

    public static double readProgress(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return 0;
        return be.getPersistentData().getDouble(PROGRESS_KEY);
    }

    public static double readCraftingTime(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return 0;
        return be.getPersistentData().getDouble(TIME_KEY);
    }

    /** Mod is mid-cycle: progress has advanced past 0 but hasn't reached 150. */
    public static boolean isRunning(Level level, BlockPos pos) {
        double progress = readProgress(level, pos);
        return progress > 0 && progress < PROGRESS_COMPLETE;
    }

    /** Grinder has a finished batch sitting in the output slot. */
    public static boolean hasFinishedOutput(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof Container container)) return false;
        return !container.getItem(SLOT_OUTPUT).isEmpty()
                || !container.getItem(SLOT_BOTTLE_RETURN).isEmpty();
    }

    @Nullable
    public static Container container(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof Container c ? c : null;
    }

    private static ResourceLocation idOf(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }
}

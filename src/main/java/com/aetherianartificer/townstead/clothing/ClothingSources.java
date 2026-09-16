package com.aetherianartificer.townstead.clothing;

import com.aetherianartificer.townstead.compat.curios.CuriosCompat;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Where worn pieces come from, server side. Armor slots, Curios slots, and the MCA skin are the
 * defaults; a provider whose garments are records rather than stacks registers its own source.
 * Insulation, the dress behaviour, and presentation all walk this and never ask a mod directly.
 */
public final class ClothingSources {

    /** One place an entity may be wearing something. */
    public interface Source {
        String id();

        void collect(LivingEntity entity, Consumer<WornPiece> out);
    }

    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();

    static {
        register(new ArmorSlots());
        register(new CuriosSlots());
        register(new McaSkin());
    }

    private ClothingSources() {}

    public static void register(Source source) {
        if (source == null) return;
        for (Source existing : SOURCES) {
            if (existing.id().equals(source.id())) return;
        }
        SOURCES.add(source);
    }

    public static List<Source> all() {
        return List.copyOf(SOURCES);
    }

    public static void collect(@Nullable LivingEntity entity, Consumer<WornPiece> out) {
        if (entity == null || out == null) return;
        for (Source source : SOURCES) {
            source.collect(entity, out);
        }
    }

    public static List<WornPiece> worn(@Nullable LivingEntity entity) {
        List<WornPiece> out = new ArrayList<>();
        collect(entity, out::add);
        return out;
    }

    /** Where a coat waits between rooms: the villager's own inventory. Not a registered source. */
    public static final String CARRIED_SOURCE = "townstead:carried";

    /**
     * Clothing stacks in a villager's pockets, as pieces. They are not worn: they never insulate
     * and never render. The dress decision counts them as meeting a rule, since a coat taken off
     * indoors is still the villager's coat, and stows them when a rule says none.
     */
    public static List<WornPiece> carried(@Nullable VillagerEntityMCA villager) {
        List<WornPiece> out = new ArrayList<>();
        if (villager == null) return out;
        var inventory = villager.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack == null || stack.isEmpty()) continue;
            ClothingEntry entry = ClothingDefs.forStack(villager.level(), stack);
            if (entry == null || entry.isSkin()) continue;
            out.add(WornPiece.ofStack(entry.layer(), entry.slot(), stack, entry, CARRIED_SOURCE));
        }
        return out;
    }

    /** The layer a stack occupies: its entry's if one describes it, else armour for armor items. */
    static ClothingLayer layerOf(@Nullable ClothingEntry entry, ItemStack stack, ClothingLayer fallback) {
        if (entry != null) return entry.layer();
        if (stack.getItem() instanceof ArmorItem) return ClothingLayer.ARMOUR;
        return fallback;
    }

    private static final class ArmorSlots implements Source {
        private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET};

        @Override
        public String id() {
            return "townstead:armor_slots";
        }

        @Override
        public void collect(LivingEntity entity, Consumer<WornPiece> out) {
            for (EquipmentSlot slot : SLOTS) {
                ItemStack stack = entity.getItemBySlot(slot);
                if (stack == null || stack.isEmpty()) continue;
                ClothingEntry entry = ClothingDefs.forStack(entity.level(), stack);
                out.accept(WornPiece.ofStack(layerOf(entry, stack, ClothingLayer.OUTERWEAR),
                        ClothingChannel.of(slot), stack, entry, id()));
            }
        }
    }

    private static final class CuriosSlots implements Source {
        @Override
        public String id() {
            return "townstead:curios_slots";
        }

        @Override
        public void collect(LivingEntity entity, Consumer<WornPiece> out) {
            if (!CuriosCompat.present()) return;
            CuriosCompat.forEachWornVisible(entity, (slotId, stack) -> {
                if (stack == null || stack.isEmpty()) return;
                ClothingEntry entry = ClothingDefs.forStack(entity.level(), stack);
                ClothingChannel channel = entry != null && entry.slot() != ClothingChannel.ALL
                        ? entry.slot() : ClothingChannel.ofCurioSlot(slotId);
                out.accept(WornPiece.ofStack(entry != null ? entry.layer() : ClothingLayer.ACCESSORY,
                        channel, stack, entry, id()));
            });
        }
    }

    private static final class McaSkin implements Source {
        @Override
        public String id() {
            return "townstead:mca_skin";
        }

        @Override
        public void collect(LivingEntity entity, Consumer<WornPiece> out) {
            if (!(entity instanceof VillagerEntityMCA villager)) return;
            String clothes = villager.getClothes();
            if (clothes == null || clothes.isEmpty()) return;
            out.accept(WornPiece.ofSkin(clothes, ClothingDefs.forSkin(clothes), id()));
        }
    }
}

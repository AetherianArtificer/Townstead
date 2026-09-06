package com.aetherianartificer.townstead.inventory;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.curios.CurioSlotSpec;
import com.aetherianartificer.townstead.compat.curios.CuriosCompat;
import com.aetherianartificer.townstead.item.ScarfEquip;
import com.mojang.datafixers.util.Pair;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
//? if neoforge {
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.items.SlotItemHandler;
//?} else {
/*import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.items.SlotItemHandler;
*///?}
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A villager's inventory laid out like the player's own: armor column, portrait and offhand up top, a
 * Curios panel floating to the left the way Curios shows the player's, then the villager's storage rows
 * and the player's inventory below, chest style. Slot coordinates follow the vanilla textures so the
 * screen can be drawn from them and follow GUI resource packs.
 *
 * <p>Armor and offhand are shown but locked: MCA's equipment task owns those slots and would strip or
 * replace anything placed by hand. Curios slots are live, since MCA never touches them. Opened only with
 * Curios installed; otherwise MCA's chest stays in charge.</p>
 */
public class VillagerInventoryMenu extends AbstractContainerMenu {

    public static final int STORAGE_ROWS = 3;
    public static final int STORAGE_COLS = 9;
    public static final int STORAGE_SIZE = STORAGE_ROWS * STORAGE_COLS;
    public static final int EQUIPMENT_SLOTS = LivingEquipmentContainer.SLOTS.length;
    /** Top block (armor, portrait, offhand) is 83 tall; storage rows follow; the chest lower block is 96. */
    public static final int STORAGE_Y = 84;
    public static final int LOWER_BLOCK_Y = 83 + 18 * STORAGE_ROWS;
    public static final int PLAYER_INVENTORY_Y = LOWER_BLOCK_Y + 14;
    public static final int HOTBAR_Y = LOWER_BLOCK_Y + 72;
    public static final int IMAGE_HEIGHT = LOWER_BLOCK_Y + 96;

    private static final ResourceLocation[] EQUIPMENT_ICONS = {
            InventoryMenu.EMPTY_ARMOR_SLOT_HELMET, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
            InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS,
            InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD
    };

    @Nullable
    private final VillagerEntityMCA villager;
    private final Container storage;
    private final CurioPanel curioPanel;
    private final int curioStart;
    private final int storageStart;
    private final int playerStart;
    private final int playerEnd;

    /**
     * Geometry of the floating Curios panel, shared by the slot placement and the screen. Two columns
     * like Curios' own panel, widening only when a column would overflow the screen height.
     */
    public record CurioPanel(int count, int columns, int rows) {
        public static final int MAX_ROWS = 12;
        public static final int BORDER = 7;
        public static final int GAP = 4;

        public static CurioPanel of(int count) {
            if (count <= 0) return new CurioPanel(0, 0, 0);
            int columns = Math.max(2, (count + MAX_ROWS - 1) / MAX_ROWS);
            int rows = (count + columns - 1) / columns;
            return new CurioPanel(count, columns, rows);
        }

        public boolean isEmpty() {
            return count == 0;
        }

        public int width() {
            return BORDER * 2 + 18 * columns;
        }

        public int height() {
            return BORDER * 2 + 18 * rows;
        }

        /** Panel left edge, relative to the main panel's left edge. */
        public int x() {
            return -(GAP + width());
        }

        public int slotX(int index) {
            return x() + BORDER + 1 + 18 * (index % columns);
        }

        public int slotY(int index) {
            return BORDER + 1 + 18 * (index / columns);
        }
    }

    /** Opens the villager's inventory for {@code player}, sending the client what it needs to mirror the slots. */
    public static void open(ServerPlayer player, VillagerEntityMCA villager) {
        int curios = CuriosCompat.slotSpecs(villager).size();
        MenuProvider provider = new SimpleMenuProvider(
                (id, inventory, p) -> new VillagerInventoryMenu(id, inventory, villager, curios),
                villager.getDisplayName());
        //? if neoforge {
        player.openMenu(provider, buf -> {
            buf.writeVarInt(villager.getId());
            buf.writeVarInt(curios);
        });
        //?} else {
        /*net.minecraftforge.network.NetworkHooks.openScreen(player, provider, buf -> {
            buf.writeVarInt(villager.getId());
            buf.writeVarInt(curios);
        });
        *///?}
    }

    //? if neoforge {
    public static VillagerInventoryMenu clientFactory(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
    //?} else {
    /*public static VillagerInventoryMenu clientFactory(int id, Inventory inventory, FriendlyByteBuf buf) {
    *///?}
        int entityId = buf.readVarInt();
        int curios = buf.readVarInt();
        Entity entity = inventory.player.level().getEntity(entityId);
        return new VillagerInventoryMenu(id, inventory, entity instanceof VillagerEntityMCA v ? v : null, curios);
    }

    private VillagerInventoryMenu(int id, Inventory playerInventory, @Nullable VillagerEntityMCA villager, int curioCount) {
        super(Townstead.VILLAGER_INVENTORY_MENU.get(), id);
        this.villager = villager;
        this.storage = villager != null ? villager.getInventory() : new SimpleContainer(STORAGE_SIZE);
        this.curioPanel = CurioPanel.of(curioCount);
        storage.startOpen(playerInventory.player);

        Container equipment = villager != null ? new LivingEquipmentContainer(villager) : new SimpleContainer(EQUIPMENT_SLOTS);
        for (int i = 0; i < 4; i++) {
            addSlot(new LockedSlot(equipment, i, 8, 8 + 18 * i, EQUIPMENT_ICONS[i]));
        }
        addSlot(new LockedSlot(equipment, 4, 77, 62, EQUIPMENT_ICONS[4]));

        // The server's count is the truth for slot indices; a client that cannot see the entity (or whose
        // Curios data lags) pads with inert slots so the two sides never disagree on slot numbering.
        curioStart = slots.size();
        List<CurioSlotSpec> specs = CuriosCompat.slotSpecs(villager);
        for (int i = 0; i < curioCount; i++) {
            int x = curioPanel.slotX(i);
            int y = curioPanel.slotY(i);
            if (i < specs.size() && villager != null) {
                addSlot(new CurioSlot(villager, specs.get(i), x, y));
            } else {
                addSlot(new LockedSlot(new SimpleContainer(1), 0, x, y, null));
            }
        }

        storageStart = slots.size();
        for (int row = 0; row < STORAGE_ROWS; row++) {
            for (int col = 0; col < STORAGE_COLS; col++) {
                addSlot(new Slot(storage, col + row * STORAGE_COLS, 8 + 18 * col, STORAGE_Y + 18 * row));
            }
        }

        playerStart = slots.size();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + 18 * col, PLAYER_INVENTORY_Y + 18 * row));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + 18 * col, HOTBAR_Y));
        }
        playerEnd = slots.size();
    }

    /** The villager on show, or null on a client that could not resolve the entity. */
    @Nullable
    public VillagerEntityMCA villager() {
        return villager;
    }

    public CurioPanel curioPanel() {
        return curioPanel;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < playerStart) {
            if (!moveItemStackTo(stack, playerStart, playerEnd, true)) return ItemStack.EMPTY;
        } else {
            int curios = curioPanel.count();
            boolean placed = curios > 0 && moveItemStackTo(stack, curioStart, curioStart + curios, false);
            if (!placed && !moveItemStackTo(stack, storageStart, storageStart + STORAGE_SIZE, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return villager != null && villager.isAlive() && player.distanceToSqr(villager) <= 64.0;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        storage.stopOpen(player);
    }

    /** A slot that shows what is worn but takes nothing in or out; MCA's equipment task owns it. */
    public static final class LockedSlot extends Slot {
        @Nullable
        private final ResourceLocation icon;

        LockedSlot(Container container, int index, int x, int y, @Nullable ResourceLocation icon) {
            super(container, index, x, y);
            this.icon = icon;
        }

        /** Whether this mirrors a real equipment slot (as opposed to client-side padding). */
        public boolean mirrorsEquipment() {
            return icon != null;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            return icon == null ? null : Pair.of(InventoryMenu.BLOCK_ATLAS, icon);
        }
    }

    /** One of the villager's Curios slots, validated the way Curios validates the player's. */
    public static final class CurioSlot extends SlotItemHandler {
        private final LivingEntity wearer;
        private final CurioSlotSpec spec;

        CurioSlot(LivingEntity wearer, CurioSlotSpec spec, int x, int y) {
            super(spec.handler(), spec.index(), x, y);
            this.wearer = wearer;
            this.spec = spec;
        }

        public String slotId() {
            return spec.id();
        }

        public int slotIndex() {
            return spec.index();
        }

        public boolean canToggleRender() {
            return spec.canToggleRender();
        }

        @Override
        public void set(ItemStack stack) {
            ItemStack previous = getItem();
            super.set(stack);
            if (!wearer.level().isClientSide && !stack.isEmpty() && !ItemStack.matches(previous, stack)) {
                CuriosCompat.onEquipFromUse(wearer, spec.id(), spec.index(), stack);
            }
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !stack.isEmpty()
                    && ScarfEquip.mayWear(wearer, stack)
                    && CuriosCompat.canEquip(wearer, spec.id(), spec.index(), stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return CuriosCompat.canUnequip(wearer, spec.id(), spec.index(), getItem());
        }

        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            return Pair.of(InventoryMenu.BLOCK_ATLAS, spec.icon());
        }
    }
}

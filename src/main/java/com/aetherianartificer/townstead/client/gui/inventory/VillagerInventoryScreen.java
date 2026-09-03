package com.aetherianartificer.townstead.client.gui.inventory;

import com.aetherianartificer.townstead.compat.curios.CuriosCompat;
import com.aetherianartificer.townstead.inventory.VillagerInventoryMenu;
import com.aetherianartificer.townstead.inventory.VillagerInventoryMenu.CurioPanel;
import com.aetherianartificer.townstead.inventory.VillagerInventoryMenu.CurioSlot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The villager inventory, drawn as a mirror of the player's: the vanilla inventory texture for the
 * portrait block, the chest texture for the villager's storage rows and the player's inventory, and a
 * floating Curios panel to the left, nine-sliced from the inventory texture, with Curios' own per-slot
 * render toggles. Only the toggle sprites are Curios art; the rest follows GUI resource packs.
 */
public class VillagerInventoryScreen extends AbstractContainerScreen<VillagerInventoryMenu> {

    //? if >=1.21 {
    private static final ResourceLocation INVENTORY = ResourceLocation.withDefaultNamespace("textures/gui/container/inventory.png");
    private static final ResourceLocation CHEST = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final ResourceLocation CURIOS = ResourceLocation.fromNamespaceAndPath("curios", "textures/gui/curios/inventory.png");
    //?} else {
    /*private static final ResourceLocation INVENTORY = new ResourceLocation("textures/gui/container/inventory.png");
    private static final ResourceLocation CHEST = new ResourceLocation("textures/gui/container/generic_54.png");
    private static final ResourceLocation CURIOS = new ResourceLocation("curios", "textures/gui/inventory.png");
    *///?}

    private static final int PANEL_WIDTH = 176;
    /** Portrait block (armor, portrait, offhand) from the vanilla inventory texture. */
    private static final int TOP_HEIGHT = 83;
    /** The chest texture stacks its rows right under a 17 px header. */
    private static final int CHEST_ROWS_Y = 17;
    private static final int STORAGE_HEIGHT = 18 * VillagerInventoryMenu.STORAGE_ROWS;
    /** The chest texture's lower block: label gap, player rows, hotbar, bottom border. */
    private static final int CHEST_LOWER_Y = 126;
    private static final int CHEST_LOWER_HEIGHT = 96;
    private static final int BORDER = CurioPanel.BORDER;
    /** The vanilla panel's right border, sampled beside the player rows where no slot frame overlaps it. */
    private static final int RIGHT_BORDER_X = 169;
    private static final int CLEAN_RIGHT_BORDER_Y = 84;
    /** Curios' round render toggle: lit at (75,0) for "shown", dark at (83,0) for "hidden"; 8 px square. */
    private static final int TOGGLE_SIZE = 8;
    private static final int TOGGLE_ON_U = 75;
    private static final int TOGGLE_OFF_U = 83;
    private static final int TOGGLE_DX = 12;
    private static final int TOGGLE_DY = -1;
    private static final int TEXT_COLOR = 0x404040;

    public VillagerInventoryScreen(VillagerInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PANEL_WIDTH;
        imageHeight = VillagerInventoryMenu.IMAGE_HEIGHT;
        inventoryLabelY = imageHeight - 94;
        titleLabelX = 97;
        titleLabelY = 8;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        //? if <1.21 {
        /*renderBackground(graphics);
        *///?}
        super.render(graphics, mouseX, mouseY, partialTick);
        renderToggles(graphics);
        // A toggle sits on its slot's corner: over it, only the toggle speaks.
        if (hoveredToggle(mouseX, mouseY) != null) {
            graphics.renderTooltip(font, Component.translatable("gui.townstead.villager_inventory.toggle_render"), mouseX, mouseY);
            return;
        }
        renderTooltip(graphics, mouseX, mouseY);
        renderSlotHints(graphics, mouseX, mouseY);
    }

    /** Names an empty Curios slot the way Curios does, and explains the locked equipment slots. */
    private void renderSlotHints(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hoveredSlot == null || hoveredSlot.hasItem()) return;
        if (hoveredSlot instanceof CurioSlot curio) {
            graphics.renderTooltip(font, slotName(curio.slotId()), mouseX, mouseY);
        } else if (hoveredSlot instanceof VillagerInventoryMenu.LockedSlot locked && locked.mirrorsEquipment()) {
            graphics.renderTooltip(font,
                    Component.translatable("gui.townstead.villager_inventory.equipment_managed"), mouseX, mouseY);
        }
    }

    /** Curios' own naming: the slot type's translation when it has one, else the id capitalised. */
    private static Component slotName(String slotId) {
        String key = "curios.identifier." + slotId;
        if (net.minecraft.client.resources.language.I18n.exists(key)) return Component.translatable(key);
        String fallback = Character.toUpperCase(slotId.charAt(0))
                + slotId.substring(1).toLowerCase(java.util.Locale.ROOT);
        return Component.translatable("gui.townstead.villager_inventory.curio_slot", fallback);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.blit(INVENTORY, x, y, 0, 0, PANEL_WIDTH, TOP_HEIGHT);
        graphics.blit(CHEST, x, y + TOP_HEIGHT, 0, CHEST_ROWS_Y, PANEL_WIDTH, STORAGE_HEIGHT);
        graphics.blit(CHEST, x, y + VillagerInventoryMenu.LOWER_BLOCK_Y, 0, CHEST_LOWER_Y, PANEL_WIDTH, CHEST_LOWER_HEIGHT);
        // The crafting grid has no meaning here: plain panel over it, and a clean right border over the
        // result slot's frame, which the vanilla texture lets spill into the border.
        paintPlain(graphics, x + 97, y + 7, 72, 72);
        graphics.blit(INVENTORY, x + RIGHT_BORDER_X, y + 7, RIGHT_BORDER_X, CLEAN_RIGHT_BORDER_Y, BORDER, 72);
        renderCurioPanel(graphics, x, y);
        renderPortrait(graphics, mouseX, mouseY);
    }

    /** Tiles the empty patch between the portrait and the crafting grid over an area. */
    private static void paintPlain(GuiGraphics graphics, int x, int y, int width, int height) {
        final int patchX = 77, patchY = 7, patchW = 20, patchH = 53;
        for (int px = 0; px < width; px += patchW) {
            for (int py = 0; py < height; py += patchH) {
                graphics.blit(INVENTORY, x + px, y + py, patchX, patchY,
                        Math.min(patchW, width - px), Math.min(patchH, height - py));
            }
        }
    }

    /**
     * A floating panel like Curios' own: corners and edges nine-sliced from the inventory texture's
     * border, a plain interior, and the armor column's slot frame stamped under every Curios slot.
     */
    private void renderCurioPanel(GuiGraphics graphics, int x, int y) {
        CurioPanel panel = menu.curioPanel();
        if (panel.isEmpty()) return;
        int px = x + panel.x();
        int width = panel.width();
        int height = panel.height();
        int innerW = width - 2 * BORDER;
        int innerH = height - 2 * BORDER;

        paintPlain(graphics, px + BORDER, y + BORDER, innerW, innerH);
        // Edges: plain 18 px stretches of each border of the vanilla panel, tiled.
        for (int ox = 0; ox < innerW; ox += 18) {
            int w = Math.min(18, innerW - ox);
            graphics.blit(INVENTORY, px + BORDER + ox, y, 26, 0, w, BORDER);
            graphics.blit(INVENTORY, px + BORDER + ox, y + height - BORDER, 26, 159, w, BORDER);
        }
        for (int oy = 0; oy < innerH; oy += 18) {
            int h = Math.min(18, innerH - oy);
            graphics.blit(INVENTORY, px, y + BORDER + oy, 0, 26, BORDER, h);
            graphics.blit(INVENTORY, px + width - BORDER, y + BORDER + oy, RIGHT_BORDER_X, CLEAN_RIGHT_BORDER_Y, BORDER, h);
        }
        // Corners.
        graphics.blit(INVENTORY, px, y, 0, 0, BORDER, BORDER);
        graphics.blit(INVENTORY, px + width - BORDER, y, RIGHT_BORDER_X, 0, BORDER, BORDER);
        graphics.blit(INVENTORY, px, y + height - BORDER, 0, 159, BORDER, BORDER);
        graphics.blit(INVENTORY, px + width - BORDER, y + height - BORDER, RIGHT_BORDER_X, 159, BORDER, BORDER);
        // Slot frames (the helmet slot's 18 px frame), one per Curios slot.
        for (int i = 0; i < panel.count(); i++) {
            graphics.blit(INVENTORY, x + panel.slotX(i) - 1, y + panel.slotY(i) - 1, 7, 7, 18, 18);
        }
    }

    /** Curios' render toggles, drawn over the slot corner after the items so they stay visible. */
    private void renderToggles(GuiGraphics graphics) {
        LivingEntity subject = menu.villager();
        if (subject == null) return;
        for (Slot slot : menu.slots) {
            if (!(slot instanceof CurioSlot curio) || !curio.canToggleRender()) continue;
            boolean shown = CuriosCompat.isRendered(subject, curio.slotId(), curio.slotIndex());
            graphics.blit(CURIOS, toggleX(slot), toggleY(slot), shown ? TOGGLE_ON_U : TOGGLE_OFF_U, 0,
                    TOGGLE_SIZE, TOGGLE_SIZE, 256, 256);
        }
    }

    private CurioSlot hoveredToggle(double mouseX, double mouseY) {
        if (menu.villager() == null) return null;
        for (Slot slot : menu.slots) {
            if (slot instanceof CurioSlot curio && curio.canToggleRender() && overToggle(slot, mouseX, mouseY)) return curio;
        }
        return null;
    }

    private int toggleX(Slot slot) {
        return leftPos + slot.x + TOGGLE_DX;
    }

    private int toggleY(Slot slot) {
        return topPos + slot.y + TOGGLE_DY;
    }

    private boolean overToggle(Slot slot, double mouseX, double mouseY) {
        int tx = toggleX(slot);
        int ty = toggleY(slot);
        return mouseX >= tx && mouseX < tx + TOGGLE_SIZE && mouseY >= ty && mouseY < ty + TOGGLE_SIZE;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        LivingEntity subject = menu.villager();
        CurioSlot toggled = button == 0 ? hoveredToggle(mouseX, mouseY) : null;
        if (subject != null && toggled != null) {
            VillagerCurioRenderClient.requestToggle(subject.getId(), toggled.slotId(), toggled.slotIndex());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderPortrait(GuiGraphics graphics, int mouseX, int mouseY) {
        LivingEntity subject = menu.villager();
        if (subject == null) return;
        //? if >=1.21 {
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
                leftPos + 26, topPos + 8, leftPos + 75, topPos + 78, 30, 0.0625F, mouseX, mouseY, subject);
        //?} else {
        /*InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
                leftPos + 51, topPos + 75, 30, (float) (leftPos + 51) - mouseX, (float) (topPos + 75 - 50) - mouseY, subject);
        *///?}
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        String name = font.plainSubstrByWidth(title.getString(), 70);
        graphics.drawString(font, name, titleLabelX, titleLabelY, TEXT_COLOR, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT_COLOR, false);
    }
}

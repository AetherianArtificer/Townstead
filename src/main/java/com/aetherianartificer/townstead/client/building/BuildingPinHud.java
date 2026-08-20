package com.aetherianartificer.townstead.client.building;

import com.aetherianartificer.townstead.building.pin.BuildingPinProgressS2CPayload;
import com.aetherianartificer.townstead.building.pin.BuildingPinProgressPolicy;
import com.aetherianartificer.townstead.client.catalog.RequirementNameResolver;
import com.aetherianartificer.townstead.compat.BuildingIconResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Compact upper-right building checklist shown while the player is in the world. */
public final class BuildingPinHud {
    private BuildingPinHud() {}

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        BuildingPinProgressS2CPayload pin = BuildingPinClientStore.current();
        if (!pin.active() || minecraft.player == null || minecraft.level == null || minecraft.screen != null
                || minecraft.options.hideGui) return;

        Font font = minecraft.font;
        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        int width = Math.max(136, Math.min(168, screenW - 16));
        int x = screenW - width - 8;
        int y = 8;
        int titleHeight = 18;
        int dividerHeight = 1;
        int columnHeaderHeight = 10;
        int rowHeight = 12;
        int bottomPadding = 2;
        int listTop = titleHeight + dividerHeight + columnHeaderHeight;
        int maxRows = Math.max(1,
                Math.min(pin.rows().size(), (screenH - 58 - columnHeaderHeight) / rowHeight));
        int pageCount = Math.max(1, (int) Math.ceil(pin.rows().size() / (double) maxRows));
        long pageClock = minecraft.level.getGameTime() / 80L;
        int page = pageCount <= 1 ? 0 : (int) Math.floorMod(pageClock, pageCount);
        int start = page * maxRows;
        int end = Math.min(pin.rows().size(), start + maxRows);
        int height = listTop + ((end - start) * rowHeight) + bottomPadding;

        graphics.fill(x, y, x + width, y + height, 0xC8120E0A);
        graphics.fill(x, y + titleHeight, x + width, y + titleHeight + dividerHeight, 0x667F6840);

        ItemStack buildingIcon = buildingIcon(pin);
        if (!buildingIcon.isEmpty()) graphics.renderItem(buildingIcon, x + 4, y + 1);
        String title = Component.translatable("buildingType." + pin.buildingType()).getString();
        int titleX = x + (buildingIcon.isEmpty() ? 5 : 23);
        int pageReserve = pageCount > 1 ? 30 : 0;
        String clippedTitle = font.plainSubstrByWidth(title, Math.max(24, x + width - 5 - pageReserve - titleX));
        graphics.drawString(font, clippedTitle, titleX, y + 5, 0xFFF2E4C1, false);
        if (pageCount > 1) {
            String pageLabel = (page + 1) + "/" + pageCount;
            graphics.drawString(font, pageLabel, x + width - 5 - font.width(pageLabel), y + 5, 0xFF9E8E70, false);
        }

        String roomHeader = Component.translatable("townstead.building_pin.room_header").getString();
        String heldHeader = Component.translatable("townstead.building_pin.held_header").getString();
        int heldColumnWidth = font.width(heldHeader);
        for (int i = start; i < end; i++) {
            heldColumnWidth = Math.max(heldColumnWidth,
                    font.width(Integer.toString(Math.max(0, pin.rows().get(i).inventory()))));
        }
        int heldRight = x + width - 5;
        int roomRight = heldRight - heldColumnWidth - 7;
        int headerY = y + titleHeight + dividerHeight + 1;
        graphics.drawString(font, roomHeader, roomRight - font.width(roomHeader), headerY, 0xFF9E8E70, false);
        graphics.drawString(font, heldHeader, heldRight - font.width(heldHeader), headerY, 0xFF9E8E70, false);

        long ticker = minecraft.level.getGameTime();
        for (int i = start; i < end; i++) {
            BuildingPinProgressS2CPayload.Row row = pin.rows().get(i);
            int rowTop = y + listTop + ((i - start) * rowHeight);
            ItemStack icon = RequirementNameResolver.displayStack(row.requirement(), ticker, i);
            if (!icon.isEmpty()) {
                graphics.pose().pushPose();
                graphics.pose().scale(0.625f, 0.625f, 1.0f);
                graphics.renderItem(icon, Math.round((x + 4) / 0.625f), Math.round((rowTop + 1) / 0.625f));
                graphics.pose().popPose();
            }
            int have = BuildingPinProgressPolicy.countedBlocks(row.placed(), row.inventory());
            String room = have + "/" + row.required();
            String held = Integer.toString(Math.max(0, row.inventory()));
            String name = RequirementNameResolver.displayName(row.requirement(), icon);
            name = font.plainSubstrByWidth(name, Math.max(20, roomRight - (x + 17) - 5));
            int color = have >= row.required() ? 0xFF7FCE70 : 0xFFD8D0C2;
            graphics.drawString(font, name, x + 17, rowTop + 2, color, false);
            graphics.drawString(font, room, roomRight - font.width(room), rowTop + 2, color, false);
            graphics.drawString(font, held, heldRight - font.width(held), rowTop + 2, 0xFFB9A578, false);
        }

    }

    private static ItemStack buildingIcon(BuildingPinProgressS2CPayload pin) {
        var configured = BuildingIconResolver.nodeItemForType(pin.buildingType());
        if (configured.isPresent() && BuiltInRegistries.ITEM.containsKey(configured.get())) {
            return new ItemStack(BuiltInRegistries.ITEM.get(configured.get()));
        }
        return pin.rows().isEmpty() ? ItemStack.EMPTY
                : RequirementNameResolver.displayStack(pin.rows().get(0).requirement(), 0L, 0);
    }
}

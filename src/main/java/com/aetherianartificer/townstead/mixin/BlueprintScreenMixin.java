package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.farming.FarmingPolicyClientStore;
import com.aetherianartificer.townstead.farming.FarmingPolicySetPayload;
import com.aetherianartificer.townstead.farming.pattern.FarmPatternDefinition;
import com.aetherianartificer.townstead.farming.pattern.FarmPatternRegistry;
import com.aetherianartificer.townstead.mixin.accessor.BlueprintScreenAccessor;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.aetherianartificer.townstead.compat.ModCompat;
import net.conczin.mca.MCA;
import net.conczin.mca.client.gui.BlueprintScreen;
import net.conczin.mca.client.gui.widget.TooltipButtonWidget;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.GetVillageRequest;
import net.conczin.mca.network.c2s.ReportBuildingMessage;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.util.compat.ButtonWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.TagKey;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.net.URL;
import java.util.Enumeration;

@Mixin(BlueprintScreen.class)
public abstract class BlueprintScreenMixin extends Screen {
    @Shadow
    private String page;

    @Shadow
    private void setPage(String page) {
    }

    @Shadow
    protected abstract void drawBuildingIcon(GuiGraphics context, ResourceLocation texture, int x, int y, int u, int v);

    @Unique
    private static final String TOWNSTEAD_FARMING_PAGE = "townstead_farming";
    @Unique
    private static final String TOWNSTEAD_CATALOG_PAGE = "townstead_catalog";
    @Unique
    private static final int NAV_BUTTON_WIDTH = 80;
    @Unique
    private static final int NAV_BUTTON_HEIGHT = 20;
    @Unique
    private static final int NAV_BUTTON_STEP = 22;
    @Unique
    private static final int NAV_VISIBLE_ROWS = 6;
    @Unique
    private static final long POLICY_DEBOUNCE_NANOS = 150_000_000L; // 150ms
    @Unique
    private static final String KITCHEN_TYPE_PREFIX = "compat/farmersdelight/kitchen_l";
    @Unique
    private static final int ADV_WINDOW_W = 320;
    @Unique
    private static final int ADV_WINDOW_H = 188;
    @Unique
    private static final int ADV_INSIDE_X = 9;
    @Unique
    private static final int ADV_INSIDE_Y = 18;
    @Unique
    private static final int ADV_INSIDE_W = 302;
    @Unique
    private static final int ADV_INSIDE_H = 161;
    @Unique
    private static final int CATALOG_DETAILS_W = 108;
    @Unique
    private static final ResourceLocation MCA_BUILDING_ICONS = MCA.locate("textures/buildings.png");

    @Unique
    private final List<Button> townstead$navButtons = new ArrayList<>();
    @Unique
    private final Map<Button, Integer> townstead$navBaseY = new IdentityHashMap<>();
    @Unique
    private int townstead$navScrollPx = 0;
    @Unique
    private Button townstead$farmingNavButton;
    @Unique
    private boolean townstead$redirectingCatalog = false;
    @Unique
    private Button townstead$catalogBackButton;
    @Unique
    private Button townstead$catalogZoomInButton;
    @Unique
    private Button townstead$catalogZoomOutButton;
    @Unique
    private Button townstead$catalogNeedsPrevButton;
    @Unique
    private Button townstead$catalogNeedsNextButton;
    @Unique
    private Button townstead$upgradeBuildingButton;
    @Unique
    private String townstead$catalogReturnPage = "map";

    @Unique
    private List<String> townstead$farmingFamilies = List.of();
    @Unique
    private int townstead$farmingFamilyIndex = 0;
    @Unique
    private int townstead$farmingTier = 3;
    @Unique
    private boolean townstead$pendingPatternChange = false;
    @Unique
    private String townstead$pendingFamily = "starter_rows";
    @Unique
    private boolean townstead$pendingPolicySend = false;
    @Unique
    private long townstead$lastPolicyInputNanos = 0L;
    @Unique
    private Button townstead$farmPatternValue;
    @Unique
    private List<BuildingType> townstead$catalogEntries = List.of();
    @Unique
    private int townstead$catalogSelected = 0;
    @Unique
    private final List<NodeData> townstead$catalogNodes = new ArrayList<>();
    @Unique
    private double townstead$catalogPanX = 0.0;
    @Unique
    private double townstead$catalogPanY = 0.0;
    @Unique
    private double townstead$catalogZoom = 1.0;
    @Unique
    private boolean townstead$catalogDragging = false;
    @Unique
    private boolean townstead$catalogDragArmed = false;
    @Unique
    private double townstead$dragStartX = 0.0;
    @Unique
    private double townstead$dragStartY = 0.0;
    @Unique
    private double townstead$lastDragX = 0.0;
    @Unique
    private double townstead$lastDragY = 0.0;
    @Unique
    private final Map<String, Optional<ResourceLocation>> townstead$nodeItemIconCache = new HashMap<>();
    @Unique
    private final Map<Long, Optional<ResourceLocation>> townstead$iconUvItemCache = new HashMap<>();
    @Unique
    private int townstead$catalogNeedsPage = 0;
    @Unique
    private int townstead$catalogNeedsRowsPerPage = 1;

    @Unique
    private record NodeData(int index, BuildingType type, String group, int worldX, int worldY) {
    }

    @Unique
    private record RequirementRow(ResourceLocation id, String name, int qty) {
    }

    private BlueprintScreenMixin() {
        super(Component.empty());
    }

    @Inject(method = "setPage", at = @At("HEAD"), cancellable = true)
    private void townstead$redirectCatalogPage(String pageName, CallbackInfo ci) {
        if (!"catalog".equals(pageName) || townstead$redirectingCatalog)
            return;
        if (!TownsteadConfig.USE_TOWNSTEAD_CATALOG.get())
            return;
        if (this.page != null && !this.page.isBlank() && !TOWNSTEAD_CATALOG_PAGE.equals(this.page)) {
            townstead$catalogReturnPage = this.page;
        } else {
            townstead$catalogReturnPage = "map";
        }
        townstead$redirectingCatalog = true;
        setPage(TOWNSTEAD_CATALOG_PAGE);
        townstead$redirectingCatalog = false;
        ci.cancel();
    }

    @Inject(method = "setPage", at = @At("TAIL"))
    private void townstead$injectFarmingPage(String pageName, CallbackInfo ci) {
        townstead$collectNavButtons();
        townstead$applyNavScroll();

        if (TOWNSTEAD_FARMING_PAGE.equals(this.page)) {
            townstead$refreshFamilies();
            townstead$syncFromClientStore();
            PacketDistributor.sendToServer(new FarmingPolicySetPayload("", -1));
            townstead$addFarmingPageControls();
            townstead$setNavVisible(true);
        } else if (TOWNSTEAD_CATALOG_PAGE.equals(this.page)) {
            townstead$nodeItemIconCache.clear();
            townstead$buildCatalogEntries();
            townstead$buildCatalogNodes();
            townstead$addCatalogControls();
            townstead$catalogNeedsPage = 0;
            townstead$setNavVisible(false);
        } else if ("map".equals(this.page)) {
            townstead$addUpgradeBuildingControl();
            townstead$setNavVisible(true);
        } else {
            townstead$farmPatternValue = null;
            townstead$catalogNodes.clear();
            townstead$catalogDragging = false;
            townstead$catalogDragArmed = false;
            townstead$catalogBackButton = null;
            townstead$catalogZoomInButton = null;
            townstead$catalogZoomOutButton = null;
            townstead$catalogNeedsPrevButton = null;
            townstead$catalogNeedsNextButton = null;
            townstead$catalogNeedsPage = 0;
            townstead$upgradeBuildingButton = null;
            townstead$setNavVisible(true);
        }
        for (Button b : townstead$navButtons) {
            if (!(b.getMessage().getContents() instanceof TranslatableContents t))
                continue;
            if ("gui.blueprint.catalog".equals(t.getKey())) {
                b.active = !TOWNSTEAD_CATALOG_PAGE.equals(this.page);
            }
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void townstead$renderFarmingPage(GuiGraphics context, int mouseX, int mouseY, float partialTicks,
            CallbackInfo ci) {
        if (!TOWNSTEAD_FARMING_PAGE.equals(this.page))
            return;

        townstead$syncFromClientStore();
        townstead$flushDebouncedPolicyIfReady();
        townstead$refreshFarmingLabels();

        int cx = this.width / 2 + 38;
        int cy = this.height / 2 - 52;
        context.drawCenteredString(this.font, Component.translatable("gui.blueprint.farming"), cx, cy, 0xFFFFFF);
        context.drawCenteredString(this.font, Component.translatable("townstead.blueprint.farming.pattern"), cx,
                cy + 14, 0xA0A0A0);
        context.drawCenteredString(this.font, Component.translatable("townstead.blueprint.farming.tier.auto"), cx,
                cy + 38, 0xA0A0A0);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void townstead$refreshMapUpgradeButton(GuiGraphics context, int mouseX, int mouseY, float partialTicks,
            CallbackInfo ci) {
        if (!"map".equals(this.page) || townstead$upgradeBuildingButton == null)
            return;
        townstead$upgradeBuildingButton.active = townstead$upgradeTargetTypeAtPlayer() != null;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void townstead$renderCompatCatalog(GuiGraphics context, int mouseX, int mouseY, float partialTicks,
            CallbackInfo ci) {
        if (!TOWNSTEAD_CATALOG_PAGE.equals(this.page))
            return;
        int windowX = townstead$catalogWindowX();
        int windowY = townstead$catalogWindowY();
        context.fill(windowX, windowY, windowX + ADV_WINDOW_W, windowY + ADV_WINDOW_H, 0xFFDEDEDE);
        context.fill(windowX + 1, windowY + 1, windowX + ADV_WINDOW_W - 1, windowY + ADV_WINDOW_H - 1, 0xFF2B2F38);
        context.fill(windowX + 3, windowY + 3, windowX + ADV_WINDOW_W - 3, windowY + 14, 0xFF3A3F47);

        int insideX = windowX + ADV_INSIDE_X;
        int insideY = windowY + ADV_INSIDE_Y;
        int insideRight = insideX + ADV_INSIDE_W;
        int insideBottom = insideY + ADV_INSIDE_H;
        int graphX = insideX;
        int graphY = insideY;
        int graphW = ADV_INSIDE_W - CATALOG_DETAILS_W - 2;
        int graphH = ADV_INSIDE_H;
        int graphRight = graphX + graphW;
        int detailsX = graphRight + 2;
        int detailsY = insideY;
        int detailsRight = insideRight;
        int detailsBottom = insideBottom;

        // Reliable drag fallback: only activate after movement threshold while left
        // mouse is held.
        boolean mouseHeld = this.minecraft != null
                && GLFW.glfwGetMouseButton(this.minecraft.getWindow().getWindow(),
                        GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (!mouseHeld) {
            townstead$catalogDragging = false;
            townstead$catalogDragArmed = false;
        } else if (townstead$catalogDragArmed) {
            if (!townstead$catalogDragging) {
                double ddx = mouseX - townstead$dragStartX;
                double ddy = mouseY - townstead$dragStartY;
                if ((ddx * ddx + ddy * ddy) >= 9.0) {
                    townstead$catalogDragging = true;
                }
            }
            if (townstead$catalogDragging) {
                double dx = mouseX - townstead$lastDragX;
                double dy = mouseY - townstead$lastDragY;
                if (dx != 0.0 || dy != 0.0) {
                    townstead$catalogPanX += dx / townstead$catalogZoom;
                    townstead$catalogPanY += dy / townstead$catalogZoom;
                    townstead$lastDragX = mouseX;
                    townstead$lastDragY = mouseY;
                }
            }
        }

        context.enableScissor(graphX, graphY, graphRight, insideBottom);
        context.fill(graphX, graphY, graphRight, insideBottom, 0xFF1B1E24);
        townstead$drawCatalogGrid(context, graphX, graphY, graphW, graphH);
        townstead$drawCatalogConnections(context, graphX, graphY, graphW, graphH);
        townstead$drawCatalogNodes(context, graphX, graphY, graphW, graphH);
        context.disableScissor();

        context.fill(detailsX, detailsY, detailsRight, detailsBottom, 0xFF232A36);
        context.fill(detailsX, detailsY, detailsRight, detailsY + 1, 0xFF8CA2BF);
        context.fill(detailsX, detailsBottom - 1, detailsRight, detailsBottom, 0xFF8CA2BF);
        context.fill(detailsX, detailsY, detailsX + 1, detailsBottom, 0xFF8CA2BF);
        context.fill(detailsRight - 1, detailsY, detailsRight, detailsBottom, 0xFF8CA2BF);
        context.drawCenteredString(this.font, Component.literal("Catalog"), windowX + (ADV_WINDOW_W / 2), windowY + 6,
                0xFFFFFF);

        BuildingType selected = townstead$getSelectedCatalogEntry();
        if (selected == null)
            return;
        int detailsMidY = detailsY + ((detailsBottom - detailsY) / 2);
        context.fill(detailsX + 1, detailsMidY, detailsRight - 1, detailsMidY + 1, 0x446E86A5);

        int detailsTextX = detailsX + 4;
        int detailsTextY = detailsY + 4;
        Component nameComponent = Component.literal(townstead$displayBuildingName(selected.name()));
        context.drawWordWrap(this.font, nameComponent, detailsTextX, detailsTextY, CATALOG_DETAILS_W - 8, 0xFFFFFF);
        detailsTextY += Math.max(this.font.lineHeight + 2,
                this.font.split(nameComponent, CATALOG_DETAILS_W - 8).size() * this.font.lineHeight + 2);
        String tierLine = townstead$tierLine(selected.name());
        if (tierLine != null) {
            context.pose().pushPose();
            context.pose().scale(0.68f, 0.68f, 1.0f);
            context.drawString(this.font, Component.literal(tierLine), (int) Math.floor(detailsTextX / 0.68f),
                    (int) Math.floor(detailsTextY / 0.68f), 0xE3D18A);
            context.pose().popPose();
            detailsTextY += (int) Math.ceil(this.font.lineHeight * 0.68f) + 1;
        }
        String modLine = townstead$modLine(selected.name());
        if (modLine != null) {
            context.pose().pushPose();
            context.pose().scale(0.68f, 0.68f, 1.0f);
            context.drawString(this.font, Component.literal(modLine), (int) Math.floor(detailsTextX / 0.68f),
                    (int) Math.floor(detailsTextY / 0.68f), 0x8FC1FF);
            context.pose().popPose();
            detailsTextY += (int) Math.ceil(this.font.lineHeight * 0.68f) + 2;
        }
        String descKey = "buildingType." + selected.name() + ".description";
        String desc = Component.translatable(descKey).getString();
        if (desc.equals(descKey))
            desc = "No description.";
        Component descComponent = Component.literal(desc);
        context.pose().pushPose();
        context.pose().scale(0.85f, 0.85f, 1.0f);
        int scaledDescX = (int) Math.floor(detailsTextX / 0.85f);
        int scaledDescY = (int) Math.floor(detailsTextY / 0.85f);
        int scaledDescW = (int) Math.floor((CATALOG_DETAILS_W - 8) / 0.85f);
        context.drawWordWrap(this.font, descComponent, scaledDescX, scaledDescY, scaledDescW, 0xA8A8A8);
        context.pose().popPose();

        int needsHeaderY = detailsMidY + 6;
        context.drawString(this.font, Component.literal("Needs"), detailsTextX, needsHeaderY, 0xD0D0D0);
        int pageIndicatorY = needsHeaderY + 2;
        int pageIndicatorX = detailsRight - 50;
        int needsListTop = needsHeaderY + this.font.lineHeight + 4;
        int needsListBottom = detailsBottom - 4;
        List<RequirementRow> allRequirements = townstead$sortedRequirements(selected.getGroups());
        int rowHeight = 12;
        int listHeight = Math.max(0, needsListBottom - needsListTop);
        int rowsPerPage = Math.max(1, listHeight / rowHeight);
        townstead$catalogNeedsRowsPerPage = rowsPerPage;
        int totalPages = Math.max(1, (int) Math.ceil(allRequirements.size() / (double) rowsPerPage));
        townstead$catalogNeedsPage = Math.max(0, Math.min(townstead$catalogNeedsPage, totalPages - 1));
        boolean showPager = totalPages > 1;
        if (showPager) {
            int buttonY = needsHeaderY - 1;
            int nextX = detailsRight - 12;
            int prevX = nextX - 12;
            if (townstead$catalogNeedsPrevButton != null) {
                townstead$catalogNeedsPrevButton.visible = true;
                townstead$catalogNeedsPrevButton.active = townstead$catalogNeedsPage > 0;
                townstead$catalogNeedsPrevButton.setX(prevX);
                townstead$catalogNeedsPrevButton.setY(buttonY);
            }
            if (townstead$catalogNeedsNextButton != null) {
                townstead$catalogNeedsNextButton.visible = true;
                townstead$catalogNeedsNextButton.active = townstead$catalogNeedsPage < (totalPages - 1);
                townstead$catalogNeedsNextButton.setX(nextX);
                townstead$catalogNeedsNextButton.setY(buttonY);
            }
            pageIndicatorX = prevX - 42;
            context.pose().pushPose();
            context.pose().scale(0.72f, 0.72f, 1.0f);
            context.drawString(
                    this.font,
                    Component.literal("[" + (townstead$catalogNeedsPage + 1) + " / " + totalPages + "]"),
                    (int) Math.floor(pageIndicatorX / 0.72f),
                    (int) Math.floor(pageIndicatorY / 0.72f),
                    0xA8BDD8);
            context.pose().popPose();
        } else {
            if (townstead$catalogNeedsPrevButton != null) {
                townstead$catalogNeedsPrevButton.visible = false;
                townstead$catalogNeedsPrevButton.active = false;
            }
            if (townstead$catalogNeedsNextButton != null) {
                townstead$catalogNeedsNextButton.visible = false;
                townstead$catalogNeedsNextButton.active = false;
            }
        }

        int start = townstead$catalogNeedsPage * rowsPerPage;
        int end = Math.min(allRequirements.size(), start + rowsPerPage);
        long ticker = this.minecraft != null && this.minecraft.level != null ? this.minecraft.level.getGameTime()
                : System.currentTimeMillis() / 50L;
        String hovered = null;
        for (int i = start; i < end; i++) {
            RequirementRow row = allRequirements.get(i);
            int rowIndex = i - start;
            int rowY = needsListTop + (rowIndex * rowHeight);
            ItemStack ingredientIcon = townstead$resolveRequirementIcon(row.id(), ticker, i);
            if (!ingredientIcon.isEmpty()) {
                context.pose().pushPose();
                context.pose().scale(0.75f, 0.75f, 1.0f);
                context.renderItem(ingredientIcon, (int) Math.round((detailsTextX + 1) / 0.75f),
                        (int) Math.round((rowY - 2) / 0.75f));
                context.pose().popPose();
            }
            context.pose().pushPose();
            context.pose().scale(0.72f, 0.72f, 1.0f);
            int qtyX = (int) Math.floor((detailsTextX + 15) / 0.72f);
            int textY = (int) Math.floor(rowY / 0.72f);
            String qtyText = row.qty() + "x";
            context.drawString(this.font, Component.literal(qtyText), qtyX, textY, 0xE3D18A);
            int nameX = qtyX + 18;
            int maxNameWidth = Math.max(8, (int) Math.floor((detailsRight - 8) / 0.72f) - nameX);
            context.drawString(this.font, Component.literal(townstead$truncateToWidth(row.name(), maxNameWidth)), nameX,
                    textY, 0x9AD0FF);
            context.pose().popPose();

            int hoverLeft = detailsTextX + 14;
            int hoverRight = detailsRight - 6;
            if (mouseX >= hoverLeft && mouseX <= hoverRight && mouseY >= rowY - 1 && mouseY <= rowY + rowHeight) {
                hovered = row.name();
            }
        }
        if (hovered != null) {
            context.renderTooltip(this.font, Component.literal(hovered), mouseX, mouseY);
        }
    }

    @Inject(method = "drawBuildingIcon", at = @At("HEAD"), cancellable = true)
    private void townstead$drawCompatBuildingIcon(
            GuiGraphics context,
            ResourceLocation texture,
            int x,
            int y,
            int u,
            int v,
            CallbackInfo ci) {
        Optional<ResourceLocation> itemId = townstead$nodeItemForIconUv(u, v);
        if (itemId.isEmpty() || !BuiltInRegistries.ITEM.containsKey(itemId.get()))
            return;
        Item item = BuiltInRegistries.ITEM.get(itemId.get());
        if (item == null)
            return;
        ItemStack stack = new ItemStack(item);
        if (stack.isEmpty())
            return;
        // Match MCA map icon visual weight (smaller than full 16x16 item render).
        context.pose().pushPose();
        context.pose().translate(x - 6.0, y - 6.0, 0.0);
        context.pose().scale(0.75f, 0.75f, 1.0f);
        context.renderItem(stack, 0, 0);
        context.pose().popPose();
        ci.cancel();
    }

    @Unique
    private Optional<ResourceLocation> townstead$nodeItemForIconUv(int u, int v) {
        long key = (((long) u) << 32) ^ (v & 0xFFFFFFFFL);
        return townstead$iconUvItemCache.computeIfAbsent(key, ignored -> {
            ResourceLocation resolved = null;
            for (BuildingType bt : BuildingTypes.getInstance()) {
                if (bt.iconU() != u || bt.iconV() != v)
                    continue;
                Optional<ResourceLocation> candidate = townstead$nodeItemForType(bt.name());
                if (candidate.isEmpty() || !BuiltInRegistries.ITEM.containsKey(candidate.get()))
                    continue;
                if (resolved == null) {
                    resolved = candidate.get();
                    continue;
                }
                if (!resolved.equals(candidate.get())) {
                    // Ambiguous icon slot: do not override vanilla icon rendering.
                    return Optional.empty();
                }
            }
            return Optional.ofNullable(resolved);
        });
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void townstead$scrollNav(double mouseX, double mouseY, double horizontalAmount, double verticalAmount,
            CallbackInfoReturnable<Boolean> cir) {
        if (TOWNSTEAD_CATALOG_PAGE.equals(this.page))
            return;
        if (townstead$navButtons.isEmpty())
            return;
        int left = this.width / 2 - 180;
        int top = this.height / 2 - 56;
        int right = left + NAV_BUTTON_WIDTH;
        int bottom = top + (NAV_VISIBLE_ROWS * NAV_BUTTON_STEP);
        if (!(mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom))
            return;

        int overflowRows = Math.max(0, townstead$navButtons.size() - NAV_VISIBLE_ROWS);
        if (overflowRows <= 0)
            return;
        int maxScroll = overflowRows * NAV_BUTTON_STEP;

        if (verticalAmount < 0) {
            townstead$navScrollPx = Math.max(-maxScroll, townstead$navScrollPx - NAV_BUTTON_STEP);
        } else if (verticalAmount > 0) {
            townstead$navScrollPx = Math.min(0, townstead$navScrollPx + NAV_BUTTON_STEP);
        }
        townstead$applyNavScroll();
        cir.setReturnValue(true);
        cir.cancel();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void townstead$catalogMouseClicked(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (!TOWNSTEAD_CATALOG_PAGE.equals(this.page) || button != 0)
            return;
        int windowX = townstead$catalogWindowX();
        int windowY = townstead$catalogWindowY();
        int insideX = windowX + ADV_INSIDE_X;
        int insideY = windowY + ADV_INSIDE_Y;
        int graphW = ADV_INSIDE_W - CATALOG_DETAILS_W - 2;
        int detailsX = insideX + graphW + 2;
        int detailsY = insideY;
        int detailsRight = insideX + ADV_INSIDE_W;
        int detailsBottom = insideY + ADV_INSIDE_H;
        if (mouseX >= detailsX && mouseX <= detailsRight && mouseY >= detailsY && mouseY <= detailsBottom) {
            return;
        }
        int insideRight = insideX + (ADV_INSIDE_W - CATALOG_DETAILS_W - 2);
        int insideBottom = insideY + ADV_INSIDE_H;
        if (mouseX < insideX || mouseX > insideRight || mouseY < insideY || mouseY > insideBottom)
            return;

        townstead$catalogDragging = false;
        townstead$catalogDragArmed = true;
        townstead$dragStartX = mouseX;
        townstead$dragStartY = mouseY;
        townstead$lastDragX = mouseX;
        townstead$lastDragY = mouseY;
        int clickedIndex = townstead$findCatalogNodeAt(mouseX, mouseY, insideX, insideY);
        if (clickedIndex >= 0 && clickedIndex != townstead$catalogSelected)
            townstead$catalogSelected = clickedIndex;
        cir.setReturnValue(true);
        cir.cancel();
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void townstead$catalogMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY,
            CallbackInfoReturnable<Boolean> cir) {
        if (!TOWNSTEAD_CATALOG_PAGE.equals(this.page) || button != 0)
            return;
        int windowX = townstead$catalogWindowX();
        int windowY = townstead$catalogWindowY();
        int insideX = windowX + ADV_INSIDE_X;
        int insideY = windowY + ADV_INSIDE_Y;
        int detailsX = insideX + (ADV_INSIDE_W - CATALOG_DETAILS_W - 2) + 2;
        int detailsRight = insideX + ADV_INSIDE_W;
        int detailsBottom = insideY + ADV_INSIDE_H;
        if (mouseX >= detailsX && mouseX <= detailsRight && mouseY >= insideY && mouseY <= detailsBottom) {
            cir.setReturnValue(true);
            cir.cancel();
            return;
        }
        if (!townstead$catalogDragArmed)
            return;
        if (!townstead$catalogDragging) {
            double ddx = mouseX - townstead$dragStartX;
            double ddy = mouseY - townstead$dragStartY;
            if ((ddx * ddx + ddy * ddy) < 9.0) {
                cir.setReturnValue(true);
                cir.cancel();
                return;
            }
            townstead$catalogDragging = true;
        }
        townstead$catalogPanX += dragX / townstead$catalogZoom;
        townstead$catalogPanY += dragY / townstead$catalogZoom;
        townstead$lastDragX = mouseX;
        townstead$lastDragY = mouseY;
        cir.setReturnValue(true);
        cir.cancel();
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void townstead$catalogMouseReleased(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (!TOWNSTEAD_CATALOG_PAGE.equals(this.page) || button != 0)
            return;
        townstead$catalogDragging = false;
        townstead$catalogDragArmed = false;
        cir.setReturnValue(true);
        cir.cancel();
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void townstead$catalogKeyScroll(int keyCode, int scanCode, int modifiers,
            CallbackInfoReturnable<Boolean> cir) {
        if (!TOWNSTEAD_CATALOG_PAGE.equals(this.page))
            return;
        BuildingType selected = townstead$getSelectedCatalogEntry();
        if (selected == null)
            return;
        int pages = townstead$needsPageCount(selected.getGroups());
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_LEFT_BRACKET) {
            if (townstead$catalogNeedsPage > 0) {
                townstead$catalogNeedsPage--;
                cir.setReturnValue(true);
                cir.cancel();
            }
            return;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN || keyCode == GLFW.GLFW_KEY_RIGHT_BRACKET) {
            if (townstead$catalogNeedsPage < (pages - 1)) {
                townstead$catalogNeedsPage++;
                cir.setReturnValue(true);
                cir.cancel();
            }
        }
    }

    @Unique
    private int townstead$catalogWindowX() {
        return (this.width - ADV_WINDOW_W) / 2;
    }

    @Unique
    private int townstead$catalogWindowY() {
        return (this.height - ADV_WINDOW_H) / 2;
    }

    @Unique
    private void townstead$addCatalogControls() {
        int windowX = townstead$catalogWindowX();
        int windowY = townstead$catalogWindowY();
        townstead$catalogBackButton = addRenderableWidget(new ButtonWidget(
                windowX + 6,
                windowY + 2,
                38,
                14,
                Component.literal("<<"),
                b -> setPage(townstead$catalogReturnPage)));
        townstead$catalogZoomOutButton = addRenderableWidget(new ButtonWidget(
                windowX + ADV_WINDOW_W - 40,
                windowY + 2,
                16,
                14,
                Component.literal("-"),
                b -> townstead$applyCatalogZoom(-1,
                        windowX + ADV_INSIDE_X + ((ADV_INSIDE_W - CATALOG_DETAILS_W - 2) / 2.0),
                        windowY + ADV_INSIDE_Y + (ADV_INSIDE_H / 2.0), windowX + ADV_INSIDE_X,
                        windowY + ADV_INSIDE_Y)));
        townstead$catalogZoomInButton = addRenderableWidget(new ButtonWidget(
                windowX + ADV_WINDOW_W - 22,
                windowY + 2,
                16,
                14,
                Component.literal("+"),
                b -> townstead$applyCatalogZoom(1,
                        windowX + ADV_INSIDE_X + ((ADV_INSIDE_W - CATALOG_DETAILS_W - 2) / 2.0),
                        windowY + ADV_INSIDE_Y + (ADV_INSIDE_H / 2.0), windowX + ADV_INSIDE_X,
                        windowY + ADV_INSIDE_Y)));
        int detailsX = windowX + ADV_INSIDE_X + (ADV_INSIDE_W - CATALOG_DETAILS_W - 2) + 2;
        int detailsY = windowY + ADV_INSIDE_Y;
        int detailsMidY = detailsY + (ADV_INSIDE_H / 2);
        int needsHeaderY = detailsMidY + 6;
        int buttonY = needsHeaderY - 1;
        int rightEdge = detailsX + CATALOG_DETAILS_W - 5;
        townstead$catalogNeedsPrevButton = addRenderableWidget(new ButtonWidget(
                rightEdge - 24,
                buttonY,
                10,
                10,
                Component.literal("<"),
                b -> {
                    if (townstead$catalogNeedsPage > 0)
                        townstead$catalogNeedsPage--;
                }));
        townstead$catalogNeedsNextButton = addRenderableWidget(new ButtonWidget(
                rightEdge - 11,
                buttonY,
                10,
                10,
                Component.literal(">"),
                b -> {
                    BuildingType selected = townstead$getSelectedCatalogEntry();
                    if (selected == null)
                        return;
                    int pages = townstead$needsPageCount(selected.getGroups());
                    if (townstead$catalogNeedsPage < (pages - 1))
                        townstead$catalogNeedsPage++;
                }));
    }

    @Unique
    private void townstead$addUpgradeBuildingControl() {
        int bx = this.width / 2 + 180 - 64 - 16;
        int by = this.height / 2 - 56 + 22 * 6;
        townstead$upgradeBuildingButton = addRenderableWidget(new TooltipButtonWidget(
                bx,
                by,
                96,
                20,
                Component.translatable("townstead.blueprint.upgradeBuilding"),
                Component.translatable("townstead.blueprint.upgradeBuilding.tooltip"),
                b -> townstead$tryUpgradeCurrentBuilding()));
        String next = townstead$upgradeTargetTypeAtPlayer();
        townstead$upgradeBuildingButton.active = next != null;
    }

    @Unique
    private void townstead$tryUpgradeCurrentBuilding() {
        String nextType = townstead$upgradeTargetTypeAtPlayer();
        if (nextType == null)
            return;
        Network.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.FORCE_TYPE, nextType));
        Network.sendToServer(new GetVillageRequest());
        BlueprintScreenAccessor accessor = (BlueprintScreenAccessor) (Object) this;
        accessor.townstead$invokeSetPage("map");
    }

    @Unique
    private String townstead$upgradeTargetTypeAtPlayer() {
        if (this.minecraft == null || this.minecraft.player == null)
            return null;
        BlueprintScreenAccessor accessor = (BlueprintScreenAccessor) (Object) this;
        if (accessor.townstead$getVillage() == null)
            return null;
        BlockPos pos = this.minecraft.player.blockPosition();
        for (Building building : accessor.townstead$getVillage().getBuildings().values()) {
            if (!building.containsPos(pos))
                continue;
            return townstead$highestSatisfiableUpgradeType(building);
        }
        return null;
    }

    @Unique
    private String townstead$highestSatisfiableUpgradeType(Building building) {
        String current = building.getType();
        if (current == null)
            return null;
        int idx = current.lastIndexOf("_l");
        if (idx < 0 || idx >= current.length() - 2)
            return null;
        String tierText = current.substring(idx + 2);
        if (!tierText.chars().allMatch(Character::isDigit))
            return null;

        int startTier;
        try {
            startTier = Integer.parseInt(tierText);
        } catch (NumberFormatException ignored) {
            return null;
        }
        if (startTier <= 0)
            return null;

        String prefix = current.substring(0, idx + 2);
        String best = null;
        for (int tier = startTier + 1; tier < startTier + 20; tier++) {
            String candidateType = prefix + tier;
            if (!BuildingTypes.getInstance().getBuildingTypes().containsKey(candidateType))
                break;
            BuildingType candidate = BuildingTypes.getInstance().getBuildingType(candidateType);
            if (candidate == null)
                break;
            if (townstead$buildingMeetsRequirements(building, candidate)) {
                best = candidateType;
            } else {
                break;
            }
        }
        return best;
    }

    @Unique
    private boolean townstead$buildingMeetsRequirements(Building building, BuildingType targetType) {
        if (building == null || targetType == null)
            return false;
        Map<ResourceLocation, Integer> liveCounts = townstead$collectLiveBlockCounts(building);
        for (Map.Entry<ResourceLocation, Integer> req : targetType.getGroups().entrySet()) {
            int have = townstead$countMatchingRequirementBlocks(liveCounts, req.getKey());
            if (have < req.getValue())
                return false;
        }
        return true;
    }

    @Unique
    private Map<ResourceLocation, Integer> townstead$collectLiveBlockCounts(Building building) {
        Map<ResourceLocation, Integer> counts = new HashMap<>();
        if (this.minecraft == null)
            return counts;
        ClientLevel level = this.minecraft.level;
        if (level == null)
            return counts;

        BlockPos p0 = building.getPos0();
        BlockPos p1 = building.getPos1();
        for (BlockPos pos : BlockPos.betweenClosed(p0, p1)) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir())
                continue;
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (id == null)
                continue;
            counts.merge(id, 1, Integer::sum);
        }
        return counts;
    }

    @Unique
    private int townstead$countMatchingRequirementBlocks(Map<ResourceLocation, Integer> presentCounts,
            ResourceLocation requirement) {
        if (BuiltInRegistries.BLOCK.containsKey(requirement)) {
            return presentCounts.getOrDefault(requirement, 0);
        }
        TagKey<Block> blockTag = TagKey.create(Registries.BLOCK, requirement);
        int total = 0;
        for (Map.Entry<ResourceLocation, Integer> entry : presentCounts.entrySet()) {
            ResourceLocation blockId = entry.getKey();
            if (!BuiltInRegistries.BLOCK.containsKey(blockId))
                continue;
            Block block = BuiltInRegistries.BLOCK.get(blockId);
            if (!block.defaultBlockState().is(blockTag))
                continue;
            total += entry.getValue();
        }
        return total;
    }

    @Unique
    private void townstead$applyCatalogZoom(int direction, double px, double py, int insideX, int insideY) {
        double worldX = (px - insideX) / townstead$catalogZoom - townstead$catalogPanX;
        double worldY = (py - insideY) / townstead$catalogZoom - townstead$catalogPanY;
        double step = direction > 0 ? 1.12 : 0.88;
        double newZoom = Math.max(0.55, Math.min(2.0, townstead$catalogZoom * step));
        townstead$catalogZoom = newZoom;
        townstead$catalogPanX = (px - insideX) / newZoom - worldX;
        townstead$catalogPanY = (py - insideY) / newZoom - worldY;
    }

    @Unique
    private void townstead$setNavVisible(boolean visible) {
        for (Button b : townstead$navButtons) {
            b.visible = visible;
            b.active = visible;
        }
    }

    @Unique
    private void townstead$buildCatalogEntries() {
        List<BuildingType> all = new ArrayList<>(BuildingTypes.getInstance().getBuildingTypes().values());
        Set<String> builtTypes = new HashSet<>();
        BlueprintScreenAccessor accessor = (BlueprintScreenAccessor) (Object) this;
        if (accessor.townstead$getVillage() != null) {
            for (Building building : accessor.townstead$getVillage().getBuildings().values()) {
                builtTypes.add(building.getType());
            }
        }
        all = all.stream()
                .filter(BuildingType::visible)
                .filter(bt -> ModCompat.isCompatAvailable(bt.name()))
                .sorted(Comparator.comparing(this::townstead$catalogSortKey))
                .collect(Collectors.toList());
        townstead$catalogEntries = all;
        townstead$catalogSelected = Math.max(0, Math.min(townstead$catalogSelected, Math.max(0, all.size() - 1)));
    }

    @Unique
    private void townstead$buildCatalogNodes() {
        townstead$catalogNodes.clear();
        if (townstead$catalogEntries.isEmpty())
            return;

        Map<String, List<Integer>> grouped = new LinkedHashMap<>();
        for (int i = 0; i < townstead$catalogEntries.size(); i++) {
            BuildingType type = townstead$catalogEntries.get(i);
            String group = townstead$compatGroupLabel(type.name());
            grouped.computeIfAbsent(group, ignored -> new ArrayList<>()).add(i);
        }

        int y = 16;
        for (Map.Entry<String, List<Integer>> entry : grouped.entrySet()) {
            List<Integer> indices = entry.getValue();
            int maxBottom = y + 24;
            int col = 0;
            int row = 0;
            for (int index : indices) {
                BuildingType type = townstead$catalogEntries.get(index);
                String name = type.name();
                int nodeX;
                int nodeY;
                if (name.startsWith(KITCHEN_TYPE_PREFIX)) {
                    int tier = 1;
                    try {
                        tier = Integer.parseInt(name.substring(KITCHEN_TYPE_PREFIX.length()));
                    } catch (NumberFormatException ignored) {
                    }
                    nodeX = 24 + (tier - 1) * 56;
                    nodeY = y + 8;
                } else {
                    nodeX = 24 + col * 56;
                    nodeY = y + 8 + row * 42;
                    col++;
                    if (col >= 4) {
                        col = 0;
                        row++;
                    }
                }
                townstead$catalogNodes.add(new NodeData(index, type, entry.getKey(), nodeX, nodeY));
                maxBottom = Math.max(maxBottom, nodeY + 30);
            }
            y = maxBottom + 26;
        }
    }

    @Unique
    private boolean townstead$isCatalogEntryVisible(String typeName, Set<String> builtTypes) {
        return true;
    }

    @Unique
    private void townstead$drawCatalogGrid(GuiGraphics context, int insideX, int insideY, int insideW, int insideH) {
        int spacing = Math.max(14, (int) Math.round(20 * townstead$catalogZoom));
        int offsetX = (int) Math.round((townstead$catalogPanX * townstead$catalogZoom) % spacing);
        int offsetY = (int) Math.round((townstead$catalogPanY * townstead$catalogZoom) % spacing);
        for (int x = insideX - spacing + offsetX; x <= insideX + insideW; x += spacing) {
            context.fill(x, insideY, x + 1, insideY + insideH, 0x182A2F38);
        }
        for (int y = insideY - spacing + offsetY; y <= insideY + insideH; y += spacing) {
            context.fill(insideX, y, insideX + insideW, y + 1, 0x182A2F38);
        }
    }

    @Unique
    private void townstead$drawCatalogConnections(GuiGraphics context, int insideX, int insideY, int insideW,
            int insideH) {
        for (int tier = 1; tier < 5; tier++) {
            NodeData from = null;
            NodeData to = null;
            String fromId = KITCHEN_TYPE_PREFIX + tier;
            String toId = KITCHEN_TYPE_PREFIX + (tier + 1);
            for (NodeData node : townstead$catalogNodes) {
                String name = node.type().name();
                if (fromId.equals(name))
                    from = node;
                if (toId.equals(name))
                    to = node;
            }
            if (from == null || to == null)
                continue;
            int x1 = insideX + (int) Math.round((from.worldX() + 26 + townstead$catalogPanX) * townstead$catalogZoom);
            int y1 = insideY + (int) Math.round((from.worldY() + 13 + townstead$catalogPanY) * townstead$catalogZoom);
            int x2 = insideX + (int) Math.round((to.worldX() + townstead$catalogPanX) * townstead$catalogZoom);
            int y2 = insideY + (int) Math.round((to.worldY() + 13 + townstead$catalogPanY) * townstead$catalogZoom);
            int minY = Math.min(y1, y2);
            int maxY = Math.max(y1, y2);
            context.fill(Math.min(x1, x2), minY, Math.max(x1, x2) + 1, maxY + 1, 0xFFA6B6CC);
        }
    }

    @Unique
    private void townstead$drawCatalogNodes(GuiGraphics context, int insideX, int insideY, int insideW, int insideH) {
        for (NodeData node : townstead$catalogNodes) {
            int screenX = insideX + (int) Math.round((node.worldX() + townstead$catalogPanX) * townstead$catalogZoom);
            int screenY = insideY + (int) Math.round((node.worldY() + townstead$catalogPanY) * townstead$catalogZoom);
            int nodeW = Math.max(16, (int) Math.round(26 * townstead$catalogZoom));
            int nodeH = Math.max(16, (int) Math.round(26 * townstead$catalogZoom));

            int border = node.index() == townstead$catalogSelected ? 0xFFD9E9FF : 0xFF6D7A8D;
            int fill = node.index() == townstead$catalogSelected ? 0xFF3A4D66 : 0xFF2A3342;
            context.fill(screenX - 1, screenY - 1, screenX + nodeW + 1, screenY + nodeH + 1, border);
            context.fill(screenX, screenY, screenX + nodeW, screenY + nodeH, fill);

            townstead$drawNodeIcon(context, node, screenX, screenY, nodeW, nodeH);
        }
    }

    @Unique
    private ItemStack townstead$resolveNodeIcon(BuildingType type) {
        Optional<ResourceLocation> configured = townstead$nodeItemForType(type.name());
        if (configured.isPresent() && BuiltInRegistries.ITEM.containsKey(configured.get())) {
            Item item = BuiltInRegistries.ITEM.get(configured.get());
            if (item != null)
                return new ItemStack(item);
        }
        for (ResourceLocation requirement : type.getGroups().keySet()) {
            if (BuiltInRegistries.BLOCK.containsKey(requirement)) {
                Item item = BuiltInRegistries.BLOCK.get(requirement).asItem();
                if (item != null)
                    return new ItemStack(item);
            }
            if (BuiltInRegistries.ITEM.containsKey(requirement)) {
                Item item = BuiltInRegistries.ITEM.get(requirement);
                if (item != null)
                    return new ItemStack(item);
            }
        }
        return ItemStack.EMPTY;
    }

    @Unique
    private Optional<ResourceLocation> townstead$nodeItemForType(String buildingTypeName) {
        Optional<ResourceLocation> cached = townstead$nodeItemIconCache.get(buildingTypeName);
        if (cached != null && cached.isPresent()) {
            return cached;
        }
        Optional<ResourceLocation> result = Optional.empty();
        try {
            String relPath = "data/mca/building_types/" + buildingTypeName + ".json";
            ClassLoader cl = BlueprintScreenMixin.class.getClassLoader();
            if (cl != null) {
                Enumeration<URL> urls = cl.getResources(relPath);
                while (urls.hasMoreElements()) {
                    URL url = urls.nextElement();
                    try (InputStream in = url.openStream();
                            InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                        JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                        if (obj.has("townsteadNodeItem")) {
                            result = Optional.of(ResourceLocation.parse(obj.get("townsteadNodeItem").getAsString()));
                            break;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            result = Optional.empty();
        }
        if (result.isPresent()) {
            townstead$nodeItemIconCache.put(buildingTypeName, result);
        }
        return result;
    }

    @Unique
    private void townstead$drawNodeIcon(GuiGraphics context, NodeData node, int screenX, int screenY, int nodeW,
            int nodeH) {
        BuildingType type = node.type();
        if (!type.name().startsWith("compat/")) {
            int centerX = screenX + (nodeW / 2);
            int centerY = screenY + (nodeH / 2);
            this.drawBuildingIcon(context, MCA_BUILDING_ICONS, centerX, centerY, type.iconU(), type.iconV());
            return;
        }
        ItemStack icon = townstead$resolveNodeIcon(type);
        if (icon.isEmpty())
            return;
        int iconX = screenX + nodeW / 2 - 8;
        int iconY = screenY + nodeH / 2 - 8;
        context.renderItem(icon, iconX, iconY);
    }

    @Unique
    private int townstead$findCatalogNodeAt(double mouseX, double mouseY, int insideX, int insideY) {
        for (int i = townstead$catalogNodes.size() - 1; i >= 0; i--) {
            NodeData node = townstead$catalogNodes.get(i);
            int screenX = insideX + (int) Math.round((node.worldX() + townstead$catalogPanX) * townstead$catalogZoom);
            int screenY = insideY + (int) Math.round((node.worldY() + townstead$catalogPanY) * townstead$catalogZoom);
            int nodeW = Math.max(16, (int) Math.round(26 * townstead$catalogZoom));
            int nodeH = Math.max(16, (int) Math.round(26 * townstead$catalogZoom));
            if (mouseX >= screenX && mouseX <= screenX + nodeW && mouseY >= screenY && mouseY <= screenY + nodeH) {
                return node.index();
            }
        }
        return -1;
    }

    @Unique
    private BuildingType townstead$getSelectedCatalogEntry() {
        if (townstead$catalogEntries.isEmpty())
            return null;
        int idx = Math.max(0, Math.min(townstead$catalogSelected, townstead$catalogEntries.size() - 1));
        return townstead$catalogEntries.get(idx);
    }

    @Unique
    private String townstead$catalogSortKey(BuildingType type) {
        String group = townstead$compatGroupLabel(type.name());
        String name = townstead$displayBuildingName(type.name());
        return group + "|" + name;
    }

    @Unique
    private String townstead$compatGroupLabel(String name) {
        if (!name.startsWith("compat/"))
            return "Core";
        String[] parts = name.split("/");
        if (parts.length < 2)
            return "Compat";
        String mod = parts[1];
        if ("farmersdelight".equals(mod))
            return "Farmer's Delight";
        return mod.substring(0, 1).toUpperCase(Locale.ROOT) + mod.substring(1);
    }

    @Unique
    private String townstead$modLine(String buildingTypeId) {
        if (!buildingTypeId.startsWith("compat/"))
            return null;
        return townstead$compatGroupLabel(buildingTypeId);
    }

    @Unique
    private String townstead$tierLine(String buildingTypeId) {
        int tier = -1;
        if (buildingTypeId.matches(".*_l\\d+$")) {
            int idx = buildingTypeId.lastIndexOf("_l");
            try {
                tier = Integer.parseInt(buildingTypeId.substring(idx + 2));
            } catch (NumberFormatException ignored) {
                tier = -1;
            }
        }
        if (tier <= 0)
            return null;
        return "Tier " + townstead$roman(tier);
    }

    @Unique
    private String townstead$roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> Integer.toString(value);
        };
    }

    @Unique
    private String townstead$displayBuildingName(String buildingTypeId) {
        String key = "buildingType." + buildingTypeId;
        String translated = Component.translatable(key).getString();
        if (!translated.equals(key))
            return translated;
        String[] parts = buildingTypeId.split("/");
        String raw = parts[parts.length - 1];
        String[] words = raw.split("_");
        StringBuilder out = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty())
                continue;
            if (!out.isEmpty())
                out.append(' ');
            out.append(w.substring(0, 1).toUpperCase(Locale.ROOT)).append(w.substring(1));
        }
        return out.toString();
    }

    @Unique
    private String townstead$displayRequirementName(ResourceLocation id) {
        if (BuiltInRegistries.BLOCK.containsKey(id)) {
            Block block = BuiltInRegistries.BLOCK.get(id);
            return Component.translatable(block.getDescriptionId()).getString();
        }
        String tagPath = id.toString().replace(':', '.').replace('/', '.');
        String slashKey = "tag.block." + tagPath;
        String dottedKey = "tag.item." + tagPath;
        String slash = Component.translatable(slashKey).getString();
        if (!slash.equals(slashKey))
            return slash;
        String dotted = Component.translatable(dottedKey).getString();
        if (!dotted.equals(dottedKey))
            return dotted;
        String fallback = id.getPath().replace('_', ' ');
        if (fallback.endsWith("s") && fallback.length() > 3) {
            fallback = fallback.substring(0, fallback.length() - 1);
        }
        String[] words = fallback.split(" ");
        StringBuilder out = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty())
                continue;
            if (!out.isEmpty())
                out.append(' ');
            out.append(w.substring(0, 1).toUpperCase(Locale.ROOT)).append(w.substring(1));
        }
        return out.toString();
    }

    @Unique
    private ItemStack townstead$resolveRequirementIcon(ResourceLocation id, long ticker, int salt) {
        if (BuiltInRegistries.BLOCK.containsKey(id)) {
            Item item = BuiltInRegistries.BLOCK.get(id).asItem();
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        }
        if (BuiltInRegistries.ITEM.containsKey(id)) {
            Item item = BuiltInRegistries.ITEM.get(id);
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        }

        List<Item> candidates = new ArrayList<>();
        TagKey<Block> blockTag = TagKey.create(Registries.BLOCK, id);
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!block.defaultBlockState().is(blockTag))
                continue;
            Item item = block.asItem();
            if (item == null || item == ItemStack.EMPTY.getItem())
                continue;
            candidates.add(item);
        }
        if (candidates.isEmpty()) {
            TagKey<Item> itemTag = TagKey.create(Registries.ITEM, id);
            for (Item item : BuiltInRegistries.ITEM) {
                if (item.builtInRegistryHolder().is(itemTag)) {
                    candidates.add(item);
                }
            }
        }
        if (candidates.isEmpty())
            return ItemStack.EMPTY;
        int idx = (int) Math.floorMod((ticker / 20L) + salt, candidates.size());
        return new ItemStack(candidates.get(idx));
    }

    @Unique
    private String townstead$truncate(String text, int visibleChars) {
        if (text.length() <= visibleChars)
            return text;
        return text.substring(0, Math.max(1, visibleChars - 1)) + "…";
    }

    @Unique
    private String townstead$truncateToWidth(String text, int maxWidth) {
        if (this.font == null || maxWidth <= 0)
            return text;
        if (this.font.width(text) <= maxWidth)
            return text;
        String ellipsis = "…";
        int ellipsisWidth = this.font.width(ellipsis);
        if (ellipsisWidth >= maxWidth)
            return ellipsis;
        int end = text.length();
        while (end > 1) {
            String candidate = text.substring(0, end) + ellipsis;
            if (this.font.width(candidate) <= maxWidth)
                return candidate;
            end--;
        }
        return ellipsis;
    }

    @Unique
    private List<RequirementRow> townstead$sortedRequirements(Map<ResourceLocation, Integer> requirements) {
        return requirements.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<ResourceLocation, Integer> e) -> e.getKey().toString())
                        .thenComparingInt(Map.Entry::getValue))
                .map(e -> new RequirementRow(e.getKey(), townstead$displayRequirementName(e.getKey()), e.getValue()))
                .toList();
    }

    @Unique
    private int townstead$needsPageCount(Map<ResourceLocation, Integer> requirements) {
        int total = requirements.size();
        int rows = Math.max(1, townstead$catalogNeedsRowsPerPage);
        return Math.max(1, (int) Math.ceil(total / (double) rows));
    }

    @Unique
    private void townstead$collectNavButtons() {
        townstead$navButtons.clear();
        townstead$navBaseY.clear();

        int navX = this.width / 2 - 180;
        for (GuiEventListener listener : this.children()) {
            if (!(listener instanceof Button b))
                continue;
            if (b.getWidth() != NAV_BUTTON_WIDTH)
                continue;
            if (b.getHeight() != NAV_BUTTON_HEIGHT)
                continue;
            if (b.getX() != navX)
                continue;
            townstead$navButtons.add(b);
        }
        townstead$navButtons.sort(Comparator.comparingInt(Button::getY));
        for (Button b : townstead$navButtons) {
            townstead$navBaseY.put(b, b.getY());
        }
    }

    @Unique
    private void townstead$ensureFarmingNavButton() {
        if (townstead$farmingNavButton != null && this.children().contains(townstead$farmingNavButton)) {
            if (!townstead$navButtons.contains(townstead$farmingNavButton)) {
                townstead$navButtons.add(townstead$farmingNavButton);
            }
            return;
        }

        int navX = this.width / 2 - 180;
        int navYStart = this.height / 2 - 56;
        int y = navYStart + (NAV_BUTTON_STEP * townstead$navButtons.size());

        townstead$farmingNavButton = addRenderableWidget(new ButtonWidget(
                navX, y, NAV_BUTTON_WIDTH, NAV_BUTTON_HEIGHT,
                Component.translatable("gui.blueprint.farming"),
                b -> setPage(TOWNSTEAD_FARMING_PAGE)));
        townstead$navButtons.add(townstead$farmingNavButton);
        townstead$navBaseY.put(townstead$farmingNavButton, y);
    }

    @Unique
    private void townstead$applyNavScroll() {
        for (Button b : townstead$navButtons) {
            Integer baseY = townstead$navBaseY.get(b);
            if (baseY == null)
                continue;
            b.setY(baseY + townstead$navScrollPx);
        }
    }

    @Unique
    private void townstead$addFarmingPageControls() {
        int x = this.width / 2 - 26;
        int y = this.height / 2 - 32;

        addRenderableWidget(new ButtonWidget(x, y, 20, 20, Component.literal("<"), b -> townstead$cycleFamily(-1)));
        townstead$farmPatternValue = addRenderableWidget(new ButtonWidget(x + 22, y, 84, 20, Component.empty(), b -> {
        }));
        townstead$farmPatternValue.active = false;
        addRenderableWidget(
                new ButtonWidget(x + 108, y, 20, 20, Component.literal(">"), b -> townstead$cycleFamily(1)));

        townstead$refreshFarmingLabels();
    }

    @Unique
    private void townstead$refreshFamilies() {
        List<String> families = FarmPatternRegistry.all().stream()
                .map(FarmPatternDefinition::family)
                .filter(f -> f != null && !f.isBlank())
                .distinct()
                .sorted(Comparator.comparing((String f) -> !"starter_rows".equals(f)).thenComparing(String::compareTo))
                .toList();
        if (families.isEmpty())
            families = List.of("starter_rows");
        townstead$farmingFamilies = new ArrayList<>(families);
    }

    @Unique
    private void townstead$syncFromClientStore() {
        String currentFamily = FarmingPolicyClientStore.getPatternId();
        townstead$farmingTier = Math.max(1, Math.min(5, FarmingPolicyClientStore.getTier()));
        if (townstead$pendingPatternChange) {
            if (currentFamily.equals(townstead$pendingFamily)) {
                townstead$pendingPatternChange = false;
            } else {
                // Keep optimistic UI selection until server confirms.
                return;
            }
        }
        int idx = townstead$farmingFamilies.indexOf(currentFamily);
        if (idx >= 0) {
            townstead$farmingFamilyIndex = idx;
        } else if (!townstead$farmingFamilies.isEmpty()) {
            townstead$farmingFamilyIndex = 0;
        }
    }

    @Unique
    private void townstead$cycleFamily(int delta) {
        if (townstead$farmingFamilies.isEmpty())
            return;
        int size = townstead$farmingFamilies.size();
        int next = (townstead$farmingFamilyIndex + delta) % size;
        if (next < 0)
            next += size;
        townstead$farmingFamilyIndex = next;
        townstead$pendingFamily = townstead$farmingFamilies.get(townstead$farmingFamilyIndex);
        townstead$pendingPatternChange = true;
        townstead$queuePolicySend();
        townstead$refreshFarmingLabels();
    }

    @Unique
    private void townstead$sendPolicy() {
        String family = townstead$farmingFamilies.isEmpty()
                ? "starter_rows"
                : townstead$farmingFamilies.get(townstead$farmingFamilyIndex);
        // Keep policy tier cap at max so per-villager progression drives effective
        // unlocks.
        PacketDistributor.sendToServer(new FarmingPolicySetPayload(family, 5));
    }

    @Unique
    private void townstead$queuePolicySend() {
        townstead$pendingPolicySend = true;
        townstead$lastPolicyInputNanos = System.nanoTime();
    }

    @Unique
    private void townstead$flushDebouncedPolicyIfReady() {
        if (!townstead$pendingPolicySend)
            return;
        long now = System.nanoTime();
        if (now - townstead$lastPolicyInputNanos < POLICY_DEBOUNCE_NANOS)
            return;
        townstead$pendingPolicySend = false;
        townstead$sendPolicy();
    }

    @Unique
    private void townstead$refreshFarmingLabels() {
        if (townstead$farmPatternValue != null) {
            String family = townstead$farmingFamilies.isEmpty()
                    ? "starter_rows"
                    : townstead$farmingFamilies.get(townstead$farmingFamilyIndex);
            townstead$farmPatternValue.setMessage(Component.literal(townstead$displayFamilyName(family)));
        }
    }

    @Unique
    private String townstead$displayFamilyName(String family) {
        String key = "townstead.farming.family." + family;
        String translated = Component.translatable(key).getString();
        if (!translated.equals(key))
            return translated;
        String[] parts = family.split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty())
                continue;
            if (!out.isEmpty())
                out.append(' ');
            out.append(part.substring(0, 1).toUpperCase(Locale.ROOT))
                    .append(part.substring(1));
        }
        return out.toString();
    }
}

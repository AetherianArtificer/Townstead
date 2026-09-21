package com.aetherianartificer.townstead.mixin;

//? if forge {
/*import com.aetherianartificer.townstead.TownsteadNetwork;
*///?}
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.client.gui.shift.ShiftManagerScreen;
import com.aetherianartificer.townstead.compat.BuildingIconResolver;
import com.aetherianartificer.townstead.mixin.accessor.BlueprintScreenAccessor;
import com.aetherianartificer.townstead.profession.ProfessionClientStore;
import com.aetherianartificer.townstead.profession.ProfessionQueryPayload;
import com.aetherianartificer.townstead.profession.ProfessionSetPayload;
import com.aetherianartificer.townstead.recognition.OptionalBuildingRecognition;
import com.aetherianartificer.townstead.village.VillageResidentClientStore;
import net.conczin.mca.MCA;
import net.conczin.mca.client.gui.BlueprintScreen;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.conczin.mca.server.world.data.Building;
//? if >=1.21 {
import net.conczin.mca.server.world.data.RoomScanPlan;
//?}
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.util.compat.ButtonWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Mixin(BlueprintScreen.class)
public abstract class BlueprintScreenMixin extends Screen {
    @Shadow(remap = false)
    private String page;

    @Shadow(remap = false)
    private Village village;

    //? if >=1.21 {
    @Shadow(remap = false)
    private ButtonWidget removeBuildingButton;
    //?}

    @Shadow(remap = false)
    private void setPage(String page) {
    }

    @Unique
    private static final String TOWNSTEAD_CATALOG_PAGE = "townstead_catalog";
    @Unique
    private static final String TOWNSTEAD_SPIRIT_PAGE = "townstead_spirit";
    @Unique
    private static final String TOWNSTEAD_PROFESSION_PAGE = "townstead_profession";
    @Unique
    private static final int NAV_BUTTON_WIDTH = 80;
    @Unique
    private static final int NAV_BUTTON_HEIGHT = 20;
    @Unique
    private static final int NAV_BUTTON_STEP = 22;
    @Unique
    private static final int NAV_VISIBLE_ROWS = 6;
    @Unique
    private final List<Button> townstead$navButtons = new ArrayList<>();
    @Unique
    private final Map<Button, Integer> townstead$navBaseY = new IdentityHashMap<>();
    @Unique
    private int townstead$navScrollPx = 0;
    @Unique
    private boolean townstead$redirectingCatalog = false;

    @Unique
    private com.aetherianartificer.townstead.client.gui.catalog.CatalogPanel townstead$catalogPanel;

    @Unique
    private String townstead$catalogReturnPage = "map";

    @Unique
    private final Map<String, String> townstead$translationTextCache = new HashMap<>();
    @Unique
    private final Map<String, Component> townstead$translationComponentCache = new HashMap<>();
    // --- Profession page state ---
    @Unique
    private static final int PROF_ROWS_PER_PAGE = 7;
    @Unique
    private int townstead$profPage = 0;
    @Unique
    private List<UUID> townstead$profVillagerUuids = List.of();
    @Unique
    private Map<UUID, String> townstead$profVillagerNames = new HashMap<>();
    @Unique
    private Map<UUID, Integer> townstead$profVillagerEntityIds = new HashMap<>();
    @Unique
    private UUID townstead$profSelectedVillager = null;
    @Unique
    private int townstead$profScroll = 0;
    @Unique
    private float townstead$spiritScrollCurrent = 0f; // interpolated pixel offset
    @Unique
    private int townstead$spiritScrollTarget = 0;     // desired pixel offset
    @Unique
    private boolean townstead$spiritRadarMode = false;
    @Unique
    private final java.util.Map<Integer, java.util.Set<String>> townstead$spiritCollapsedByVillage = new java.util.HashMap<>();
    @Unique
    private final java.util.Map<Integer, Integer> townstead$spiritScrollByVillage = new java.util.HashMap<>();
    @Unique
    private final java.util.Map<Integer, ContribCacheEntry> townstead$spiritContribCache = new java.util.HashMap<>();
    @Unique
    private long townstead$lastNarrationMs = 0L;
    @Unique
    private int townstead$lastHoverMouseX = Integer.MIN_VALUE;
    @Unique
    private int townstead$lastHoverMouseY = Integer.MIN_VALUE;
    @Unique
    private String townstead$lastHoverSpirit = null;
    @Unique
    private int townstead$spiritSortMode = 0;   // 0=points, 1=count, 2=alpha
    @Unique
    private boolean townstead$spiritFilterTop3 = false;
    @Unique
    private boolean townstead$spiritFilterThreshold = false; // hide under 10% share
    @Unique
    private String townstead$pendingCatalogBuildingType = null;
    @Unique
    private String townstead$lastNarratedSpirit = null;
    @Unique
    private ButtonWidget townstead$spiritSortBtn;
    @Unique
    private ButtonWidget townstead$spiritTop3Btn;
    @Unique
    private ButtonWidget townstead$spiritThresholdBtn;

    private BlueprintScreenMixin() {
        super(Component.empty());
    }

    //? if >=1.21 {
    /**
     * MCA only exposes this control when its enclosed-room scan can update/add a room. External
     * buildings have no room scan by design, so surface MCA's own button when Townstead can resolve
     * a nearby datapack-declared open-air building instead.
     */
    @Inject(method = "updateStructureScanControl", remap = false, at = @At("TAIL"))
    private void townstead$showRemoveForOpenAirBuilding(RoomScanPlan plan, CallbackInfo ci) {
        if (removeBuildingButton == null || village == null || minecraft == null || minecraft.player == null) {
            return;
        }
        if (OptionalBuildingRecognition.findNearby(village, minecraft.player.blockPosition()).isPresent()) {
            removeBuildingButton.visible = true;
            removeBuildingButton.active = true;
        }
    }
    //?}

    @Inject(method = "setPage", remap = false, at = @At("HEAD"), cancellable = true)
    private void townstead$redirectCatalogPage(String pageName, CallbackInfo ci) {
        if (TOWNSTEAD_CATALOG_PAGE.equals(pageName) && !TOWNSTEAD_CATALOG_PAGE.equals(this.page)
                && !townstead$redirectingCatalog && this.page != null && !this.page.isBlank()) {
            townstead$catalogReturnPage = this.page;
        }
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

    @Inject(method = "setPage", remap = false, at = @At("TAIL"))
    private void townstead$injectFarmingPage(String pageName, CallbackInfo ci) {
        townstead$collectNavButtons();
        townstead$applyNavScroll();

        if (TOWNSTEAD_SPIRIT_PAGE.equals(this.page)) {
            townstead$initSpiritPage();
            // Full-panel takeover: hide MCA's nav column (same as catalog)
            // so the spirit panel can own the screen width without the
            // nav buttons bleeding through.
            townstead$setNavVisible(false);
        } else if (TOWNSTEAD_PROFESSION_PAGE.equals(this.page)) {
            townstead$initProfessionPage();
            townstead$setNavVisible(true);
        } else if (TOWNSTEAD_CATALOG_PAGE.equals(this.page)) {
            // The catalog owns its navigation, including Back. Remove MCA's close/nav controls
            // from both rendering and hit testing before registering the shared panel.
            clearWidgets();
            if (townstead$catalogPanel == null) {
                townstead$catalogPanel = new com.aetherianartificer.townstead.client.gui.catalog.CatalogPanel(
                        this, this.font, () -> setPage(townstead$catalogReturnPage),
                        () -> setPage(TOWNSTEAD_CATALOG_PAGE));
            }
            if (townstead$pendingCatalogBuildingType != null) {
                townstead$catalogPanel.focusBuilding(townstead$pendingCatalogBuildingType);
                townstead$pendingCatalogBuildingType = null;
            }
            townstead$catalogPanel.init(this.width, this.height, village, widget -> addWidget(widget));
            townstead$setNavVisible(false);
        } else if ("map".equals(this.page)) {
            townstead$setNavVisible(true);
        } else if ("rank".equals(this.page)) {
            townstead$addSpiritButtonOnStatusPage();
            townstead$setNavVisible(true);
        } else if ("villagers".equals(this.page)) {
            townstead$addVillagersPageControls();
            townstead$setNavVisible(true);
        } else {
            townstead$profSelectedVillager = null;
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

    //? if neoforge {
    @Inject(method = "render", at = @At("TAIL"))
    //?} else {
    /*@Inject(method = "m_88315_", remap = false, at = @At("TAIL"))
    *///?}
    private void townstead$renderCompatCatalog(GuiGraphics context, int mouseX, int mouseY, float partialTicks,
            CallbackInfo ci) {
        if (TOWNSTEAD_CATALOG_PAGE.equals(this.page) && townstead$catalogPanel != null)
            townstead$catalogPanel.render(context, mouseX, mouseY, partialTicks, village);
    }

    // Map item icons are applied where the renderer still has the BuildingType:
    // BlueprintMapRendererIconMixin on the floor-system API and
    // BlueprintScreenLegacyIconMixin on 1.20.1. Neither path depends on MCA atlas UVs.

    //? if neoforge {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (townstead$dispatchScroll(mouseX, mouseY, verticalAmount))
            return true;
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (townstead$dispatchScroll(mouseX, mouseY, delta))
            return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
    *///?}

    @Unique
    private boolean townstead$dispatchScroll(double mouseX, double mouseY, double verticalAmount) {
        if (townstead$handleCatalogScroll(mouseX, mouseY, verticalAmount))
            return true;
        if (townstead$handleProfessionScroll(mouseX, mouseY, verticalAmount))
            return true;
        if (townstead$handleSpiritScroll(mouseX, mouseY, verticalAmount))
            return true;
        return townstead$handleNavScroll(mouseX, mouseY, verticalAmount);
    }

    @Unique
    private boolean townstead$handleSpiritScroll(double mouseX, double mouseY, double verticalAmount) {
        if (!TOWNSTEAD_SPIRIT_PAGE.equals(this.page)) return false;
        if (verticalAmount == 0) return false;
        // Pixel-based scroll; 18 px per wheel tick feels about right for a
        // 10 px bar + 8 px header/footer row.
        int step = 18;
        townstead$setSpiritScrollTarget(townstead$spiritScrollTarget
                + (verticalAmount > 0 ? -step : step));
        return true;
    }

    @Unique
    private boolean townstead$handleCatalogScroll(double mouseX, double mouseY, double verticalAmount) {
        return TOWNSTEAD_CATALOG_PAGE.equals(this.page) && townstead$catalogPanel != null
                && townstead$catalogPanel.scroll(mouseX, mouseY, verticalAmount);
    }

    @Unique
    private boolean townstead$handleNavScroll(double mouseX, double mouseY, double verticalAmount) {
        if (townstead$navButtons.isEmpty())
            return false;
        int left = this.width / 2 - 180;
        int top = this.height / 2 - 56;
        int right = left + NAV_BUTTON_WIDTH;
        int bottom = top + (NAV_VISIBLE_ROWS * NAV_BUTTON_STEP);
        if (!(mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom))
            return false;
        int overflowRows = Math.max(0, townstead$navButtons.size() - NAV_VISIBLE_ROWS);
        if (overflowRows <= 0)
            return false;
        int maxScroll = overflowRows * NAV_BUTTON_STEP;
        if (verticalAmount < 0) {
            townstead$navScrollPx = Math.max(-maxScroll, townstead$navScrollPx - NAV_BUTTON_STEP);
        } else if (verticalAmount > 0) {
            townstead$navScrollPx = Math.min(0, townstead$navScrollPx + NAV_BUTTON_STEP);
        }
        townstead$applyNavScroll();
        return true;
    }

    //? if neoforge {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_6375_", remap = false, at = @At("HEAD"), cancellable = true)
    *///?}
    private void townstead$catalogMouseClicked(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (TOWNSTEAD_CATALOG_PAGE.equals(this.page) && townstead$catalogPanel != null
                && townstead$catalogPanel.mouseClicked(mouseX, mouseY, button)) cir.setReturnValue(true);
    }

    //? if neoforge {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_7933_", remap = false, at = @At("HEAD"), cancellable = true)
    *///?}
    private void townstead$catalogKeyScroll(int keyCode, int scanCode, int modifiers,
            CallbackInfoReturnable<Boolean> cir) {
        if (TOWNSTEAD_CATALOG_PAGE.equals(this.page) && townstead$catalogPanel != null
                && townstead$catalogPanel.keyPressed(keyCode, scanCode, modifiers)) cir.setReturnValue(true);
    }

    /**
     * Adds the Community Spirit entry button to MCA's "Rank" (renamed
     * "Status" in our lang override) page. The status page is the natural
     * home for village-scoped identity info — population, taxes, reputation
     * already live here, so spirit fits.
     */
    @Unique
    private void townstead$addSpiritButtonOnStatusPage() {
        int bx = this.width / 2 + 180 - 64 - 16;
        // Top of MCA's button column — same slot 0 as the side-nav "Map" tab,
        // so the Spirit entry reads as the page's primary action.
        int by = this.height / 2 - 56;
        addRenderableWidget(townstead$tooltipButton(
                bx, by, 96, 20,
                Component.translatable("gui.blueprint.spirit"),
                Component.translatable("gui.blueprint.spirit.tooltip"),
                b -> setPage(TOWNSTEAD_SPIRIT_PAGE)));
    }

    /**
     * Vanilla stand-in for MCA's TooltipButtonWidget, whose constructor takes
     * MutableComponent on pre-floor-update builds and Component after — linking
     * against either signature throws NoSuchMethodError on the other. MCA's
     * widget is just a vanilla Button plus setTooltip, so this renders the same.
     */
    @Unique
    private static Button townstead$tooltipButton(int x, int y, int width, int height,
                                                  Component message, Component tooltip,
                                                  Button.OnPress onPress) {
        Button button = Button.builder(message, onPress).bounds(x, y, width, height).build();
        if (!tooltip.getString().isEmpty()) {
            button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tooltip));
        }
        return button;
    }

    @Unique
    private void townstead$setNavVisible(boolean visible) {
        for (Button b : townstead$navButtons) {
            b.visible = visible;
            b.active = visible;
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
        return BuildingIconResolver.nodeItemForType(buildingTypeName);
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
    private void townstead$applyNavScroll() {
        for (Button b : townstead$navButtons) {
            Integer baseY = townstead$navBaseY.get(b);
            if (baseY == null)
                continue;
            b.setY(baseY + townstead$navScrollPx);
        }
    }

    // =====================================================================
    // Villagers page controls (entry points for Shifts and Professions)
    // =====================================================================

    @Unique
    private void townstead$addVillagersPageControls() {
        // Position mirroring the nav column: same Y as "Map" button, right side,
        // matching the padding/width of the map page's right-side buttons.
        int x = this.width / 2 + 100;
        int y = this.height / 2 - 56;
        addRenderableWidget(townstead$tooltipButton(
                x, y, 96, 20,
                Component.translatable("gui.blueprint.shifts"),
                Component.empty(),
                b -> net.minecraft.client.Minecraft.getInstance().setScreen(new ShiftManagerScreen((Screen) (Object) this))));
        addRenderableWidget(townstead$tooltipButton(
                x, y + 22, 96, 20,
                Component.translatable("gui.blueprint.professions"),
                Component.empty(),
                b -> setPage(TOWNSTEAD_PROFESSION_PAGE)));
        addRenderableWidget(townstead$tooltipButton(
                x, y + 44, 96, 20,
                Component.translatable("gui.blueprint.wardrobe"),
                Component.empty(),
                b -> net.minecraft.client.Minecraft.getInstance().setScreen(
                        new com.aetherianartificer.townstead.client.gui.wardrobe.WardrobeScreen((Screen) (Object) this))));
    }

    // =====================================================================
    // Profession Manager page
    // =====================================================================

    @Unique
    private void townstead$initProfessionPage() {
        townstead$profPage = 0;
        townstead$profSelectedVillager = null;
        townstead$profScroll = 0;
        townstead$refreshProfVillagers();

        int leftX = this.width / 2 - 80;
        int topY = this.height / 2 - 74;

        // Back button
        addRenderableWidget(new ButtonWidget(
                leftX, topY, 40, 14,
                Component.translatable("townstead.gui.back"),
                b -> setPage("villagers")));

        // Villager list pagination (right-aligned with profession panel)
        int profRight = this.width / 2 + 176;
        addRenderableWidget(new ButtonWidget(
                profRight - 20, topY, 20, 14,
                Component.literal(">"),
                b -> townstead$profPageDelta(1)));
        addRenderableWidget(new ButtonWidget(
                profRight - 42, topY, 20, 14,
                Component.literal("<"),
                b -> townstead$profPageDelta(-1)));

        // Profession list scroll buttons — bottom-aligned with Refresh button
        int profPanelX = this.width / 2 + 48;
        int refreshBottom = this.height / 2 - 56 + 22 * 5 + 20;
        int scrollBtnY = refreshBottom - 14;
        addRenderableWidget(new ButtonWidget(
                profPanelX, scrollBtnY, 20, 14,
                Component.literal("\u25B2"),
                b -> townstead$profScroll = Math.max(0, townstead$profScroll - 1)));
        addRenderableWidget(new ButtonWidget(
                profRight - 20, scrollBtnY, 20, 14,
                Component.literal("\u25BC"),
                b -> townstead$profScroll++));

        // Query available professions from server
        //? if neoforge {
        PacketDistributor.sendToServer(new ProfessionQueryPayload());
        //?} else if forge {
        /*TownsteadNetwork.sendToServer(new ProfessionQueryPayload());
        *///?}
    }

    @Unique
    private void townstead$populateProfVillagers() {
        townstead$refreshProfVillagers();
    }

    @Unique
    private void townstead$refreshProfVillagers() {
        townstead$profVillagerUuids = new ArrayList<>();
        townstead$profVillagerNames.clear();
        townstead$profVillagerEntityIds.clear();

        for (VillageResidentClientStore.Resident resident : VillageResidentClientStore.getResidents()) {
            UUID uuid = resident.villagerUuid();
            townstead$profVillagerUuids.add(uuid);
            townstead$profVillagerNames.put(uuid, resident.name());
        }

        townstead$profVillagerUuids.sort(Comparator.comparing(
                uuid -> townstead$profVillagerNames.getOrDefault(uuid, uuid.toString())));
        int totalPages = Math.max(1, (int) Math.ceil(townstead$profVillagerUuids.size() / (double) PROF_ROWS_PER_PAGE));
        townstead$profPage = Math.max(0, Math.min(townstead$profPage, totalPages - 1));
        if (townstead$profSelectedVillager != null && VillageResidentClientStore.get(townstead$profSelectedVillager) == null) {
            townstead$profSelectedVillager = null;
        }
    }

    @Unique
    private void townstead$profPageDelta(int delta) {
        int totalPages = Math.max(1, (int) Math.ceil(townstead$profVillagerUuids.size() / (double) PROF_ROWS_PER_PAGE));
        townstead$profPage = Math.max(0, Math.min(townstead$profPage + delta, totalPages - 1));
    }

    @Unique
    private String townstead$profDisplayName(String professionId) {
        if ("minecraft:none".equals(professionId)) {
            return Component.translatable("townstead.profession.none").getString();
        }
        //? if >=1.21 {
        ResourceLocation id = ResourceLocation.parse(professionId);
        //?} else {
        /*ResourceLocation id = new ResourceLocation(professionId);
        *///?}
        return com.aetherianartificer.townstead.profession.ProfessionDisplayNames
                .component(id).getString();
    }

    @Unique
    private String townstead$currentProfessionId(VillagerEntityMCA mca) {
        VillagerProfession prof = mca.getVillagerData().getProfession();
        ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(prof);
        return key != null ? key.toString() : "minecraft:none";
    }

    @Unique
    private String townstead$currentProfessionId(UUID villagerUuid) {
        VillageResidentClientStore.Resident resident = VillageResidentClientStore.get(villagerUuid);
        return resident != null ? resident.professionId() : "minecraft:none";
    }

    //? if neoforge {
    @Inject(method = "render", at = @At("TAIL"))
    //?} else {
    /*@Inject(method = "m_88315_", remap = false, at = @At("TAIL"))
    *///?}
    private void townstead$renderProfessionPage(GuiGraphics context, int mouseX, int mouseY, float partialTicks,
            CallbackInfo ci) {
        if (!TOWNSTEAD_PROFESSION_PAGE.equals(this.page))
            return;
        townstead$refreshProfVillagers();

        int leftX = this.width / 2 - 80;
        int topY = this.height / 2 - 74;
        int listRight = this.width / 2 + 40;
        int profPanelX = listRight + 8;
        int profPanelRight = this.width / 2 + 176;

        // Title (centered between back button and pagination)
        int titleCenterX = (leftX + 42 + profPanelRight - 44) / 2;
        context.drawCenteredString(this.font, Component.translatable("townstead.profession.title"),
                titleCenterX, topY + 3, 0xFFFFFF);

        // Page indicator (to the left of < > buttons)
        int totalPages = Math.max(1, (int) Math.ceil(townstead$profVillagerUuids.size() / (double) PROF_ROWS_PER_PAGE));
        String pageText = String.format("%d/%d", townstead$profPage + 1, totalPages);
        context.drawString(this.font, Component.literal(pageText),
                profPanelRight - 44 - this.font.width(pageText) - 4, topY + 4, 0xA0A0A0, false);

        // Villager list
        int listY = this.height / 2 - 48;
        int rowH = 14;
        int startIdx = townstead$profPage * PROF_ROWS_PER_PAGE;
        int endIdx = Math.min(startIdx + PROF_ROWS_PER_PAGE, townstead$profVillagerUuids.size());

        for (int row = 0; row < endIdx - startIdx; row++) {
            UUID uuid = townstead$profVillagerUuids.get(startIdx + row);
            String name = townstead$profVillagerNames.getOrDefault(uuid, "???");
            int rowY = listY + row * (rowH + 1);

            // Highlight selected
            boolean selected = uuid.equals(townstead$profSelectedVillager);
            if (selected) {
                context.fill(leftX - 1, rowY - 1, listRight + 1, rowY + rowH, 0x40FFFFFF);
            }

            // Hover highlight
            if (mouseX >= leftX && mouseX < listRight && mouseY >= rowY && mouseY < rowY + rowH) {
                context.fill(leftX, rowY, listRight, rowY + rowH, 0x20FFFFFF);
            }

            // Name (left)
            String truncName = name;
            int maxNameW = 54;
            while (this.font.width(truncName) > maxNameW && truncName.length() > 1) {
                truncName = truncName.substring(0, truncName.length() - 1);
            }
            if (!truncName.equals(name)) truncName += "..";
            context.drawString(this.font, truncName,
                    leftX + 2, rowY + (rowH - this.font.lineHeight) / 2 + 1, 0xFFFFFF, false);

            // Current profession (right, smaller)
            String profText = townstead$profDisplayName(townstead$currentProfessionId(uuid));
            context.pose().pushPose();
            int profTextX = leftX + 58;
            context.pose().translate(profTextX, rowY + (rowH - this.font.lineHeight * 0.7f) / 2 + 1, 0);
            context.pose().scale(0.7f, 0.7f, 1.0f);
            context.drawString(this.font, profText, 0, 0, 0xA0A0A0, false);
            context.pose().popPose();
        }

        // Right panel: available professions for selected villager
        if (townstead$profSelectedVillager != null) {
            List<String> available = ProfessionClientStore.getProfessions();

            // Get the selected villager's current profession
            String currentProfId = townstead$currentProfessionId(townstead$profSelectedVillager);

            // Draw profession buttons with scroll support
            int btnH = 14;
            int btnW = profPanelRight - profPanelX;
            int panelBottom = this.height / 2 - 56 + 22 * 5 + 20 - 16;
            int maxVisible = (panelBottom - listY) / (btnH + 1);
            int maxScroll = Math.max(0, available.size() - maxVisible);
            townstead$profScroll = Math.max(0, Math.min(townstead$profScroll, maxScroll));

            context.enableScissor(profPanelX, listY, profPanelRight, panelBottom);
            for (int i = 0; i < available.size(); i++) {
                String profId = available.get(i);
                int by = listY + (i - townstead$profScroll) * (btnH + 1);
                if (by + btnH < listY || by > panelBottom) continue;

                boolean isCurrent = profId.equals(currentProfId);
                boolean isFull = ProfessionClientStore.isFull(i) && !isCurrent;
                int maxS = ProfessionClientStore.getMax(i);
                int usedS = ProfessionClientStore.getUsed(i);

                // Background: green=current, red=full, gray=available
                int bgColor;
                if (isCurrent) {
                    bgColor = 0xFF3A6A3A;
                } else if (isFull) {
                    bgColor = 0xFF5A2A2A;
                } else {
                    bgColor = 0xFF333333;
                }
                if (!isFull && mouseX >= profPanelX && mouseX < profPanelRight && mouseY >= by && mouseY < by + btnH) {
                    bgColor = isCurrent ? 0xFF4A8A4A : 0xFF555555;
                }
                context.fill(profPanelX, by, profPanelRight, by + btnH, bgColor);

                // Label with slot count for limited professions
                String displayName = townstead$profDisplayName(profId);
                if (maxS >= 0) {
                    displayName += " (" + usedS + "/" + maxS + ")";
                }
                String truncDisplay = displayName;
                while (this.font.width(truncDisplay) > btnW - 4 && truncDisplay.length() > 1) {
                    truncDisplay = truncDisplay.substring(0, truncDisplay.length() - 1);
                }
                if (!truncDisplay.equals(displayName)) truncDisplay += "..";
                int textColor = isFull ? 0xFF6666 : (isCurrent ? 0xFFFFFF : 0xC0C0C0);
                context.drawString(this.font, truncDisplay,
                        profPanelX + 2, by + (btnH - this.font.lineHeight) / 2 + 1,
                        textColor, false);
            }
            context.disableScissor();
        } else {
            // No villager selected - show hint
            context.drawCenteredString(this.font, Component.translatable("townstead.profession.select"),
                    (profPanelX + profPanelRight) / 2, this.height / 2, 0x808080);
        }
    }

    //? if neoforge {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_6375_", remap = false, at = @At("HEAD"), cancellable = true)
    *///?}
    private void townstead$professionMouseClicked(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (!TOWNSTEAD_PROFESSION_PAGE.equals(this.page) || button != 0)
            return;

        int leftX = this.width / 2 - 80;
        int listRight = this.width / 2 + 40;
        int profPanelX = listRight + 8;
        int profPanelRight = this.width / 2 + 176;
        int listY = this.height / 2 - 48;
        int rowH = 14;

        // Check villager list clicks
        int startIdx = townstead$profPage * PROF_ROWS_PER_PAGE;
        int endIdx = Math.min(startIdx + PROF_ROWS_PER_PAGE, townstead$profVillagerUuids.size());

        if (mouseX >= leftX && mouseX < listRight) {
            for (int row = 0; row < endIdx - startIdx; row++) {
                int rowY = listY + row * (rowH + 1);
                if (mouseY >= rowY && mouseY < rowY + rowH) {
                    UUID uuid = townstead$profVillagerUuids.get(startIdx + row);
                    townstead$profSelectedVillager = uuid.equals(townstead$profSelectedVillager) ? null : uuid;
                    townstead$profScroll = 0;
                    cir.setReturnValue(true);
                    cir.cancel();
                    return;
                }
            }
        }

        // Check profession button clicks (only above the scroll buttons)
        int profPanelBottom = this.height / 2 - 56 + 22 * 5 + 20 - 16;
        if (townstead$profSelectedVillager != null && mouseX >= profPanelX && mouseX < profPanelRight
                && mouseY < profPanelBottom) {
            List<String> available = ProfessionClientStore.getProfessions();
            int btnH = 14;
            for (int i = 0; i < available.size(); i++) {
                int by = listY + (i - townstead$profScroll) * (btnH + 1);
                if (by + btnH < listY || by > this.height / 2 - 56 + 22 * 5 + 20 - 16) continue;
                if (mouseY >= by && mouseY < by + btnH) {
                    String profId = available.get(i);
                    //? if neoforge {
                    PacketDistributor.sendToServer(new ProfessionSetPayload(townstead$profSelectedVillager, profId));
                    //?} else if forge {
                    /*TownsteadNetwork.sendToServer(new ProfessionSetPayload(townstead$profSelectedVillager, profId));
                    *///?}
                    cir.setReturnValue(true);
                    cir.cancel();
                    return;
                }
            }
        }
    }

    @Unique
    private boolean townstead$handleProfessionScroll(double mouseX, double mouseY, double verticalAmount) {
        if (!TOWNSTEAD_PROFESSION_PAGE.equals(this.page))
            return false;
        int leftX = this.width / 2 - 80;
        int listRight = this.width / 2 + 40;
        int profPanelX = listRight + 8;
        int profPanelRight = this.width / 2 + 176;
        int listY = this.height / 2 - 48;
        int listBottom = listY + PROF_ROWS_PER_PAGE * 15;

        if (mouseX >= leftX && mouseX <= listRight && mouseY >= listY && mouseY <= listBottom) {
            if (verticalAmount < 0) {
                townstead$profPageDelta(1);
            } else if (verticalAmount > 0) {
                townstead$profPageDelta(-1);
            } else {
                return false;
            }
            return true;
        }

        if (mouseX >= profPanelX && mouseX <= profPanelRight && mouseY >= listY && mouseY <= this.height / 2 + 76) {
            if (verticalAmount < 0) {
                townstead$profScroll++;
            } else if (verticalAmount > 0) {
                townstead$profScroll = Math.max(0, townstead$profScroll - 1);
            } else {
                return false;
            }
            return true;
        }
        return false;
    }

    // =====================================================================
    // Community Spirit page
    // =====================================================================

    @Unique
    private int townstead$spiritWindowW() {
        return Math.min(this.width - 40, 420);
    }

    @Unique
    private int townstead$spiritWindowH() {
        return Math.min(this.height - 60, 280);
    }

    @Unique
    private int townstead$spiritWindowX() {
        return (this.width - townstead$spiritWindowW()) / 2;
    }

    @Unique
    private int townstead$spiritWindowY() {
        return (this.height - townstead$spiritWindowH()) / 2;
    }

    @Unique
    private void townstead$initSpiritPage() {
        // Ask MCA for a fresh snapshot only when the client does not already
        // have spirit state for this village. Building changes still refresh
        // via MCA's normal village requests after report actions.
        net.conczin.mca.server.world.data.Village currentVillage =
                ((BlueprintScreenAccessor) (Object) this).townstead$getVillage();
        boolean hasCache = currentVillage != null
                && com.aetherianartificer.townstead.spirit.ClientVillageSpiritStore
                        .get(currentVillage.getId()).isPresent();
        if (currentVillage == null || !hasCache) {
            //? if neoforge {
            PacketDistributor.sendToServer(new com.aetherianartificer.townstead.spirit.VillageSpiritQueryPayload());
            //?} else {
            /*TownsteadNetwork.sendToServer(new com.aetherianartificer.townstead.spirit.VillageSpiritQueryPayload());
            *///?}
        }
        // Restore per-village scroll position so re-opening the page lands
        // where the player left off.
        int saved = townstead$spiritScrollByVillage.getOrDefault(
                townstead$currentSpiritVillageId(), 0);
        townstead$spiritScrollTarget = saved;
        townstead$spiritScrollCurrent = saved;
        // Entrance stinger — a soft page-turn when the Spirit dashboard opens.
        // Uses the UI sound channel so it respects the player's master volume.
        try {
            net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.0f));
        } catch (Throwable ignored) {}
        // Back button sits inside the panel's title strip, top-left corner.
        int windowX = townstead$spiritWindowX();
        int windowY = townstead$spiritWindowY();
        int windowW = townstead$spiritWindowW();
        addRenderableWidget(new ButtonWidget(
                windowX + 4, windowY + 3, 40, 14,
                Component.translatable("townstead.gui.back"),
                b -> setPage("map")));
        // Sort + filter controls below the header divider. Kept as widgets so
        // MC handles their click/hover chrome; visibility is toggled each
        // frame from the render path so they disappear in radar mode.
        int controlsY = windowY + 22 + 26 + 4; // mirrors render's contentTop math
        int controlsLeft = windowX + 10;
        townstead$spiritSortBtn = new ButtonWidget(
                controlsLeft, controlsY, 60, 12,
                Component.translatable(townstead$spiritSortLabelKey()),
                b -> {
                    townstead$spiritSortMode = (townstead$spiritSortMode + 1) % 3;
                    b.setMessage(Component.translatable(townstead$spiritSortLabelKey()));
                });
        addRenderableWidget(townstead$spiritSortBtn);

        townstead$spiritTop3Btn = new ButtonWidget(
                controlsLeft + 64, controlsY, 52, 12,
                townstead$filterLabel("townstead.spirit.filter.top3", townstead$spiritFilterTop3),
                b -> {
                    townstead$spiritFilterTop3 = !townstead$spiritFilterTop3;
                    b.setMessage(townstead$filterLabel("townstead.spirit.filter.top3",
                            townstead$spiritFilterTop3));
                });
        addRenderableWidget(townstead$spiritTop3Btn);

        townstead$spiritThresholdBtn = new ButtonWidget(
                controlsLeft + 120, controlsY, 52, 12,
                townstead$filterLabel("townstead.spirit.filter.threshold", townstead$spiritFilterThreshold),
                b -> {
                    townstead$spiritFilterThreshold = !townstead$spiritFilterThreshold;
                    b.setMessage(townstead$filterLabel("townstead.spirit.filter.threshold",
                            townstead$spiritFilterThreshold));
                });
        addRenderableWidget(townstead$spiritThresholdBtn);
    }

    @Unique
    private Component townstead$filterLabel(String baseKey, boolean on) {
        // \u25CF = filled circle, \u25CB = empty circle — reads as a checkbox
        // toggle while keeping the button fully clickable (setting active=false
        // would disable clicks).
        return Component.literal(on ? "\u25CF " : "\u25CB ").append(Component.translatable(baseKey));
    }

    // --------------- Per-village state + cache helpers ---------------
    @Unique
    private int townstead$currentSpiritVillageId() {
        net.conczin.mca.server.world.data.Village v =
                ((BlueprintScreenAccessor) (Object) this).townstead$getVillage();
        return v != null ? v.getId() : 0;
    }

    @Unique
    private java.util.Set<String> townstead$collapsedSet() {
        return townstead$spiritCollapsedByVillage.computeIfAbsent(
                townstead$currentSpiritVillageId(), k -> new java.util.HashSet<>());
    }

    @Unique
    private void townstead$setSpiritScrollTarget(int target) {
        townstead$spiritScrollTarget = Math.max(0, target);
        townstead$spiritScrollByVillage.put(
                townstead$currentSpiritVillageId(), townstead$spiritScrollTarget);
    }

    @Unique
    private java.util.Map<String, java.util.List<ContributorEntry>> townstead$contribsFor(
            net.conczin.mca.server.world.data.Village village,
            com.aetherianartificer.townstead.spirit.VillageSpiritSyncPayload snapshot) {
        int vid = village.getId();
        // Prefer the server-built contributor map riding on the payload — no
        // render-thread iteration over the building list, no classpath JSON
        // scans for spirit contributions.
        java.util.Map<String, java.util.List<com.aetherianartificer.townstead.spirit.ContributorRow>> fromServer =
                snapshot.contributors();
        if (fromServer != null && !fromServer.isEmpty()) {
            ContribCacheEntry cached = townstead$spiritContribCache.get(vid);
            if (cached != null && cached.buildingCount() == snapshot.total()
                    && cached.payloadTotal() == snapshot.total()
                    && cached.serverSourced()) {
                return cached.data();
            }
            java.util.Map<String, java.util.List<ContributorEntry>> projected = new java.util.HashMap<>();
            for (java.util.Map.Entry<String, java.util.List<com.aetherianartificer.townstead.spirit.ContributorRow>>
                    e : fromServer.entrySet()) {
                java.util.List<ContributorEntry> rows = new java.util.ArrayList<>(e.getValue().size());
                for (com.aetherianartificer.townstead.spirit.ContributorRow r : e.getValue()) {
                    rows.add(new ContributorEntry(r.buildingType(), r.count(), r.points()));
                }
                projected.put(e.getKey(), rows);
            }
            townstead$spiritContribCache.put(vid,
                    new ContribCacheEntry(snapshot.total(), snapshot.total(), projected, true));
            return projected;
        }
        // Fallback: server didn't ship contributors (cache built before the
        // payload was extended). Compute on the client and cache locally.
        int bct = com.aetherianartificer.townstead.compat.mca.McaBuildings.allById(village).size();
        int total = snapshot.total();
        ContribCacheEntry cached = townstead$spiritContribCache.get(vid);
        if (cached != null && cached.buildingCount() == bct && cached.payloadTotal() == total
                && !cached.serverSourced()) {
            return cached.data();
        }
        java.util.Map<String, java.util.List<ContributorEntry>> fresh =
                townstead$aggregateContributors(village);
        townstead$spiritContribCache.put(vid, new ContribCacheEntry(bct, total, fresh, false));
        return fresh;
    }

    // --------------- A11y config accessors + row scaling helpers ---------------
    @Unique
    private boolean townstead$a11yColorblind() {
        try { return com.aetherianartificer.townstead.TownsteadConfig.SPIRIT_COLORBLIND_PATTERNS.get(); }
        catch (Throwable t) { return false; }
    }
    @Unique
    private boolean townstead$a11yNarration() {
        try { return com.aetherianartificer.townstead.TownsteadConfig.SPIRIT_NARRATION.get(); }
        catch (Throwable t) { return false; }
    }
    @Unique
    private boolean townstead$a11yLargerHit() {
        try { return com.aetherianartificer.townstead.TownsteadConfig.SPIRIT_LARGER_HIT_TARGETS.get(); }
        catch (Throwable t) { return false; }
    }
    @Unique
    private boolean townstead$a11yHighContrast() {
        try { return com.aetherianartificer.townstead.TownsteadConfig.SPIRIT_HIGH_CONTRAST.get(); }
        catch (Throwable t) { return false; }
    }
    @Unique
    private double townstead$a11yFontScale() {
        try { return com.aetherianartificer.townstead.TownsteadConfig.SPIRIT_FONT_SCALE.get(); }
        catch (Throwable t) { return 1.0; }
    }

    /**
     * Composite "row scale" used to size chrome (heights, bar thickness,
     * contributor line height). Pulls up with either larger-hit-targets or
     * font-scale so the layout keeps its proportions.
     */
    @Unique
    private double townstead$rowScale() {
        double fs = townstead$a11yFontScale();
        double hit = townstead$a11yLargerHit() ? 1.35 : 1.0;
        return Math.max(fs, hit);
    }

    @Unique
    private void townstead$drawScaledString(GuiGraphics ctx, Component text, int x, int y,
                                            int color, double scale) {
        if (Math.abs(scale - 1.0) < 0.001) {
            ctx.drawString(this.font, text, x, y, color, false);
            return;
        }
        ctx.pose().pushPose();
        ctx.pose().translate(x, y, 0);
        ctx.pose().scale((float) scale, (float) scale, 1f);
        ctx.drawString(this.font, text, 0, 0, color, false);
        ctx.pose().popPose();
    }

    @Unique
    private void townstead$drawScaledString(GuiGraphics ctx, String text, int x, int y,
                                            int color, double scale) {
        if (Math.abs(scale - 1.0) < 0.001) {
            ctx.drawString(this.font, text, x, y, color, false);
            return;
        }
        ctx.pose().pushPose();
        ctx.pose().translate(x, y, 0);
        ctx.pose().scale((float) scale, (float) scale, 1f);
        ctx.drawString(this.font, text, 0, 0, color, false);
        ctx.pose().popPose();
    }

    @Unique
    private void townstead$narrateHover(String hoveredSpiritId,
            com.aetherianartificer.townstead.spirit.VillageSpiritSyncPayload snapshot,
            java.util.Map<String, java.util.List<ContributorEntry>> contribs) {
        if (!townstead$a11yNarration()) return;
        if (java.util.Objects.equals(hoveredSpiritId, townstead$lastNarratedSpirit)) return;
        // Debounce — rapid flyover across rows shouldn't fire narration for
        // every one. 200ms floor lets the user "settle" on a row first.
        long nowMs = System.currentTimeMillis();
        if (nowMs - townstead$lastNarrationMs < 200L) return;
        townstead$lastNarrationMs = nowMs;
        townstead$lastNarratedSpirit = hoveredSpiritId;
        if (hoveredSpiritId == null) return;
        var opt = com.aetherianartificer.townstead.spirit.SpiritRegistry.get(hoveredSpiritId);
        if (opt.isEmpty()) return;
        int pts = snapshot.perSpirit().getOrDefault(hoveredSpiritId, 0);
        int total = snapshot.total();
        int sharePct = total > 0 ? (int) Math.round(100.0 * pts / total) : 0;
        int tier = com.aetherianartificer.townstead.spirit.VillageSpiritAggregator.tierForSpirit(pts);
        int[] thresholds = com.aetherianartificer.townstead.spirit.VillageSpiritAggregator.tierThresholds();
        String displayName = townstead$translatedText(opt.get().displayKey());
        StringBuilder sb = new StringBuilder();
        sb.append(displayName).append(": ").append(pts);
        if (tier < thresholds.length) {
            sb.append(" of ").append(thresholds[tier]).append(" points");
        } else {
            sb.append(" points, max tier");
        }
        sb.append(", ").append(sharePct).append(" percent share");
        if (tier >= 1) {
            String tierKey = "townstead.spirit.tier." + hoveredSpiritId + "." + tier;
            sb.append(", ").append(townstead$translatedText(tierKey)).append(" tier");
        }
        int contribCount = contribs.getOrDefault(hoveredSpiritId, java.util.List.of()).size();
        if (contribCount > 0) {
            sb.append(", ").append(contribCount).append(" contributing buildings");
        }
        try {
            com.mojang.text2speech.Narrator.getNarrator().say(sb.toString(), true);
        } catch (Throwable ignored) {}
    }

    /**
     * Per-spirit colorblind hatching pattern. Each spirit gets a unique
     * pattern so the bars can be told apart without relying on color. Pattern
     * pixels are dark overlaid on the filled region.
     */
    @Unique
    private void townstead$applyHatching(GuiGraphics context, String spiritId,
                                         int x1, int y1, int x2, int y2) {
        if (!townstead$a11yColorblind()) return;
        int idx = com.aetherianartificer.townstead.spirit.SpiritRegistry.indexOf(spiritId);
        if (idx < 0) return;
        int darkened = 0xB0000000;
        for (int y = y1; y < y2; y++) {
            for (int x = x1; x < x2; x++) {
                int lx = x - x1;
                int ly = y - y1;
                boolean mark = switch (idx) {
                    case 0 -> ((lx + ly) % 4) == 0;                     // diagonal stripes
                    case 1 -> (lx % 3 == 0) && (ly % 2 == 0);            // sparse dots
                    case 2 -> (lx % 4 == 0) || (ly % 4 == 0);            // grid
                    case 3 -> (ly == 0) || (ly == (y2 - y1) / 2);        // horizontal bands
                    case 4 -> (lx % 3 == 0);                             // vertical lines
                    case 5 -> ((lx + ly) % 3 == 0) && (lx + ly) % 6 != 0;// dashed diagonal
                    default -> ((lx ^ ly) & 3) == 0;                     // checker blend
                };
                if (mark) {
                    context.fill(x, y, x + 1, y + 1, darkened);
                }
            }
        }
    }

    /**
     * Spirit-themed ambient particles drifting inside the panel. A handful
     * of animated sprites whose path + color matches the dominant spirit:
     * bubbles for Nautical, sparks for Industrious, motes for Scholar, etc.
     */
    @Unique
    private void townstead$drawSpiritParticles(GuiGraphics context, int x1, int y1, int x2, int y2,
                                               String spiritId, long nowMs) {
        if (spiritId == null) return;
        int idx = com.aetherianartificer.townstead.spirit.SpiritRegistry.indexOf(spiritId);
        if (idx < 0) return;
        int w = x2 - x1;
        int h = y2 - y1;
        if (w < 8 || h < 8) return;
        int count = 14;
        double t = nowMs / 1000.0;
        for (int i = 0; i < count; i++) {
            double phase = i * 0.7731;
            double cycle = 6.0 + (i % 4) * 1.2; // seconds per full vertical sweep
            double progress = ((t / cycle) + phase) % 1.0;
            if (progress < 0) progress += 1.0;
            // Base horizontal position — deterministic per index, slight wobble.
            double baseX = (i * 29 + 13) % Math.max(1, w - 4);
            double wobble = Math.sin(t * 1.3 + phase * 3) * 2.5;
            int px = x1 + 2 + (int) Math.round(baseX + wobble);
            int py;
            switch (idx) {
                case 0 -> { // Nautical — bubbles rising
                    py = y2 - 2 - (int) Math.round(progress * (h - 6));
                    int alpha = (int) (0x40 * (1.0 - progress)) & 0xFF;
                    int col = (alpha << 24) | 0xCCE8FF;
                    context.fill(px, py, px + 2, py + 2, col);
                    if ((i & 1) == 0) context.fill(px + 1, py - 1, px + 2, py, col);
                }
                case 1 -> { // Pastoral — falling flecks
                    py = y1 + 2 + (int) Math.round(progress * (h - 6));
                    int alpha = (int) (0x40 * (1.0 - Math.abs(progress - 0.5) * 2)) & 0xFF;
                    int col = (alpha << 24) | 0x9FC870;
                    context.fill(px, py, px + 2, py + 1, col);
                }
                case 2 -> { // Martial — flickering embers
                    double life = (t * 3 + phase) % 1.0;
                    py = y1 + 2 + ((int) ((i * 37 + 11) % Math.max(1, h - 6)));
                    int alpha = life < 0.3 ? (int) (0x80 * (life / 0.3)) : (int) (0x80 * (1.0 - (life - 0.3) / 0.7));
                    int col = ((alpha & 0xFF) << 24) | 0xFF7020;
                    context.fill(px, py, px + 2, py + 2, col);
                }
                case 3 -> { // Scholar — slow floating motes
                    py = y2 - 2 - (int) Math.round(progress * (h - 6));
                    int alpha = (int) (0x55 * Math.sin(progress * Math.PI)) & 0xFF;
                    int col = (alpha << 24) | 0xC9B2FF;
                    context.fill(px, py, px + 1, py + 1, col);
                    context.fill(px + 1, py, px + 2, py + 1, (col & 0xFFFFFF) | ((alpha / 2) << 24));
                }
                case 4 -> { // Industrious — upward sparks
                    py = y2 - 2 - (int) Math.round(progress * (h - 6));
                    int alpha = (int) (0x90 * (1.0 - progress)) & 0xFF;
                    int hot = progress < 0.4 ? 0xFFC060 : 0xFF8030;
                    int col = (alpha << 24) | hot;
                    context.fill(px, py, px + 1, py + 1, col);
                    if (progress < 0.3) context.fill(px, py + 1, px + 1, py + 2, (col & 0xFFFFFF) | ((alpha / 2) << 24));
                }
                case 5 -> { // Commercial — twinkling glints
                    double life = (t * 1.5 + phase) % 1.0;
                    py = y1 + 3 + (int) ((i * 53 + 19) % Math.max(1, h - 8));
                    int alpha = (int) (0xA0 * Math.pow(Math.sin(life * Math.PI), 6)) & 0xFF;
                    if (alpha > 6) {
                        int col = (alpha << 24) | 0xFFE89A;
                        // Tiny cross glint
                        context.fill(px - 1, py, px + 2, py + 1, col);
                        context.fill(px, py - 1, px + 1, py + 2, col);
                    }
                }
                default -> { // Tourism — drifting petals
                    py = y1 + 2 + (int) Math.round(progress * (h - 6));
                    double drift = Math.sin(t * 0.8 + phase * 2) * 4;
                    int alpha = (int) (0x60 * Math.sin(progress * Math.PI)) & 0xFF;
                    int col = (alpha << 24) | 0xFFB0D0;
                    int fx = px + (int) Math.round(drift);
                    context.fill(fx, py, fx + 2, py + 1, col);
                    context.fill(fx + 1, py + 1, fx + 2, py + 2, col);
                }
            }
        }
    }

    @Unique
    private int townstead$spiritTogglePillW() { return 28; }
    @Unique
    private int townstead$spiritTogglePillH() { return 10; }
    @Unique
    private int townstead$spiritTogglePillX(int windowX, int windowW) {
        return windowX + windowW - townstead$spiritTogglePillW() - 4;
    }
    @Unique
    private int townstead$spiritTogglePillY(int windowY) { return windowY + 4; }

    @Unique
    private Component townstead$translatedComponent(String key) {
        return townstead$translationComponentCache.computeIfAbsent(key, Component::translatable);
    }

    @Unique
    private String townstead$translatedText(String key) {
        return townstead$translationTextCache.computeIfAbsent(key,
                k -> Component.translatable(k).getString());
    }

    @Unique
    private void townstead$drawListIcon(GuiGraphics context, int x, int y, int color) {
        // Three horizontal bars, 6×1 each, 2 px apart. Fits in 6×5.
        context.fill(x,     y,     x + 6, y + 1, color);
        context.fill(x,     y + 2, x + 6, y + 3, color);
        context.fill(x,     y + 4, x + 6, y + 5, color);
    }

    @Unique
    private void townstead$drawRadarIcon(GuiGraphics context, int x, int y, int color) {
        // Small diamond/target, 7×7.
        context.fill(x + 3, y,     x + 4, y + 1, color);
        context.fill(x + 2, y + 1, x + 5, y + 2, color);
        context.fill(x + 1, y + 2, x + 6, y + 3, color);
        context.fill(x,     y + 3, x + 7, y + 4, color);
        context.fill(x + 1, y + 4, x + 6, y + 5, color);
        context.fill(x + 2, y + 5, x + 5, y + 6, color);
        context.fill(x + 3, y + 6, x + 4, y + 7, color);
        // Center dot hole to make it read as a radar reticle, not a solid diamond.
        context.fill(x + 3, y + 3, x + 4, y + 4, 0xFF000000);
    }

    @Unique
    private String townstead$spiritSortLabelKey() {
        return switch (townstead$spiritSortMode) {
            case 1 -> "townstead.spirit.sort.count";
            case 2 -> "townstead.spirit.sort.alpha";
            default -> "townstead.spirit.sort.points";
        };
    }

    //? if neoforge {
    @Inject(method = "render", at = @At("TAIL"))
    //?} else {
    /*@Inject(method = "m_88315_", remap = false, at = @At("TAIL"))
    *///?}
    private void townstead$renderSpiritPage(GuiGraphics context, int mouseX, int mouseY, float partialTicks,
                                            CallbackInfo ci) {
        if (!TOWNSTEAD_SPIRIT_PAGE.equals(this.page)) return;
        BlueprintScreenAccessor accessor = (BlueprintScreenAccessor) (Object) this;
        net.conczin.mca.server.world.data.Village village = accessor.townstead$getVillage();
        if (village == null) return;

        java.util.Optional<com.aetherianartificer.townstead.spirit.VillageSpiritSyncPayload> snapshotOpt =
                com.aetherianartificer.townstead.spirit.ClientVillageSpiritStore.get(village.getId());

        // Full-screen panel — sized to claim most of the blueprint workspace
        // so the page can house a richer per-spirit breakdown with contributor
        // lists beneath each bar. Bounds are shared with the init method so
        // the back button (placed in init) sits inside the rendered panel.
        int windowW = townstead$spiritWindowW();
        int windowH = townstead$spiritWindowH();
        int windowX = townstead$spiritWindowX();
        int windowY = townstead$spiritWindowY();

        // Chrome: outer light border, dark interior, slightly lighter title strip.
        context.fill(windowX, windowY, windowX + windowW, windowY + windowH, 0xFFDEDEDE);
        context.fill(windowX + 1, windowY + 1, windowX + windowW - 1, windowY + windowH - 1, 0xFF2B2F38);
        context.fill(windowX + 3, windowY + 3, windowX + windowW - 3, windowY + 16, 0xFF3A3F47);

        // Title, centered over the title strip.
        Component title = townstead$translatedComponent("townstead.spirit.title");
        context.drawCenteredString(this.font, title, windowX + windowW / 2, windowY + 6, 0xFFFFFF);

        if (snapshotOpt.isEmpty()) {
            Component pending = townstead$translatedComponent("townstead.spirit.subtitle.loading");
            context.drawCenteredString(this.font, pending,
                    windowX + windowW / 2, windowY + windowH / 2 - 4, 0xA0A0A0);
            return;
        }

        com.aetherianartificer.townstead.spirit.VillageSpiritSyncPayload snapshot = snapshotOpt.get();
        com.aetherianartificer.townstead.spirit.SpiritReadout readout = snapshot.toReadout();
        int readoutColor = townstead$spiritAccentColor(readout);

        // Visual spirit — drives chrome theming. Prefer the readout's
        // primary (SINGLE / BLEND), but fall back to the spirit with the
        // most points so Outpost and MIXED villages still get themed chrome.
        String visualSpiritId = readout.primarySpiritId();
        int visualColor = readoutColor;
        if (visualSpiritId == null) {
            String topId = null;
            int topPts = 0;
            for (var entry : snapshot.perSpirit().entrySet()) {
                if (entry.getValue() > topPts) {
                    topPts = entry.getValue();
                    topId = entry.getKey();
                }
            }
            if (topId != null) {
                visualSpiritId = topId;
                var topSpirit = com.aetherianartificer.townstead.spirit.SpiritRegistry.get(topId);
                if (topSpirit.isPresent()) visualColor = topSpirit.get().color();
            }
        }

        if (visualSpiritId != null) {
            // Title strip tint — dominant spirit's color blended into the
            // header bar. Everything below gets per-row tinting in
            // drawSpiritSection, so each spirit row reads with its own color.
            int tintedTitle = townstead$blend(0xFF3A3F47, visualColor, 0.35f);
            context.fill(windowX + 3, windowY + 3, windowX + windowW - 3, windowY + 16, tintedTitle);

            // Ambient particles behind content.
            townstead$drawSpiritParticles(context,
                    windowX + 3, windowY + 16,
                    windowX + windowW - 3, windowY + windowH - 2,
                    visualSpiritId, System.currentTimeMillis());

            // Title text on top of the tint.
            context.drawCenteredString(this.font,
                    townstead$translatedComponent("townstead.spirit.title"),
                    windowX + windowW / 2, windowY + 6, 0xFFFFFF);

            // Soft edge tint — gradient bleeding inward top + bottom.
            int accent = visualColor & 0x00FFFFFF;
            int edgeBand = 8;
            for (int i = 0; i < edgeBand; i++) {
                int alpha = (edgeBand - i) * 4;
                int color = (alpha << 24) | accent;
                context.fill(windowX + 3, windowY + 17 + i,
                        windowX + windowW - 3, windowY + 18 + i, color);
                context.fill(windowX + 3, windowY + windowH - 2 - i,
                        windowX + windowW - 3, windowY + windowH - 1 - i, color);
            }
        }

        // Filter widgets are list-mode only — hide them when the radar view
        // is active so the chart isn't competing with irrelevant chrome.
        boolean listMode = !townstead$spiritRadarMode;
        if (townstead$spiritSortBtn != null) townstead$spiritSortBtn.visible = listMode;
        if (townstead$spiritTop3Btn != null) townstead$spiritTop3Btn.visible = listMode;
        if (townstead$spiritThresholdBtn != null) townstead$spiritThresholdBtn.visible = listMode;

        // View-mode toggle — a pixel-art segmented pill at the top-right of
        // the title strip. Two halves (list / radar), active half tinted with
        // the spirit accent color + light icon, inactive half dark grey with
        // dim icon. Click dispatch lives in the spirit mouseClicked hook.
        int pillX = townstead$spiritTogglePillX(windowX, windowW);
        int pillY = townstead$spiritTogglePillY(windowY);
        int pillW = townstead$spiritTogglePillW();
        int pillH = townstead$spiritTogglePillH();
        int halfW = pillW / 2;
        int activeBg = townstead$blend(0xFF3A3F47, readoutColor, 0.55f);
        int inactiveBg = 0xFF1E2128;
        // Outline + halves.
        context.fill(pillX - 1, pillY - 1, pillX + pillW + 1, pillY + pillH + 1, 0xFF000000);
        context.fill(pillX, pillY, pillX + halfW, pillY + pillH,
                townstead$spiritRadarMode ? inactiveBg : activeBg);
        context.fill(pillX + halfW, pillY, pillX + pillW, pillY + pillH,
                townstead$spiritRadarMode ? activeBg : inactiveBg);
        // Inner divider.
        context.fill(pillX + halfW, pillY, pillX + halfW + 1, pillY + pillH, 0xFF000000);
        // Icons.
        int listIconCol = townstead$spiritRadarMode ? 0xFF70747C : 0xFFFFFFFF;
        int radarIconCol = townstead$spiritRadarMode ? 0xFFFFFFFF : 0xFF70747C;
        // List icon is 6 wide, radar icon 7 wide; center each within its half.
        townstead$drawListIcon(context, pillX + (halfW - 6) / 2, pillY + 2, listIconCol);
        townstead$drawRadarIcon(context, pillX + halfW + (halfW - 7) / 2, pillY + 1, radarIconCol);

        // Header area: big readout line rendered at 2.0 scale (integer scale
        // keeps the pixel font crisp; 1.5 produces sub-pixel blur). Font-scale
        // a11y setting further multiplies this for users who want bigger text.
        // headerTop is set so that top + bottom padding around the readout
        // (between title strip and divider) is balanced at ~8px each.
        int headerTop = windowY + 24;
        Component readoutLine = readout.asComponent();
        float headerScale = (float) (2.0 * townstead$a11yFontScale());
        int centerX = windowX + windowW / 2;
        int headerTextColor = townstead$a11yHighContrast() ? 0xFFFFFFFF : readoutColor;
        context.pose().pushPose();
        context.pose().translate(centerX, headerTop, 0);
        context.pose().scale(headerScale, headerScale, 1.0f);
        context.drawString(this.font, readoutLine, -this.font.width(readoutLine) / 2, 0,
                headerTextColor, false);
        context.pose().popPose();

        // Divider between header and the scrollable spirit list.
        int dividerY = headerTop + 26;
        context.fill(windowX + 8, dividerY, windowX + windowW - 8, dividerY + 1, 0xFF404048);

        // Content bounds. In list mode the control row (sort + filter buttons,
        // 12 px tall + 6 px gap) sits between the divider and the list; radar
        // mode claims the full band below the divider.
        int contentLeft = windowX + 10;
        int contentRight = windowX + windowW - 10;
        int contentBottom = windowY + windowH - 6;
        int contentTop = townstead$spiritRadarMode ? dividerY + 4 : dividerY + 22;

        if (townstead$spiritRadarMode) {
            townstead$renderSpiritRadar(context, snapshot, readoutColor,
                    contentLeft, contentTop, contentRight, contentBottom);
            return;
        }

        // Active spirits — zero-point filtered, then user-applied sort/filter.
        // Contributor aggregation is cached keyed on (villageId, buildingCount,
        // payloadTotal) so we don't walk all buildings every frame.
        java.util.Map<String, java.util.List<ContributorEntry>> preContribs =
                townstead$contribsFor(village, snapshot);
        java.util.List<com.aetherianartificer.townstead.spirit.SpiritRegistry.Spirit> active =
                townstead$buildActiveSpirits(snapshot, preContribs);

        java.util.Map<String, java.util.List<ContributorEntry>> contributorsBySpirit = preContribs;

        // Measure each section's height so we can compute max scroll and
        // render only what's visible in the content band.
        int[] heights = new int[active.size()];
        int totalHeight = 0;
        int[] thresholds = com.aetherianartificer.townstead.spirit.VillageSpiritAggregator.tierThresholds();
        for (int i = 0; i < active.size(); i++) {
            heights[i] = townstead$spiritSectionHeight(active.get(i).id(),
                    contributorsBySpirit.getOrDefault(active.get(i).id(), java.util.List.of()));
            totalHeight += heights[i];
        }
        int viewport = contentBottom - contentTop;
        int maxScroll = Math.max(0, totalHeight - viewport);
        if (townstead$spiritScrollTarget > maxScroll) townstead$setSpiritScrollTarget(maxScroll);
        // Ease current toward target — roughly 10 frames to cover any distance.
        townstead$spiritScrollCurrent += (townstead$spiritScrollTarget
                - townstead$spiritScrollCurrent) * 0.25f;
        if (Math.abs(townstead$spiritScrollTarget - townstead$spiritScrollCurrent) < 0.5f) {
            townstead$spiritScrollCurrent = townstead$spiritScrollTarget;
        }

        // Hover detection for narration — which section is under the mouse
        // right now? Used to speak alt text when the hovered row changes.
        // Cached on mouse-unchanged frames to avoid rescanning every frame.
        String hoveredSpiritId;
        if (mouseX == townstead$lastHoverMouseX && mouseY == townstead$lastHoverMouseY) {
            hoveredSpiritId = townstead$lastHoverSpirit;
        } else {
            hoveredSpiritId = null;
            int hy = contentTop - Math.round(townstead$spiritScrollCurrent);
            for (int i = 0; i < active.size(); i++) {
                int secBot = hy + heights[i];
                if (mouseY >= hy && mouseY < secBot && mouseY >= contentTop && mouseY < contentBottom) {
                    hoveredSpiritId = active.get(i).id();
                    break;
                }
                hy += heights[i];
            }
            townstead$lastHoverMouseX = mouseX;
            townstead$lastHoverMouseY = mouseY;
            townstead$lastHoverSpirit = hoveredSpiritId;
        }
        townstead$narrateHover(hoveredSpiritId, snapshot, contributorsBySpirit);

        // Scissor + draw. Sections fully outside the viewport are skipped
        // entirely; scissor still clips the edges for sections that straddle.
        context.enableScissor(contentLeft - 2, contentTop, contentRight + 2, contentBottom);
        int y = contentTop - Math.round(townstead$spiritScrollCurrent);
        String dominantId = readout.primarySpiritId();
        long animNow = System.currentTimeMillis();
        for (int i = 0; i < active.size(); i++) {
            int sectionTop = y;
            int sectionBot = y + heights[i];
            if (sectionBot < contentTop || sectionTop > contentBottom) {
                y += heights[i];
                continue;
            }
            com.aetherianartificer.townstead.spirit.SpiritRegistry.Spirit s = active.get(i);
            int pts = snapshot.perSpirit().getOrDefault(s.id(), 0);
            java.util.List<ContributorEntry> contributors =
                    contributorsBySpirit.getOrDefault(s.id(), java.util.List.of());
            boolean isDominant = s.id().equals(dominantId);
            boolean isLast = i == active.size() - 1;
            townstead$drawSpiritSection(context, s, pts, snapshot.total(),
                    contributors, thresholds, contentLeft, y, contentRight,
                    isDominant, animNow, mouseX, mouseY, heights[i], isLast);
            y += heights[i];
        }
        context.disableScissor();

        // Scroll indicator on the right edge if content overflows.
        if (totalHeight > viewport) {
            int trackLeft = windowX + windowW - 6;
            context.fill(trackLeft, contentTop, trackLeft + 2, contentBottom, 0xFF1F2230);
            int thumbH = Math.max(12, viewport * viewport / Math.max(1, totalHeight));
            int thumbY = contentTop + (viewport - thumbH)
                    * Math.round(townstead$spiritScrollCurrent) / Math.max(1, maxScroll);
            context.fill(trackLeft, thumbY, trackLeft + 2, thumbY + thumbH, 0xFF8A8A8A);
        }
    }

    @Unique
    private record ContributorEntry(String buildingType, int count, int points) {}

    @Unique
    private record ContribCacheEntry(int buildingCount, int payloadTotal,
            java.util.Map<String, java.util.List<ContributorEntry>> data,
            boolean serverSourced) {}

    @Unique
    private java.util.Map<String, java.util.List<ContributorEntry>> townstead$aggregateContributors(
            net.conczin.mca.server.world.data.Village village) {
        // Map: spirit id -> (building type -> [count, total points])
        java.util.Map<String, java.util.Map<String, int[]>> bySpirit = new java.util.HashMap<>();
        for (net.conczin.mca.server.world.data.Building b : com.aetherianartificer.townstead.compat.mca.McaBuildings.all(village)) {
            if (!b.isComplete()) continue;
            String type = b.getType();
            java.util.Map<String, Integer> contributions =
                    com.aetherianartificer.townstead.spirit.BuildingSpiritIndex.contributionsFor(type);
            if (contributions.isEmpty()) continue;
            for (java.util.Map.Entry<String, Integer> e : contributions.entrySet()) {
                int pts = e.getValue();
                if (pts <= 0) continue;
                if (!com.aetherianartificer.townstead.spirit.SpiritRegistry.contains(e.getKey())) continue;
                bySpirit.computeIfAbsent(e.getKey(), k -> new java.util.HashMap<>())
                        .computeIfAbsent(type, k -> new int[]{0, 0});
                int[] agg = bySpirit.get(e.getKey()).get(type);
                agg[0]++;          // count
                agg[1] += pts;     // total points
            }
        }
        java.util.Map<String, java.util.List<ContributorEntry>> out = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, java.util.Map<String, int[]>> spiritEntry : bySpirit.entrySet()) {
            java.util.List<ContributorEntry> list = new java.util.ArrayList<>();
            for (java.util.Map.Entry<String, int[]> typeEntry : spiritEntry.getValue().entrySet()) {
                list.add(new ContributorEntry(typeEntry.getKey(),
                        typeEntry.getValue()[0], typeEntry.getValue()[1]));
            }
            // Sort by points desc so the biggest contributor reads first.
            list.sort((a, b) -> Integer.compare(b.points(), a.points()));
            out.put(spiritEntry.getKey(), list);
        }
        return out;
    }

    @Unique
    private java.util.List<com.aetherianartificer.townstead.spirit.SpiritRegistry.Spirit>
            townstead$buildActiveSpirits(
                    com.aetherianartificer.townstead.spirit.VillageSpiritSyncPayload snapshot,
                    java.util.Map<String, java.util.List<ContributorEntry>> contribs) {
        java.util.List<com.aetherianartificer.townstead.spirit.SpiritRegistry.Spirit> list = new java.util.ArrayList<>();
        for (com.aetherianartificer.townstead.spirit.SpiritRegistry.Spirit s :
                com.aetherianartificer.townstead.spirit.SpiritRegistry.ordered()) {
            if (snapshot.perSpirit().getOrDefault(s.id(), 0) > 0) list.add(s);
        }
        int total = snapshot.total();
        // Filter: hide under 10% share.
        if (townstead$spiritFilterThreshold && total > 0) {
            list.removeIf(s -> snapshot.perSpirit().getOrDefault(s.id(), 0) * 10 < total);
        }
        // Sort.
        switch (townstead$spiritSortMode) {
            case 1 -> list.sort((a, b) -> Integer.compare(
                    contribs.getOrDefault(b.id(), java.util.List.of()).size(),
                    contribs.getOrDefault(a.id(), java.util.List.of()).size()));
            case 2 -> list.sort((a, b) -> townstead$translatedText(a.displayKey())
                    .compareToIgnoreCase(townstead$translatedText(b.displayKey())));
            default -> list.sort((a, b) -> Integer.compare(
                    snapshot.perSpirit().getOrDefault(b.id(), 0),
                    snapshot.perSpirit().getOrDefault(a.id(), 0)));
        }
        // Filter: top 3 only (after sort if sort was by points, top 3 are biggest).
        if (townstead$spiritFilterTop3 && list.size() > 3) {
            // If the user picked alpha/count sort, "top 3" should still mean 3 by points.
            if (townstead$spiritSortMode != 0) {
                java.util.List<com.aetherianartificer.townstead.spirit.SpiritRegistry.Spirit> byPts =
                        new java.util.ArrayList<>(list);
                byPts.sort((a, b) -> Integer.compare(
                        snapshot.perSpirit().getOrDefault(b.id(), 0),
                        snapshot.perSpirit().getOrDefault(a.id(), 0)));
                java.util.Set<String> keep = new java.util.HashSet<>();
                for (int i = 0; i < 3 && i < byPts.size(); i++) keep.add(byPts.get(i).id());
                list.removeIf(s -> !keep.contains(s.id()));
            } else {
                list.subList(3, list.size()).clear();
            }
        }
        return list;
    }

    @Unique
    private int townstead$spiritSectionHeight(String spiritId, java.util.List<ContributorEntry> contributors) {
        double rs = townstead$rowScale();
        int headerH = (int) Math.round(18 * rs); // ~5 above text, 9 text, ~4 below
        if (townstead$collapsedSet().contains(spiritId)) {
            return headerH + 2;
        }
        int barH = (int) Math.round(10 * rs);
        int barGap = (int) Math.round(8 * rs);   // breathing room between bar and contributor list
        int contribH = (int) Math.round(10 * rs);
        int footer = (int) Math.round(8 * rs);
        return headerH + barH + barGap + Math.max(1, contributors.size()) * contribH + footer;
    }

    @Unique
    private void townstead$drawSpiritSection(GuiGraphics context,
                                             com.aetherianartificer.townstead.spirit.SpiritRegistry.Spirit s,
                                             int pts, int totalPts,
                                             java.util.List<ContributorEntry> contributors,
                                             int[] thresholds, int left, int y, int right,
                                             boolean isDominant, long animNowMs,
                                             int mouseX, int mouseY, int sectionHeight,
                                             boolean isLast) {
        int maxThreshold = thresholds[thresholds.length - 1];
        int tier = com.aetherianartificer.townstead.spirit.VillageSpiritAggregator.tierForSpirit(pts);
        boolean maxed = tier >= thresholds.length;
        boolean collapsed = townstead$collapsedSet().contains(s.id());
        boolean hc = townstead$a11yHighContrast();
        double rs = townstead$rowScale();
        double fs = townstead$a11yFontScale();
        int headerH = (int) Math.round(18 * rs);
        int textY = y + (int) Math.round(5 * rs); // balanced top/bottom padding around the 9px text

        // Per-row background tint in the spirit's own color. Inset top + bottom
        // by a few px so adjacent rows don't visually bleed into each other —
        // the panel's dark body reads as a clean break between sections.
        int tintTop = y + 1;
        int tintBot = y + sectionHeight - 5;
        int rowTintAlpha = hc ? 0x22000000 : 0x14000000; // ~13% / ~8%
        int rowTint = (s.color() & 0x00FFFFFF) | rowTintAlpha;
        context.fill(left - 4, tintTop, right + 4, tintBot, rowTint);

        // Hover highlight — additional wash on top when the mouse is inside.
        boolean hovered = mouseX >= left - 4 && mouseX <= right + 4
                && mouseY >= tintTop && mouseY < tintBot;
        if (hovered) {
            int tintAlpha = hc ? 0x60000000 : 0x20000000;
            int tintColor = (s.color() & 0x00FFFFFF) | tintAlpha;
            context.fill(left - 4, tintTop, right + 4, tintBot, tintColor);
        }

        // Chevron indicating collapse state — aligned with the spirit name text.
        int chevColor = hc ? 0xFFFFFFFF : 0xFF8A8A92;
        int chevX = left;
        int chevY = textY + 1;
        if (collapsed) {
            context.fill(chevX,     chevY,     chevX + 1, chevY + 5, chevColor);
            context.fill(chevX + 1, chevY + 1, chevX + 2, chevY + 4, chevColor);
            context.fill(chevX + 2, chevY + 2, chevX + 3, chevY + 3, chevColor);
        } else {
            context.fill(chevX,     chevY + 1, chevX + 5, chevY + 2, chevColor);
            context.fill(chevX + 1, chevY + 2, chevX + 4, chevY + 3, chevColor);
            context.fill(chevX + 2, chevY + 3, chevX + 3, chevY + 4, chevColor);
        }

        // Spirit icon — scales with row scale so it doesn't look tiny when
        // larger-hit-targets is on.
        float iconScale = (float) (0.625 * rs);
        context.pose().pushPose();
        context.pose().translate(left + 7, textY - 1, 0);
        context.pose().scale(iconScale, iconScale, 1f);
        context.renderItem(new net.minecraft.world.item.ItemStack(s.icon()), 0, 0);
        context.pose().popPose();

        int textLeft = left + (int) Math.round(19 * rs);
        Component label = townstead$translatedComponent(s.displayKey());
        int labelColor = hc ? 0xFFFFFFFF : s.color();
        townstead$drawScaledString(context, label, textLeft, textY, labelColor, fs);
        int labelW = (int) Math.round(this.font.width(label) * fs);
        if (tier >= 1) {
            String tierKey = "townstead.spirit.tier." + s.id() + "." + tier;
            String tierName = townstead$translatedText(tierKey);
            int tierColor = hc ? 0xFFCCCCCC : 0xFF808890;
            townstead$drawScaledString(context, "\u00B7 " + tierName,
                    textLeft + labelW + 4, textY, tierColor, fs);
        }
        int sharePct = totalPts > 0 ? (int) Math.round(100.0 * pts / totalPts) : 0;
        String num;
        if (maxed) {
            num = pts + " pts \u00B7 " + sharePct + "% \u00B7 max";
        } else {
            int nextThreshold = thresholds[tier];
            int remain = nextThreshold - pts;
            String nextTierName = townstead$translatedText(
                    "townstead.spirit.tier." + s.id() + "." + (tier + 1));
            num = pts + " pts \u00B7 " + sharePct + "% \u00B7 " + remain + " \u2192 " + nextTierName;
        }
        int numW = (int) Math.round(this.font.width(num) * fs);
        int numColor = hc ? 0xFFFFFFFF : 0xC0C0C0;
        townstead$drawScaledString(context, num, right - numW, textY, numColor, fs);

        if (collapsed) {
            if (!isLast) {
                int divY = y + sectionHeight - 2;
                int divCol = hc ? 0xFF909090 : 0xFF2A2D35;
                context.fill(left - 2, divY, right + 2, divY + 1, divCol);
            }
            return;
        }

        // Bar — recessed dark trough with colored fill + 3-band gradient
        // (lighter top, mid body, deeper shadow at bottom).
        int barY = y + headerH;
        int barH = (int) Math.round(10 * rs);
        int barLeft = left;
        int barRight = right;
        int troughBorder = hc ? 0xFFFFFFFF : 0xFF454545;
        int troughBg = hc ? 0xFF000000 : 0xFF0E1014;
        context.fill(barLeft - 1, barY - 1, barRight + 1, barY + barH + 1, troughBorder);
        context.fill(barLeft, barY, barRight, barY + barH, troughBg);
        int fillWidth = Math.min(barRight - barLeft,
                (int) Math.round((double) pts / maxThreshold * (barRight - barLeft)));
        if (fillWidth > 0) {
            int fillEnd = barLeft + fillWidth;
            int base = s.color();
            context.fill(barLeft, barY, fillEnd, barY + barH, base);
            // Top 2px sheen — noticeably lighter.
            context.fill(barLeft, barY, fillEnd, barY + 2, townstead$lighten(base, 0.45f));
            // Row 2-3 — soft highlight transition.
            context.fill(barLeft, barY + 2, fillEnd, barY + 3, townstead$lighten(base, 0.2f));
            // Bottom 2px — deep shadow.
            context.fill(barLeft, barY + barH - 2, fillEnd, barY + barH,
                    townstead$lighten(base, -0.4f));
            // Row above that — mid shadow.
            context.fill(barLeft, barY + barH - 3, fillEnd, barY + barH - 2,
                    townstead$lighten(base, -0.15f));

            // A11y colorblind hatching overlay — distinct pattern per spirit.
            townstead$applyHatching(context, s.id(), barLeft, barY, fillEnd, barY + barH);

            // Dominant spirit shimmer — a bright band sweeping across the fill.
            if (isDominant) {
                double cycle = (animNowMs % 2600) / 2600.0;
                int sweep = (int) Math.round(cycle * (fillWidth + 30)) - 15;
                int peakX = barLeft + sweep;
                for (int dx = -5; dx <= 5; dx++) {
                    int sx = peakX + dx;
                    if (sx < barLeft || sx >= fillEnd) continue;
                    int falloff = 110 - Math.abs(dx) * 22;
                    if (falloff <= 0) continue;
                    int shimmerColor = (falloff << 24) | 0xFFFFFF;
                    context.fill(sx, barY + 1, sx + 1, barY + barH - 1, shimmerColor);
                }
            }
        }
        for (int t : thresholds) {
            int tx = barLeft + (int) Math.round((double) t / maxThreshold * (barRight - barLeft));
            boolean reached = pts >= t;
            context.fill(tx, barY, tx + 1, barY + barH, 0xFF000000);
            context.fill(tx, barY - 2, tx + 1, barY, reached ? 0xFFFFFFFF : 0xFF606068);
        }

        // Contributor list — small item icons followed by count · name · +pts.
        // Extra breathing room below the bar so the two sections read as
        // distinct groups (eventual pagination target).
        int barGap = (int) Math.round(8 * rs);
        int listY = barY + barH + barGap;
        int contribLineH = (int) Math.round(10 * rs);
        float contribIconScale = (float) (0.625 * rs);
        int contribTextX = left + (int) Math.round(14 * rs);
        int contribColor = hc ? 0xFFFFFFFF : 0xFFB0B0B0;
        int emptyColor = hc ? 0xFFCCCCCC : 0xFF707078;
        if (contributors.isEmpty()) {
            townstead$drawScaledString(context,
                    townstead$translatedComponent("townstead.spirit.no_contributors"),
                    left, listY, emptyColor, fs);
        } else {
            for (ContributorEntry c : contributors) {
                net.minecraft.world.item.ItemStack iconStack = townstead$catalogIconFor(c.buildingType());
                if (!iconStack.isEmpty()) {
                    context.pose().pushPose();
                    context.pose().translate(left + 2, listY - 1, 0);
                    context.pose().scale(contribIconScale, contribIconScale, 1f);
                    context.renderItem(iconStack, 0, 0);
                    context.pose().popPose();
                }
                String displayName = townstead$translatedText("buildingType." + c.buildingType());
                String line = c.count() + "x " + displayName + "  +" + c.points();
                townstead$drawScaledString(context, line, contribTextX, listY, contribColor, fs);
                listY += contribLineH;
            }
        }

        // Subtle divider between sections (skip the last one).
        if (!isLast) {
            int dividerY = y + sectionHeight - 3;
            int divCol = hc ? 0xFF909090 : 0xFF2A2D35;
            context.fill(left - 2, dividerY, right + 2, dividerY + 1, divCol);
        }
    }

    @Unique
    private int townstead$lighten(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.max(0, Math.min(255, (int) (((argb >>> 16) & 0xFF) * (1 + factor))));
        int g = Math.max(0, Math.min(255, (int) (((argb >>> 8) & 0xFF) * (1 + factor))));
        int b = Math.max(0, Math.min(255, (int) ((argb & 0xFF) * (1 + factor))));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Unique
    private void townstead$renderSpiritRadar(
            GuiGraphics context,
            com.aetherianartificer.townstead.spirit.VillageSpiritSyncPayload snapshot,
            int readoutColor,
            int contentLeft, int contentTop, int contentRight, int contentBottom) {
        java.util.List<com.aetherianartificer.townstead.spirit.SpiritRegistry.Spirit> spirits =
                com.aetherianartificer.townstead.spirit.SpiritRegistry.ordered();
        int n = spirits.size();
        int cx = (contentLeft + contentRight) / 2;
        int cy = (contentTop + contentBottom) / 2;
        int radius = Math.min((contentRight - contentLeft) / 2 - 46,
                (contentBottom - contentTop) / 2 - 20);
        if (radius < 20) radius = 20;
        int[] thresholds = com.aetherianartificer.townstead.spirit.VillageSpiritAggregator.tierThresholds();
        int maxPts = thresholds[thresholds.length - 1];

        double[] angles = new double[n];
        int[] axisX = new int[n];
        int[] axisY = new int[n];
        for (int i = 0; i < n; i++) {
            angles[i] = -Math.PI / 2 + 2 * Math.PI * i / n;
            axisX[i] = cx + (int) Math.round(Math.cos(angles[i]) * radius);
            axisY[i] = cy + (int) Math.round(Math.sin(angles[i]) * radius);
        }

        // Grid rings at each tier threshold as a heptagon outline.
        for (int t = 0; t < thresholds.length; t++) {
            double frac = (double) thresholds[t] / maxPts;
            int ringColor = t == thresholds.length - 1 ? 0x80707078 : 0x40606068;
            int[] rx = new int[n];
            int[] ry = new int[n];
            for (int i = 0; i < n; i++) {
                rx[i] = cx + (int) Math.round(Math.cos(angles[i]) * radius * frac);
                ry[i] = cy + (int) Math.round(Math.sin(angles[i]) * radius * frac);
            }
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                townstead$drawLine(context, rx[i], ry[i], rx[j], ry[j], ringColor);
            }
        }
        // Radial axes from center to each spirit tip.
        for (int i = 0; i < n; i++) {
            townstead$drawLine(context, cx, cy, axisX[i], axisY[i], 0x50606068);
        }

        // Data polygon — each spirit's share of tier 5 threshold maps to its
        // distance along that axis. Fill-ish effect done by drawing from the
        // center to each data point with accent color (cheap approximation).
        int[] dx = new int[n];
        int[] dy = new int[n];
        for (int i = 0; i < n; i++) {
            int pts = snapshot.perSpirit().getOrDefault(spirits.get(i).id(), 0);
            double frac = Math.min(1.0, (double) pts / maxPts);
            dx[i] = cx + (int) Math.round(Math.cos(angles[i]) * radius * frac);
            dy[i] = cy + (int) Math.round(Math.sin(angles[i]) * radius * frac);
        }
        int fillColor = (readoutColor & 0x00FFFFFF) | 0x30000000;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            // Two lines offset by 1px give a slightly thicker outline.
            townstead$drawLine(context, dx[i], dy[i], dx[j], dy[j], (readoutColor & 0x00FFFFFF) | 0xE0000000);
            townstead$drawLine(context, dx[i] + 1, dy[i], dx[j] + 1, dy[j], fillColor);
        }
        // Data point markers.
        for (int i = 0; i < n; i++) {
            com.aetherianartificer.townstead.spirit.SpiritRegistry.Spirit s = spirits.get(i);
            context.fill(dx[i] - 2, dy[i] - 2, dx[i] + 3, dy[i] + 3, 0xFF000000);
            context.fill(dx[i] - 1, dy[i] - 1, dx[i] + 2, dy[i] + 2, s.color());
        }

        // Tip labels — icon + name + points just outside each axis end.
        for (int i = 0; i < n; i++) {
            com.aetherianartificer.townstead.spirit.SpiritRegistry.Spirit s = spirits.get(i);
            int pts = snapshot.perSpirit().getOrDefault(s.id(), 0);
            int ox = (int) Math.round(Math.cos(angles[i]) * 14);
            int oy = (int) Math.round(Math.sin(angles[i]) * 14);
            int iconX = axisX[i] + ox - 5;
            int iconY = axisY[i] + oy - 5;
            context.pose().pushPose();
            context.pose().translate(iconX, iconY, 0);
            context.pose().scale(0.625f, 0.625f, 1f);
            context.renderItem(new net.minecraft.world.item.ItemStack(s.icon()), 0, 0);
            context.pose().popPose();
            String lbl = townstead$translatedText(s.displayKey()) + " " + pts;
            context.pose().pushPose();
            context.pose().scale(0.75f, 0.75f, 1f);
            int lw = this.font.width(lbl);
            int fx = (int) Math.round((axisX[i] + ox) / 0.75) - lw / 2;
            int fy = (int) Math.round((axisY[i] + oy + 6) / 0.75);
            context.drawString(this.font, lbl, fx, fy, s.color(), false);
            context.pose().popPose();
        }
    }

    //? if neoforge {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_7933_", remap = false, at = @At("HEAD"), cancellable = true)
    *///?}
    private void townstead$spiritKeyPressed(int keyCode, int scanCode, int modifiers,
            CallbackInfoReturnable<Boolean> cir) {
        if (!TOWNSTEAD_SPIRIT_PAGE.equals(this.page)) return;
        // Esc — bounce back to the map page.
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            setPage("map");
            cir.setReturnValue(true);
            cir.cancel();
            return;
        }
        // Arrows — pixel-step scroll in list mode.
        if (!townstead$spiritRadarMode) {
            if (keyCode == GLFW.GLFW_KEY_UP) {
                townstead$setSpiritScrollTarget(townstead$spiritScrollTarget - 18);
                cir.setReturnValue(true);
                cir.cancel();
                return;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                townstead$setSpiritScrollTarget(townstead$spiritScrollTarget + 18);
                cir.setReturnValue(true);
                cir.cancel();
                return;
            }
            // Numeric 1..7 — jump to the n-th active spirit.
            int idx = -1;
            if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
                idx = keyCode - GLFW.GLFW_KEY_1;
            }
            if (idx >= 0) {
                BlueprintScreenAccessor accessor = (BlueprintScreenAccessor) (Object) this;
                net.conczin.mca.server.world.data.Village village = accessor.townstead$getVillage();
                if (village == null) return;
                java.util.Optional<com.aetherianartificer.townstead.spirit.VillageSpiritSyncPayload> snapshotOpt =
                        com.aetherianartificer.townstead.spirit.ClientVillageSpiritStore.get(village.getId());
                if (snapshotOpt.isEmpty()) return;
                com.aetherianartificer.townstead.spirit.VillageSpiritSyncPayload snapshot = snapshotOpt.get();
                java.util.Map<String, java.util.List<ContributorEntry>> contribs =
                        townstead$contribsFor(village, snapshot);
                java.util.List<com.aetherianartificer.townstead.spirit.SpiritRegistry.Spirit> active =
                        townstead$buildActiveSpirits(snapshot, contribs);
                if (idx >= active.size()) return;
                String targetId = active.get(idx).id();
                int offset = 0;
                for (com.aetherianartificer.townstead.spirit.SpiritRegistry.Spirit s : active) {
                    if (s.id().equals(targetId)) break;
                    offset += townstead$spiritSectionHeight(s.id(),
                            contribs.getOrDefault(s.id(), java.util.List.of()));
                }
                // Expand if collapsed so user sees the bar + contributors after jump.
                townstead$collapsedSet().remove(targetId);
                townstead$setSpiritScrollTarget(offset);
                cir.setReturnValue(true);
                cir.cancel();
            }
        }
    }

    //? if neoforge {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_6375_", remap = false, at = @At("HEAD"), cancellable = true)
    *///?}
    private void townstead$spiritMouseClicked(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (!TOWNSTEAD_SPIRIT_PAGE.equals(this.page) || button != 0) return;

        // View-mode pill — highest priority, works in both modes.
        int windowX0 = townstead$spiritWindowX();
        int windowY0 = townstead$spiritWindowY();
        int windowW0 = townstead$spiritWindowW();
        int pillX = townstead$spiritTogglePillX(windowX0, windowW0);
        int pillY = townstead$spiritTogglePillY(windowY0);
        int pillW = townstead$spiritTogglePillW();
        int pillH = townstead$spiritTogglePillH();
        if (mouseX >= pillX && mouseX < pillX + pillW
                && mouseY >= pillY && mouseY < pillY + pillH) {
            boolean wantRadar = mouseX >= pillX + pillW / 2;
            if (wantRadar != townstead$spiritRadarMode) {
                townstead$spiritRadarMode = wantRadar;
            }
            cir.setReturnValue(true);
            cir.cancel();
            return;
        }

        if (townstead$spiritRadarMode) return;

        BlueprintScreenAccessor accessor = (BlueprintScreenAccessor) (Object) this;
        net.conczin.mca.server.world.data.Village village = accessor.townstead$getVillage();
        if (village == null) return;
        java.util.Optional<com.aetherianartificer.townstead.spirit.VillageSpiritSyncPayload> snapshotOpt =
                com.aetherianartificer.townstead.spirit.ClientVillageSpiritStore.get(village.getId());
        if (snapshotOpt.isEmpty()) return;
        com.aetherianartificer.townstead.spirit.VillageSpiritSyncPayload snapshot = snapshotOpt.get();

        int windowX = townstead$spiritWindowX();
        int windowY = townstead$spiritWindowY();
        int windowW = townstead$spiritWindowW();
        int windowH = townstead$spiritWindowH();
        int headerTop = windowY + 22;
        int dividerY = headerTop + 26;
        int contentTop = dividerY + 22; // below sort/filter controls row
        int contentBottom = windowY + windowH - 6;
        int contentLeft = windowX + 10;
        int contentRight = windowX + windowW - 10;
        if (mouseX < contentLeft - 4 || mouseX > contentRight + 4) return;
        if (mouseY < contentTop || mouseY > contentBottom) return;

        java.util.Map<String, java.util.List<ContributorEntry>> contribs =
                townstead$contribsFor(village, snapshot);
        java.util.List<com.aetherianartificer.townstead.spirit.SpiritRegistry.Spirit> active =
                townstead$buildActiveSpirits(snapshot, contribs);

        double rs = townstead$rowScale();
        int headerH = (int) Math.round(18 * rs);
        int barH = (int) Math.round(10 * rs);
        int barGap = (int) Math.round(8 * rs);
        int contribLineH = (int) Math.round(10 * rs);
        int barBlock = headerH + barH + barGap; // header + bar + spacing
        int y = contentTop - Math.round(townstead$spiritScrollCurrent);
        for (com.aetherianartificer.townstead.spirit.SpiritRegistry.Spirit s : active) {
            java.util.List<ContributorEntry> list = contribs.getOrDefault(s.id(), java.util.List.of());
            int h = townstead$spiritSectionHeight(s.id(), list);
            int sectionTop = y;
            int sectionBot = y + h;
            if (mouseY >= sectionTop && mouseY < sectionBot) {
                int rel = (int) mouseY - sectionTop;
                if (rel < headerH) {
                    if (townstead$collapsedSet().contains(s.id())) {
                        townstead$collapsedSet().remove(s.id());
                    } else {
                        townstead$collapsedSet().add(s.id());
                    }
                    cir.setReturnValue(true);
                    cir.cancel();
                    return;
                }
                if (townstead$collapsedSet().contains(s.id())) break;
                if (rel < barBlock) {
                    int desired = sectionTop + Math.round(townstead$spiritScrollCurrent) - contentTop;
                    townstead$setSpiritScrollTarget(desired);
                    cir.setReturnValue(true);
                    cir.cancel();
                    return;
                }
                int contribIdx = (rel - barBlock) / Math.max(1, contribLineH);
                if (contribIdx >= 0 && contribIdx < list.size()) {
                    String buildingType = list.get(contribIdx).buildingType();
                    townstead$pendingCatalogBuildingType = buildingType;
                    setPage(TOWNSTEAD_CATALOG_PAGE);
                    cir.setReturnValue(true);
                    cir.cancel();
                    return;
                }
                break;
            }
            y += h;
        }
    }

    @Unique
    private void townstead$drawLine(GuiGraphics context, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1);
        int sx = x1 < x2 ? 1 : -1;
        int dy = -Math.abs(y2 - y1);
        int sy = y1 < y2 ? 1 : -1;
        int err = dx + dy;
        int x = x1;
        int y = y1;
        int guard = dx - dy + 2;
        while (guard-- > 0) {
            context.fill(x, y, x + 1, y + 1, color);
            if (x == x2 && y == y2) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x += sx; }
            if (e2 <= dx) { err += dx; y += sy; }
        }
    }

    @Unique
    private net.minecraft.world.item.ItemStack townstead$catalogIconFor(String buildingTypeName) {
        if (buildingTypeName == null || buildingTypeName.isEmpty()) return net.minecraft.world.item.ItemStack.EMPTY;
        BuildingType bt = BuildingTypes.getInstance().getBuildingTypes().get(buildingTypeName);
        if (bt == null) {
            bt = com.aetherianartificer.townstead.client.catalog.CatalogDataLoader
                    .scannedBuildingTypes().get(buildingTypeName);
        }
        if (bt == null) return net.minecraft.world.item.ItemStack.EMPTY;
        return townstead$resolveNodeIcon(bt);
    }

    @Unique
    private int townstead$blend(int base, int accent, float mix) {
        int ba = (base >>> 24) & 0xFF;
        int br = (base >>> 16) & 0xFF;
        int bg = (base >>> 8) & 0xFF;
        int bb = base & 0xFF;
        int aa = (accent >>> 24) & 0xFF;
        int ar = (accent >>> 16) & 0xFF;
        int ag = (accent >>> 8) & 0xFF;
        int ab = accent & 0xFF;
        int r = (int) (br + (ar - br) * mix);
        int g = (int) (bg + (ag - bg) * mix);
        int b = (int) (bb + (ab - bb) * mix);
        int a = (int) (ba + (aa - ba) * mix);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Unique
    private int townstead$spiritAccentColor(com.aetherianartificer.townstead.spirit.SpiritReadout readout) {
        if (readout.primarySpiritId() != null) {
            var spirit = com.aetherianartificer.townstead.spirit.SpiritRegistry.get(readout.primarySpiritId());
            if (spirit.isPresent()) return spirit.get().color();
        }
        return 0xFFE3D18A;
    }
}

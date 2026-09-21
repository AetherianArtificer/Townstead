package com.aetherianartificer.townstead.client.catalog;

import com.aetherianartificer.townstead.client.building.BuildingPinClientStore;
import com.aetherianartificer.townstead.compat.BuildingIconResolver;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.spirit.BuildingSpiritIndex;
import net.conczin.mca.MCA;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.regex.Pattern;

/** Definition-specific adapters keep graph rendering independent of rooms and object recipes. */
public final class CatalogEntries {
    private static final Pattern TIER = Pattern.compile("(.+_l)([0-9]+)$");
    public record Display(CatalogGraphLayout.Entry entry, BuildingType building,
                          CatalogSyncS2CPayload.DecorationSummary decoration,
                          Component description, ItemStack icon) {
        public void drawIcon(GuiGraphics g, int x, int y) {
            if (!icon.isEmpty()) g.renderItem(icon, x, y);
            else if (building != null) g.blit(MCA.locate("textures/buildings.png"), x, y,
                    building.iconU(), building.iconV(), 20, 20);
        }
    }
    private CatalogEntries() {}
    public static String humanize(String value) {
        String raw = value.substring(Math.max(value.lastIndexOf('/'), value.lastIndexOf(':')) + 1);
        StringBuilder out = new StringBuilder();
        for (String word : raw.split("[_ .]+")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
    public static String translated(String key, String fallback) {
        return Component.translatableWithFallback(key, fallback).getString();
    }
    public static List<Display> buildings(Village village) {
        Set<String> recognized = new HashSet<>();
        if (village != null) for (var building : McaBuildings.all(village)) recognized.add(building.getType());
        Map<String, BuildingType> types = new LinkedHashMap<>(BuildingTypes.getInstance().getBuildingTypes());
        CatalogDataLoader.scannedBuildingTypes().forEach(types::putIfAbsent);
        List<BuildingType> available = types.values().stream().filter(BuildingType::visible)
                .filter(t -> ModCompat.isCompatAvailable(t.name()))
                .filter(t -> !CatalogDataLoader.overrideFor(t.name()).hide()).toList();
        Set<String> superseded = CatalogDataLoader.activeSupersededBuildingTypes(available.stream().map(BuildingType::name).toList());
        List<Display> out = new ArrayList<>();
        for (BuildingType type : available) {
            String id = type.name();
            if (superseded.contains(id)) continue;
            var match = CatalogDataLoader.matchGroup(id);
            var matcher = TIER.matcher(id);
            int tier = CatalogGraphLayout.tierNumber(id, match.filter(g -> "tiered".equals(g.layout()))
                    .map(CatalogDataLoader.GroupDef::tierPrefix).orElse(null));
            String family = "";
            if (matcher.matches()) {
                family = tier > 0 ? matcher.group(1) : "";
            }
            if (tier > 0 && match.isPresent() && "tiered".equals(match.get().layout())) family = match.get().id();
            String group = match.map(CatalogDataLoader.GroupDef::id).orElse(
                    !family.isEmpty() ? family : id.startsWith("compat/") ? id.split("/")[1] : "core");
            String fallback = group.endsWith("_l") ? humanize(group.substring(0, group.length() - 2)) : humanize(group);
            String groupLabel = match.map(g -> translated(g.label(), g.label())).orElse(
                    group.equals("core") ? translated("townstead.catalog.group.core", "Core") : fallback);
            Map<String, Integer> spirits = BuildingSpiritIndex.contributionsFor(id);
            String spiritNames = String.join(" ", spirits.keySet().stream().map(s ->
                    translated("townstead.spirit." + s, humanize(s))).toList());
            String name = translated("buildingType." + id, humanize(id));
            var entry = new CatalogGraphLayout.Entry(id, name, group, groupLabel, family, tier,
                    spirits.keySet(), recognized.contains(id), CatalogDataLoader.isHangout(id),
                    BuildingPinClientStore.isPinned(id), spiritNames + " " + String.join(" ", spirits.keySet()));
            ItemStack icon = BuildingIconResolver.nodeItemForType(id).filter(BuiltInRegistries.ITEM::containsKey)
                    .map(key -> new ItemStack(BuiltInRegistries.ITEM.get(key))).orElse(ItemStack.EMPTY);
            out.add(new Display(entry, type, null, Component.translatableWithFallback("buildingType." + id + ".description",
                    Component.translatable("townstead.catalog.no_description").getString()), icon));
        }
        out.sort(Comparator.comparing(d -> d.entry().id()));
        return List.copyOf(out);
    }
    public static List<Display> decorations() {
        List<Display> out = new ArrayList<>();
        for (var set : DecorationCatalogClientStore.entries()) {
            String key = "decoration." + set.id().getNamespace() + "." + set.id().getPath().replace('/', '.');
            var entry = new CatalogGraphLayout.Entry(set.id().toString(), translated(key, humanize(set.id().getPath())),
                    "decorations", Component.translatable("townstead.decorations.title").getString(), "", 0, Set.of(),
                    set.recognized() > 0, set.hangout(), false, "");
            ItemStack icon = BuiltInRegistries.ITEM.containsKey(set.icon())
                    ? new ItemStack(BuiltInRegistries.ITEM.get(set.icon())) : ItemStack.EMPTY;
            out.add(new Display(entry, null, set, Component.translatableWithFallback(key + ".description", ""), icon));
        }
        return List.copyOf(out);
    }
}

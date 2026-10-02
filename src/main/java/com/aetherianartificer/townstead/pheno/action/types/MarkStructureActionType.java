package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.marker.PlayerMarkers;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Sends a player somewhere: finds the nearest structure from a tag between {@code min} and
 * {@code max} blocks away, remembers it for that player under {@code key}, and hands them a map
 * marked with it. When no such structure exists, a small ruin {@code fallback} template is
 * placed in the band instead. Runs once per key; later runs do nothing.
 * <pre>
 * { "type": "pheno:mark_structure", "key": "old_town", "structures": "#townstead:story_ruins",
 *   "min": 500, "max": 1500, "fallback": "townstead:ruin/small_house", "map_name": "A map drawn from memory" }
 * </pre>
 * The player may be the action's focus or its {@code other}, so it works from a story's
 * {@code act()} as well as from a reward.
 */
public final class MarkStructureActionType implements ActionType {
    public static final String KEY = "pheno:mark_structure";
    private static final String DEFAULT_FALLBACK = "townstead:ruin/small_house";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        String key = GsonHelper.getAsString(json, "key", "");
        String structures = GsonHelper.getAsString(json, "structures", "#townstead:story_ruins");
        if (key.isBlank() || !structures.startsWith("#")) return null;
        ResourceLocation tagId = DataPackLang.parseId(structures.substring(1));
        ResourceLocation fallback = DataPackLang.parseId(GsonHelper.getAsString(json, "fallback", DEFAULT_FALLBACK));
        if (tagId == null || fallback == null) return null;
        int min = Math.max(0, GsonHelper.getAsInt(json, "min", 500));
        int max = Math.max(min + 16, GsonHelper.getAsInt(json, "max", 1500));
        boolean giveMap = GsonHelper.getAsBoolean(json, "map", true);
        String mapName = GsonHelper.getAsString(json, "map_name", "townstead.map.hand_drawn");
        TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, tagId);
        return ctx -> {
            ServerPlayer player = ctx.entity() instanceof ServerPlayer p ? p : ctx.other() instanceof ServerPlayer p ? p : null;
            if (player == null || PlayerMarkers.get(player, key) != null) return;
            ServerLevel level = player.serverLevel();
            BlockPos target = find(level, player.blockPosition(), tag, min, max);
            if (target == null) target = place(level, player.blockPosition(), fallback, min, max);
            if (target == null) {
                ctx.fail();
                return;
            }
            PlayerMarkers.set(player, key, GlobalPos.of(level.dimension(), target));
            if (giveMap) giveMap(player, level, target, mapName);
        };
    }

    /** The nearest tagged structure inside the band, searching outward from a few points. */
    private static @Nullable BlockPos find(ServerLevel level, BlockPos origin, TagKey<Structure> tag, int min, int max) {
        BlockPos found = level.findNearestMapStructure(tag, origin, Math.max(1, max / 16), false);
        if (inBand(found, origin, min, max)) return found;
        int ring = (min + max) / 2;
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8;
            BlockPos from = origin.offset((int) (Math.cos(angle) * ring), 0, (int) (Math.sin(angle) * ring));
            found = level.findNearestMapStructure(tag, from, Math.max(1, (max - min) / 32), false);
            if (inBand(found, origin, min, max)) return found;
        }
        return null;
    }

    private static boolean inBand(@Nullable BlockPos pos, BlockPos origin, int min, int max) {
        if (pos == null) return false;
        double dx = pos.getX() - origin.getX(), dz = pos.getZ() - origin.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        return distance >= min && distance <= max;
    }

    /** Places the fallback ruin on dry ground somewhere in the band. */
    private static @Nullable BlockPos place(ServerLevel level, BlockPos origin, ResourceLocation id, int min, int max) {
        Optional<StructureTemplate> template = level.getStructureManager().get(id);
        if (template.isEmpty()) return null;
        int ring = (min + max) / 2;
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            int x = origin.getX() + (int) (Math.cos(angle) * ring);
            int z = origin.getZ() + (int) (Math.sin(angle) * ring);
            level.getChunk(x >> 4, z >> 4);
            int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
            BlockPos ground = new BlockPos(x, y, z);
            if (!level.getFluidState(ground).isEmpty() || !level.getFluidState(ground.above()).isEmpty()) continue;
            template.get().placeInWorld(level, ground, ground, new StructurePlaceSettings(), level.getRandom(), 2);
            return ground;
        }
        return null;
    }

    private static void giveMap(ServerPlayer player, ServerLevel level, BlockPos target, String name) {
        ItemStack map = MapItem.create(level, target.getX(), target.getZ(), (byte) 2, true, true);
        MapItem.renderBiomePreviewMap(level, map);
        //? if >=1.21 {
        MapItemSavedData.addTargetDecoration(map, target, "+", net.minecraft.world.level.saveddata.maps.MapDecorationTypes.RED_X);
        map.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.translatable(name));
        //?} else {
        /*MapItemSavedData.addTargetDecoration(map, target, "+", net.minecraft.world.level.saveddata.maps.MapDecoration.Type.RED_X);
        map.setHoverName(Component.translatable(name));
        *///?}
        if (!player.getInventory().add(map)) player.drop(map, false);
    }
}

package com.aetherianartificer.townstead.pheno.action.block.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionType;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.Block;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionContext;
import com.aetherianartificer.townstead.pheno.resource.ResourceResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.core.BlockPos;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sets the target block to {@code block} (Apoli's block {@code set_block}).
 *
 * <p>JSON: {@code { "type":"pheno:set_block", "block":"minecraft:cobblestone" }}</p>
 * Properties may be copied from the replaced block, or from a relative source selected with
 * {@code "copy_from":[x,y,z]}. This keeps orientation and other shared state data-authored when
 * constructing one block beside another.
 */
public final class SetBlockBlockActionType implements BlockActionType {

    public static final String KEY = "pheno:set_block";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public BlockAction parse(JsonObject json) {
        ResourceResolver resolver = ResourceResolver.parse(json.get("block"));
        if (resolver == null) return null;
        Map<String, String> properties = new LinkedHashMap<>();
        if (json.has("properties")) {
            if (!json.get("properties").isJsonObject()) return null;
            for (var entry : json.getAsJsonObject("properties").entrySet()) {
                if (!entry.getValue().isJsonPrimitive()) return null;
                properties.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        List<String> copied = new ArrayList<>();
        if (json.has("copy_properties")) {
            if (!json.get("copy_properties").isJsonArray()) return null;
            for (var entry : json.getAsJsonArray("copy_properties")) {
                if (!entry.isJsonPrimitive()) return null;
                copied.add(entry.getAsString());
            }
        }
        int[] copyFrom = null;
        if (json.has("copy_from")) {
            if (!json.get("copy_from").isJsonArray()
                    || json.getAsJsonArray("copy_from").size() != 3) return null;
            copyFrom = new int[3];
            for (int i = 0; i < copyFrom.length; i++) {
                var value = json.getAsJsonArray("copy_from").get(i);
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return null;
                copyFrom[i] = value.getAsInt();
            }
        }
        int[] sourceOffset = copyFrom;
        return new BlockAction() {
            @Override public boolean canRun(BlockActionContext context) {
                ResourceLocation id = resolver.resolve(context);
                return id != null && BuiltInRegistries.BLOCK.containsKey(id);
            }

            @Override public void run(BlockActionContext context) {
                ResourceLocation id = resolver.resolve(context);
                if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) { context.fail(); return; }
                BlockPos source = sourceOffset == null ? context.pos()
                        : context.pos().offset(sourceOffset[0], sourceOffset[1], sourceOffset[2]);
                BlockState before = context.level().getBlockState(source);
                BlockState after = BuiltInRegistries.BLOCK.get(id).defaultBlockState();
                for (var entry : properties.entrySet()) after = set(after, entry.getKey(), entry.getValue());
                for (String name : copied) after = copy(before, after, name);
                context.level().setBlock(context.pos(), after, 3);
            }
        };
    }

    private static <T extends Comparable<T>> BlockState set(BlockState state, String name, String value) {
        Property<T> property = (Property<T>) state.getBlock().getStateDefinition().getProperty(name);
        return property == null ? state : property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState copy(BlockState before, BlockState after, String name) {
        Property from = before.getBlock().getStateDefinition().getProperty(name);
        Property to = after.getBlock().getStateDefinition().getProperty(name);
        if (from == null || to == null) return after;
        Comparable value = before.getValue(from);
        return to.getPossibleValues().contains(value) ? after.setValue(to, value) : after;
    }
}

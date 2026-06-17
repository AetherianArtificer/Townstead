package com.aetherianartificer.townstead.client.attachment.geo;

import com.aetherianartificer.townstead.Townstead;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses a Bedrock {@code .geo.json} geometry into a vanilla {@link ModelPart}
 * tree. Applies the standard Bedrock→Java transform: Bedrock entity space is
 * Y-up with cube origins at the min corner, Java {@code ModelPart} is Y-down with
 * the box grown from a pivot-relative corner, so Y is negated and cube corners are
 * offset by their height. Bone rotations carry the {@code (x,-y,-z)} sign flip the
 * coordinate change implies; most attachment models leave bones unrotated and tilt
 * via the definition instead, so this is rarely exercised.
 *
 * <p>Box UV only (per-face UV is not parsed). Returns {@code null} on a malformed
 * file so the caller skips the attachment.</p>
 */
public final class BedrockGeometryLoader {

    private BedrockGeometryLoader() {}

    private record Bone(String name, String parent, float[] pivot, float[] rotation, List<JsonObject> cubes) {}

    public static ModelPart parse(JsonObject root) {
        try {
            JsonArray geometries = GsonHelper.getAsJsonArray(root, "minecraft:geometry");
            if (geometries.isEmpty()) return null;
            JsonObject geometry = geometries.get(0).getAsJsonObject();
            JsonObject description = GsonHelper.getAsJsonObject(geometry, "description", new JsonObject());
            int texWidth = GsonHelper.getAsInt(description, "texture_width", 16);
            int texHeight = GsonHelper.getAsInt(description, "texture_height", 16);

            Map<String, Bone> bones = new LinkedHashMap<>();
            for (var element : GsonHelper.getAsJsonArray(geometry, "bones", new JsonArray())) {
                JsonObject boneJson = element.getAsJsonObject();
                String name = GsonHelper.getAsString(boneJson, "name");
                String parent = GsonHelper.getAsString(boneJson, "parent", "");
                float[] pivot = readVec(boneJson, "pivot");
                float[] rotation = boneJson.has("rotation") ? readVec(boneJson, "rotation") : new float[]{0, 0, 0};
                List<JsonObject> cubes = new ArrayList<>();
                for (var cube : GsonHelper.getAsJsonArray(boneJson, "cubes", new JsonArray())) {
                    cubes.add(cube.getAsJsonObject());
                }
                bones.put(name, new Bone(name, parent, pivot, rotation, cubes));
            }

            MeshDefinition mesh = new MeshDefinition();
            Map<String, PartDefinition> built = new LinkedHashMap<>();
            Set<String> pending = new HashSet<>(bones.keySet());
            // Multi-pass so a child whose parent appears later still attaches correctly.
            boolean progress = true;
            while (!pending.isEmpty() && progress) {
                progress = false;
                for (String name : new ArrayList<>(pending)) {
                    Bone bone = bones.get(name);
                    boolean hasParent = bone.parent() != null && !bone.parent().isEmpty();
                    PartDefinition parentDef = hasParent ? built.get(bone.parent()) : mesh.getRoot();
                    if (parentDef == null) continue;
                    float[] parentPivot = hasParent && bones.containsKey(bone.parent())
                            ? bones.get(bone.parent()).pivot() : new float[]{0, 0, 0};
                    built.put(name, addBone(parentDef, bone, parentPivot, texWidth, texHeight));
                    pending.remove(name);
                    progress = true;
                }
            }
            if (!pending.isEmpty()) {
                Townstead.LOGGER.warn("Attachment geometry has unresolved bone parents: {}", pending);
            }

            return LayerDefinition.create(mesh, texWidth, texHeight).bakeRoot();
        } catch (Exception e) {
            Townstead.LOGGER.error("Failed to parse attachment geometry", e);
            return null;
        }
    }

    private static PartDefinition addBone(PartDefinition parentDef, Bone bone, float[] parentPivot,
                                          int texWidth, int texHeight) {
        CubeListBuilder cubes = CubeListBuilder.create();
        for (JsonObject cube : bone.cubes()) {
            float[] origin = readVec(cube, "origin");
            float[] size = readVec(cube, "size");
            float inflate = GsonHelper.getAsFloat(cube, "inflate", 0f);
            int[] uv = readUv(cube);
            cubes.texOffs(uv[0], uv[1]).addBox(
                    origin[0] - bone.pivot()[0],
                    -(origin[1] - bone.pivot()[1]) - size[1],
                    origin[2] - bone.pivot()[2],
                    size[0], size[1], size[2],
                    new CubeDeformation(inflate));
        }
        PartPose pose = PartPose.offsetAndRotation(
                bone.pivot()[0] - parentPivot[0],
                -(bone.pivot()[1] - parentPivot[1]),
                bone.pivot()[2] - parentPivot[2],
                (float) Math.toRadians(bone.rotation()[0]),
                (float) Math.toRadians(-bone.rotation()[1]),
                (float) Math.toRadians(-bone.rotation()[2]));
        return parentDef.addOrReplaceChild(bone.name(), cubes, pose);
    }

    private static float[] readVec(JsonObject json, String key) {
        JsonArray array = GsonHelper.getAsJsonArray(json, key, new JsonArray());
        float[] out = new float[3];
        for (int i = 0; i < 3 && i < array.size(); i++) out[i] = array.get(i).getAsFloat();
        return out;
    }

    private static int[] readUv(JsonObject cube) {
        if (cube.has("uv") && cube.get("uv").isJsonArray()) {
            JsonArray uv = cube.getAsJsonArray("uv");
            return new int[]{uv.size() > 0 ? uv.get(0).getAsInt() : 0, uv.size() > 1 ? uv.get(1).getAsInt() : 0};
        }
        return new int[]{0, 0};
    }
}

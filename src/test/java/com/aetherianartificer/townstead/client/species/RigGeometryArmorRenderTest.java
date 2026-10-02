package com.aetherianartificer.townstead.client.species;

import com.aetherianartificer.townstead.client.attachment.geo.BedrockGeometryLoader;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RigGeometryArmorRenderTest {
    private ModelPart bake(String layer) throws Exception {
        try (var stream = getClass().getResourceAsStream("/rig-armor/" + layer + ".geo.json")) {
            assertNotNull(stream);
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            var root = BedrockGeometryLoader.parse(json, true);
            assertNotNull(root);
            for (var entry : json.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")) {
                var bone = entry.getAsJsonObject();
                if (!bone.has("scale")) continue;
                var part = root.getChild(bone.get("name").getAsString());
                var scale = bone.getAsJsonArray("scale");
                part.xScale = scale.get(0).getAsFloat();
                part.yScale = scale.get(1).getAsFloat();
                part.zScale = scale.get(2).getAsFloat();
            }
            return root;
        }
    }

    @Test void everyArmorPartRendersInTheAuthoredGeometryFrame() throws Exception {
        for (String layer : List.of("inner", "outer")) {
            var root = bake(layer);
            var model = new RigGeometryArmor.FittedModel(root, "test:rendered_geometry");
            // The generic body is hierarchical: age/size scaling belongs to its enclosing pose.
            // Neither the default young flag nor a copied baby flag may add humanoid offsets.
            for (boolean young : new boolean[]{true, false}) {
                model.young = young;
                for (String name : List.of("head", "body", "right_arm", "left_arm", "right_leg", "left_leg")) {
                    model.setAllVisible(false);
                    var part = root.getChild(name);
                    part.visible = true;
                    // Include a non-unit host scale and a crouch-like parent rotation.
                    var pose = new PoseStack();
                    pose.scale(.8F, .8F, .8F);
                    pose.mulPose(new org.joml.Quaternionf().rotationX(.07F));
                    var expected = new Vertices();
                    var actual = new Vertices();
                    //? if neoforge {
                    part.render(pose, expected, 0, 0, -1);
                    model.renderToBuffer(pose, actual, 0, 0, -1);
                    //?} else {
                    /*part.render(pose, expected, 0, 0, 1F, 1F, 1F, 1F);
                    model.renderToBuffer(pose, actual, 0, 0, 1F, 1F, 1F, 1F);
                    *///?}
                    assertEquals(24, actual.positions.size(), layer + "/" + name);
                    for (int i = 0; i < actual.positions.size(); i++) {
                        assertTrue(expected.positions.get(i).distance(actual.positions.get(i)) < 1e-6,
                                layer + "/" + name + " young=" + young + ": emitted vertex moved out of the rig frame");
                    }
                }
            }
        }
    }

    private static class Vertices implements VertexConsumer {
        final List<Vector3f> positions = new ArrayList<>();
        //? if neoforge {
        public VertexConsumer addVertex(float x, float y, float z) { positions.add(new Vector3f(x,y,z)); return this; }
        public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        public VertexConsumer setUv(float u, float v) { return this; }
        public VertexConsumer setUv1(int u, int v) { return this; }
        public VertexConsumer setUv2(int u, int v) { return this; }
        public VertexConsumer setNormal(float x, float y, float z) { return this; }
        //?} else {
        /*public VertexConsumer vertex(double x, double y, double z) { positions.add(new Vector3f((float) x, (float) y, (float) z)); return this; }
        public VertexConsumer color(int r, int g, int b, int a) { return this; }
        public VertexConsumer uv(float u, float v) { return this; }
        public VertexConsumer overlayCoords(int u, int v) { return this; }
        public VertexConsumer uv2(int u, int v) { return this; }
        public VertexConsumer normal(float x, float y, float z) { return this; }
        public void endVertex() {}
        public void defaultColor(int r, int g, int b, int a) {}
        public void unsetDefaultColor() {}
        *///?}
    }
}

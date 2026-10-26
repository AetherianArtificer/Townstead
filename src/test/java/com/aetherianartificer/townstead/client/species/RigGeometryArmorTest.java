package com.aetherianartificer.townstead.client.species;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RigGeometryArmorTest {
    private RigGeometryArmor.FittedModel model(float y, float scale) {
        var parts = new HashMap<String, ModelPart>();
        for (String name : List.of("head","hat","body","right_arm","left_arm","right_leg","left_leg")) {
            var part = new ModelPart(List.of(), Map.of());
            part.setInitialPose(PartPose.offset(0,y,0)); part.loadPose(part.getInitialPose());
            part.yScale = scale; parts.put(name,part);
        }
        return new RigGeometryArmor.FittedModel(new ModelPart(List.of(),parts),"test:independent_layers");
    }

    @Test void vanillaParentCopyPreservesIndependentLeggingsFit() {
        var parent = model(16,.28F);
        var leggings = model(20,.425F);
        parent.copyPropertiesTo(leggings);
        assertEquals(20,leggings.body.y);
        assertEquals(.425F,leggings.body.yScale);
        assertEquals(.425F,leggings.leftLeg.yScale);
        parent.body.yScale=.7F;
        parent.copyPropertiesTo(leggings);
        assertEquals(.425F,leggings.body.yScale,"repeat draws must restore the authored scale");
        assertEquals(16,parent.body.y);
    }

    @Test void playerArmorSubstitutionCannotDiscardTheFittedLayer() {
        var parent = model(16,.28F);
        var inner = model(18.7F,1F);
        var outer = model(16.6F,.28F);
        // MCA returns its own human mesh before renderArmorPiece copies the parent pose.
        HumanoidModel<LivingEntity> human = new HumanoidModel<>(LayerDefinition.create(
                HumanoidModel.createMesh(new CubeDeformation(.5F),0),64,32).bakeRoot());
        for (var slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            var selected = RigArmorRenderer.preserveFittedModel(slot, human, inner, outer);
            assertSame(slot == EquipmentSlot.LEGS ? inner : outer, selected,
                    "The draw must use the layer's actual mesh, not just copy its pose onto a human mesh");
        }
        @SuppressWarnings("unchecked")
        var selected = (HumanoidModel<LivingEntity>) RigArmorRenderer.preserveFittedModel(
                EquipmentSlot.LEGS, human, inner, outer);
        parent.copyPropertiesTo(selected);
        assertEquals(18.7F, selected.body.y);
        assertEquals(1F, selected.body.yScale);
        assertEquals(0F, human.body.y, "The shared human model must remain untouched");
    }

    @Test void ordinaryArmorLayersKeepTheOtherModsSelection() {
        var root = LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE,0),64,32);
        var original = new HumanoidModel<LivingEntity>(root.bakeRoot());
        var replacement = new HumanoidModel<LivingEntity>(root.bakeRoot());
        assertSame(replacement, RigArmorRenderer.preserveFittedModel(
                EquipmentSlot.LEGS,replacement,original,original));
    }
}

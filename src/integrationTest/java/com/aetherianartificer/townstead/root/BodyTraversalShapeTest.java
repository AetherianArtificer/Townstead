package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.root.rig.BodySize;
import com.aetherianartificer.townstead.root.rig.RigDefinition;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Uses Minecraft's real shape collision solver. This is not a full AI or modded-client test. */
class BodyTraversalShapeTest {
    private static AABB box(float width, float height) {
        return new AABB(.5-width/2, 0, -width/2, .5+width/2, height, width/2);
    }
    @Test void centredOpenDoorsClearBothHingesWhileOldPointSevenCapHitsTheLeaf() {
        for (boolean right : new boolean[]{false,true}) {
            var door = Shapes.create(right ? 13/16d : 0, 0, 1, right ? 1 : 3/16d, 2, 2);
            var header = Shapes.create(0, 2, 1, 1, 3, 2);
            var walls = List.of(door, header, Shapes.create(-1,0,1,0,3,2), Shapes.create(1,0,1,2,3,2));
            assertTrue(Shapes.collide(Direction.Axis.Z, box(.7f,1.9f), walls, 3) < 1);
            for (float scale : new float[]{.25f, 1, 2, 10}) {
                var size = BodySize.resolve(new RigDefinition.Hitbox(.5f,.75f,.625f),scale,scale,BodySize.Posture.STANDING);
                assertEquals(3, Shapes.collide(Direction.Axis.Z,box(size.width(),size.height()),walls,3),1e-7);
            }
        }
    }
    @Test void halfBlockRisersNeedStepClearanceRegardlessOfBodyHeight() {
        // A bottom slab and the first riser of a straight stair share this collision height.
        var slab = Shapes.create(0, 0, 1, 1, .5, 2);
        for (float height : new float[]{.2f,.75f,1.9f}) {
            AABB body = box(.5f,height);
            assertTrue(Shapes.collide(Direction.Axis.Z,body,List.of(slab),1.5) < 1.5);
            double lift = Shapes.collide(Direction.Axis.Y,body,List.of(slab),.6);
            assertEquals(1.5,Shapes.collide(Direction.Axis.Z,body.move(0,lift,0),List.of(slab),1.5),1e-7);
            double settle = Shapes.collide(Direction.Axis.Y,body.move(0,lift,1.5),List.of(slab),-.6);
            assertEquals(.5,lift+settle,1e-7);
            assertTrue(Shapes.collide(Direction.Axis.Z,body.move(0,.3,0),List.of(slab),1.5) < 1.5);
        }
    }
    @Test void lowCeilingDistinguishesStandingCrouchingAndSwimming() {
        var base = new RigDefinition.Hitbox(.5f,.75f,.625f,.4f,0,true);
        var roof = List.of(Shapes.create(0,.65,1,1,2,2));
        for (var posture : BodySize.Posture.values()) {
            var size = BodySize.resolve(base,1,1,posture);
            double movement=Shapes.collide(Direction.Axis.Z,box(size.width(),size.height()),roof,3);
            if (posture == BodySize.Posture.STANDING) assertTrue(movement<3);
            else assertEquals(3,movement,1e-7);
        }
    }
}

package com.aetherianartificer.townstead.sizing;

import com.aetherianartificer.townstead.root.PlayerRoot;
import com.aetherianartificer.townstead.root.rig.RigHitboxes;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@Mod("townstead_size_tests")
@GameTestHolder("townstead_size_tests")
@PrefixGameTestTemplate(false)
public final class BodySizeGameTests {
    private static final String ROOT = "townstead_size_tests:frog";

    private static void floor(GameTestHelper h) {
        for (int x=0;x<16;x++) for (int z=0;z<16;z++) h.setBlock(x,0,z,Blocks.STONE);
    }
    private static void position(GameTestHelper h, LivingEntity e, int x, int y, int z) {
        BlockPos p=h.absolutePos(new BlockPos(x,y,z));
        e.setPos(p.getX()+.5,p.getY(),p.getZ()+.5);
        e.setOnGround(true); e.setDeltaMovement(Vec3.ZERO);
    }
    private static void walkSouth(LivingEntity e, int ticks) {
        for (int i=0;i<ticks;i++) e.move(MoverType.SELF,new Vec3(0,-.08,.1));
    }
    private static void door(GameTestHelper h, int x, DoorHingeSide hinge) {
        var state=Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.OPEN,true)
                .setValue(DoorBlock.FACING,Direction.SOUTH).setValue(DoorBlock.HINGE,hinge);
        h.setBlock(x,1,5,state.setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER));
        h.setBlock(x,2,5,state.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));
        h.setBlock(x,3,5,Blocks.STONE);
        for(int y=1;y<=3;y++) { h.setBlock(x-1,y,5,Blocks.STONE); h.setBlock(x+1,y,5,Blocks.STONE); }
    }
    private static void assertCoherent(GameTestHelper h, LivingEntity e) {
        var query=e.getDimensions(e.getPose());
        h.assertTrue(Math.abs(query.width()-e.getBbWidth())<.0001,"Query/cached width disagree");
        h.assertTrue(Math.abs(query.height()-e.getBbHeight())<.0001,"Query/cached height disagree");
        h.assertTrue(e.getEyeHeight()>0 && e.getEyeHeight()<e.getBbHeight(),"Eye outside physical body");
    }
    @GameTest(template="empty")
    public static void playerDoorsStairsAndScale(GameTestHelper h) {
        floor(h);
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        PlayerRoot.setRootId(player,ROOT);
        h.assertTrue(Math.abs(player.getBbHeight()-.75)<.0001,"Ribbit rig was not applied");
        for(var hinge:DoorHingeSide.values()) {
            door(h,2,hinge);position(h,player,2,1,2);assertCoherent(h,player);
            walkSouth(player,60);
            h.assertTrue(player.getZ()>h.absolutePos(new BlockPos(2,1,7)).getZ(),"Player blocked at "+hinge+" door");
        }
        h.setBlock(6,1,4,Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.SOUTH));
        for(int z=5;z<12;z++)h.setBlock(6,1,z,Blocks.STONE);
        position(h,player,6,1,2);walkSouth(player,70);
        h.assertTrue(player.getZ()>h.absolutePos(new BlockPos(6,1,8)).getZ(),"Player blocked at stairs");
        h.assertTrue(Math.abs(player.getY()-h.absolutePos(new BlockPos(6,2,8)).getY())<.01,"Player did not climb stairs");
        position(h,player,10,1,2);
        player.getAttribute(Attributes.SCALE).setBaseValue(2);player.refreshDimensions();assertCoherent(h,player);
        h.assertTrue(Math.abs(player.getBbHeight()-1.5)<.0001,"Scale applied twice or ignored");
        h.assertTrue(player.getBbWidth()<=.60001,"Width cap bypassed by scale attribute");
        player.setPose(Pose.CROUCHING);player.refreshDimensions();assertCoherent(h,player);
        h.assertTrue(player.getBbHeight()<1.5,"Crouch did not reduce clearance");
        player.getAttribute(Attributes.SCALE).setBaseValue(1);player.setPose(Pose.STANDING);
        PlayerRoot.setRootId(player,"");
        h.assertTrue(Math.abs(player.getBbHeight()-1.8)<.0001,"Removing rig left its collision box cached");
        assertCoherent(h,player);
        h.succeed();
    }
    @GameTest(template="empty")
    public static void villagerCollisionAndNavigation(GameTestHelper h) {
        floor(h);door(h,2,DoorHingeSide.LEFT);
        var type=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("mca:male_villager"));
        var villager=(VillagerEntityMCA)h.spawn(type,2,1,2);
        villager.setAge(0);villager.setNoAi(true);
        TownsteadVillagers.get(villager).life().setRoot(ROOT);villager.refreshDimensions();
        h.assertTrue(RigHitboxes.definition(villager)!=null,"Villager rig not resolved");
        assertCoherent(h,villager);position(h,villager,2,1,2);
        var target=h.absolutePos(new BlockPos(2,1,8));
        var path=villager.getNavigation().createPath(target,0);
        h.assertTrue(path!=null && path.canReach(),"Villager cannot plan through open doorway");
        walkSouth(villager,60);
        h.assertTrue(villager.getZ()>h.absolutePos(new BlockPos(2,1,7)).getZ(),"Villager collides with open doorway");
        position(h,villager,8,1,2);
        h.setBlock(8,1,4,Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.SOUTH));
        for(int z=5;z<12;z++)h.setBlock(8,1,z,Blocks.STONE);
        var stairTarget=h.absolutePos(new BlockPos(8,2,8));
        var stairPath=villager.getNavigation().createPath(stairTarget,0);
        h.assertTrue(stairPath!=null && stairPath.canReach(),"Villager cannot plan up stairs");
        walkSouth(villager,70);
        h.assertTrue(villager.getZ()>stairTarget.getZ(),"Villager blocked at stairs");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void growthWaitsForClearanceAndHumanoidsFollowTheirProportions(GameTestHelper h) {
        floor(h);
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        PlayerRoot.setRootId(player,ROOT);position(h,player,2,1,2);player.refreshDimensions();
        player.tickCount=5;
        h.setBlock(2,2,2,Blocks.STONE);
        double feet=player.getY();
        player.getAttribute(Attributes.SCALE).setBaseValue(2);player.refreshDimensions();
        h.assertTrue(Math.abs(player.getBbHeight()-.75)<.0001,"Growth entered low ceiling");
        assertCoherent(h,player);
        h.assertTrue(player.getY()==feet,"Blocked growth moved feet");
        h.setBlock(2,2,2,Blocks.AIR);player.refreshDimensions();
        h.assertTrue(Math.abs(player.getBbHeight()-1.5)<.0001,"Growth did not resume when space cleared");
        assertCoherent(h,player);
        var type=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("mca:male_villager"));
        var villager=(VillagerEntityMCA)h.spawn(type,8,1,2);
        villager.setAge(0);villager.setNoAi(true);
        TownsteadVillagers.get(villager).life().setRoot(com.aetherianartificer.townstead.root.RootRegistry.DEFAULT_ID.toString());
        villager.getGenetics().setGene(net.conczin.mca.entity.ai.Genetics.SIZE,0f);
        villager.getGenetics().setGene(net.conczin.mca.entity.ai.Genetics.WIDTH,0f);
        villager.refreshDimensions();assertCoherent(h,villager);
        float smallWidth=villager.getBbWidth(), smallHeight=villager.getBbHeight();
        villager.getGenetics().setGene(net.conczin.mca.entity.ai.Genetics.SIZE,1f);
        villager.getGenetics().setGene(net.conczin.mca.entity.ai.Genetics.WIDTH,1f);
        villager.refreshDimensions();assertCoherent(h,villager);
        h.assertTrue(villager.getBbWidth()>smallWidth,"Humanoid width did not follow genetics");
        h.assertTrue(villager.getBbHeight()>smallHeight,"Humanoid height did not follow genetics");
        h.assertTrue(villager.getBbWidth()<=.60001 && villager.getBbHeight()<=1.90001,"Humanoid exceeded safe caps");
        h.succeed();
    }

}

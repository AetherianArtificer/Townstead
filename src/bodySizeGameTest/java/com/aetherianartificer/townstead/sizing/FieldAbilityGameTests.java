package com.aetherianartificer.townstead.sizing;

import com.aetherianartificer.townstead.pheno.action.ItemRetrievals;
import com.aetherianartificer.townstead.pheno.action.Leaps;
import com.aetherianartificer.townstead.root.PlayerRoot;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("townstead_size_tests")
@PrefixGameTestTemplate(false)
public final class FieldAbilityGameTests {
    private static Player player(GameTestHelper h, int y) {
        for (int x=0;x<16;x++) for(int z=0;z<16;z++) h.setBlock(x,0,z,Blocks.STONE);
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        PlayerRoot.setRootId(p,"townstead_size_tests:frog");
        BlockPos pos = h.absolutePos(new BlockPos(8,y,2));
        p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        p.setOnGround(true);p.setDeltaMovement(Vec3.ZERO);
        return p;
    }
    private static void aim(LivingEntity p, ItemEntity item) {
        Vec3 d = item.getBoundingBox().getCenter().subtract(p.getEyePosition());
        p.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));
        p.setXRot((float)-Math.toDegrees(Math.atan2(d.y,d.horizontalDistance())));
    }
    private static double flight(Player p) {
        double peak=p.getY();
        p.setOnGround(false);
        for(int i=0;i<90;i++) {
            Vec3 v=p.getDeltaMovement();
            p.move(MoverType.SELF,v);
            peak=Math.max(peak,p.getY());
            if(p.onGround()) break;
            Leaps.tick(p);
            p.setDeltaMovement(v.multiply(.91,1,.91).add(0,-.08,0).multiply(1,.98,1));
        }
        Leaps.tick(p);
        return peak;
    }

    @GameTest(template="empty")
    public static void leapRoofGroundingAndLandingDamage(GameTestHelper h) {
        Player p=player(h,1);
        h.setBlock(8,2,2,Blocks.STONE);
        h.assertFalse(Leaps.start(p,.7,0),"Leap entered a low ceiling");
        h.setBlock(8,2,2,Blocks.AIR);
        double start=p.getY();
        h.assertTrue(Leaps.start(p,.7,0),"Clear grounded leap failed");
        h.assertFalse(Leaps.start(p,.7,0),"Leap stacked in flight");
        double peak=flight(p);
        h.assertTrue(peak-start>2.8 && peak-start<3.5,"Unexpected leap height: "+(peak-start));
        h.assertTrue(p.getHealth()==p.getMaxHealth(),"Own leap caused landing damage");
        // A leap over a cliff only discounts its own ascent, not the height of the cliff.
        h.assertTrue(Leaps.start(p,.7,0),"Second grounded leap failed");
        p.setOnGround(false);
        for(int i=0;i<8;i++) {
            Vec3 v=p.getDeltaMovement();p.move(MoverType.SELF,v);Leaps.tick(p);
            p.setDeltaMovement(v.add(0,-.08,0).multiply(1,.98,1));
        }
        // Pass the total descent through the real damage hook: ascent plus a deep drop.
        p.causeFallDamage(9,1,p.damageSources().fall());
        h.assertTrue(p.getHealth()<p.getMaxHealth(),"Leap made a cliff fall harmless");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void retrievalRejectsWallsOwnersDelayAndDistance(GameTestHelper h) {
        Player p=player(h,1);
        ItemEntity item=h.spawnItem(Items.APPLE,8.5f,1.1f,6.5f);
        item.setNoPickUpDelay();aim(p,item);
        h.assertTrue(ItemRetrievals.target(p,6,.35)==item,"Aimed drop not selected");
        h.assertTrue(ItemRetrievals.target(p,2,.35)==null,"Out of range drop selected");
        item.setTarget(UUID.randomUUID());
        h.assertTrue(ItemRetrievals.target(p,6,.35)==null,"Another owner's drop selected");
        item.setTarget(p.getUUID());item.setPickUpDelay(10);
        h.assertTrue(ItemRetrievals.target(p,6,.35)==null,"Pickup delay bypassed");
        item.setNoPickUpDelay();
        for(int y=1;y<=3;y++)h.setBlock(8,y,4,Blocks.STONE);
        h.assertTrue(ItemRetrievals.target(p,6,.35)==null,"Drop targeted through wall");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=60)
    public static void tongueDeliversExactlyOneOriginalStack(GameTestHelper h) {
        Player p=player(h,1);
        ItemEntity item=h.spawnItem(Items.APPLE,8.5f,1.1f,6.5f);
        item.getItem().setCount(3);item.setNoPickUpDelay();aim(p,item);
        h.assertTrue(ItemRetrievals.start(p,6,.65,.35,4,0xE58D9A),"Retrieval failed to start");
        h.assertFalse(ItemRetrievals.start(p,6,.65,.35,4,0xE58D9A),"Duplicate retrieval started");
        h.onEachTick(() -> ItemRetrievals.tick(p));
        h.runAfterDelay(30,() -> {
            h.assertTrue(item.isRemoved(),"Item was not picked up");
            h.assertTrue(p.getInventory().countItem(Items.APPLE)==3,"Stack was lost or duplicated");
            h.succeed();
        });
    }

    @GameTest(template="empty",timeoutTicks=60)
    public static void tongueLeavesItemWhenInventoryFull(GameTestHelper h) {
        Player p=player(h,1);
        for(int i=0;i<36;i++)p.getInventory().setItem(i,new net.minecraft.world.item.ItemStack(Items.STONE,64));
        ItemEntity item=h.spawnItem(Items.APPLE,8.5f,1.1f,6.5f);
        item.setNoPickUpDelay();aim(p,item);
        h.assertTrue(ItemRetrievals.start(p,6,.65,.35,4,0xE58D9A),"Retrieval failed to start");
        h.onEachTick(() -> ItemRetrievals.tick(p));
        h.runAfterDelay(30,() -> {
            h.assertTrue(item.isAlive() && item.getItem().getCount()==1,"Full inventory lost item");
            h.assertTrue(item.distanceToSqr(p)<1,"Item was not delivered to feet");
            h.succeed();
        });
    }

    @GameTest(template="empty")
    public static void villagersUseOnlyWantedItemsAndSafeEscapeArcs(GameTestHelper h) {
        Player threat=player(h,1);
        var type=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(
                net.minecraft.resources.ResourceLocation.parse("mca:male_villager"));
        var villager=(net.conczin.mca.entity.VillagerEntityMCA)h.spawn(type,8,1,3);
        villager.setNoAi(true);villager.setAge(0);villager.setCanPickUpLoot(true);
        com.aetherianartificer.townstead.villager.TownsteadVillagers.get(villager).life()
                .setRoot("townstead_size_tests:frog");
        villager.refreshDimensions();villager.setOnGround(true);
        ItemEntity bread=h.spawnItem(Items.BREAD,8.5f,1.1f,6.5f);bread.setNoPickUpDelay();
        h.assertTrue(ItemRetrievals.target(villager,6,.35)==null,"Idle villager targeted arbitrary drops");
        villager.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM,bread);
        h.assertTrue(ItemRetrievals.target(villager,6,.35)==bread,"Wanted food was not eligible");
        bread.setTarget(UUID.randomUUID());
        h.assertTrue(ItemRetrievals.target(villager,6,.35)==null,"Villager targeted an owned item");
        h.assertTrue(Leaps.safeArc(villager,new Vec3(0,.7,.18)),"Flat safe escape arc rejected");
        h.setBlock(8,1,5,Blocks.FIRE);
        h.assertFalse(Leaps.safeArc(villager,new Vec3(0,.7,.18)),"Escape arc accepted a burning landing");
        h.setBlock(8,1,5,Blocks.AIR);
        h.assertFalse(Leaps.start(villager,.7,.18),"Villager leapt without a threat");
        villager.setLastHurtByMob(threat);villager.tickCount+=101;
        h.assertFalse(Leaps.start(villager,.7,.18),"Stale threat caused a leap");
        for(int x=0;x<16;x++)for(int z=4;z<16;z++)h.setBlock(x,0,z,Blocks.AIR);
        h.assertFalse(Leaps.safeArc(villager,new Vec3(0,.7,.18)),"Escape arc accepted unsupported landing");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void grantingPlayerAbilitiesPreservesAppearanceAndExposesActives(GameTestHelper h)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        // FakePlayer's no-op connection avoids a client handshake in this headless test.
        var p=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ribbit-gene-test"));
        PlayerRoot.setRootId(p,"townstead_size_tests:frog");
        var id=net.minecraft.resources.ResourceLocation.parse("townstead_size_tests:appearance");
        var kept=com.aetherianartificer.townstead.root.gene.Allele.of(id,"kept");
        var genotype=PlayerRoot.getGenotype(p);genotype.set(id,kept,kept);PlayerRoot.setGenotype(p,genotype);
        var source=p.createCommandSourceStack().withPermission(2);
        var commands=h.getLevel().getServer().getCommands().getDispatcher();
        for(String name:new String[]{"amphibious","spring_legs","sticky_tongue"}) {
            int result=commands.execute("townstead gene grant @s \"townstead_size_tests:"+name+"\"",source);
            h.assertTrue(result==1,"Player gene grant failed: "+name);
        }
        h.assertTrue(PlayerRoot.getGenotype(p).at(id)[0].equals(kept),"Grant rerolled another locus");
        h.assertTrue(com.aetherianartificer.townstead.root.ability.ActiveAbilities.resolve(p).size()==2,
                "New active genes did not resolve through the ability system");
        h.assertTrue(com.aetherianartificer.townstead.root.ExpressedGenes.instancesOf(p,
                com.aetherianartificer.townstead.root.gene.types.AbilityGeneType.Instance.class).stream()
                .anyMatch(g -> g.ability()==com.aetherianartificer.townstead.root.ability.Ability.WATER_BREATHING),
                "Amphibious did not grant underwater breathing");
        commands.execute("townstead gene revoke @s \"townstead_size_tests:spring_legs\"",source);
        h.assertTrue(com.aetherianartificer.townstead.root.ability.ActiveAbilities.resolve(p).size()==1,
                "Revoking player ability failed");
        h.assertTrue(PlayerRoot.getGenotype(p).at(id)[0].equals(kept),"Revoke changed appearance");
        h.succeed();
    }
}

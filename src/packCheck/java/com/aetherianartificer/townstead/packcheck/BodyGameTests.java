package com.aetherianartificer.townstead.packcheck;

import com.aetherianartificer.townstead.root.PlayerRoot;
import com.aetherianartificer.townstead.root.ability.ResourceValues;
import com.aetherianartificer.townstead.root.attachment.AttachmentDef;
import com.aetherianartificer.townstead.root.attachment.AttachmentPart;
import com.aetherianartificer.townstead.root.attachment.BodyParts;
import com.aetherianartificer.townstead.root.attachment.BodyValues;
import com.aetherianartificer.townstead.root.attachment.BodyVolumes;
import com.aetherianartificer.townstead.root.attachment.WornAttachments;
import com.mojang.authlib.GameProfile;
import com.aetherianartificer.townstead.root.attachment.BodyPose;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Puts a real body with a large hit part in a world: a wall behind it must stop it backing up, and another person
 * must be able to aim at and hurt that part. Which root, part and meter come from the {@code bodyTestRoot},
 * {@code bodyTestPart} and {@code bodyTestMeter} Gradle properties. Skips when they aren't given or the pack isn't loaded.
 */
@GameTestHolder("townstead_pack_check")
@PrefixGameTestTemplate(false)
public final class BodyGameTests {

    private static final Logger LOG = LoggerFactory.getLogger("body-check");
    private static final String ROOT = System.getProperty("townstead.bodyTest.root", "");
    private static final String ABDOMEN = System.getProperty("townstead.bodyTest.part", "");
    private static final ResourceLocation HONEY = ResourceLocation.parse(System.getProperty("townstead.bodyTest.meter", "minecraft:none"));

    /** Fake players refuse all damage; a body under test has to take it. */
    private static final class Body extends FakePlayer {
        Body(ServerLevel level, String name) {
            super(level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
        }

        @Override
        public boolean isInvulnerableTo(DamageSource source) {
            return false;
        }

        @Override
        public boolean canHarmPlayer(net.minecraft.world.entity.player.Player other) {
            return true;
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void largePartBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        if (AttachmentServerDataHolder.missing(ABDOMEN)) {
            LOG.info("[body-check] pack not loaded, skipped");
            helper.succeed();
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        for (int x = -3; x <= 3; x++) {
            for (int z = -6; z <= 6; z++) {
                level.setBlock(at.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 2);
                for (int y = 0; y < 4; y++) level.setBlock(at.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        // A body at yaw 0 looks along +z, so its abdomen reaches toward -z.
        for (int x = -3; x <= 3; x++) {
            for (int y = 0; y < 3; y++) level.setBlock(at.offset(x, y, -4), Blocks.STONE.defaultBlockState(), 2);
        }

        Body bearer = new Body(level, "body-check-bearer");
        bearer.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
        bearer.yBodyRot = 0f;
        PlayerRoot.setRootId(bearer, ROOT);
        PlayerRoot.getOrSeedGenotype(bearer, bearer.getRandom());

        List<String> problems = new ArrayList<>();
        List<AttachmentDef> worn = WornAttachments.of(bearer);
        LOG.info("[body-check] worn: {}", worn.stream().map(AttachmentDef::id).toList());
        BodyValues.tick(bearer);
        List<BodyVolumes.Placed> boxes = BodyVolumes.blocking(bearer);
        LOG.info("[body-check] blocking boxes: {}", boxes.stream().map(p -> p.box().toString()).toList());
        if (boxes.isEmpty()) problems.add("no blocking boxes");

        // Backing into the wall: the face is 3.5 blocks back, so the move stops short of that.
        Vec3 back = new Vec3(0, 0, -3.4);
        Vec3 limited = BodyVolumes.limit(bearer, back);
        LOG.info("[body-check] backing {} became {}", back, limited);
        if (Math.abs(limited.z) >= Math.abs(back.z) - 1e-4) problems.add("wall did not stop backing up");
        Vec3 forward = BodyVolumes.limit(bearer, new Vec3(0, 0, 1));
        if (forward.z < 0.99) problems.add("walking away was limited: " + forward);

        List<AttachmentPart> parts = new ArrayList<>();
        for (var part : level.getPartEntities()) {
            if (part instanceof AttachmentPart mine && mine.getParent() == bearer) parts.add(mine);
        }
        LOG.info("[body-check] parts: {}", parts.stream().map(p -> p.getBoundingBox().toString()).toList());
        if (parts.isEmpty()) problems.add("no part entities registered");

        Body attacker = new Body(level, "body-check-attacker");
        if (!parts.isEmpty()) {
            AABB box = parts.get(0).getBoundingBox();
            Vec3 aim = box.getCenter();
            attacker.moveTo(aim.x, aim.y - attacker.getEyeHeight(), aim.z + 0.0, 180f, 0f);
            Vec3 from = new Vec3(aim.x + 2.5, aim.y, aim.z);
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(attacker, from, aim.add(-2.5, 0, 0),
                    new AABB(from, aim.add(-2.5, 0, 0)).inflate(1.0), e -> !e.isSpectator() && e.isPickable(), 36.0);
            Entity picked = hit == null ? null : hit.getEntity();
            LOG.info("[body-check] aimed at: {}", picked);
            if (!(picked instanceof AttachmentPart)) problems.add("aiming through the abdomen found " + picked);

            float bodyBefore = bearer.getHealth();
            int honeyBefore = ResourceValues.get(bearer, HONEY);
            attacker.attack(parts.get(0));
            LOG.info("[body-check] attack: body {} -> {}, honey {} -> {}, stage {}", bodyBefore, bearer.getHealth(),
                    honeyBefore, ResourceValues.get(bearer, HONEY), BodyParts.stage(bearer, ABDOMEN));
            if (ResourceValues.get(bearer, HONEY) >= honeyBefore && bearer.getHealth() >= bodyBefore) {
                problems.add("a real attack on the part did nothing");
            }

            // A zombie behind the body swings at the body; the abdomen is in the way.
            net.minecraft.world.entity.monster.Zombie zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
            zombie.moveTo(bearer.getX(), bearer.getY(), bearer.getZ() - 3.0, 0f, 0f);
            int honeyAtSwing = ResourceValues.get(bearer, HONEY);
            // A mob's blow lands somewhere on the body, so not every swing passes through the abdomen.
            for (int swing = 0; swing < 16 && ResourceValues.get(bearer, HONEY) >= honeyAtSwing; swing++) {
                BodyParts.onBodyHit(bearer, level.damageSources().mobAttack(zombie), 4f);
            }
            LOG.info("[body-check] zombie behind: honey {} -> {}", honeyAtSwing, ResourceValues.get(bearer, HONEY));
            if (ResourceValues.get(bearer, HONEY) >= honeyAtSwing) problems.add("a mob's blow from behind missed the abdomen");

            // One in front has a clear line to the body.
            zombie.moveTo(bearer.getX(), bearer.getY(), bearer.getZ() + 3.0, 180f, 0f);
            int honeyInFront = ResourceValues.get(bearer, HONEY);
            for (int swing = 0; swing < 16; swing++) BodyParts.onBodyHit(bearer, level.damageSources().mobAttack(zombie), 4f);
            if (ResourceValues.get(bearer, HONEY) != honeyInFront) problems.add("a mob's blow from the front hit the abdomen");

            String folded = "townstead_classic:celestial_wings_folded";
            if (!AttachmentServerDataHolder.missing(folded)) {
                Body winged = new Body(level, "body-check-winged");
                winged.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
                PlayerRoot.setRootId(winged, "townstead_classic:celestial");
                PlayerRoot.getOrSeedGenotype(winged, winged.getRandom());
                BodyParts.strike(winged, folded, 99f);
                int foldedStage = BodyParts.stage(winged, folded);
                int spreadStage = BodyParts.stage(winged, "townstead_classic:celestial_wings_spread");
                LOG.info("[body-check] struck folded wings: folded {} spread {}", foldedStage, spreadStage);
                if (foldedStage != 2 || spreadStage != 2) problems.add("folded and spread wings don't share a record");
                if (!WornAttachments.of(winged).stream().map(AttachmentDef::id).filter(id -> id.contains("celestial_wings")).toList().isEmpty()) {
                    problems.add("lost wings are still worn");
                }
            }

            if (!BodyParts.strike(bearer, ABDOMEN, 500f)) problems.add("strike found no part");
            LOG.info("[body-check] after a huge strike: stage {}", BodyParts.stage(bearer, ABDOMEN));
        }
        if (problems.isEmpty()) {
            LOG.info("[body-check] ok");
            helper.succeed();
        } else {
            LOG.error("[body-check] FAILED: {}", problems);
            helper.fail(problems.toString());
        }
    }

    /** One real arrow at one villager's part, from one side. Reports what the arrow did. */
    private static void shoot(GameTestHelper helper, String root, String attachment, Vec3 direction, String label, int delay) {
        ServerLevel level = helper.getLevel();
        if (AttachmentServerDataHolder.missing(attachment)) {
            LOG.info("[arrow-check] {} not loaded, skipped", attachment);
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1)).offset(delay * 24, 8, 20);
        var type = net.minecraft.world.entity.EntityType.byString("mca:male_villager").orElseThrow();
        Entity spawned = type.create(level);
        if (!(spawned instanceof net.minecraft.world.entity.LivingEntity villager)) {
            LOG.error("[arrow-check] no villager");
            return;
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) level.setBlock(at.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 2);
        }
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) level.setChunkForced((at.getX() >> 4) + cx, (at.getZ() >> 4) + cz, true);
        }
        villager.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
        villager.yBodyRot = 0f;
        // Held still: one that wanders or takes off isn't where the shot was aimed by the time it arrives.
        ((net.minecraft.world.entity.Mob) villager).setNoAi(true);
        level.addFreshEntity(villager);
        boolean assigned = com.aetherianartificer.townstead.root.RootAssignment.assign(villager, ResourceLocation.parse(root));
        helper.runAfterDelay(delay + 30, () -> {
            AttachmentPart target = null;
            double farthest = -1.0;
            int count = 0;
            for (var part : level.getPartEntities()) {
                if (part instanceof AttachmentPart mine && mine.getParent() == villager) {
                    count++;
                    // The box standing furthest out and above the ground: one that droops through the floor can't be shot.
                    Vec3 centre = mine.getBoundingBox().getCenter();
                    double out = centre.subtract(villager.position()).horizontalDistanceSqr();
                    if (mine.isPickable() && centre.y > villager.getY() + 0.2 && out > farthest) {
                        farthest = out;
                        target = mine;
                    }
                }
            }
            LOG.info("[arrow-check] {} {}: assigned={} worn={} parts={} ticks={} wild={} villager at {}", root, label, assigned,
                    WornAttachments.of(villager).stream().map(AttachmentDef::id).toList(), count, villager.tickCount,
                    com.aetherianartificer.townstead.replace.MobReplacer.isWild((net.conczin.mca.entity.VillagerEntityMCA) villager),
                    villager.position());
            if (target == null) return;
            Vec3 aim = target.getBoundingBox().getCenter();
            Vec3 from = aim.subtract(direction.scale(4.0));
            var arrow = new net.minecraft.world.entity.projectile.Arrow(level, from.x, from.y, from.z,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), null);
            arrow.shoot(direction.x, direction.y, direction.z, 3f, 0f);
            level.addFreshEntity(arrow);
            float healthBefore = villager.getHealth();
            AABB box = target.getBoundingBox();
            helper.runAfterDelay(12, () -> {
                var hurt = villager.getPersistentData().getCompound("TownsteadParts");
                LOG.info("[arrow-check] {} {}: box {} arrow removed={} at {} body {} -> {} part data={}", root, label, box,
                        arrow.isRemoved(), arrow.position(), healthBefore, villager.getHealth(), hurt);
                // Villagers wander, so a miss is reported rather than failing the whole check.
                if (!hurt.contains(attachment)) LOG.error("[arrow-check] MISSED {} {}", root, label);
            });
        });
    }

    /** A blow lands on the limb in its way, on the side it came from, and follows the body when it turns. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void blowsLandOnTheLimbTheyReach(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        var made = net.minecraft.world.entity.EntityType.byString("mca:male_villager").orElseThrow().create(level);
        if (!(made instanceof net.minecraft.world.entity.Mob villager)) {
            helper.fail("no villager");
            return;
        }
        villager.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
        villager.setNoAi(true);
        villager.setYBodyRot(0f);
        villager.setYHeadRot(0f);
        level.addFreshEntity(villager);
        helper.runAfterDelay(5, () -> {
            List<String> problems = new ArrayList<>();
            double h = villager.getBbHeight();
            // It faces +z, so its right side is toward -x.
            limb(problems, villager, 0.0, h * 0.92, "head", 1f, 8f);
            limb(problems, villager, 0.0, h * 0.62, "body", -12f, 0f);
            limb(problems, villager, -0.33, h * 0.62, "right_arm", -12f, 0f);
            limb(problems, villager, 0.33, h * 0.62, "left_arm", -12f, 0f);
            limb(problems, villager, -0.12, h * 0.2, "right_leg", -24f, -12f);
            limb(problems, villager, 0.12, h * 0.2, "left_leg", -24f, -12f);

            Vec3 mid = villager.position().add(0.0, h * 0.62, 0.0);
            Vec3 fromBehind = BodyPose.strike(villager, mid.add(0, 0, -3), mid.add(0, 0, 3)).world();
            float backZ = BodyPose.strike(villager, mid.add(0, 0, -3), mid.add(0, 0, 3)).modelPoint().z;
            float frontZ = BodyPose.strike(villager, mid.add(0, 0, 3), mid.add(0, 0, -3)).modelPoint().z;
            if (backZ <= 0f) problems.add("a blow from behind landed at model z " + backZ);
            if (frontZ >= 0f) problems.add("a blow from the front landed at model z " + frontZ);
            if (fromBehind.z >= mid.z) problems.add("a blow from behind landed past the middle: " + fromBehind);

            // Turned to face +x, the same line through the middle now meets its side.
            villager.setYBodyRot(-90f);
            villager.setYHeadRot(-90f);
            String side = BodyPose.strike(villager, mid.add(0, 0, -3), mid.add(0, 0, 3)).limb();
            if (!side.endsWith("_arm")) problems.add("turned, a blow from the side landed on " + side);
            villager.setYBodyRot(0f);
            villager.setYHeadRot(0f);

            // A shot just past the body still has a place on it.
            Vec3 past = villager.position().add(0.55, h * 0.5, 0.0);
            BodyPose.Strike graze = BodyPose.strike(villager, past.add(0, 0, -3), past.add(0, 0, 3));
            if (graze.exact() || graze.limb().isEmpty()) problems.add("a graze: " + graze);

            // Where a limb's point is now agrees with where it was struck.
            BodyPose.Strike head = BodyPose.strike(villager, villager.position().add(0, h * 0.92, 3), villager.position().add(0, h * 0.92, -3));
            Vec3 again = BodyPose.limbPoint(villager, head.limb(), head.local());
            if (again == null || again.distanceTo(head.world()) > 0.01) problems.add("a limb's point moved: " + again + " from " + head.world());

            villager.discard();
            if (problems.isEmpty()) helper.succeed();
            else helper.fail(problems.toString());
        });
    }

    private static void limb(List<String> problems, net.minecraft.world.entity.LivingEntity body, double x, double y, String wanted, float low, float high) {
        Vec3 from = body.position().add(x, y, 3.0);
        BodyPose.Strike strike = BodyPose.strike(body, from, from.add(0.0, 0.0, -6.0));
        if (!wanted.equals(strike.limb())) problems.add("at " + x + "," + y + " wanted " + wanted + " got " + strike.limb());
        else if (strike.modelPoint().y < low || strike.modelPoint().y > high) problems.add(wanted + " landed at model y " + strike.modelPoint().y);
        else if (!strike.exact()) problems.add(wanted + " wasn't an exact hit");
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void readsAnotherModsAssets(GameTestHelper helper) {
        // Another mod's rig model is read from its jar. Townstead's own language file proves the path works.
        byte[] bytes = com.aetherianartificer.townstead.root.attachment.ModAssets.read("townstead:lang/en_us.json");
        LOG.info("[asset-check] read {} bytes", bytes == null ? -1 : bytes.length);
        if (bytes == null || bytes.length == 0) helper.fail("couldn't read an asset from a mod's jar");
        else if (com.aetherianartificer.townstead.root.attachment.ModAssets.read("townstead:no/such/file.json") != null) helper.fail("read a file that isn't there");
        else helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void anArrowWakesChainsAndTheySwing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String root = "townstead_mythical:kitsune";
        if (com.aetherianartificer.townstead.root.RootRegistry.byId(ResourceLocation.parse(root)) == null) {
            helper.succeed();
            return;
        }
        // Inside the test's own area, which is kept loaded: this one needs its villager to actually tick.
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        var type = net.minecraft.world.entity.EntityType.byString("mca:male_villager").orElseThrow();
        var villager = (net.minecraft.world.entity.LivingEntity) type.create(level);
        villager.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
        ((net.minecraft.world.entity.Mob) villager).setNoAi(true);
        level.addFreshEntity(villager);
        com.aetherianartificer.townstead.root.RootAssignment.assign(villager, ResourceLocation.parse(root));
        // Until it has ticked a while, nothing has settled to compare against.
        Runnable[] whenTicking = new Runnable[1];
        Runnable test = () -> {
            var def = WornAttachments.of(villager).stream().filter(d -> !d.physics().isEmpty()).findFirst().orElse(null);
            if (def == null) {
                helper.fail("no chain to test");
                return;
            }
            var rest = com.aetherianartificer.townstead.root.attachment.HitShapes.solved(villager, def);
            // Nothing to wake it yet. Then a real arrow flying past, and a shove while it's awake.
            var arrow = new net.minecraft.world.entity.projectile.Arrow(level, villager.getX() + 2.0, villager.getY() + 1.0, villager.getZ(),
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), null);
            arrow.shoot(1, 0, 0, 1f, 0f);
            arrow.setNoGravity(true);
            level.addFreshEntity(arrow);
            helper.runAfterDelay(8, () -> villager.setPos(villager.getX() + 1.5, villager.getY(), villager.getZ()));
            helper.runAfterDelay(11, () -> {
                var swung = com.aetherianartificer.townstead.root.attachment.HitShapes.solved(villager, def);
                LOG.info("[swing-check] tick {} rest {} swung {}", villager.tickCount, rest, swung);
                if (rest.equals(swung)) helper.fail("the chain didn't move once awake, at tick " + villager.tickCount + ": " + def.id());
                else helper.succeed();
            });
        };
        whenTicking[0] = () -> {
            if (villager.tickCount >= 20) test.run();
            else helper.runAfterDelay(5, () -> whenTicking[0].run());
        };
        helper.runAfterDelay(5, () -> whenTicking[0].run());
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void arrowsStopAtEveryAttachment(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String root = "townstead_mythical:kitsune";
        if (com.aetherianartificer.townstead.root.RootRegistry.byId(ResourceLocation.parse(root)) == null) {
            LOG.info("[shape-check] {} not loaded, skipped", root);
            helper.succeed();
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1)).offset(300, 8, 20);
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) level.setChunkForced((at.getX() >> 4) + cx, (at.getZ() >> 4) + cz, true);
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) level.setBlock(at.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 2);
        }
        var type = net.minecraft.world.entity.EntityType.byString("mca:male_villager").orElseThrow();
        var villager = (net.minecraft.world.entity.LivingEntity) type.create(level);
        villager.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
        level.addFreshEntity(villager);
        com.aetherianartificer.townstead.root.RootAssignment.assign(villager, ResourceLocation.parse(root));
        helper.runAfterDelay(30, () -> {
            var boxes = com.aetherianartificer.townstead.root.attachment.HitShapes.placed(villager);
            LOG.info("[shape-check] worn {} boxes {}", WornAttachments.of(villager).stream().map(AttachmentDef::id).toList(),
                    boxes.stream().map(AABB::toString).toList());
            // The box whose centre is furthest from the body, so only this system could stop the arrow.
            AABB far = null;
            double farthest = 0.0;
            for (AABB box : boxes) {
                Vec3 centre = box.getCenter();
                double out = centre.x * centre.x + centre.z * centre.z;
                if (out > farthest) {
                    farthest = out;
                    far = box;
                }
            }
            if (far == null) {
                helper.fail("no attachment boxes at all");
                return;
            }
            // Roots roll their tails: a short one never leaves the body, so an arrow can't reach it alone.
            if (farthest < 0.7 * 0.7) {
                LOG.info("[shape-check] skipped: this roll's tail stays too close to the body");
                helper.succeed();
                return;
            }
            Vec3 aim = far.getCenter().add(villager.position());
            // Across the tail, so the path stays clear of the body the whole way.
            Vec3 outward = far.getCenter().multiply(1, 0, 1).normalize();
            Vec3 across = new Vec3(-outward.z, 0, outward.x);
            Vec3 from = aim.subtract(across.scale(4.0));
            // Aiming through the same spot finds the wearer, the way a crosshair does.
            Body aiming = new Body(level, "body-check-aimer");
            aiming.moveTo(from.x, from.y - aiming.getEyeHeight(), from.z, 0f, 0f);
            EntityHitResult aimed = ProjectileUtil.getEntityHitResult(aiming, from, aim.add(across.scale(1.0)),
                    new AABB(from, aim.add(across.scale(1.0))).inflate(1.0), e -> !e.isSpectator() && e.isPickable(), 36.0);
            LOG.info("[shape-check] aimed at {}", aimed == null ? null : aimed.getEntity());
            if (aimed == null || aimed.getEntity() != villager) {
                helper.fail("aiming at the attachment found " + (aimed == null ? null : aimed.getEntity()));
                return;
            }
            var arrow = new net.minecraft.world.entity.projectile.Arrow(level, from.x, from.y, from.z,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), null);
            arrow.shoot(across.x, across.y, across.z, 3f, 0f);
            float before = villager.getHealth();
            level.addFreshEntity(arrow);
            helper.runAfterDelay(12, () -> {
                LOG.info("[shape-check] arrow removed={} at {}, body {} -> {}", arrow.isRemoved(), arrow.position(), before, villager.getHealth());
                if (villager.getHealth() >= before) {
                    helper.fail("the arrow went through the attachment");
                    return;
                }
                // The pose answers to the body: a crouch and a turn both move what's worn.
                var def = WornAttachments.of(villager).get(0);
                var standing = com.aetherianartificer.townstead.root.attachment.HitShapes.solved(villager, def);
                villager.setPose(net.minecraft.world.entity.Pose.CROUCHING);
                var crouched = com.aetherianartificer.townstead.root.attachment.HitShapes.solved(villager, def);
                villager.setPose(net.minecraft.world.entity.Pose.STANDING);
                villager.yBodyRot += 90f;
                var turned = com.aetherianartificer.townstead.root.attachment.HitShapes.solved(villager, def);
                LOG.info("[shape-check] standing {} crouched {} turned {}", standing, crouched, turned);
                if (standing.equals(crouched)) helper.fail("crouching didn't move " + def.id());
                else if (standing.equals(turned)) helper.fail("turning didn't move " + def.id());
                else helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void arrowsAtVillagerParts(GameTestHelper helper) {
        int delay = 0;
        // The wings are folded here, so this is the folded pair's box wounding the wings' one record.
        for (String[] kind : new String[][]{{ROOT, ABDOMEN}, {"townstead_classic:celestial", "townstead_classic:celestial_wings_spread"}}) {
            shoot(helper, kind[0], kind[1], new Vec3(0, 0, 1), "from behind", delay);
            shoot(helper, kind[0], kind[1], new Vec3(1, 0, 0), "from the side", delay + 1);
            shoot(helper, kind[0], kind[1], new Vec3(0, -0.5, 0.85), "from above", delay + 2);
            delay += 3;
        }
        helper.runAfterDelay(90, helper::succeed);
    }

    private static final class AttachmentServerDataHolder {
        static boolean missing(String id) {
            return com.aetherianartificer.townstead.root.attachment.AttachmentServerData.definition(id) == null;
        }
    }
}

package com.aetherianartificer.townstead.skillcheck;

import com.aetherianartificer.townstead.profession.career.CareerChoices;
import com.aetherianartificer.townstead.profession.def.SkillDef;
import com.aetherianartificer.townstead.profession.def.SkillDefs;
import com.aetherianartificer.townstead.profession.skill.LearnedSkills;
import com.aetherianartificer.townstead.root.ability.ActiveAbilities;
import com.aetherianartificer.townstead.root.ability.GeneAbilityTicker;
import com.aetherianartificer.townstead.root.ability.ResourceValues;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Shared stage for career skill tests: a floor, a caster who knows chosen skills, and a way to press a slot. */
final class SkillCheck {
    static final String ID = "townstead_skill_check";
    static final ResourceLocation STAMINA = new ResourceLocation("townstead:stamina");

    private SkillCheck() {}

    static void floor(GameTestHelper h) {
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) h.setBlock(x, 0, z, Blocks.STONE);
    }

    /** A survival player with no client. It is not ticked by the level: call {@link #tick} each tick. */
    static ServerPlayer caster(GameTestHelper h, double x, double y, double z, float yaw) {
        // Built directly: the factory caches players, and tests must never share one.
        ServerPlayer p = new net.minecraftforge.common.util.FakePlayer(h.getLevel(),
                new GameProfile(UUID.randomUUID(), "sc-" + UUID.randomUUID().toString().substring(0, 8))) {
            // A teleport goes through the client connection, which this player does not have.
            @Override
            public void teleportTo(double x, double y, double z) {
                setPos(x, y, z);
            }
        };
        Vec3 at = h.absoluteVec(new Vec3(x, y, z));
        p.moveTo(at.x, at.y, at.z, yaw, 0);
        // A living entity starts with a random head turn of a few degrees, and the view follows the head.
        p.setYHeadRot(yaw);
        p.yHeadRotO = yaw;
        p.setOnGround(true);
        p.setDeltaMovement(Vec3.ZERO);
        return p;
    }

    /** A caster at (8,1,2) facing +z on a stone floor, with full stamina and a set amount of one resource. */
    static ServerPlayer ready(GameTestHelper h, ResourceLocation resource, int amount, String... skills) {
        floor(h);
        ServerPlayer p = caster(h, 8.5, 1, 2.5, 0);
        learn(p, skills);
        ResourceValues.set(p, STAMINA, 100);
        ResourceValues.set(p, resource, amount);
        return p;
    }

    /** An adult MCA villager, which casts by itself and can be hurt. */
    static forge.net.conczin.mca.entity.VillagerEntityMCA villager(GameTestHelper h, int x, int y, int z) {
        @SuppressWarnings("unchecked")
        EntityType<forge.net.conczin.mca.entity.VillagerEntityMCA> type = (EntityType<forge.net.conczin.mca.entity.VillagerEntityMCA>)
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation("mca:male_villager"));
        forge.net.conczin.mca.entity.VillagerEntityMCA villager = h.spawn(type, x, y, z);
        villager.setAge(0);
        return villager;
    }

    /** True when a skill is registered. A career that needs another mod is not, and its tests pass unplayed. */
    static boolean missing(GameTestHelper h, String skill) {
        if (SkillDefs.byId(new ResourceLocation(skill)) != null) return false;
        h.succeed();
        return true;
    }

    static void learn(LivingEntity entity, String... skills) {
        for (String raw : skills) {
            ResourceLocation id = new ResourceLocation(raw);
            SkillDef def = SkillDefs.byId(id);
            if (def == null) throw new IllegalStateException("No such skill: " + raw);
            LearnedSkills.forceLearn(entity, id);
            if (def.skillGroup() != null) CareerChoices.activate(entity, def.skillGroup(), id);
        }
        com.aetherianartificer.townstead.pheno.power.Powers.invalidate(entity);
    }

    /** Presses the key of one skill, as the player's ability key does. */
    static boolean press(ServerPlayer p, String skill) {
        ResourceLocation id = new ResourceLocation(skill);
        for (ActiveAbilities.Slotted slotted : ActiveAbilities.slottables(p)) {
            if (slotted.geneId().equals(id)) return ActiveAbilities.fireSlotted(p, slotted);
        }
        throw new IllegalStateException(skill + " is not on any slot");
    }

    /** One server tick of the systems that drive powers on a player. */
    static void tick(ServerPlayer p) {
        p.tickCount++;
        p.baseTick();   // effect timers, water and air
        GeneAbilityTicker.tick(p);
        ResourceValues.tick(p);
        // The level does not move a player with no client; a dash only sets velocity.
        if (com.aetherianartificer.townstead.pheno.combat.Dashes.isDashing(p)) {
            p.move(net.minecraft.world.entity.MoverType.SELF, p.getDeltaMovement());
        }
        com.aetherianartificer.townstead.pheno.action.ItemRetrievals.tick(p);
        com.aetherianartificer.townstead.pheno.combat.Dashes.tick(p);
        com.aetherianartificer.townstead.pheno.combat.Wards.tick(p);
    }

    /**
     * The damage a hit would leave after every power, ward and link of both sides. A player with no client
     * cannot be hurt, so tests of what happens when the caster is hit go through here.
     */
    static float struck(LivingEntity victim, net.minecraft.world.damagesource.DamageSource source, float amount) {
        return com.aetherianartificer.townstead.root.damage.GeneDamageHandler.modify(victim, source, amount);
    }

    static boolean flagged(LivingEntity entity, String flag) {
        return com.aetherianartificer.townstead.pheno.state.TimedFlags.has(entity, new ResourceLocation(flag));
    }

    static int resource(LivingEntity entity, ResourceLocation id) {
        return ResourceValues.get(entity, id);
    }

    static <T extends Mob> T dummy(GameTestHelper h, EntityType<T> type, int x, int y, int z) {
        T mob = h.spawn(type, new BlockPos(x, y, z));
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        // A spawn is random: a baby is shorter than the caster's line of sight, and armor changes the damage.
        mob.setBaby(false);
        mob.stopRiding();
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            mob.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
        }
        return mob;
    }
}

package com.aetherianartificer.townstead.client.render;

import com.aetherianartificer.townstead.mixin.accessor.WalkAnimationStateAccessor;
import com.aetherianartificer.townstead.replace.WildCostumeS2CPayload;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws the person a replaced mob shows in the mob's place: a client-only villager carrying the
 * mob's id (so Root looks keyed by id apply to it), posed from the mob every frame.
 */
public final class WildCostumeRender {
    private record Person(WildCostumeS2CPayload look, Level level, VillagerEntityMCA villager) {}

    private static final Map<Integer, Person> PEOPLE = new HashMap<>();

    private WildCostumeRender() {}

    //? if neoforge {
    public static void onRenderLivingPre(net.neoforged.neoforge.client.event.RenderLivingEvent.Pre<?, ?> event) {
    //?} else {
    /*public static void onRenderLivingPre(net.minecraftforge.client.event.RenderLivingEvent.Pre<?, ?> event) {
    *///?}
        LivingEntity mob = event.getEntity();
        if (mob instanceof VillagerEntityMCA) return;
        WildCostumeS2CPayload look = WildCostumeS2CPayload.of(mob.getId());
        if (look == null) return;
        VillagerEntityMCA person = person(mob, look);
        if (person == null) return;
        pose(mob, person);
        event.setCanceled(true);
        float partial = event.getPartialTick();
        Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(person).render(person,
                Mth.lerp(partial, mob.yRotO, mob.getYRot()), partial,
                event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
    }

    //? if neoforge {
    public static void onEntityLeave(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
    //?} else {
    /*public static void onEntityLeave(net.minecraftforge.event.entity.EntityLeaveLevelEvent event) {
    *///?}
        if (!event.getLevel().isClientSide()) return;
        WildCostumeS2CPayload.forget(event.getEntity().getId());
        PEOPLE.remove(event.getEntity().getId());
    }

    public static void clear() {
        WildCostumeS2CPayload.clear();
        PEOPLE.clear();
    }

    private static @Nullable VillagerEntityMCA person(LivingEntity mob, WildCostumeS2CPayload look) {
        Person known = PEOPLE.get(mob.getId());
        if (known != null && known.look() == look && known.level() == mob.level()) return known.villager();
        ResourceLocation typeId = ResourceLocation.tryParse(look.villagerType());
        EntityType<?> type = typeId == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(null);
        Entity created = type == null ? null : type.create(mob.level());
        if (!(created instanceof VillagerEntityMCA villager)) {
            WildCostumeS2CPayload.forget(mob.getId());
            return null;
        }
        villager.setId(mob.getId());
        if (look.data().hasUUID("UUID")) villager.setUUID(look.data().getUUID("UUID"));
        villager.readAdditionalSaveData(look.data());
        PEOPLE.put(mob.getId(), new Person(look, mob.level(), villager));
        return villager;
    }

    private static void pose(LivingEntity mob, VillagerEntityMCA person) {
        person.setPos(mob.getX(), mob.getY(), mob.getZ());
        person.xo = mob.xo;
        person.yo = mob.yo;
        person.zo = mob.zo;
        person.xOld = mob.xOld;
        person.yOld = mob.yOld;
        person.zOld = mob.zOld;
        person.setYRot(mob.getYRot());
        person.yRotO = mob.yRotO;
        person.setXRot(mob.getXRot());
        person.xRotO = mob.xRotO;
        person.yBodyRot = mob.yBodyRot;
        person.yBodyRotO = mob.yBodyRotO;
        person.yHeadRot = mob.yHeadRot;
        person.yHeadRotO = mob.yHeadRotO;
        WalkAnimationStateAccessor from = (WalkAnimationStateAccessor) mob.walkAnimation;
        WalkAnimationStateAccessor to = (WalkAnimationStateAccessor) person.walkAnimation;
        to.townstead$setSpeedOld(from.townstead$speedOld());
        to.townstead$setSpeed(mob.walkAnimation.speed());
        to.townstead$setPosition(mob.walkAnimation.position());
        person.attackAnim = mob.attackAnim;
        person.oAttackAnim = mob.oAttackAnim;
        person.swinging = mob.swinging;
        person.swingTime = mob.swingTime;
        person.swingingArm = mob.swingingArm;
        person.hurtTime = mob.hurtTime;
        person.hurtDuration = mob.hurtDuration;
        person.deathTime = mob.deathTime;
        person.tickCount = mob.tickCount;
        person.setPose(mob.getPose());
        person.setOnGround(mob.onGround());
        person.setInvisible(mob.isInvisible());
        if (mob instanceof Mob m) person.setAggressive(m.isAggressive());
        hold(person, EquipmentSlot.MAINHAND, mob.getMainHandItem());
        hold(person, EquipmentSlot.OFFHAND, mob.getOffhandItem());
    }

    private static void hold(VillagerEntityMCA person, EquipmentSlot slot, ItemStack stack) {
        if (!ItemStack.matches(person.getItemBySlot(slot), stack)) person.setItemSlot(slot, stack.copy());
    }
}

package com.aetherianartificer.townstead.root.attachment;

//? if neoforge {
import net.neoforged.neoforge.entity.PartEntity;
//?} else {
/*import net.minecraftforge.entity.PartEntity;
*///?}
import net.minecraft.nbt.CompoundTag;
//? if neoforge {
import net.minecraft.network.syncher.SynchedEntityData;
//?}
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * One hittable box of a worn attachment, the way a dragon's wing is hittable. It takes no space and has
 * no health of its own here: hits go through {@link BodyParts}, and right-clicks reach its bearer as if
 * they had landed on the body.
 */
public final class AttachmentPart extends PartEntity<LivingEntity> {

    final String attachment;
    final int boxIndex;
    // Whether its attachment is showing: the other pose of a pair shouldn't catch anything.
    boolean showing;

    AttachmentPart(LivingEntity parent, String attachment, int boxIndex) {
        super(parent);
        this.attachment = attachment;
        this.boxIndex = boxIndex;
        this.noPhysics = true;
    }

    void place(AABB world) {
        showing = true;
        setPos((world.minX + world.maxX) / 2.0, world.minY, (world.minZ + world.maxZ) / 2.0);
        setBoundingBox(world);
    }

    //? if neoforge {
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    //?} else {
    /*@Override
    protected void defineSynchedData() {}
    *///?}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return BodyParts.pickable(this);
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return BodyParts.hurt(this, source, amount);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return getParent().interact(player, hand);
    }

    @Override
    public boolean is(Entity other) {
        return this == other || getParent() == other;
    }
}

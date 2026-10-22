package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.replace.MobReplacer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Offers every finished spawn to {@link MobReplacer}, not only those that fire the loader's
 * finalize event: some mods call {@code finalizeSpawn} directly (Vampirism's camp tents).
 */
@Mixin(Mob.class)
public abstract class MobFinalizeSpawnCostumeMixin {
    //? if neoforge {
    @Inject(method = "finalizeSpawn", at = @At("RETURN"))
    private void townstead$costume(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType type,
                                   SpawnGroupData data, CallbackInfoReturnable<SpawnGroupData> cir) {
        MobReplacer.onFinalizeSpawn((Mob) (Object) this, level, type);
    }
    //?} else {
    /*@Inject(method = "m_6518_", remap = false, at = @At("RETURN"))
    private void townstead$costume(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType type,
                                   SpawnGroupData data, net.minecraft.nbt.CompoundTag tag,
                                   CallbackInfoReturnable<SpawnGroupData> cir) {
        MobReplacer.onFinalizeSpawn((Mob) (Object) this, level, type);
    }
    *///?}
}

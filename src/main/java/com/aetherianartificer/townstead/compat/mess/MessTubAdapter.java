package com.aetherianartificer.townstead.compat.mess;

import com.aetherianartificer.townstead.hangout.HangoutEmbodiment;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A Bit of a Mess washtub as a hangout spot: the villager sits in it through Bit of a Mess, which soaks them as it would a
 * player, and gets out when the visit ends. Without Bit of a Mess, or with the tub taken or dry, nobody sits.
 */
public final class MessTubAdapter implements HangoutEmbodiment.PostureAdapter {

    public static final ResourceLocation ID = ResourceLocation.tryParse("bitofamess:washtub");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public @Nullable HangoutEmbodiment.Handle enter(ServerLevel level, VillagerEntityMCA villager, BlockPos spot,
                                                    ResourceLocation posture, Vec3 position, UUID visitId) {
        if (!MessBridge.active()) return HangoutEmbodiment.BLOCKED;
        MessBridge.dressForBathing(villager);
        if (!MessBridge.sitInTub(villager, spot)) return HangoutEmbodiment.BLOCKED;
        return (where, who) -> {
            if (who.isPassenger()) who.stopRiding();
        };
    }
}

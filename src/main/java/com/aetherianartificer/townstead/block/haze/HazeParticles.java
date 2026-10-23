package com.aetherianartificer.townstead.block.haze;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import java.util.function.Supplier;
//? if neoforge {
import net.neoforged.neoforge.registries.DeferredRegister;
//?} else {
/*import net.minecraftforge.registries.DeferredRegister;
*///?}

/**
 * The soft puff a cloud haze is drawn with. Spawned only on the client, from the block's own
 * animation tick, so its tint and strength ride in the velocity arguments: x is the packed RGB
 * colour, y the cell's density as a fraction of full, and z is 1 for a concealing kind.
 */
public final class HazeParticles {
    public static final DeferredRegister<ParticleType<?>> TYPES = DeferredRegister.create(
            net.minecraft.core.registries.Registries.PARTICLE_TYPE, Townstead.MOD_ID);
    // Concealment is gameplay, so the cloud stays visible with Minimal particles.
    public static final Supplier<SimpleParticleType> PUFF = TYPES.register("haze_puff", () -> new SimpleParticleType(true));
    private HazeParticles() {}
}

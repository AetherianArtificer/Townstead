package com.aetherianartificer.townstead.fatigue;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import java.util.function.Supplier;
//? if neoforge {
import net.neoforged.neoforge.registries.DeferredRegister;
//?} else {
/*import net.minecraftforge.registries.DeferredRegister;
*///?}

public final class SleepParticles {
    public static final DeferredRegister<ParticleType<?>> TYPES = DeferredRegister.create(
            net.minecraft.core.registries.Registries.PARTICLE_TYPE, Townstead.MOD_ID);
    // A sparse gameplay status cue: keep it visible even with Minimal particles.
    public static final Supplier<SimpleParticleType> SLEEP = TYPES.register("sleep", () -> new SimpleParticleType(true));
    private SleepParticles() {}
}

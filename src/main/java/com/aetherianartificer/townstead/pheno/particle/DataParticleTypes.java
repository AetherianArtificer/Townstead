package com.aetherianartificer.townstead.pheno.particle;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.core.particles.ParticleType;
import java.util.function.Supplier;
//? if neoforge {
import net.neoforged.neoforge.registries.DeferredRegister;
//?} else {
/*import net.minecraftforge.registries.DeferredRegister;
*///?}

/**
 * The single registered particle type every data-pack particle is spawned through. Particle types
 * cannot be registered after startup, so data particles share this one and name their definition
 * in {@link DataParticleOptions}.
 */
public final class DataParticleTypes {
    public static final String DATA_ID = Townstead.MOD_ID + ":data";

    public static final DeferredRegister<ParticleType<?>> TYPES = DeferredRegister.create(
            net.minecraft.core.registries.Registries.PARTICLE_TYPE, Townstead.MOD_ID);

    //? if >=1.21 {
    public static final Supplier<ParticleType<DataParticleOptions>> DATA = TYPES.register("data",
            () -> new ParticleType<DataParticleOptions>(true) {
                @Override
                public com.mojang.serialization.MapCodec<DataParticleOptions> codec() {
                    return DataParticleOptions.CODEC;
                }

                @Override
                public net.minecraft.network.codec.StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf,
                        DataParticleOptions> streamCodec() {
                    return DataParticleOptions.STREAM_CODEC;
                }
            });
    //?} else {
    /*@SuppressWarnings("deprecation")
    public static final Supplier<ParticleType<DataParticleOptions>> DATA = TYPES.register("data",
            () -> new ParticleType<DataParticleOptions>(true, DataParticleOptions.DESERIALIZER) {
                @Override
                public com.mojang.serialization.Codec<DataParticleOptions> codec() {
                    return DataParticleOptions.CODEC;
                }
            });
    *///?}

    private DataParticleTypes() {}
}

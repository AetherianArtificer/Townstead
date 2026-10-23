package com.aetherianartificer.townstead.pheno.particle;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.ResourceLocation;

/**
 * Spawn options for {@code townstead:data}: which data-pack particle to draw. The particle's look
 * and behaviour come from its synced {@link DataParticleDef}, so only the id crosses the wire.
 */
public record DataParticleOptions(ResourceLocation id) implements ParticleOptions {

    //? if >=1.21 {
    public static final com.mojang.serialization.MapCodec<DataParticleOptions> CODEC =
            ResourceLocation.CODEC.fieldOf("id").xmap(DataParticleOptions::new, DataParticleOptions::id);
    public static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, DataParticleOptions> STREAM_CODEC =
            ResourceLocation.STREAM_CODEC.map(DataParticleOptions::new, DataParticleOptions::id);
    //?} else {
    /*public static final com.mojang.serialization.Codec<DataParticleOptions> CODEC =
            ResourceLocation.CODEC.xmap(DataParticleOptions::new, DataParticleOptions::id);

    @SuppressWarnings("deprecation")
    public static final ParticleOptions.Deserializer<DataParticleOptions> DESERIALIZER =
            new ParticleOptions.Deserializer<>() {
                @Override
                public DataParticleOptions fromCommand(ParticleType<DataParticleOptions> type,
                                                       com.mojang.brigadier.StringReader reader)
                        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
                    reader.expect(' ');
                    return new DataParticleOptions(ResourceLocation.read(reader));
                }

                @Override
                public DataParticleOptions fromNetwork(ParticleType<DataParticleOptions> type,
                                                       net.minecraft.network.FriendlyByteBuf buf) {
                    return new DataParticleOptions(buf.readResourceLocation());
                }
            };

    @Override
    public void writeToNetwork(net.minecraft.network.FriendlyByteBuf buf) {
        buf.writeResourceLocation(id);
    }

    @Override
    public String writeToString() {
        return DataParticleTypes.DATA_ID + " " + id;
    }
    *///?}

    @Override
    public ParticleType<?> getType() {
        return DataParticleTypes.DATA.get();
    }
}

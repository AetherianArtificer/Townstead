package com.aetherianartificer.townstead.compat.mca;

import net.conczin.mca.entity.VillagerLike;
//? if neoforge {
import com.aetherianartificer.townstead.mixin.accessor.GeneticsGenomesAccessor;
import net.conczin.mca.Config;
import net.conczin.mca.entity.ai.Genetics;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
//?}

/**
 * MCA's genome roll, safe on any level. On 1.21.1 MCA's {@code Genetics.randomize()} reads the sea
 * level from a ServerLevel only, so client previews (editor, JEP) roll here with the client
 * level's sea level instead.
 */
public final class McaGeneticsCompat {
    private McaGeneticsCompat() {}

    public static void randomize(VillagerLike<?> villager) {
        //? if neoforge {
        Entity entity = villager.asEntity();
        if (entity.level() instanceof ServerLevel) {
            villager.getGenetics().randomize();
            return;
        }
        Genetics genetics = villager.getGenetics();
        RandomSource random = entity.getRandom();
        for (Genetics.GeneType type : GeneticsGenomesAccessor.townstead$getGenomes()) {
            genetics.getGenome(type).randomize();
        }

        genetics.setGene(Genetics.SIZE, centeredRandom(random));
        genetics.setGene(Genetics.WIDTH, centeredRandom(random));

        float temp = entity.level().getBiome(entity.blockPosition()).value().getBaseTemperature();
        if (random.nextFloat() < Config.getInstance().geneticImmigrantChance) {
            temp = random.nextFloat() * 2 - 0.5F;
        }

        @SuppressWarnings("deprecation")
        float height = (entity.blockPosition().getY() - entity.level().getSeaLevel()) / 128f;

        genetics.setGene(Genetics.MELANIN, Mth.clamp(temperatureBaseRandom(random, temp) - height * 0.2f, 0, 1));
        genetics.setGene(Genetics.HEMOGLOBIN, Mth.clamp(temperatureBaseRandom(random, temp) * 0.5f + height * 0.5f, 0, 1));
        genetics.setGene(Genetics.EUMELANIN, random.nextFloat());
        genetics.setGene(Genetics.PHEOMELANIN, random.nextFloat());
        //?} else {
        /*villager.getGenetics().randomize();
        *///?}
    }

    //? if neoforge {
    private static float centeredRandom(RandomSource random) {
        return Math.min(1, Math.max(0, (random.nextFloat() - 0.5F) * (random.nextFloat() - 0.5F) + 0.5F));
    }

    private static float temperatureBaseRandom(RandomSource random, float temp) {
        return (random.nextFloat() - 0.5F) * 0.35F + temp * 0.4F + 0.1F;
    }
    //?}
}

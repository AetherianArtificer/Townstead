package com.aetherianartificer.townstead.compat.farmersdelight.cook;

final class RecipeScoring {
    private RecipeScoring() {}

    static double scoreRecipeModel(
            boolean beverage,
            int inputCount,
            int cookTimeTicks,
            boolean requiresTool,
            int containerCount,
            int tier,
            int recipeHash,
            double nutrition,
            double saturation,
            int currentStock,
            int chainDemand,
            double chainOpportunity,
            long cookSeed
    ) {
        double complexityScore = inputCount * 1.25d;
        complexityScore += Math.min(6.0d, cookTimeTicks / 80.0d);
        if (requiresTool) complexityScore += 0.75d;
        if (containerCount > 0) {
            complexityScore += containerCount * 0.6d;
        }
        complexityScore += Math.max(0, tier - 1) * 1.25d;

        double qualityScore = nutrition * 3.5d + saturation * 7.0d + complexityScore;
        if (beverage) {
            qualityScore += 10.0d;
        }

        double scarcityBonus = Math.max(0.0d, 8.0d - currentStock * 1.5d);
        double stockPenalty = currentStock * 2.5d;
        if (currentStock >= 3) {
            stockPenalty += (currentStock - 2) * 1.5d;
        }
        double chainBonus = Math.min(4.0d, chainDemand * 1.25d);
        double unlockBonus = Math.min(12.0d, chainOpportunity);

        double score = qualityScore + scarcityBonus + chainBonus + unlockBonus - stockPenalty;
        score += perCookJitter(cookSeed, recipeHash) * 1.5d;
        return score;
    }

    private static double perCookJitter(long cookSeed, int recipeHash) {
        long mix = cookSeed ^ recipeHash;
        long bucket = Math.floorMod(mix, 1000L);
        return bucket / 1000.0d;
    }
}

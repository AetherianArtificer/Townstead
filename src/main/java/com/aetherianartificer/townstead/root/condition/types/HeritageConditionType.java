package com.aetherianartificer.townstead.root.condition.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.root.Heritage;
import com.aetherianartificer.townstead.root.PlayerRoot;
import com.aetherianartificer.townstead.root.RootRegistry;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * True while the entity's bloodline share of an {@code ancestry} (or of a {@code lineage}) lies
 * within {@code min} and {@code max} (0 to 1; defaults 0 and 1). For traits a dilute line
 * shouldn't keep: egg-laying that needs at least half drake blood, a giant's frame that needs
 * three quarters. A player counts as their root's own heritage.
 *
 * <p>Heritage lives on the server, so use this in a gene's {@code condition} (decided there and
 * synced), not in a client-side attachment {@code when}; on the client it is always false.</p>
 *
 * <p>JSON: {@code { "type":"pheno:heritage", "ancestry":"my_pack:draconic", "min":0.5 }}</p>
 */
public final class HeritageConditionType implements ConditionType {

    public static final String KEY = "pheno:heritage";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        ResourceLocation ancestry = json.has("ancestry") ? DataPackLang.parseId(GsonHelper.getAsString(json, "ancestry", "")) : null;
        ResourceLocation lineage = json.has("lineage") ? DataPackLang.parseId(GsonHelper.getAsString(json, "lineage", "")) : null;
        if ((ancestry == null) == (lineage == null)) return null;
        float min = GsonHelper.getAsFloat(json, "min", 0f);
        float max = GsonHelper.getAsFloat(json, "max", 1f);
        return ctx -> {
            if (ctx.entity().level().isClientSide()) return false;
            Heritage heritage = heritageOf(ctx.entity());
            if (heritage == null) return false;
            float share = ancestry != null ? heritage.fractionOf(ancestry) : heritage.lineageFractionOf(lineage);
            return share >= min - 1e-4f && share <= max + 1e-4f;
        };
    }

    private static @Nullable Heritage heritageOf(LivingEntity entity) {
        if (entity instanceof VillagerEntityMCA villager) {
            var life = TownsteadVillagers.get(villager).life();
            if (life.hasHeritage()) return life.heritage();
            ResourceLocation root = ResourceLocation.tryParse(life.rootId());
            return root == null ? null : RootRegistry.seedHeritage(root);
        }
        if (entity instanceof Player player) return PlayerRoot.getHeritage(player);
        return null;
    }
}

package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Branch-agnostic server handling for {@link OriginSetC2SPayload}: resolves the
 * target (player self or villager), validates the origin against the registry,
 * applies it (player id, or villager id + a gene re-roll into the origin's
 * ranges), or answers a request. MCA's {@code Genetics} is SynchedEntityData, so
 * the re-roll syncs to tracking clients the same tick (no reload). The two
 * network branches only differ in how they ship the returned {@link Result}
 * back, so the decision logic lives here once.
 */
public final class OriginServerLogic {

    private OriginServerLogic() {}

    /** What to sync back to clients; {@code targetId == SELF} ⇒ the player. */
    public record Result(int targetId, String originId) {}

    /**
     * Apply the set (or answer the request) and return the value to sync, or
     * {@code null} if the request was invalid (unknown target/origin).
     */
    @Nullable
    public static Result applyOrRequest(ServerPlayer sp, int entityId, String originId) {
        boolean request = originId == null || originId.isEmpty();

        if (entityId == OriginSetC2SPayload.NONE) return null;

        if (entityId == OriginSetC2SPayload.SELF) {
            if (request) {
                return new Result(OriginSetC2SPayload.SELF, orDefault(PlayerOrigin.getOriginId(sp)));
            }
            ResourceLocation id = resolveKnown(originId);
            if (id == null) return null;
            PlayerOrigin.setOriginId(sp, id.toString());
            return new Result(OriginSetC2SPayload.SELF, id.toString());
        }

        Entity entity = sp.serverLevel().getEntity(entityId);
        if (!(entity instanceof VillagerEntityMCA villager)) return null;
        TownsteadVillager state = TownsteadVillagers.get(villager);

        if (request) {
            return new Result(villager.getId(), orDefault(state.life().originId()));
        }
        ResourceLocation id = resolveKnown(originId);
        if (id == null) return null;
        boolean changed = !id.toString().equals(state.life().originId());
        state.life().setOrigin(id.toString());
        // Reseed the diploid genotype + heritage to the new origin. MCA's *float*
        // genes (size/skin-tone) are committed separately by the editor's WYSIWYG
        // preview, but the diploid layer (ears, skin variant, chronotype, diet, the
        // Heritage screen, cosmetic expression) is origin-derived and lives only
        // server-side, so it must be rolled here or it stays empty/stale. Reroll only
        // on an actual change (or when none exists yet), so re-applying the same
        // origin doesn't randomize an existing villager — matching the picker preview.
        if (changed || !state.life().hasGenotype()) {
            Heredity.seedFounder(state.life(), id, villager.getRandom());
        }
        // Flush now: the origin lives in a data attachment that only persists when
        // the snapshot is written, and the periodic flush may not run before the
        // world saves/exits — which lost the origin (and so the skin tint) on reload.
        TownsteadVillagers.flush(villager);
        return new Result(villager.getId(), id.toString());
    }

    @Nullable
    private static ResourceLocation resolveKnown(String originId) {
        ResourceLocation id = DataPackLang.parseId(originId);
        return id != null && OriginRegistry.byId(id) != null ? id : null;
    }

    /** Treat an unset origin as the default (everyone is an Overworlder by default). */
    private static String orDefault(String originId) {
        return originId == null || originId.isEmpty() ? OriginRegistry.DEFAULT_ID.toString() : originId;
    }
}

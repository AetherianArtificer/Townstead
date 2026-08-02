package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.root.gene.Allele;
import com.aetherianartificer.townstead.root.gene.AllelePayload;
import com.aetherianartificer.townstead.root.gene.Gene;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneRegistry;
import com.aetherianartificer.townstead.root.gene.GeneVariant;
import com.aetherianartificer.townstead.root.gene.Genotype;
import com.aetherianartificer.townstead.root.gene.types.AttachmentGeneType;
import com.aetherianartificer.townstead.pheno.power.Power;
import com.aetherianartificer.townstead.pheno.power.Powers;
import com.aetherianartificer.townstead.root.ability.GeneAbilityTicker;
import com.aetherianartificer.townstead.root.attribute.GeneAttributeApplier;
import com.aetherianartificer.townstead.root.personality.PersonalityResolver;
import com.aetherianartificer.townstead.root.reproduction.Fertility;
import net.conczin.mca.entity.ai.relationship.Personality;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Branch-agnostic server handling for {@link RootSetC2SPayload}: resolves the
 * target (player self or villager), validates the origin against the registry,
 * applies it (player id, or villager id + a gene re-roll into the origin's
 * ranges), or answers a request. MCA's {@code Genetics} is SynchedEntityData, so
 * the re-roll syncs to tracking clients the same tick (no reload). The two
 * network branches only differ in how they ship the returned {@link Result}
 * back, so the decision logic lives here once.
 */
public final class RootServerLogic {

    private RootServerLogic() {}

    /** What to sync back to clients; {@code targetId == SELF} ⇒ the player. */
    public record Result(int targetId, String rootId) {}

    /**
     * Apply the set (or answer the request) and return the value to sync, or
     * {@code null} if the request was invalid (unknown target/origin).
     */
    @Nullable
    public static Result applyOrRequest(ServerPlayer sp, int entityId, String rootId) {
        boolean request = rootId == null || rootId.isEmpty();

        if (entityId == RootSetC2SPayload.NONE) return null;

        if (entityId == RootSetC2SPayload.SELF) {
            if (request) {
                return new Result(RootSetC2SPayload.SELF, orDefault(PlayerRoot.getRootId(sp)));
            }
            ResourceLocation id = resolveKnown(rootId);
            if (id == null) return null;
            boolean changed = !id.toString().equals(orDefault(PlayerRoot.getRootId(sp)));
            List<Power> oldGenes = changed ? new ArrayList<>(Powers.active(sp)) : List.of();
            PlayerRoot.setRootId(sp, id.toString());
            StartingEquipment.grant(sp);
            if (changed) resetPassives(sp, oldGenes);
            return new Result(RootSetC2SPayload.SELF, id.toString());
        }

        Entity entity = sp.serverLevel().getEntity(entityId);
        if (!(entity instanceof VillagerEntityMCA villager)) return null;
        TownsteadVillager state = TownsteadVillagers.get(villager);

        if (request) {
            return new Result(villager.getId(), orDefault(state.life().rootId()));
        }
        ResourceLocation id = resolveKnown(rootId);
        if (id == null) return null;
        boolean changed = !id.toString().equals(state.life().rootId());
        List<Power> oldGenes = changed ? new ArrayList<>(Powers.active(villager)) : List.of();
        state.life().setRoot(id.toString());
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
        // Roll a personality from the new origin's allowlist (the natural-spawn path does this too).
        // Also fill one in when the villager has none yet, so re-applying an origin to a pre-existing
        // villager grants the personality it should have had.
        if (changed || state.life().personalityId().isEmpty()) {
            RootSpawnHandler.assignPersonality(villager, state, id);
        }
        // Reseeding may have changed the fertility gene; keep MCA's infertile trait in step.
        Fertility.syncMcaInfertileTrait(villager);
        // Flush now: the origin lives in a data attachment that only persists when
        // the snapshot is written, and the periodic flush may not run before the
        // world saves/exits — which lost the origin (and so the skin tint) on reload.
        TownsteadVillagers.flush(villager);
        if (changed) resetPassives(villager, oldGenes);
        return new Result(villager.getId(), id.toString());
    }

    /**
     * Commit the editor preview's exact MCA float genes — the WYSIWYG roll an origin Apply
     * produced on the editor dummy. A villager gets them on its synced {@code Genetics}; the
     * player gets them written into the stored MCA player data and re-broadcast. Deliberately
     * NOT MCA's own editor save ({@code syncVillagerData}): that path also rewrites the
     * target's family-tree entry (gender, typed-in parents) and, for players, replaces the
     * whole stored MCA snapshot from stale editor-buffer keys — which erased player-set parent
     * names and broke gendered family dialogue on a root change.
     */
    public static void commitGenes(ServerPlayer sp, int entityId, float[] genes) {
        if (genes == null || genes.length != RootGenes.geneCount()) return;
        float[] clamped = new float[genes.length];
        for (int i = 0; i < genes.length; i++) {
            float v = genes[i];
            clamped[i] = Float.isNaN(v) ? 0.5f : Math.max(0f, Math.min(1f, v));
        }

        if (entityId == RootSetC2SPayload.SELF) {
            net.conczin.mca.server.world.data.PlayerSaveData data =
                    net.conczin.mca.server.world.data.PlayerSaveData.get(sp);
            net.minecraft.nbt.CompoundTag entityData = data.getEntityData();
            RootGenes.writeToPlayerData(entityData, clamped);
            data.setEntityDataSet(true);
            data.setDirty();
            // MCA renders players-as-villagers from this snapshot on every client; mirror its
            // own editor-save broadcast so the change shows without a relog.
            for (ServerPlayer p : sp.serverLevel().players()) {
                //? if neoforge {
                net.conczin.mca.network.Network.sendToPlayer(
                        new net.conczin.mca.network.s2c.PlayerDataMessage(sp.getUUID(), entityData), p);
                //?} else {
                /*net.conczin.mca.cobalt.network.NetworkHandler.sendToPlayer(
                        new net.conczin.mca.network.s2c.PlayerDataMessage(sp.getUUID(), entityData), p);
                *///?}
            }
            return;
        }

        Entity entity = sp.serverLevel().getEntity(entityId);
        if (!(entity instanceof VillagerEntityMCA villager)) return;
        RootGenes.restore(villager, clamped);
        // SIZE/WIDTH feed the hitbox; MCA's editor save refreshed it too.
        villager.refreshDimensions();
    }

    /**
     * Pin a target's carried allele payload for one gene (the editor's variant picker and
     * size-channel sliders — {@code variantId} is a full {@link AllelePayload} encoding:
     * a variant id, channel values, or both). The variant part must name a real option;
     * channel values are clamped to the gene's (or chosen variant's) declared ranges and
     * missing channels fill with their midpoint. Sets both genotype alleles to the result
     * so it expresses, persists, and inherits like a roll, then returns the resolved
     * entity id for re-sync, or {@link RootSetC2SPayload#NONE} if invalid.
     */
    public static int setVariant(ServerPlayer sp, int entityId, String geneId, String variantId) {
        ResourceLocation gid = DataPackLang.parseId(geneId);
        if (gid == null) return RootSetC2SPayload.NONE;
        Gene gene = GeneRegistry.byId(gid);
        if (gene == null) return RootSetC2SPayload.NONE;
        AllelePayload incoming = AllelePayload.parse(variantId);
        GeneInstance chosen = gene.instance();
        if (gene.hasVariants()) {
            GeneVariant match = null;
            for (GeneVariant v : gene.variants()) {
                if (v.id().equals(incoming.variant())) { match = v; break; }
            }
            if (match == null) return RootSetC2SPayload.NONE;
            chosen = match.instance();
        } else if (!incoming.variant().isEmpty()) {
            return RootSetC2SPayload.NONE;
        }
        List<AttachmentGeneType.Channel> channels =
                chosen instanceof AttachmentGeneType.Instance att ? att.channels() : List.of();
        if (channels.isEmpty() && !gene.hasVariants()) return RootSetC2SPayload.NONE;
        java.util.Map<String, Float> canonical = new java.util.LinkedHashMap<>();
        for (AttachmentGeneType.Channel channel : channels) {
            Float raw = incoming.channels().get(channel.name());
            if (raw == null && incoming.channels().size() == 1
                    && incoming.channels().containsKey(AllelePayload.LEGACY_CHANNEL)) {
                raw = incoming.channels().get(AllelePayload.LEGACY_CHANNEL);
            }
            float value = raw == null ? (channel.min() + channel.max()) / 2f : raw;
            canonical.put(channel.name(), Math.max(channel.min(), Math.min(channel.max(), value)));
        }
        Allele allele = Allele.of(gid, AllelePayload.encode(incoming.variant(), canonical));
        ResourceLocation locus = Heredity.locusOf(gene);

        // The editor reads, previews, and commits the EXPRESSED payload, so heritage-coupled
        // channels arrive pre-scaled; store the recovered raw roll or re-expression would
        // compound the factor (a hybrid's wings shrinking on every edit round trip).
        if (entityId == RootSetC2SPayload.SELF) {
            Genotype genotype = PlayerRoot.getGenotype(sp);
            if (genotype.isEmpty()) return RootSetC2SPayload.NONE;
            ResourceLocation rootId = ResourceLocation.tryParse(PlayerRoot.getRootId(sp));
            Allele stored = Heredity.unscaleByHeritage(allele,
                    RootRegistry.seedHeritage(rootId == null ? RootRegistry.DEFAULT_ID : rootId));
            genotype.set(locus, stored, stored);
            PlayerRoot.setGenotype(sp, genotype);
            return sp.getId();
        }
        Entity entity = sp.serverLevel().getEntity(entityId);
        if (!(entity instanceof VillagerEntityMCA villager)) return RootSetC2SPayload.NONE;
        TownsteadVillager state = TownsteadVillagers.get(villager);
        Allele stored = Heredity.unscaleByHeritage(allele,
                state.life().hasHeritage() ? state.life().heritage() : null);
        state.life().genotype().set(locus, stored, stored);
        Heredity.recomputeExpressed(state.life());
        // Pinning the fertility variant flips sterility; mirror it onto MCA's infertile trait.
        Fertility.syncMcaInfertileTrait(villager);
        TownsteadVillagers.flush(villager);
        return villager.getId();
    }

    /**
     * Set a villager's personality from the editor's dynamic picker. Stores the chosen ref on the Life
     * (drives display + voice) and sets the MCA brain personality to the base enum it maps to. Returns
     * the entity id so the caller can re-broadcast the life sync, or {@link RootSetC2SPayload#NONE}.
     */
    public static int setPersonality(ServerPlayer sp, int entityId, String ref) {
        Entity entity = sp.serverLevel().getEntity(entityId);
        if (!(entity instanceof VillagerEntityMCA villager)) return RootSetC2SPayload.NONE;
        TownsteadVillager state = TownsteadVillagers.get(villager);
        state.life().setPersonalityId(ref == null ? "" : ref);
        Personality base = PersonalityResolver.baseOf(ref);
        if (base != null) villager.getVillagerBrain().setPersonality(base);
        TownsteadVillagers.flush(villager);
        return villager.getId();
    }

    /**
     * Clear the old origin's lingering passive state on a change. The ability/attribute
     * tickers only ever undo a gene they are still iterating, so a gene that leaves the
     * expressed set would orphan its applied state (a stuck no-gravity/sprint flag, glow
     * tag, or attribute modifier). The convergent tickers re-apply the new origin's set
     * on their next pass.
     */
    private static void resetPassives(LivingEntity entity, List<Power> oldGenes) {
        GeneAbilityTicker.resetPassives(entity);
        GeneAttributeApplier.removeFor(entity, oldGenes);
    }

    @Nullable
    private static ResourceLocation resolveKnown(String rootId) {
        ResourceLocation id = DataPackLang.parseId(rootId);
        if (id == null || RootRegistry.byId(id) == null) return null;
        // The picker hides blocked roots; rejecting here keeps a modified client from applying one.
        return RootBlocklist.isBlocked(id) ? null : id;
    }

    /** Treat an unset origin as the default (everyone is an Overworlder by default). */
    private static String orDefault(String rootId) {
        return rootId == null || rootId.isEmpty() ? RootRegistry.DEFAULT_ID.toString() : rootId;
    }
}

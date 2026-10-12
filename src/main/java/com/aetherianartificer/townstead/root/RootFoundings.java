package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.clothing.BodyClothingResolver;
import com.aetherianartificer.townstead.clothing.policy.RigSkinPicker;
import com.aetherianartificer.townstead.clothing.policy.SkinPicker;
import com.aetherianartificer.townstead.pheno.power.Power;
import com.aetherianartificer.townstead.pheno.power.Powers;
import com.aetherianartificer.townstead.root.appearance.HairColors;
import com.aetherianartificer.townstead.root.appearance.HairResolver;
import com.aetherianartificer.townstead.root.appearance.HairSettings;
import com.aetherianartificer.townstead.root.appearance.IrisColors;
import com.aetherianartificer.townstead.root.gene.Allele;
import com.aetherianartificer.townstead.root.gene.GeneExpression;
import com.aetherianartificer.townstead.root.gene.Genotype;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The editor's foundings: one roll of a root or a mix, made here once, shown in the editor as it is,
 * and given to the target unchanged on Apply. Nothing about the individual is rolled again, so what
 * the editor shows is what the target becomes. Each player keeps their last few rolls until one is
 * applied.
 */
public final class RootFoundings {

    private RootFoundings() {}

    private static final int KEPT = 32;

    private record Founding(int id, int target, ResourceLocation rootId, @Nullable RootMix mix,
                            Genotype genotype, Heritage heritage, FoundingLook look) {}

    private static final Map<UUID, LinkedHashMap<Integer, Founding>> BY_PLAYER = new HashMap<>();
    private static int nextId;

    /** What the target is now. */
    public static RootFoundingS2CPayload state(ServerPlayer sp, int entityId, int requestId, int op) {
        LivingEntity target = targetOf(sp, entityId);
        if (target == null) return RootFoundingS2CPayload.refused(requestId, op);
        String root;
        RootMix mix;
        Heritage heritage;
        if (target instanceof VillagerEntityMCA villager) {
            TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();
            root = RootServerLogic.orDefault(life.rootId());
            mix = life.rootMix();
            heritage = life.hasHeritage() ? life.heritage() : null;
        } else {
            root = RootServerLogic.orDefault(PlayerRoot.getRootId(sp));
            mix = PlayerRoot.getMix(sp);
            heritage = PlayerRoot.getHeritage(sp);
        }
        ResourceLocation rootId = ResourceLocation.tryParse(root);
        String[] named = describe(rootId, mix, heritage);
        return new RootFoundingS2CPayload(requestId, op, 0, root, mix == null ? RootMix.NONE : mix,
                named[0], named[1], null);
    }

    /** Roll a founding of the asked root or mix for the target, keep it, and describe it. */
    public static RootFoundingS2CPayload roll(ServerPlayer sp, RootFoundingC2SPayload ask) {
        int op = RootFoundingC2SPayload.ROLL;
        LivingEntity target = targetOf(sp, ask.entityId());
        if (target == null) return RootFoundingS2CPayload.refused(ask.requestId(), op);

        RootMix mix = ask.mix().members().isEmpty() ? null : ask.mix();
        List<RootSelector.Weighted> shares;
        if (mix != null) {
            shares = mix.resolve();
        } else {
            ResourceLocation id = RootServerLogic.resolveKnown(ask.rootId());
            shares = id == null ? null : List.of(new RootSelector.Weighted(id, 1f));
        }
        if (shares == null) return RootFoundingS2CPayload.refused(ask.requestId(), op);
        if (ask.entityId() == RootSetC2SPayload.SELF) {
            for (RootSelector.Weighted share : shares) {
                if (!RootServerLogic.mayChooseOwn(sp, share.rootId())) return RootFoundingS2CPayload.refused(ask.requestId(), op);
            }
        }

        RandomSource random = sp.getRandom();
        ResourceLocation rootId;
        Genotype genotype;
        Heritage heritage;
        if (mix != null) {
            Heredity.MixedSeed seed = Heredity.seedMixed(shares, random);
            rootId = seed.rootId();
            genotype = seed.genotype();
            heritage = seed.heritage();
        } else {
            rootId = shares.get(0).rootId();
            genotype = Heredity.seedGenotype(rootId, random);
            heritage = RootRegistry.seedHeritage(rootId);
        }

        // A fresh member's body, held to the root's ranges (a mix's blended by share), as a new villager is.
        float[] floats = RootGenes.rollFresh(target.level(), target.blockPosition(), random);
        List<Map<String, GeneRange>> perRoot = new ArrayList<>(shares.size());
        List<Float> fractions = new ArrayList<>(shares.size());
        for (RootSelector.Weighted share : shares) {
            perRoot.add(RootGenes.resolveBodyMetrics(RootRegistry.effectiveInheritedGenes(share.rootId())));
            fractions.add(share.fraction());
        }
        RootGenes.apply(floats, mix == null ? perRoot.get(0) : RootGenes.blendRanges(perRoot, fractions), random);
        HairSettings hair = HairResolver.resolve(rootId, heritage);
        int hairDye = HairColors.rollSnapshot(floats, hair, random, hairDyeOf(sp, target));

        List<Allele> expressed = GeneExpression.activeAlleles(target, genotype, heritage);
        int[] iris = IrisColors.editable(expressed);
        FoundingLook look = new FoundingLook(floats, hairDye, iris != null, iris != null ? iris[0] : 0,
                iris != null ? iris[1] : -1, fittedClothes(sp, target, rootId, heritage),
                ExpressedGenesS2CPayload.of(0, expressed, heritage, rootId));

        Founding founding = keep(sp, new Founding(++nextId, ask.entityId(), rootId, mix, genotype, heritage, look));
        String[] named = describe(rootId, mix, heritage);
        return new RootFoundingS2CPayload(ask.requestId(), op, founding.id(), rootId.toString(),
                mix == null ? RootMix.NONE : mix, named[0], named[1], look);
    }

    /** Answers one editor request; a commit's root change is for the caller to sync. */
    public static Answer handle(ServerPlayer sp, RootFoundingC2SPayload ask) {
        return switch (ask.op()) {
            case RootFoundingC2SPayload.ROLL -> new Answer(null, roll(sp, ask));
            case RootFoundingC2SPayload.COMMIT -> commit(sp, ask);
            default -> new Answer(null, state(sp, ask.entityId(), ask.requestId(), RootFoundingC2SPayload.STATE));
        };
    }

    /** A root change to sync (null when nothing changed), and the reply for the asking client. */
    public record Answer(RootServerLogic.@Nullable Result result, RootFoundingS2CPayload reply) {}

    /** Give the target the founding exactly as rolled. */
    public static Answer commit(ServerPlayer sp, RootFoundingC2SPayload ask) {
        int op = RootFoundingC2SPayload.COMMIT;
        LinkedHashMap<Integer, Founding> kept = BY_PLAYER.get(sp.getUUID());
        Founding f = kept == null ? null : kept.remove(ask.foundingId());
        LivingEntity target = targetOf(sp, ask.entityId());
        if (f == null || target == null || f.target() != ask.entityId()) {
            return new Answer(null, state(sp, ask.entityId(), ask.requestId(), op));
        }

        RootServerLogic.Result result;
        if (target instanceof VillagerEntityMCA villager) {
            TownsteadVillager state = TownsteadVillagers.get(villager);
            TownsteadVillager.Life life = state.life();
            String before = RootServerLogic.orDefault(life.rootId());
            List<Power> oldGenes = new ArrayList<>(Powers.active(villager));
            life.setGenotype(f.genotype());
            life.setHeritage(f.heritage());
            life.setRoot(f.rootId().toString());
            life.clearCarriedVariants();
            Heredity.recomputeExpressed(life);
            if (f.mix() != null) life.setRootMix(f.mix());
            FoundingLook look = f.look();
            RootGenes.restore(villager, look.floats());
            villager.setHairDye(look.hairDye());
            if (look.iris()) IrisColors.apply(villager, look.irisTint(), look.irisRight());
            if (!look.clothes().isEmpty()) villager.setClothes(look.clothes());
            RootServerLogic.afterReseed(villager, state, f.rootId(), true, oldGenes);
            villager.refreshDimensions();
            if (!before.equals(f.rootId().toString())) {
                com.aetherianartificer.townstead.api.impl.v1.ApiEvents.rootChanged(villager, before, f.rootId().toString());
            }
            result = new RootServerLogic.Result(villager.getId(), f.rootId().toString());
        } else {
            String before = RootServerLogic.orDefault(PlayerRoot.getRootId(sp));
            RootServerLogic.adoptPlayerBody(sp, f.rootId(), f.genotype(), f.mix() != null ? f.heritage() : null);
            if (f.mix() != null) PlayerRoot.setMix(sp, f.mix());
            writePlayerLook(sp, f.look());
            if (!before.equals(f.rootId().toString())) {
                com.aetherianartificer.townstead.api.impl.v1.ApiEvents.rootChanged(sp, before, f.rootId().toString());
            }
            result = new RootServerLogic.Result(RootSetC2SPayload.SELF, f.rootId().toString());
        }
        return new Answer(result, state(sp, ask.entityId(), ask.requestId(), op));
    }

    /** A player leaving drops their rolls. */
    public static void forget(UUID player) {
        BY_PLAYER.remove(player);
    }

    private static Founding keep(ServerPlayer sp, Founding founding) {
        LinkedHashMap<Integer, Founding> kept = BY_PLAYER.computeIfAbsent(sp.getUUID(), k -> new LinkedHashMap<>());
        kept.put(founding.id(), founding);
        while (kept.size() > KEPT) kept.remove(kept.keySet().iterator().next());
        return founding;
    }

    @Nullable
    private static LivingEntity targetOf(ServerPlayer sp, int entityId) {
        if (entityId == RootSetC2SPayload.SELF) return sp;
        return sp.serverLevel().getEntity(entityId) instanceof VillagerEntityMCA villager ? villager : null;
    }

    /** {@code [name, backstory]}: a root's own name, or a mix's heritage name and backstory. */
    private static String[] describe(@Nullable ResourceLocation rootId, @Nullable RootMix mix, @Nullable Heritage heritage) {
        Root root = rootId == null ? null : RootRegistry.byId(rootId);
        Component rootName = root != null ? root.displayName() : Component.literal(rootId == null ? "" : rootId.toString());
        if (mix == null || heritage == null) return new String[] { rootName.getString(), "" };
        HeritageProfile profile = HeritageRegistry.bestMatch(heritage);
        String backstory = profile != null && profile.backstory() != null ? profile.backstory().getString() : "";
        return new String[] { HeritageRegistry.resolve(heritage, rootName).displayName().getString(), backstory };
    }

    private static CompoundTag playerData(ServerPlayer sp) {
        return net.conczin.mca.server.world.data.PlayerSaveData.get(sp).getEntityData();
    }

    private static int hairDyeOf(ServerPlayer sp, LivingEntity target) {
        if (target instanceof VillagerEntityMCA villager) return villager.getHairDye();
        CompoundTag data = playerData(sp);
        return data.contains("HairColor") ? data.getInt("HairColor") : 0xFF000000;
    }

    /** An outfit made for the body when it only wears its own and the current one doesn't fit; else empty. */
    private static String fittedClothes(ServerPlayer sp, LivingEntity target, ResourceLocation rootId, Heritage heritage) {
        var fitted = BodyClothingResolver.fitted(rootId, heritage);
        if (fitted.isEmpty() || SkinPicker.onMcaRig(rootId)) return "";
        String current;
        Gender gender;
        if (target instanceof VillagerEntityMCA villager) {
            current = villager.getClothes();
            gender = villager.getGenetics().getGender();
        } else {
            CompoundTag data = playerData(sp);
            current = data.getString("Clothes");
            gender = Gender.byId(data.getInt("Gender"));
        }
        if (SkinPicker.fits(fitted, current)) return "";
        return RigSkinPicker.pick(skin -> SkinPicker.fits(fitted, skin), gender, "minecraft:none",
                target.getUUID().getMostSignificantBits()).orElse("");
    }

    /** The look into the player's saved MCA data, which every client draws a villager-model player from. */
    private static void writePlayerLook(ServerPlayer sp, FoundingLook look) {
        net.conczin.mca.server.world.data.PlayerSaveData data = net.conczin.mca.server.world.data.PlayerSaveData.get(sp);
        CompoundTag entityData = data.getEntityData();
        RootGenes.writeToPlayerData(entityData, look.floats());
        entityData.putInt("HairColor", look.hairDye());
        if (look.iris()) IrisColors.seedInto(entityData, look.irisTint(), look.irisRight());
        if (!look.clothes().isEmpty()) entityData.putString("Clothes", look.clothes());
        data.setEntityDataSet(true);
        data.setDirty();
        for (ServerPlayer p : sp.serverLevel().players()) {
            //? if neoforge {
            net.conczin.mca.network.Network.sendToPlayer(
                    new net.conczin.mca.network.s2c.PlayerDataMessage(sp.getUUID(), entityData), p);
            //?} else {
            /*net.conczin.mca.cobalt.network.NetworkHandler.sendToPlayer(
                    new net.conczin.mca.network.s2c.PlayerDataMessage(sp.getUUID(), entityData), p);
            *///?}
        }
    }
}

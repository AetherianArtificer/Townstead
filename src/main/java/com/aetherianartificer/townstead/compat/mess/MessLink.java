package com.aetherianartificer.townstead.compat.mess;

import com.aetherianartificer.bitofamess.material.MessMaterial;
import com.aetherianartificer.bitofamess.material.MessMaterials;
import com.aetherianartificer.bitofamess.trace.BodyMarks;
import com.aetherianartificer.bitofamess.trace.TraceDef;
import com.aetherianartificer.bitofamess.trace.TraceHooks;
import com.aetherianartificer.bitofamess.trace.Traces;
import com.aetherianartificer.bitofamess.world.Blows;
import com.aetherianartificer.bitofamess.world.MessWorld;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.pheno.power.Powers;
import com.aetherianartificer.townstead.root.attachment.AttachmentDef;
import com.aetherianartificer.townstead.root.attachment.BodyPose;
import com.aetherianartificer.townstead.root.attachment.HitShapes;
import com.aetherianartificer.townstead.root.attachment.WornAttachments;
import com.aetherianartificer.townstead.root.gene.types.BleedsGeneType;
import com.aetherianartificer.townstead.switchboard.Switchboard;
import com.google.gson.JsonElement;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Predicate;

/** Tells Bit of a Mess who counts as a person, what a Root bleeds and what a pack's conditions mean. Loaded only when it's installed. */
final class MessLink {

    private static final String BLOOD = "bitofamess:blood";

    private MessLink() {}

    static void ate(LivingEntity body, net.minecraft.world.item.ItemStack food) {
        com.aetherianartificer.bitofamess.world.Meals.ate(body, food);
    }

    static void init() {
        TraceHooks.setPeople(entity -> entity instanceof VillagerEntityMCA);
        TraceHooks.setConditions(MessLink::condition);
        TraceHooks.setBloodOf(MessLink::bloodOf);
        TraceHooks.setLandsAs(MessLink::landsAs);
        TraceHooks.setGoreAllowed(MessLink::goreAllowed);
        TraceHooks.setParts(MessLink::parts);
    }

    private static List<TraceHooks.Part> parts(LivingEntity entity) {
        if (!(entity instanceof Player) && !(entity instanceof VillagerEntityMCA)) return List.of();
        List<TraceHooks.Part> out = new java.util.ArrayList<>();
        for (AttachmentDef def : WornAttachments.of(entity)) {
            List<AABB> boxes = HitShapes.aligned(entity, def);
            for (int i = 0; i < boxes.size(); i++) {
                if (boxes.get(i) == null) continue;
                int anchor = BodyPose.anchorOfBox(entity, def, i);
                var rules = def.body().part();
                out.add(new TraceHooks.Part(partKey(def.id(), anchor), boxes.get(i), () -> BodyPose.anchorFrame(entity, def, anchor), rules == null ? null : rules.bleeds()));
            }
        }
        return out;
    }

    /** One attachment at one place it fills, as both sides name it. */
    static String partKey(String attachment, int anchor) {
        return attachment + "/" + anchor;
    }

    private static Predicate<LivingEntity> condition(JsonElement when) {
        Condition condition = Conditions.parse(when);
        return entity -> condition == null || condition.test(new ConditionContext(entity));
    }

    private static boolean goreAllowed() {
        try {
            return Switchboard.get(TownsteadConfig.ENABLE_GORE);
        } catch (Exception e) {
            return true;
        }
    }

    private static BleedsGeneType.Instance gene(LivingEntity entity) {
        var bleeds = Powers.componentsOf(entity, BleedsGeneType.Instance.class);
        return bleeds.isEmpty() ? null : bleeds.get(0);
    }

    /** A body that doesn't bleed red turns gore into what it does bleed, or nothing at all. */
    private static String landsAs(LivingEntity entity, String trace) {
        BleedsGeneType.Instance gene = gene(entity);
        return gene == null || !gene.replacesTrace() ? trace : gene.trace();
    }

    /** What this Trace leaves this body as: empty when it isn't a material, or the body doesn't bleed. */
    private static String materialOf(LivingEntity entity, String trace) {
        TraceDef def = Traces.definition(trace);
        if (def != null && "gore".equals(def.category())) def = Traces.definition(landsAs(entity, trace));
        if (def == null || def.liquid().isEmpty()) return "";
        BleedsGeneType.Instance gene = gene(entity);
        return gene == null || gene.liquid().isEmpty() ? def.liquid() : gene.liquid();
    }

    private static TraceHooks.Blood bloodOf(LivingEntity entity) {
        if (!(entity instanceof Player) && !(entity instanceof VillagerEntityMCA)) return null;
        String material = materialOf(entity, BLOOD);
        if (material.isEmpty()) return new TraceHooks.Blood("", -1, -1);
        BleedsGeneType.Instance gene = gene(entity);
        return new TraceHooks.Blood(known(material), gene == null ? -1 : gene.color(), gene == null ? -1 : gene.dryColor());
    }

    private static String known(String material) {
        return MessMaterials.get(material) != null ? material : BLOOD;
    }

    private static String variant(LivingEntity body, String material) {
        BleedsGeneType.Instance gene = gene(body);
        return MessMaterial.variant(known(material), gene == null ? -1 : gene.color(), gene == null ? -1 : gene.dryColor());
    }

    static void addTrace(LivingEntity body, String trace, float amount) {
        Traces.add(body, trace, amount);
    }

    static float traceAmount(LivingEntity body, String trace) {
        return Traces.amount(body, trace);
    }

    static void wash(LivingEntity body, int strength, float amount, String trace) {
        Traces.wash(body, strength, amount, trace);
    }

    static void event(LivingEntity body, String event) {
        Traces.onEvent(body, event);
    }

    static void spray(LivingEntity body, String material, Vec3 at, Vec3 way, float force) {
        if (!(body.level() instanceof ServerLevel level)) return;
        MessWorld.of(level).splash(variant(body, material), at, way, force, false, body);
    }

    static void partLost(LivingEntity body, String trace, Vec3 at) {
        if (!(body.level() instanceof ServerLevel level)) return;
        String material = materialOf(body, trace);
        if (material.isEmpty()) return;
        // It sprays out from the body, and the stump is on whatever it was attached to.
        Vec3 way = at.subtract(body.position().add(0.0, body.getBbHeight() / 2.0, 0.0));
        String variant = variant(body, material);
        MessWorld.of(level).splash(variant, at, way, 8f, false, body);
        BodyMarks.blow(body, variant, at, way, 8f);
    }

    static void partHit(LivingEntity body, Vec3 at, Vec3 way, float amount) {
        Blows.onPartHit(body, at, way, amount);
    }

    static void partRegrown(LivingEntity body, String attachment) {
        BodyMarks.clearParts(body, attachment.isEmpty() ? "" : attachment + "/");
    }

    static List<String> traceIds() {
        return Traces.definitions().stream().map(TraceDef::id).toList();
    }

    static boolean hasMaterial(String id) {
        return MessMaterials.get(id) != null;
    }
}

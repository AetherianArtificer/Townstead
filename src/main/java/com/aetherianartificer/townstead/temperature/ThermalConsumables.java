package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.temperature.*;
import com.aetherianartificer.townstead.root.needs.NeedSuppression;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Shared native-temperature benefits for eating, drinking and assisted feeding. */
public final class ThermalConsumables {
    private static final String KEY = "townstead_food_temperature";
    public record Status(String effect, String opposite, int amplifier, int duration) {}
    private ThermalConsumables() {}

    public static void apply(VillagerEntityMCA recipient, ItemStack stack) {
        if (recipient.level().isClientSide() || !TownsteadConfig.isVillagerTemperatureEnabled()
                || NeedSuppression.suppressesTemperature(recipient) || stack.isEmpty()) return;
        for (MobEffectInstance effect : potionEffects(stack)) recipient.addEffect(new MobEffectInstance(effect));
        // One registry owns a food's thermal benefit, in the same order as ambient backends.
        // This avoids tripling a soup's warmth when a pack has multiple temperature mods.
        List<Status> lso = LsoConsumables.effects(stack);
        if (!lso.isEmpty()) { lso.forEach(effect -> applyStatus(recipient, effect)); return; }
        var coldSweat = ColdSweatConsumables.effects(stack, recipient);
        if (!coldSweat.isEmpty()) {
            var needs = TownsteadVillagers.get(recipient).needs();
            if (!needs.hasBodyTemp()) needs.setBodyTempTenths(ThermalProfile.of(recipient).neutralTenths());
            long now = recipient.level().getGameTime();
            List<TimedTemperatureEffects.Entry> timed = read(recipient);
            String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            for (var effect : coldSweat) {
                if (effect.duration() <= 0) needs.adjustBodyTemp(TemperatureData.tenths(effect.bodyDegrees()));
                else timed = TimedTemperatureEffects.add(timed, item + ":" + effect.duration(), effect.bodyDegrees(), effect.duration(), effect.stackLimit(), now);
            }
            write(recipient, timed);
            return;
        }
        if (!ModCompat.isLoaded("toughasnails")) return;
        int duration = tanDuration();
        for (String sign : List.of("cooling", "heating")) {
            if (stack.is(TagKey.create(Registries.ITEM, id("toughasnails:" + sign + "_consumed_items")))) {
                // TAN allows both effects to coexist; they cancel in its internal-temperature rule.
                applyStatus(recipient, new Status("toughasnails:internal_" + (sign.equals("heating") ? "warmth" : "chill"), "", 0, duration));
            }
        }
    }

    /** Temperature-only drinks need not restore hunger or thirst to be usable. */
    public static boolean hasEffects(ItemStack stack, LivingEntity consumer) {
        if (stack.getUseAnimation() != net.minecraft.world.item.UseAnim.EAT
                && stack.getUseAnimation() != net.minecraft.world.item.UseAnim.DRINK) return false;
        if (!potionEffects(stack).isEmpty() || !LsoConsumables.effects(stack).isEmpty()
                || !ColdSweatConsumables.effects(stack, consumer).isEmpty()) return true;
        return ModCompat.isLoaded("toughasnails") && (stack.is(TagKey.create(Registries.ITEM, id("toughasnails:heating_consumed_items")))
                || stack.is(TagKey.create(Registries.ITEM, id("toughasnails:cooling_consumed_items"))));
    }

    private static List<MobEffectInstance> potionEffects(ItemStack stack) {
        if (stack.getUseAnimation() != net.minecraft.world.item.UseAnim.DRINK) return List.of();
        //? if >=1.21 {
        var contents = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        if (contents == null) return List.of();
        Iterable<MobEffectInstance> effects = contents.getAllEffects();
        //?} else {
        /*Iterable<MobEffectInstance> effects = net.minecraft.world.item.alchemy.PotionUtils.getMobEffects(stack);
        *///?}
        List<MobEffectInstance> thermal = new ArrayList<>();
        for (var effect : effects) {
            //? if >=1.21 {
            var key = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
            //?} else {
            /*var key = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect());
            *///?}
            if (key != null && thermalPotion(key.getNamespace(), key.getPath())) thermal.add(effect);
        }
        return thermal;
    }

    static boolean thermalPotion(String namespace, String path) {
        return namespace.equals("legendarysurvivaloverhaul") && switch (path) {
            case "hot_food", "cold_food", "hot_drink", "cold_drink", "heat_resistance", "cold_resistance",
                 "heat_immunity", "cold_immunity", "temperature_immunity" -> true;
            default -> false;
        } || namespace.equals("toughasnails") && switch (path) {
            case "internal_warmth", "internal_chill", "ice_resistance", "climate_clemency" -> true;
            default -> false;
        };
    }

    static void applyStatus(LivingEntity recipient, Status status) {
        if (status.duration() <= 0 || status.amplifier() < 0) return;
        //? if >=1.21 {
        var effect = BuiltInRegistries.MOB_EFFECT.getHolder(id(status.effect()));
        if (effect.isEmpty()) return;
        recipient.addEffect(new MobEffectInstance(effect.get(), status.duration(), status.amplifier(), false, false, true));
        if (!status.opposite().isEmpty()) BuiltInRegistries.MOB_EFFECT.getHolder(id(status.opposite())).ifPresent(recipient::removeEffect);
        //?} else {
        /*var effect = BuiltInRegistries.MOB_EFFECT.get(id(status.effect()));
        if (effect == null) return;
        recipient.addEffect(new MobEffectInstance(effect, status.duration(), status.amplifier(), false, false, true));
        if (!status.opposite().isEmpty()) {
            var opposite = BuiltInRegistries.MOB_EFFECT.get(id(status.opposite()));
            if (opposite != null) recipient.removeEffect(opposite);
        }
        *///?}
    }

    public static float internalAmbient(LivingEntity entity, float ambient) {
        float adjusted = ToughAsNailsEntityCompat.internalAmbient(entity, ambient);
        adjusted += LsoEntityCompat.effects(entity).ambientOffset();
        long now = entity.level().getGameTime();
        List<TimedTemperatureEffects.Entry> entries = read(entity);
        if (entries.stream().anyMatch(e -> e.expiresAt() <= now)) {
            entries = entries.stream().filter(e -> e.expiresAt() > now).toList();
            write(entity, entries);
        }
        return adjusted + TimedTemperatureEffects.offset(entries, now) / TemperatureData.AMBIENT_PULL_PER_DEGREE;
    }

    private static List<TimedTemperatureEffects.Entry> read(LivingEntity entity) {
        List<TimedTemperatureEffects.Entry> entries = new ArrayList<>();
        ListTag list = entity.getPersistentData().getList(KEY, 10);
        for (int i = 0; i < Math.min(64, list.size()); i++) {
            CompoundTag tag = list.getCompound(i);
            entries.add(new TimedTemperatureEffects.Entry(tag.getString("item"), tag.getFloat("body"), tag.getLong("expires")));
        }
        return entries;
    }

    private static void write(LivingEntity entity, List<TimedTemperatureEffects.Entry> entries) {
        if (entries.isEmpty()) { entity.getPersistentData().remove(KEY); return; }
        ListTag list = new ListTag();
        for (var entry : entries) {
            CompoundTag tag = new CompoundTag();
            tag.putString("item", entry.key()); tag.putFloat("body", entry.bodyDegrees()); tag.putLong("expires", entry.expiresAt());
            list.add(tag);
        }
        entity.getPersistentData().put(KEY, list);
    }

    private static int tanDuration() {
        try {
            Object config = Class.forName("toughasnails.init.ModConfig").getField("temperature").get(null);
            return config.getClass().getField("consumableEffectDuration").getInt(config);
        } catch (ReflectiveOperationException | RuntimeException e) { return 1200; }
    }

    private static ResourceLocation id(String id) {
        //? if >=1.21 {
        return ResourceLocation.parse(id);
        //?} else {
        /*return new ResourceLocation(id);
        *///?}
    }
}

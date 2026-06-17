package com.aetherianartificer.townstead.pheno.selector.types;

import com.aetherianartificer.townstead.pheno.selector.Selector;
import com.aetherianartificer.townstead.pheno.selector.SelectorType;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.google.gson.JsonObject;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * The vanilla {@code @e[...]} command selector as an escape hatch (Apoli's {@code selector_action}
 * source), resolved against a command source at the focus. Parsed once at load; an invalid
 * selector string rejects the carrying action.
 */
public final class CommandSelectorType implements SelectorType {

    public static final String KEY = "pheno:command";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Selector parse(JsonObject json) {
        String raw = GsonHelper.getAsString(json, "selector", "");
        EntitySelector parsed;
        try {
            //? if >=1.21 {
            parsed = new EntitySelectorParser(new StringReader(raw), true).parse();
            //?} else {
            /*parsed = new EntitySelectorParser(new StringReader(raw)).parse();
            *///?}
        } catch (CommandSyntaxException e) {
            return null;
        }
        EntitySelector selector = parsed;
        return ctx -> {
            LivingEntity self = ctx.self();
            if (self == null || !(self.level() instanceof ServerLevel)) return List.of();
            try {
                List<LivingEntity> out = new ArrayList<>();
                for (Entity entity : selector.findEntities(self.createCommandSourceStack())) {
                    if (entity instanceof LivingEntity living) out.add(living);
                }
                return out;
            } catch (CommandSyntaxException e) {
                return List.of();
            }
        };
    }
}

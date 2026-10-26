package com.aetherianartificer.townstead.commands;

import com.aetherianartificer.townstead.root.attachment.AttachmentDef;
import com.aetherianartificer.townstead.root.attachment.AttachmentDoctor;
import com.aetherianartificer.townstead.root.attachment.AttachmentServerData;
import com.aetherianartificer.townstead.root.attachment.AttachmentSync;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /townstead attachment ...}: the attachment authoring toolkit.
 *
 * <ul>
 *   <li>{@code doctor} — health report: genes vs definitions, morph bones vs
 *       geometry, texture sizes vs declared UV space, targets vs points.
 *       Attachments' own surface — not /pheno.</li>
 *   <li>{@code adjust <id> offset|rotation|scale|bone|physics ...} — live-edit a
 *       loaded definition and re-broadcast the manifest, so placement and physics
 *       feel update on every rendered villager instantly (no redeploy). Lasts until
 *       the next reload. {@code physics <chain|all> <param> <value>} covers every
 *       knob; {@code <knob>.root} or {@code <knob>.tip} sets one end of a ramp.</li>
 *   <li>{@code dump <id>} — the definition as file-ready JSON with the live
 *       adjustments folded in; click the message to copy it.</li>
 * </ul>
 */
public final class AttachmentDoctorCommand {

    private AttachmentDoctorCommand() {}

    private static final SuggestionProvider<CommandSourceStack> IDS = (c, b) -> {
        List<String> ids = new ArrayList<>();
        for (AttachmentDef def : AttachmentServerData.definitions()) ids.add(def.id());
        return QuotedSuggestions.suggest(ids, b);
    };

    private static final SuggestionProvider<CommandSourceStack> BONES = (c, b) ->
            QuotedSuggestions.suggest(
                    List.of("head", "body", "left_arm", "right_arm", "left_leg", "right_leg"), b);

    // Each knob sets root and tip together; ".root"/".tip" sets one end of the ramp.
    private static final List<String> PHYSICS_PARAMS = List.of(
            "frequency", "frequency.root", "frequency.tip", "damping_ratio", "damping_ratio.root",
            "damping_ratio.tip", "gravity", "gravity.root", "gravity.tip", "inertia", "inertia.root",
            "inertia.tip", "drag", "drag.root", "drag.tip", "limit", "limit.root", "limit.tip",
            "rest_pitch", "sway", "sway_speed");

    private static final SuggestionProvider<CommandSourceStack> PARAMS = (c, b) ->
            QuotedSuggestions.suggest(PHYSICS_PARAMS, b);

    private static final SuggestionProvider<CommandSourceStack> CHAINS = (c, b) -> {
        List<String> options = new ArrayList<>(List.of("all"));
        try {
            AttachmentDef def = find(StringArgumentType.getString(c, "id"));
            if (def != null) for (int i = 0; i < def.physics().size(); i++) options.add(String.valueOf(i));
        } catch (Exception ignored) {}
        return QuotedSuggestions.suggest(options, b);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext ctx) {
        dispatcher.register(Commands.literal("townstead").then(Commands.literal("attachment")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("doctor").executes(c -> doctor(c.getSource())))
                .then(Commands.literal("dump")
                        .then(Commands.argument("id", StringArgumentType.string()).suggests(IDS)
                                .executes(c -> dump(c.getSource(), StringArgumentType.getString(c, "id")))))
                .then(Commands.literal("dump-geo")
                        .then(Commands.argument("id", StringArgumentType.string()).suggests(IDS)
                                .executes(c -> dumpGeo(c.getSource(), StringArgumentType.getString(c, "id")))))
                .then(Commands.literal("adjust")
                        .then(Commands.argument("id", StringArgumentType.string()).suggests(IDS)
                                .then(Commands.literal("offset").then(vec3(AttachmentDoctorCommand::adjustOffset)))
                                .then(Commands.literal("rotation").then(vec3(AttachmentDoctorCommand::adjustRotation)))
                                .then(Commands.literal("scale")
                                        .then(Commands.argument("s", FloatArgumentType.floatArg(0.01f, 16f))
                                                .executes(c -> adjust(c, def -> withScale(def,
                                                        FloatArgumentType.getFloat(c, "s"))))))
                                .then(Commands.literal("bone")
                                        .then(Commands.argument("name", StringArgumentType.string()).suggests(BONES)
                                                .executes(c -> adjust(c, def -> withBone(def,
                                                        StringArgumentType.getString(c, "name"))))))
                                .then(Commands.literal("physics")
                                        .then(Commands.argument("chain", StringArgumentType.word()).suggests(CHAINS)
                                                .then(Commands.argument("param", StringArgumentType.word()).suggests(PARAMS)
                                                        .then(Commands.argument("value", FloatArgumentType.floatArg())
                                                                .executes(AttachmentDoctorCommand::adjustPhysics)))))))));
    }

    /** Writes the def's live geometry blob (post-conversion, as the client renders it) for offline diffing. */
    private static int dumpGeo(CommandSourceStack source, String id) {
        AttachmentDef def = find(id);
        if (def == null) {
            source.sendFailure(Component.translatable("townstead.command.attachment.not_loaded", id));
            return 0;
        }
        AttachmentServerData.Blob blob = AttachmentServerData.blob(def.geoSha1());
        if (blob == null) {
            source.sendFailure(Component.translatable("townstead.command.attachment.no_geometry", id));
            return 0;
        }
        try {
            java.nio.file.Path path = java.nio.file.Paths.get("townstead-dump",
                    id.replace(':', '_').replace('/', '_') + ".geo.json");
            java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.write(path, blob.bytes());
            String where = path.toAbsolutePath().toString();
            source.sendSuccess(() -> Component.translatable("townstead.command.attachment.wrote", where), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.translatable("townstead.command.attachment.dump_failed", e.toString()));
            return 0;
        }
    }

    private interface VecAdjust {
        AttachmentDef apply(AttachmentDef def, float x, float y, float z);
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Float> vec3(VecAdjust fn) {
        return Commands.argument("x", FloatArgumentType.floatArg())
                .then(Commands.argument("y", FloatArgumentType.floatArg())
                        .then(Commands.argument("z", FloatArgumentType.floatArg())
                                .executes(c -> adjust(c, def -> fn.apply(def,
                                        FloatArgumentType.getFloat(c, "x"),
                                        FloatArgumentType.getFloat(c, "y"),
                                        FloatArgumentType.getFloat(c, "z"))))));
    }

    private interface DefEdit {
        AttachmentDef apply(AttachmentDef def);
    }

    private static int adjust(CommandContext<CommandSourceStack> c, DefEdit edit) {
        String id = StringArgumentType.getString(c, "id");
        AttachmentDef current = find(id);
        if (current == null) {
            c.getSource().sendFailure(Component.translatable("townstead.command.attachment.not_loaded", id));
            return 0;
        }
        AttachmentDef updated = edit.apply(current);
        AttachmentServerData.replaceDefinition(updated);
        c.getSource().getServer().getPlayerList().getPlayers().forEach(AttachmentSync::sendManifest);
        c.getSource().sendSuccess(() -> Component.translatable("townstead.command.attachment.adjusted",
                id, fmt(fileOffset(updated)[0]), fmt(fileOffset(updated)[1]), fmt(fileOffset(updated)[2]),
                fmt(fileRotation(updated)[0]), fmt(fileRotation(updated)[1]), fmt(fileRotation(updated)[2]),
                fmt(updated.scale()), updated.bone()), false);
        return 1;
    }

    /** Live-edit one spring parameter on one chain (or {@code all}), then re-broadcast. */
    private static int adjustPhysics(CommandContext<CommandSourceStack> c) {
        String id = StringArgumentType.getString(c, "id");
        String chainSel = StringArgumentType.getString(c, "chain");
        String param = StringArgumentType.getString(c, "param");
        float value = FloatArgumentType.getFloat(c, "value");
        AttachmentDef current = find(id);
        if (current == null) {
            c.getSource().sendFailure(Component.translatable("townstead.command.attachment.not_loaded", id));
            return 0;
        }
        if (current.physics().isEmpty()) {
            c.getSource().sendFailure(Component.translatable("townstead.command.attachment.no_physics", id));
            return 0;
        }
        if (!PHYSICS_PARAMS.contains(param)) {
            c.getSource().sendFailure(Component.translatable("townstead.command.attachment.physics.unknown_parameter",
                    param, String.join(", ", PHYSICS_PARAMS)));
            return 0;
        }
        int index = -1;
        if (!chainSel.equals("all")) {
            try {
                index = Integer.parseInt(chainSel);
            } catch (NumberFormatException e) {
                c.getSource().sendFailure(Component.translatable(
                        "townstead.command.attachment.physics.invalid_chain"));
                return 0;
            }
            if (index < 0 || index >= current.physics().size()) {
                c.getSource().sendFailure(Component.translatable(
                        "townstead.command.attachment.physics.chain_out_of_range",
                        index, current.physics().size() - 1));
                return 0;
            }
        }
        List<AttachmentDef.PhysicsChain> chains = new ArrayList<>(current.physics().size());
        for (int i = 0; i < current.physics().size(); i++) {
            AttachmentDef.PhysicsChain chain = current.physics().get(i);
            chains.add(index == -1 || index == i ? chainWith(chain, param, value) : chain);
        }
        AttachmentDef updated = new AttachmentDef(current.id(), current.geoSha1(), current.textureSha1(),
                current.targetTag(), current.targetPoint(), current.bone(), current.offset(), current.rotation(),
                current.scale(), current.tint(), current.tintSource(), current.tintBlend(), current.tintStrength(), current.tintMaskSha1(), current.emissiveSha1(), current.translucent(), current.hidesUnder(), current.whenJson(), current.morph(), current.visibility(),
                current.stages(), current.poses(), chains, current.animations());
        AttachmentServerData.replaceDefinition(updated);
        c.getSource().getServer().getPlayerList().getPlayers().forEach(AttachmentSync::sendManifest);
        c.getSource().sendSuccess(() -> Component.translatable(
                "townstead.command.attachment.physics.adjusted", id, chainSel, param, fmt(value)), false);
        return 1;
    }

    private static AttachmentDef.PhysicsChain chainWith(AttachmentDef.PhysicsChain chain, String param, float value) {
        String name = param;
        String end = "";
        int dot = param.indexOf('.');
        if (dot >= 0) {
            name = param.substring(0, dot);
            end = param.substring(dot + 1);
        }
        AttachmentDef.Knob frequency = chain.frequency(), dampingRatio = chain.dampingRatio();
        AttachmentDef.Knob gravity = chain.gravity(), inertia = chain.inertia();
        AttachmentDef.Knob drag = chain.drag(), limit = chain.limit();
        float restPitch = chain.restPitch(), sway = chain.sway(), swaySpeed = chain.swaySpeed();
        switch (name) {
            case "frequency" -> frequency = ramp(frequency, end, value);
            case "damping_ratio" -> dampingRatio = ramp(dampingRatio, end, value);
            case "gravity" -> gravity = ramp(gravity, end, value);
            case "inertia" -> inertia = ramp(inertia, end, value);
            case "drag" -> drag = ramp(drag, end, value);
            case "limit" -> limit = ramp(limit, end, value);
            case "rest_pitch" -> restPitch = value;
            case "sway" -> sway = value;
            case "sway_speed" -> swaySpeed = value;
        }
        return new AttachmentDef.PhysicsChain(chain.bones(), chain.preset(), frequency, dampingRatio, gravity,
                inertia, drag, limit, chain.hinge(), chain.collide(), restPitch, sway, swaySpeed,
                chain.scaleWithSize(), chain.segments(), chain.axis(), chain.notes());
    }

    private static AttachmentDef.Knob ramp(AttachmentDef.Knob knob, String end, float value) {
        return switch (end) {
            case "root" -> new AttachmentDef.Knob(value, knob.tip());
            case "tip" -> new AttachmentDef.Knob(knob.root(), value);
            default -> AttachmentDef.Knob.of(value);
        };
    }

    private static int dump(CommandSourceStack source, String id) {
        AttachmentDef def = find(id);
        if (def == null) {
            source.sendFailure(Component.translatable("townstead.command.attachment.not_loaded", id));
            return 0;
        }
        JsonObject source0 = AttachmentServerData.sourceJson(id);
        JsonObject json = source0 == null ? new JsonObject() : source0.deepCopy();
        json.add("offset", vecJson(fileOffset(def)));
        json.add("rotation", vecJson(fileRotation(def)));
        json.addProperty("scale", def.scale());
        json.addProperty("bone", def.bone());
        if (!def.physics().isEmpty()) json.add("physics", physicsJson(def.physics()));
        String pretty = new GsonBuilder().setPrettyPrinting().create().toJson(json);
        source.sendSuccess(() -> Component.literal(pretty).withStyle(Style.EMPTY
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, pretty))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("townstead.command.attachment.copy")))), false);
        return 1;
    }

    private static int doctor(CommandSourceStack source) {
        List<String> problems = new ArrayList<>(AttachmentDoctor.selfChecks());
        problems.addAll(AttachmentDoctor.crossChecks());
        int defs = AttachmentServerData.definitions().size();
        int slots = AttachmentServerData.slots().size();
        if (problems.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(
                    "townstead.command.attachment.healthy", defs, slots),
                    false);
            return 1;
        }
        source.sendSuccess(() -> Component.translatable(
                "townstead.command.attachment.problems", defs, slots, problems.size()), false);
        for (String problem : problems) {
            source.sendSuccess(() -> Component.translatable(
                    "townstead.command.attachment.problem", problem), false);
        }
        return 0;
    }

    private static AttachmentDef find(String id) {
        for (AttachmentDef def : AttachmentServerData.definitions()) {
            if (def.id().equals(id)) return def;
        }
        return null;
    }

    private static AttachmentDef withScale(AttachmentDef d, float scale) {
        return new AttachmentDef(d.id(), d.geoSha1(), d.textureSha1(), d.targetTag(), d.targetPoint(), d.bone(),
                d.offset(), d.rotation(), scale, d.tint(), d.tintSource(), d.tintBlend(), d.tintStrength(), d.tintMaskSha1(), d.emissiveSha1(), d.translucent(), d.hidesUnder(), d.whenJson(), d.morph(), d.visibility(), d.stages(), d.poses(), d.physics(), d.animations());
    }

    private static AttachmentDef withBone(AttachmentDef d, String bone) {
        return new AttachmentDef(d.id(), d.geoSha1(), d.textureSha1(), d.targetTag(), d.targetPoint(), bone,
                d.offset(), d.rotation(), d.scale(), d.tint(), d.tintSource(), d.tintBlend(), d.tintStrength(), d.tintMaskSha1(), d.emissiveSha1(), d.translucent(), d.hidesUnder(), d.whenJson(), d.morph(), d.visibility(), d.stages(), d.poses(), d.physics(), d.animations());
    }

    /** Whether this attachment's file reads in Blockbench space (v2), so commands speak its numbers. */
    private static boolean blockbench(String id) {
        return com.aetherianartificer.townstead.root.attachment.AttachmentConvention.isBlockbench(
                AttachmentServerData.sourceJson(id));
    }

    private static float[] fileOffset(AttachmentDef d) {
        return blockbench(d.id()) ? com.aetherianartificer.townstead.root.attachment.AttachmentConvention.offset(d.offset()) : d.offset();
    }

    private static float[] fileRotation(AttachmentDef d) {
        return blockbench(d.id()) ? com.aetherianartificer.townstead.root.attachment.AttachmentConvention.rotation(d.rotation()) : d.rotation();
    }

    private static AttachmentDef adjustOffset(AttachmentDef d, float x, float y, float z) {
        float[] offset = new float[]{x, y, z};
        if (blockbench(d.id())) offset = com.aetherianartificer.townstead.root.attachment.AttachmentConvention.offset(offset);
        return new AttachmentDef(d.id(), d.geoSha1(), d.textureSha1(), d.targetTag(), d.targetPoint(), d.bone(),
                offset, d.rotation(), d.scale(), d.tint(), d.tintSource(), d.tintBlend(), d.tintStrength(), d.tintMaskSha1(), d.emissiveSha1(), d.translucent(), d.hidesUnder(), d.whenJson(), d.morph(), d.visibility(), d.stages(), d.poses(), d.physics(), d.animations());
    }

    private static AttachmentDef adjustRotation(AttachmentDef d, float x, float y, float z) {
        float[] rotation = new float[]{x, y, z};
        if (blockbench(d.id())) rotation = com.aetherianartificer.townstead.root.attachment.AttachmentConvention.rotation(rotation);
        return new AttachmentDef(d.id(), d.geoSha1(), d.textureSha1(), d.targetTag(), d.targetPoint(), d.bone(),
                d.offset(), rotation, d.scale(), d.tint(), d.tintSource(), d.tintBlend(), d.tintStrength(), d.tintMaskSha1(), d.emissiveSha1(), d.translucent(), d.hidesUnder(), d.whenJson(), d.morph(), d.visibility(), d.stages(), d.poses(), d.physics(), d.animations());
    }

    /** The chains as file-ready {@code physics} JSON, so dump round-trips live tuning. */
    private static JsonObject physicsJson(List<AttachmentDef.PhysicsChain> physics) {
        JsonArray chains = new JsonArray();
        for (AttachmentDef.PhysicsChain chain : physics) {
            chains.add(com.aetherianartificer.townstead.root.attachment.PhysicsChains.toJson(chain));
        }
        JsonObject json = new JsonObject();
        json.add("chains", chains);
        return json;
    }

    private static JsonArray vecJson(float[] v) {
        JsonArray out = new JsonArray();
        for (float f : v) out.add(f);
        return out;
    }

    private static String fmt(float f) {
        return f == Math.floor(f) ? String.valueOf((int) f) : String.valueOf(f);
    }
}

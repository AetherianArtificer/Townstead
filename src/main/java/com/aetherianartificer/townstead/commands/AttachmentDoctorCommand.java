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
import net.minecraft.commands.SharedSuggestionProvider;
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
 *       spring parameter including the four response channels.</li>
 *   <li>{@code dump <id>} — the definition as file-ready JSON with the live
 *       adjustments folded in; click the message to copy it.</li>
 * </ul>
 */
public final class AttachmentDoctorCommand {

    private AttachmentDoctorCommand() {}

    private static final SuggestionProvider<CommandSourceStack> IDS = (c, b) -> {
        List<String> ids = new ArrayList<>();
        for (AttachmentDef def : AttachmentServerData.definitions()) ids.add(def.id());
        return SharedSuggestionProvider.suggest(ids, b);
    };

    private static final SuggestionProvider<CommandSourceStack> BONES = (c, b) ->
            SharedSuggestionProvider.suggest(
                    List.of("head", "body", "left_arm", "right_arm", "left_leg", "right_leg"), b);

    private static final List<String> PHYSICS_PARAMS = List.of(
            "stiffness", "damping", "gravity", "max_angle", "min_pitch", "max_pitch", "rest_pitch", "sway",
            "follow", "droop_angle", "sway_speed", "snap", "response_vertical", "response_forward",
            "response_lateral", "response_turn");

    private static final SuggestionProvider<CommandSourceStack> PARAMS = (c, b) ->
            SharedSuggestionProvider.suggest(PHYSICS_PARAMS, b);

    private static final SuggestionProvider<CommandSourceStack> CHAINS = (c, b) -> {
        List<String> options = new ArrayList<>(List.of("all"));
        try {
            AttachmentDef def = find(StringArgumentType.getString(c, "id"));
            if (def != null) for (int i = 0; i < def.physics().size(); i++) options.add(String.valueOf(i));
        } catch (Exception ignored) {}
        return SharedSuggestionProvider.suggest(options, b);
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
            source.sendFailure(Component.literal("No attachment '" + id + "' is loaded."));
            return 0;
        }
        AttachmentServerData.Blob blob = AttachmentServerData.blob(def.geoSha1());
        if (blob == null) {
            source.sendFailure(Component.literal("No geometry blob for " + id));
            return 0;
        }
        try {
            java.nio.file.Path path = java.nio.file.Paths.get("townstead-dump",
                    id.replace(':', '_').replace('/', '_') + ".geo.json");
            java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.write(path, blob.bytes());
            String where = path.toAbsolutePath().toString();
            source.sendSuccess(() -> Component.literal("Wrote " + where), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Dump failed: " + e));
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
            c.getSource().sendFailure(Component.literal("No attachment '" + id + "' is loaded."));
            return 0;
        }
        AttachmentDef updated = edit.apply(current);
        AttachmentServerData.replaceDefinition(updated);
        c.getSource().getServer().getPlayerList().getPlayers().forEach(AttachmentSync::sendManifest);
        c.getSource().sendSuccess(() -> Component.literal(id + " -> offset ["
                + fmt(updated.offset()[0]) + ", " + fmt(updated.offset()[1]) + ", " + fmt(updated.offset()[2])
                + "], rotation [" + fmt(updated.rotation()[0]) + ", " + fmt(updated.rotation()[1]) + ", "
                + fmt(updated.rotation()[2]) + "], scale " + fmt(updated.scale()) + ", bone " + updated.bone()
                + " (live until reload; '/townstead attachment dump " + id + "' for the JSON)"), false);
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
            c.getSource().sendFailure(Component.literal("No attachment '" + id + "' is loaded."));
            return 0;
        }
        if (current.physics().isEmpty()) {
            c.getSource().sendFailure(Component.literal(id + " has no physics chains."));
            return 0;
        }
        if (!PHYSICS_PARAMS.contains(param)) {
            c.getSource().sendFailure(Component.literal(
                    "Unknown parameter '" + param + "' (known: " + String.join(", ", PHYSICS_PARAMS) + ")"));
            return 0;
        }
        int index = -1;
        if (!chainSel.equals("all")) {
            try {
                index = Integer.parseInt(chainSel);
            } catch (NumberFormatException e) {
                c.getSource().sendFailure(Component.literal("Chain must be 'all' or a chain index."));
                return 0;
            }
            if (index < 0 || index >= current.physics().size()) {
                c.getSource().sendFailure(Component.literal(
                        "Chain index " + index + " out of range (0.." + (current.physics().size() - 1) + ")."));
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
        c.getSource().sendSuccess(() -> Component.literal(id + " physics[" + chainSel + "] " + param + " = "
                + fmt(value) + " (live until reload; '/townstead attachment dump " + id + "' for the JSON)"), false);
        return 1;
    }

    private static AttachmentDef.PhysicsChain chainWith(AttachmentDef.PhysicsChain chain, String param, float value) {
        float stiffness = chain.stiffness(), damping = chain.damping(), gravity = chain.gravity();
        float maxAngle = chain.maxAngle(), minPitch = chain.minPitch(), maxPitch = chain.maxPitch();
        float restPitch = chain.restPitch(), sway = chain.sway(), follow = chain.follow();
        float droopAngle = chain.droopAngle(), swaySpeed = chain.swaySpeed(), snap = chain.snap();
        float[] response = java.util.Arrays.copyOf(chain.response(), 4);
        switch (param) {
            case "stiffness" -> stiffness = value;
            case "damping" -> damping = value;
            case "gravity" -> gravity = value;
            case "max_angle" -> maxAngle = value;
            case "min_pitch" -> minPitch = value;
            case "max_pitch" -> maxPitch = value;
            case "rest_pitch" -> restPitch = value;
            case "sway" -> sway = value;
            case "follow" -> follow = value;
            case "droop_angle" -> droopAngle = value;
            case "sway_speed" -> swaySpeed = value;
            case "snap" -> snap = value;
            case "response_vertical" -> response[0] = value;
            case "response_forward" -> response[1] = value;
            case "response_lateral" -> response[2] = value;
            case "response_turn" -> response[3] = value;
        }
        return new AttachmentDef.PhysicsChain(chain.bones(), stiffness, damping, gravity, maxAngle,
                minPitch, maxPitch, restPitch, sway, follow, droopAngle, swaySpeed, snap, response,
                chain.segments(), chain.axis());
    }

    private static int dump(CommandSourceStack source, String id) {
        AttachmentDef def = find(id);
        if (def == null) {
            source.sendFailure(Component.literal("No attachment '" + id + "' is loaded."));
            return 0;
        }
        JsonObject source0 = AttachmentServerData.sourceJson(id);
        JsonObject json = source0 == null ? new JsonObject() : source0.deepCopy();
        json.add("offset", vecJson(def.offset()));
        json.add("rotation", vecJson(def.rotation()));
        json.addProperty("scale", def.scale());
        json.addProperty("bone", def.bone());
        if (!def.physics().isEmpty()) json.add("physics", physicsJson(def.physics()));
        String pretty = new GsonBuilder().setPrettyPrinting().create().toJson(json);
        source.sendSuccess(() -> Component.literal(pretty).withStyle(Style.EMPTY
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, pretty))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Click to copy")))), false);
        return 1;
    }

    private static int doctor(CommandSourceStack source) {
        List<String> problems = new ArrayList<>(AttachmentDoctor.selfChecks());
        problems.addAll(AttachmentDoctor.crossChecks());
        int defs = AttachmentServerData.definitions().size();
        int slots = AttachmentServerData.slots().size();
        if (problems.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "Attachments healthy: " + defs + " definition(s), " + slots + " point(s), no problems found."),
                    false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal(
                "Attachment doctor: " + defs + " definition(s), " + slots + " point(s), "
                        + problems.size() + " problem(s):"), false);
        for (String problem : problems) {
            source.sendSuccess(() -> Component.literal(" - " + problem), false);
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

    private static AttachmentDef adjustOffset(AttachmentDef d, float x, float y, float z) {
        return new AttachmentDef(d.id(), d.geoSha1(), d.textureSha1(), d.targetTag(), d.targetPoint(), d.bone(),
                new float[]{x, y, z}, d.rotation(), d.scale(), d.tint(), d.tintSource(), d.tintBlend(), d.tintStrength(), d.tintMaskSha1(), d.emissiveSha1(), d.translucent(), d.hidesUnder(), d.whenJson(), d.morph(), d.visibility(), d.stages(), d.poses(), d.physics(), d.animations());
    }

    private static AttachmentDef adjustRotation(AttachmentDef d, float x, float y, float z) {
        return new AttachmentDef(d.id(), d.geoSha1(), d.textureSha1(), d.targetTag(), d.targetPoint(), d.bone(),
                d.offset(), new float[]{x, y, z}, d.scale(), d.tint(), d.tintSource(), d.tintBlend(), d.tintStrength(), d.tintMaskSha1(), d.emissiveSha1(), d.translucent(), d.hidesUnder(), d.whenJson(), d.morph(), d.visibility(), d.stages(), d.poses(), d.physics(), d.animations());
    }

    /** The chains as file-ready {@code physics} JSON, so dump round-trips live tuning. */
    private static JsonObject physicsJson(List<AttachmentDef.PhysicsChain> physics) {
        JsonArray chains = new JsonArray();
        for (AttachmentDef.PhysicsChain chain : physics) {
            JsonObject out = new JsonObject();
            JsonArray bones = new JsonArray();
            for (String bone : chain.bones()) bones.add(bone);
            out.add("bones", bones);
            out.addProperty("stiffness", chain.stiffness());
            out.addProperty("damping", chain.damping());
            out.addProperty("gravity", chain.gravity());
            out.addProperty("max_angle", chain.maxAngle());
            out.addProperty("min_pitch", chain.minPitch());
            out.addProperty("max_pitch", chain.maxPitch());
            out.addProperty("rest_pitch", chain.restPitch());
            out.addProperty("sway", chain.sway());
            out.addProperty("follow", chain.follow());
            out.addProperty("droop_angle", chain.droopAngle());
            out.addProperty("sway_speed", chain.swaySpeed());
            out.addProperty("snap", chain.snap());
            JsonObject response = new JsonObject();
            response.addProperty("vertical", chain.response()[0]);
            response.addProperty("forward", chain.response()[1]);
            response.addProperty("lateral", chain.response()[2]);
            response.addProperty("turn", chain.response()[3]);
            out.add("response", response);
            if (chain.segments() > 0) out.addProperty("segments", chain.segments());
            if (!chain.axis().equals("auto")) out.addProperty("axis", chain.axis());
            chains.add(out);
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

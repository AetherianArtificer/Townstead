package com.aetherianartificer.townstead.client.root;

import com.aetherianartificer.townstead.root.GeneCatalogEntry;
import com.aetherianartificer.townstead.root.RootCatalogEntry;
import com.aetherianartificer.townstead.root.appearance.HairColors;
import com.aetherianartificer.townstead.root.appearance.IrisColors;
import com.aetherianartificer.townstead.root.RootGenes;
import com.aetherianartificer.townstead.root.GeneRange;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /townstead root_icons <id> [rolls]}: a picker icon for every root of a pack ({@code <id>} a
 * namespace) or one root ({@code <id>} a root id), rendered from
 * a freshly rolled founder of each (its skin, markings, eyes, hair and head attachments, on its own
 * body) as the head alone, in the three-quarter view of a placed head. Written as 64x64 PNGs to
 * {@code townstead-dump/icons/data/<namespace>/textures/root/<root>.png} (or the path the root's
 * {@code icon} names), laid out as in a data pack so the folder copies straight in. With rolls above
 * one, each extra roll is saved beside the first ({@code _2}, {@code _3}...) to pick from.
 *
 * <p>{@code /townstead root_icons <id> calibrate} renders the first matching root at several camera
 * angles (named by angle), to settle the view.</p>
 */
public final class RootIcons {
    private static final int SIZE = 256;        // rendered, then trimmed to the head and scaled down
    private static final int ICON = 64;
    static float yaw = 35f;                     // the view: turned to show one side, tilted to show the top
    static float pitch = 25f;

    private RootIcons() {}

    /** {@code /townstead root_icons}: rolls 0 is a calibration sheet. */
    public static void render(String id, int rolls) {
        if (rolls <= 0) calibrate(id);
        else run(id, rolls);
    }

    /** Whether a root is what {@code id} names: that root, or any root of that namespace. */
    private static boolean matches(RootCatalogEntry root, String id) {
        return id.contains(":") ? root.id().equals(id) : root.id().startsWith(id + ":");
    }

    private static int run(String namespace, int rolls) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return 0;
        Path out = mc.gameDirectory.toPath().resolve("townstead-dump/icons");
        int done = 0;
        int id = 0;
        for (RootCatalogEntry root : RootCatalogClient.origins()) {
            if (!matches(root, namespace)) continue;
            for (int roll = 0; roll < rolls; roll++) {
                try {
                    int[] icon = renderIcon(root, ++id, roll);
                    if (icon == null) continue;
                    write(out.resolve(iconPath(root, roll)), icon);
                    done++;
                } catch (Exception e) {
                    org.slf4j.LoggerFactory.getLogger("townstead/icons").warn("Icon for {} failed", root.id(), e);
                }
            }
        }
        mc.player.displayClientMessage(Component.literal("Rendered " + done + " icons to " + out), false);
        return done;
    }

    private static int calibrate(String namespace) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return 0;
        RootCatalogEntry root = RootCatalogClient.origins().stream()
                .filter(r -> matches(r, namespace)).findFirst().orElse(null);
        if (root == null) return 0;
        Path out = mc.gameDirectory.toPath().resolve("townstead-dump/icons/calibrate");
        float keepYaw = yaw, keepPitch = pitch;
        int id = 0;
        try {
            for (float y : new float[]{-35f, 35f, 145f, 215f}) {
                for (float p : new float[]{-25f, 25f}) {
                    yaw = y;
                    pitch = p;
                    int[] icon = renderIcon(root, ++id, 0);
                    if (icon != null) write(out.resolve("yaw" + (int) y + "_pitch" + (int) p + ".png"), icon);
                }
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("townstead/icons").warn("Calibration failed", e);
        } finally {
            yaw = keepYaw;
            pitch = keepPitch;
        }
        mc.player.displayClientMessage(Component.literal("Calibration renders in " + out), false);
        return 1;
    }

    /** Where a root's icon goes: the path its {@code icon} names, else textures/root/<root>.png. */
    private static Path iconPath(RootCatalogEntry root, int roll) {
        String[] id = root.id().split(":", 2);
        var browse = RootCatalogClient.browse(root.id());
        String ns = id[0], path = "textures/root/" + id[1] + ".png";
        if (browse != null && !browse.icon().isEmpty() && browse.icon().contains(":")) {
            String[] icon = browse.icon().split(":", 2);
            ns = icon[0];
            path = icon[1].endsWith(".png") ? icon[1] : icon[1] + ".png";
        }
        if (roll > 0) path = path.substring(0, path.length() - 4) + "_" + (roll + 1) + ".png";
        return Path.of("data", ns, path);
    }

    /** A freshly rolled founder of the root, the way the editor previews a browsed root. */
    private static VillagerEntityMCA founder(RootCatalogEntry root, int id, int roll) {
        Minecraft mc = Minecraft.getInstance();
        RandomSource random = RandomSource.create(root.id().hashCode() * 31L + roll);
        Gender gender = random.nextBoolean() ? Gender.MALE : Gender.FEMALE;
        VillagerEntityMCA villager = gender.getVillagerType().create(mc.level);
        if (villager == null) return null;
        villager.setId(-1_900_000_000 - id);         // far from real ids: client stores keyed by id stay clean
        com.aetherianartificer.townstead.compat.mca.McaGeneticsCompat.randomize(villager);
        villager.initializeSkin(false);
        villager.validateClothes();
        Map<String, GeneRange> ranges = new LinkedHashMap<>();
        for (RootCatalogEntry.GeneRangeView r : root.geneRanges()) ranges.put(r.key(), new GeneRange(r.min(), r.max()));
        RootGenes.apply(villager, ranges, random);
        RootClientStore.set(villager.getId(), root.id());
        RootClientStore.setHair(villager.getId(), root.hair(), root.hairColorRanges(), root.hairColors(), root.hairGradients());
        HairColors.roll(villager, RootClientStore.hairSettings(villager), random);
        // one roll of each style gene (ears, horns, markings, skin...), and the people's eye colour
        for (RootCatalogEntry.Inherited inherited : root.inheritedGenes()) {
            GeneCatalogEntry gene = RootCatalogClient.gene(inherited.geneId());
            if (gene == null || gene.variants().isEmpty()) continue;
            GeneCatalogEntry.Variant pick = weighted(gene.variants(), random);
            if (pick == null) continue;
            if ("iris_seed".equals(gene.faceSlot())) {
                IrisColors.apply(villager, pick.tint(), pick.palette().isEmpty() ? -1 : pick.palette().get(0));
            } else {
                RootClientStore.setCarriedVariant(villager.getId(), gene.id(), pick.id());
            }
        }
        return villager;
    }

    private static GeneCatalogEntry.Variant weighted(List<GeneCatalogEntry.Variant> variants, RandomSource random) {
        int total = 0;
        for (var v : variants) total += Math.max(1, v.weight());
        int roll = random.nextInt(Math.max(1, total));
        for (var v : variants) {
            roll -= Math.max(1, v.weight());
            if (roll < 0) return v;
        }
        return variants.get(0);
    }

    /** The root's founder head, rendered and trimmed to an ICON x ICON ARGB image; null if it can't. */
    private static int[] renderIcon(RootCatalogEntry root, int id, int roll) {
        VillagerEntityMCA villager = founder(root, id, roll);
        if (villager == null) return null;
        try {
            int[] pixels = renderHead(villager);
            return pixels == null ? null : trim(pixels);
        } finally {
            RootClientStore.remove(villager.getId());
            String rigBase = com.aetherianartificer.townstead.client.species.RigModels.rigBaseFor(villager);
            var rig = com.aetherianartificer.townstead.client.species.RigModels.model(rigBase);
            if (rig != null) restoreScales(rig);
        }
    }

    /** Undo the head-only hiding on a shared rig model, so other bodies on it draw whole. */
    private static void restoreScales(net.minecraft.client.model.HumanoidModel<?> model) {
        for (var part : new net.minecraft.client.model.geom.ModelPart[]{model.body, model.rightArm, model.leftArm,
                model.rightLeg, model.leftLeg}) {
            part.xScale = 1f;
            part.yScale = 1f;
            part.zScale = 1f;
        }
    }

    /** The head drawn offscreen in the icon view, as ARGB pixels (SIZE x SIZE), or null. */
    private static int[] renderHead(VillagerEntityMCA villager) {
        //? if >=1.21 {
        Minecraft mc = Minecraft.getInstance();
        var target = new com.mojang.blaze3d.pipeline.TextureTarget(SIZE, SIZE, true, Minecraft.ON_OSX);
        target.setClearColor(0f, 0f, 0f, 0f);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        org.joml.Matrix4f projection = new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
        var sorting = com.mojang.blaze3d.systems.RenderSystem.getVertexSorting();
        com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(
                new org.joml.Matrix4f().setOrtho(0f, SIZE, SIZE, 0f, 1000f, 21000f),
                com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
        org.joml.Matrix4fStack view = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        view.pushMatrix();
        view.translation(0f, 0f, -11000f);
        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        IconRender.setHeadOnly(true);
        try {
            villager.yBodyRot = 180f;
            villager.yBodyRotO = 180f;
            villager.setYRot(180f);
            villager.setXRot(0f);
            villager.yHeadRot = 180f;
            villager.yHeadRotO = 180f;
            var graphics = new net.minecraft.client.gui.GuiGraphics(mc, mc.renderBuffers().bufferSource());
            float anchor = villager.getEyeHeight();
            Float rigEye = com.aetherianartificer.townstead.client.species.RigCamera.eyeHeight(villager);
            if (rigEye != null) anchor = rigEye;
            org.joml.Quaternionf model = new org.joml.Quaternionf().rotateZ((float) Math.PI)
                    .rotateX((float) Math.toRadians(pitch)).rotateY((float) Math.toRadians(yaw));
            net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventory(
                    graphics, SIZE / 2f, SIZE / 2f, SIZE * 0.45f, new org.joml.Vector3f(0f, anchor, 0f),
                    model, new org.joml.Quaternionf(), villager);
            graphics.flush();
        } finally {
            IconRender.setHeadOnly(false);
            view.popMatrix();
            com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
            com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(projection, sorting);
        }
        var image = new com.mojang.blaze3d.platform.NativeImage(SIZE, SIZE, false);
        com.mojang.blaze3d.systems.RenderSystem.bindTexture(target.getColorTextureId());
        image.downloadTexture(0, false);
        image.flipY();
        int[] out = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int abgr = image.getPixelRGBA(x, y);       // NativeImage packs ABGR
                int a = abgr >>> 24, b = (abgr >> 16) & 0xFF, g = (abgr >> 8) & 0xFF, r = abgr & 0xFF;
                out[y * SIZE + x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        image.close();
        target.destroyBuffers();
        mc.getMainRenderTarget().bindWrite(true);
        return out;
        //?} else {
        /*return null;   // the offscreen icon render is 1.21 only for now
        *///?}
    }

    /** Crop to the drawn head (a square around its pixels, with a margin) and box-filter down to ICON. */
    private static int[] trim(int[] pixels) {
        int minX = SIZE, minY = SIZE, maxX = -1, maxY = -1;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if ((pixels[y * SIZE + x] >>> 24) > 8) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < 0) return null;
        int side = Math.round(Math.max(maxX - minX + 1, maxY - minY + 1) * 1.08f);
        float cx = (minX + maxX) / 2f, cy = (minY + maxY) / 2f;
        float x0 = cx - side / 2f, y0 = cy - side / 2f, step = side / (float) ICON;
        int[] out = new int[ICON * ICON];
        for (int oy = 0; oy < ICON; oy++) {
            for (int ox = 0; ox < ICON; ox++) {
                // premultiplied average over the source block, so edges don't fringe dark
                float a = 0, r = 0, g = 0, b = 0;
                int n = 0;
                for (float sy = y0 + oy * step; sy < y0 + (oy + 1) * step; sy += 1f) {
                    for (float sx = x0 + ox * step; sx < x0 + (ox + 1) * step; sx += 1f) {
                        int px = (int) sx, py = (int) sy;
                        n++;
                        if (px < 0 || py < 0 || px >= SIZE || py >= SIZE) continue;
                        int c = pixels[py * SIZE + px];
                        float ca = (c >>> 24) / 255f;
                        a += ca;
                        r += ((c >> 16) & 0xFF) * ca;
                        g += ((c >> 8) & 0xFF) * ca;
                        b += (c & 0xFF) * ca;
                    }
                }
                if (n == 0 || a <= 0f) continue;
                int oa = Math.round(a / n * 255f);
                out[oy * ICON + ox] = (oa << 24) | (Math.round(r / a) << 16) | (Math.round(g / a) << 8) | Math.round(b / a);
            }
        }
        return out;
    }

    private static void write(Path path, int[] argb) throws java.io.IOException {
        Files.createDirectories(path.getParent());
        var image = new java.awt.image.BufferedImage(ICON, ICON, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, ICON, ICON, argb, 0, ICON);
        javax.imageio.ImageIO.write(image, "png", path.toFile());
    }
}

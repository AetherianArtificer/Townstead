package com.aetherianartificer.townstead.client.render.block;

import com.aetherianartificer.townstead.block.CalendarBlock;
import com.aetherianartificer.townstead.block.CalendarBlockEntity;
import com.aetherianartificer.townstead.calendar.CalendarClientStore;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.joml.Matrix4f;

/**
 * Renders the current month + day directly on the calendar block's parchment
 * face, like a flip-day wall calendar.
 *
 * <p>Layout (in pixel coordinates of the visible face, centred at 0,0):</p>
 * <ul>
 *   <li>Crimson banner across the top of the inset, ~3 px tall</li>
 *   <li>Parchment-toned tile filling the bottom ~7 px</li>
 *   <li>Month name centred on the banner in warm off-white</li>
 *   <li>Day number centred on the tile, dark, scaled up dominantly</li>
 * </ul>
 *
 * <p>Colored fills use explicit quads (not {@link Font}'s background-color
 * feature) so the tiles are fixed-size, and so the depth-ordering of
 * backdrop-vs-glyph doesn't end up hiding the text. Text is then drawn a hair
 * forward of the quads to avoid Z-fighting.</p>
 */
public class CalendarBlockEntityRenderer implements BlockEntityRenderer<CalendarBlockEntity> {

    // Pixel coordinates referenced below are in BLOCK pixel space (1 block face
    // = 16 pixels). The parchment inset on the block model spans (2.5..13.5) on
    // both axes (an 11x11 area). After centring on the inset, x and y range
    // from -5.5 to +5.5.

    // ── Banner / tile geometry (pixel coords, inset-centred) ────────────────
    // The banner and tile are now real 3D model elements (see calendar_wall.json);
    // these bounds mirror the model so the BER knows where to centre the text.
    private static final float BANNER_X1 = -5.5f, BANNER_X2 = 5.5f;
    private static final float BANNER_Y1 =  2.5f, BANNER_Y2 = 5.5f;
    private static final float TILE_X1 = -5.5f,  TILE_X2 = 5.5f;
    private static final float TILE_Y1 = -5.5f,  TILE_Y2 = 2.5f;

    // ── Palette ─────────────────────────────────────────────────────────────
    private static final int MONTH_FG = 0xFFF8E8C6; // off-white, reads on red_terracotta
    private static final int DAY_FG   = 0xFF1A1208; // near-black, reads on birch tile

    // ── Z offsets (in pose units, after FACE_SCALE) ─────────────────────────
    // Text is pushed a fraction of a pixel toward the viewer so it draws on
    // top of the coloured tiles without z-fighting. (After FACE_SCALE = 1/16,
    // a "pose Z" of 0.05 ≈ 1/320 block ≈ less than a single subpixel.)
    private static final float TEXT_Z_OFFSET = 0.05f;

    // ── Text scales (multiplier on Font's native size) ──────────────────────
    // Native font line height is 9 px. Month text wants to fit in the 3-px
    // banner band; day number is the dominant element. These are MAXIMUMS —
    // text is shrunk further if needed to fit horizontally in its container
    // (long month names like "Vendémiaire" would otherwise overflow the block).
    private static final float MONTH_TEXT_SCALE = 3.0f / 9.0f;
    private static final float DAY_TEXT_SCALE   = 5.0f / 9.0f;

    // ── Horizontal padding inside the banner / tile (face pixels) ───────────
    private static final float TEXT_INSET = 0.5f;

    // ── Optical-centering nudge for digits ──────────────────────────────────
    // Vanilla MC font's lineHeight (9) includes descender space that digits
    // don't use, so geometric Y-centre of the text box sits below the visible
    // glyph's centre. Push the day number up by half a font-pixel (in face
    // coords, scaled by DAY_TEXT_SCALE) to align the visible "5" with the
    // tile centre.
    private static final float DAY_OPTICAL_NUDGE = 0.5f * DAY_TEXT_SCALE;

    // ── Block-face scale ────────────────────────────────────────────────────
    // 1/16 maps "1 block face" to "16 pixel units" so all the constants above
    // can be expressed in friendly pixel numbers.
    private static final float FACE_SCALE = 1.0f / 16.0f;

    private final Font font;

    public CalendarBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public void render(CalendarBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light, int overlay) {
        CalendarClientStore.Snapshot snap = CalendarClientStore.get();
        if (snap == null) return;

        BlockState state = be.getBlockState();
        AttachFace face = state.getValue(CalendarBlock.ATTACH_FACE);
        Direction facing = state.getValue(CalendarBlock.FACING);

        String monthName = snap.monthComponent().getString();
        String dayStr = Integer.toString(snap.dayOfMonth());

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        orientToFace(poseStack, face, facing);
        poseStack.scale(FACE_SCALE, FACE_SCALE, FACE_SCALE);

        // In this frame: +X is "right" along the face, +Y is "up" on the face
        // in world space, +Z is "out" toward the viewer. All coordinates that
        // follow are in pixel units of the block face. The banner and tile are
        // real 3D geometry (see block model), so we only draw the text overlays.

        // Text frame: flip Y so Font's natural +Y-down draws upright in world,
        // and push slightly toward the viewer to clear the protruding 3D faces.
        poseStack.pushPose();
        poseStack.translate(0, 0, TEXT_Z_OFFSET);
        poseStack.scale(1f, -1f, 1f);

        drawCenteredText(poseStack, buffers, monthName,
                /*worldY=*/ (BANNER_Y1 + BANNER_Y2) * 0.5f,
                MONTH_TEXT_SCALE,
                /*maxWidth=*/ (BANNER_X2 - BANNER_X1) - 2f * TEXT_INSET,
                MONTH_FG, light);
        drawCenteredText(poseStack, buffers, dayStr,
                /*worldY=*/ (TILE_Y1 + TILE_Y2) * 0.5f + DAY_OPTICAL_NUDGE,
                DAY_TEXT_SCALE,
                /*maxWidth=*/ (TILE_X2 - TILE_X1) - 2f * TEXT_INSET,
                DAY_FG, light);

        poseStack.popPose();
        poseStack.popPose();
    }

    /**
     * Draw text centred horizontally at x=0 and vertically at the requested
     * face-Y. Caller has flipped pose-Y already; we translate to {@code -worldY}
     * so the text lands at the requested world-Y.
     *
     * <p>{@code baseScale} is the desired font multiplier; the actual scale is
     * shrunk if needed so the rendered text width never exceeds {@code maxWidth}
     * face-pixels. Long month names (e.g. "Vendémiaire") would otherwise spill
     * past the parchment inset and out of the block.</p>
     */
    private void drawCenteredText(PoseStack poseStack, MultiBufferSource buffers,
                                  String text, float worldY,
                                  float baseScale, float maxWidth,
                                  int color, int light) {
        int textWidth = font.width(text);
        float scale = baseScale;
        if (textWidth > 0 && textWidth * scale > maxWidth) {
            scale = maxWidth / textWidth;
        }
        poseStack.pushPose();
        poseStack.translate(0, -worldY, 0);
        poseStack.scale(scale, scale, 1f);
        Matrix4f matrix = poseStack.last().pose();
        float halfWidth = textWidth * 0.5f;
        font.drawInBatch(text, -halfWidth, -font.lineHeight * 0.5f,
                color, false, matrix, buffers,
                Font.DisplayMode.NORMAL, 0, light);
        poseStack.popPose();
    }

    /**
     * After {@code translate(0.5, 0.5, 0.5)}, align pose +Z with the visible
     * face's outward direction and step the matrix to just outside the
     * banner/tile front plane. Subtleties recorded the hard way:
     * <ul>
     *   <li>Yaw is {@code -facing.toYRot()}: blockstate {@code y} rotations are
     *       applied as a negative (clockwise) model rotation, and the glyph
     *       quad's default +Z normal must end up matching the banner's -Z front
     *       normal (physical model rotation + 180°). North/south are 180°-apart
     *       so the sign doesn't matter, but east/west break if the yaw isn't
     *       negated, landing the text on the opposite side of the block.</li>
     *   <li>The visible face sits OPPOSITE the FACING direction from the block
     *       centre (the panel hangs against the wall opposite the viewer), so
     *       the depth step is in pose {@code -Z}; overshooting puts text into
     *       the panel volume where the block model hides it.</li>
     *   <li>Wall variant: banner/tile front faces protrude to model z=14.5,
     *       which is (14.5-8)/16 = 0.40625 blocks south of block centre. We
     *       land the text plane exactly there, then {@link #TEXT_Z_OFFSET}
     *       nudges the glyphs a hair forward so the text reads as painted
     *       onto the banner/tile, not floating in front of them.</li>
     *   <li>Floor variant: parchment top sits at model y=0.5, so the offset
     *       from centre is (8-0.5)/16 = 0.46875.</li>
     * </ul>
     */
    private static final float WALL_FACE_DEPTH  = -0.40625f;
    private static final float FLOOR_FACE_DEPTH = -0.46875f;

    private static void orientToFace(PoseStack poseStack, AttachFace face, Direction facing) {
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));

        if (face == AttachFace.WALL) {
            poseStack.translate(0.0, 0.0, WALL_FACE_DEPTH);
        } else if (face == AttachFace.CEILING) {
            // Floor model flipped (x:180 in the blockstate): tip the text frame
            // the other way so it lands on the now-downward parchment face.
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
            poseStack.translate(0.0, 0.0, FLOOR_FACE_DEPTH);
        } else {
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
            poseStack.translate(0.0, 0.0, FLOOR_FACE_DEPTH);
        }
    }
}

package com.aetherianartificer.townstead.client.gui.dialogue;

import com.aetherianartificer.townstead.client.camera.DialogueCameraController;
import com.aetherianartificer.townstead.client.gui.dialogue.DialogueAccessibility;
import com.aetherianartificer.townstead.client.gui.dialogue.effect.DialogueEffects;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Memories;
//? if neoforge {
import net.conczin.mca.network.Network;
//?} else {
/*import net.conczin.mca.cobalt.network.NetworkHandler;
*///?}
import net.conczin.mca.network.c2s.InteractionCloseRequest;
import net.conczin.mca.network.c2s.InteractionDialogueInitMessage;
import net.conczin.mca.network.c2s.InteractionDialogueMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * RPG-style dialogue screen that replaces MCA's centered dialogue UI.
 * Shows a bottom dialogue box with typewriter text, right-side choices,
 * and a camera focused on the villager.
 *
 * <p>The dialogue flows automatically: text plays in the box, choices appear
 * when available, and only stops when there are no more choices (the player
 * dismisses the final line).</p>
 */
public class RpgDialogueScreen extends Screen {
    private static final int CONVERSATION_TIMEOUT_TICKS = 60;

    private final VillagerLike<?> villager;
    private final UUID villagerUUID;

    private final DialogueBox dialogueBox = new DialogueBox();
    private final ChoicePanel choicePanel = new ChoicePanel();
    private DialogueCameraController cameraController;

    private String dialogQuestionId;
    private List<String> dialogAnswers;
    private DialogueState state = DialogueState.AWAITING_DIALOGUE;
    private int awaitingResponseTimer;
    private boolean userInitiatedClose;
    private boolean initialized;
    private boolean debugEffects;
    private int debugEffectIndex;

    private enum DialogueState {
        AWAITING_DIALOGUE,
        TYPEWRITER_PLAYING,
        CHOICES_VISIBLE,
        AWAITING_RESPONSE,
        ENDING,
        CLOSING
    }

    public RpgDialogueScreen(VillagerLike<?> villager) {
        super(Component.literal("Dialogue"));
        this.villager = villager;
        this.villagerUUID = villager.asEntity().getUUID();
    }

    @Override
    protected void init() {
        dialogueBox.layout(width, height);
        dialogueBox.setVillagerName(villager.asEntity().getDisplayName());
        choicePanel.layout(width, height, dialogueBox.getY());

        if (!initialized) {
            initialized = true;
            if (DialogueAccessibility.cameraEnabled()) {
                cameraController = new DialogueCameraController(villager.asEntity());
            }
            //? if neoforge {
            Network.sendToServer(new InteractionDialogueInitMessage(villagerUUID));
            //?} else {
            /*NetworkHandler.sendToServer(new InteractionDialogueInitMessage(villagerUUID));
            *///?}
        }
    }

    private int particleTimer;

    @Override
    public void tick() {
        dialogueBox.tick();
        choicePanel.tick();
        if (cameraController != null) cameraController.tick(); // for restore completion tracking
        tickParticles();

        switch (state) {
            case TYPEWRITER_PLAYING -> {
                if (dialogueBox.getTypewriter().isComplete()) {
                    if (dialogAnswers != null && !dialogAnswers.isEmpty()) {
                        // Choices are ready — show them alongside the current text
                        state = DialogueState.CHOICES_VISIBLE;
                        choicePanel.setVisible(true);
                    } else {
                        // No choices — this is the last line, player dismisses
                        state = DialogueState.ENDING;
                    }
                }
            }
            case AWAITING_RESPONSE -> {
                awaitingResponseTimer--;
                if (awaitingResponseTimer <= 0) {
                    closeByUser();
                }
            }
            case ENDING -> {
                // Wait for player to dismiss
            }
            case CLOSING -> {
                if (cameraController == null || cameraController.isRestoreComplete()) {
                    userInitiatedClose = true;
                    onClose();
                }
            }
            default -> {}
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Hide the HUD (crosshair, hotbar, etc.) during dialogue
        Objects.requireNonNull(minecraft).options.hideGui = true;

        // Update camera every frame for smooth interpolation
        if (cameraController != null) cameraController.update();

        if (state == DialogueState.CLOSING) return;
        dialogueBox.render(graphics, font);
        choicePanel.render(graphics, font, mouseX, mouseY);
        renderHearts(graphics);

        if (debugEffects) {
            DialogueEffects[] all = DialogueEffects.all();
            String label = "[Debug] Effect: " + all[debugEffectIndex].getDisplayName() + " (PgUp/PgDn to cycle, F8 to exit)";
            graphics.drawString(font, label, 4, 4, 0xFFFF8800);
        }
    }


    private void renderHearts(GuiGraphics graphics) {
        Memories memory = villager.getVillagerBrain().getMemoriesForPlayer(
                Objects.requireNonNull(minecraft).player);
        int hearts = memory.getHearts();
        int color = hearts < 0 ? 0xFFAA0000 : hearts >= 100 ? 0xFFFFD700 : 0xFFFF6666;
        String heartsText = "\u2764 " + hearts;
        int textWidth = font.width(heartsText);
        int hx = dialogueBox.getX() + dialogueBox.getWidth() - 8 - textWidth;
        int hy = dialogueBox.getY() + 8;
        graphics.drawString(font, heartsText, hx, hy, color);
    }

    //? if >=1.21 {
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }
    //?} else {
    /*@Override
    public void renderBackground(GuiGraphics guiGraphics) {
    }
    *///?}

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    //? if >=1.21 {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (choicePanel.mouseScrolled(scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (choicePanel.mouseScrolled(scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }
    *///?}

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        if (state == DialogueState.TYPEWRITER_PLAYING) {
            // If paused on a page boundary, advance to next page; otherwise skip typewriter
            dialogueBox.getTypewriter().skipToEnd();
            return true;
        }
        if (state == DialogueState.ENDING && dialogueBox.getTypewriter().hasMorePages()) {
            dialogueBox.getTypewriter().advancePage();
            return true;
        }
        if (state == DialogueState.CHOICES_VISIBLE && choicePanel.mouseClicked(mouseX, mouseY)) {
            handleChoiceSelection();
            return true;
        }
        if (state == DialogueState.ENDING) {
            closeByUser();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeByUser();
            return true;
        }
        if (state == DialogueState.TYPEWRITER_PLAYING) {
            if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER) {
                dialogueBox.getTypewriter().skipToEnd();
                return true;
            }
        }
        if (state == DialogueState.ENDING && dialogueBox.getTypewriter().hasMorePages()) {
            if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER) {
                dialogueBox.getTypewriter().advancePage();
                return true;
            }
        }
        if (state == DialogueState.CHOICES_VISIBLE) {
            if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_W) {
                choicePanel.moveSelection(-1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_S) {
                choicePanel.moveSelection(1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
                handleChoiceSelection();
                return true;
            }
        }
        if (state == DialogueState.ENDING) {
            if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER) {
                closeByUser();
                return true;
            }
        }
        // Debug: F8 toggles effect debug, PageUp/PageDown cycles effects
        if (keyCode == GLFW.GLFW_KEY_F8) {
            debugEffects = !debugEffects;
            if (!debugEffects) {
                dialogueBox.setEffect(DialogueEffects.NORMAL);
                debugEffectIndex = 0;
            }
            return true;
        }
        if (debugEffects) {
            DialogueEffects[] all = DialogueEffects.all();
            if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
                debugEffectIndex = Math.floorMod(debugEffectIndex - 1, all.length);
                dialogueBox.setEffect(all[debugEffectIndex]);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
                debugEffectIndex = Math.floorMod(debugEffectIndex + 1, all.length);
                dialogueBox.setEffect(all[debugEffectIndex]);
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void tickParticles() {
        if (state == DialogueState.CLOSING || state == DialogueState.AWAITING_DIALOGUE) return;
        if (!DialogueAccessibility.particlesEnabled()) return;
        if (!(dialogueBox.getEffect() instanceof DialogueEffects effect)) return;
        SimpleParticleType particle = effect.getParticleType();
        if (particle == null) return;

        particleTimer++;
        if (particleTimer % 10 != 0) return; // Spawn every half second

        Entity entity = villager.asEntity();
        if (entity.level() == null) return;
        double px = entity.getX() + (entity.level().random.nextDouble() - 0.5) * 1.2;
        double py = entity.getEyeY() + (entity.level().random.nextDouble() - 0.3) * 0.8;
        double pz = entity.getZ() + (entity.level().random.nextDouble() - 0.5) * 1.2;
        entity.level().addParticle(particle, px, py, pz, 0, 0.02, 0);
    }

    private void closeByUser() {
        if (state == DialogueState.CLOSING) return;
        state = DialogueState.CLOSING;
        if (cameraController != null) cameraController.beginRestore();
        choicePanel.setVisible(false);
    }

    @Override
    public void removed() {
        super.removed();
        if (!userInitiatedClose) {
            // Server closed us (stopInteracting → closeContainer → setScreen(null)).
            // Re-open so the final dialogue line can display.
            Minecraft mc = Minecraft.getInstance();
            mc.tell(() -> mc.setScreen(this));
        }
    }

    @Override
    public void onClose() {
        if (!userInitiatedClose) {
            return;
        }
        Objects.requireNonNull(this.minecraft).options.hideGui = false;
        this.minecraft.setScreen(null);
        //? if neoforge {
        Network.sendToServer(new InteractionCloseRequest(villagerUUID));
        //?} else {
        /*NetworkHandler.sendToServer(new InteractionCloseRequest(villagerUUID));
        *///?}
    }

    // --- Called by ClientHandlerImplMixin ---

    public void setDialogue(String questionId, List<String> answers) {
        this.dialogQuestionId = questionId;
        this.dialogAnswers = answers;
        choicePanel.setChoices(questionId, answers, font);
        choicePanel.layout(width, height, dialogueBox.getY());
        choicePanel.setVisible(false);

        // If typewriter is already done, show choices immediately
        if (dialogueBox.getTypewriter().isComplete() && dialogueBox.getTypewriter().hasText()) {
            if (!answers.isEmpty()) {
                state = DialogueState.CHOICES_VISIBLE;
                choicePanel.setVisible(true);
            }
        }
    }

    public void setLastPhrase(Component questionText, boolean silent) {
        //? if >=1.21 {
        Component text = villager.transformMessage(questionText);
        //?} else {
        /*Component text = villager.transformMessage(questionText.copy());
        *///?}

        // Silent text = a question prompt accompanying choices (e.g., the "main" greeting).
        // Non-silent text = the villager's actual spoken response.
        // If we already have a response showing, skip silent text — the response
        // stays visible in the dialogue box while choices appear alongside it.
        if (silent && dialogueBox.getTypewriter().hasText()) {
            return;
        }

        dialogueBox.setText(text, font);
        choicePanel.setVisible(false);
        state = DialogueState.TYPEWRITER_PLAYING;
        awaitingResponseTimer = 0;
        if (!silent) {
            speakDisplayedText(text);
        }
        narrateText(text);
    }

    public boolean isVillager(UUID uuid) {
        return villagerUUID.equals(uuid);
    }

    public void setFinalPhrase(Component message) {
        // Terminal dialogue line — sent via VillagerMessage instead of
        // InteractionDialogueQuestionResponse. Already transformed by server.
        dialogueBox.setText(message, font);
        choicePanel.setVisible(false);
        dialogAnswers = null;
        state = DialogueState.TYPEWRITER_PLAYING;
        awaitingResponseTimer = 0;
        speakDisplayedText(message);
        narrateText(message);
    }

    private void speakDisplayedText(Component text) {
        if (!(villager.asEntity() instanceof VillagerEntityMCA mca)) return;
        TypewriterText.DisplayText displayText = TypewriterText.resolveDisplayText(text);
        TownsteadLiteralTts.speak(displayText.component().getString(), mca);
    }

    private void narrateText(Component text) {
        if (DialogueAccessibility.narratorEnabled()) {
            String clean = com.aetherianartificer.townstead.client.gui.dialogue.effect.EffectTagParser
                    .stripTags(text.getString());
            String name = villager.asEntity().getDisplayName().getString();
            try {
                com.mojang.text2speech.Narrator.getNarrator().say(name + ": " + clean, true);
            } catch (Exception ignored) {
                // Narrator not available on this platform
            }
        }
    }

    private void handleChoiceSelection() {
        ChoicePanel.SelectionResult result = choicePanel.select();
        switch (result.type()) {
            case ANSWER -> selectChoice(result.mcaAnswer());
            case SUB_MENU -> choicePanel.openSubMenu(result.subMenuId(), font);
            case BACK -> choicePanel.goBack(font);
            case NONE -> {}
        }
    }

    private void selectChoice(String choice) {
        if (choice == null || dialogQuestionId == null) return;
        //? if neoforge {
        Network.sendToServer(new InteractionDialogueMessage(villagerUUID, dialogQuestionId, choice));
        //?} else {
        /*NetworkHandler.sendToServer(new InteractionDialogueMessage(villagerUUID, dialogQuestionId, choice));
        *///?}
        choicePanel.setVisible(false);
        dialogAnswers = null;
        dialogQuestionId = null;
        state = DialogueState.AWAITING_RESPONSE;
        awaitingResponseTimer = CONVERSATION_TIMEOUT_TICKS;
    }
}

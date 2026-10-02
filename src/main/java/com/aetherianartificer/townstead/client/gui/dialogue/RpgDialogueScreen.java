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
    private static final int CONVERSATION_TIMEOUT_TICKS = 20 * 20;

    private final VillagerLike<?> villager;
    private final UUID villagerUUID;

    private final DialogueBox dialogueBox = new DialogueBox();
    private final ChoicePanel choicePanel = new ChoicePanel();
    private final DialogueLog log = new DialogueLog();
    /** Each speaker's own theme id, by entity id, as the server reported it. */
    private final java.util.Map<Integer, String> speakerThemes = new java.util.HashMap<>();
    /** Ticks until held skip moves the conversation on again. */
    private int skipCooldown;
    private DialogueCameraController cameraController;
    /** Who is talking: the villager, or someone else in a story scene. Null means the villager. */
    private Entity speaker;

    private String dialogQuestionId;
    private List<String> dialogAnswers;
    private DialogueState state = DialogueState.AWAITING_DIALOGUE;
    private int awaitingResponseTimer;
    private boolean userInitiatedClose;
    private boolean initialized;
    private boolean debugEffects;
    private int debugEffectIndex;
    /** The main menu's answers, kept so a finished story can hand the player back to them. */
    private List<String> mainAnswers;
    private boolean storyActive;

    private enum DialogueState {
        AWAITING_DIALOGUE,
        TYPEWRITER_PLAYING,
        CHOICES_VISIBLE,
        AWAITING_RESPONSE,
        AWAITING_LATE_CONTENT,
        /** A story line is shown and another follows; the player moves on when ready. */
        STORY_NEXT,
        ENDING,
        CLOSING
    }

    private static final int LATE_CONTENT_TIMEOUT_TICKS = 40;

    public RpgDialogueScreen(VillagerLike<?> villager) {
        super(Component.translatable("townstead.dialogue.title"));
        this.villager = villager;
        this.villagerUUID = villager.asEntity().getUUID();
        if (villager.asEntity() instanceof VillagerEntityMCA mca
                && com.aetherianartificer.townstead.switchboard.Systems.on(com.aetherianartificer.townstead.switchboard.Systems.CAREERS)
                && com.aetherianartificer.townstead.profession.career.CareerTreeOpener.isScribe(mca)
                && com.aetherianartificer.townstead.profession.career.CareerTreeOpener.isOnDuty(mca)) {
            choicePanel.setShowCareersEntry(true);
        }
    }

    @Override
    protected void init() {
        dialogueBox.layout(width, height);
        // The header names whoever you are talking to, so it shows their full name: the
        // family name their culture gave them, not just the given name MCA tracks.
        dialogueBox.setVillagerName(
                com.aetherianartificer.townstead.client.naming.ClientNames.displayName(speaker()));
        choicePanel.layout(width, height, dialogueBox.getY());
        applyTheme();

        if (!initialized) {
            initialized = true;
            if (DialogueAccessibility.cameraEnabled()) {
                cameraController = new DialogueCameraController(villager.asEntity());
            }
            // The story offer goes first: for a Persona it also tells the server this player is
            // someone they know, before MCA picks its opening from that memory.
            sendStory(com.aetherianartificer.townstead.story.net.StoryC2SPayload.OFFER, 0);
            //? if neoforge {
            Network.sendToServer(new InteractionDialogueInitMessage(villagerUUID));
            //?} else {
            /*NetworkHandler.sendToServer(new InteractionDialogueInitMessage(villagerUUID));
            *///?}
            sendDialogueState(true);
        }
    }

    private int particleTimer;

    @Override
    public void tick() {
        dialogueBox.tick();
        choicePanel.tick();
        if (cameraController != null) cameraController.tick(); // for restore completion tracking
        tickParticles();
        tickHeldSkip();

        switch (state) {
            case TYPEWRITER_PLAYING -> {
                if (dialogueBox.getTypewriter().isComplete()) {
                    if (storyActive && (dialogAnswers == null || dialogAnswers.isEmpty())) {
                        state = DialogueState.STORY_NEXT;
                    } else if (dialogAnswers != null && !dialogAnswers.isEmpty()) {
                        // Choices are ready — show them alongside the current text
                        state = DialogueState.CHOICES_VISIBLE;
                        choicePanel.setVisible(true);
                    } else {
                        // No choices — this is the last line, player dismisses
                        state = DialogueState.ENDING;
                    }
                }
            }
            case AWAITING_RESPONSE, AWAITING_LATE_CONTENT -> {
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
        if (log.isOpen()) {
            log.render(graphics, font, dialogueBox.getX(), 20, dialogueBox.getWidth(),
                    dialogueBox.getY() + dialogueBox.getHeight());
            return;
        }
        dialogueBox.render(graphics, font);
        choicePanel.render(graphics, font, mouseX, mouseY);
        if (choicePanel.isVisible() && com.aetherianartificer.townstead.api.impl.v1.client.ChoicePanelPaintHooks.any()) {
            com.aetherianartificer.townstead.api.impl.v1.client.ChoicePanelPaintHooks.fire(
                    new com.aetherianartificer.townstead.api.v1.client.ChoicePanelPaint(this, graphics, font,
                            choicePanel.panelX(), choicePanel.panelY(), choicePanel.panelWidth(),
                            choicePanel.panelHeight(), choicePanel.fadeAlpha(), choicePanel.visibleRows()));
        }
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
        if (scrollLog(mouseX, mouseY, scrollY)) return true;
        if (choicePanel.mouseScrolled(scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (scrollLog(mouseX, mouseY, scrollY)) return true;
        if (choicePanel.mouseScrolled(scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }
    *///?}

    /** Scrolls an open log; scrolling up over the dialogue box opens it. */
    private boolean scrollLog(double mouseX, double mouseY, double scrollY) {
        if (log.isOpen()) {
            log.scroll(scrollY);
            return true;
        }
        if (scrollY > 0 && !log.isEmpty()
                && mouseX >= dialogueBox.getX() && mouseX < dialogueBox.getX() + dialogueBox.getWidth()
                && mouseY >= dialogueBox.getY() && mouseY < dialogueBox.getY() + dialogueBox.getHeight()) {
            log.setOpen(true);
            return true;
        }
        return false;
    }

    /** While the skip key is held, lines run past until a choice or the last line. */
    private void tickHeldSkip() {
        if (log.isOpen() || !com.aetherianartificer.townstead.client.TownsteadKeybinds.isHeld(
                minecraft, com.aetherianartificer.townstead.client.TownsteadKeybinds.DIALOGUE_SKIP)) {
            skipCooldown = 0;
            return;
        }
        if (skipCooldown-- > 0) return;
        skipCooldown = 2;
        TypewriterText typewriter = dialogueBox.getTypewriter();
        if (state == DialogueState.TYPEWRITER_PLAYING) {
            typewriter.skipToEnd();
        } else if (state == DialogueState.STORY_NEXT) {
            if (typewriter.hasMorePages()) typewriter.advancePage();
            else requestNextStoryLine();
        } else if (state == DialogueState.ENDING && typewriter.hasMorePages()) {
            typewriter.advancePage();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (log.isOpen()) {
            log.setOpen(false);
            return true;
        }
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        if (state == DialogueState.TYPEWRITER_PLAYING) {
            // If paused on a page boundary, advance to next page; otherwise skip typewriter
            dialogueBox.getTypewriter().skipToEnd();
            return true;
        }
        if ((state == DialogueState.ENDING || state == DialogueState.STORY_NEXT)
                && dialogueBox.getTypewriter().hasMorePages()) {
            dialogueBox.getTypewriter().advancePage();
            return true;
        }
        if (state == DialogueState.STORY_NEXT) {
            requestNextStoryLine();
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
        if (log.isOpen()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE
                    || com.aetherianartificer.townstead.client.TownsteadKeybinds.DIALOGUE_LOG.matches(keyCode, scanCode)) {
                log.setOpen(false);
            } else if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_PAGE_UP) {
                log.scroll(keyCode == GLFW.GLFW_KEY_UP ? 1 : 4);
            } else if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
                log.scroll(keyCode == GLFW.GLFW_KEY_DOWN ? -1 : -4);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeByUser();
            return true;
        }
        if (com.aetherianartificer.townstead.client.TownsteadKeybinds.DIALOGUE_LOG.matches(keyCode, scanCode)
                && !log.isEmpty()) {
            log.setOpen(true);
            return true;
        }
        if (state == DialogueState.TYPEWRITER_PLAYING) {
            if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER) {
                dialogueBox.getTypewriter().skipToEnd();
                return true;
            }
        }
        if ((state == DialogueState.ENDING || state == DialogueState.STORY_NEXT)
                && dialogueBox.getTypewriter().hasMorePages()) {
            if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER) {
                dialogueBox.getTypewriter().advancePage();
                return true;
            }
        }
        if (state == DialogueState.STORY_NEXT) {
            if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER) {
                requestNextStoryLine();
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
            // Number keys pick the Nth visible row, only while the panel paints the numbers.
            if (choicePanel.isNumbered() && keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
                List<com.aetherianartificer.townstead.api.v1.client.ChoiceRow> rows = choicePanel.visibleRows();
                int ordinal = keyCode - GLFW.GLFW_KEY_1;
                if (ordinal < rows.size()) apiSelectChoice(rows.get(ordinal).index());
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

        Entity entity = speaker();
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
        if (userInitiatedClose) return;

        Minecraft mc = Minecraft.getInstance();
        if (state == DialogueState.TYPEWRITER_PLAYING || state == DialogueState.ENDING
                || state == DialogueState.STORY_NEXT) {
            // Content is rolling; re-open so the player can finish reading.
            dialogAnswers = null;
            choicePanel.setVisible(false);
            mc.tell(() -> { if (mc.screen == null) mc.setScreen(this); });
        } else if (state == DialogueState.AWAITING_RESPONSE) {
            // Server closed before content arrived. Some MCA actions dispatch
            // content asynchronously (e.g. rumors → command:location runs off
            // the server thread and sends VillagerMessage after the close).
            // Stay open briefly so a late message can be routed in.
            state = DialogueState.AWAITING_LATE_CONTENT;
            awaitingResponseTimer = LATE_CONTENT_TIMEOUT_TICKS;
            mc.tell(() -> { if (mc.screen == null) mc.setScreen(this); });
        } else {
            // Nothing pending — accept the close.
            userInitiatedClose = true;
            mc.options.hideGui = false;
            if (cameraController != null) cameraController.snapToOriginal();
            sendDialogueState(false);
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
        sendDialogueState(false);
    }

    private void sendDialogueState(boolean open) {
        Entity entity = villager.asEntity();
        if (entity == null) return;
        if (!open) sendStory(com.aetherianartificer.townstead.story.net.StoryC2SPayload.CLOSE, 0);
        var payload = new com.aetherianartificer.townstead.reaction.net.DialogueStateC2SPayload(
                entity.getId(), open);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
        *///?}
    }

    // --- Called by ClientHandlerImplMixin ---

    private boolean persona;
    /** The MCA main-menu answers this Persona hides, as the server listed them. */
    private java.util.Set<String> personaHidden = java.util.Set.of();
    /** The Persona's own greeting, said in place of MCA's; empty for none. */
    private String personaGreeting = "";
    /** Whether the box is showing MCA's greeting, so a late Persona greeting can take its place. */
    private boolean showingMainGreeting;

    private List<String> forSpeaker(String questionId, List<String> answers) {
        if (!persona || answers == null || !DialogueMenuOrganizer.isMainQuestion(questionId)) return answers;
        return answers.stream().filter(a -> !personaHidden.contains(a)).toList();
    }

    public void setDialogue(String questionId, List<String> answers) {
        answers = forSpeaker(questionId, answers);
        if (DialogueMenuOrganizer.isMainQuestion(questionId)) mainAnswers = answers;
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
        boolean mainGreeting = !silent && DialogueMenuOrganizer.isMainQuestion(dialogQuestionId);
        if (mainGreeting && persona && !personaGreeting.isEmpty()) text = Component.literal(storyText(personaGreeting));

        // Silent text on the main greeting accompanies the menu and must not overwrite
        // a villager's just-spoken response. Silent sub-question prompts (divorce/procreate/
        // adopt confirms) are genuine questions and must always show.
        if (silent && dialogueBox.getTypewriter().hasText()
                && DialogueMenuOrganizer.isMainQuestion(dialogQuestionId)) {
            return;
        }

        // Silent prompts are the player's own thoughts/menus, not villager speech: drop the name plate.
        setSpeaker(villager.asEntity());
        dialogueBox.setNameVisible(!silent);
        dialogueBox.setText(text, font);
        showingMainGreeting = mainGreeting;
        if (!silent) logLine(text);
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

    // Match "§7<name>: §o<line>" and return the inner line, or null.
    public Component tryExtractExternalVillagerLine(Component message) {
        String full = message.getString();
        if (full.isEmpty() || full.charAt(0) != '§') return null;
        String name = villager.asEntity().getDisplayName().getString();
        String prefix = "§7" + name + ": §o";
        if (!full.startsWith(prefix)) return null;
        String body = full.substring(prefix.length());
        if (body.isEmpty()) return null;
        return Component.literal(body);
    }

    public void setIncomingChatLine(Component line) {
        setSpeaker(villager.asEntity());
        dialogueBox.setNameVisible(true);
        dialogueBox.setText(line, font);
        logLine(line);
        showingMainGreeting = false;
        choicePanel.setVisible(false);
        state = DialogueState.TYPEWRITER_PLAYING;
        awaitingResponseTimer = 0;
        // No speech here: this line reached the chat listener, which means MCA's own handler was
        // left to run and speaks it a moment later. Saying it again would say it twice.
        narrateText(line);
    }

    public void setFinalPhrase(Component message) {
        // Terminal dialogue line — sent via VillagerMessage instead of
        // InteractionDialogueQuestionResponse. Already transformed by server.
        setSpeaker(villager.asEntity());
        dialogueBox.setNameVisible(true);
        dialogueBox.setText(message, font);
        logLine(message);
        showingMainGreeting = false;
        choicePanel.setVisible(false);
        dialogAnswers = null;
        state = DialogueState.TYPEWRITER_PLAYING;
        awaitingResponseTimer = 0;
        speakDisplayedText(message);
        narrateText(message);
    }

    private void logLine(Component text) {
        log.addLine(com.aetherianartificer.townstead.client.naming.ClientNames.displayName(speaker()),
                TypewriterText.resolveDisplayText(text).component());
    }

    private void speakDisplayedText(Component text) {
        if (!(speaker() instanceof VillagerEntityMCA mca)) return;
        // Resolving first is what registers the line's translation key with MCA, so the lookup
        // inside speakLine can find it and hand a stock MCA line back to MCA's own speech manager.
        // Clearing first keeps a line MCA does not own from claiming the previous line's key.
        com.aetherianartificer.townstead.client.tts.McaSpeechKeys.clearPending();
        TypewriterText.DisplayText displayText = TypewriterText.resolveDisplayText(text);
        TownsteadLiteralTts.speakLine(text, displayText.component().getString(), mca);
    }

    private void narrateText(Component text) {
        if (DialogueAccessibility.narratorEnabled()) {
            String clean = com.aetherianartificer.townstead.client.gui.dialogue.effect.EffectTagParser
                    .stripTags(text.getString());
            String name = speaker().getDisplayName().getString();
            try {
                com.mojang.text2speech.Narrator.getNarrator().say(name + ": " + clean, true);
            } catch (Exception ignored) {
                // Narrator not available on this platform
            }
        }
    }

    /** Client API: whether choices are showing. */
    public boolean apiChoicesVisible() {
        return state == DialogueState.CHOICES_VISIBLE && choicePanel.isVisible();
    }

    /** Client API: the visible choice rows. */
    public List<com.aetherianartificer.townstead.api.v1.client.ChoiceRow> apiVisibleChoices() {
        return apiChoicesVisible() ? choicePanel.visibleRows() : List.of();
    }

    /** Client API: select a row through the native hub, sub-menu and back routine. */
    public boolean apiSelectChoice(int index) {
        if (!apiChoicesVisible() || !choicePanel.selectIndex(index)) return false;
        handleChoiceSelection();
        return true;
    }

    private void handleChoiceSelection() {
        Component label = choicePanel.selectedLabel();
        ChoicePanel.SelectionResult result = choicePanel.select();
        if (result.type() == ChoicePanel.SelectionResult.Type.ANSWER && label != null) log.addChoice(label);
        switch (result.type()) {
            case ANSWER -> selectChoice(result.mcaAnswer());
            case SUB_MENU -> choicePanel.openSubMenu(result.subMenuId(), font);
            case BACK -> choicePanel.goBack(font);
            case NONE -> {}
        }
    }

    private void selectChoice(String choice) {
        if (choice == null || dialogQuestionId == null) return;
        if (ChoicePanel.STORY_ANSWER.equals(choice)) {
            sendStory(com.aetherianartificer.townstead.story.net.StoryC2SPayload.TALK, 0);
            awaitStory();
            return;
        }
        if (choice.startsWith(ChoicePanel.STORY_CHOICE_PREFIX)) {
            int index;
            try {
                index = Integer.parseInt(choice.substring(ChoicePanel.STORY_CHOICE_PREFIX.length()));
            } catch (NumberFormatException e) {
                return;
            }
            sendStory(com.aetherianartificer.townstead.story.net.StoryC2SPayload.CHOOSE, index);
            awaitStory();
            return;
        }
        if (ChoicePanel.CAREERS_ANSWER.equals(choice)) {
            int counsellorId = villager.asEntity().getId();
            onClose();
            com.aetherianartificer.townstead.profession.career.CareerTreeRequestC2SPayload request =
                    new com.aetherianartificer.townstead.profession.career.CareerTreeRequestC2SPayload(counsellorId);
            //? if neoforge {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(request);
            //?} else {
            /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(request);
            *///?}
            return;
        }
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

    // --- Stories (called by StoryClient) ---

    private Entity speaker() {
        return speaker != null && !speaker.isRemoved() ? speaker : villager.asEntity();
    }

    /** Moves the name plate and the camera to whoever says the next line. */
    private void setSpeaker(Entity entity) {
        if (entity == null || entity == speaker()) return;
        speaker = entity;
        applyTheme();
        dialogueBox.setVillagerName(com.aetherianartificer.townstead.client.naming.ClientNames.displayName(entity));
        if (cameraController != null) cameraController.setTarget(entity);
    }

    private void applyTheme() {
        DialogueTheme theme = DialogueThemes.resolve(speakerThemes.get(speaker().getId()));
        dialogueBox.setTheme(theme);
        choicePanel.setTheme(theme);
    }

    public int villagerEntityId() {
        return villager.asEntity().getId();
    }

    public void onStory(com.aetherianartificer.townstead.story.net.StoryS2CPayload payload) {
        if (payload.kind() != com.aetherianartificer.townstead.story.net.StoryS2CPayload.END) {
            String before = speakerThemes.put(payload.speakerId(), payload.theme());
            if (!payload.theme().equals(before) && payload.speakerId() == speaker().getId()) applyTheme();
        }
        switch (payload.kind()) {
            case com.aetherianartificer.townstead.story.net.StoryS2CPayload.OFFER,
                 com.aetherianartificer.townstead.story.net.StoryS2CPayload.OFFER_PERSONA -> {
                if (payload.kind() == com.aetherianartificer.townstead.story.net.StoryS2CPayload.OFFER_PERSONA && !persona) {
                    persona = true;
                    personaHidden = java.util.Set.copyOf(payload.choices());
                    personaGreeting = payload.greeting();
                    // MCA's greeting got here first: say the Persona's own in its place.
                    if (showingMainGreeting && !personaGreeting.isEmpty() && !storyActive) {
                        Component greeting = Component.literal(storyText(personaGreeting));
                        dialogueBox.setText(greeting, font);
                        logLine(greeting);
                        choicePanel.setVisible(false);
                        state = DialogueState.TYPEWRITER_PLAYING;
                    }
                    if (mainAnswers != null) mainAnswers = forSpeaker("main", mainAnswers);
                    if (dialogAnswers != null && DialogueMenuOrganizer.isMainQuestion(dialogQuestionId)) {
                        dialogAnswers = forSpeaker(dialogQuestionId, dialogAnswers);
                    }
                }
                Component label = !payload.more() ? null : payload.text().isEmpty()
                        ? Component.translatable("townstead.story.talk") : Component.literal(payload.text());
                choicePanel.setStoryEntry(label);
                if (DialogueMenuOrganizer.isMainQuestion(dialogQuestionId) && dialogAnswers != null
                        && choicePanel.isMainHub()) {
                    boolean visible = choicePanel.isVisible();
                    choicePanel.setChoices(dialogQuestionId, dialogAnswers, font);
                    choicePanel.layout(width, height, dialogueBox.getY());
                    choicePanel.setVisible(visible);
                }
            }
            case com.aetherianartificer.townstead.story.net.StoryS2CPayload.LINE -> showStoryLine(payload);
            default -> finishStory();
        }
    }

    private void showStoryLine(com.aetherianartificer.townstead.story.net.StoryS2CPayload payload) {
        boolean last = !payload.more() && payload.choices().isEmpty();
        storyActive = !last;
        choicePanel.setVisible(false);
        if (payload.choices().isEmpty()) {
            dialogAnswers = null;
            dialogQuestionId = null;
        } else {
            dialogQuestionId = ChoicePanel.STORY_QUESTION;
            dialogAnswers = new java.util.ArrayList<>();
            for (int i = 0; i < payload.choices().size(); i++) dialogAnswers.add(ChoicePanel.STORY_CHOICE_PREFIX + i);
            choicePanel.setLiteralChoices(payload.choices().stream().map(RpgDialogueScreen::storyText).toList(),
                    payload::questChoice, font);
            choicePanel.layout(width, height, dialogueBox.getY());
        }
        if (last) restoreMainMenu();
        awaitingResponseTimer = 0;
        if (payload.text().isEmpty()) {
            state = dialogAnswers != null && !dialogAnswers.isEmpty() ? DialogueState.CHOICES_VISIBLE : DialogueState.ENDING;
            choicePanel.setVisible(state == DialogueState.CHOICES_VISIBLE);
            return;
        }
        Component text = Component.literal(storyText(payload.text()));
        Entity by = minecraft == null || minecraft.level == null ? null : minecraft.level.getEntity(payload.speakerId());
        setSpeaker(by == null ? villager.asEntity() : by);
        dialogueBox.setNameVisible(true);
        dialogueBox.setText(text, font);
        logLine(text);
        showingMainGreeting = false;
        state = DialogueState.TYPEWRITER_PLAYING;
        speakDisplayedText(text);
        narrateText(text);
    }

    private static String storyText(String raw) {
        return com.aetherianartificer.townstead.story.StoryText.resolve(raw,
                key -> net.minecraft.client.resources.language.I18n.get(key));
    }

    private void finishStory() {
        storyActive = false;
        setSpeaker(villager.asEntity());
        restoreMainMenu();
        if (dialogueBox.getTypewriter().isComplete() && dialogAnswers != null && !dialogAnswers.isEmpty()) {
            state = DialogueState.CHOICES_VISIBLE;
            choicePanel.setVisible(true);
        } else if (dialogueBox.getTypewriter().isComplete()) {
            state = DialogueState.ENDING;
        }
    }

    /** Puts the villager's main menu back under the story's last line. */
    private void restoreMainMenu() {
        if (mainAnswers == null) return;
        dialogQuestionId = "main";
        dialogAnswers = mainAnswers;
        choicePanel.setChoices("main", mainAnswers, font);
        choicePanel.layout(width, height, dialogueBox.getY());
        choicePanel.setVisible(false);
    }

    private void requestNextStoryLine() {
        sendStory(com.aetherianartificer.townstead.story.net.StoryC2SPayload.NEXT, 0);
        awaitStory();
    }

    private void awaitStory() {
        choicePanel.setVisible(false);
        dialogAnswers = null;
        dialogQuestionId = null;
        state = DialogueState.AWAITING_RESPONSE;
        awaitingResponseTimer = CONVERSATION_TIMEOUT_TICKS;
    }

    private void sendStory(byte action, int index) {
        Entity entity = villager.asEntity();
        if (entity == null) return;
        com.aetherianartificer.townstead.client.story.StoryClient.send(
                new com.aetherianartificer.townstead.story.net.StoryC2SPayload(entity.getId(), action, index));
    }
}

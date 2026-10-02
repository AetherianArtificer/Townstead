package com.aetherianartificer.townstead.client.gui.switchboard;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.switchboard.SettingIndex;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The vanilla control for each kind of setting: a green ON / red OFF button, a {@code <  value  >}
 * stepper for choices and wide number ranges, a slider for narrow ranges, and a text box only for
 * lists of ids.
 */
final class SettingControls {
    private SettingControls() {}

    static final int STEP_WIDTH = 20;

    /** One value a text setting can take, offered only when the mod it names is installed. */
    private record Choice(String value, @Nullable String modId) {}

    /** Strings that only ever take one of a few values get a stepper instead of a text box. */
    private static final Map<String, List<Choice>> CHOICES = Map.of(
            "needs.thirst.preferredBackend", List.of(new Choice("auto", null),
                    new Choice("legendary_survival_overhaul", "legendarysurvivaloverhaul"),
                    new Choice("thirst", "thirst"), new Choice("tough_as_nails", "toughasnails")),
            "needs.temperature.preferredBackend", List.of(new Choice("auto", null),
                    new Choice("legendary_survival_overhaul", "legendarysurvivaloverhaul"),
                    new Choice("cold_sweat", "cold_sweat"), new Choice("tough_as_nails", "toughasnails"),
                    new Choice("builtin", null)),
            "calendar.profile", List.of(new Choice("auto", null), new Choice("townstead_calendar:default", null),
                    new Choice("townstead_calendar:serene", "sereneseasons"), new Choice("townstead_calendar:tfc", "tfc"),
                    new Choice("townstead_calendar:ecliptic", "eclipticseasons")));

    /** A named stop on a number setting, such as a pace. */
    private record Stop(double value, String name) {}

    /** Numbers with well-known values get a stepper over those, and any other value shows as Custom. */
    private static final Map<String, List<Stop>> STOPS = Map.of(
            "calendar.agingScale", List.of(new Stop(0.9, "mca"), new Stop(3.0, "quick"),
                    new Stop(8.0, "townstead"), new Stop(20.0, "slow")));

    /** Gap the settings list leaves between the parts of one control. */
    private static final int ROW_GAP = 4;

    static Component on() {
        return CommonComponents.OPTION_ON.copy().withStyle(ChatFormatting.GREEN);
    }

    static Component off() {
        return CommonComponents.OPTION_OFF.copy().withStyle(ChatFormatting.RED);
    }

    static CycleButton<Boolean> onOff(boolean value, int width, Component label, Consumer<Boolean> set) {
        return CycleButton.booleanBuilder(on(), off()).withInitialValue(value).displayOnlyValue()
                .create(0, 0, width, 20, label, (b, v) -> set.accept(v));
    }

    static List<AbstractWidget> build(SettingIndex.Entry entry, Object current, Component label, int width,
                                      Consumer<Object> set) {
        Object def = entry.defaultValue();
        if (def instanceof Boolean) return List.of(onOff((Boolean) current, width, label, set::accept));
        if (def instanceof Enum<?> e) {
            List<Object> values = List.of((Object[]) e.getDeclaringClass().getEnumConstants());
            return stepper(values, current, v -> Component.literal(SettingLabels.pretty(((Enum<?>) v).name())), label, width, set);
        }
        List<Choice> choices = CHOICES.get(entry.key());
        if (choices != null && current instanceof String s) {
            List<Object> values = new ArrayList<>();
            for (Choice choice : choices) {
                if (choice.modId() == null || ModCompat.isLoaded(choice.modId())) values.add(choice.value());
            }
            if (!values.contains(s)) values.add(s);
            return stepper(values, s, v -> Component.literal(choiceName((String) v)), label, width, set);
        }
        List<Stop> stops = STOPS.get(entry.key());
        if (stops != null && current instanceof Number n) {
            String prefix = "townstead.switchboard.stop." + entry.key() + ".";
            List<Object> values = new ArrayList<>();
            for (Stop stop : stops) values.add(stop.value());
            double now = n.doubleValue();
            if (stops.stream().noneMatch(stop -> Math.abs(stop.value() - now) < 1e-9)) {
                int at = 0;
                while (at < values.size() && (Double) values.get(at) < now) at++;
                values.add(at, now);
            }
            return stepper(values, stops.stream().filter(stop -> Math.abs(stop.value() - now) < 1e-9)
                            .findFirst().<Object>map(Stop::value).orElse(now),
                    v -> stops.stream().filter(stop -> Math.abs(stop.value() - (Double) v) < 1e-9).findFirst()
                            .<Component>map(stop -> Component.translatable(prefix + stop.name()))
                            .orElseGet(() -> Component.translatable("townstead.switchboard.stop.custom",
                                    number((Double) v, false))),
                    label, width, set);
        }
        Number[] range = SettingIndex.range(entry);
        if (range != null && current instanceof Number n) {
            double min = range[0].doubleValue();
            double max = range[1].doubleValue();
            boolean integer = def instanceof Integer;
            if (integer ? max - min <= 200 : max - min <= 10) {
                return List.of(new SettingSlider(width, min, max, integer, n.doubleValue(), set));
            }
            return numberStepper(n.doubleValue(), min, max, integer, label, width, set);
        }
        if (range == null && current instanceof Double d) {
            return numberStepper(d, 0, 10, false, label, width, set);
        }
        EditBox box = new EditBox(Minecraft.getInstance().font, 0, 0, width, 20, label);
        box.setMaxLength(4096);
        box.setValue(text(current));
        box.setResponder(t -> {
            Object parsed = SettingIndex.parse(entry, json(def, t));
            box.setTextColor(parsed == null ? 0xFF5555 : 0xE0E0E0);
            if (parsed != null) set.accept(parsed);
        });
        return List.of(box);
    }

    /** {@code <  value  >}: step through a fixed list, wrapping at both ends. */
    static List<AbstractWidget> stepper(List<Object> values, Object current, Function<Object, Component> name,
                                        Component label, int width, Consumer<Object> set) {
        int[] index = {Math.max(0, values.indexOf(current))};
        ValueBox box = new ValueBox(boxWidth(width), label, name.apply(values.get(index[0])));
        Consumer<Integer> move = dir -> {
            index[0] = Math.floorMod(index[0] + dir, values.size());
            box.setValue(name.apply(values.get(index[0])));
            set.accept(values.get(index[0]));
        };
        return List.of(arrow("<", label, () -> move.accept(-1)), box, arrow(">", label, () -> move.accept(1)));
    }

    /** A number with too wide a range for a slider. Shift steps finer. */
    private static List<AbstractWidget> numberStepper(double current, double min, double max, boolean integer,
                                                     Component label, int width, Consumer<Object> set) {
        boolean geometric = !integer && min > 0 && max / min >= 1000;
        double step = Math.max(integer ? 1 : 0.01, Math.pow(10, Math.floor(Math.log10((max - min) / 40))));
        double[] value = {current};
        ValueBox box = new ValueBox(boxWidth(width), label, Component.literal(number(current, integer)));
        Consumer<Integer> move = dir -> {
            boolean fine = Screen.hasShiftDown();
            double next;
            if (geometric) {
                double factor = fine ? 1.1 : 2.0;
                next = dir > 0 ? value[0] * factor : value[0] / factor;
                next = Math.round(next * 100) / 100.0;
            } else {
                double s = fine && step >= 10 ? step / 10 : step;
                next = value[0] + dir * s;
            }
            value[0] = Math.max(min, Math.min(max, next));
            box.setValue(Component.literal(number(value[0], integer)));
            set.accept(integer ? (Object) (int) Math.round(value[0]) : (Object) value[0]);
        };
        return List.of(arrow("<", label, () -> move.accept(-1)), box, arrow(">", label, () -> move.accept(1)));
    }

    /** The value plate's width, so arrows, plate and gaps together match a plain button's width. */
    private static int boxWidth(int width) {
        return width - 2 * STEP_WIDTH - 2 * ROW_GAP;
    }

    private static Button arrow(String glyph, Component label, Runnable action) {
        Button button = Button.builder(Component.literal(glyph), b -> action.run()).size(STEP_WIDTH, 20).build();
        return button;
    }

    private static String number(double value, boolean integer) {
        if (integer) return String.valueOf((long) Math.round(value));
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(Math.round(value * 100) / 100.0);
    }

    private static String choiceName(String raw) {
        String path = raw.contains(":") ? raw.substring(raw.indexOf(':') + 1) : raw;
        return SettingLabels.pretty(path.toUpperCase(Locale.ROOT));
    }

    static String text(Object value) {
        if (value instanceof List<?> items) return String.join(", ", items.stream().map(String::valueOf).toList());
        return String.valueOf(value);
    }

    static JsonElement json(Object def, String text) {
        if (def instanceof List<?>) {
            JsonArray array = new JsonArray();
            for (String part : text.split(",")) {
                if (!part.isBlank()) array.add(part.trim());
            }
            return array;
        }
        return new JsonPrimitive(text);
    }

    /** The middle of a stepper: the value on a sunken dark plate, like MCA's skin picker. */
    static final class ValueBox extends AbstractWidget {
        private final Component label;

        ValueBox(int width, Component label, Component value) {
            super(0, 0, width, 20, value);
            this.label = label;
            this.active = false;
        }

        void setValue(Component value) {
            setMessage(value);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            g.fill(x, y, x + width, y + height, 0xFF000000);
            g.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF2B2B2B);
            g.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, 0xFF1A1A1A);
            // Long values scroll back and forth like vanilla button text rather than spilling out.
            renderScrollingString(g, Minecraft.getInstance().font, getMessage(), x + 3, y, x + width - 3, y + height, 0xFFFFFF);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, Component.empty().append(label).append(": ").append(getMessage()));
        }
    }
}

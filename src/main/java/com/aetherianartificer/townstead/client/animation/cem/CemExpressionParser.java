package com.aetherianartificer.townstead.client.animation.cem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class CemExpressionParser {
    private final String input;
    private final CemVariableStore.Layout layout;
    private int index;

    private CemExpressionParser(String input, CemVariableStore.Layout layout) {
        this.input = input;
        this.layout = layout;
    }

    static CemExpression parse(String input) {
        return parse(input, new CemVariableStore.Layout());
    }

    static CemExpression parse(String input, CemVariableStore.Layout layout) {
        CemExpressionParser parser = new CemExpressionParser(input, layout);
        CemExpression expression = parser.parseOr();
        parser.skipWhitespace();
        if (!parser.done()) {
            throw new IllegalArgumentException("Unexpected token at " + parser.index);
        }
        return expression;
    }

    private CemExpression parseOr() {
        CemExpression left = parseAnd();
        while (match("||")) {
            CemExpression right = parseAnd();
            CemExpression previous = left;
            left = CemExpression.fold(context -> CemAnimationProgram.truthy(previous.evaluate(context)) || CemAnimationProgram.truthy(right.evaluate(context)) ? 1.0D : 0.0D, previous, right);
        }
        return left;
    }

    private CemExpression parseAnd() {
        CemExpression left = parseEquality();
        while (match("&&")) {
            CemExpression right = parseEquality();
            CemExpression previous = left;
            left = CemExpression.fold(context -> CemAnimationProgram.truthy(previous.evaluate(context)) && CemAnimationProgram.truthy(right.evaluate(context)) ? 1.0D : 0.0D, previous, right);
        }
        return left;
    }

    private CemExpression parseEquality() {
        CemExpression left = parseComparison();
        while (true) {
            if (match("==")) {
                CemExpression right = parseComparison();
                CemExpression previous = left;
                left = CemExpression.fold(context -> Math.abs(previous.evaluate(context) - right.evaluate(context)) < 0.00001D ? 1.0D : 0.0D, previous, right);
            } else if (match("!=")) {
                CemExpression right = parseComparison();
                CemExpression previous = left;
                left = CemExpression.fold(context -> Math.abs(previous.evaluate(context) - right.evaluate(context)) >= 0.00001D ? 1.0D : 0.0D, previous, right);
            } else {
                return left;
            }
        }
    }

    private CemExpression parseComparison() {
        CemExpression left = parseAdditive();
        while (true) {
            if (match(">=")) {
                CemExpression right = parseAdditive();
                CemExpression previous = left;
                left = CemExpression.fold(context -> previous.evaluate(context) >= right.evaluate(context) ? 1.0D : 0.0D, previous, right);
            } else if (match("<=")) {
                CemExpression right = parseAdditive();
                CemExpression previous = left;
                left = CemExpression.fold(context -> previous.evaluate(context) <= right.evaluate(context) ? 1.0D : 0.0D, previous, right);
            } else if (match(">")) {
                CemExpression right = parseAdditive();
                CemExpression previous = left;
                left = CemExpression.fold(context -> previous.evaluate(context) > right.evaluate(context) ? 1.0D : 0.0D, previous, right);
            } else if (match("<")) {
                CemExpression right = parseAdditive();
                CemExpression previous = left;
                left = CemExpression.fold(context -> previous.evaluate(context) < right.evaluate(context) ? 1.0D : 0.0D, previous, right);
            } else {
                return left;
            }
        }
    }

    private CemExpression parseAdditive() {
        CemExpression left = parseMultiplicative();
        while (true) {
            if (match("+")) {
                CemExpression right = parseMultiplicative();
                CemExpression previous = left;
                left = CemExpression.fold(context -> previous.evaluate(context) + right.evaluate(context), previous, right);
            } else if (match("-")) {
                CemExpression right = parseMultiplicative();
                CemExpression previous = left;
                left = CemExpression.fold(context -> previous.evaluate(context) - right.evaluate(context), previous, right);
            } else {
                return left;
            }
        }
    }

    private CemExpression parseMultiplicative() {
        CemExpression left = parseUnary();
        while (true) {
            if (match("*")) {
                CemExpression right = parseUnary();
                CemExpression previous = left;
                left = CemExpression.fold(context -> previous.evaluate(context) * right.evaluate(context), previous, right);
            } else if (match("/")) {
                CemExpression right = parseUnary();
                CemExpression previous = left;
                left = CemExpression.fold(context -> previous.evaluate(context) / right.evaluate(context), previous, right);
            } else if (match("%")) {
                CemExpression right = parseUnary();
                CemExpression previous = left;
                left = CemExpression.fold(context -> previous.evaluate(context) % right.evaluate(context), previous, right);
            } else {
                return left;
            }
        }
    }

    private CemExpression parseUnary() {
        if (match("!")) {
            CemExpression value = parseUnary();
            return CemExpression.fold(context -> CemAnimationProgram.truthy(value.evaluate(context)) ? 0.0D : 1.0D, value);
        }
        if (match("-")) {
            CemExpression value = parseUnary();
            return CemExpression.fold(context -> -value.evaluate(context), value);
        }
        if (match("+")) return parseUnary();
        return parsePrimary();
    }

    private CemExpression parsePrimary() {
        skipWhitespace();
        if (match("(")) {
            CemExpression expression = parseOr();
            expect(")");
            return expression;
        }
        if (peekNumber()) return parseNumber();
        String identifier = parseIdentifier();
        // CEM treats `true`/`false` as boolean literals (1/0). Without this, packs
        // like Better Animations that write `is_climbing == true` parse `true` as
        // an unseeded variable (0), so the comparison passes when *not* climbing
        // and the climbing arm pose latches on permanently.
        String lowerId = identifier.toLowerCase(Locale.ROOT);
        if ("true".equals(lowerId)) return new CemExpression.Constant(1.0D);
        if ("false".equals(lowerId)) return new CemExpression.Constant(0.0D);
        skipWhitespace();
        if (match("(")) {
            String method = identifier.toLowerCase(Locale.ROOT);
            if ("nbt".equals(method)) {
                String query = readRawCallBody();
                return CemAnimationProgram.nbt(query);
            }
            List<CemExpression> args = new ArrayList<>();
            if (!peek(")")) {
                do {
                    args.add(parseOr());
                } while (match(","));
            }
            expect(")");
            // Branches may contain NBT reads; only evaluate the selected value.
            if ("if".equals(method)) {
                return context -> {
                    for (int i = 0; i + 1 < args.size(); i += 2) {
                        if (CemAnimationProgram.truthy(args.get(i).evaluate(context))) {
                            return args.get(i + 1).evaluate(context);
                        }
                    }
                    return args.size() % 2 == 1 ? args.get(args.size() - 1).evaluate(context) : 0.0D;
                };
            }
            CemExpression function = CemAnimationProgram.compileMethod(method, args);
            // random() reads frame_counter; random(seed) is deterministic. NBT and
            // variables take separate paths above/below and never qualify as constants.
            if ("random".equals(method) && args.isEmpty()) {
                layout.reference("frame_counter");
                return function;
            }
            return CemExpression.fold(function, args.toArray(CemExpression[]::new));
        }
        int slot = layout.reference(identifier);
        return context -> context.value(slot);
    }

    private CemExpression parseNumber() {
        int start = index;
        if (peek("+") || peek("-")) index++;
        while (!done() && (Character.isDigit(input.charAt(index)) || input.charAt(index) == '.')) index++;
        if (!done() && (input.charAt(index) == 'e' || input.charAt(index) == 'E')) {
            index++;
            if (!done() && (input.charAt(index) == '+' || input.charAt(index) == '-')) index++;
            while (!done() && Character.isDigit(input.charAt(index))) index++;
        }
        double value = Double.parseDouble(input.substring(start, index));
        return new CemExpression.Constant(value);
    }

    private String parseIdentifier() {
        skipWhitespace();
        int start = index;
        while (!done()) {
            char c = input.charAt(index);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == ':') {
                index++;
            } else {
                break;
            }
        }
        if (start == index) throw new IllegalArgumentException("Expected identifier at " + index);
        return input.substring(start, index);
    }

    private boolean match(String token) {
        skipWhitespace();
        if (!input.startsWith(token, index)) return false;
        index += token.length();
        return true;
    }

    private void expect(String token) {
        if (!match(token)) throw new IllegalArgumentException("Expected " + token + " at " + index);
    }

    private String readRawCallBody() {
        int start = index;
        int depth = 1;
        while (!done() && depth > 0) {
            char c = input.charAt(index++);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }
        }
        if (depth != 0) throw new IllegalArgumentException("Expected ) at " + index);
        return input.substring(start, index - 1);
    }

    private boolean peek(String token) {
        skipWhitespace();
        return input.startsWith(token, index);
    }

    private boolean peekNumber() {
        skipWhitespace();
        if (done()) return false;
        char c = input.charAt(index);
        if (Character.isDigit(c) || c == '.') return true;
        return (c == '+' || c == '-') && index + 1 < input.length()
                && (Character.isDigit(input.charAt(index + 1)) || input.charAt(index + 1) == '.');
    }

    private void skipWhitespace() {
        while (!done() && Character.isWhitespace(input.charAt(index))) index++;
    }

    private boolean done() {
        return index >= input.length();
    }
}

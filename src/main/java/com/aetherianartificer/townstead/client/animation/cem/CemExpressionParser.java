package com.aetherianartificer.townstead.client.animation.cem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class CemExpressionParser {
    private final String input;
    private int index;

    private CemExpressionParser(String input) {
        this.input = input;
    }

    static CemExpression parse(String input) {
        CemExpressionParser parser = new CemExpressionParser(input);
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
            left = context -> CemAnimationProgram.truthy(previous.evaluate(context)) || CemAnimationProgram.truthy(right.evaluate(context)) ? 1.0D : 0.0D;
        }
        return left;
    }

    private CemExpression parseAnd() {
        CemExpression left = parseEquality();
        while (match("&&")) {
            CemExpression right = parseEquality();
            CemExpression previous = left;
            left = context -> CemAnimationProgram.truthy(previous.evaluate(context)) && CemAnimationProgram.truthy(right.evaluate(context)) ? 1.0D : 0.0D;
        }
        return left;
    }

    private CemExpression parseEquality() {
        CemExpression left = parseComparison();
        while (true) {
            if (match("==")) {
                CemExpression right = parseComparison();
                CemExpression previous = left;
                left = context -> Math.abs(previous.evaluate(context) - right.evaluate(context)) < 0.00001D ? 1.0D : 0.0D;
            } else if (match("!=")) {
                CemExpression right = parseComparison();
                CemExpression previous = left;
                left = context -> Math.abs(previous.evaluate(context) - right.evaluate(context)) >= 0.00001D ? 1.0D : 0.0D;
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
                left = context -> previous.evaluate(context) >= right.evaluate(context) ? 1.0D : 0.0D;
            } else if (match("<=")) {
                CemExpression right = parseAdditive();
                CemExpression previous = left;
                left = context -> previous.evaluate(context) <= right.evaluate(context) ? 1.0D : 0.0D;
            } else if (match(">")) {
                CemExpression right = parseAdditive();
                CemExpression previous = left;
                left = context -> previous.evaluate(context) > right.evaluate(context) ? 1.0D : 0.0D;
            } else if (match("<")) {
                CemExpression right = parseAdditive();
                CemExpression previous = left;
                left = context -> previous.evaluate(context) < right.evaluate(context) ? 1.0D : 0.0D;
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
                left = context -> previous.evaluate(context) + right.evaluate(context);
            } else if (match("-")) {
                CemExpression right = parseMultiplicative();
                CemExpression previous = left;
                left = context -> previous.evaluate(context) - right.evaluate(context);
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
                left = context -> previous.evaluate(context) * right.evaluate(context);
            } else if (match("/")) {
                CemExpression right = parseUnary();
                CemExpression previous = left;
                left = context -> previous.evaluate(context) / right.evaluate(context);
            } else if (match("%")) {
                CemExpression right = parseUnary();
                CemExpression previous = left;
                left = context -> previous.evaluate(context) % right.evaluate(context);
            } else {
                return left;
            }
        }
    }

    private CemExpression parseUnary() {
        if (match("!")) {
            CemExpression value = parseUnary();
            return context -> CemAnimationProgram.truthy(value.evaluate(context)) ? 0.0D : 1.0D;
        }
        if (match("-")) {
            CemExpression value = parseUnary();
            return context -> -value.evaluate(context);
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
        skipWhitespace();
        if (match("(")) {
            String method = identifier.toLowerCase(Locale.ROOT);
            if ("nbt".equals(method)) {
                skipRawCallBody();
                return context -> 0.0D;
            }
            List<CemExpression> args = new ArrayList<>();
            if (!peek(")")) {
                do {
                    args.add(parseOr());
                } while (match(","));
            }
            expect(")");
            return context -> {
                List<Double> values = new ArrayList<>(args.size());
                for (CemExpression arg : args) values.add(arg.evaluate(context));
                return CemAnimationProgram.method(method, values, context);
            };
        }
        return context -> context.value(identifier);
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
        return context -> value;
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

    private void skipRawCallBody() {
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

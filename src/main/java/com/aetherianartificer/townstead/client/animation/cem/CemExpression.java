package com.aetherianartificer.townstead.client.animation.cem;

@FunctionalInterface
interface CemExpression {
    double evaluate(CemAnimationProgram.CemEvaluationContext<?> context);

    record Constant(double value) implements CemExpression {
        @Override
        public double evaluate(CemAnimationProgram.CemEvaluationContext<?> context) {
            return value;
        }
    }

    /** Fold only proven constants, without reassociating floating-point arithmetic. */
    static CemExpression fold(CemExpression expression, CemExpression... inputs) {
        for (CemExpression input : inputs) {
            if (!(input instanceof Constant)) return expression;
        }
        try {
            return new Constant(expression.evaluate(null));
        } catch (RuntimeException ignored) {
            // Keep malformed function calls on their existing evaluation path.
            return expression;
        }
    }
}

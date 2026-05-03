package com.aetherianartificer.townstead.client.animation.cem;

@FunctionalInterface
interface CemExpression {
    double evaluate(CemAnimationProgram.CemEvaluationContext<?> context);
}

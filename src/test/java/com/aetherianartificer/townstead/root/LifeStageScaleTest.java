package com.aetherianartificer.townstead.root;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LifeStageScaleTest {
    @Test void sharedInterpolationGrowsBetweenMidpointsAndKeepsEndpoints() {
        float[] scales={.5f,1f,.9f}; int[] days={10,10,10};
        assertEquals(.5f,LifeStageScale.interpolate(scales,days,0));
        assertEquals(.75f,LifeStageScale.interpolate(scales,days,10));
        assertEquals(1f,LifeStageScale.interpolate(scales,days,15));
        assertEquals(.95f,LifeStageScale.interpolate(scales,days,20),.00001);
        assertEquals(.9f,LifeStageScale.interpolate(scales,days,100));
    }
    @Test void missingOrNonFiniteStageScalesCannotPoisonRenderingOrCollision() {
        assertEquals(1f,LifeStageScale.interpolate(null,new int[]{1},5));
        assertEquals(1f,LifeStageScale.interpolate(new float[]{.5f},new int[]{1,2},5));
        assertEquals(1f,LifeStageScale.interpolate(new float[]{Float.NaN},new int[]{10},0));
        assertEquals(1f,LifeStageScale.interpolate(new float[]{Float.POSITIVE_INFINITY},new int[]{10},100));
        assertEquals(.75f,LifeStageScale.interpolate(new float[]{Float.NaN,.5f},new int[]{10,10},10));
    }
}

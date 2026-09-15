package com.aetherianartificer.townstead.temperature;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Connected, furnished two-storey reference building; not a live-world replay. */
class ThermalControlAcceptanceTest {
    record Run(double upper,double lower,double min,double max,int starts,int onTicks,int comfortableAt) {}
    private Run run(boolean controlled,boolean cooling,double outside,double conductance,double initial,double heaterPower) {
        double[] t={initial,initial,initial,initial,initial,initial};
        boolean upper=false,lower=false; int starts=0,onTicks=0,comfortableAt=-1;
        double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        for(int second=0;second<1800;second++) {
            boolean next=!controlled || ThermalDemand.next(!cooling,upper,t[0],20,1);
            if(next&&!upper)starts++;
            upper=next;
            lower=!controlled || ThermalDemand.next(!cooling,lower,t[1],20,1);
            if(upper)onTicks++;
            var nodes=List.of(
                    new ThermalNetwork.Node(90*2000,t[0],upper?heaterPower:0,15,outside),
                    new ThermalNetwork.Node(90*2000,t[1],lower?heaterPower:0,15,outside),
                    new ThermalNetwork.Node(100*4000,t[2],0,2*conductance,outside),
                    new ThermalNetwork.Node(100*4000,t[3],0,2*conductance,outside),
                    new ThermalNetwork.Node(30*4000,t[4],0,0,0),
                    new ThermalNetwork.Node(8*4000,t[5],0,0,0));
            var links=List.of(new ThermalNetwork.Link(0,2,2*conductance),new ThermalNetwork.Link(1,3,2*conductance),
                    new ThermalNetwork.Link(0,4,50.4),new ThermalNetwork.Link(1,4,50.4),new ThermalNetwork.Link(0,5,20));
            var result=ThermalNetwork.advance(nodes,links,20);
            assertEquals(0,result.errorJoules(),1e-4);
            t=result.temperatures();
            if(comfortableAt<0 && t[0]>=19 && t[0]<=21)comfortableAt=second;
            if(second>1200){min=Math.min(min,t[0]);max=Math.max(max,t[0]);}
        }
        return new Run(t[0],t[1],min,max,starts,onTicks,comfortableAt);
    }
    @Test void tavernRecoversFromWarmWallsAndRegulatesWithoutWeakeningBoilers() {
        var uncontrolled=run(false,false,-5,84,30.3,3750);
        var controlled=run(true,false,-5,84,30.3,3750);
        assertTrue(uncontrolled.upper()>30);
        assertTrue(controlled.min()>18.5 && controlled.max()<21.5,controlled.toString());
        assertTrue(controlled.onTicks()<1600);
        assertTrue(controlled.starts()>1 && controlled.starts()<180,"Must cycle without per-second chatter: "+controlled);
    }
    @Test void coldStartAndCoolingUseTheSamePhysicalBalance() {
        var heating=run(true,false,-5,84,-5,3750);
        var cooling=run(true,true,40,84,40,-3750);
        assertTrue(heating.min()>18.5 && heating.max()<21.5,heating.toString());
        assertTrue(cooling.min()>18.5 && cooling.max()<21.5,cooling.toString());
        assertTrue(heating.comfortableAt()>=0 && heating.comfortableAt()<300,heating.toString());
        assertTrue(cooling.comfortableAt()>=0 && cooling.comfortableAt()<300,cooling.toString());
    }
    @Test void insulationSavesFuelAndAnUndersizedHeaterStillFallsShort() {
        var wood=run(true,false,-5,84,20,3750);
        var insulated=run(true,false,-5,20,20,3750);
        assertTrue(insulated.onTicks()<wood.onTicks()*.6);
        var leaky=run(true,false,-5,240,20,3750);
        assertTrue(leaky.upper()<15,"No artificial comfort floor: "+leaky);
    }
    @Test void openDoorIncreasesDemandInsteadOfResettingTheRoomToOutside() {
        var shut=run(true,false,-5,84,20,3750);
        var open=run(true,false,-5,144,20,3750);
        assertTrue(open.onTicks()>shut.onTicks());
        assertTrue(open.upper()<shut.upper());
        assertTrue(open.upper()>10,"Opening a door must not erase stored air and wall heat");
    }
    @Test void hysteresisAndMissingSensorHaveDefinedBehavior() {
        assertFalse(ThermalDemand.next(true,false,20,20,1));
        assertTrue(ThermalDemand.next(true,true,20,20,1));
        assertFalse(ThermalDemand.next(true,true,21,20,1));
        assertFalse(ThermalDemand.next(false,false,20,20,1));
        assertTrue(ThermalDemand.next(false,true,20,20,1));
        assertFalse(ThermalDemand.next(false,true,19,20,1));
        assertFalse(ThermalDemand.next(true,true,Double.NaN,20,1));
        for(double t=19;t<=21;t+=.01) {
            assertFalse(ThermalDemand.next(true,false,t,20,1));
            assertFalse(ThermalDemand.next(false,false,t,20,1));
        }
    }
}

package com.aetherianartificer.townstead.temperature;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ThermalNetworkTest {
    @Test void sealedRoomsAndOneSharedWallConserveEnergy() {
        var nodes = List.of(new ThermalNetwork.Node(80000,35,0,0,0),new ThermalNetwork.Node(160000,0,0,0,0),
                new ThermalNetwork.Node(40000,10,0,0,0));
        var links = List.of(new ThermalNetwork.Link(0,2,100),new ThermalNetwork.Link(1,2,100));
        var result = ThermalNetwork.advance(nodes,links,600);
        assertEquals(0,result.storedJoules(),1e-5);
        assertTrue(result.temperatures()[0]<35);
        assertTrue(result.temperatures()[1]>0);
        assertTrue(Math.abs(result.errorJoules())<1e-5);
    }
    @Test void permutationDoesNotChangeTheBuildingSolution() {
        var nodes = List.of(new ThermalNetwork.Node(10000,40,3750,3,-10),new ThermalNetwork.Node(40000,0,0,7,0),
                new ThermalNetwork.Node(500,15,0,0,0));
        var a = ThermalNetwork.advance(nodes,List.of(new ThermalNetwork.Link(0,2,50),new ThermalNetwork.Link(1,2,50)),100);
        var b = ThermalNetwork.advance(List.of(nodes.get(2),nodes.get(0),nodes.get(1)),
                List.of(new ThermalNetwork.Link(2,0,50),new ThermalNetwork.Link(1,0,50)),100);
        assertEquals(a.temperatures()[0],b.temperatures()[1],1e-8);
        assertEquals(a.temperatures()[1],b.temperatures()[2],1e-8);
        assertEquals(a.temperatures()[2],b.temperatures()[0],1e-8);
    }
    @Test void externalLossAndSourcesCloseTheEnergyLedger() {
        var result = ThermalNetwork.advance(List.of(new ThermalNetwork.Node(12000,30,3750,20,-5),
                new ThermalNetwork.Node(8000,10,-500,10,0)),List.of(new ThermalNetwork.Link(0,1,30)),120);
        assertEquals(3250*120,result.suppliedJoules(),1e-5);
        assertEquals(result.suppliedJoules()-result.escapedJoules(),result.storedJoules(),1e-5);
    }
    @Test void addedMassChangesTransientButNotSteadyDemand() {
        var light = ThermalNetwork.advance(List.of(new ThermalNetwork.Node(2000,0,1000,100,0)),List.of(),20);
        var heavy = ThermalNetwork.advance(List.of(new ThermalNetwork.Node(20000,0,1000,100,0)),List.of(),20);
        assertTrue(light.temperatures()[0]>heavy.temperatures()[0]);
        assertEquals(10,ThermalNetwork.advance(List.of(new ThermalNetwork.Node(20000,10,1000,100,0)),List.of(),100).temperatures()[0],1e-9);
    }
    @Test void noResolvedExteriorMeansNoInventedHeatSink() {
        var result=ThermalNetwork.advance(List.of(new ThermalNetwork.Node(2000,20,0,0,-40)),List.of(),1000);
        assertEquals(20,result.temperatures()[0]);
        assertEquals(0,result.escapedJoules());
    }
    @Test void largeBoundedNetworkConvergesWithoutTemperatureOvershoot() {
        int n=32768;
        var nodes=new ArrayList<ThermalNetwork.Node>(); var links=new ArrayList<ThermalNetwork.Link>();
        for(int i=0;i<n;i++) {
            nodes.add(new ThermalNetwork.Node(500,i%2==0?0:40,0,0,0));
            if(i>0)links.add(new ThermalNetwork.Link(i-1,i,50));
        }
        var result=ThermalNetwork.advance(nodes,links,20);
        assertEquals(0,result.errorJoules(),.001);
        for(double t:result.temperatures())assertTrue(Double.isFinite(t)&&t>=0&&t<=40);
    }
    @Test void invalidInputsCannotPoisonOtherRooms() {
        assertThrows(IllegalArgumentException.class,()->new ThermalNetwork.Node(0,0,0,0,0));
        assertThrows(IllegalArgumentException.class,()->new ThermalNetwork.Node(1,Double.NaN,0,0,0));
        assertThrows(IllegalArgumentException.class,()->ThermalNetwork.advance(List.of(),List.of(new ThermalNetwork.Link(0,1,1)),1));
    }
}

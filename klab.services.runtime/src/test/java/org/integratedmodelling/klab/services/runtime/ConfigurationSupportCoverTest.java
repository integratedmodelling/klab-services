package org.integratedmodelling.klab.services.runtime;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.junit.jupiter.api.*;
class ConfigurationSupportCoverTest {
 @BeforeAll static void setup(){org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();}
 static Geometry time(long first,long last){return Geometry.create("T1(1){ttype=PHYSICAL,tstart="+first+",tend="+last+"}");}
 static Geometry space(int first,int last){return Geometry.create("S2(1,1){proj=EPSG:3857,bbox=["+first+" "+last+" 0 10]}");}
 @Test void disjointAndPartiallyOverlappingEqualDurationAreNotCompletion(){
  var requested=time(1000,11000);
  assertFalse(RuntimeService.configurationSupportCovered(requested,List.of(time(11000,21000))));
  assertFalse(RuntimeService.configurationSupportCovered(requested,List.of(time(6000,16000))));
 }
 @Test void spatialPositionIsRequiredEvenForEqualArea(){
  var requested=space(0,10);
  assertFalse(RuntimeService.configurationSupportCovered(requested,List.of(space(10,20))));
  assertFalse(RuntimeService.configurationSupportCovered(requested,List.of(space(5,15))));
 }
 @Test void exactContainingAndCompleteAdjacentSlabsCover(){
  var requested=time(1000,11000);
  assertTrue(RuntimeService.configurationSupportCovered(requested,List.of(requested)));
  assertTrue(RuntimeService.configurationSupportCovered(requested,List.of(time(0,12000))));
  assertTrue(RuntimeService.configurationSupportCovered(requested,List.of(time(1000,6000),time(6000,11000))));
  assertFalse(RuntimeService.configurationSupportCovered(requested,List.of(time(1000,5000),time(6000,11000))));
 }
}

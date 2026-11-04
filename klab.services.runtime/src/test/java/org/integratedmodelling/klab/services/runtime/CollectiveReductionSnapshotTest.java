package org.integratedmodelling.klab.services.runtime;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.utilities.Utils;

/** Portable descriptor only; this test creates no query, execution or membership authority. */
class CollectiveReductionSnapshotTest {
 @BeforeAll static void setup(){org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();}
 ActuatorImpl plan() {
  var concept=new ConceptImpl();concept.setUrn("count of each test:Tree");concept.setType(EnumSet.of(SemanticType.QUALITY,SemanticType.NUMEROSITY));
  var output=new ObservationImpl();output.setId(42);output.setObservable(ObservableImpl.promote(concept,null));output.setGeometry(Geometry.create("t0(1){ttype=PHYSICAL,tstart=1000,tend=2000}"));
  var plan=new ActuatorImpl();plan.setObservation(output);plan.setActuatorType(Actuator.Type.OBSERVE);plan.setExecutionRole(Actuator.ExecutionRole.INITIALIZATION);return plan;
 }
 @Test void requestedSupportSurvivesPortableSnapshotAndJsonRoundTrip() {
  var plan=plan();plan.setRequestedSupport(plan.getObservation().getGeometry());
  var snapshot=CompiledDataflow.portableOccurrencePlan(plan);
  assertNotNull(snapshot.getRequestedSupport());assertEquals(plan.getRequestedSupport().encode(),snapshot.getRequestedSupport().encode());
  var wire=Utils.Json.parseObject(Utils.Json.asString(snapshot),Actuator.class);
  assertNotNull(wire.getRequestedSupport());assertEquals(plan.getRequestedSupport().encode(),wire.getRequestedSupport().encode());
 }
 @Test void coverageCannotReplaceTheOriginalRequestedSupport() {
  var plan=plan();plan.setRequestedSupport(plan.getObservation().getGeometry());plan.setCoverage(Geometry.create("t0(1){ttype=PHYSICAL,tstart=1100,tend=1900}"));
  var snapshot=CompiledDataflow.portableOccurrencePlan(plan);
  assertEquals(plan.getRequestedSupport().encode(),snapshot.getRequestedSupport().encode());assertEquals(plan.getCoverage().encode(),snapshot.getCoverage().encode());assertNotEquals(snapshot.getCoverage().encode(),snapshot.getRequestedSupport().encode());
 }
 @Test void missingRequestedSupportRemainsMissingRatherThanBeingInvented() {
  var plan=plan();plan.setCoverage(plan.getObservation().getGeometry());
  assertNull(CompiledDataflow.portableOccurrencePlan(plan).getRequestedSupport());
 }
}

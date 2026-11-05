package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.common.runtime.DataflowImpl;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.InstantiationResolution.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.*;

class InstantiationPolicyTest {
  static final Geometry FULL = time(0, 10000);
  static Geometry time(long start, long end) {
    return Geometry.create("T0(1){ttype=PHYSICAL,tstart=" + start + ",tend=" + end + "}");
  }
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }
  static Annotation annotation(Object value) {
    var annotation = Annotation.of("resolution"); annotation.putUnnamed(value); return annotation;
  }
  static ObservationImpl observation(long id, String name, boolean collective, Geometry support) {
    var semantics = new ConceptImpl(); semantics.setUrn("test:Road");
    semantics.setType(EnumSet.of(SemanticType.SUBJECT, SemanticType.COUNTABLE, SemanticType.OBSERVABLE));
    semantics.setCollective(collective); semantics.setName("Road"); semantics.setReferenceName("road");
    var observable = new ObservableImpl(); observable.setSemantics(semantics); observable.setUrn(collective ? "each test:Road" : "test:Road");
    observable.setDescriptionType(collective ? Contextualization.INSTANTIATION : Contextualization.ACKNOWLEDGEMENT);
    observable.setArtifactType(org.integratedmodelling.klab.api.knowledge.Artifact.Type.OBJECT);
    var result = new ObservationImpl(); result.setId(id); result.setUrn(name); result.setObservable(observable); result.setGeometry(support);
    return result;
  }
  static Declaration declaration(String model, String mode) {
    var annotation = annotation(mode);
    return new Declaration(model, InstantiationResolution.mode(annotation), annotation);
  }
  static ContextualizationScopeImpl batch(ObservationImpl collective, List<ObservationImpl> children, String mode) {
    var result = new ContextualizationScopeImpl(collective, null, FULL);
    result.getOutcomes().addAll(children);
    if (mode != null) result.bindResolution(0, 0, declaration("test:instantiator", mode));
    return result;
  }
  static ServiceContextScope scope(Resolver resolver) {
    var scope = mock(ServiceContextScope.class);
    when(scope.getService(Resolver.class)).thenReturn(resolver);
    when(scope.withResolutionConstraints(any(org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint[].class))).thenReturn(scope);
    var ids = new AtomicLong(-100);
    when(scope.getNextObservationId()).thenAnswer(call -> ids.decrementAndGet());
    return scope;
  }

  @Test void defaultAndAllSpellingsAreValidatedWithoutSilentlyFallingBack() {
    assertEquals(Mode.INDIVIDUAL, InstantiationResolution.mode(null));
    for (var mode : List.of("none", "collective", "individual"))
      assertEquals(Mode.valueOf(mode.toUpperCase()), InstantiationResolution.mode(Annotation.of("resolution", "value", mode)));
    for (var value : List.of("", "invalid", "NONE", 1, true))
      assertThrows(IllegalArgumentException.class, () -> InstantiationResolution.mode(annotation(value)));
    assertThrows(IllegalArgumentException.class, () -> InstantiationResolution.mode(Annotation.of("resolution")));
    assertThrows(IllegalArgumentException.class, () -> InstantiationResolution.mode(Annotation.of("resolution", "none", "collective")));
    assertThrows(IllegalArgumentException.class, () -> InstantiationResolution.mode(Annotation.of("resolution", "other", "none")));
  }

  @Test void originatingDefinitionControlsMixedContributionsAndSurvivesTransport() {
    var target = observation(-2, "roads", true, FULL);
    var builder = new org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationBuilderImpl(target.getObservable(), null) {
      public Observation register() { return build(); }
      public CompletableFuture<Observation> submit() { return CompletableFuture.completedFuture(build()); }
    };
    target = (ObservationImpl) builder.geometry(FULL).withAnnotation(annotation("none")).build();
    target.setId(-2);
    assertEquals(ObservationImpl.DEFINITION_ANNOTATIONS,target.getAnnotationPriorities().get("resolution"));
    target = (ObservationImpl) Utils.Json.parseObject(Utils.Json.asString(Observation.forTransport(target)), Observation.class);
    var a = observation(-3, "a", false, time(0, 2000));
    var b = observation(-4, "b", false, time(4000, 6000));
    var outputs = batch(target, List.of(a), "collective");
    outputs.getOutcomes().add(b); outputs.bindResolution(1, 1, declaration("other", "individual"));
    var plans = InstantiationPlanning.prepare(outputs, scope(mock(Resolver.class)));
    assertEquals(Mode.NONE, plans.get(a).mode()); assertEquals(Mode.NONE, plans.get(b).mode());
    assertEquals("none", a.getMetadata().get(InstantiationPlanning.MODE));
    assertEquals(Mode.NONE, InstantiationResolution.mode(InstantiationResolution.annotation(b.getAnnotations())));
  }

  @Test void mergedModelAnnotationDoesNotLeakBetweenContributionsAndChildDefinitionCanOverrideModel() {
    var target = observation(-2, "roads", true, FULL);
    target.mergeAnnotations(List.of(annotation("none")), ObservationImpl.MODEL_ANNOTATIONS);
    var a = observation(-3, "a", false, FULL); var b = observation(-4, "b", false, FULL);
    a.mergeAnnotations(List.of(annotation("individual")), ObservationImpl.DEFINITION_ANNOTATIONS);
    var outputs = batch(target, List.of(a), "none");
    outputs.getOutcomes().add(b); outputs.bindResolution(1, 1, declaration("other", "collective"));
    var plans = InstantiationPlanning.prepare(outputs, scope(mock(Resolver.class)));
    assertNull(plans.get(a)); assertEquals(Mode.COLLECTIVE, plans.get(b).mode());
  }

  @Test void originatingKimDefineSuppliesTheSameRequestPriorityAsBuilderAnnotations() {
    var collective=observation(-2,"roads",true,FULL).getObservable();
    var scope=scope(mock(Resolver.class));
    var reasoner=mock(org.integratedmodelling.klab.api.services.Reasoner.class);
    when(scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class)).thenReturn(reasoner);
    when(reasoner.resolveObservable(collective.getUrn())).thenReturn(collective);
    var define=new org.integratedmodelling.klab.api.lang.kim.impl.KimSymbolDefinitionImpl();
    define.setDefineClass("observation");define.setNamespace("test");define.setName("roads");define.setUrn("test.roads");
    define.setValue(Map.of("semantics",collective.getUrn()));define.getAnnotations().add(annotation("none"));
    var builder=new org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationBuilderImpl(define,scope) {
      public Observation register() { return build(); }
      public CompletableFuture<Observation> submit() { return CompletableFuture.completedFuture(build()); }
    };
    var target=(ObservationImpl)builder.geometry(FULL).build();target.setId(-2);
    assertEquals(ObservationImpl.DEFINITION_ANNOTATIONS,target.getAnnotationPriorities().get("resolution"));
    var child=observation(-3,"a",false,FULL);
    var plans=InstantiationPlanning.prepare(batch(target,List.of(child),"individual"),scope);
    assertEquals(Mode.NONE,plans.get(child).mode());
  }

  @Test void largeNoneBatchPerformsNoResolverWorkAndRetainsDistinctAcknowledgments() {
    var children = new ArrayList<ObservationImpl>();
    for (int i = 0; i < 1000; i++) children.add(observation(-3-i, "road:"+i, false, FULL));
    var resolver = mock(Resolver.class); var scope = scope(resolver);
    var outputs = batch(observation(-2, "roads", true, FULL), children, "none");
    var plans = InstantiationPlanning.prepare(outputs, scope);
    for (var child : children) {
      var plan = plans.get(child).plan(child, scope).join();
      assertEquals(child.getId(), plan.getComputation().getFirst().getId());
      assertTrue(plan.getComputation().getFirst().getComputation().isEmpty());
      assertEquals(child.getGeometry().encode(), plan.getComputation().getFirst().getObservation().getGeometry().encode());
    }
    verifyNoInteractions(resolver);
  }

  @Test void sharedSelectionUsesFullSupportOnceThenBindsIndependentIdentitiesAndDependencies() {
    var resolver = mock(Resolver.class); var scope = scope(resolver);
    var pending = new CompletableFuture<Dataflow>();
    when(resolver.resolve(any(), any())).thenAnswer(call -> {
      var prototype = call.getArgument(0, Observation.class);
      assertEquals(FULL.encode(), prototype.getGeometry().encode());
      var flow = (DataflowImpl) InstantiationPlanning.acknowledgment(prototype);
      flow.getComputation().getFirst().getData().put("im:observer-contributions",List.of(
          Utils.Json.asString(Map.of("modelUrn","test:producer","observerUrn","test:Observer","revision",3,"support",FULL.encode())),
          Utils.Json.asString(Map.of("modelUrn","test:otherFragment","observerUrn","test:Observer","revision",3,"support",time(8000,9000).encode()))));
      flow.getComputation().getFirst().getData().put("im:model-admissions",List.of("frozen-admission"));
      var quality = (ObservationImpl) Observation.forTransport(prototype);
      quality.setId(-90); quality.setParentId(prototype.getId());
      var q = new ActuatorImpl(); q.setId(-90); q.setObservation(quality); q.setActuatorType(Actuator.Type.OBSERVE);
      q.setCoverage(FULL); q.setName("quality"); q.setExecutionRole(Actuator.ExecutionRole.INITIALIZATION);
      flow.getComputation().getFirst().getChildren().add(q);
      pending.complete(flow); return pending;
    });
    var a = observation(-3, "a", false, time(0, 2000)); var b = observation(-4, "b", false, time(4000, 6000));
    var outputs = batch(observation(-2, "roads", true, FULL), List.of(a,b), "collective");
    var plans = InstantiationPlanning.prepare(outputs, scope);
    assertSame(plans.get(a), plans.get(b));
    var first = plans.get(a).plan(a, scope).join(); var second = plans.get(b).plan(b, scope).join();
    verify(resolver, times(1)).resolve(any(), any());
    assertEquals(a.getId(), first.getComputation().getFirst().getId());
    assertEquals(b.getId(), second.getComputation().getFirst().getId());
    var qa = first.getComputation().getFirst().getChildren().getFirst();
    var qb = second.getComputation().getFirst().getChildren().getFirst();
    assertNotEquals(qa.getId(), qb.getId());
    assertNotEquals(qa.getObservation().getTransientId(), qb.getObservation().getTransientId());
    assertEquals(a.getId(), qa.getObservation().getParentId()); assertEquals(b.getId(), qb.getObservation().getParentId());
    assertEquals(a.getGeometry().encode(), qa.getObservation().getGeometry().encode());
    assertEquals(b.getGeometry().encode(), qb.getObservation().getGeometry().encode());
    var contributions=(List<?>)second.getComputation().getFirst().getData().get("im:observer-contributions");
    assertEquals(1,contributions.size());
    var contribution=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    try { assertEquals(b.getGeometry().encode(),contribution.readTree(contributions.getFirst().toString()).get("support").asText()); }
    catch(java.io.IOException invalid) { fail(invalid); }
    assertEquals(List.of("frozen-admission"),second.getComputation().getFirst().getData().get("im:model-admissions"));
    qa.getObservation().getMetadata().put("changed", true);
    assertFalse(qb.getObservation().getMetadata().containsKey("changed"));
  }

  @Test void invalidLaterDeclarationPreventsAnyChildWorkAndIncompleteDiscoveryIsNotSharedAbsence() {
    var a = observation(-3, "a", false, FULL); var b = observation(-4, "b", false, FULL);
    b.mergeAnnotations(List.of(annotation("bogus")), ObservationImpl.DEFINITION_ANNOTATIONS);
    var resolver = mock(Resolver.class); var scope = scope(resolver);
    assertThrows(IllegalArgumentException.class, () -> InstantiationPlanning.prepare(
        batch(observation(-2, "roads", true, FULL), List.of(a,b), "none"), scope));
    assertFalse(a.getMetadata().containsKey(InstantiationPlanning.MODE)); verifyNoInteractions(resolver);
    for (var outcome : List.of(Dataflow.ResolutionOutcome.DEFERRED, Dataflow.ResolutionOutcome.FAILED)) {
      var missing = Dataflow.empty(); ((org.integratedmodelling.klab.api.services.runtime.impl.EmptyDataflow) missing).setResolutionOutcome(outcome);
      assertSame(missing, InstantiationPlanning.bind(missing, a, FULL, scope));
    }
    var absent = Dataflow.empty(); ((org.integratedmodelling.klab.api.services.runtime.impl.EmptyDataflow) absent).setResolutionOutcome(Dataflow.ResolutionOutcome.NO_MODEL);
    assertFalse(InstantiationPlanning.bind(absent, a, FULL, scope).isEmpty());
    assertThrows(IllegalArgumentException.class, () -> InstantiationPlanning.bind(absent,
        observation(-5, "outside", false, time(0, 20000)), FULL, scope));
  }

  @Test void individualDefaultStillUsesOneOrdinarySubmissionPerChild() {
    var target = observation(-2, "roads", true, FULL);
    var outputs = batch(target, List.of(observation(-3,"a",false,FULL), observation(-4,"b",false,FULL)), null);
    var runtime = mock(RuntimeService.class, CALLS_REAL_METHODS);
    var parent = mock(org.integratedmodelling.klab.api.scope.ContextScope.class);
    var members = mock(org.integratedmodelling.klab.api.scope.ContextScope.class, RETURNS_SELF);
    when(parent.within(target)).thenReturn(members);
    var service = mock(org.integratedmodelling.klab.api.services.RuntimeService.class);
    when(members.getService(org.integratedmodelling.klab.api.services.RuntimeService.class)).thenReturn(service);
    when(service.submit(any(), eq(members))).thenAnswer(call -> CompletableFuture.completedFuture(call.getArgument(0)));
    runtime.submitContextualizationResult(outputs, parent, Activity.Outcome.SUCCESS);
    verify(service, times(2)).submit(any(), eq(members));
  }

  @Test void cancellationAndPartialCoverageRejectSharedBindingWithoutFallback() {
    var child=observation(-3,"a",false,FULL);
    var scope=scope(mock(Resolver.class));
    var template=InstantiationPlanning.acknowledgment(observation(-20,"prototype",false,FULL));
    ((DataflowImpl)template).setResolvedCoverage(0.5);
    assertThrows(IllegalArgumentException.class,()->InstantiationPlanning.bind(template,child,FULL,scope));
    ((DataflowImpl)template).setResolvedCoverage(1);
    ((DataflowImpl)template).setCoverage(time(0,2000));
    assertThrows(IllegalArgumentException.class,()->InstantiationPlanning.bind(template,
        observation(-4,"withinIncompleteTemplate",false,time(0,1000)),FULL,scope));
    when(scope.isInterrupted()).thenReturn(true);
    assertThrows(CancellationException.class,()->InstantiationPlanning.bind(template,child,FULL,scope));
  }

  @Test void sharedFragmentsRetainOnlyApplicableRootsAndNativeReferencesMustRemainCurrent() {
    var child=observation(-3,"a",false,time(0,2000));
    var scope=scope(mock(Resolver.class));
    var template=InstantiationPlanning.acknowledgment(observation(-20,"prototype",false,FULL));
    var first=(ActuatorImpl)template.getComputation().getFirst();first.setCoverage(time(0,5000));
    var second=Utils.Json.parseObject(Utils.Json.asString(first),Actuator.class);
    ((ActuatorImpl)second).setCoverage(time(5000,10000));template.getComputation().add(second);
    var input=observation(12,"existing",false,FULL);
    var reference=new ActuatorImpl();reference.setId(12);reference.setName("input");
    reference.setObservation(input);reference.setCoverage(FULL);reference.setActuatorType(Actuator.Type.REFERENCE);
    first.getChildren().add(reference);
    var twin=mock(org.integratedmodelling.klab.api.digitaltwin.DigitalTwin.class);
    var graph=mock(org.integratedmodelling.klab.api.data.KnowledgeGraph.class);
    when(scope.getDigitalTwin()).thenReturn(twin);when(twin.getKnowledgeGraph()).thenReturn(graph);
    when(scope.getObservation(12)).thenReturn(input);when(graph.isObservationCurrent(input,scope)).thenReturn(true);
    var encoded=Utils.Json.asString(template);
    var bound=InstantiationPlanning.bind(Utils.Json.parseObject(encoded,Dataflow.class),child,FULL,scope);
    assertEquals(1,bound.getComputation().size());
    assertEquals(12,bound.getComputation().getFirst().getChildren().getFirst().getId());
    when(graph.isObservationCurrent(input,scope)).thenReturn(false);
    assertThrows(IllegalStateException.class,()->InstantiationPlanning.bind(Utils.Json.parseObject(encoded,Dataflow.class),child,FULL,scope));
    first.setActuatorType(Actuator.Type.UPDATE);
    assertThrows(IllegalArgumentException.class,()->InstantiationPlanning.bind(template,child,FULL,scope));
  }
}

package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.common.lang.*;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resolver.*;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.*;

class ScheduleNegotiationResolutionTest {
  @BeforeAll static void setup() { ServiceConfiguration.injectInstantiators(); }
  static final Geometry GEOMETRY = Geometry.create("T0(1){tstart=1704067200000,tend=1735689600000,ttype=PHYSICAL}");
  static OccurrenceNegotiation.Request request(String owner, String cadence) {
    return new OccurrenceNegotiation.Request(owner, "erosion", "test:erosion",
        OccurrenceSchedule.fromDependency(List.of(Annotation.of("time", "step", QuantityImpl.parse(cadence)))));
  }
  static ModelImpl model(String name, boolean locked) {
    var model = new ModelImpl(); model.setUrn(name); model.setCoverage(Geometry.UNIVERSAL);
    model.getObservables().add(ProcessModelBindingsTest.observable("erosion", SemanticType.PROCESS));
    model.getAnnotations().add(Annotation.of("time", "step", QuantityImpl.parse("1.month"),
        "minStep", QuantityImpl.parse("1.day"), "maxStep", QuantityImpl.parse("1.month"), "overridable", !locked));
    var call = mock(Contextualizable.class);
    when(call.getServiceCall()).thenReturn(new ServiceCallImpl("test.process"));
    model.getComputation().add(call);
    return model;
  }
  static ContextScope scope(long bearerId) {
    var scope = mock(ContextScope.class);
    when(scope.withResolutionConstraints(any(ResolutionConstraint[].class))).thenReturn(scope);
    var bearer = new ObservationImpl(); bearer.setId(bearerId); bearer.setGeometry(GEOMETRY);
    bearer.setObservable(ProcessModelBindingsTest.observable("region", SemanticType.SUBJECT));
    when(scope.getContextObservation()).thenReturn(bearer);
    when(scope.getService(Reasoner.class)).thenReturn(mock(Reasoner.class));
    var runtime = mock(RuntimeService.class);
    when(scope.getService(RuntimeService.class)).thenReturn(runtime);
    when(runtime.resolveContextualizables(anyList(), any())).thenReturn(new org.integratedmodelling.klab.api.services.resources.ResourceSet());
    return scope;
  }

  @Test void rejectsLockedCandidateThenCompilesAcceptedAlternativeWithRequestProvenance() {
    var scope = scope(10); var scale = GeometryRepository.INSTANCE.scale(GEOMETRY);
    var locked = model("test:locked", true); var flexible = model("test:flexible", false);
    var compiler = spy(new ResolutionCompiler(mock(ResolverService.class)));
    doReturn(List.of(locked, flexible)).when(compiler).queryModels(any(), any(), eq(scope), any());
    var strategy = mock(ObservationStrategy.class);
    var operation = mock(ObservationStrategy.Operation.class);
    when(operation.getObservable()).thenReturn(flexible.getObservables().getFirst());
    when(operation.getType()).thenReturn(ObservationStrategy.Operation.Type.OBSERVE);
    when(operation.getId()).thenReturn("process");
    when(strategy.getOperations()).thenReturn(List.of(operation));
    var request = request("regionModel", "1.day");
    var result = compiler.resolve(strategy, scale, ResolutionGraph.create(scope).withScheduleRequest(request), scope);
    assertFalse(result.isEmpty());
    assertFalse(result.graph().containsVertex(locked));
    assertTrue(result.graph().containsVertex(flexible));
    assertTrue(compiler.getNotifications().getFirst().getMessage().toString().contains("test:locked"));
    var observation = new ObservationImpl(); observation.setId(-10); observation.setGeometry(GEOMETRY);
    observation.setObservable(flexible.getObservables().getFirst());
    var actuator = new ActuatorImpl(); actuator.setObservation(observation);
    new DataflowCompiler(observation, result, scope).compileStrategy(actuator, observation, GEOMETRY, strategy);
    var accepted = Utils.Json.parseObject(actuator.getData().get(OccurrenceNegotiation.DATA_KEY).toString(), OccurrenceNegotiation.class);
    assertEquals(request, accepted.request());
    assertEquals(accepted.effective(), actuator.getOccurrenceSchedules().get(0));
    assertEquals("test:flexible", accepted.model());
    verify(scope, never()).observation(any());
  }

  @Test void requestsAreLexicalAndReuseCannotChangeRegisteredCadence() {
    var scope = scope(10); var root = ResolutionGraph.create(scope);
    var request = request("regionModel", "1.day");
    var branch = root.withScheduleRequest(request);
    var model = model("test:flexible", false);
    var scale = GeometryRepository.INSTANCE.scale(GEOMETRY);
    assertEquals(request, branch.createChild(model, scale).scheduleRequest);
    assertNull(root.scheduleRequest);
    assertNull(branch.withScheduleRequest(null).createChild(model, scale).scheduleRequest);
    var accepted = ScheduleNegotiationSupport.negotiate(model, branch, scope, scale.getTime());
    var existing = new ObservationImpl(); existing.setId(100); existing.setGeometry(GEOMETRY);
    existing.setObservable(model.getObservables().getFirst());
    existing.getMetadata().put(OccurrenceNegotiation.DATA_KEY, Utils.Json.asString(accepted));
    assertDoesNotThrow(() -> ScheduleNegotiationSupport.checkReuse(existing, request("anotherRegionModel", "24.hour"), scale.getTime()));
    assertThrows(RuntimeException.class, () -> ScheduleNegotiationSupport.checkReuse(existing, request("anotherRegionModel", "1.month"), scale.getTime()));
    existing.getMetadata().clear();
    assertThrows(RuntimeException.class, () -> ScheduleNegotiationSupport.checkReuse(existing, request, scale.getTime()));
  }

  @Test void dependencyAnnotationsAreExtractedOnTheirOwnEdgeAndReferencesRetainProvenance() throws Exception {
    var scope = scope(20); var scale = GeometryRepository.INSTANCE.scale(GEOMETRY);
    var processModel = model("test:flexible", false);
    var process = processModel.getObservables().getFirst();
    process.getAnnotations().add(Annotation.of("time", "step", QuantityImpl.parse("1.day")));
    var request = request("test:region", "1.day");
    var registered = ScheduleNegotiationSupport.negotiate(processModel,
        ResolutionGraph.create(scope).withScheduleRequest(request), scope, scale.getTime());
    var existing = new ObservationImpl(); existing.setId(100); existing.setGeometry(GEOMETRY); existing.setObservable(process);
    existing.getMetadata().put(OccurrenceNegotiation.DATA_KEY, Utils.Json.asString(registered));
    var parent = new ModelImpl(); parent.setUrn("test:region"); parent.setCoverage(Geometry.UNIVERSAL);
    parent.getObservables().add(scope.getContextObservation().getObservable());
    parent.getDependencies().add(process);
    var compiler = spy(new ResolutionCompiler(mock(ResolverService.class)));
    doReturn(new ResolutionCompiler.QueryMatch(existing, existing, scale, scale, Coverage.create(scale, 1)))
        .when(compiler).query(process, scale, scope);
    var resolveModel = ResolutionCompiler.class.getDeclaredMethod("resolve", Model.class,
        org.integratedmodelling.klab.api.knowledge.observation.scale.Scale.class, ResolutionGraph.class, ContextScope.class);
    resolveModel.setAccessible(true);
    var root = ResolutionGraph.create(scope).withScheduleRequest(request("unrelatedOuterModel", "2.month"));
    var resolved = (ResolutionGraph) resolveModel.invoke(compiler, parent, scale, root, scope);
    assertFalse(resolved.isEmpty());
    var edge = resolved.graph().outgoingEdgesOf(parent).iterator().next();
    assertEquals(request, edge.scheduleRequest);
    var actuator = new ActuatorImpl();
    new DataflowCompiler(null, resolved, scope).compileModel(actuator, null, GEOMETRY, null, parent, null);
    var reference = actuator.getChildren().getFirst();
    assertEquals(request, Utils.Json.parseObject(reference.getData().get(OccurrenceNegotiation.REQUEST_KEY).toString(),
        OccurrenceNegotiation.Request.class));
    process.getAnnotations().clear();
    var withoutRequest = (ResolutionGraph) resolveModel.invoke(compiler, parent, scale, root, scope);
    assertFalse(withoutRequest.isEmpty());
    assertNull(withoutRequest.graph().outgoingEdgesOf(parent).iterator().next().scheduleRequest);
    process.getAnnotations().add(Annotation.of("time", "step", QuantityImpl.parse("1.month")));
    var conflict = (ResolutionGraph) resolveModel.invoke(compiler, parent, scale, root, scope);
    assertTrue(conflict.isEmpty());
    assertTrue(compiler.getNotifications().getLast().getMessage().toString().contains("reschedule"));
    verify(scope, never()).observation(any());
  }
}

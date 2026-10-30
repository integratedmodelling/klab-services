package org.integratedmodelling.klab.runtime.libraries;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.runtime.kactors.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InspectorApplyTest {
  static class Fixture {
    final TestCaseBase.TestCaseScope caller = mock(TestCaseBase.TestCaseScope.class);
    final ContextScope base = mock(ContextScope.class), selected = mock(ContextScope.class), operation = mock(ContextScope.class);
    final RuntimeService runtime = mock(RuntimeService.class, withSettings().extraInterfaces(RuntimeService.Testing.class));
    final RuntimeService.Testing testing = (RuntimeService.Testing) runtime;
    final Geometry support = mock(Geometry.class);
    final Observable directive = mock(Observable.class);
    final Agent provenance = mock(Agent.class);
    final CompletableFuture<RuntimeService.OperationReceipt> pending = new CompletableFuture<>();
    Fixture() {
      when(base.withResolutionConstraints(any(ResolutionConstraint[].class))).thenReturn(selected);
      when(selected.withResolutionConstraints(any(ResolutionConstraint[].class))).thenReturn(operation);
      when(base.getService(RuntimeService.class)).thenReturn(runtime);
      when(selected.getService(RuntimeService.class)).thenReturn(runtime);
      var actor = mock(TestCaseBase.class); when(caller.getAgent()).thenReturn(actor); when(actor.getName()).thenReturn("test-agent");
      var twin = mock(DigitalTwin.class); var graph = mock(KnowledgeGraph.class);
      when(selected.getDigitalTwin()).thenReturn(twin); when(twin.getKnowledgeGraph()).thenReturn(graph);
      when(graph.requireAgent("test-agent")).thenReturn(provenance);
      when(testing.applyForTesting(directive, support, operation, caller)).thenReturn(pending);
    }
    CompletableFuture<RuntimeService.OperationReceipt> apply(Object semantics) {
      return CoreActorLibrary.Inspector.apply(caller, base, semantics, support,
          Metadata.create("namespace", "test.models", "project", "test", "observer", false));
    }
  }

  @Test void nonTestCallerFailsBeforeContextOptionsOrSemanticLookup() {
    var ordinary = mock(AgentScope.class); var context = mock(ContextScope.class); var literal = mock(KimObservable.class);
    assertThrows(IllegalArgumentException.class, () -> CoreActorLibrary.Inspector.apply(
        ordinary, context, literal, null, Metadata.create("unknown", true)));
    verifyNoInteractions(ordinary, context, literal);
  }

  @Test void invalidOptionsFailBeforeScopeDerivationOrServiceLookup() {
    var caller = mock(TestCaseBase.TestCaseScope.class); var context = mock(ContextScope.class);
    assertThrows(IllegalArgumentException.class, () -> CoreActorLibrary.Inspector.apply(caller, context, null, null,
        Metadata.create("namesapce", "typo")));
    assertThrows(IllegalArgumentException.class, () -> CoreActorLibrary.Inspector.apply(caller, context, null, null, "ignored"));
    verifyNoInteractions(context);
  }

  @Test void remoteRuntimeFailsBeforeSemanticLookup() {
    var caller = mock(TestCaseBase.TestCaseScope.class); var context = mock(ContextScope.class);
    var literal = mock(KimObservable.class); when(context.getService(RuntimeService.class)).thenReturn(mock(RuntimeService.class));
    assertThrows(IllegalArgumentException.class, () -> CoreActorLibrary.Inspector.apply(caller, context, literal, null));
    verifyNoInteractions(literal); verify(context, never()).getService(Reasoner.class);
  }

  @Test void nativeCapabilityReceivesOriginalCallerValidatedOptionsAndCallingProvenance() {
    var f = new Fixture(); assertSame(f.pending, f.apply(f.directive));
    var options = ArgumentCaptor.forClass(ResolutionConstraint[].class);
    verify(f.base).withResolutionConstraints(options.capture());
    assertEquals(java.util.List.of(ResolutionConstraint.Type.DoNotCreateDefaultObserver,
        ResolutionConstraint.Type.ResolutionNamespace, ResolutionConstraint.Type.ResolutionProject),
        java.util.Arrays.stream(options.getValue()).map(ResolutionConstraint::getType).toList());
    var provenance = ArgumentCaptor.forClass(ResolutionConstraint.class);
    verify(f.selected).withResolutionConstraints(provenance.capture());
    assertEquals(ResolutionConstraint.Type.Provenance, provenance.getValue().getType());
    assertEquals(java.util.List.of(f.provenance), provenance.getValue().payload(Agent.class));
    verify(f.testing).applyForTesting(f.directive, f.support, f.operation, f.caller);
    verify(f.runtime, never()).submit(any(Observation.class), any(ContextScope.class));
  }

  @Test void semanticLiteralUsesSelectedScopeReasoner() {
    var f = new Fixture(); var literal = mock(KimObservable.class); var reasoner = mock(Reasoner.class);
    when(literal.getUrn()).thenReturn("test:Directive"); when(f.selected.getService(Reasoner.class)).thenReturn(reasoner);
    when(reasoner.resolveObservable("test:Directive")).thenReturn(f.directive);
    assertSame(f.pending, f.apply(literal)); verify(f.base, never()).getService(Reasoner.class);
    verify(f.caller.getAgent(), never()).getCreationScope();
  }

  @Test void absentDirectiveOrEmptySupportNeverDispatchesNativeOperation() {
    var f = new Fixture(); assertThrows(IllegalArgumentException.class, () -> f.apply("test:Directive"));
    when(f.support.isEmpty()).thenReturn(true);
    assertThrows(IllegalArgumentException.class, () -> f.apply(f.directive));
    verify(f.testing, never()).applyForTesting(any(), any(), any(), any());
  }
  @Test void snakeCaseReceiptAccessorResolvesToRealRecordCommitId() throws Exception {
    var lookup = RuntimeAgentBase.class.getDeclaredMethod("findActorMethod", Class.class,
        String.class, boolean.class, AgentScope.class, Object[].class);
    lookup.setAccessible(true);
    var accessor = (java.lang.reflect.Method) lookup.invoke(mock(TestCaseBase.class),
        RuntimeService.OperationReceipt.class, "commit_id", false,
        mock(TestCaseBase.TestCaseScope.class), new Object[0]);
    assertEquals("commitId", accessor.getName());
    assertEquals(23L, accessor.invoke(new RuntimeService.OperationReceipt("context", 23L, 42L, "test:Directive")));
  }
}

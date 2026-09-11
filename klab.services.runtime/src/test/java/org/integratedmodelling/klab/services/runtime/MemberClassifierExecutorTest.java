package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.nio.file.*;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.tools.ToolProvider;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class MemberClassifierExecutorTest {
  public static class Classifier {
    Concept result;
    int failAt;
    final AtomicInteger calls = new AtomicInteger();
    Observable receivedObservable; Observation receivedMember; Scope receivedScope;
    public Concept classify(Observable observable, Observation member, ContextScope scope, ServiceCall call) {
      int invocation = calls.incrementAndGet(); receivedObservable = observable; receivedMember = member; receivedScope = scope;
      if (invocation == failAt) throw new IllegalStateException("classifier failure");
      return result;
    }
    public Object unsupported(Observable observable, Scope scope) { return result; }
  }
  static class Fixture {
    final ContextScope scope = mock(ContextScope.class), memberScope = mock(ContextScope.class);
    final Reasoner reasoner = mock(Reasoner.class);
    final Observable observable = mock(Observable.class);
    final Concept predicate = mock(Concept.class), result = mock(Concept.class);
    final ObservationImpl member = new ObservationImpl();
    final ActuatorImpl actuator = new ActuatorImpl();
    final Classifier classifier = new Classifier();
    final Scheduler.Event event = Scheduler.Event.initialization();
    Fixture() {
      var builder = mock(Observable.Builder.class);
      when(observable.builder(scope)).thenReturn(builder);
      when(builder.without(SemanticRole.INHERENT)).thenReturn(builder);
      when(builder.buildConcept()).thenReturn(predicate);
      when(observable.getContextualization()).thenReturn(Contextualization.CLASSIFICATION);
      when(scope.getService(Reasoner.class)).thenReturn(reasoner);
      when(memberScope.getService(Reasoner.class)).thenReturn(reasoner);
      when(scope.within(any(Observation.class))).thenReturn(memberScope);
      when(predicate.isAbstract()).thenReturn(true);
      when(predicate.is(SemanticType.PREDICATE)).thenReturn(true);
      when(predicate.getUrn()).thenReturn("test:Abstract");
      when(result.getUrn()).thenReturn("test:Concrete");
      when(result.is(SemanticType.PREDICATE)).thenReturn(true);
      when(reasoner.satisfiable(any())).thenReturn(true);
      when(reasoner.is(result, predicate)).thenReturn(true);
      member.setId(42); member.setObservable(mock(Observable.class));
      actuator.setActuatorType(Actuator.Type.UPDATE); actuator.setEffect(Actuator.Effect.SEMANTIC_UPDATE);
      actuator.setContextualization(Contextualization.CLASSIFICATION); actuator.setOperationObservable(observable);
      actuator.getComputation().add(new ServiceCallImpl("klab.generators.random.categories"));
      var binding = new ActuatorImpl.TargetBindingImpl(); binding.setKind(Actuator.TargetBinding.Kind.COHORT_MEMBERS);
      binding.setSources(List.of("cohort")); actuator.getTargetBindings().add(binding);
      classifier.result = result;
    }
    MemberClassifierExecutor executor() throws Exception {
      return new MemberClassifierExecutor(actuator, Classifier.class.getMethod("classify", Observable.class,
          Observation.class, ContextScope.class, ServiceCall.class), classifier, scope);
    }
    List<MemberClassifierExecutor.PendingAttribution> run(MemberClassifierExecutor executor) {
      return executor.execute(Map.of("cohort", List.of(member)), Geometry.UNIVERSAL, event, scope);
    }
    void optionalDependency() { var dependency = new ObservableImpl(); dependency.setOptional(true); actuator.setModelDependency(dependency); }
  }
  @Test void typedInvocationReturnsPendingAttributionWithoutMutation() throws Exception {
    var f = new Fixture(); var before = f.member.getObservable();
    var pending = f.run(f.executor()).getFirst();
    assertSame(f.observable, f.classifier.receivedObservable); assertSame(f.member, f.classifier.receivedMember);
    assertSame(f.memberScope, f.classifier.receivedScope); assertSame(f.result, pending.predicate());
    assertSame(f.predicate, pending.abstractPredicate()); assertSame(before, pending.originalObservable());
    assertSame(before, f.member.getObservable()); assertEquals(42, f.member.getId());
  }
  @Test void onlyOptionalOriginalModelDependencyAllowsNull() throws Exception {
    var f = new Fixture(); f.classifier.result = null;
    when(f.observable.isOptional()).thenReturn(true); // root/strategy optionality is insufficient
    assertThrows(IllegalStateException.class, () -> f.run(f.executor()));
    var required = new ObservableImpl(); required.setOptional(false); f.actuator.setModelDependency(required);
    assertThrows(IllegalStateException.class, () -> f.run(f.executor()));
    f.optionalDependency(); assertTrue(f.run(f.executor()).isEmpty());
  }
  @Test void invalidReturnsRemainErrorsEvenForOptionalDependency() throws Exception {
    for (String invalid : List.of("nothing", "abstract", "unrelated", "equivalent", "inconsistent", "wrong-family")) {
      var f = new Fixture(); f.optionalDependency();
      switch (invalid) {
        case "nothing" -> when(f.result.is(SemanticType.NOTHING)).thenReturn(true);
        case "abstract" -> when(f.result.isAbstract()).thenReturn(true);
        case "unrelated" -> when(f.reasoner.is(f.result, f.predicate)).thenReturn(false);
        case "equivalent" -> when(f.reasoner.is(f.predicate, f.result)).thenReturn(true);
        case "inconsistent" -> when(f.reasoner.satisfiable(f.result)).thenReturn(false);
        case "wrong-family" -> when(f.result.is(SemanticType.PREDICATE)).thenReturn(false);
      }
      assertThrows(IllegalStateException.class, () -> f.run(f.executor()), invalid);
    }
  }
  @Test void refusesReclassificationAndPreservesOtherPredicates() throws Exception {
    var f = new Fixture(); var unrelated = mock(Concept.class);
    when(f.reasoner.directTraits(f.member.getObservable())).thenReturn(List.of(unrelated));
    assertEquals(1, f.run(f.executor()).size());
    when(f.reasoner.is(unrelated, f.predicate)).thenReturn(true);
    assertThrows(IllegalStateException.class, () -> f.run(f.executor()));
    assertEquals(List.of(unrelated), f.reasoner.directTraits(f.member.getObservable()));
  }
  @Test void concurrentDuplicateRequestsInvokeOnceAndEmptyCohortNeedsNoClassifierCall() throws Exception {
    var f = new Fixture(); var executor = f.executor();
    var a = CompletableFuture.supplyAsync(() -> f.run(executor));
    var b = CompletableFuture.supplyAsync(() -> f.run(executor));
    assertEquals(a.get(), b.get()); assertEquals(1, f.classifier.calls.get());
    assertTrue(executor.execute(Map.of("cohort", List.of()), Geometry.UNIVERSAL, f.event, f.scope).isEmpty());
    assertThrows(IllegalStateException.class, () -> executor.execute(Map.of(), Geometry.UNIVERSAL, f.event, f.scope));
    assertEquals(1, f.classifier.calls.get());
  }
  @Test void mixedMembersAreDeduplicatedAndFailuresAreNotPublishedAsPartialSuccess() throws Exception {
    var f = new Fixture(); var provisional = new ObservationImpl(); provisional.setObservable(mock(Observable.class));
    var executor = f.executor();
    var values = executor.execute(Map.of("cohort", List.of(f.member, provisional, f.member)), Geometry.UNIVERSAL, f.event, f.scope);
    assertEquals(2, values.size()); assertEquals(2, f.classifier.calls.get());
    var failing = new Fixture(); failing.optionalDependency(); failing.classifier.failAt = 2;
    var failingExecutor = failing.executor();
    assertThrows(IllegalStateException.class, () -> failingExecutor.execute(
        Map.of("cohort", List.of(failing.member, provisional)), Geometry.UNIVERSAL, failing.event, failing.scope));
    assertEquals(42, failing.member.getId());
    assertThrows(IllegalStateException.class, () -> failingExecutor.execute(
        Map.of("cohort", List.of(failing.member, provisional)), Geometry.UNIVERSAL, failing.event, failing.scope));
    assertEquals(2, failing.classifier.calls.get(), "Failed invocation is not repeated within the event");
    assertFalse(MemberClassifierExecutor.supports(Classifier.class.getMethod("unsupported", Observable.class, Scope.class)));
  }
  @Test void registrySelectionRequiresOneSupportedLocalImplementation() throws Exception {
    var f = new Fixture();
    var registry = mock(org.integratedmodelling.klab.components.ComponentRegistry.class);
    var descriptor = new org.integratedmodelling.klab.api.services.runtime.extension.Extensions.FunctionDescriptor();
    var implementation = new org.integratedmodelling.klab.components.ComponentRegistry.ServiceImplementation();
    implementation.method = Classifier.class.getMethod("classify", Observable.class, Observation.class, ContextScope.class, ServiceCall.class);
    implementation.mainClassInstance = f.classifier;
    when(registry.getFunctionDescriptor(any(ServiceCall.class))).thenReturn(List.of(descriptor));
    when(registry.implementation(descriptor)).thenReturn(implementation);
    assertEquals(1, f.run(MemberClassifierExecutor.compile(f.actuator, registry, f.scope)).size());
    when(registry.getFunctionDescriptor(any(ServiceCall.class))).thenReturn(List.of(descriptor, descriptor));
    assertThrows(IllegalArgumentException.class, () -> MemberClassifierExecutor.compile(f.actuator, registry, f.scope));
    when(registry.getFunctionDescriptor(any(ServiceCall.class))).thenReturn(List.of());
    assertThrows(IllegalArgumentException.class, () -> MemberClassifierExecutor.compile(f.actuator, registry, f.scope));
  }
  @Test void actualGeneratorSourceSignatureAndEmptyClosure(@TempDir Path temp) throws Exception {
    String source = System.getProperty("classifier.generator.source");
    Assumptions.assumeTrue(source != null, "Set classifier.generator.source to test the actual generator checkout");
    var compiler = ToolProvider.getSystemJavaCompiler(); assertNotNull(compiler);
    assertEquals(0, compiler.run(null, null, null, "-classpath", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
        "-d", temp.toString(), source));
    try (var loader = new URLClassLoader(new java.net.URL[]{temp.toUri().toURL()}, getClass().getClassLoader())) {
      var method = loader.loadClass("org.integratedmodelling.generators.library.RandomContextualizers")
          .getMethod("generateConcept", Observable.class, ServiceCall.class, Scope.class);
      assertTrue(MemberClassifierExecutor.supports(method));
      var f = new Fixture(); when(f.reasoner.closure(f.observable)).thenReturn(Set.of(f.result));
      assertSame(f.result, f.run(new MemberClassifierExecutor(f.actuator, method, null, f.scope)).getFirst().predicate());
      when(f.reasoner.closure(f.observable)).thenReturn(Set.of());
      assertThrows(IllegalStateException.class, () -> f.run(new MemberClassifierExecutor(f.actuator, method, null, f.scope)));
      f.optionalDependency();
      assertTrue(f.run(new MemberClassifierExecutor(f.actuator, method, null, f.scope)).isEmpty());
    }
  }
}

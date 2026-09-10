package org.integratedmodelling.klab.components;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.*;

import java.util.List;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsStatementImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsArgumentsImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsActionImpl;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.runtime.kactors.compiler.BehaviorAnalyzer;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.impl.HistogramImpl;
import org.integratedmodelling.klab.api.services.runtime.extension.Actor;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.runtime.kactors.compiler.AgentCompiler;
import org.integratedmodelling.klab.runtime.libraries.CoreActorLibrary;
import org.junit.jupiter.api.Test;

class ComponentRegistryActorVerbTest {
  @Test
  void transmittedContextProxyCallsDoNotRetrieveJavaActorsAsBehaviorDocuments() throws Exception {
    var registry = mock(ComponentRegistry.class, CALLS_REAL_METHODS);
    var instances = ComponentRegistry.class.getDeclaredField("globalInstances");
    instances.setAccessible(true);
    instances.set(registry, new java.util.HashMap<>());
    var descriptor = discover(registry, CoreActorLibrary.Context.class);
    descriptor.urn = "core.context";
    doReturn(List.of()).when(registry).getActorDescriptors(anyString(), isNull());
    doReturn(List.of(descriptor)).when(registry).getActorDescriptors("core.context", null);
    var resolver = AgentCompiler.componentResolver(registry);
    var scope = mock(UserScope.class);
    var resources = mock(ResourcesService.class);
    when(scope.getService(ResourcesService.class)).thenReturn(resources);

    var imported = new KActorsBehaviorImpl.ImportImpl();
    imported.setImportedBehavior("core.context");
    imported.setImportedAlias("context");
    var create = new KActorsStatementImpl.VerbImpl();
    create.setRecipient("context");
    create.setMessage("new");
    create.setArguments(new KActorsArgumentsImpl());
    var assign = new KActorsStatementImpl.AssignmentImpl();
    assign.setVariable("ctx");
    assign.setAssignmentScope(KActorsStatement.Assignment.Scope.FRAME);
    assign.setFunction(create);
    var query = new KActorsStatementImpl.VerbImpl();
    query.setRecipient("ctx");
    query.setMessage("query");
    query.setArguments(new KActorsArgumentsImpl());
    var action = new KActorsActionImpl();
    action.setUrn("main");
    action.setCode(List.of(assign, query));
    var behavior = new KActorsBehaviorImpl();
    behavior.setUrn("test.context.transport");
    behavior.setDescription("Context proxy transport regression");
    behavior.setBehaviorType(KActorsBehavior.Type.BEHAVIOR);
    behavior.setImports(List.of(imported));
    behavior.setStatements(List.of(action));
    var mapper = JacksonConfiguration.newObjectMapper();
    var restored = mapper.readValue(mapper.writeValueAsString(behavior),
        KActorsBehavior.class);
    var environment = AgentCompiler.runtimeEnvironment(resolver, scope);
    var analyzer = new BehaviorAnalyzer(restored, environment.validator());
    assertTrue(analyzer.analyze(), analyzer.getNotifications().toString());
    assertNull(resolver.resolveBehavior("core.context", scope));
    assertNull(resolver.resolveBehavior(AgentCompiler.CORE_AGENT_URN, scope));
    verifyNoInteractions(resources);

    // Real document URNs still use the Resources transmission contract.
    when(resources.retrieve("test.remote", KActorsBehavior.class, scope)).thenReturn(restored);
    assertSame(restored, resolver.resolveBehavior("test.remote", scope));
  }

  @Test
  void inspectorAndStringsKeepDistinctImplementationsRegardlessOfDiscoveryOrder() throws Exception {
    for (boolean inspectorFirst : List.of(true, false)) {
      var registry = mock(ComponentRegistry.class, CALLS_REAL_METHODS);
      var globalInstances = ComponentRegistry.class.getDeclaredField("globalInstances");
      globalInstances.setAccessible(true);
      globalInstances.set(registry, new java.util.HashMap<>());
      var first = inspectorFirst ? CoreActorLibrary.Inspector.class : CoreActorLibrary.Strings.class;
      var second = inspectorFirst ? CoreActorLibrary.Strings.class : CoreActorLibrary.Inspector.class;
      var firstDescriptor = discover(registry, first);
      var secondDescriptor = discover(registry, second);
      for (var descriptor : List.of(firstDescriptor, secondDescriptor)) {
        doReturn(List.of(descriptor)).when(registry).getActorDescriptors(descriptor.urn, null);
        for (var verb : descriptor.verbs) {
          assertTrue(verb.serviceInfo.getName().startsWith(descriptor.urn + "."));
          assertEquals(descriptor.javaClassName, registry.implementation(verb).implementation.getName());
        }
      }

      var resolver = AgentCompiler.componentResolver(registry);
      var inspector = resolver.resolveActor("regression.inspector", null);
      var strings = resolver.resolveActor("regression.strings", null);
      assertSame(CoreActorLibrary.Inspector.class, inspector.implementationClass());
      assertSame(CoreActorLibrary.Strings.class, strings.implementationClass());
      assertSame(CoreActorLibrary.Inspector.class, inspector.verbs().get("contains").method.getDeclaringClass());
      assertSame(CoreActorLibrary.Strings.class, strings.verbs().get("contains").method.getDeclaringClass());
      assertSame(inspector.verbs().get("viable"), inspector.verbs().get("regression.inspector.viable"));

      // Exercise the same boolean metadata delivered by the staging testcase's !nodata call.
      var histogram = new HistogramImpl();
      histogram.setEmpty(false);
      var bin = new HistogramImpl.BinImpl();
      bin.setCount(3);
      histogram.getBins().add(bin);
      var viable = inspector.implementationClass().getMethod("checkViable", RuntimeAgent.Scope.class, Object[].class);
      assertEquals(true, viable.invoke(null, null, new Object[] {histogram, Metadata.create("nodata", false)}));
      histogram.setMissingCount(1);
      assertEquals(false, viable.invoke(null, null, new Object[] {histogram, Metadata.create("nodata", false)}));
    }
  }

  private Extensions.ActorDescriptor discover(ComponentRegistry registry, Class<?> actor) throws Exception {
    var method = ComponentRegistry.class.getDeclaredMethod("createActorDescriptor", Actor.class, String.class, Class.class);
    method.setAccessible(true);
    return (Extensions.ActorDescriptor) method.invoke(registry, actor.getAnnotation(Actor.class), "regression.", actor);
  }
}

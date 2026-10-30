package org.integratedmodelling.klab.modeler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.kim.impl.KimSymbolDefinitionImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.junit.jupiter.api.Test;

class ObserverSettingSubmissionTest {
  @Test
  void observeTransmitsClientOptOutAlongsideLexicalConstraints() {
    for (boolean disabled : new boolean[] {false, true}) {
      var modeler = mock(ModelerImpl.class);
      var engine = mock(Engine.class);
      var settings = mock(Settings.class);
      when(modeler.engine()).thenReturn(engine);
      when(engine.getSettings()).thenReturn(settings);
      when(settings.get(Setting.DO_NOT_CREATE_A_DEFAULT_OBSERVER, Boolean.class)).thenReturn(disabled);
      var scope = mock(ContextScope.class);
      var selected = mock(ContextScope.class);
      var runtime = mock(RuntimeService.class);
      var reasoner = mock(Reasoner.class);
      when(scope.getService(Reasoner.class)).thenReturn(reasoner);
      when(scope.getService(RuntimeService.class)).thenReturn(runtime);
      when(selected.getService(RuntimeService.class)).thenReturn(runtime);
      when(scope.withResolutionConstraints(any(ResolutionConstraint[].class))).thenAnswer(call -> {
        var constraints = (ResolutionConstraint[]) call.getRawArguments()[0];
        var optOut = Arrays.stream(constraints)
            .filter(c -> c.getType() == ResolutionConstraint.Type.DoNotCreateDefaultObserver).toList();
        assertEquals(disabled ? 1 : 0, optOut.size());
        if (disabled) assertEquals(java.util.List.of(true), optOut.getFirst().payload(Boolean.class));
        assertTrue(Arrays.stream(constraints).anyMatch(c -> c.getType() == ResolutionConstraint.Type.ResolutionNamespace));
        return selected;
      });
      var concept = new ConceptImpl();
      concept.setType(EnumSet.of(SemanticType.SUBJECT));
      var observable = new ObservableImpl();
      observable.setSemantics(concept);
      when(reasoner.resolveObservable("test:Subject")).thenReturn(observable);
      var definition = new KimSymbolDefinitionImpl();
      definition.setDefineClass("observation");
      definition.setNamespace("test");
      definition.setProjectName("project");
      definition.setUrn("test.request");
      definition.setValue(Map.of("semantics", "test:Subject"));
      var pending = new CompletableFuture<Observation>();
      when(runtime.submit(any(), same(selected))).thenReturn(pending);
      doCallRealMethod().when(modeler).observe(scope, definition, false);

      assertSame(pending, modeler.observe(scope, definition, false));
      verify(runtime).submit(any(), same(selected));
    }
  }
}

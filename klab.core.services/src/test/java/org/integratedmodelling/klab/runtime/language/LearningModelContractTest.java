package org.integratedmodelling.klab.runtime.language;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import org.integratedmodelling.common.knowledge.ModelImpl;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.kim.KimModel;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.extension.LearningContextualizer;
import org.integratedmodelling.languages.api.ModelSyntax;
import org.junit.jupiter.api.Test;

class LearningModelContractTest {

  @Test
  void adaptationAndProjectionKeepExactResourceIdIndependentOfModelUrn() throws Exception {
    var syntax = mock(ModelSyntax.class);
    when(syntax.getModelType()).thenReturn(ModelSyntax.ModelType.LEARN);
    when(syntax.getLearnedComputationURN()).thenReturn("fitted.runoff");
    when(syntax.getName()).thenReturn("generated-name");
    var namespace = new KimNamespaceImpl();
    namespace.setUrn("hydrology.training");
    var method = LanguageAdapter.class.getDeclaredMethod(
        "adaptModel", ModelSyntax.class, KimNamespace.class, Map.class);
    method.setAccessible(true);
    var source = (KimModel) method.invoke(LanguageAdapter.INSTANCE, syntax, namespace, Map.of());
    assertTrue(source.isLearningModel());
    assertEquals("fitted.runoff", source.getLearnedResourceId());
    assertNotEquals(source.getLearnedResourceId(), source.getUrn());

    var model = SourceModelMapper.load(source, namespace, mock(ContextScope.class));
    assertTrue(model.isLearningModel());
    assertEquals("fitted.runoff", model.getLearnedResourceId());
    assertEquals("hydrology.training", model.getNamespace());
    assertEquals(source.getUrn(), model.getUrn());
    var transported = Utils.Json.parseObject(Utils.Json.asString(model), Model.class);
    assertTrue(transported.isLearningModel());
    assertEquals("fitted.runoff", transported.getLearnedResourceId());
    var sourceCopy = Utils.Json.parseObject(Utils.Json.asString(source), KimModel.class);
    assertTrue(sourceCopy.isLearningModel());
    assertEquals("fitted.runoff", sourceCopy.getLearnedResourceId());
  }

  @Test
  void ordinaryModelsDefaultToNoLearningIdentity() {
    var model = new ModelImpl();
    assertFalse(model.isLearningModel());
    assertNull(model.getLearnedResourceId());
  }

  @Test
  void inputSnapshotsEngineParametersAndRetainsObservationHandle() {
    var observation = mock(Observation.class);
    var parameters = new HashMap<String, Object>();
    parameters.put("weight", 0.5);
    var input = new LearningContextualizer.Input("rainfall", observation, parameters);
    parameters.put("weight", 1.0);
    assertEquals(0.5, input.parameters().get("weight"));
    assertSame(observation, input.observation());
    assertThrows(UnsupportedOperationException.class, () -> input.parameters().put("weight", 2));
    assertThrows(IllegalArgumentException.class,
        () -> new LearningContextualizer.Input(" ", observation, Map.of()));
    assertThrows(NullPointerException.class,
        () -> new LearningContextualizer.Input("rainfall", null, Map.of()));
  }
}

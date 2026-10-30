package org.integratedmodelling.klab.services.runtime.neo4j;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Values;

class ObservationCoveragePersistenceTest {
  @Test void coldRestorationPreservesPartialAndFullReceiptsAndDoesNotInventLegacyCoverage()
      throws Exception {
    var concept = new ConceptImpl(); concept.setUrn("test:Elevation"); concept.setName("Elevation");
    concept.getType().add(SemanticType.QUALITY);
    var observable = ObservableImpl.promote(concept, null);
    var scope = mock(Scope.class);
    var reasoner = mock(Reasoner.class);
    var runtime = mock(RuntimeService.class);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    when(scope.getService(RuntimeService.class)).thenReturn(runtime);
    when(reasoner.resolveObservable("test:Elevation")).thenReturn(observable);
    when(runtime.getUrl()).thenReturn(URI.create("http://127.0.0.1/runtime").toURL());
    var writer = mock(AbstractKnowledgeGraph.class, CALLS_REAL_METHODS);
    var reader = mock(KnowledgeGraphNeo4j.class, CALLS_REAL_METHODS);
    var restore = KnowledgeGraphNeo4j.class.getDeclaredMethod(
        "restoreObservationProperties", org.neo4j.driver.Value.class, Scope.class);
    restore.setAccessible(true);
    for (double fraction : new double[] {0.375, 1.0}) {
      var source = new ObservationImpl(); source.setObservable(observable); source.setId(123);
      source.setUrn("context.123"); source.setParentId(20); source.setResolvedCoverage(fraction);
      var properties = writer.asParameters(source);
      assertEquals(fraction, properties.get(GraphModel.Fields.RESOLVED_COVERAGE));
      var restored = (ObservationImpl) restore.invoke(reader, Values.value(properties), scope);
      assertEquals(123, restored.getId());
      assertEquals(fraction, restored.getResolvedCoverage());
      properties.remove(GraphModel.Fields.RESOLVED_COVERAGE);
      var legacy = (ObservationImpl) restore.invoke(reader, Values.value(properties), scope);
      assertEquals(0, legacy.getResolvedCoverage(), "Missing resolution evidence is unknown, not complete");
    }
  }
}

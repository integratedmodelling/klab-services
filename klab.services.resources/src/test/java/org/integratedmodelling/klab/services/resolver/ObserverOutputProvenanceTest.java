package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.EnumSet;
import java.util.List;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.ObservationStrategy;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ObserverOutputProvenanceTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }
  private ObservableImpl observable(String name, SemanticType type) {
    var concept = new ConceptImpl(); concept.setUrn(name); concept.setType(EnumSet.of(type));
    var result = new ObservableImpl(); result.setSemantics(concept); result.setUrn(name); return result;
  }
  @Test void actualDataflowSelectsSpecificOutputRatherThanFirstAbsoluteVariant() { compileOutput(false); }
  @Test void nullDefaultStillRetainsSoleActualSpecificOutput() { compileOutput(true); }
  private void compileOutput(boolean nullDefault) {
    var scope = mock(ContextScope.class); when(scope.getId()).thenReturn("test");
    var reasoner = mock(Reasoner.class); when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    var receipt = mock(Reasoner.Capabilities.class); when(receipt.getKnowledgeRevision()).thenReturn(7L);when(receipt.getServiceId()).thenReturn("fixture-reasoner");
    when(reasoner.capabilities(scope)).thenReturn(receipt);
    when(reasoner.semanticDistance(any(), any(), isNull(), eq(7L))).thenReturn(0);
    var quality = observable("test:Quality", SemanticType.QUALITY); quality.setUrn("test:Quality in m");
    var observer = observable("test:Observer", SemanticType.AGENT);
    when(reasoner.resolveConcept(observer.getUrn())).thenReturn(observer.asConcept());
    var selected = new ObservationImpl(); selected.setId(100); selected.setObservable(observer);
    when(scope.getObserver()).thenReturn(nullDefault ? null : selected);
    var specific = ObservableImpl.withObserverSemantics(quality, observer.asConcept());
    var support = GeometryRepository.INSTANCE.scale(Geometry.create("1"));
    var request = new ObservationImpl(); request.setId(-2); request.setObservable(quality); request.setGeometry(support);
    var model = mock(Model.class); when(model.getUrn()).thenReturn("test:model"); when(model.getNamespace()).thenReturn("test");
    when(model.getCoverage()).thenReturn(support); when(model.getObservables()).thenReturn(nullDefault ? List.of(specific) : List.of(quality, specific));
    var resources = ModelAdmissionFixtures.prepare(scope, reasoner); ModelAdmissionFixtures.saved(resources, model, scope);
    var strategy = mock(ObservationStrategy.class);
    var graph = ResolutionGraph.create(scope).createChild(request, support);
    graph.admitModel(model,org.integratedmodelling.klab.runtime.language.ModelValidationGate.validatedPreparableAdmission(model,scope,quality));
    var strategyGraph = graph.createChild(strategy, support);
    strategyGraph.merge(strategyGraph.createChild(model, support)); graph.merge(strategyGraph);
    var flow = new DataflowCompiler(request, graph, scope).compile();
    var root = flow.getComputation().getFirst();
    assertEquals("test:Quality observed as test:Observer in m", root.getObservation().getObservable().getUrn());
    assertNull(root.getOperationObservable().getObserverSemantics());
    assertNull(request.getObservable().getObserverSemantics());
    var entries = (List<?>) root.getData().get(DataflowCompiler.OBSERVER_CONTRIBUTIONS);
    var contribution = org.integratedmodelling.klab.utilities.Utils.Json.parseObject(
        entries.getFirst().toString(), DataflowCompiler.ObserverContribution.class);
    assertEquals(observer.getUrn(), contribution.observerUrn()); assertEquals(7, contribution.revision());
  }
}

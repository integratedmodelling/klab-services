package org.integratedmodelling.klab.services.resources.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.integratedmodelling.common.knowledge.ModelImpl;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Contextualization;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimModelImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.services.resolver.PrioritizerImpl;
import org.junit.jupiter.api.Test;

class OccurrenceModelIndexTest {
  @Test void failedChangeConstructionIdentifiesTheProcessAndQuality() {
    var scope = mock(ContextScope.class);
    var reasoner = mock(Reasoner.class);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    var process = observable("test:Erosion", SemanticType.PROCESS);
    var quality = observable("test:Elevation", SemanticType.QUALITY);
    when(reasoner.affectedBy(quality, process)).thenReturn(true);
    var builder = mock(Observable.Builder.class);
    when(quality.builder(scope)).thenReturn(builder);
    when(builder.as(UnarySemanticOperator.CHANGE)).thenReturn(builder);
    var error = assertThrows(org.integratedmodelling.klab.api.exceptions.KlabValidationException.class,
        () -> org.integratedmodelling.klab.runtime.language.OccurrentSemantics.changes(
            process, List.of(quality), scope));
    assertTrue(error.getMessage().contains("test:Erosion"));
    assertTrue(error.getMessage().contains("test:Elevation"));
  }

  private Observable observable(String urn, SemanticType... types) {
    var observable = mock(Observable.class);
    var concept = mock(Concept.class);
    when(observable.getUrn()).thenReturn(urn);
    when(observable.getSemantics()).thenReturn(concept);
    when(concept.getUrn()).thenReturn(urn);
    for (var type : types) when(observable.is(type)).thenReturn(true);
    when(observable.getContextualization()).thenReturn(Contextualization.SIMULATION);
    return observable;
  }

  private KimObservable syntax(Observable observable) {
    var syntax = mock(KimObservable.class);
    var urn = observable.getUrn();
    when(syntax.getUrn()).thenReturn(urn);
    when(syntax.getSemantics()).thenReturn(new KimConceptImpl());
    return syntax;
  }

  @Test void affectedAndCreatedDependenciesAreIndexedAndAcceptedBySemanticRanking() {
    var scope = mock(ContextScope.class);
    var reasoner = mock(Reasoner.class);
    var resources = mock(ResourcesService.class);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    var process = observable("test:Erosion", SemanticType.PROCESS);
    var affected = observable("test:Elevation", SemanticType.QUALITY);
    var created = observable("test:Sediment", SemanticType.QUALITY);
    var input = observable("test:Intensity", SemanticType.QUALITY);
    var changeX = observable("change in test:Elevation", SemanticType.PROCESS, SemanticType.CHANGE);
    var changeY = observable("change in test:Sediment", SemanticType.PROCESS, SemanticType.CHANGE);
    for (var pair : List.of(List.of(affected, changeX), List.of(created, changeY))) {
      var builder = mock(Observable.Builder.class);
      when(pair.getFirst().builder(scope)).thenReturn(builder);
      when(builder.as(UnarySemanticOperator.CHANGE)).thenReturn(builder);
      when(builder.buildObservable()).thenReturn(pair.getLast());
    }
    when(reasoner.affectedBy(affected, process)).thenReturn(true);
    when(reasoner.createdBy(created, process)).thenReturn(true);
    for (var observable : List.of(process, affected, created, input))
      when(reasoner.resolveObservable(observable.getUrn())).thenReturn(observable);
    var model = new KimModelImpl();
    model.setUrn("test.model"); model.setNamespace("test");
    model.getObservables().add(syntax(process));
    model.getDependencies().addAll(List.of(syntax(affected), syntax(created), syntax(input), syntax(affected)));
    when(resources.retrieve("test", KimNamespace.class, scope)).thenReturn(mock(KimNamespace.class));
    var kbox = mock(ModelKbox.class, CALLS_REAL_METHODS);
    kbox.resourceService = resources;
    var descriptors = kbox.inferModels(model, scope);
    assertEquals(List.of(process.getUrn(), changeX.getUrn(), changeY.getUrn()),
        descriptors.stream().map(ModelReference::getObservable).toList());
    assertTrue(descriptors.stream().allMatch(d -> d.getName().equals(model.getUrn())));
    verify(input, never()).builder(any());

    var executable = new ModelImpl();
    executable.getObservables().add(process);
    executable.getDependencies().addAll(List.of(affected, created, input));
    when(reasoner.semanticDistance(process, changeX, null)).thenReturn(-1);
    when(reasoner.semanticDistance(changeY, changeX, null)).thenReturn(-1);
    when(reasoner.semanticDistance(changeX, changeX, null)).thenReturn(0);
    assertEquals(0, new PrioritizerImpl(scope, null, Map.of(), changeX, null).semanticDistance(executable));
  }
}

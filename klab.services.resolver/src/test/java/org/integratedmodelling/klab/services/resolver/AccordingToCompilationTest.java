package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.integratedmodelling.common.knowledge.ModelImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.Codelist;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.junit.jupiter.api.Test;

class AccordingToCompilationTest {

  @Test
  void resolvesCodelistFromTheServiceOwningTheTypePredicate() {
    var scope = mock(ContextScope.class);
    var reasoner = mock(Reasoner.class);
    var resources = mock(ResourcesService.class);
    var output = mock(Concept.class);
    var root = mock(Concept.class);
    var codelist = mock(Codelist.class);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    when(scope.getServices(ResourcesService.class)).thenReturn(List.of(resources));
    when(output.is(SemanticType.CLASS)).thenReturn(true);
    when(root.is(SemanticType.PREDICATE)).thenReturn(true);
    when(root.getUrn()).thenReturn("landcover:LandCoverType");
    when(root.getServiceId()).thenReturn("resources-one");
    when(reasoner.describedType(output)).thenReturn(root);
    when(resources.serviceId()).thenReturn("resources-one");
    when(resources.retrieve("landcover:LandCoverType", Codelist.class, scope))
        .thenReturn(codelist);
    when(codelist.getAuthorityIds()).thenReturn(Set.of("corine"));
    when(codelist.codes("corine")).thenReturn(List.of(231L));

    var model = model(output);
    var compiler =
        new DataflowCompiler(
            mock(Observation.class), mock(ResolutionGraph.class), scope);
    assertEquals(codelist, compiler.resolveCodelist("corine", model));
  }

  @Test
  void rejectsAccordingToForAnythingOtherThanTypeOfPredicate() {
    var scope = mock(ContextScope.class);
    var output = mock(Concept.class);
    when(output.is(SemanticType.CLASS)).thenReturn(false);
    var compiler =
        new DataflowCompiler(
            mock(Observation.class), mock(ResolutionGraph.class), scope);
    assertThrows(
        KlabValidationException.class,
        () -> compiler.resolveCodelist("corine", model(output)));
  }

  private static ModelImpl model(Concept semantics) {
    var observable = new ObservableImpl();
    observable.setSemantics(semantics);
    var model = new ModelImpl();
    model.getObservables().add(observable);
    return model;
  }
}

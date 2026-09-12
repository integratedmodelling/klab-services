package org.integratedmodelling.klab.services.resources.lang;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.EnumSet;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.reasoner.internal.SemanticsBuilder;
import org.integratedmodelling.klab.services.reasoner.owl.OWL;
import org.junit.jupiter.api.Test;

class ObservationSemanticAnnotationsTest {
  @Test
  void semanticBuilderPreservesInheritedAndExplicitAnnotations() {
    var type = EnumSet.of(SemanticType.SUBJECT, SemanticType.COUNTABLE,
        SemanticType.OBSERVABLE, SemanticType.DIRECT_OBSERVABLE);
    var concept = new ConceptImpl();
    concept.setUrn("test:Thing");
    concept.setName("Thing");
    concept.setNamespace("test");
    concept.getType().addAll(type);
    concept.getAnnotations().add(Annotation.of("label", "value", "concept"));
    var syntax = new KimConceptImpl();
    syntax.setName("test:Thing");
    syntax.setUrn("test:Thing");
    syntax.setType(type);
    var reasoner = mock(ReasonerService.class);
    when(reasoner.resolveConcept("test:Thing")).thenReturn(concept);
    when(reasoner.owl()).thenReturn(mock(OWL.class));
    var resources = mock(ResourcesService.class);
    when(resources.declareConcept("test:Thing")).thenReturn(syntax);
    var scope = mock(Scope.class);
    when(scope.getService(ResourcesService.class)).thenReturn(resources);
    var source = ObservableImpl.promote(concept, null);
    source.getAnnotations().add(Annotation.of("style", "value", "dependency"));
    var builder = SemanticsBuilder.create(source, reasoner, scope);
    assertSame(builder, builder.withAnnotation(Annotation.of("style", "value", "override")));
    var built = builder.buildObservable();
    assertEquals("override", built.getAnnotations().getFirst().get("value"));
    assertEquals(1, built.getAnnotations().size());
    assertEquals("concept", built.getSemantics().getAnnotations().getFirst().get("value"));
    assertEquals("dependency", source.getAnnotations().getFirst().get("value"));
  }
}

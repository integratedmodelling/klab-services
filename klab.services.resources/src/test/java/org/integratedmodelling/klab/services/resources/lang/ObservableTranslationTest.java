package org.integratedmodelling.klab.services.resources.lang;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.data.mediation.NumericRange;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.reasoner.internal.SemanticsBuilder;
import org.integratedmodelling.klab.services.reasoner.owl.OWL;
import org.junit.jupiter.api.Test;

class ObservableTranslationTest {
  @Test
  void builderRetainsRangeAndGenericFlagAndAcceptsRangeReplacement() {
    var concept = new ConceptImpl();
    concept.setUrn("test:Thing");
    concept.setName("Thing");
    concept.setNamespace("test");
    concept.getType().addAll(java.util.EnumSet.of(
        SemanticType.SUBJECT, SemanticType.OBSERVABLE, SemanticType.COUNTABLE));
    var syntax = new KimConceptImpl();
    syntax.setName(concept.getUrn());
    syntax.setType(concept.getType());
    var reasoner = mock(ReasonerService.class);
    when(reasoner.resolveConcept(concept.getUrn())).thenReturn(concept);
    when(reasoner.owl()).thenReturn(new OWL(mock(Scope.class)));
    var resources = mock(ResourcesService.class);
    when(resources.declareConcept(concept.getUrn())).thenReturn(syntax);
    var scope = mock(Scope.class);
    when(scope.getService(ResourcesService.class)).thenReturn(resources);
    var observable = ObservableImpl.promote(concept, null);
    var range = mock(NumericRange.class);
    observable.setRange(range);
    observable.setGeneric(true);
    var builder = SemanticsBuilder.create(observable, reasoner, scope);
    var rebuilt = builder.buildObservable();
    assertSame(range, rebuilt.getRange());
    assertTrue(rebuilt.isGeneric());
    var replacement = mock(NumericRange.class);
    assertSame(builder, builder.withRange(replacement));
    assertSame(replacement, builder.buildObservable().getRange());
    assertSame(range, observable.getRange());
    when(reasoner.declareConcept(any())).thenReturn(concept);
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabValidationException.class,
        () -> builder.withObserverSemantics(concept).buildObservable());
    var agent = new ConceptImpl();
    agent.setUrn("test:Observer");
    agent.getType().addAll(java.util.EnumSet.of(SemanticType.AGENT, SemanticType.SUBJECT));
    var agentSyntax = new KimConceptImpl();
    agentSyntax.setName(agent.getUrn());
    agentSyntax.setType(agent.getType());
    when(resources.declareConcept(agent.getUrn())).thenReturn(agentSyntax);
    when(reasoner.declareConcept(any())).thenReturn(agent);
    assertEquals(agent, builder.withObserverSemantics(agent).buildObservable().getObserverSemantics());
  }
}

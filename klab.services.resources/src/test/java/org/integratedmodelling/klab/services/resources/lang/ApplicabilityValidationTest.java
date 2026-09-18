package org.integratedmodelling.klab.services.resources.lang;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.reasoner.internal.SemanticsBuilder;
import org.integratedmodelling.klab.services.reasoner.owl.OWL;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;

class ApplicabilityValidationTest {
  @Test void semanticBuilderEnforcesDomainsForInherentsAndPredicates() throws Exception {
    var scope=mock(Scope.class); var reasoner=mock(ReasonerService.class);
    when(scope.getService(ResourcesService.class)).thenReturn(mock(ResourcesService.class));
    when(scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class)).thenReturn(reasoner);
    var owl=new OWL(scope);
    var field=OWL.class.getDeclaredField("manager"); field.setAccessible(true); field.set(owl,OWLManager.createOWLOntologyManager());
    var ontology=owl.requireOntology("audit");
    ontology.define(List.of(Axiom.ClassAssertion("Height",EnumSet.of(SemanticType.QUALITY,SemanticType.OBSERVABLE)),
        Axiom.ClassAssertion("Tree",EnumSet.of(SemanticType.SUBJECT,SemanticType.COUNTABLE,SemanticType.OBSERVABLE)),
        Axiom.ClassAssertion("Rock",EnumSet.of(SemanticType.SUBJECT,SemanticType.COUNTABLE,SemanticType.OBSERVABLE))));
    owl.requireOntology("odo").define(List.of(Axiom.ObjectPropertyAssertion("appliesTo"),Axiom.ObjectPropertyAssertion("isInherentTo")));
    owl.setApplicableObservables(ontology.getConcept("Height"),List.of(ontology.getConcept("Tree")),ontology);
    when(reasoner.owl()).thenReturn(owl);
    when(reasoner.resolveConcept(anyString())).thenAnswer(call -> owl.getConcept(call.getArgument(0)));
    var syntax=new KimConceptImpl(); syntax.setName("audit:Height"); syntax.setType(ontology.getConcept("Height").getType());
    var inherent=new KimConceptImpl(); inherent.setName("audit:Rock"); inherent.setType(ontology.getConcept("Rock").getType());
    syntax.setInherent(inherent);
    assertThrows(KlabValidationException.class,() -> SemanticsBuilder.create(syntax,reasoner,scope).buildConcept());
    inherent.setName("audit:Tree"); syntax.resetDefinition();
    assertNotNull(SemanticsBuilder.create(syntax,reasoner,scope).buildConcept());

    syntax.setInherent(null); syntax.resetDefinition();
    when(reasoner.inherent(ontology.getConcept("Height"))).thenReturn(ontology.getConcept("Rock"));
    assertThrows(KlabValidationException.class,() -> SemanticsBuilder.create(syntax,reasoner,scope).buildConcept());
    when(reasoner.inherent(ontology.getConcept("Height"))).thenReturn(ontology.getConcept("Tree"));
    assertNotNull(SemanticsBuilder.create(syntax,reasoner,scope).buildConcept());

    ontology.define(List.of(Axiom.ClassAssertion("Red",EnumSet.of(SemanticType.ATTRIBUTE,SemanticType.PREDICATE))));
    owl.requireOntology("odo").define(List.of(Axiom.ObjectPropertyAssertion("hasAttribute")));
    var red=ontology.getConcept("Red");
    red.getMetadata().put(org.integratedmodelling.klab.services.reasoner.internal.CoreOntology.NS.TRAIT_RESTRICTING_PROPERTY,"odo:hasAttribute");
    when(reasoner.lexicalRoot(red)).thenReturn(red);
    owl.setApplicableObservables(red,List.of(ontology.getConcept("Tree")),ontology);
    var predicate=new KimConceptImpl(); predicate.setName("audit:Red"); predicate.setType(red.getType());
    var qualified=new KimConceptImpl(); qualified.setName("audit:Rock"); qualified.setType(ontology.getConcept("Rock").getType());
    qualified.getTraits().add(predicate); qualified.resetDefinition();
    assertThrows(KlabValidationException.class,() -> SemanticsBuilder.create(qualified,reasoner,scope).buildConcept());
    qualified.setName("audit:Tree"); qualified.resetDefinition();
    assertNotNull(SemanticsBuilder.create(qualified,reasoner,scope).buildConcept());
  }
}

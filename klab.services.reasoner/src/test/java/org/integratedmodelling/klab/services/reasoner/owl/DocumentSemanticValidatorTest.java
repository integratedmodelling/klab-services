package org.integratedmodelling.klab.services.reasoner.owl;

import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.reasoner.internal.DocumentSemanticValidator;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;

class DocumentSemanticValidatorTest {
  @Test void applicabilityFailurePointsToTheInherentOccurrence() {
    var owl = new OWL(mock(Scope.class)); owl.manager = OWLManager.createOWLOntologyManager();
    var ontology = owl.requireOntology("earth");
    ontology.define(List.of(
        Axiom.ClassAssertion("Height", EnumSet.of(SemanticType.QUALITY, SemanticType.OBSERVABLE)),
        Axiom.ClassAssertion("Tree", EnumSet.of(SemanticType.SUBJECT, SemanticType.COUNTABLE)),
        Axiom.ClassAssertion("Rock", EnumSet.of(SemanticType.SUBJECT, SemanticType.COUNTABLE))));
    owl.requireOntology("odo").define(List.of(Axiom.ObjectPropertyAssertion("appliesTo")));
    owl.setApplicableObservables(ontology.getConcept("Height"), List.of(ontology.getConcept("Tree")), ontology);
    var reasoner = mock(ReasonerService.class); when(reasoner.owl()).thenReturn(owl);
    when(reasoner.resolveConcept(anyString())).thenAnswer(c -> owl.getConcept(c.getArgument(0)));
    when(reasoner.declareConcept(any())).thenAnswer(c -> owl.getConcept(((org.integratedmodelling.klab.api.lang.kim.KimConcept)c.getArgument(0)).getName()));
    var document = new KimNamespaceImpl(); document.setUrn("test");
    var height = syntax("Height", 10); height.setInherent(syntax("Rock", 30));
    var observable = new KimObservableImpl(); observable.setSemantics(height);
    var model = new KimModelImpl(); model.getObservables().add(observable); document.getStatements().add(model);
    var errors = new DocumentSemanticValidator(reasoner).validate(document);
    assertEquals(1, errors.size());
    assertEquals(30, errors.getFirst().getLexicalContext().getOffsetInDocument());
    assertTrue(errors.getFirst().getMessage().contains("applies to"));
  }

  @Test void ontologyEditsWaitForSynchronizationAndSyntaxErrorsSkipSemantics() throws Exception {
    var reasoner = mock(ReasonerService.class);
    when(reasoner.validateDocument(any(), any())).thenCallRealMethod();
    when(reasoner.knowledgeRevision()).thenReturn(7L);
    var worldview = ReasonerService.class.getDeclaredField("worldview"); worldview.setAccessible(true);
    worldview.set(reasoner, mock(org.integratedmodelling.klab.api.knowledge.Worldview.class));
    var loaded = ReasonerService.class.getDeclaredField("loadedOntologySources"); loaded.setAccessible(true);
    loaded.set(reasoner, new HashMap<String,String>());
    var diagnostics = ReasonerService.class.getDeclaredField("loadedOntologyDiagnostics"); diagnostics.setAccessible(true);
    diagnostics.set(reasoner, new HashMap<>());
    var scope = mock(Scope.class);
    when(scope.getService(org.integratedmodelling.klab.api.services.ResourcesService.class))
        .thenReturn(mock(org.integratedmodelling.klab.api.services.ResourcesService.class));
    var ontology = new KimOntologyImpl(); ontology.setUrn("earth"); ontology.setSourceCode("edited ontology");
    var request = SemanticValidationRequest.of(ontology, "2");
    var response = reasoner.validateDocument(request, scope);
    assertEquals(SemanticValidationResponse.Status.STALE_KNOWLEDGE, response.getStatus());
    assertEquals(7, response.getKnowledgeRevision()); assertFalse(response.valid());
    @SuppressWarnings("unchecked")
    var hashes = (Map<String,String>) loaded.get(reasoner);
    hashes.put("earth", SemanticValidationRequest.sourceHash(ontology.getSourceCode()));
    when(reasoner.owl()).thenReturn(mock(OWL.class));
    assertTrue(reasoner.validateDocument(request, scope).valid());
    var lexical = new KimConceptImpl(); lexical.setOffsetInDocument(12); lexical.setLength(8);
    var notification = org.integratedmodelling.klab.api.services.runtime.Notification.error("Invalid ontology clause",
        org.integratedmodelling.klab.api.services.runtime.Notification.LexicalContext.of(lexical, ontology));
    diagnostics.set(reasoner, Map.of("earth", List.of(notification)));
    var invalid = reasoner.validateDocument(request, scope);
    assertEquals(SemanticValidationResponse.Status.COMPLETE, invalid.getStatus());
    assertFalse(invalid.valid());
    assertEquals(12, invalid.getNotifications().getFirst().getLexicalContext().getOffsetInDocument());
    request.setKnowledgeRevision(6);
    assertEquals(SemanticValidationResponse.Status.STALE_KNOWLEDGE, reasoner.validateDocument(request, scope).getStatus());
    ontology.getNotifications().add(org.integratedmodelling.klab.api.services.runtime.Notification.error("syntax error"));
    assertEquals(SemanticValidationResponse.Status.SYNTAX_ERRORS,
        reasoner.validateDocument(request, scope).getStatus());
    verify(reasoner, never()).declareConcept(any());
    verify(reasoner, never()).defineConcept(any(), any());
  }

  @Test void invalidEndpointsHaveOccurrenceLocationsWithoutChangingCachedConcepts() {
    var owl = new OWL(mock(Scope.class)); owl.manager = OWLManager.createOWLOntologyManager();
    var ontology = owl.requireOntology("earth");
    ontology.define(List.of(
        Axiom.ClassAssertion("StreamConnection", EnumSet.of(SemanticType.RELATIONSHIP, SemanticType.COUNTABLE)),
        Axiom.ClassAssertion("StreamJunction", EnumSet.of(SemanticType.SUBJECT, SemanticType.COUNTABLE)),
        Axiom.ClassAssertion("Region", EnumSet.of(SemanticType.SUBJECT, SemanticType.COUNTABLE))));
    owl.requireOntology("odo").define(List.of(Axiom.ObjectPropertyAssertion("impliesSource"),
        Axiom.ObjectPropertyAssertion("impliesDestination")));
    var declaration = new KimConceptStatementImpl();
    declaration.getSubjectsLinked().add(new ApplicableConceptImpl(syntax("StreamJunction", 0), syntax("StreamJunction", 0)));
    WorldviewDeclarationSupport.compile(owl, ontology, ontology.getConcept("StreamConnection"), declaration,
        s -> owl.getConcept(s.getName()));
    var reasoner = mock(ReasonerService.class);
    when(reasoner.owl()).thenReturn(owl);
    when(reasoner.resolveConcept(anyString())).thenAnswer(c -> owl.getConcept(c.getArgument(0)));
    when(reasoner.declareConcept(any())).thenAnswer(c -> owl.getConcept(((org.integratedmodelling.klab.api.lang.kim.KimConcept)c.getArgument(0)).getName()));
    when(reasoner.satisfiable(any())).thenReturn(true);
    var document = new KimNamespaceImpl(); document.setUrn("test"); document.setProjectName("project");
    for (int offset : List.of(50, 150)) {
      var connection = syntax("StreamConnection", offset - 30);
      connection.setRelationshipSource(syntax("Region", offset));
      connection.setRelationshipTarget(syntax("StreamJunction", offset + 20));
      var observable = new KimObservableImpl(); observable.setSemantics(connection);
      var model = new KimModelImpl(); model.getObservables().add(observable); document.getStatements().add(model);
    }
    long count = ontology.getOWLOntology().getAxiomCount();
    var notifications = new DocumentSemanticValidator(reasoner).validate(document);
    assertEquals(2, notifications.size());
    assertEquals(List.of(50,150), notifications.stream().map(n -> n.getLexicalContext().getOffsetInDocument()).toList());
    assertTrue(notifications.getFirst().getMessage().contains("earth:StreamJunction"));
    assertEquals("test", notifications.getFirst().getLexicalContext().getDocumentUrn());
    assertTrue(ontology.getConcept("Region").getNotifications().isEmpty());
    assertEquals(count, ontology.getOWLOntology().getAxiomCount());
    for (var statement : document.getStatements()) {
      var connection = (KimConceptImpl) ((KimModelImpl) statement).getObservables().getFirst().getSemantics();
      connection.setRelationshipSource(syntax("StreamJunction", 50));
      connection.resetDefinition();
    }
    assertTrue(new DocumentSemanticValidator(reasoner).validate(document).isEmpty());
  }

  private KimConceptImpl syntax(String name, int offset) {
    var ret = new KimConceptImpl(); ret.setName("earth:" + name);
    ret.setType(name.equals("Height") ? EnumSet.of(SemanticType.QUALITY, SemanticType.OBSERVABLE)
        : EnumSet.of(name.equals("StreamConnection") ? SemanticType.RELATIONSHIP : SemanticType.SUBJECT,
            SemanticType.COUNTABLE, SemanticType.OBSERVABLE));
    ret.setOffsetInDocument(offset); ret.setLength(name.length() + 6); return ret;
  }
}

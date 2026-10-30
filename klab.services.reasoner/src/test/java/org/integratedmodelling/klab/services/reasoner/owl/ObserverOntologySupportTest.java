package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.KlabStatement;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.model.*;

class ObserverOntologySupportTest {
  private static final String NAMESPACE = "private.models";

  private ConceptImpl concept(String urn, SemanticType... types) {
    var ret = new ConceptImpl(); ret.setUrn(urn); ret.setName(urn.substring(urn.indexOf(':') + 1));
    ret.setNamespace(urn.substring(0, urn.indexOf(':'))); ret.setReferenceName(ret.getName().toLowerCase());
    ret.setType(EnumSet.copyOf(List.of(types))); return ret;
  }

  private ObservableImpl absolute() {
    var ret = ObservableImpl.promote(concept("earth:Elevation", SemanticType.QUALITY,
        SemanticType.LENGTH, SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE), null);
    ret.setUrn("earth:Elevation in m optional"); ret.setOptional(true); ret.setDefaultValue(7);
    return ret;
  }

  private ObservableImpl perspective(ObservableImpl absolute, Concept observer) {
    var ret = new ObservableImpl(absolute); ret.setObserverSemantics(observer);
    ret.setUrn("earth:Elevation observed as " + observer.getUrn() + " in m optional");
    return ret;
  }

  private KimObservableImpl source(String observer) {
    var ret = new KimObservableImpl(); ret.setNamespace(NAMESPACE); ret.setProjectName("private-project");
    ret.setScope(KlabStatement.Scope.PRIVATE); ret.setOffsetInDocument(24); ret.setLength(63);
    if (observer != null) {
      var syntax = new KimConceptImpl(); syntax.setName(observer); syntax.setUrn(observer);
      ret.setObserverSemantics(syntax);
    }
    return ret;
  }

  private Ontology owner(OWLOntology ontology) {
    var ret = mock(Ontology.class); when(ret.getName()).thenReturn(NAMESPACE);
    when(ret.getOWLOntology()).thenReturn(ontology); return ret;
  }

  private OWL owl() { return mock(OWL.class); }

  private void add(OWLOntology ontology, String json) {
    var manager = ontology.getOWLOntologyManager(); var f = manager.getOWLDataFactory();
    manager.applyChange(new AddOntologyAnnotation(ontology,
        f.getOWLAnnotation(f.getOWLAnnotationProperty(ObserverOntologySupport.PROPERTY), f.getOWLLiteral(json))));
  }

  @Test void freshManagerRoundTripPreservesPerspectivesProvenanceAndAbsoluteWrapperWithoutBaseAxioms() throws Exception {
    var manager = OWLManager.createOWLOntologyManager();
    var shared = manager.createOntology(IRI.create("urn:earth"));
    var baseClass = manager.getOWLDataFactory().getOWLClass(IRI.create("urn:earth#Elevation"));
    manager.addAxiom(shared, manager.getOWLDataFactory().getOWLDeclarationAxiom(baseClass));
    var before = shared.getAxioms();
    var owned = manager.createOntology(IRI.create("urn:private-models"));
    var owner = owner(owned); var absolute = absolute();
    var x = concept("society:GeneralObserver", SemanticType.AGENT, SemanticType.ABSTRACT);
    x.setAbstract(true); // An abstract class constraint is not an abstract observer instantiation.
    var y = concept("society:OtherObserver", SemanticType.AGENT);
    var ox = perspective(absolute, x); var oy = perspective(absolute, y);
    ObserverOntologySupport.compile(owl(), owner, ox, source(x.getUrn()));
    ObserverOntologySupport.compile(owl(), owner, ox, source(x.getUrn()));
    ObserverOntologySupport.compile(owl(), owner, oy, source(y.getUrn()));
    assertEquals(2, owned.getAnnotations().size());
    assertTrue(owned.getAxioms().isEmpty(), "Perspective records must not restrict the base class");
    assertEquals(before, shared.getAxioms()); assertTrue(shared.getAnnotations().isEmpty());
    assertTrue(x.isAbstract());

    var bytes = new ByteArrayOutputStream(); manager.saveOntology(owned, new RDFXMLDocumentFormat(), bytes);
    var fresh = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(new ByteArrayInputStream(bytes.toByteArray()));
    var records = ObserverOntologySupport.records(List.of(fresh)); assertEquals(2, records.size());
    for (var record : records) {
      assertEquals(NAMESPACE, record.sourceNamespace()); assertEquals("private-project", record.sourceProject());
      assertEquals(KlabStatement.Scope.PRIVATE, record.sourceScope());
      assertEquals(24, record.offset()); assertEquals(63, record.length());
    }
    var observers = Map.<String, Concept>of(x.getUrn(), x, y.getUrn(), y);
    for (var expected : List.of(ox, oy)) {
      var restored = ObserverOntologySupport.restore(expected.getUrn(), List.of(fresh), urn -> {
        assertEquals(absolute.getUrn(), urn); return absolute;
      }, observers::get);
      assertEquals(expected.getUrn(), restored.getUrn());
      assertSame(absolute.asConcept(), restored.asConcept());
      assertEquals(expected.getObserverSemantics(), restored.getObserverSemantics());
      assertFalse(restored.isAbstract(), "Abstract observer constraints do not make the observable abstract");
      assertTrue(restored.isOptional()); assertEquals(7, restored.getDefaultValue());
    }
    assertNull(absolute.getObserverSemantics()); assertEquals("earth:Elevation in m optional", absolute.getUrn());
    assertNull(ObserverOntologySupport.restore("unrecorded", List.of(fresh), u -> fail("No lookup expected"), u -> fail("No lookup expected")));
  }

  @Test void repeatedSemanticIdentityMayRetainDistinctDeclarationProvenance() throws Exception {
    var ontology = OWLManager.createOWLOntologyManager().createOntology();
    var observer = concept("society:Observer", SemanticType.AGENT); var observable = perspective(absolute(), observer);
    var first = source(observer.getUrn()); var second = source(observer.getUrn()); second.setOffsetInDocument(101);
    ObserverOntologySupport.compile(owl(), owner(ontology), observable, first);
    ObserverOntologySupport.compile(owl(), owner(ontology), observable, second);
    assertEquals(2, ObserverOntologySupport.records(List.of(ontology)).size());
  }

  @Test void wrongKindMissingOwnershipAndNonAuthoredObserverFailBeforeMutation() throws Exception {
    var ontology = OWLManager.createOWLOntologyManager().createOntology(); var owner = owner(ontology);
    var wrong = concept("earth:Region", SemanticType.SUBJECT);
    assertThrows(KlabValidationException.class, () -> ObserverOntologySupport.compile(owl(), owner, perspective(absolute(), wrong), source(wrong.getUrn())));
    var observer = concept("society:Observer", SemanticType.AGENT); var observable = perspective(absolute(), observer);
    var missing = source(observer.getUrn()); missing.setNamespace(null);
    assertThrows(KlabValidationException.class, () -> ObserverOntologySupport.compile(owl(), owner, observable, missing));
    missing.setNamespace("earth");
    assertThrows(KlabValidationException.class, () -> ObserverOntologySupport.compile(owl(), owner, observable, missing));
    assertThrows(KlabValidationException.class, () -> ObserverOntologySupport.compile(owl(), owner, observable, source(null)));
    assertTrue(ontology.getAnnotations().isEmpty());
    ObserverOntologySupport.compile(owl(), owner, absolute(), source(null));
    assertTrue(ontology.getAnnotations().isEmpty());
  }

  @Test void restoringRejectsWrongCanonicalKindBaseAndAlreadyPerspectiveQualifiedResolverResults() throws Exception {
    var ontology = OWLManager.createOWLOntologyManager().createOntology(); var absolute = absolute();
    var observer = concept("society:Observer", SemanticType.AGENT); var observable = perspective(absolute, observer);
    ObserverOntologySupport.compile(owl(), owner(ontology), observable, source(observer.getUrn()));
    var wrong = concept(observer.getUrn(), SemanticType.SUBJECT);
    assertThrows(KlabValidationException.class, () -> ObserverOntologySupport.restore(observable.getUrn(), List.of(ontology), u -> absolute, u -> wrong));
    assertThrows(KlabValidationException.class, () -> ObserverOntologySupport.restore(observable.getUrn(), List.of(ontology), u -> observable, u -> observer));
    var wrongBase = new ObservableImpl(absolute); wrongBase.setSemantics(concept("earth:Wrong", SemanticType.QUALITY));
    assertThrows(KlabValidationException.class, () -> ObserverOntologySupport.restore(observable.getUrn(), List.of(ontology), u -> wrongBase, u -> observer));
    assertThrows(KlabValidationException.class, () -> ObserverOntologySupport.restore(observable.getUrn(), List.of(ontology), u -> absolute, u -> null));
  }

  @Test void contradictoryRecordsAndMalformedVersionsCannotSilentlyChooseOnePerspective() throws Exception {
    var ontology = OWLManager.createOWLOntologyManager().createOntology(); var json = new ObjectMapper();
    String full = "earth:Elevation observed as society:Observer in m";
    var one = new ObserverOntologySupport.ObservableRecord(1, full, "earth:Elevation in m", "earth:Elevation",
        "society:Observer", NAMESPACE, "project", KlabStatement.Scope.PRIVATE, 1, 2);
    // Same textual key with a contradictory boundary between class semantics and mediator.
    var two = new ObserverOntologySupport.ObservableRecord(1, full, "earth:Elevation", "earth:Elevation",
        "society:Observer in m", NAMESPACE, "project", KlabStatement.Scope.PRIVATE, 1, 2);
    add(ontology, json.writeValueAsString(one)); add(ontology, json.writeValueAsString(two));
    assertThrows(KlabValidationException.class, () -> ObserverOntologySupport.records(List.of(ontology)));
    var malformed = OWLManager.createOWLOntologyManager().createOntology();
    add(malformed, json.writeValueAsString(one).replace("\"version\":1", "\"version\":2"));
    assertThrows(KlabValidationException.class, () -> ObserverOntologySupport.records(List.of(malformed)));
    assertThrows(KlabValidationException.class, () -> new ObserverOntologySupport.ObservableRecord(1,
        "earth:Other observed as society:Observer", "earth:Elevation", "earth:Elevation", "society:Observer",
        NAMESPACE, null, null, 0, 0));
  }
}

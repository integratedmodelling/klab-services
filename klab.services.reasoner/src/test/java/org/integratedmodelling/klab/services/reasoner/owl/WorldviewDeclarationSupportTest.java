package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.collections.impl.PairImpl;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

class WorldviewDeclarationSupportTest {
  @Test void descriptiveKindsValidateTheirSourcesAndTargetsWithoutClassificationCardinality() {
    var owl = owl(); var ontology = owl.requireOntology("audit");
    declare(ontology, "Quality", SemanticType.QUALITY, SemanticType.QUANTIFIABLE);
    declare(ontology, "Boolean", SemanticType.QUALITY, SemanticType.PRESENCE);
    declare(ontology, "Order", SemanticType.PREDICATE, SemanticType.ORDERING);
    declare(ontology, "Predicate", SemanticType.PREDICATE);
    declare(ontology, "Process", SemanticType.PROCESS);
    for (var type : KimConceptStatement.DescriptionType.values()) {
      String source = type == KimConceptStatement.DescriptionType.CLASSIFIES ? "Predicate"
          : type == KimConceptStatement.DescriptionType.DISCRETIZES ? "Order" : "Quality";
      var statement = new KimConceptStatementImpl();
      statement.getObservablesDescribed().add(new PairImpl<>(syntax(
          type == KimConceptStatement.DescriptionType.MARKS ? "Boolean" : "Quality"), type));
      assertDoesNotThrow(() -> WorldviewDeclarationSupport.compile(owl, ontology, ontology.getConcept(source),
          statement, value -> owl.getConcept(value.getName())));
      assertThrows(KlabValidationException.class, () -> WorldviewDeclarationSupport.compile(owl, ontology,
          ontology.getConcept("Process"), statement, value -> owl.getConcept(value.getName())));
    }
    var discrete = new KimConceptStatementImpl();
    discrete.getObservablesDescribed().add(new PairImpl<>(syntax("Boolean"), KimConceptStatement.DescriptionType.DISCRETIZES));
    assertThrows(KlabValidationException.class, () -> WorldviewDeclarationSupport.compile(owl, ontology,
        ontology.getConcept("Order"), discrete, value -> owl.getConcept(value.getName())));
    var direct = new KimConceptStatementImpl(); direct.getQualitiesAffected().add(syntax("Quality"));
    assertThrows(KlabValidationException.class, () -> WorldviewDeclarationSupport.compile(owl, ontology,
        ontology.getConcept("Quality"), direct, value -> owl.getConcept(value.getName())));
  }
  @Test void marksRejectsNonBooleanQualityBeforeWritingRestrictions() {
    var owl = owl(); var ontology = owl.requireOntology("audit");
    declare(ontology, "Owner", SemanticType.OBSERVABLE, SemanticType.QUALITY);
    declare(ontology, "Numeric", SemanticType.OBSERVABLE, SemanticType.QUALITY);
    var statement = new KimConceptStatementImpl();
    statement.getObservablesDescribed().add(new PairImpl<>(syntax("Numeric"), KimConceptStatement.DescriptionType.MARKS));
    long before = ontology.getOWLOntology().getAxiomCount();
    assertThrows(KlabValidationException.class, () -> WorldviewDeclarationSupport.compile(owl, ontology,
        ontology.getConcept("Owner"), statement, value -> owl.getConcept(value.getName())));
    assertEquals(before, ontology.getOWLOntology().getAxiomCount());
  }
  private OWL owl() {
    var scope = (org.integratedmodelling.klab.api.scope.Scope) java.lang.reflect.Proxy.newProxyInstance(
        getClass().getClassLoader(), new Class<?>[]{org.integratedmodelling.klab.api.scope.Scope.class},
        (self, method, args) -> null);
    var owl = new OWL(scope); owl.manager = OWLManager.createOWLOntologyManager();
    var properties = new ArrayList<Axiom>();
    for (String name : List.of("affects", "creates", "requiresIdentity", "requiresRealm", "requiresExtent",
        "requiresAttribute", "impliesObservable", "emergesFrom", "describesQuality", "increasesWith",
        "decreasesWith", "marksQuality", "classifiesQuality", "discretizesQuality", "impliesSource",
        "impliesDestination", "appliesTo")) properties.add(Axiom.ObjectPropertyAssertion(name));
    owl.requireOntology("odo").define(properties);
    return owl;
  }
  private KimConcept syntax(String name) { var value = new KimConceptImpl(); value.setName("audit:" + name); return value; }
  private void declare(Ontology ontology, String name, SemanticType... types) {
    ontology.define(List.of(Axiom.ClassAssertion(name, EnumSet.copyOf(List.of(types)))));
  }
  private void assertRestriction(OWL owl, Ontology ontology, Concept owner, String property, Concept target) {
    var f = owl.manager.getOWLDataFactory();
    assertTrue(ontology.getOWLOntology().containsAxiom(f.getOWLSubClassOfAxiom(owl.getOWLClass(owner),
        f.getOWLObjectSomeValuesFrom(f.getOWLObjectProperty(IRI.create(owl.getProperty("odo:"+property).getURI())),
            owl.getOWLClass(target)))), property);
  }
  @Test void applicabilityDeclarationsMayNarrowButNotWidenAnInheritedDomain() {
    var owl=owl(); var ontology=owl.requireOntology("audit");
    for (String name : List.of("Parent", "Child", "Invalid")) declare(ontology,name,SemanticType.ATTRIBUTE,SemanticType.PREDICATE);
    for (String name : List.of("Tree", "Oak", "Rock")) declare(ontology,name,SemanticType.SUBJECT,SemanticType.COUNTABLE,SemanticType.OBSERVABLE);
    ontology.define(List.of(Axiom.SubClass("Parent","Child"), Axiom.SubClass("Parent","Invalid"), Axiom.SubClass("Tree","Oak")));
    var parent=new KimConceptStatementImpl(); parent.getAppliesTo().add(new ApplicableConceptImpl(null,syntax("Tree")));
    WorldviewDeclarationSupport.compile(owl,ontology,ontology.getConcept("Parent"),parent,value -> owl.getConcept(value.getName()));
    var child=new KimConceptStatementImpl(); child.getAppliesTo().add(new ApplicableConceptImpl(null,syntax("Oak")));
    WorldviewDeclarationSupport.compile(owl,ontology,ontology.getConcept("Child"),child,value -> owl.getConcept(value.getName()));
    assertRestriction(owl,ontology,ontology.getConcept("Child"),"appliesTo",ontology.getConcept("Oak"));
    var invalid=new KimConceptStatementImpl(); invalid.getAppliesTo().add(new ApplicableConceptImpl(null,syntax("Rock")));
    long before=ontology.getOWLOntology().getAxiomCount();
    assertThrows(KlabValidationException.class,() -> WorldviewDeclarationSupport.compile(owl,ontology,
        ontology.getConcept("Invalid"),invalid,value -> owl.getConcept(value.getName())));
    assertEquals(before,ontology.getOWLOntology().getAxiomCount());
  }
  @Test void clausesProduceDurableExistentialsAndApplicabilityUnionInDeclaringOntology() throws Exception {
    var owl = owl(); var ontology = owl.requireOntology("audit");
    declare(ontology,"Owner",SemanticType.OBSERVABLE,SemanticType.PROCESS);
    declare(ontology,"Quality",SemanticType.OBSERVABLE,SemanticType.QUALITY);
    declare(ontology,"BooleanQuality",SemanticType.OBSERVABLE,SemanticType.QUALITY,SemanticType.PRESENCE);
    declare(ontology,"Entity",SemanticType.OBSERVABLE,SemanticType.COUNTABLE,SemanticType.SUBJECT);
    for (var type : List.of(SemanticType.IDENTITY, SemanticType.REALM, SemanticType.EXTENT, SemanticType.ATTRIBUTE))
      declare(ontology,type.name(),type);
    var statement = new KimConceptStatementImpl();
    statement.getQualitiesAffected().add(syntax("Quality"));
    statement.getObservablesCreated().add(syntax("Entity"));
    statement.getRequiredIdentities().add(syntax("IDENTITY")); statement.getRequiredRealms().add(syntax("REALM"));
    statement.getRequiredExtents().add(syntax("EXTENT")); statement.getRequiredAttributes().add(syntax("ATTRIBUTE"));
    statement.getImpliedObservables().add(syntax("Quality")); statement.getEmergenceTriggers().add(syntax("Entity"));
    statement.getAppliesTo().add(new ApplicableConceptImpl(null, syntax("Entity")));
    statement.getAppliesTo().add(new ApplicableConceptImpl(null, syntax("Quality")));
    // Exercise the Resources -> Reasoner wire representation, including nested generic pairs.
    var mapper = org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    statement = (KimConceptStatementImpl) mapper.readValue(mapper.writeValueAsBytes(statement), KimConceptStatement.class);
    var owner=ontology.getConcept("Owner");
    WorldviewDeclarationSupport.compile(owl,ontology,owner,statement,value -> owl.getConcept(value.getName()));
    for (String property : List.of("affects","impliesObservable"))
      assertRestriction(owl,ontology,owner,property,ontology.getConcept("Quality"));
    assertRestriction(owl,ontology,owner,"creates",ontology.getConcept("Entity"));
    for (String kind : List.of("Identity","Realm","Extent","Attribute"))
      assertRestriction(owl,ontology,owner,"requires"+kind,ontology.getConcept(kind.toUpperCase()));
    var f=owl.manager.getOWLDataFactory();
    var union=f.getOWLObjectUnionOf(owl.getOWLClass(ontology.getConcept("Entity")),owl.getOWLClass(ontology.getConcept("Quality")));
    var axiom=f.getOWLSubClassOfAxiom(owl.getOWLClass(owner), f.getOWLObjectSomeValuesFrom(
        f.getOWLObjectProperty(IRI.create(owl.getProperty("odo:appliesTo").getURI())),union));
    assertTrue(ontology.getOWLOntology().containsAxiom(axiom));
    assertFalse(owl.getOntology("odo").getOWLOntology().containsAxiom(axiom));
    var bytes=new java.io.ByteArrayOutputStream(); owl.manager.saveOntology(ontology.getOWLOntology(),bytes);
    var reloadManager=OWLManager.createOWLOntologyManager();
    reloadManager.createOntology(owl.getOntology("odo").getOWLOntology().getAxioms(),
        owl.getOntology("odo").getOWLOntology().getOntologyID().getOntologyIRI().orElseThrow());
    var reloaded=reloadManager.loadOntologyFromOntologyDocument(new java.io.ByteArrayInputStream(bytes.toByteArray()));
    assertTrue(reloaded.containsAxiom(axiom));
  }
  @Test void linksSpecializeInheritedEndpointsAndDisjointPredicatesAreExcluded() {
    var owl=owl(); var ontology=owl.requireOntology("audit");
    for(String name:List.of("Connection","Specialized")) declare(ontology,name,SemanticType.RELATIONSHIP,SemanticType.OBSERVABLE);
    for(String name:List.of("Tree","Oak","Rock")) declare(ontology,name,SemanticType.COUNTABLE,SemanticType.SUBJECT,SemanticType.OBSERVABLE);
    ontology.define(List.of(Axiom.SubClass("Tree","Oak"), Axiom.SubClass("Connection","Specialized"),
        Axiom.DisjointClasses(new String[]{"audit:Tree","audit:Rock"})));
    var parent=new KimConceptStatementImpl(); parent.getSubjectsLinked().add(new ApplicableConceptImpl(syntax("Tree"),syntax("Rock")));
    WorldviewDeclarationSupport.compile(owl,ontology,ontology.getConcept("Connection"),parent,value -> owl.getConcept(value.getName()));
    var child=new KimConceptStatementImpl(); child.getSubjectsLinked().add(new ApplicableConceptImpl(syntax("Oak"),syntax("Rock")));
    WorldviewDeclarationSupport.compile(owl,ontology,ontology.getConcept("Specialized"),child,value -> owl.getConcept(value.getName()));
    assertRestriction(owl,ontology,ontology.getConcept("Specialized"),"impliesSource",ontology.getConcept("Oak"));
    child.getSubjectsLinked().clear(); child.getSubjectsLinked().add(new ApplicableConceptImpl(syntax("Rock"),syntax("Oak")));
    assertThrows(KlabValidationException.class,() -> WorldviewDeclarationSupport.compile(owl,ontology,
        ontology.getConcept("Specialized"),child,value -> owl.getConcept(value.getName())));
    var support=new OWLSemanticClauseSupport(owl);
    assertFalse(support.predicatesCompatible(List.of(ontology.getConcept("Oak"),ontology.getConcept("Rock"))));
    assertTrue(support.predicatesCompatible(List.of(ontology.getConcept("Tree"),ontology.getConcept("Oak"))));
  }
}

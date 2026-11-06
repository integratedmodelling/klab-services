package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.services.reasoner.*;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;

class ContextualRequestAdmissionTest {
  private final OWL owl;
  private final Ontology graph;
  ContextualRequestAdmissionTest() {
    owl = new OWL(mock(Scope.class)); owl.manager = OWLManager.createOWLOntologyManager();
    owl.requireOntology("odo").define(List.of(Axiom.ObjectPropertyAssertion("observationallyContains"),
        Axiom.ObjectPropertyAssertion("isInherentTo")));
    graph = owl.requireOntology("audit");
    for (var name : List.of("Region", "Subregion", "Stream", "Reach", "Basin", "Other"))
      graph.define(List.of(Axiom.ClassAssertion(name, EnumSet.of(SemanticType.SUBJECT, SemanticType.OBSERVABLE))));
    for (var name : List.of("Gradient", "ReachGradient", "FlowDirection", "Joint"))
      graph.define(List.of(Axiom.ClassAssertion(name, EnumSet.of(SemanticType.QUALITY, SemanticType.OBSERVABLE))));
    parent("Subregion", "Region"); parent("Reach", "Stream");
    bearer("Gradient", "Stream"); bearer("ReachGradient", "Reach");
    bearer("FlowDirection", "Basin"); bearer("Joint", "Stream"); bearer("Joint", "Basin");
  }
  private Concept c(String name) { return graph.getConcept(name); }
  private void parent(String child, String parent) {
    owl.manager.addAxiom(graph.getOWLOntology(), owl.manager.getOWLDataFactory().getOWLSubClassOfAxiom(
        owl.getOWLClass(c(child)), owl.getOWLClass(c(parent))));
  }
  private void bearer(String quality, String bearer) {
    var f = owl.manager.getOWLDataFactory();
    owl.manager.addAxiom(graph.getOWLOntology(), f.getOWLSubClassOfAxiom(owl.getOWLClass(c(quality)),
        f.getOWLObjectSomeValuesFrom(owl.getProperty("odo:isInherentTo").getOWLEntity().asOWLObjectProperty(),
            owl.getOWLClass(c(bearer)))));
  }
  private void contains(String container, String member) {
    var a = new KimConceptImpl(); a.setName(c(container).getUrn());
    var b = new KimConceptImpl(); b.setName(c(member).getUrn());
    var statement = new KimConceptStatementImpl();
    statement.getObservationalContainments().add(new KimConceptStatement.ObservationalContainment(a,b));
    owl.manager.addAxioms(graph.getOWLOntology(), ObservationalContainmentSupport.prepare(owl,
        c(container), statement, syntax -> owl.getConcept(syntax.getName())));
  }
  private boolean admits(String request, String context) {
    return ContextualRequestAdmission.admits(owl, c(request), c(context));
  }
  @Test void declaredInheritedAndNativeRequestsNeedNoDiscoveryOrRetyping() {
    contains("Region", "Stream"); contains("Region", "Basin");
    assertTrue(admits("Gradient", "Stream"));
    assertTrue(admits("Gradient", "Region"));
    assertTrue(admits("Gradient", "Subregion"));
    assertTrue(admits("ReachGradient", "Region"));
    assertTrue(admits("FlowDirection", "Region"));
    assertEquals(List.of("audit:Stream"), new ArrayList<>(new OWLSemanticClauseSupport(owl)
        .bearerConstraints(c("Gradient")).stream().map(b -> owl.getConceptFor(b.filler().asOWLClass()).getUrn()).toList()));
  }
  @Test void undeclaredReverseTransitiveAndNarrowerEndpointDoNotAdmit() {
    assertFalse(admits("Gradient", "Region"));
    contains("Stream", "Region"); assertFalse(admits("Gradient", "Region"));
    contains("Region", "Reach"); assertFalse(admits("Gradient", "Region"));
    contains("Other", "Region"); assertFalse(admits("ReachGradient", "Other"));
    assertFalse(admits("FlowDirection", "Region"));
  }
  @Test void separateMembersCannotSatisfyOneConjunctiveBearer() {
    contains("Region", "Stream"); contains("Region", "Basin");
    assertFalse(admits("Joint", "Region"));
  }
  @Test void booleanBoundsRequireOneNamedWitnessForTheCompleteConjunction() {
    var f = owl.manager.getOWLDataFactory();
    var property = owl.getProperty("odo:isInherentTo").getOWLEntity().asOWLObjectProperty();
    graph.define(List.of(Axiom.ClassAssertion("BooleanQuality",
        EnumSet.of(SemanticType.QUALITY, SemanticType.OBSERVABLE))));
    var either = f.getOWLObjectUnionOf(owl.getOWLClass(c("Stream")), owl.getOWLClass(c("Basin")));
    owl.manager.addAxiom(graph.getOWLOntology(), f.getOWLSubClassOfAxiom(
        owl.getOWLClass(c("BooleanQuality")), f.getOWLObjectSomeValuesFrom(property, either)));
    contains("Region", "Stream");
    assertTrue(admits("BooleanQuality", "Region"), "Stream proves the complete union");
    bearer("BooleanQuality", "Basin");
    assertFalse(admits("BooleanQuality", "Region"), "A union arm cannot waive another bound");
    contains("Region", "Basin");
    assertTrue(admits("BooleanQuality", "Region"), "Basin alone now proves both clauses");

    graph.define(List.of(Axiom.ClassAssertion("IntersectionQuality",
        EnumSet.of(SemanticType.QUALITY, SemanticType.OBSERVABLE))));
    var both = f.getOWLObjectIntersectionOf(owl.getOWLClass(c("Stream")), owl.getOWLClass(c("Reach")));
    owl.manager.addAxiom(graph.getOWLOntology(), f.getOWLSubClassOfAxiom(
        owl.getOWLClass(c("IntersectionQuality")), f.getOWLObjectSomeValuesFrom(property, both)));
    assertTrue(admits("IntersectionQuality", "Region"), "Reach specializes Stream and proves both");
    assertFalse(admits("IntersectionQuality", "Other"));
  }
  @Test void complementsAndPropertyFillersAreNotNativeWitnessCandidates() {
    var f = owl.manager.getOWLDataFactory();
    assertTrue(ContextualRequestAdmission.namedBearerCandidates(
        f.getOWLObjectComplementOf(owl.getOWLClass(c("Stream")))).toList().isEmpty());
    assertTrue(ContextualRequestAdmission.namedBearerCandidates(f.getOWLObjectSomeValuesFrom(
        owl.getProperty("odo:isInherentTo").getOWLEntity().asOWLObjectProperty(),
        owl.getOWLClass(c("Stream")))).toList().isEmpty());
  }
  @Test void unsatisfiableUnionArmCannotSupplyAVacuousNativeWitness() throws Exception {
    var f = owl.manager.getOWLDataFactory();
    graph.define(List.of(Axiom.ClassAssertion("EitherQuality",
        EnumSet.of(SemanticType.QUALITY, SemanticType.OBSERVABLE))));
    owl.manager.addAxiom(graph.getOWLOntology(), f.getOWLSubClassOfAxiom(
        owl.getOWLClass(c("EitherQuality")), f.getOWLObjectSomeValuesFrom(
            owl.getProperty("odo:isInherentTo").getOWLEntity().asOWLObjectProperty(),
            f.getOWLObjectUnionOf(owl.getOWLClass(c("Stream")), owl.getOWLClass(c("Basin"))))));
    owl.manager.addAxiom(graph.getOWLOntology(),
        f.getOWLSubClassOfAxiom(owl.getOWLClass(c("Stream")), f.getOWLNothing()));
    contains("Region", "Stream");
    var dl = new org.semanticweb.HermiT.Reasoner.ReasonerFactory()
        .createReasoner(graph.getOWLOntology());
    var field = OWL.class.getDeclaredField("reasoner"); field.setAccessible(true); field.set(owl, dl);
    try {
      assertTrue(owl.isSatisfiable(c("EitherQuality")), "Basin keeps the request satisfiable");
      assertFalse(admits("EitherQuality", "Region"), "An impossible Stream is no native witness");
      contains("Region", "Basin"); dl.flush();
      assertTrue(admits("EitherQuality", "Region"), "A separately declared live arm is sufficient");
    } finally { dl.dispose(); field.set(owl, null); }
  }
  @Test void processAdmissionAndUnchangedOtherActivityAndMissingContextPaths() {
    graph.define(List.of(Axiom.ClassAssertion("Transport",EnumSet.of(SemanticType.PROCESS,SemanticType.OBSERVABLE))));
    bearer("Transport","Basin");
    assertTrue(admits("Transport","Basin"));
    assertFalse(admits("Transport","Region"));
    contains("Region","Basin"); assertTrue(admits("Transport","Subregion"));
    assertTrue(ContextualRequestAdmission.admits(owl,c("Gradient"),null));
    assertTrue(admits("Stream","Other"));
    assertTrue(admits("Gradient","Reach"));
  }
  @Test void unmarkedTautologicalCarrierIsNotAnAuthoredPermission() {
    var f=owl.manager.getOWLDataFactory();
    owl.manager.addAxiom(graph.getOWLOntology(), f.getOWLSubClassOfAxiom(owl.getOWLClass(c("Region")),
        f.getOWLObjectMinCardinality(0, owl.getProperty("odo:observationallyContains").getOWLEntity()
            .asOWLObjectProperty(), owl.getOWLClass(c("Stream")))));
    assertFalse(admits("Gradient", "Region"));
  }
  @Test void importedMarkedCarrierMustStillHaveSupportedEndpointFamilies() {
    var f=owl.manager.getOWLDataFactory();
    var marker=f.getOWLAnnotation(f.getOWLAnnotationProperty(ObservationalContainmentSupport.DECLARATION),
        f.getOWLLiteral("ADDITIVE_DECLARATION"));
    // A raw imported carrier bypasses the source compiler: a quality cannot be its container.
    owl.manager.addAxiom(graph.getOWLOntology(), f.getOWLSubClassOfAxiom(owl.getOWLClass(c("FlowDirection")),
        f.getOWLObjectMinCardinality(0,owl.getProperty("odo:observationallyContains").getOWLEntity()
            .asOWLObjectProperty(),owl.getOWLClass(c("Stream"))),Set.of(marker)));
    assertFalse(admits("Gradient","FlowDirection"));
  }
  @Test void precomputedModelIsValidButContainmentDoesNotBridgeAComputationalDependency() {
    contains("Region", "Stream");
    var reasoner=mock(ReasonerService.class); when(reasoner.owl()).thenReturn(owl);
    when(reasoner.inherent(c("Gradient"))).thenReturn(c("Stream"));
    var syntax=new KimConceptImpl(); syntax.setName("audit:Gradient");
    syntax.setType(EnumSet.of(SemanticType.QUALITY,SemanticType.OBSERVABLE));
    var output=new KimObservableImpl(); output.setSemantics(syntax);
    when(reasoner.declareConcept(syntax)).thenReturn(c("Gradient"));
    var model=new KimModelImpl(); model.getObservables().add(output);
    var context=mock(org.integratedmodelling.klab.runtime.language.KimObservableVisitor.Context.class);
    doReturn(new KimNamespaceImpl()).when(context).getDocument();
    var validator=new org.integratedmodelling.klab.services.reasoner.internal.DocumentSemanticValidator(reasoner,true);
    assertTrue(validator.validateModel(model,context).isEmpty());
    assertTrue(admits("Gradient","Region"));
    // The dependency is a Region-native field, while Gradient remains Stream-native.
    graph.define(List.of(Axiom.ClassAssertion("Elevation",EnumSet.of(SemanticType.QUALITY,SemanticType.OBSERVABLE))));
    bearer("Elevation","Region");
    var dependencySyntax=new KimConceptImpl(); dependencySyntax.setName("audit:Elevation");
    dependencySyntax.setType(EnumSet.of(SemanticType.QUALITY,SemanticType.OBSERVABLE));
    var dependency=new KimObservableImpl(); dependency.setSemantics(dependencySyntax);
    model.getDependencies().add(dependency);
    when(reasoner.declareConcept(dependencySyntax)).thenReturn(c("Elevation"));
    when(reasoner.satisfiable(c("Elevation"))).thenReturn(true);
    var rejected=validator.validateModel(model,context);
    assertTrue(rejected.stream().anyMatch(n->n.getMessage().contains("MODEL_DEPENDENCY_UNRESOLVED")
        || n.getMessage().contains("MODEL_DEPENDENCY_INCOMPATIBLE")));
  }
  @Test void productionStrategyEntryAdmitsTierZeroAndRejectsBeforeMatchingWithoutContract() throws Exception {
    var service=mock(ReasonerService.class); when(service.owl()).thenReturn(owl);
    var matcher=mock(ObservationReasoner.class);
    var field=ReasonerService.class.getDeclaredField("observationReasoner"); field.setAccessible(true); field.set(service,matcher);
    var request=mock(Observation.class); var observable=mock(org.integratedmodelling.klab.api.knowledge.Observable.class);
    when(request.getObservable()).thenReturn(observable); when(observable.getSemantics()).thenReturn(c("Gradient"));
    when(observable.getUrn()).thenReturn("audit:Gradient");
    var context=mock(Observation.class); var contextObservable=mock(org.integratedmodelling.klab.api.knowledge.Observable.class);
    when(context.getObservable()).thenReturn(contextObservable); when(contextObservable.getSemantics()).thenReturn(c("Region"));
    var scope=mock(ContextScope.class); when(scope.getContextObservation()).thenReturn(context);
    when(service.computeObservationStrategies(request,scope)).thenCallRealMethod();
    assertTrue(service.computeObservationStrategies(request,scope).isEmpty()); verifyNoInteractions(matcher);
    contains("Region", "Stream");
    var tierZero=mock(ObservationStrategy.class); when(tierZero.getRank()).thenReturn(0);
    when(matcher.computeMatchingStrategies(request,scope,true)).thenReturn(List.of(tierZero));
    assertEquals(List.of(tierZero),service.computeObservationStrategies(request,scope));
    verify(matcher).computeMatchingStrategies(request,scope,true);
    verify(scope,never()).observation(any());
  }
}

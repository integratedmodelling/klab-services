package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.services.reasoner.*;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;

/** Actual OWL unary construction and operator decomposition; controlled lookup/capability boundary. */
class CollectiveReductionSemanticTest {
  @Test void realCountAndPresenceTypesUseTheirDifferentProductionContextualizations() {
    var scope=mock(ContextScope.class);var service=mock(ReasonerService.class);
    var owl=new OWL(mock(Scope.class));owl.manager=OWLManager.createOWLOntologyManager();
    owl.requireOntology("klab").define(List.of(Axiom.ObjectPropertyAssertion("describesObservable"),
        Axiom.AnnotationPropertyAssertion("referenceName"),Axiom.AnnotationPropertyAssertion("conceptDefinition"),
        Axiom.AnnotationPropertyAssertion("baseDeclaration"),Axiom.AnnotationPropertyAssertion("displayLabel")));
    owl.requireOntology("odo").define(List.of(Axiom.ClassAssertion("Numerosity",EnumSet.of(SemanticType.QUALITY,SemanticType.NUMEROSITY)),
        Axiom.ClassAssertion("Presence",EnumSet.of(SemanticType.QUALITY,SemanticType.PRESENCE))));
    var ontology=owl.requireOntology("test");ontology.define(List.of(Axiom.ClassAssertion("Tree",EnumSet.of(SemanticType.SUBJECT,SemanticType.COUNTABLE,SemanticType.OBSERVABLE)),
        Axiom.ClassAssertion("Region",EnumSet.of(SemanticType.SUBJECT,SemanticType.COUNTABLE,SemanticType.OBSERVABLE))));
    var tree=ontology.getConcept("Tree");((ConceptImpl)tree).setReferenceName("tree");
    var region=ontology.getConcept("Region");
    var context=new ObservationImpl();context.setId(21);context.setUrn("context:21");context.setObservable(ObservableImpl.promote(region,scope));
    context.setGeometry(Geometry.create("t0(1){tstart=0,tend=10,ttype=PHYSICAL}"));context.setEventTimestamps(List.of(0L));
    when(scope.getContextObservation()).thenReturn(context);when(scope.getObservation(21)).thenReturn(context);
    when(scope.getService(Reasoner.class)).thenReturn(service);
    var cap=mock(Reasoner.Capabilities.class);when(cap.getServiceId()).thenReturn("reasoner");when(cap.getKnowledgeRevision()).thenReturn(3L);when(service.capabilities(scope)).thenReturn(cap);
    when(service.describedType(any())).thenAnswer(call->owl.getRestrictedClasses(call.getArgument(0,Semantics.class).asConcept(),
        owl.getProperty("klab:describesObservable")).stream().findFirst().orElse(null));
    doCallRealMethod().when(service).splitOperators(any());
    var collective=ObservableImpl.promote(tree.collective(),scope);when(service.resolveObservable(collective.getUrn())).thenReturn(collective);
    var lowerer=new ObservationReasoner(service,ignored->null);
    for(var mode:CollectiveReductionBinding.Mode.values()) {
      var derived=mode==CollectiveReductionBinding.Mode.COUNT?owl.makeCount(tree):owl.makePresence(tree);
      var syntax=new KimConceptImpl();syntax.setType(derived.getType());
      var description=Contextualization.forSemantics(syntax); // Same mapping used by SemanticsBuilder.
      ((ConceptImpl)derived).setDescriptionType(description);
      assertEquals(mode==CollectiveReductionBinding.Mode.COUNT?Contextualization.QUANTIFICATION:Contextualization.VERIFICATION,description);
      var request=new ObservationImpl();request.setObservable(ObservableImpl.promote(derived,scope));request.setGeometry(context.getGeometry());
      var plans=lowerer.computeMatchingStrategies(request,scope,true);assertEquals(1,plans.size());
      assertEquals(mode,plans.getFirst().getCollectiveReduction().mode());assertEquals(collective.getUrn(),plans.getFirst().getCollectiveReduction().operand());
    }
  }
}

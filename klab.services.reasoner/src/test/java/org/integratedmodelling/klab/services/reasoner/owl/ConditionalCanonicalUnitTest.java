package org.integratedmodelling.klab.services.reasoner.owl;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.scope.Scope;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
class ConditionalCanonicalUnitTest {
  @Test void nearestExplicitUnitMustBeUnambiguouslyCanonicalUnity() {
    var owl=new OWL(mock(Scope.class));owl.manager=OWLManager.createOWLOntologyManager();
    var graph=owl.requireOntology("units");
    for(var name:List.of("Child","Left","Right"))
      graph.define(List.of(Axiom.ClassAssertion(name,EnumSet.of(SemanticType.QUALITY))));
    var child=graph.getConcept("Child");var left=graph.getConcept("Left");var right=graph.getConcept("Right");
    var f=owl.manager.getOWLDataFactory();
    for(var parent:List.of(left,right))owl.manager.addAxiom(graph.getOWLOntology(),
        f.getOWLSubClassOfAxiom(owl.getOWLClass(child),owl.getOWLClass(parent)));
    assertFalse(ConditionalModelContextSupport.explicitlyUnitless(owl,child));
    left.getMetadata().put("odo:unit","1");
    assertFalse(ConditionalModelContextSupport.explicitlyUnitless(owl,child),"Unknown branch cannot be assumed unitless");
    right.getMetadata().put("odo:unit","1");
    assertTrue(ConditionalModelContextSupport.explicitlyUnitless(owl,child));
    right.getMetadata().put("odo:unit","rad");
    assertFalse(ConditionalModelContextSupport.explicitlyUnitless(owl,child));
    child.getMetadata().put("odo:unit","1");
    assertTrue(ConditionalModelContextSupport.explicitlyUnitless(owl,child),"Nearest explicit contract specializes inherited metadata");
    var property=f.getOWLAnnotationProperty(org.semanticweb.owlapi.model.IRI.create("http://integratedmodelling.org/odo#unit"));
    owl.manager.addAxiom(graph.getOWLOntology(),f.getOWLAnnotationAssertionAxiom(property,
        owl.getOWLClass(child).getIRI(),f.getOWLLiteral("m")));
    assertFalse(ConditionalModelContextSupport.explicitlyUnitless(owl,child),"Conflicting source and imported annotations fail closed");
  }
}

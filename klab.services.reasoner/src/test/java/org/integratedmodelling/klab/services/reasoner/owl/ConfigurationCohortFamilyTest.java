package org.integratedmodelling.klab.services.reasoner.owl;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.junit.jupiter.api.Test;
/** Genuine baseSubstantialType execution; the relationship names are authored fixture classes. */
class ConfigurationCohortFamilyTest {
 @Test void authoredRelationshipSubclassesRemainDifferentCohortKeysEvenWithACommonParent()throws Exception {
  try(var fixture=new MonotoneRefinementTest.Fixture()) {
   for(var name:List.of("Connection","RoadConnection","RailConnection")) {
    fixture.declare(name,SemanticType.RELATIONSHIP,SemanticType.COUNTABLE,SemanticType.OBSERVABLE,SemanticType.STRUCTURAL,SemanticType.UNIDIRECTIONAL);
    fixture.register(fixture.leaf(name));
   }
   fixture.sub("RoadConnection","Connection");fixture.sub("RailConnection","Connection");fixture.ready();
   var road=fixture.reasoner.baseSubstantialType(fixture.concept("RoadConnection"),fixture.scope);
   var rail=fixture.reasoner.baseSubstantialType(fixture.concept("RailConnection"),fixture.scope);
   assertEquals(fixture.concept("RoadConnection").getUrn(),road.getUrn());assertEquals(fixture.concept("RailConnection").getUrn(),rail.getUrn());assertNotEquals(road.getUrn(),rail.getUrn());
  }
 }
 @Test void actualIntersectionDistinguishesOverlappingSiblingsFromProvenDisjointClasses()throws Exception {
  try(var fixture=new MonotoneRefinementTest.Fixture()) {
   for(var name:List.of("Left","Right","Excluded")){fixture.declare(name,SemanticType.SUBJECT,SemanticType.COUNTABLE,SemanticType.OBSERVABLE);fixture.register(fixture.leaf(name));}
   fixture.add(fixture.f.getOWLDisjointClassesAxiom(fixture.type("Left"),fixture.type("Excluded")));fixture.ready();
   assertFalse(fixture.reasoner.is(fixture.concept("Left"),fixture.concept("Right")));assertFalse(fixture.reasoner.is(fixture.concept("Right"),fixture.concept("Left")));
   var possible=fixture.reasoner.compose(List.of(fixture.concept("Left"),fixture.concept("Right")),org.integratedmodelling.klab.api.lang.LogicalConnector.INTERSECTION);
   assertNotNull(fixture.reasoner.resolveConcept(possible.getUrn()));assertTrue(fixture.reasoner.satisfiable(possible));
   var excluded=fixture.reasoner.compose(List.of(fixture.concept("Left"),fixture.concept("Excluded")),org.integratedmodelling.klab.api.lang.LogicalConnector.INTERSECTION);
   assertNotNull(fixture.reasoner.resolveConcept(excluded.getUrn()));assertFalse(fixture.reasoner.satisfiable(excluded));
  }
 }

}

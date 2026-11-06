package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.junit.jupiter.api.Test;

class QuantitativeContainmentSemanticTest {
  private void declare(ClassifierNativeAdmissionTest.Fixture t) {
    t.declare("Volume",SemanticType.QUALITY,SemanticType.VOLUME,SemanticType.EXTENSIVE,SemanticType.OBSERVABLE,SemanticType.QUANTIFIABLE);
    t.declare("Temperature",SemanticType.QUALITY,SemanticType.TEMPERATURE,SemanticType.INTENSIVE,SemanticType.OBSERVABLE);
    t.declare("Oxygen",SemanticType.IDENTITY,SemanticType.PREDICATE);
    t.declare("Air",SemanticType.IDENTITY,SemanticType.PREDICATE);
    t.owl.getOntology("odo").define(List.of(org.integratedmodelling.common.lang.Axiom.ClassAssertion(
        "ExtensivePhysicalProperty",EnumSet.of(SemanticType.QUALITY,SemanticType.EXTENSIVE))));
    t.add(t.f.getOWLSubClassOfAxiom(t.type("Volume"),
        t.owl.getOWLClass(t.owl.getConcept("odo:ExtensivePhysicalProperty"))));
    for(var name:List.of("Volume","Temperature","Oxygen","Air"))t.register(t.leaf(name));
  }
  private KimConceptImpl quantity(ClassifierNativeAdmissionTest.Fixture t,String property,String portion,String whole) {
    var c=t.leaf(property);c.setPortionIdentity(t.leaf(portion));c.setWholeIdentity(t.leaf(whole));return t.register(c);
  }
  @Test void declarationPreservesMeasurementAndOrderedAnnotationsWithoutTraitOrInherency() throws Exception {
    try(var t=new ClassifierNativeAdmissionTest.Fixture()) {
      declare(t);
      var syntax=quantity(t,"Volume","Oxygen","Air");
      var c=t.reasoner.declareConcept(syntax);
      assertNotNull(c);assertTrue(c.is(SemanticType.VOLUME));assertTrue(c.is(SemanticType.EXTENSIVE));
      assertFalse(c.is(SemanticType.RATIO));assertEquals(syntax.getUrn(),c.getUrn());
      assertEquals("audit:Oxygen",c.getMetadata().get(QuantitativeContainmentSupport.PORTION));
      assertEquals("audit:Air",c.getMetadata().get(QuantitativeContainmentSupport.WHOLE));
      assertNull(t.reasoner.inherent(c));assertTrue(t.reasoner.traits(c).isEmpty());
      var restored=new OWLMetadata(t.owl.getOWLClass(c),t.owl.getOntology(c.getNamespace()).getOWLOntology());
      assertEquals("audit:Oxygen",restored.get(QuantitativeContainmentSupport.PORTION));
      var reverse=t.reasoner.declareConcept(quantity(t,"Volume","Air","Oxygen"));
      assertNotEquals(c.getUrn(),reverse.getUrn());
      assertFalse(QuantitativeContainmentSupport.sameOperands(c,reverse));
      assertFalse(QuantitativeContainmentSupport.sameOperands(c,t.concept("Volume")));
      assertTrue(QuantitativeContainmentSupport.sameOperands(c,t.reasoner.declareConcept(syntax)));
    }
  }
  @Test void publicDeclarationRejectsInvalidManuallyConstructedOperandsAndIntensiveType() throws Exception {
    try(var t=new ClassifierNativeAdmissionTest.Fixture()) {
      declare(t);
      for(var c:List.of(quantity(t,"Temperature","Oxygen","Air"),quantity(t,"Volume","Region","Air"),quantity(t,"Volume","Oxygen","Region"))) {
        assertThrows(KlabValidationException.class,()->t.reasoner.declareConcept(c));
        assertThrows(KlabValidationException.class,()->t.reasoner.declareConcept(c),"Rejected syntax must not poison the concept registry");
      }
    }
  }

  @Test void odoAncestryAdmitsNewInheritedPropertyAndRejectsExtensiveFlagsWithoutProof() throws Exception {
    try(var t=new ClassifierNativeAdmissionTest.Fixture()) {
      declare(t);
      // A new property has no built-in quantity name or EXTENSIVE flag; ODO ancestry is decisive.
      t.declare("NewPhysicalProperty",SemanticType.QUALITY,SemanticType.OBSERVABLE,SemanticType.QUANTIFIABLE);
      t.add(t.f.getOWLSubClassOfAxiom(t.type("NewPhysicalProperty"),
          t.owl.getOWLClass(t.owl.getConcept("odo:ExtensivePhysicalProperty"))));
      t.declare("InheritedProperty",SemanticType.QUALITY,SemanticType.OBSERVABLE,SemanticType.QUANTIFIABLE);
      t.sub("InheritedProperty","NewPhysicalProperty");
      t.declare("FakeExtensive",SemanticType.QUALITY,SemanticType.OBSERVABLE,SemanticType.EXTENSIVE,SemanticType.MONEY);
      for(var name:List.of("NewPhysicalProperty","InheritedProperty","FakeExtensive"))t.register(t.leaf(name));
      for(var name:List.of("NewPhysicalProperty","InheritedProperty")) {
        var partial=t.reasoner.declareConcept(quantity(t,name,"Oxygen","Air"));
        assertNotNull(partial);assertEquals("audit:Oxygen",partial.getMetadata().get(QuantitativeContainmentSupport.PORTION));
      }
      var invalid=quantity(t,"FakeExtensive","Oxygen","Air");
      assertThrows(KlabValidationException.class,()->t.reasoner.declareConcept(invalid));
      assertThrows(KlabValidationException.class,()->t.reasoner.declareConcept(invalid));
    }
  }

  @Test void measurementPreservingSpecializationRetainsPairInOwlAndConflictsFailClosed() throws Exception {
    try(var t=new ClassifierNativeAdmissionTest.Fixture()) {
      declare(t);
      var syntax=quantity(t,"Volume","Oxygen","Air");
      var partial=t.reasoner.declareConcept(syntax);
      var specialized=t.owl.makeSubclass(partial,"audit:QualifiedPartial");
      assertTrue(QuantitativeContainmentSupport.sameOperands(partial,specialized));
      var restored=new OWLMetadata(t.owl.getOWLClass(specialized),t.owl.getOntology(specialized.getNamespace()).getOWLOntology());
      assertEquals("audit:Oxygen",restored.get(QuantitativeContainmentSupport.PORTION));
      var reversed=t.reasoner.declareConcept(quantity(t,"Volume","Air","Oxygen"));
      assertThrows(KlabValidationException.class,()->QuantitativeContainmentSupport.inherit(t.owl,specialized,reversed));
      var wrapper=new KimConceptImpl();wrapper.setObservable(syntax);
      wrapper.setType(new HashSet<>(syntax.getType()));wrapper.setInherent(t.leaf("Region"));t.register(wrapper);
      var qualified=org.integratedmodelling.klab.services.reasoner.internal.SemanticsBuilder.create(wrapper,t.reasoner,t.scope).buildConcept();
      assertTrue(QuantitativeContainmentSupport.sameOperands(partial,qualified));
      assertFalse(QuantitativeContainmentSupport.sameOperands(qualified,reversed));
    }
  }

  @Test void nestedOrNamedPartialCannotHaveItsOrderedPairReplaced() throws Exception {
    try(var t=new ClassifierNativeAdmissionTest.Fixture()) {
      declare(t);var inner=quantity(t,"Volume","Oxygen","Air");
      var outer=new KimConceptImpl();outer.setObservable(inner);outer.setType(new HashSet<>(inner.getType()));
      outer.setPortionIdentity(t.leaf("Air"));outer.setWholeIdentity(t.leaf("Oxygen"));t.register(outer);
      for(int pass=0;pass<2;pass++) {
        assertThrows(KlabValidationException.class,()->t.reasoner.declareConcept(outer));
        assertThrows(KlabValidationException.class,()->org.integratedmodelling.klab.services.reasoner.internal.SemanticsBuilder.create(outer,t.reasoner,t.scope).buildConcept());
      }
      var original=t.reasoner.declareConcept(inner);
      assertEquals("audit:Oxygen",original.getMetadata().get(QuantitativeContainmentSupport.PORTION));
      assertEquals("audit:Air",original.getMetadata().get(QuantitativeContainmentSupport.WHOLE));
    }
  }
}

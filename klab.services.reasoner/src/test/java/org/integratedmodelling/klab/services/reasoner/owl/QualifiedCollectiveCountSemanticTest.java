package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.services.reasoner.internal.SemanticsBuilder;
import org.integratedmodelling.klab.services.reasoner.internal.SemanticEnvironmentFactory;
import org.integratedmodelling.klab.services.reasoner.internal.CandidatePublicationCoordinator;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptStatementImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Actual public declaration of the canonical COUNT operand, including its parenthesis wrapper. */
class QualifiedCollectiveCountSemanticTest {
  @TempDir java.nio.file.Path directory;

  @Test void acceptedCompilerRoundTripsDerivedCountThroughCapturedInherentLookup() throws Exception {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
    var ontology=new KimOntologyImpl();ontology.setUrn("audit");ontology.setProjectName("count-test");
    ontology.setSourceCode(" ".repeat(256));
    for(var name:List.of("Region","Concrete")) {
      var declaration=new KimConceptStatementImpl();declaration.setUrn(name);declaration.setNamespace("audit");
      declaration.setOffsetInDocument(1);
      declaration.setType(name.equals("Region")
          ? EnumSet.of(SemanticType.SUBJECT,SemanticType.COUNTABLE,SemanticType.OBSERVABLE)
          : EnumSet.of(SemanticType.ATTRIBUTE,SemanticType.PREDICATE));
      ontology.getStatements().add(declaration);
    }
    var world=new WorldviewImpl();world.setUrn("audit");world.setWorldviewId("audit");world.setEmpty(false);
    world.setOntologies(new ArrayList<>(List.of(ontology)));
    var core=new TreeMap<String,byte[]>();
    for(var name:List.of("odo.owl","klab.owl","dc.owl")) {
      try(var input=getClass().getResourceAsStream("/knowledge/"+name)) {
        assertNotNull(input);core.put(name,input.readAllBytes());
      }
    }
    var factory=new SemanticEnvironmentFactory(directory,core);
    var compiled=factory.build(factory.capture(world),()->false);
    assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,compiled.logicalStatus(),compiled.diagnostics().toString());
    try(var state=compiled.state()) {
      var expression="count of (each audit:Concrete audit:Region)";
      var count=state.actor().resolveObservable(expression);
      assertTrue(count.is(SemanticType.NUMEROSITY));assertFalse(count.getSemantics().isCollective());
      assertEquals(expression,count.getSemantics().getUrn());
      assertNull(state.actor().inherent(count));
      assertNotNull(state.actor().resolveConcept(count.getSemantics().getUrn()));
      var operand=state.actor().describedType(count);
      assertNotNull(operand);assertTrue(state.actor().hasTrait(operand,state.actor().resolveConcept("audit:Concrete")));
    }
  }

  @Test void unaryBuilderRetainsWholeQualifiedOperandAndSeparatesResultArity() throws Exception {
    try (var t=new ClassifierNativeAdmissionTest.Fixture()) {
      t.declare("Role",SemanticType.ROLE,SemanticType.PREDICATE);
      var qualified=t.leaf("Region");qualified.getTraits().add(t.leaf("Concrete"));
      qualified.getTraits().add(t.leaf("RootA"));qualified.getRoles().add(t.leaf("Role"));
      qualified.setInherent(t.leaf("Other"));qualified.setCollective(true);t.register(qualified);
      var builder=SemanticsBuilder.create(qualified,t.reasoner,t.scope);
      builder.as(UnarySemanticOperator.COUNT);
      var field=SemanticsBuilder.class.getDeclaredField("syntax");field.setAccessible(true);
      var result=(KimConceptImpl)field.get(builder);var operand=result.getObservable();
      assertTrue(operand.isCollective());assertFalse(result.isCollective());
      assertEquals(List.of("audit:Concrete","audit:RootA"),operand.getTraits().stream().map(c->c.getUrn()).toList());
      assertEquals(List.of("audit:Role"),operand.getRoles().stream().map(c->c.getUrn()).toList());
      assertEquals("audit:Other",operand.getInherent().getUrn());
      assertTrue(result.getTraits().isEmpty());assertTrue(result.getRoles().isEmpty());
      assertNull(result.getInherent());assertEquals(1,qualified.getRoles().size(),"Source syntax remains caller-owned");
      builder.as(UnarySemanticOperator.RATE);
      var rate=(KimConceptImpl)field.get(builder);
      assertEquals(UnarySemanticOperator.RATE,rate.getSemanticModifier());
      assertEquals(UnarySemanticOperator.COUNT,rate.getObservable().getSemanticModifier());
      assertEquals(qualified.getUrn(),rate.getObservable().getObservable().getUrn());
    }
  }

  @Test void countOfQualifiedCollectiveKeepsPredicateOnOperand() throws Exception {
    try (var t=new ClassifierNativeAdmissionTest.Fixture()) {
      t.owl.requireOntology("odo","http://integratedmodelling.org").define(List.of(
          Axiom.ClassAssertion("Numerosity",EnumSet.of(SemanticType.QUALITY,SemanticType.NUMEROSITY,
              SemanticType.OBSERVABLE,SemanticType.QUANTIFIABLE))));
      t.owl.requireOntology("klab").define(List.of(
          Axiom.ObjectPropertyAssertion("describesObservable"),
          Axiom.AnnotationPropertyAssertion("referenceName"),
          Axiom.AnnotationPropertyAssertion("conceptDefinition"),
          Axiom.AnnotationPropertyAssertion("baseDeclaration"),
          Axiom.AnnotationPropertyAssertion("displayLabel")));
      t.concept("Region").setReferenceName("region");
      var qualified=t.leaf("Region");qualified.getTraits().add(t.leaf("Concrete"));
      qualified.setCollective(true);t.register(qualified);
      var parentheses=new KimConceptImpl();parentheses.setObservable(qualified);
      parentheses.setType(new HashSet<>(qualified.getType()));parentheses.setCollective(true);
      t.register(parentheses);
      var count=new KimConceptImpl();count.setObservable(parentheses);
      count.setSemanticModifier(UnarySemanticOperator.COUNT);
      count.setType(UnarySemanticOperator.COUNT.apply(parentheses.getType()));t.register(count);
      // Resolving the standalone operand URN reparses its unparenthesized head. The parser's
      // transparent parenthesis node belongs to COUNT's syntax, not this standalone lookup.
      t.syntax.put(qualified.getUrn(),qualified);
      assertEquals("count of (each audit:Concrete audit:Region)",count.getUrn());
      var built=t.reasoner.declareConcept(count);
      assertFalse(built.is(SemanticType.NOTHING));
      assertTrue(built.is(SemanticType.NUMEROSITY));assertFalse(built.isCollective());
      assertTrue(t.reasoner.traits(built).isEmpty(),"Operand predicates must not qualify numerosity");
      var operand=t.reasoner.describedType(built);
      assertNotNull(operand);assertTrue(t.reasoner.hasTrait(operand,t.concept("Concrete")));
      assertTrue(new OWLSemanticClauseSupport(t.owl).applicableTo(t.concept("Concrete"),operand));

      var invalidOuter=(KimConceptImpl)KimConceptImpl.copyValue(count);
      invalidOuter.getTraits().add(t.register(t.leaf("Concrete")));
      invalidOuter.resetDefinition();t.register(invalidOuter);
      var failure=assertThrows(org.integratedmodelling.klab.api.exceptions.KlabValidationException.class,
          ()->t.reasoner.declareConcept(invalidOuter));
      assertTrue(failure.getMessage().contains("outside its applies to domain"),
          "An explicitly invalid predicate on the resulting quality must still be rejected");
    }
  }
}

package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.KimConsequenceRule;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptStatementImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.api.services.reasoner.objects.ConcretizationRequest;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.junit.jupiter.api.Test;

class ContextualInferenceTest {
  @Test
  void contextualRoleSelectionPrefersTheMostSpecificAndReportsAmbiguity() throws Exception {
    try (var t = new ImpliedRolesTest.Fixture()) {
      ClassifierNativeAdmissionTest.field(t.reasoner, ReasonerService.class, "worldview", null);
      t.restriction("Region", "hasRole", t.type("FirstRole"));
      t.restriction("FirstRole", "impliesRole", t.type("SpecialRole"));
      t.restriction("SpecialRole", "impliesRole", t.type("FirstRole"));
      t.sub("SpecialRole", "FirstRole");
      t.restriction("SpecialRole", "appliesTo", t.type("Region"));
      var region = t.concept("Region");
      var first = t.concept("FirstRole");
      var special = t.concept("SpecialRole");
      assertEquals(Set.of(first, special), Set.copyOf(t.reasoner.rolesFor(region, null)));
      assertEquals(special, t.reasoner.impliedRole(first, region));
      assertEquals(List.of(), t.reasoner.rolesFor(t.concept("Other"), first));
      assertNull(t.reasoner.impliedRole(first, t.concept("Other")));

      t.sub("SecondRole", "FirstRole");
      t.restriction("Region", "hasRole", t.type("SecondRole"));
      assertThrows(IllegalArgumentException.class, () -> t.reasoner.impliedRole(first, region));
    }
  }

  @Test
  void authoredRoleImplicationRequiresItsRecipientAndWithinBounds() throws Exception {
    try (var t = new ImpliedRolesTest.Fixture()) {
      t.declare("Configuration", SemanticType.CONFIGURATION, SemanticType.OBSERVABLE);
      var configuration = t.concept("Configuration");
      var role = t.concept("FirstRole");
      var item = "audit:FirstRole for audit:Region within audit:Region";
      var source = "ontology audit in domain root version 1.0; configuration Configuration implies "
          + item + ";";
      var statement = new KimConceptStatementImpl();
      statement.setUrn("Configuration");
      statement.setNamespace("audit");
      statement.setType(EnumSet.of(SemanticType.CONFIGURATION, SemanticType.OBSERVABLE));
      var ontology = new KimOntologyImpl();
      ontology.setUrn("audit");
      ontology.setSourceCode(source);
      ontology.getStatements().add(statement);
      var rule = new KimConsequenceRule(
          KimConsequenceRule.Kind.IMPLIES,
          t.register(t.leaf("FirstRole")),
          KimConsequenceRule.Recipient.implicit(),
          t.register(t.leaf("Region")),
          t.register(t.leaf("Region")),
          new KimConceptStatement.DeclarationClause("impliesClause", item, source.indexOf(item), item.length()));
      statement.getConsequenceRules().add(rule);
      var worldview = new WorldviewImpl();
      worldview.setUrn("audit");
      worldview.setWorldviewId("audit");
      worldview.setOntologies(List.of(ontology));
      var index = ConsequenceRuleIndex.capture(worldview, t.owl::getConcept, operand -> t.owl.getConcept(operand.getUrn()));
      ClassifierNativeAdmissionTest.field(t.reasoner, ReasonerService.class, "worldview", worldview);
      ClassifierNativeAdmissionTest.field(t.reasoner, ReasonerService.class, "consequenceRuleIndex", index);

      assertEquals(List.of(role), t.reasoner.rolesFor(t.concept("Region"), configuration));
      assertEquals(List.of(), t.reasoner.rolesFor(t.concept("Other"), configuration));
      assertEquals(List.of(), t.reasoner.rolesFor(t.concept("Region"), null));
    }
  }

  @Test
  void concretizationIgnoresUnrelatedInputsAndRejectsCompetingSpecializations() throws Exception {
    try (var t = new ClassifierNativeAdmissionTest.Fixture()) {
      var generic = t.concept("Category");
      var concrete = t.concept("Concrete");
      var unrelated = t.concept("Other");
      assertEquals(concrete, t.reasoner.concretize(generic, List.<Concept>of(unrelated, concrete, concrete)));
      assertSame(generic, t.reasoner.concretize(generic, List.<Concept>of(unrelated)));

      t.declare("Alternate", SemanticType.ATTRIBUTE, SemanticType.PREDICATE);
      t.sub("Alternate", "Category");
      assertThrows(IllegalArgumentException.class,
          () -> t.reasoner.concretize(generic, List.<Concept>of(concrete, t.concept("Alternate"))));
    }
  }

  @Test
  void observableConcretizationPreservesOtherTokensAndTracksTheBinding() throws Exception {
    try (var t = new ClassifierNativeAdmissionTest.Fixture()) {
      var generic = t.concept("Category");
      var concrete = t.concept("Concrete");
      var pattern = ObservableImpl.promote(generic, t.scope);
      pattern.setUrn("audit:Category audit:CategoryOther = \"audit:Category\"");
      pattern.setStatedName("category");
      pattern.setGeneric(true);
      pattern.getGenericComponents().add(generic);
      var declaration = "audit:Concrete audit:CategoryOther = \"audit:Category\"";
      var resolved = ObservableImpl.promote(concrete, t.scope);
      resolved.setUrn(declaration);
      doReturn(resolved).when(t.reasoner).resolveObservable(declaration);

      var mapper = JacksonConfiguration.newObjectMapper();
      var request = mapper.readValue(
          mapper.writeValueAsString(new ConcretizationRequest(pattern, List.of(concrete))),
          ConcretizationRequest.class);
      var transported = assertInstanceOf(ObservableImpl.class, request.pattern());
      var result = t.reasoner.concretize(transported, request.concreteConcepts());
      assertEquals(declaration, result.getUrn());
      assertEquals("category", result.getStatedName());
      assertFalse(result.isGeneric());
      assertTrue(result.getGenericComponents().isEmpty());
      assertEquals(generic, result.getSpecializedComponents().iterator().next().getFirst());
      assertEquals(concrete, result.getSpecializedComponents().iterator().next().getSecond());
      assertTrue(resolved.getSpecializedComponents().isEmpty());
    }
  }
}

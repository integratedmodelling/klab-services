package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalMatchers.aryEq;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.reasoner.internal.AcceptedSemanticRuntime;
import org.junit.jupiter.api.Test;

class ImpliedRolesTest {
  static class Fixture extends ClassifierNativeAdmissionTest.Fixture {
    Fixture() throws Exception {
      owl.requireOntology("odo", "http://integratedmodelling.org").define(List.of(
          Axiom.ObjectPropertyAssertion("impliesRole"),
          Axiom.ObjectPropertyAssertion("impliesPredicate"),
          Axiom.ObjectPropertyAssertion("impliesSource"),
          Axiom.ObjectPropertyAssertion("impliesDestination")));
      for (var name : List.of("FirstRole", "SecondRole", "ThirdRole", "SpecialRole", "EmptyRole"))
        declare(name, SemanticType.ROLE, SemanticType.PREDICATE);
      declare("Link", SemanticType.RELATIONSHIP, SemanticType.OBSERVABLE);
    }

    Set<String> implied(String role, boolean endpoints) {
      return reasoner.impliedRoles(concept(role), endpoints).stream()
          .map(Concept::getUrn).collect(Collectors.toSet());
    }
  }

  @Test
  void rolesWithoutImplicationsAndNonRolesReturnAnEmptyCollection() throws Exception {
    try (var t = new Fixture()) {
      assertEquals(Set.of(), t.implied("EmptyRole", false));
      assertEquals(Set.of(), t.implied("Region", false));
      assertThrows(IllegalArgumentException.class, () -> t.reasoner.impliedRoles(null, false));
    }
  }

  @Test
  void inheritedLegacyAndCurrentImplicationsFormACycleSafeRoleOnlyClosure() throws Exception {
    try (var t = new Fixture()) {
      t.restriction("FirstRole", "impliesRole", t.type("SecondRole"));
      t.restriction("SecondRole", "impliesPredicate", t.type("ThirdRole"));
      t.restriction("ThirdRole", "impliesRole", t.type("FirstRole"));
      t.restriction("FirstRole", "impliesPredicate", t.type("Concrete"));
      t.sub("SpecialRole", "FirstRole");
      assertEquals(Set.of("audit:SecondRole", "audit:ThirdRole"), t.implied("FirstRole", false));
      assertEquals(Set.of("audit:FirstRole", "audit:SecondRole", "audit:ThirdRole"),
          t.implied("SpecialRole", false));
      assertFalse(t.reasoner.hasRole(t.concept("Region"), t.concept("ThirdRole")),
          "Reading implication targets must not attribute roles to a bearer");
    }
  }

  @Test
  void relationshipEndpointsAreIncludedOnlyWhenRequestedAndApplicable() throws Exception {
    try (var t = new Fixture()) {
      t.restriction("FirstRole", "impliesRole", t.type("SecondRole"));
      t.restriction("SecondRole", "appliesTo", t.type("Link"));
      t.restriction("SecondRole", "impliesSource", t.type("Region"));
      t.restriction("SecondRole", "impliesDestination", t.type("Other"));
      t.restriction("EmptyRole", "impliesSource", t.type("Region"));
      assertEquals(Set.of("audit:SecondRole"), t.implied("FirstRole", false));
      assertEquals(Set.of("audit:SecondRole", "audit:Region", "audit:Other"),
          t.implied("FirstRole", true));
      assertEquals(Set.of(), t.implied("EmptyRole", true));
    }
  }

  @Test
  void publicServiceStillDispatchesThroughTheAcceptedSemanticGeneration() throws Exception {
    var reasoner = mock(ReasonerService.class, CALLS_REAL_METHODS);
    var runtime = mock(AcceptedSemanticRuntime.class);
    ClassifierNativeAdmissionTest.field(reasoner, ReasonerService.class, "candidateRuntime", runtime);
    var role = mock(Concept.class);
    var expected = List.of(mock(Concept.class));
    when(runtime.invoke(eq("impliedRoles"), any(Class[].class), any(Object[].class)))
        .thenReturn(expected);
    assertSame(expected, reasoner.impliedRoles(role, false));
    verify(runtime).invoke(eq("impliedRoles"),
        aryEq(new Class<?>[] {Concept.class, boolean.class}), aryEq(new Object[] {role, false}));
  }
}

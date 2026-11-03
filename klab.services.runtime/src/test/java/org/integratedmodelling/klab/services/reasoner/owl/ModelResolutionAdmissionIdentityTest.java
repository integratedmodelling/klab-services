package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.junit.jupiter.api.Test;

/** Diagnose existing effective-abstraction rules, not permission to weaken them or edit inputs. */
class ModelResolutionAdmissionIdentityTest {
  private SubmissionNativeFixture fixture() throws Exception {
    var f = new SubmissionNativeFixture();
    f.declare("Areal", SemanticType.IDENTITY, SemanticType.PREDICATE);
    f.declare("Volumetric", SemanticType.IDENTITY, SemanticType.PREDICATE);
    f.require("Input", f.f.getOWLObjectUnionOf(f.type("Areal"), f.type("Volumetric")));
    return f;
  }

  @Test void concreteDeclarationMayHaveAnAbstractUnqualifiedDependentExpression() throws Exception {
    try (var f = fixture()) {
      var declaration = f.concept("Input");
      assertFalse(declaration.isAbstract());
      var expression = new IdentityRequirementSupport(f.owl).project(declaration);
      assertTrue(expression.isAbstract());
      assertTrue(expression.is(SemanticType.ABSTRACT));
      assertFalse(declaration.isAbstract(), "Query projection must not relabel the declaration");
      assertFalse(declaration.is(SemanticType.ABSTRACT));
    }
  }

  @Test void explicitAdmittedBearerDischargesRequirementWithoutChangingTheDeclaration() throws Exception {
    try (var f = fixture()) {
      var syntax = f.named("Input"); syntax.setInherent(f.bearer("Areal"));
      var expression = new IdentityRequirementSupport(f.owl).project(f.concept("Input"), syntax);
      assertFalse(expression.isAbstract()); assertFalse(expression.is(SemanticType.ABSTRACT));
      assertFalse(f.concept("Input").isAbstract());
    }
  }

  @Test void aPlainBearerIsNotInventedAsIdentityEvidence() throws Exception {
    try (var f = fixture()) {
      var syntax = f.named("Input"); syntax.setInherent(f.named("Region"));
      assertTrue(new IdentityRequirementSupport(f.owl).project(f.concept("Input"), syntax).isAbstract());
      assertFalse(f.concept("Input").isAbstract());
    }
  }
}

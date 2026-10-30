package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl;
import org.junit.jupiter.api.Test;

/** A bound concrete-family provider is distinct from an ANY classification request. */
class ConcreteRoleClassifierAdmissionTest {
  @Test void boundConcreteRoleProviderIsAdmittedButAnySourceOutputRemainsUnbound() throws Exception {
    try(var t=new ClassifierNativeAdmissionTest.Fixture()) {
      t.declare("Reference",SemanticType.ROLE,SemanticType.PREDICATE);
      t.concept("Reference").getMetadata().put(
          org.integratedmodelling.klab.services.reasoner.internal.CoreOntology.NS.BASE_DECLARATION,"true");
      t.register(t.leaf("Reference"));
      var members=t.leaf("Region");members.setCollective(true);t.register(members);
      var predicate=t.leaf("Reference");predicate.setInherent(members);t.register(predicate);
      var primary=new KimObservableImpl();primary.setNamespace("models");primary.setSemantics(predicate);
      assertEquals(Contextualization.CLASSIFICATION,Contextualization.forSemantics(predicate));
      var admitted=t.validate(primary,List.of(),true);
      assertTrue(admitted.valid(),admitted.getNotifications().toString());

      var any=(org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl)
          org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl.copyValue(predicate);
      any.getType().add(SemanticType.ANY);any.resetDefinition();t.register(any);
      var unresolved=new KimObservableImpl();unresolved.setNamespace("models");unresolved.setSemantics(any);
      var rejected=t.validate(unresolved,List.of(),true);
      assertFalse(rejected.valid(),"ANY source output still needs binding before executable admission");
      assertTrue(rejected.getNotifications().stream().anyMatch(n->n.getMessage().contains("MODEL_OUTPUT_UNRESOLVED")),
          rejected.getNotifications().toString());
    }
  }
}

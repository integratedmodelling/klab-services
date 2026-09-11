package org.integratedmodelling.common.knowledge;

import static org.junit.jupiter.api.Assertions.*;
import java.util.EnumSet;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.junit.jupiter.api.Test;

class ClassificationContractTest {
  @Test void abstractionAndInherencyHaveIndependentRoles() {
    for (boolean collective : new boolean[]{false, true}) {
      var subject = new KimConceptImpl();
      subject.setType(EnumSet.of(SemanticType.SUBJECT)); subject.setCollective(collective);
      var predicate = new KimConceptImpl(); predicate.setInherent(subject);
      predicate.setType(EnumSet.of(SemanticType.PREDICATE, SemanticType.ATTRIBUTE, SemanticType.ABSTRACT));
      assertEquals(Contextualization.CLASSIFICATION, Contextualization.forSemantics(predicate));
      predicate.getType().remove(SemanticType.ABSTRACT);
      assertEquals(Contextualization.CHARACTERIZATION, Contextualization.forSemantics(predicate));
      subject.setType(EnumSet.of(SemanticType.QUALITY));
      assertEquals(Contextualization.TRANSFORMATION, Contextualization.forSemantics(predicate));
      predicate.setInherent(null);
      assertEquals(Contextualization.VOID, Contextualization.forSemantics(predicate));
    }
  }
  @Test void activityTypesAndEffectsMatchEveryExecutableContextualization() throws Exception {
    for (var activity : Contextualization.values()) {
      if (activity == Contextualization.VOID) continue;
      var type = org.integratedmodelling.klab.api.provenance.Activity.Type.forContextualization(activity);
      assertTrue(type.isContextualization());
      assertEquals(activity, type.getContextualization());
      assertEquals(activity.name(), type.name());
      var mapper = org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
      var bean = org.integratedmodelling.klab.api.provenance.Activity.of(type);
      var json = mapper.writerFor(org.integratedmodelling.klab.api.provenance.Activity.class).writeValueAsString(bean);
      assertEquals(type.name(), mapper.readTree(json).get("type").asText());
      assertEquals(type, mapper.readValue(json, org.integratedmodelling.klab.api.provenance.Activity.class).getType());
      var effect = GraphModel.Relationship.forContextualization(activity);
      assertTrue(GraphModel.Relationship.CONTEXTUALIZATION_EFFECTS.contains(effect));
      assertEquals(GraphModel.Relationship.Direction.OUTGOING, effect.direction());
    }
    assertThrows(IllegalArgumentException.class,
        () -> GraphModel.Relationship.forContextualization(Contextualization.VOID));
    assertThrows(IllegalArgumentException.class,
        () -> org.integratedmodelling.klab.api.provenance.Activity.Type.forContextualization(null));
    assertFalse(org.integratedmodelling.klab.api.provenance.Activity.Type.RESOLUTION.isContextualization());
    assertEquals(GraphModel.Relationship.CLASSIFIED,
        GraphModel.Relationship.forContextualization(Contextualization.CLASSIFICATION));
    assertTrue(Contextualization.CLASSIFICATION.modifiesExistingObservations());
    assertTrue(Contextualization.CHARACTERIZATION.modifiesExistingObservations());
    assertFalse(Contextualization.TRANSFORMATION.modifiesExistingObservations());
  }
}

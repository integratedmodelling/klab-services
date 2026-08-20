package org.integratedmodelling.klab.modeler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimObservationStrategiesImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KlabDocumentImpl;
import org.integratedmodelling.klab.modeler.model.NavigableKActorsBehavior;
import org.junit.jupiter.api.Test;

class ModelerDocumentUpdateTest {

  @Test
  void everyBehaviorTypeUpdatesWithoutRequiringConcreteRetrievedDocument() {
    for (var type : KActorsBehavior.Type.values()) {
      var remote = new KActorsBehaviorImpl();
      remote.setUrn("old.behavior");
      remote.setProjectName("stale.project");
      remote.setBehaviorType(type);
      var navigable = new NavigableKActorsBehavior(remote, null);

      var payload =
          ModelerImpl.documentUpdatePayload(
              navigable, "selected.project", "old.behavior", "updated source\n");

      var update = assertInstanceOf(KActorsBehaviorImpl.class, payload);
      assertNotSame(remote, update);
      assertEquals("selected.project", update.getProjectName());
      assertEquals("old.behavior", update.getUrn());
      assertEquals(type, update.getBehaviorType());
      assertEquals("updated source\n", update.getSourceCode());
    }
  }

  @Test
  void parsedKimDocumentsCarryEditedSourceAndSelectedProjectIntoSubmit() {
    for (KlabDocumentImpl<?> parsed :
        List.of(
            new KimNamespaceImpl(),
            new KimOntologyImpl(),
            new KimObservationStrategiesImpl())) {
      parsed.setUrn(null);

      var payload =
          ModelerImpl.documentUpdatePayload(
              parsed, "selected.project", "test.document", "updated source\n");

      assertSame(parsed, payload);
      assertEquals("test.document", parsed.getUrn());
      assertEquals("selected.project", parsed.getProjectName());
      assertEquals("updated source\n", parsed.getSourceCode());
    }
  }
}

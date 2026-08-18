package org.integratedmodelling.klab.modeler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl;
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
}

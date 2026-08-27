package org.integratedmodelling.klab.services.resources.storage;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl;
import org.junit.jupiter.api.Test;

class WorkspaceManagerSemanticValidationTest {

  @Test
  void attachesDefaultValidationNotificationsBeforeReturningTheBean() {
    var concept = new KimConceptImpl();
    concept.setName("test:Uncountable");
    concept.setType(EnumSet.of(SemanticType.QUALITY));
    concept.setFundamentalType(SemanticType.QUALITY);
    concept.setCollective(true);

    var namespace = new KimNamespaceImpl();
    namespace.setUrn("test");
    namespace.getStatements().add(concept);
    var resolverCalled = new AtomicBoolean();

    var returned =
        WorkspaceManager.validateSemanticAsset(
            namespace,
            (urn, knowledgeClass, context) -> {
              resolverCalled.set(true);
              return null;
            });

    assertSame(namespace, returned);
    assertTrue(resolverCalled.get());
    assertTrue(
        namespace.getNotifications().stream()
            .anyMatch(notification -> notification.getMessage().contains("each")));
  }
}

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
  void attachesDefaultValidationNotificationsBeforeReturningTheBean() throws Exception {
    var concept = new KimConceptImpl();
    concept.setName("test:Uncountable");
    concept.setType(EnumSet.of(SemanticType.QUALITY));
    concept.setFundamentalType(SemanticType.QUALITY);
    concept.setCollective(true);

    var namespace = new KimNamespaceImpl();
    namespace.setUrn("test");
    namespace.getStatements().add(concept);
    var resolverCalled = new AtomicBoolean();
    org.integratedmodelling.klab.runtime.language.KimObservableVisitor.Resolver resolver =
        (urn, knowledgeClass, context) -> {
          resolverCalled.set(true);
          return null;
        };

    var method =
        Class.forName("org.integratedmodelling.klab.services.resources.storage.WorkspaceManager")
            .getDeclaredMethod(
                "validateSemanticAsset",
                org.integratedmodelling.klab.api.lang.kim.KlabDocument.class,
                org.integratedmodelling.klab.runtime.language.KimObservableVisitor.Resolver.class);
    method.setAccessible(true);
    var returned = method.invoke(null, namespace, resolver);

    assertSame(namespace, returned);
    assertTrue(resolverCalled.get());
    assertTrue(
        namespace.getNotifications().stream()
            .anyMatch(notification -> notification.getMessage().contains("each")));
  }
}

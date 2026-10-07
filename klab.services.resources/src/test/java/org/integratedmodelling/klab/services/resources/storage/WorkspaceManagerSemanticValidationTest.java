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
  void invalidDocumentsBindParserDiagnosticsAndPreserveAuditTraces() throws Exception {
    var manager = org.mockito.Mockito.mock(WorkspaceManager.class, org.mockito.Mockito.CALLS_REAL_METHODS);
    var source = new KimConceptImpl(); source.setOffsetInDocument(12); source.setLength(5);
    var issue = org.integratedmodelling.klab.api.services.runtime.Notification.error(
        new IllegalArgumentException("Invalid declaration"), source);
    var method = WorkspaceManager.class.getDeclaredMethod("invalidOntology", String.class,
        String.class, String.class, long.class, java.util.Collection.class);
    method.setAccessible(true);
    var document = (org.integratedmodelling.klab.api.lang.kim.KimOntology) method.invoke(manager,
        "test", "project", "ontology test; bad", 0L, java.util.List.of(issue));
    var bound = document.getNotifications().iterator().next();
    org.junit.jupiter.api.Assertions.assertEquals("Invalid declaration", bound.getMessage());
    org.junit.jupiter.api.Assertions.assertEquals(issue.getStackTrace(), bound.getStackTrace());
    org.junit.jupiter.api.Assertions.assertEquals("test", bound.getLexicalContext().getDocumentUrn());
    org.junit.jupiter.api.Assertions.assertEquals("project", bound.getLexicalContext().getProjectUrn());
    org.junit.jupiter.api.Assertions.assertEquals(12, bound.getLexicalContext().getOffsetInDocument());
    org.junit.jupiter.api.Assertions.assertEquals(5, bound.getLexicalContext().getLength());
  }


  @Test
  void outgoingValidationSnapshotClearsErrorsAfterCorrection() {
    var descriptor = new org.integratedmodelling.klab.api.services.resources.ResourceSet.Resource();
    descriptor.getNotifications().add(
        org.integratedmodelling.klab.api.services.runtime.Notification.error("old error"));
    var corrected = new KimNamespaceImpl();
    WorkspaceManager.refreshValidationSnapshot(descriptor, corrected);
    assertTrue(descriptor.getNotifications().isEmpty());
    var warning = org.integratedmodelling.klab.api.services.runtime.Notification.warning("new warning");
    corrected.getNotifications().add(warning);
    WorkspaceManager.refreshValidationSnapshot(descriptor, corrected);
    WorkspaceManager.refreshValidationSnapshot(descriptor, corrected);
    org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(warning), descriptor.getNotifications());
  }
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

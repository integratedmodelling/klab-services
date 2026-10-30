package org.integratedmodelling.common.diagnostics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.junit.jupiter.api.Test;

class DocumentValidationTraceTest {
  private static KimNamespaceImpl document() {
    var document = new KimNamespaceImpl();
    document.setUrn("staging.vxii.basic"); document.setProjectName("klab.staging.vxii");
    document.setSourceCode("RAW_SOURCE_SECRET"); document.setLastUpdateTimestamp(100);
    document.getNotifications().add(Notification.error("DIAGNOSTIC_SECRET"));
    return document;
  }
  private static String field(String record, String name) {
    return java.util.Arrays.stream(record.split(" ")).filter(token -> token.startsWith(name + "="))
        .findFirst().orElseThrow().substring(name.length() + 1);
  }
  @Test void sourceCorrelationSurvivesDiagnosticOnlyCorrectionButDigestChanges() {
    var document = document(); var version = DocumentValidationTrace.version(document);
    var before = DocumentValidationTrace.format(DocumentValidationTrace.Event.IDE_INPUT, document,
        version, 3, 3, 1, DocumentValidationTrace.Decision.OBSERVE, document.getNotifications(), null, null);
    document.getNotifications().clear();
    var after = DocumentValidationTrace.format(DocumentValidationTrace.Event.RESOURCES_SNAPSHOT, document,
        version, -1, -1, -1, DocumentValidationTrace.Decision.OBSERVE, document.getNotifications(), null, null);
    assertEquals(field(before,"doc"), field(after,"doc"));
    assertEquals(field(before,"source"), field(after,"source"));
    assertEquals(field(before,"version"), field(after,"version"));
    assertNotEquals(field(before,"diagnostics"), field(after,"diagnostics"));
    assertTrue(field(before,"diagnostics").startsWith("1:0:0:"));
    assertTrue(field(after,"diagnostics").startsWith("0:0:0:"));
    assertFalse(before.contains("RAW_SOURCE_SECRET")); assertFalse(before.contains("DIAGNOSTIC_SECRET"));
  }
  @Test void changedSourceAndExpectedRevisionHaveDistinctCorrelation() {
    var document = document(); var version = DocumentValidationTrace.version(document);
    var one = DocumentValidationTrace.format(DocumentValidationTrace.Event.IDE_RPC_SEND, document,
        version, 3, 3, 1, DocumentValidationTrace.Decision.OBSERVE, List.of(), null, "REASON_SECRET");
    var revision = DocumentValidationTrace.format(DocumentValidationTrace.Event.REASONER_RECEIVED, document,
        version, 4, 4, 1, DocumentValidationTrace.Decision.OBSERVE, List.of(), null, null);
    assertEquals(field(one,"doc"),field(revision,"doc"));
    assertNotEquals(field(one,"request"),field(revision,"request"));
    document.setSourceCode("CORRECTED_SOURCE_SECRET");
    var changed = DocumentValidationTrace.format(DocumentValidationTrace.Event.IDE_INPUT, document,
        DocumentValidationTrace.version(document),3,3,1,DocumentValidationTrace.Decision.OBSERVE,List.of(),null,null);
    assertNotEquals(field(one,"doc"),field(changed,"doc"));
    assertFalse(one.contains("REASON_SECRET")); assertFalse(changed.contains("CORRECTED_SOURCE_SECRET"));
  }
  @Test void originalExceptionFramesRemainCorrelatedWithoutMessagesOrSource() {
    var request = SemanticValidationRequest.of(document(), "VERSION_SECRET"); request.setKnowledgeRevision(3);
    var failure = new IllegalStateException("PROVIDER_CREDENTIAL_SECRET", new IllegalArgumentException("NESTED_SOURCE_SECRET"));
    failure.setStackTrace(new StackTraceElement[]{new StackTraceElement("example.Validator","validate","Validator.java",42)});
    var record = DocumentValidationTrace.formatFailure(request,3,failure);
    assertTrue(record.contains("exception0=java.lang.IllegalStateException"));
    assertTrue(record.contains("frame0_0=example.Validator.validate(Validator.java:42)"));
    assertTrue(record.contains("exception1=java.lang.IllegalArgumentException"));
    for (var secret : List.of("PROVIDER_CREDENTIAL_SECRET","NESTED_SOURCE_SECRET","VERSION_SECRET","RAW_SOURCE_SECRET","DIAGNOSTIC_SECRET"))
      assertFalse(record.contains(secret),secret);
  }
  @Test void disabledFacadeDoesNotDereferencePayloadOrFailure() {
    var previous = System.getProperty(DocumentValidationTrace.PROPERTY);
    try {
      System.setProperty(DocumentValidationTrace.PROPERTY,"false");
      assertDoesNotThrow(() -> DocumentValidationTrace.snapshot(null,null,null,0,0,null));
      assertDoesNotThrow(() -> DocumentValidationTrace.response(null,null,null,0,null));
      assertDoesNotThrow(() -> DocumentValidationTrace.resources(null,null));
      assertDoesNotThrow(() -> DocumentValidationTrace.exception(null,0,null));
      assertDoesNotThrow(() -> DocumentValidationTrace.render(null,null,null,null,0,0));
    } finally { if(previous == null) System.clearProperty(DocumentValidationTrace.PROPERTY); else System.setProperty(DocumentValidationTrace.PROPERTY,previous); }
  }
  @Test void renderedDocumentCountersAndSemanticResponseRemainSeparateAndPrivate() throws Exception {
    var logging = org.integratedmodelling.common.logging.Logging.INSTANCE;
    var previousWriter = logging.getInfoWriter(); var previousProperty = System.getProperty(DocumentValidationTrace.PROPERTY);
    var records = new java.util.concurrent.CopyOnWriteArrayList<String>();
    var ready = new java.util.concurrent.CountDownLatch(2);
    try {
      logging.setInfoWriter(record -> {
        if(record.contains("event=IDE_RENDER ") || record.contains("event=IDE_RENDER_RESPONSE ")) {
          records.add(record); ready.countDown();
        }
      });
      System.setProperty(DocumentValidationTrace.PROPERTY,"true");
      var document = document(); var request = SemanticValidationRequest.of(document,DocumentValidationTrace.version(document));
      var response = SemanticValidationResponse.forRequest(request); response.setStatus(SemanticValidationResponse.Status.UNAVAILABLE);
      response.setKnowledgeRevision(3); response.setReason("PRIVATE_REASON_SECRET");
      DocumentValidationTrace.render(document,request,response,"Semantic validation unavailable",7,2);
      assertTrue(ready.await(5,java.util.concurrent.TimeUnit.SECONDS));
      var current = records.stream().filter(record -> record.contains("event=IDE_RENDER ")).findFirst().orElseThrow();
      var semantic = records.stream().filter(record -> record.contains("event=IDE_RENDER_RESPONSE ")).findFirst().orElseThrow();
      assertTrue(current.contains("treeErrors=7 treeWarnings=2")); assertTrue(current.contains("diagnostics=1:0:0:"));
      assertTrue(semantic.contains("status=UNAVAILABLE")); assertTrue(semantic.contains("diagnostics=0:0:0:"));
      for(var record:records) for(var secret:List.of("RAW_SOURCE_SECRET","DIAGNOSTIC_SECRET","PRIVATE_REASON_SECRET"))
        assertFalse(record.contains(secret));
    } finally {
      logging.setInfoWriter(previousWriter);
      if(previousProperty==null)System.clearProperty(DocumentValidationTrace.PROPERTY);else System.setProperty(DocumentValidationTrace.PROPERTY,previousProperty);
    }
  }
}

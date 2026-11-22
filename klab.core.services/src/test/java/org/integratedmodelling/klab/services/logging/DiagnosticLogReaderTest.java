package org.integratedmodelling.klab.services.logging;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.services.logging.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class DiagnosticLogReaderTest {
  @TempDir Path root;
  Path file;
  DiagnosticLogReader reader;
  Time clock;
  static class Time extends Clock {
    long millis;
    public ZoneId getZone() { return ZoneOffset.UTC; }
    public Clock withZone(ZoneId zone) { return this; }
    public Instant instant() { return Instant.ofEpochMilli(millis); }
  }
  @BeforeEach void setup() throws Exception {
    file=root.resolve("reasoner.log");Files.writeString(file,"");clock=new Time();
    reader=new DiagnosticLogReader(file,ZoneOffset.UTC,clock);
  }
  @AfterEach void close() throws Exception { reader.close(); }
  String head(String message) {
    String logger=message.startsWith("AUTHORITY_BOOTSTRAP_COMPONENT_FAILURE")
        ? "org.integratedmodelling.klab.components.OwnedBootstrapDiagnostics"
        : "org.integratedmodelling.klab.services.reasoner.internal.AuthorityBootstrap";
    return "[2026-10-23 13:31:46.123] - 123 ERROR [http-1] --- "+logger+": "+message+"\n";
  }
  LogPage page(String cursor,int limit,boolean follow) {
    clock.millis+=250;
    return reader.query(new LogQuery("service",cursor,limit,follow,null,null,null,null),"admin");
  }
  @Test void preservesCauseShapeAndSafeIncidentFieldsWithoutSecrets() throws Exception {
    Files.writeString(file,head("AUTHORITY_BOOTSTRAP_COMPONENT_FAILURE phase=COMPONENT_EXPORT component=klab.authority.taxa@1.0.0-SNAPSHOT reason=EXCEPTION httpStatus=500 token=topsecret url=https://u:p@example/x?token=secret")
        +"java.lang.IllegalStateException: password=secret\n"
        +"\tat org.integratedmodelling.Test.run(Test.java:12)\n"
        +"Caused by: org.integratedmodelling.Http$RequestFailure: Authorization: Bearer abc\n"
        +"\tSuppressed: java.io.IOException: secret-token\n"
        +"\tat org.integratedmodelling.Test.close(Test.java:30)\n"
        +"\t... 2 more\n"
        +head("ordinary user payload password=secret"));
    var result=page(null,200,false);assertEquals(1,result.records().size());
    var record=result.records().getFirst();assertFalse(record.partial());
    assertEquals("500",record.diagnostics().get("httpStatus"));
    assertEquals("COMPONENT_EXPORT",record.diagnostics().get("phase"));
    assertTrue(record.stackTrace().contains("Caused by:"));
    assertTrue(record.stackTrace().contains("Suppressed:"));
    assertTrue(record.stackTrace().contains("Test.java:12"));
    assertTrue(record.stackTrace().contains("... 2 more"));
    String json=JacksonConfiguration.newObjectMapper().writeValueAsString(result);
    for(String secret:new String[]{"topsecret","password","Bearer","example/x","secret-token"}) assertFalse(json.contains(secret),secret);
    assertEquals(result,JacksonConfiguration.newObjectMapper().readValue(json,LogPage.class));
  }
  @Test void snapshotPagesThenExplicitFollowPreservePartialEventIdentity() throws Exception {
    Files.writeString(file,head("AUTHORITY_BOOTSTRAP_FAILURE phase=PRODUCE")
        +head("AUTHORITY_BOOTSTRAP_FAILURE phase=VERIFY_PROOF"));
    var first=page(null,1,false);assertEquals(1,first.records().size());
    Files.writeString(file,head("AUTHORITY_BOOTSTRAP_FAILURE phase=PROVIDER_INPUT"),StandardOpenOption.APPEND);
    var second=page(first.nextCursor(),1,false);assertEquals(1,second.records().size());
    assertTrue(second.records().getFirst().partial());
    var followed=page(second.nextCursor(),10,true);
    assertEquals(2,followed.records().size());
    assertEquals(second.records().getFirst().eventId(),followed.records().getFirst().eventId());
    assertFalse(followed.records().getFirst().partial());
    assertEquals("PROVIDER_INPUT",followed.records().getLast().diagnostics().get("phase"));
  }
  @Test void unfinishedUtf8AndStackLineWaitForCompletion() throws Exception {
    Files.writeString(file,head("AUTHORITY_BOOTSTRAP_FAILURE phase=PRODUCE"));
    var first=page(null,10,false);
    Files.write(file,new byte[]{(byte)0xc3},StandardOpenOption.APPEND);
    var partial=page(first.nextCursor(),10,true);assertTrue(partial.records().getFirst().partial());
    Files.write(file,new byte[]{(byte)0xa9,(byte)'\n'},StandardOpenOption.APPEND);
    Files.writeString(file,"Caused by: java.io.IOException: secret\n",StandardOpenOption.APPEND);
    var next=page(partial.nextCursor(),10,true);
    assertEquals(first.records().getFirst().eventId(),next.records().getFirst().eventId());
    assertTrue(next.records().getFirst().stackTrace().contains("java.io.IOException"));
    assertFalse(next.records().getFirst().stackTrace().contains("secret"));
  }
  @Test void rotationTruncationAndRestartAreExplicitGaps() throws Exception {
    Files.writeString(file,head("AUTHORITY_BOOTSTRAP_FAILURE phase=PRODUCE"));
    var first=page(null,10,false);
    Files.move(file,root.resolve("reasoner.log.1"));Files.writeString(file,head("AUTHORITY_BOOTSTRAP_FAILURE phase=PRODUCE"));
    assertEquals(410,assertThrows(DiagnosticLogReader.Failure.class,()->page(first.nextCursor(),10,true)).status());
    var current=page(null,10,false);
    assertNotEquals(first.records().getFirst().eventId(),current.records().getFirst().eventId());
    assertEquals(410,assertThrows(DiagnosticLogReader.Failure.class,()->page(first.nextCursor(),10,true)).status());
    assertEquals(current.records().getFirst().eventId(),page(current.nextCursor(),10,false).records().getFirst().eventId());
    Files.writeString(file,"");
    assertEquals(410,assertThrows(DiagnosticLogReader.Failure.class,()->page(current.nextCursor(),10,true)).status());
    var fresh=new DiagnosticLogReader(file,ZoneOffset.UTC,clock);
    assertEquals(410,assertThrows(DiagnosticLogReader.Failure.class,()->fresh.query(
        new LogQuery("service",current.nextCursor(),10,false,null,null,null,null),"admin")).status());
    fresh.close();
  }
  @Test void rejectsTraversalBoundsActorAndChangedFilters() throws Exception {
    assertEquals(400,assertThrows(DiagnosticLogReader.Failure.class,()->reader.query(
        new LogQuery("../../secret",null,10,false,null,null,null,null),"admin")).status());
    assertEquals(400,assertThrows(DiagnosticLogReader.Failure.class,()->page(null,1001,false)).status());
    var first=page(null,10,false);
    assertEquals(403,assertThrows(DiagnosticLogReader.Failure.class,()->reader.query(
        new LogQuery("service",first.nextCursor(),10,false,null,null,null,null),"other-admin")).status());
    clock.millis+=250;
    assertEquals(400,assertThrows(DiagnosticLogReader.Failure.class,()->reader.query(
        new LogQuery("service",first.nextCursor(),10,false,null,null,"ERROR",null),"admin")).status());
    clock.millis+=250;
    assertEquals(400,assertThrows(DiagnosticLogReader.Failure.class,()->page("/etc/passwd",10,false)).status());
  }
  @Test void expiresCursorsAndRateLimitsWithoutUnboundedState() throws Exception {
    var first=page(null,10,false);
    assertEquals(429,assertThrows(DiagnosticLogReader.Failure.class,()->reader.query(LogQuery.firstPage(),"admin")).status());
    clock.millis+=600_001;
    assertEquals(410,assertThrows(DiagnosticLogReader.Failure.class,()->page(first.nextCursor(),10,false)).status());
  }
  @Test void limitsScanEventAndResponseBytesAndReportsTruncation() throws Exception {
    String frame="\tat org.integratedmodelling.Test.run(Test.java:12)\n";
    Files.writeString(file,head("AUTHORITY_BOOTSTRAP_FAILURE phase=PRODUCE")+frame.repeat(10_000)
        +head("AUTHORITY_BOOTSTRAP_FAILURE phase=PRODUCE"));
    var first=page(null,1000,false);
    assertFalse(first.caughtUp());assertTrue(first.snapshotEnd()>DiagnosticLogReader.MAX_SCAN);
    var second=page(first.nextCursor(),1000,false);
    assertTrue(second.records().getFirst().truncated());
    assertTrue(JacksonConfiguration.newObjectMapper().writeValueAsBytes(second).length<1024*1024);
  }
  @Test void skipsUnvettedTextAndAcceptsFallbackFilePattern() throws Exception {
    Files.writeString(file,head("password=secret")
        +"[2026-10-23 13:31:46.123] ERROR [main] org.integratedmodelling.klab.services.reasoner.internal.AuthorityBootstrap: AUTHORITY_BOOTSTRAP_FAILURE phase=PRODUCE\n");
    var result=page(null,10,false);assertEquals(1,result.records().size());
    assertEquals(1,result.omittedEvents());
    assertFalse(result.toString().contains("secret"));
  }
  @Test void sameFileTruncateAndRegrowCannotReuseOldBoundary() throws Exception {
    Files.writeString(file,head("AUTHORITY_BOOTSTRAP_FAILURE phase=PRODUCE"));
    var first=page(null,10,false);
    Files.writeString(file,head("AUTHORITY_BOOTSTRAP_FAILURE phase=VERIFY_PROOF").repeat(3));
    assertEquals(410,assertThrows(DiagnosticLogReader.Failure.class,()->page(first.nextCursor(),10,true)).status());
  }
  @Test void unapprovedEmitterCannotSmuggleFieldsThroughKnownCode() throws Exception {
    Files.writeString(file,"[2026-10-23 13:31:46.123] ERROR [main] com.thirdparty.Task: AUTHORITY_BOOTSTRAP_FAILURE component=SECRET123 phase=SECRETVALUE\n");
    var result=page(null,10,false);assertTrue(result.records().isEmpty());
    assertFalse(result.toString().contains("SECRET"));
  }
  @Test void metadataViewShowsOrdinaryLogHeadersWithoutTheirPayloadOrLogger() throws Exception {
    Files.writeString(file, "[2026-10-23 13:31:46.123] INFO [my-private-thread] org.example.SecretLogger: private payload\n"
        + "\tat org.example.SecretLogger.execute(PrivateFile.java:10)\n"
        + head("AUTHORITY_BOOTSTRAP_FAILURE phase=PRODUCE"));
    var metadata = reader.query(new LogQuery("service",null,10,false,null,null,null,null,true),"admin");
    assertEquals(2, metadata.records().size());
    var ordinary = metadata.records().getFirst();
    assertEquals("2026-10-23T13:31:46.123Z", ordinary.timestamp());
    assertEquals("INFO", ordinary.level());
    assertEquals("", ordinary.logger());
    assertEquals("", ordinary.stackTrace());
    assertTrue(ordinary.diagnostics().isEmpty());
    assertEquals("Message withheld by diagnostic policy", ordinary.message());
    assertEquals("AUTHORITY_BOOTSTRAP_FAILURE", metadata.records().getLast().message());
    assertFalse(metadata.toString().contains("SecretLogger"));
    assertFalse(metadata.toString().contains("private payload"));
    assertEquals(1, page(null,10,false).records().size());
  }
  @Test void abbreviatedProducerAndGroovyCustomCausePreserveSafeStructure() throws Exception {
    Files.writeString(file,head("AUTHORITY_BOOTSTRAP_COMPONENT_FAILURE phase=COMPONENT_EXPORT reason=EXCEPTION")
        .replace("org.integratedmodelling.klab.components.OwnedBootstrapDiagnostics","o.i.k.c.OwnedBootstrapDiagnostics")
        +"Caused by: org.example.CustomProblem: password=secret\n"
        +"\tat app/module@1.0/org.example.Script.run(Script1.groovy:3)\n");
    var record=page(null,10,false).records().getFirst();
    assertTrue(record.stackTrace().contains("org.example.CustomProblem: message redacted"));
    assertTrue(record.stackTrace().contains("Script1.groovy:3"));assertFalse(record.stackTrace().contains("secret"));
  }

  @Test void messageViewRetainsTroubleshootingTextAndRedactsSecretsAcrossPages() throws Exception {
    Files.writeString(file, head("SMTP handshake failed using fixture-mail-password")
        + head("Import rejected for https://fixture-user:fixture-pass@example.org/item?sig=fixture-sig#private")
        + head("HTTP request failed Authorization: Bearer fixture-bearer")
        + head("Settings rejected {\"clientSecret\":\"escaped\\\"fixture-secret\",\"port\":587}")
        + head("Authentication failed with webui_fixtureSessionToken for fixture@example.org")
        + head("SMTP config invalid <password><![CDATA[fixture-xml-secret]]></password> at port 587")
        + head("Certificate parse failed -----BEGIN PRIVATE KEY----- fixture-key"));
    try (var messageReader = new DiagnosticLogReader(file, ZoneOffset.UTC, clock, () -> "fixture-mail-password")) {
      var first = messageReader.query(new LogQuery("service",null,2,false,null,null,null,null,true,true),"admin");
      assertEquals(DiagnosticLogReader.MESSAGE_POLICY, first.redactionPolicy());
      assertEquals("SMTP handshake failed using [redacted]", first.records().getFirst().message());
      assertTrue(first.records().getLast().message().contains("example.org/item"));
      assertFalse(first.toString().contains("fixture-user"));
      assertFalse(first.toString().contains("fixture-pass"));
      assertFalse(first.toString().contains("fixture-sig"));
      assertFalse(first.toString().contains("#private"));
      clock.millis += 250;
      assertEquals(400, assertThrows(DiagnosticLogReader.Failure.class, () ->
          messageReader.query(new LogQuery("service",first.nextCursor(),10,false,null,null,null,null,true),"admin")).status());
      clock.millis += 250;
      var next = messageReader.query(new LogQuery("service",first.nextCursor(),10,false,null,null,null,null,true,true),"admin");
      assertEquals("HTTP request failed Authorization: [redacted]", next.records().getFirst().message());
      assertTrue(next.records().get(1).message().contains("\"port\":587"));
      assertEquals("[message withheld by redaction policy]", next.records().getLast().message());
      var wire = JacksonConfiguration.newObjectMapper().writeValueAsString(next);
      for (String secret : new String[]{"fixture-bearer", "fixture-secret", "fixtureSessionToken", "fixture@example.org", "fixture-xml-secret", "fixture-key"}) {
        assertFalse(wire.contains(secret), secret);
      }
    }
  }

  @Test void messageViewWithholdsTruncatedHeadersAndNeverReadsUnsupportedContinuations() throws Exception {
    Files.writeString(file, head("request failed " + "a".repeat(8150) + " password=fixture-password")
        + "raw request continuation contains fixture-body\n"
        + head("Connection refused on SMTP port 587"));
    var result = reader.query(new LogQuery("service",null,10,false,null,null,null,null,false,true),"admin");
    assertEquals("[message withheld by redaction policy]", result.records().getFirst().message());
    assertTrue(result.records().getFirst().truncated());
    assertEquals("Connection refused on SMTP port 587", result.records().getLast().message());
    assertFalse(result.toString().contains("fixture-"));
  }
}

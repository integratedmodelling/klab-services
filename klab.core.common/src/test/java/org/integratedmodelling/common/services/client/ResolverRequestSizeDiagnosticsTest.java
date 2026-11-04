package org.integratedmodelling.common.services.client;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.junit.jupiter.api.Test;
class ResolverRequestSizeDiagnosticsTest {
 @Test void escapedUnicodeAndLargeValuesAreCountedWithoutContents() {
  String json="{\"observation\":{\"metadata\":{\"payload\":\"SECRET\\n\\u0041😀\\\"\\\\\"}},\"array\":[\"small\"]}";
  var result=ResolverRequestSizeDiagnostics.inspect(json,10000,100,20);
  assertEquals(true,result.get("complete"));
  var fields=(List<ResolverRequestSizeDiagnostics.Field>)result.get("largestStrings");
  var payload=fields.stream().filter(f->f.path().equals("/observation/metadata/payload")).findFirst().orElseThrow();
  assertEquals("SECRET\nA😀\"\\".length(),payload.decodedChars());
  assertFalse(Utils.Json.asString(result).contains("SECRET"));
 }
 @Test void redactsUnsafeFieldNamesAndEnforcesDiagnosticBudgets() {
  String json="{\"https://SECRET.invalid/token\":\"secret-value\",\"nest\":[[[1]]]}";
  var result=ResolverRequestSizeDiagnostics.inspect(json,10000,100,20);
  assertFalse(Utils.Json.asString(result).contains("SECRET"));assertFalse(Utils.Json.asString(result).contains("secret-value"));
  assertEquals("CHAR_BUDGET",ResolverRequestSizeDiagnostics.inspect(json,8,100,20).get("stop"));
  assertEquals("/payload",ResolverRequestSizeDiagnostics.inspect("{\"payload\":\""+"x".repeat(100)+"\"}",30,100,20).get("stoppedInStringPath"));
  assertEquals("VALUE_BUDGET",ResolverRequestSizeDiagnostics.inspect(json,10000,1,20).get("stop"));
  assertEquals("DEPTH_BUDGET",ResolverRequestSizeDiagnostics.inspect(json,10000,100,1).get("stop"));
  assertEquals("ESCAPE",ResolverRequestSizeDiagnostics.inspect("{\"x\":\"\\q\"}",100,100,20).get("stop"));
 }
 @Test void defaultOffDoesNotInspectScopeOrPrint() {
  var scope=mock(ContextScope.class);var prior=System.getProperty(ResolverRequestSizeDiagnostics.FLAG);var old=System.err;var bytes=new java.io.ByteArrayOutputStream();
  try{System.clearProperty(ResolverRequestSizeDiagnostics.FLAG);System.setErr(new java.io.PrintStream(bytes));
   ResolverRequestSizeDiagnostics.report("/resolve",new ResolutionRequest(),scope,"not-json SECRET");assertEquals("",bytes.toString());verifyNoInteractions(scope);
  }finally{System.setErr(old);restore(prior);}
 }
 @Test void actualAsyncPostKeepsBodyAndParserRejectionWhileReportingExactStringField()throws Exception {
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var received=new java.util.concurrent.atomic.AtomicReference<String>();var rejected=new java.util.concurrent.atomic.AtomicBoolean();
  server.createContext("/resolve",exchange->{
   String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);received.set(body);
   var factory=JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder().maxStringLength(128).build()).build();
   try{new ObjectMapper(factory).readTree(body);}catch(com.fasterxml.jackson.core.exc.StreamConstraintsException expected){rejected.set(true);}
   byte[] reply="{\"message\":\"test parser rejection\"}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(400,reply.length);try(var out=exchange.getResponseBody()){out.write(reply);}
  });server.start();var old=System.err;var bytes=new java.io.ByteArrayOutputStream();var prior=System.getProperty(ResolverRequestSizeDiagnostics.FLAG);
  var observation=new ObservationImpl();observation.setId(421);String secret="SECRET-CANARY".repeat(100);observation.getMetadata().put("payload",secret);var request=new ResolutionRequest();request.setObservation(observation);String original=Utils.Json.asString(request);
  try(var client=Utils.Http.getClient("http://127.0.0.1:"+server.getAddress().getPort(),null)) {
   System.setProperty(ResolverRequestSizeDiagnostics.FLAG,"true");System.setErr(new java.io.PrintStream(bytes));
   assertThrows(java.util.concurrent.ExecutionException.class,()->client.postAsync("/resolve",request,Object.class).get());
   assertTrue(rejected.get());assertEquals(original,received.get());assertEquals(secret,observation.getMetadata().get("payload"));
   String facts=bytes.toString();assertTrue(facts.contains("RESOLVER_REQUEST_SIZE_DIAGNOSTIC"));assertTrue(facts.contains("detachedMetadataStrings"));assertTrue(facts.contains("\"target\":421"));
   assertTrue(facts.contains("/observation/metadata/delegate/payload"));assertTrue(facts.contains("\"decodedChars\":"+secret.length()));assertFalse(facts.contains("SECRET-CANARY"));
  }finally{System.setErr(old);restore(prior);server.stop(0);}
 }
 @Test void constraintReporterExtractsOnlyActualLibraryNumbersNotExceptionContent()throws Exception {
  var factory=JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder().maxStringLength(128).build()).build();
  var failure=assertThrows(com.fasterxml.jackson.core.exc.StreamConstraintsException.class,()->new ObjectMapper(factory).readTree("\""+"SECRET".repeat(100)+"\""));
  var old=System.err;var bytes=new java.io.ByteArrayOutputStream();var prior=System.getProperty(ResolverRequestSizeDiagnostics.FLAG);
  try {
   System.setProperty(ResolverRequestSizeDiagnostics.FLAG,"true");System.setErr(new java.io.PrintStream(bytes));
   ResolverRequestSizeDiagnostics.reportConstraint(new RuntimeException("WRAPPER-SECRET",failure));
   assertTrue(bytes.toString().contains("RESOLVER_STRING_CONSTRAINT_DIAGNOSTIC"));assertTrue(bytes.toString().contains("\"maxAllowedChars\":128"));
   assertFalse(bytes.toString().contains("SECRET"));
  }finally{System.setErr(old);restore(prior);}
 }
 static void restore(String prior){if(prior==null)System.clearProperty(ResolverRequestSizeDiagnostics.FLAG);else System.setProperty(ResolverRequestSizeDiagnostics.FLAG,prior);}
}

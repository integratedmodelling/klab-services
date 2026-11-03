package org.integratedmodelling.klab.runtime.semantic;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.services.client.*;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.runtime.scale.ScaleImpl;

class SemanticCycleObservationTransportTest {
 static ObservationImpl observation() {
  var meaning=new ConceptImpl();meaning.setUrn("imod:Bearer");meaning.setType(EnumSet.of(SemanticType.SUBJECT));
  var observable=new ObservableImpl();observable.setUrn(meaning.getUrn());observable.setSemantics(meaning);observable.setSpecializedComponents(List.of(org.integratedmodelling.klab.api.collections.Pair.of(meaning,meaning)));
  var value=new ObservationImpl();value.setId(-7);value.setTransientId(123);value.setParentId(50);value.setParentTransientId(456);value.setObservable(observable);
  var local=new ScaleImpl(Geometry.UNIVERSAL);value.setGeometry(local);
  value.getMetadata().put("nested",Map.of("support",List.of(local)));
  var contextualization=new ObservationImpl.ContextualizationDataImpl();contextualization.getParameters().put("support",local);value.setContextualizationData(contextualization);
  var participant=new ObservationImpl();participant.setId(-8);participant.setUrn("participant");participant.setObservable(observable);value.setParticipants(List.of(participant));return value;
 }
 @Test void concreteRuntimeObservationIsDetachedWithoutMutatingSourceOrLosingSemanticIdentity() throws Exception {
  var original=observation();var encoded=SemanticCycleCodec.encode(original,Observation.class);
  var copy=(Observation)SemanticCycleCodec.decode(encoded,Observation.class);
  assertInstanceOf(ScaleImpl.class,original.getGeometry());assertFalse(encoded.contains(ScaleImpl.class.getName()));
  assertFalse(copy.getGeometry() instanceof org.integratedmodelling.klab.api.knowledge.observation.scale.Scale);
  assertEquals(original.getGeometry().encode(),copy.getGeometry().encode());assertEquals(-7,copy.getId());assertEquals(123,copy.getTransientId());
  assertEquals(50,copy.getParentId());assertEquals(456,copy.getParentTransientId());assertEquals(-8,copy.getParticipants().getFirst().getId());
  assertInstanceOf(ObservableImpl.class,copy.getObservable());assertInstanceOf(ConceptImpl.class,copy.getObservable().getSemantics());
  assertEquals("imod:Bearer",copy.getObservable().getSemantics().getUrn());
  assertEquals("imod:Bearer",copy.getObservable().getSpecializedComponents().iterator().next().getFirst().getUrn());
  assertInstanceOf(Geometry.class,((List<?>)((Map<?,?>)copy.getMetadata().get("nested")).get("support")).getFirst());
  assertInstanceOf(Geometry.class,copy.getContextualizationData().getParameters().get("support"));
 }
 @Test void actualPinnedHttpStrategyRequestDecodesRealObservationAndRetainsExactCycle() throws Exception {
  var mapper=JacksonConfiguration.newObjectMapper();var calls=new AtomicInteger();var problem=new AtomicReference<Throwable>();
  var binding=new SemanticCycleBinding(SemanticCycleBinding.VERSION,"reasoner","session",3,"fingerprint","lease");
  var cycle=new SemanticCycleRequest("runtime","context","cycle",binding);
  var method=Reasoner.class.getMethod("computeObservationStrategies",Observation.class,ContextScope.class);
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext(ServicesAPI.REASONER.SEMANTIC_CYCLE_INVOKE,exchange->{
   int status=200;byte[] bytes;
   try {
    var invocation=mapper.readValue(exchange.getRequestBody(),SemanticCycleInvocation.class);assertEquals(cycle,invocation.cycle());
    assertEquals(method,SemanticCycleCodec.method(invocation.method(),invocation.parameterTypes()));
    var received=(Observation)SemanticCycleCodec.decode(invocation.arguments().getFirst(),method.getGenericParameterTypes()[0]);
    assertEquals("null",invocation.arguments().get(1));assertEquals("imod:Bearer",received.getObservable().getSemantics().getUrn());assertEquals(-7,received.getId());
    assertFalse(received.getGeometry() instanceof org.integratedmodelling.klab.api.knowledge.observation.scale.Scale);calls.incrementAndGet();
    bytes=mapper.writeValueAsBytes(new SemanticCycleResult(SemanticCycleCodec.encode(List.of(),method.getGenericReturnType()),List.of(SemanticCycleCodec.encode(received,Observation.class),"null")));
   }catch(Throwable failure){problem.set(failure);status=500;bytes="{}".getBytes();}
   exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,bytes.length);
   try(var output=exchange.getResponseBody()){output.write(bytes);}
  });server.start();
  try(var http=Utils.Http.getClient("http://127.0.0.1:"+server.getAddress().getPort(),null)) {
   var scope=(ContextScope)Proxy.newProxyInstance(ContextScope.class.getClassLoader(),new Class[]{ContextScope.class},(p,m,a)->m.getName().equals("getId")?"context":null);
   var pinned=SemanticCycleFacade.create((Reasoner)Proxy.newProxyInstance(Reasoner.class.getClassLoader(),new Class[]{Reasoner.class},(p,m,a)->null),cycle,scope,(invocation,s)->http.postRequired(ServicesAPI.REASONER.SEMANTIC_CYCLE_INVOKE,invocation,SemanticCycleResult.class));
   assertTrue(pinned.computeObservationStrategies(observation(),scope).isEmpty());assertEquals(1,calls.get());assertNull(problem.get(),()->String.valueOf(problem.get()));
  }finally{server.stop(0);}
 }
 @Test void directLocalGeometryIsPortableAndScopeArgumentsRemainNonDeserializable() {
  var original=new ScaleImpl(Geometry.UNIVERSAL);var encoded=SemanticCycleCodec.encode(original,Geometry.class);
  assertFalse(encoded.contains(ScaleImpl.class.getName()));assertEquals(original.encode(),((Geometry)SemanticCycleCodec.decode(encoded,Geometry.class)).encode());
  assertThrows(SecurityException.class,()->SemanticCycleCodec.decode("{}",ContextScope.class));
  assertThrows(RuntimeException.class,()->SemanticCycleCodec.decode("{\"@class\":\"java.util.HashMap\"}",Semantics.class));
  assertThrows(SecurityException.class,()->SemanticCycleCodec.method("configureAuthority",List.of()));
 }
}

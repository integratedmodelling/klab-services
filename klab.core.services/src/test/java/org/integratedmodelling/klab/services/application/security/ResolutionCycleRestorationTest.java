package org.integratedmodelling.klab.services.application.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticCycleBinding;
import org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding;
import org.integratedmodelling.klab.services.scopes.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.context.SecurityContextHolder;

class ResolutionCycleRestorationTest {
 @Test void actualHttpHeaderSurvivesAuthorizationAndRestoresOnlyTheSelectedPinnedFacade() throws Exception {
   var user=mock(UserIdentity.class);when(user.getUsername()).thenReturn("tester");when(user.getId()).thenReturn("credential");when(user.getData()).thenReturn(org.integratedmodelling.klab.api.collections.Parameters.create());
   var root=new ServiceContextScope(new ServiceSessionScope(new ServiceUserScope(user,mock(RuntimeService.class))),
       DigitalTwin.Configuration.builder().id("context").name("Twin").build(),user);root.setId("context");root.setHostServiceId("runtime");
   var original=mock(Reasoner.class);when(original.serviceId()).thenReturn("reasoner");root.addService(original);
   var facade=mock(Reasoner.class);
   var status=mock(KlabService.ServiceStatus.class);when(status.isOperational()).thenReturn(true);
   when(original.status()).thenReturn(status);when(facade.status()).thenReturn(status);
   var oldConcept=mock(Concept.class);when(oldConcept.getUrn()).thenReturn("epoch1:concept");
   when(facade.resolveConcept("test:concept")).thenReturn(oldConcept);
   var binding=new ResolutionCycleBinding(1,"cycle","context","runtime",true,
       new SemanticCycleBinding("semantic-cycle-v1","reasoner","old-session",1,"old-fingerprint","lease"));
   when(original.bindSemanticCycle(eq(binding.semanticBinding()),eq("runtime"),eq("context"),eq("cycle"),any()))
       .thenReturn(facade);
   var scopeManager=new ScopeManager(mock(ResourcesService.class));
   var authorizations=mock(ServiceAuthorizationManager.class);
   when(authorizations.validateToken(eq("credential"),anyMap())).thenAnswer(call->{
     Map<String,String> headers=call.getArgument(1);
     var restored=scopeManager.contextualizeScope(root,ContextScope.parseScopeId(headers.get(ServicesAPI.SCOPE_HEADER)),headers);
     var token=new EngineAuthorization("hub","tester","credential",Map.of(),java.util.List.of(),java.util.List.of(Role.ROLE_USER));
     token.setAuthenticated(true);token.setScope(restored);return token;
   });
   var filter=new TokenAuthorizationFilter(mock(AuthenticationManager.class),authorizations);
   var failure=new AtomicReference<Throwable>();var restoredBinding=new AtomicReference<ResolutionCycleBinding>();
   var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
   server.createContext("/",exchange->{
     try {
       var request=mock(HttpServletRequest.class);
       when(request.getHeader(anyString())).thenAnswer(c->exchange.getRequestHeaders().getFirst(c.getArgument(0)));
       var response=mock(HttpServletResponse.class);
       FilterChain controller=(req,res)->{
         var authorized=(EngineAuthorization)SecurityContextHolder.getContext().getAuthentication();
         var scope=(ContextScope)authorized.getScope();restoredBinding.set(scope.getResolutionCycleBinding());
         var result=scope.getService(Reasoner.class).resolveConcept("test:concept").getUrn();
         var bytes=result.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);
         try(var out=exchange.getResponseBody()){out.write(bytes);}
       };
       filter.doFilterInternal(request,response,controller);
     }catch(Throwable error){failure.set(error);exchange.sendResponseHeaders(500,-1);exchange.close();}
     finally{SecurityContextHolder.clearContext();}
   });server.start();
   try(var http=Utils.Http.getClient("http://127.0.0.1:"+server.getAddress().getPort(),null)){
     // An ordinary current-generation client cannot supply this old answer.
     when(original.resolveConcept("test:concept")).thenThrow(new AssertionError("Current actor fallback"));
     var answer=http.withScope(root.withResolutionCycleBinding(binding)).get("/query",String.class);
     assertNull(failure.get(), () -> "Loopback controller failed: " + failure.get());
     assertEquals("epoch1:concept",answer);assertEquals(binding,restoredBinding.get());
     verify(original).bindSemanticCycle(eq(binding.semanticBinding()),eq("runtime"),eq("context"),eq("cycle"),any());
     verify(original,never()).resolveConcept(anyString());assertNull(root.getResolutionCycleBinding());
     var unavailable=new ResolutionCycleBinding(1,"foreign-cycle","context","runtime",true,
         new SemanticCycleBinding("semantic-cycle-v1","another-reasoner","session",2,"fingerprint","lease"));
     var encoded=java.util.Base64.getEncoder().encodeToString(Utils.Json.asString(unavailable).getBytes(StandardCharsets.UTF_8));
     assertThrows(org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException.class,
         ()->scopeManager.contextualizeScope(root,ContextScope.parseScopeId("context"),Map.of(ServicesAPI.RESOLUTION_CYCLE_HEADER,encoded)));
     var unbound=new ResolutionCycleBinding(1,"native-cycle","context","runtime",true,null);
     var nativeHeader=java.util.Base64.getEncoder().encodeToString(Utils.Json.asString(unbound).getBytes(StandardCharsets.UTF_8));
     var nativeScope=scopeManager.contextualizeScope(root,ContextScope.parseScopeId("context"),Map.of(ServicesAPI.RESOLUTION_CYCLE_HEADER,nativeHeader));
     assertTrue(nativeScope.getServices(Reasoner.class).isEmpty());assertSame(original,root.getService(Reasoner.class));
   }finally{server.stop(0);scopeManager.shutdown();}
 }
}

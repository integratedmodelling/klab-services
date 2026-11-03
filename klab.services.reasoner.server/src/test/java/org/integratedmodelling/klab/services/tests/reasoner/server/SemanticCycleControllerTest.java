package org.integratedmodelling.klab.services.tests.reasoner.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.reasoner.*;
import org.integratedmodelling.klab.services.reasoner.controllers.ReasonerController;
import org.integratedmodelling.klab.services.reasoner.controllers.SemanticCycleRequestFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SemanticCycleControllerTest {
  @Test void cycleProtocolUsesAuthenticatedScopeAndReportsRejectionWithoutCurrentFallback() throws Exception {
    var mapper=JacksonConfiguration.newObjectMapper();var service=mock(ReasonerService.class);
    var server=mock(ReasonerServer.class);when(server.klabService()).thenReturn(service);
    var controller=new ReasonerController();ReflectionTestUtils.setField(controller,"reasoner",server);
    var scope=mock(ContextScope.class);var authorization=mock(EngineAuthorization.class);when(authorization.getScope()).thenReturn(scope);
    var mvc=MockMvcBuilders.standaloneSetup(controller).addFilters(new SemanticCycleRequestFilter())
        .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
    var binding=new SemanticCycleBinding(SemanticCycleBinding.VERSION,"reasoner","session",7,"fingerprint","opaque");
    var begin=new SemanticCycleRequest("runtime","context","cycle",null);
    var begun=new SemanticCycleBeginResult(SemanticCycleBeginResult.Status.BOUND,binding,null);
    when(service.beginSemanticCycleResult("runtime","context","cycle",scope)).thenReturn(begun);
    var response=mvc.perform(post(ServicesAPI.REASONER.SEMANTIC_CYCLE_BEGIN).principal(authorization)
        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(begin))).andExpect(status().isOk()).andReturn();
    assertEquals(begun,mapper.readValue(response.getResponse().getContentAsByteArray(),SemanticCycleBeginResult.class));
    var owner=new SemanticCycleRequest("runtime","context","cycle",binding);
    var invocation=new SemanticCycleInvocation(owner,"resolveConcept",List.of(String.class.getName()),List.of("\"test:Tree\""));
    when(service.invokeSemanticCycle(eq(invocation),same(scope))).thenReturn(new SemanticCycleResult("null",invocation.arguments()));
    mvc.perform(post(ServicesAPI.REASONER.SEMANTIC_CYCLE_INVOKE).principal(authorization).contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsBytes(invocation))).andExpect(status().isOk());
    when(service.invokeSemanticCycle(eq(invocation),same(scope))).thenThrow(new SecurityException("closed"));
    mvc.perform(post(ServicesAPI.REASONER.SEMANTIC_CYCLE_INVOKE).principal(authorization).contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsBytes(invocation))).andExpect(status().isForbidden());
    mvc.perform(post(ServicesAPI.REASONER.SEMANTIC_CYCLE_INVOKE).contentType(MediaType.APPLICATION_JSON)
        .header(ServicesAPI.RESOLUTION_CYCLE_HEADER,"invalid envelope")
        .content(mapper.writeValueAsBytes(invocation))).andExpect(status().isForbidden());
    mvc.perform(post(ServicesAPI.REASONER.RESOLVE_CONCEPT).principal(authorization).contentType(MediaType.TEXT_PLAIN)
        .header(ServicesAPI.RESOLUTION_CYCLE_HEADER,"invalid envelope").content("test:Tree"))
        .andExpect(status().isConflict());
    mvc.perform(post(ServicesAPI.REASONER.SEMANTIC_CYCLE_RELEASE).principal(authorization).contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsBytes(owner))).andExpect(status().isOk());
    verify(service).releaseSemanticCycle(binding,"runtime","context","cycle",scope);
    verify(service,never()).resolveConcept(anyString());
  }
}

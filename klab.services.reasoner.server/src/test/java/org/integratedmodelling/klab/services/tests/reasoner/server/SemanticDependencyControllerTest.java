package org.integratedmodelling.klab.services.tests.reasoner.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.List;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.reasoner.*;
import org.integratedmodelling.klab.services.reasoner.controllers.ReasonerController;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SemanticDependencyControllerTest {
  @Test void exactNormalizedSemanticClosureRoutesWithActualAuthorizationAndNoCallerCompletenessFlag() throws Exception {
    var mapper=JacksonConfiguration.newObjectMapper();var service=mock(ReasonerService.class);
    var server=mock(ReasonerServer.class);when(server.klabService()).thenReturn(service);
    var controller=new ReasonerController();ReflectionTestUtils.setField(controller,"reasoner",server);
    var scope=mock(ContextScope.class);var authorization=mock(EngineAuthorization.class);when(authorization.getScope()).thenReturn(scope);
    var mvc=MockMvcBuilders.standaloneSetup(controller).setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
    String base="semantic-content-v2:"+"a".repeat(64);
    var request=new SemanticDependencyRequest("deployment",7,base,List.of("w:Tree","w:Code"));
    when(service.semanticDependencies(any(),same(scope))).thenAnswer(inv->{
      SemanticDependencyRequest actual=inv.getArgument(0);assertEquals(request,actual);
      return new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,SemanticDependencyWitness.Status.COMPLETE,
          "deployment",7,base,actual.inputHash(),List.of(),null);
    });
    var result=mvc.perform(post(ServicesAPI.REASONER.SEMANTIC_DEPENDENCIES).principal(authorization)
        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(request)))
        .andExpect(status().isOk()).andReturn();
    var witness=mapper.readValue(result.getResponse().getContentAsByteArray(),SemanticDependencyWitness.class);
    assertTrue(witness.completeAt(System.currentTimeMillis()));assertEquals(request.inputHash(),witness.inputHash());
    mvc.perform(post(ServicesAPI.REASONER.SEMANTIC_DEPENDENCIES).contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsBytes(request))).andExpect(status().isForbidden());
    verify(service,times(1)).semanticDependencies(any(),same(scope));
  }
}

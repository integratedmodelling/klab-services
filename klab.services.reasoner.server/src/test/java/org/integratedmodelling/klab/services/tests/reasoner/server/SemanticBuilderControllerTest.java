package org.integratedmodelling.klab.services.tests.reasoner.server;

import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.reasoner.ReasonerServer;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.reasoner.controllers.ReasonerController;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SemanticBuilderControllerTest {
  @Test void routesSemanticValidationAndRequiresAuthorization() throws Exception {
    var mapper = JacksonConfiguration.newObjectMapper();
    var service = mock(ReasonerService.class);
    var server = mock(ReasonerServer.class); when(server.klabService()).thenReturn(service);
    var controller = new ReasonerController(); ReflectionTestUtils.setField(controller, "reasoner", server);
    var scope = mock(ContextScope.class);
    var authorization = mock(EngineAuthorization.class); when(authorization.getScope()).thenReturn(scope);
    var mvc = MockMvcBuilders.standaloneSetup(controller)
        .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
    var document = new org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl();
    document.setUrn("test"); document.setSourceCode("namespace test;");
    var request = SemanticValidationRequest.of(document, "1");
    when(service.validateDocument(any(), same(scope))).thenAnswer(inv ->
        SemanticValidationResponse.forRequest(inv.getArgument(0)));
    String body = mapper.writeValueAsString(request);
    mvc.perform(post(ServicesAPI.REASONER.VALIDATE_DOCUMENT).principal(authorization)
        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    mvc.perform(post(ServicesAPI.REASONER.VALIDATE_DOCUMENT)
        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
    mvc.perform(post(ServicesAPI.REASONER.VALIDATE_DOCUMENT).principal(authorization)
        .contentType(MediaType.APPLICATION_JSON).content("{}")) .andExpect(status().isBadRequest());
    verify(service).validateDocument(any(), same(scope));
  }

  @Test void routesPortableBuildersWithAuthorizedScopeAndInterfaceResults() throws Exception {
    var mapper = JacksonConfiguration.newObjectMapper();
    var service = mock(ReasonerService.class);
    var server = mock(ReasonerServer.class);
    when(server.klabService()).thenReturn(service);
    var controller = new ReasonerController();
    ReflectionTestUtils.setField(controller, "reasoner", server);
    var scope = mock(ContextScope.class);
    var authorization = mock(EngineAuthorization.class);
    when(authorization.getScope()).thenReturn(scope);
    var mvc = MockMvcBuilders.standaloneSetup(controller)
        .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
    var concept = new ConceptImpl();
    concept.setUrn("test:Forest");
    concept.setName("Forest");
    concept.setNamespace("test");
    concept.getType().add(SemanticType.ATTRIBUTE);
    var observable = ObservableImpl.promote(concept, null);
    when(service.buildConcept(any(), same(scope))).thenAnswer(inv -> {
      ObservableBuildStrategy request = inv.getArgument(0);
      assertEquals(concept.getUrn(), request.getBaseConcept().getUrn());
      assertEquals(ObservableBuildStrategy.OperationType.WITHOUT_ROLES,
          request.getOperations().getFirst().getType());
      return concept;
    });
    when(service.buildObservable(any(), same(scope))).thenReturn(observable);
    var builder = new ObservableBuildStrategy(concept, scope);
    builder.without(SemanticRole.INHERENT);
    String body = mapper.writeValueAsString(builder);
    var response = mvc.perform(post(ServicesAPI.REASONER.BUILD_CONCEPT)
        .principal(authorization).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertEquals(concept.getUrn(), mapper.readValue(response, Concept.class).getUrn());
    response = mvc.perform(post(ServicesAPI.REASONER.BUILD_OBSERVABLE)
        .principal(authorization).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertEquals(observable.getUrn(), mapper.readValue(response, Observable.class).getUrn());
    verify(service).buildConcept(any(), same(scope));
    verify(service).buildObservable(any(), same(scope));
  }
}

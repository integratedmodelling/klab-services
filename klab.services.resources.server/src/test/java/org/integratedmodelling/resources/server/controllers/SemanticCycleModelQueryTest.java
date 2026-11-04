package org.integratedmodelling.resources.server.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticCycleBinding;
import org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;
import org.integratedmodelling.resources.server.ResourcesServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SemanticCycleModelQueryTest {
  @Test void newEndpointRequiresAuthorizedBoundContextAndModelQueryShape() throws Exception {
    var mapper=JacksonConfiguration.newObjectMapper();var service=mock(ResourcesProvider.class);
    var server=mock(ResourcesServer.class);when(server.klabService()).thenReturn(service);
    var controller=new ResourcesProviderController();ReflectionTestUtils.setField(controller,"resourcesServer",server);
    var mvc=MockMvcBuilders.standaloneSetup(controller).setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
    var scope=mock(ContextScope.class);var authorization=mock(EngineAuthorization.class);when(authorization.getScope()).thenReturn(scope);
    var path=ServicesAPI.RESOURCES.SEMANTIC_CYCLE_MODELS.replace("{knowledgeClass}","MODEL");
    mvc.perform(post(path).param("infoClass",ResourceSet.class.getName()).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(post(path).principal(authorization).param("infoClass",ResourceSet.class.getName()).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());
    when(scope.getResolutionCycleBinding()).thenReturn(new ResolutionCycleBinding(1,"cycle","context","runtime",true,
        new SemanticCycleBinding(SemanticCycleBinding.VERSION,"reasoner","session",7,"recipe","lease")));
    mvc.perform(post(path).principal(authorization).param("infoClass",ResourceSet.class.getName()).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
    mvc.perform(post(path.replace("MODEL","NAMESPACE")).principal(authorization).param("infoClass",ResourceSet.class.getName())
        .contentType(MediaType.APPLICATION_JSON).content("{}")) .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
    var semantics=new org.integratedmodelling.common.knowledge.ConceptImpl();semantics.setUrn("test:Height");
    var observable=new ObservableImpl();observable.setSemantics(semantics);observable.setUrn("test:Height");
    var query=Parameters.<String>create("observable",observable,"geometry",Geometry.create("1"));
    when(service.query(any(),eq(KnowledgeClass.MODEL),eq(ResourceSet.class),same(scope))).thenReturn(List.of());
    var result=mvc.perform(post(path).principal(authorization).param("infoClass",ResourceSet.class.getName())
        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(query))).andExpect(status().isOk()).andReturn();
    assertEquals("[]",result.getResponse().getContentAsString());
    verify(service).captureModelQueryView(scope);
    verify(service).query(any(),eq(KnowledgeClass.MODEL),eq(ResourceSet.class),same(scope));
  }
}

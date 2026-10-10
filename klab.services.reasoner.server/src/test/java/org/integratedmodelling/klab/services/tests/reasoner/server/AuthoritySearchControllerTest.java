package org.integratedmodelling.klab.services.tests.reasoner.server;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.reasoner.*;
import org.integratedmodelling.klab.services.reasoner.controllers.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
class AuthoritySearchControllerTest {
  @Test void hierarchyRequiresAuthenticationAndForwardsTheCanonicalCode() throws Exception {
    var service = mock(ReasonerService.class); var server = mock(ReasonerServer.class);
    when(server.klabService()).thenReturn(service);
    var controller = new AuthoritySearchController();
    ReflectionTestUtils.setField(controller, "reasoner", server);
    var mvc = MockMvcBuilders.standaloneSetup(controller).build();
    String body = "{\"authority\":\"CHEM\",\"identity\":\"CID:2244\"}";
    mvc.perform(post(ServicesAPI.REASONER.AUTHORITY_HIERARCHY).contentType("application/json").content(body))
        .andExpect(status().isForbidden());
    verifyNoInteractions(service);
    var scope = mock(ContextScope.class); var authorization = mock(EngineAuthorization.class);
    when(authorization.getScope()).thenReturn(scope);
    mvc.perform(post(ServicesAPI.REASONER.AUTHORITY_HIERARCHY).principal(authorization)
        .contentType("application/json").content(body)).andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"));
    verify(service).resolveAuthorityHierarchy("CHEM", "CID:2244", scope);
  }

  @Test void requiresAuthenticationAndForwardsScopeForSearchAndInsertion() throws Exception {
    var service = mock(ReasonerService.class); var server = mock(ReasonerServer.class);
    when(server.klabService()).thenReturn(service);
    var search = new AuthoritySearchController(); var assist = new AssistController();
    ReflectionTestUtils.setField(search, "reasoner", server); ReflectionTestUtils.setField(assist, "reasoner", server);
    var mvc = MockMvcBuilders.standaloneSetup(search, assist).build();
    var scope = mock(ContextScope.class); var authorization = mock(EngineAuthorization.class);
    when(authorization.getScope()).thenReturn(scope);
    var body = "{\"authority\":\"TAXA\",\"query\":\"tree\",\"offset\":0,\"limit\":10}";
    mvc.perform(post(ServicesAPI.REASONER.AUTHORITY_SEARCH).contentType("application/json").content(body))
        .andExpect(status().isForbidden());
    mvc.perform(post(ServicesAPI.REASONER.SEMANTIC_SEARCH).contentType("application/json").content("{}"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(service);
    when(service.searchAuthority(any(), same(scope))).thenReturn(new AuthoritySearchResponse(
        AuthoritySearchResponse.Status.OK, List.of(), 0, -1, List.of()));
    mvc.perform(post(ServicesAPI.REASONER.AUTHORITY_SEARCH).principal(authorization)
        .contentType("application/json").content(body)).andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"));
    mvc.perform(post(ServicesAPI.REASONER.AUTHORITY_SEARCH).principal(authorization)
        .contentType("application/json").content(body.replace("10}", "101}"))).andExpect(status().isBadRequest());
    when(service.semanticSearch(any(), same(scope))).thenReturn(new SemanticSearchResponse(42, 2));
    mvc.perform(post(ServicesAPI.REASONER.SEMANTIC_SEARCH).principal(authorization).contentType("application/json")
        .content("{\"searchId\":42,\"requestId\":2,\"matchesRequestId\":1,\"searchMode\":\"IDENTITY\",\"authority\":\"TAXA\",\"identityCode\":\"123\"}"))
        .andExpect(status().isOk());
    verify(service).semanticSearch(argThat(r -> r.getSearchMode() == SemanticSearchRequest.Mode.IDENTITY
        && r.getIdentityCode().equals("123") && r.getMatchesRequestId() == 1), same(scope));
  }
}

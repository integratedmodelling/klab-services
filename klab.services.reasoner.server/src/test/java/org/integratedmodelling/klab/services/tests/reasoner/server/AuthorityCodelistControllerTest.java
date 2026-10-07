package org.integratedmodelling.klab.services.tests.reasoner.server;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.reasoner.*;
import org.integratedmodelling.klab.services.reasoner.controllers.AuthorityCodelistController;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
class AuthorityCodelistControllerTest {
  @Test void reviewRequiresAdminAndConflictsRemainConflicts() throws Exception {
    var service = mock(ReasonerService.class); var server = mock(ReasonerServer.class);
    when(server.klabService()).thenReturn(service);
    var controller = new AuthorityCodelistController(); ReflectionTestUtils.setField(controller, "reasoner", server);
    var mvc = MockMvcBuilders.standaloneSetup(controller).build();
    var scope = mock(UserScope.class); var authorization = mock(EngineAuthorization.class);
    when(authorization.getScope()).thenReturn(scope);
    String body = "{\"operation\":\"REVIEW\",\"authority\":\"TAXA\"}";
    mvc.perform(post(ServicesAPI.REASONER.AUTHORITY_CODELISTS).contentType("application/json").content(body))
        .andExpect(status().isForbidden());
    mvc.perform(post(ServicesAPI.REASONER.AUTHORITY_CODELISTS).principal(authorization).contentType("application/json").content(body))
        .andExpect(status().isForbidden());
    for (var operation : List.of("CREATE", "UPDATE", "DELETE")) {
      mvc.perform(post(ServicesAPI.REASONER.AUTHORITY_CODELISTS).principal(authorization)
          .contentType("application/json").content(body.replace("REVIEW", operation)))
          .andExpect(status().isForbidden());
    }
    verifyNoInteractions(service);
    when(authorization.isAdministrator()).thenReturn(true);
    when(service.authorityCodelists(any(), same(scope))).thenThrow(new ConcurrentModificationException("changed"));
    mvc.perform(post(ServicesAPI.REASONER.AUTHORITY_CODELISTS).principal(authorization).contentType("application/json").content(body))
        .andExpect(status().isConflict());
    when(service.authorityCodelists(any(), same(scope))).thenReturn(new AuthorityCodelistResponse(0, Map.of(), List.of()));
    mvc.perform(post(ServicesAPI.REASONER.AUTHORITY_CODELISTS).principal(authorization).contentType("application/json").content(body))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
  }
}

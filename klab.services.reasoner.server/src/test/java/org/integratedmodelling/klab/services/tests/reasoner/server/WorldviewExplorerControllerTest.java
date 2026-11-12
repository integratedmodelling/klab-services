package org.integratedmodelling.klab.services.tests.reasoner.server;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.reasoner.objects.WorldviewExplorerCatalog;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.reasoner.*;
import org.integratedmodelling.klab.services.reasoner.controllers.AssistController;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WorldviewExplorerControllerTest {
  @Test void catalogRequiresAuthorizationAndCannotBeCached() throws Exception {
    var service = mock(ReasonerService.class);
    var server = mock(ReasonerServer.class);
    when(server.klabService()).thenReturn(service);
    var controller = new AssistController();
    ReflectionTestUtils.setField(controller, "reasoner", server);
    var mvc = MockMvcBuilders.standaloneSetup(controller).build();
    mvc.perform(get(ServicesAPI.REASONER.WORLDVIEW_EXPLORER)).andExpect(status().isForbidden());
    verifyNoInteractions(service);
    when(service.worldviewExplorerCatalog()).thenReturn(new WorldviewExplorerCatalog("earth", List.of(), List.of()));
    var authorization = mock(EngineAuthorization.class);
    when(authorization.getScope()).thenReturn(mock(ContextScope.class));
    mvc.perform(get(ServicesAPI.REASONER.WORLDVIEW_EXPLORER).principal(authorization))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.urn").value("earth")).andExpect(jsonPath("$.ontologies").isEmpty());
  }
}

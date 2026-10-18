package org.integratedmodelling.klab.services.application.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.common.services.client.engine.SettingsImpl;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException;
import org.integratedmodelling.klab.services.JobManager;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ComponentAdminControllerTest {

  @Test
  void componentEndpointsSubmitTheCorrespondingSettingAndReturnJobIds() throws Exception {
    var controller = new KlabAdminController();
    var instance = mock(ServiceNetworkedInstance.class);
    var service = mock(BaseService.class);
    var settings = mock(SettingsImpl.class);
    var scope = mock(ServiceUserScope.class);
    var authorization = mock(EngineAuthorization.class);
    var jobs = mock(JobManager.class);
    controller.instance = instance;
    when(instance.klabService()).thenReturn(service);
    when(service.settings()).thenReturn(settings);
    when(authorization.getScope()).thenReturn(scope);
    when(scope.isAuthorized(CRUDOperation.ADMINISTER)).thenReturn(true);
    when(scope.getJobManager()).thenReturn(jobs);
    var mvc = MockMvcBuilders.standaloneSetup(controller).build();
    Map<String, Object> request = Map.of("component", "test.component", "version", "1.0.0");

    for (var setting : new Setting[] {Setting.UPDATE_COMPONENT, Setting.REMOVE_COMPONENT}) {
      when(settings.property2Setting(setting.name())).thenReturn(setting);
      var task = CompletableFuture.completedFuture(
          Map.<String, Object>of("result", false, "message", "refused"));
      when(settings.set(setting, request)).thenReturn(task);
      when(jobs.submit(task, "Setting change: " + setting.name())).thenReturn(42L);
      var endpoint = setting == Setting.UPDATE_COMPONENT
          ? ServicesAPI.ADMIN.UPDATE_COMPONENT : ServicesAPI.ADMIN.REMOVE_COMPONENT;
      var response = mvc.perform(post(endpoint)
              .principal(authorization)
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"component\":\"test.component\",\"version\":\"1.0.0\"}"))
          .andReturn().getResponse();

      assertEquals(200, response.getStatus());
      assertEquals("42", response.getContentAsString());
      verify(settings).set(setting, request);
      verify(jobs).submit(task, "Setting change: " + setting.name());
    }
  }

  @Test
  void componentActionsRequireAdministerPermissionBeforeAccessingTheService() {
    var controller = new KlabAdminController();
    controller.instance = mock(ServiceNetworkedInstance.class);
    var authorization = mock(EngineAuthorization.class);
    when(authorization.getScope()).thenReturn(mock(ServiceUserScope.class));
    var request = Map.<String, Object>of("component", "test.component");

    assertThrows(KlabAuthorizationException.class,
        () -> controller.updateComponent(request, authorization));
    assertThrows(KlabAuthorizationException.class,
        () -> controller.removeComponent(request, authorization));
    verifyNoInteractions(controller.instance);
  }
}

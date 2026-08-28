package org.integratedmodelling.klab.services.runtime.server;

import java.util.List;
import org.integratedmodelling.common.authentication.scope.AbstractServiceDelegatingScope;
import org.integratedmodelling.common.services.ServiceStartupOptions;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.web.WebUiConfiguration;
import org.integratedmodelling.klab.services.runtime.RuntimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.availability.LivenessStateHealthIndicator;
import org.springframework.boot.actuate.availability.ReadinessStateHealthIndicator;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.actuate.system.DiskSpaceHealthIndicator;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

@Component
// TODO remove the argument when all gson dependencies are the same (never)
@EnableAutoConfiguration(
    exclude = {org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration.class})
@ComponentScan(
    basePackages = {
      "org.integratedmodelling.klab.services.application.security",
      "org.integratedmodelling.klab.services.messaging",
      "org.integratedmodelling.klab.services.application.controllers",
      "org.integratedmodelling.klab.services.runtime.server.controllers"
    })
public class RuntimeServer extends ServiceNetworkedInstance<RuntimeService> {

  @Override
  protected void configureWebUi(WebUiConfiguration.Builder dashboard) {
    dashboard
        .subtitle("Digital-twin execution and runtime telemetry")
        .panel(
            "runtime-overview",
            "Runtime workspace",
            "A protected starter panel contributed by the runtime server module.",
            "runtime-overview",
            100,
            true)
        .page(
            "session-monitor",
            "Session monitor",
            "Inspect digital-twin sessions and runtime activity.",
            "runtime-workspace",
            100,
            true);
  }

  @Autowired private HealthContributorRegistry healthContributorRegistry;

  @Override
  protected KlabService.Type serviceType() {
    return KlabService.Type.RUNTIME;
  }

  @Override
  protected List<KlabService.Type> getEssentialServices() {
    /**
     * This runtime gets resolvers and resource services from the observation requests, so does not
     * need its own services besides reasoning.
     *
     * <p>TODO the context request contains the service URLs actually. So all these should be
     * optional only for the runtime to work standalone (but there are issues if these aren't
     * there).
     */
    return List.of(/*
        KlabService.Type.RESOURCES, KlabService.Type.REASONER, KlabService.Type.RESOLVER*/ );
  }

  @Override
  protected List<KlabService.Type> getOperationalServices() {
    return List.of();
  }

  @Override
  protected RuntimeService createPrimaryService(
      ServiceScope serviceScope, ServiceStartupOptions options) {
    var ret = new RuntimeService(serviceScope, options);
    healthContributorRegistry.registerContributor(
        "diskspace",
        new DiskSpaceHealthIndicator(
            ServiceConfiguration.INSTANCE.getDataPath(), DataSize.ofMegabytes(500)));
    healthContributorRegistry.registerContributor(
        "readiness", new ReadinessStateHealthIndicator(this.applicationAvailability));
    healthContributorRegistry.registerContributor(
        "liveness", new LivenessStateHealthIndicator(this.applicationAvailability));

    return ret;
  }

  public static void main(String[] args) {
    ServiceNetworkedInstance.start(
        RuntimeServer.class, ServiceStartupOptions.create(KlabService.Type.RUNTIME, args));
  }
}

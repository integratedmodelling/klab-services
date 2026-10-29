package org.integratedmodelling.klab.services.reasoner;

import java.util.List;
import org.integratedmodelling.common.services.ServiceStartupOptions;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.web.WebUiConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

@Component
// TODO remove the argument when all gson dependencies are the same (never)
@EnableAutoConfiguration(
    exclude = {org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration.class})
@ComponentScan(
    basePackages = {
      "org.integratedmodelling.klab.services.application.security",
      "org.integratedmodelling.klab.services.application.ui",
      "org.integratedmodelling.klab.services.messaging",
      "org.integratedmodelling.klab.services.application.controllers",
      "org.integratedmodelling.klab.services.reasoner.controllers"
    })
public class ReasonerServer extends ServiceNetworkedInstance<ReasonerService> {

  @Override
  protected void configureWebUi(WebUiConfiguration.Builder dashboard) {
    dashboard
        .subtitle("Semantic knowledge and reasoning at a glance")
        .page("authority-codelists", "Authority codelists", "Review community proposals and approved aliases.",
            "authority-codelists", 110, true)
        .panel(
            "reasoner-overview",
            "Reasoner workspace",
            "A starter panel contributed by the reasoner server module.",
            "reasoner-overview",
            100,
            false)
        .page(
            "knowledge-explorer",
            "Knowledge explorer",
            "Full-page semantic knowledge and reasoning tools.",
            "reasoner-workspace",
            100,
            false);
  }

  @Override
  protected KlabService.Type serviceType() {
    return KlabService.Type.REASONER;
  }

  @Override
  protected List<KlabService.Type> getEssentialServices() {
    return List.of(KlabService.Type.RESOURCES);
  }

  @Override
  protected List<KlabService.Type> getOperationalServices() {
    return List.of();
  }

  @Override
  protected ReasonerService createPrimaryService(
      ServiceScope serviceScope, ServiceStartupOptions options) {
    ModuleExtractionTraceFilter.install();
    var service = new ReasonerService(serviceScope, options);
    try {
      var factory = org.integratedmodelling.klab.services.reasoner.internal.SemanticEnvironmentFactory.fromLocalCatalog(
              org.integratedmodelling.klab.configuration.ServiceConfiguration.INSTANCE.getDataPath("semantic-candidates").toPath(),
              org.integratedmodelling.klab.configuration.ServiceConfiguration.INSTANCE.getDataPath("knowledge").toPath(),
              org.integratedmodelling.klab.services.base.BaseService.getConfigurationDirectory(options).toPath());
      // Production ingestion must synchronize providers before compiling the candidate.
      factory.enableAuthorityBootstrap();
      service.enableCandidatePublication(factory);
    } catch (Exception failure) {
      throw new IllegalStateException("Cannot capture local semantic dependencies", failure);
    }
    return service;
  }

  public static void main(String[] args) {
    ServiceNetworkedInstance.start(
        ReasonerServer.class, ServiceStartupOptions.create(KlabService.Type.REASONER, args));
  }
}

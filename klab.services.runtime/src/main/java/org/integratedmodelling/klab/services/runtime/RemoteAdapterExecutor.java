package org.integratedmodelling.klab.services.runtime;

import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.exceptions.KlabServiceAccessException;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.runtime.extension.AdapterDescriptor;
import org.integratedmodelling.klab.data.ClientResourceContextualizer;

import java.util.Map;

public class RemoteAdapterExecutor extends AbstractExecutor
    implements CompiledDataflow.ContextualExecutor {

  private final AdapterDescriptor adapterInfo;
  private Resource resource;
  private ClientResourceContextualizer contextualizer;
  private ResourcesService service = null;

  public RemoteAdapterExecutor(
      CompiledDataflow.CallDescriptors callInfo,
      Observation observation,
      Map<String, Observable> localNames,
      ContextScope scope) {

    super(callInfo, observation, scope, localNames);

    this.resource = callInfo.resource();

    /*
     * TODO - this logic implies that the adapter is on the SAME service for every shard. We
     *  can use a more intelligent logic to determine the service to use in each shard when the
     *  resource is mirrored.
     */
    var service =
        scope.getServices(ResourcesService.class).stream()
            .filter(r -> r.serviceId().equals(this.resource.getServiceId()))
            .findFirst();

    this.adapterInfo =
        service
            .filter(resourcesService -> resource != null)
            .map(
                resourcesService -> {
                  this.service = resourcesService;
                  return resourcesService.retrieveAdapterInfo(resource.getAdapterType(), scope);
                })
            .orElse(null);
  }

  @Override
  protected boolean run(Scheduler.Event event, Storage.Scanner scanner) {

    if (resource == null) {
      cause = new KlabResourceAccessException("Resource not found " + resource.getUrn());
      return false;
    }
    if (service == null || adapterInfo == null) {
      cause = new KlabServiceAccessException("No adapter info for resource " + resource.getUrn());
      return false;
    }
    if (contextualizer == null) {
      var service =
          scope.getServices(ResourcesService.class).stream()
              .filter(r -> r.serviceId().equals(resource.getServiceId()))
              .findFirst();

      if (service.isEmpty()) {
        cause =
            new KlabIllegalStateException("Illegal service ID in resource " + resource.getUrn());
        return false;
      }

      var res = this.resource;
      // TODO validate type chain
      if (adapterInfo.isContextualizing()) {
        res = service.get().contextualizeResource(resource, scanner.shard().getGeometry(), scope);
      }

      // enqueue data extraction from service method TODO pass the scanner and use its geometry
      contextualizer = new ClientResourceContextualizer(service.get(), res, observation);
    }

    // FIXME must use scanner and its geometry
    return contextualizer.contextualize(observation, event, scope);
  }

  @Override
  public boolean validate() {
    // TODO send the validation request to the service
    return true;
  }
}

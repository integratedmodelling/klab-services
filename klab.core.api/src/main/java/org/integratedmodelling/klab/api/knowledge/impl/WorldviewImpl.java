package org.integratedmodelling.klab.api.knowledge.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategyDocument;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.utils.Utils;

public class WorldviewImpl implements Worldview {

  private String urn;
  private Metadata metadata = Metadata.create();
  private List<KimOntology> ontologies = new ArrayList<>();
  private List<KimObservationStrategyDocument> observationStrategies = new ArrayList<>();
  private boolean empty;
  private String worldviewId = Utils.Names.newName("wv");
  private List<Annotation> annotations = new ArrayList<>();
  private List<Notification> notifications = new ArrayList<>();

  @Override
  public String getUrn() {
    return this.urn;
  }

  @Override
  public Metadata getMetadata() {
    return this.metadata;
  }

  @Override
  public List<KimOntology> getOntologies() {
    return this.ontologies;
  }

  @Override
  public Collection<KimObservationStrategyDocument> getObservationStrategies() {
    return this.observationStrategies;
  }

  @Override
  public List<KlabDocument<?>> update(UserScope userScope) {

    var resources =
        Utils.Resources.queryResources(
            userScope,
            ResourcesService.class,
            // FIXME use the worldview ID from the certificate
            service ->
                service.resolve(this.getUrn(), KlabAsset.KnowledgeClass.WORLDVIEW, userScope));

    var result =
        resources.getResults().stream()
            .filter(r -> r.getKnowledgeClass() == KlabAsset.KnowledgeClass.WORLDVIEW)
            .findFirst();

    if (result.isEmpty()) {
      return List.of();
    }

    if (!resources.isEmpty() && !Utils.Notifications.hasErrors(resources.getNotifications())) {

      metadata.putAll(result.get().getMetadata());
      boolean recomputeOrder = false;
      for (var resource : resources.getOntologies()) {
        var existing =
            getOntologies().stream()
                .filter(o -> o.getUrn().equals(resource.getResourceUrn()))
                .findFirst();
        var add = existing.isEmpty();
        var replace =
            existing.isPresent()
                && (existing.get().getVersion().compareTo(resource.getResourceVersion()) < 0
                    || resource.getTimestamp() > existing.get().getLastUpdateTimestamp());

        if (!add && !replace) {
          continue;
        }

        var service =
            userScope
                .findService(
                    ResourcesService.class,
                    resourcesService ->
                        resourcesService.serviceId().equals(resource.getServiceId()))
                .orElse(null);

        if (service == null) {
          if (add) {
            notifications.add(
                Notification.error(
                    "Could not find service providing resource " + resource.getResourceUrn()));
          }
          continue;
        }

        var ontology = service.retrieve(resource.getResourceUrn(), KimOntology.class, userScope);
        if (ontology == null) {
          if (add) {
            notifications.add(
                Notification.error("Could not retrieve ontology " + resource.getResourceUrn()));
          }
          continue;
        }

        if (add) {
          ontologies.add(ontology);
        } else {
          ontologies.set(ontologies.indexOf(existing.get()), ontology);
        }

        recomputeOrder = true;
      }

      for (var resource : resources.getObservationStrategies()) {
        var existing =
            getObservationStrategies().stream()
                .filter(o -> o.getUrn().equals(resource.getResourceUrn()))
                .findFirst();
        var add = existing.isEmpty();
        var replace =
            existing.isPresent()
                && (existing.get().getVersion().compareTo(resource.getResourceVersion()) < 0
                    || resource.getTimestamp() > existing.get().getLastUpdateTimestamp());

        if (!add && !replace) {
          continue;
        }

        var service =
            userScope
                .findService(
                    ResourcesService.class,
                    resourcesService ->
                        resourcesService.serviceId().equals(resource.getServiceId()))
                .orElse(null);

        if (service == null) {
          if (add) {
            notifications.add(
                Notification.error(
                    "Could not find service providing resource " + resource.getResourceUrn()));
          }
          continue;
        }

        var observationStrategyDocument =
            service.retrieve(
                resource.getResourceUrn(), KimObservationStrategyDocument.class, userScope);
        if (observationStrategyDocument == null) {
          if (add) {
            notifications.add(
                Notification.error("Could not retrieve ontology " + resource.getResourceUrn()));
          }
          continue;
        }

        if (add) {
          observationStrategies.add(observationStrategyDocument);
        } else {
          observationStrategies.set(
              observationStrategies.indexOf(existing.get()), observationStrategyDocument);
        }
      }

      if (recomputeOrder) {
        // TODO recompute ontology topological order if anything has changed
      }
    }

    return List.of();
  }

  @Override
  public boolean isEmpty() {
    return this.empty;
  }

  public void setUrn(String urn) {
    this.urn = urn;
  }

  public void setMetadata(Metadata metadata) {
    this.metadata = metadata;
  }

  public void setOntologies(List<KimOntology> ontologies) {
    this.ontologies = ontologies;
  }

  public void setObservationStrategies(List<KimObservationStrategyDocument> observationStrategies) {
    this.observationStrategies = observationStrategies;
  }

  @Override
  public String getWorldviewId() {
    return worldviewId;
  }

  public void setWorldviewId(String worldviewId) {
    this.worldviewId = worldviewId;
  }

  public void setEmpty(boolean empty) {
    this.empty = empty;
  }

  @Override
  public List<Annotation> getAnnotations() {
    return annotations;
  }

  public void setAnnotations(List<Annotation> annotations) {
    this.annotations = annotations;
  }

  @Override
  public List<Notification> getNotifications() {
    return notifications;
  }

  public void setNotifications(List<Notification> notifications) {
    this.notifications = notifications;
  }
}

package org.integratedmodelling.klab.services.resources.persistence;

import java.util.*;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.exceptions.KlabStorageException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Contextualization;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.scale.EnumeratedExtension;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Extent;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Projection;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Shape;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Space;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.lang.kim.KimModel;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.services.resources.persistence.ModelReference.Mediation;

/** Shared domain inference for both SQL and document catalogs. */
final class ModelDescriptorFactory {
  private final ResourcesService resourceService;
  ModelDescriptorFactory(ResourcesService resourceService) {
    this.resourceService = resourceService;
  }
  public Collection<ModelReference> inferModels(KimModel model, Scope monitor) {

    List<ModelReference> ret = new ArrayList<>();

    // happens in error
    if (model.getObservables().isEmpty() || model.getObservables().getFirst() == null) {
      return ret;
    }

    boolean isInstantiator = model.getObservables().getFirst().getSemantics().isCollective();

    Observable mainObservable =
        monitor
            .getService(Reasoner.class)
            .resolveObservable(model.getObservables().getFirst().getUrn());

    ret.addAll(getModelDescriptors(model, monitor));

    if (!ret.isEmpty()) {

      if (mainObservable.is(SemanticType.PROCESS)) {
        var qualities = new ArrayList<Observable>();
        for (var declared : model.getObservables().subList(1, model.getObservables().size()))
          qualities.add(monitor.getService(Reasoner.class).resolveObservable(declared.getUrn()));
        for (var dependency : model.getDependencies())
          qualities.add(monitor.getService(Reasoner.class).resolveObservable(dependency.getUrn()));
        for (var change : org.integratedmodelling.klab.runtime.language.OccurrentSemantics.changes(
            mainObservable, qualities, monitor)) {
          var descriptor = ret.getFirst().copy();
          descriptor.setObservable(change.getUrn());
          descriptor.setObservableConcept(change.getSemantics());
          descriptor.setObservationType(change.getContextualization().name());
          descriptor.setPrimaryObservable(false);
          ret.add(descriptor);
        }
      }

      for (KimObservable attr :
          model.getObservables().stream().filter(o -> o.getFormalName() != null).toList()) {

        Observable observable = monitor.getService(Reasoner.class).resolveObservable(attr.getUrn());

        /*
         * attribute type must have inherent type added if it's an instantiated quality
         * (from an instantiator or as a secondary observable of a resolver with explicit,
         * specialized inherency)
         */
        Concept type = observable.getSemantics();
        if (isInstantiator) {
          Concept context = monitor.getService(Reasoner.class).inherent(type);
          if (context == null
              || !monitor.getService(Reasoner.class).is(context, mainObservable.getSemantics())) {
            type = observable.builder(monitor).of(mainObservable.getSemantics()).buildConcept();
          }
        }
        ModelReference m = ret.get(0).copy();
        m.setObservable(type.getUrn());
        m.setObservableConcept(type);
        m.setObservationType(observable.getContextualization().name());
        m.setDereifyingAttribute(attr.getFormalName());
        m.setMediation(Mediation.DEREIFY_QUALITY);
        m.setPrimaryObservable(!isInstantiator);
        m.setScope(model.getScope());
        ret.add(m);
      }

      if (isInstantiator) {
        // TODO add presence model for main observable type and
        // dereifying models for all mandatory attributes of observable in context
      }
    }

    return ret;
  }

  private Collection<ModelReference> getModelDescriptors(KimModel model, Scope monitor) {

    List<ModelReference> ret = new ArrayList<>();
    Coverage coverage =
        monitor instanceof UserScope userScope
            ? resourceService.info(
                model.getUrn(), KlabAsset.KnowledgeClass.MODEL, Coverage.class, userScope)
            : null;
    Scale scale = coverage == null ? null : GeometryRepository.INSTANCE.scale(coverage);

    Shape spaceExtent = null;
    Time timeExtent = null;
    long spaceMultiplicity = -1;
    long timeMultiplicity = -1;
    long scaleMultiplicity = 1;
    long timeStart = -1;
    long timeEnd = -1;
    boolean isSpatial = false;
    boolean isTemporal = false;
    String enumeratedSpaceDomain = null;
    String enumeratedSpaceLocation = null;
    Project project =
        monitor instanceof UserScope userScope
            ? resourceService.retrieve(model.getProjectName(), Project.class, userScope)
            : null;
    KimNamespace namespace =
        monitor instanceof UserScope userScope
            ? resourceService.retrieve(model.getNamespace(), KimNamespace.class, userScope)
            : null;

    if (resourceService instanceof org.integratedmodelling.klab.services.resources.ResourcesProvider provider) {
      // Startup indexing runs in a ServiceScope, before a user request exists.
      project = provider.retrieveProject(model.getProjectName(), monitor);
      namespace = provider.retrieveNamespace(model.getNamespace(), monitor);
    }
    if (namespace == null) throw new KlabStorageException("Missing namespace for model " + model.getUrn());
    if (model.getProjectName() != null && project == null)
      throw new KlabStorageException("Missing project for model " + model.getUrn());

    if (scale != null) {

      scaleMultiplicity = scale.size();

      /*
       * If the runtime allows, resolve any enumeration to physical extents
       */
      Space space = resolveEnumeratedExtensions(scale.getSpace());
      Time time = resolveEnumeratedExtensions(scale.getTime());

      if (space /* still */ instanceof EnumeratedExtension) {
        /*
         * TODO handle the enumerated extension
         */
        throw new KlabUnimplementedException("enumerated extension");
        // Pair<String, String> defs = ((EnumeratedExtension)
        // scale.getSpace()).getExtension();
        // enumeratedSpaceDomain = defs.getFirst();
        // enumeratedSpaceLocation = defs.getSecond();
      } else if (space != null) {
        spaceExtent = space.getGeometricShape();
        // may be null when we just say 'over space'.
        if (spaceExtent != null) {
          spaceExtent = spaceExtent.transform(Projection.getLatLon());
          spaceMultiplicity = space.size();
        }
        isSpatial = true;
      }

      if (time != null) {
        if (time /* still */ instanceof EnumeratedExtension) {
          // TODO
          throw new KlabUnimplementedException("enumerated extension");
        } else {
          timeExtent = time.collapsed();
          if (timeExtent != null) {
            if (timeExtent.getStart() != null) {
              timeStart = timeExtent.getStart().getMilliseconds();
            }
            if (timeExtent.getEnd() != null) {
              timeEnd = timeExtent.getEnd().getMilliseconds();
            }
          }
        }
        timeMultiplicity = time.size();
        isTemporal = true;
      }
    }

    boolean first = true;
    Observable main = null;
    for (KimObservable kobs : model.getObservables()) {

      Observable oobs = monitor.getService(Reasoner.class).resolveObservable(kobs.getUrn());

      if (first) {
        main = oobs;
      }

      boolean isInstantiator =
          !model.getObservables().isEmpty()
              && model.getObservables().getFirst().getSemantics().isCollective();

      for (Observable obs : unpackObservables(oobs, main, first, monitor)) {

        ModelReference m = new ModelReference();

        m.setName(model.getUrn());
        m.setNamespaceId(model.getNamespace());
        m.setProjectId(model.getProjectName());

        if (project != null) {
          m.setPermissions(project.getManifest().getPrivileges());
        }

        m.setTimeEnd(timeEnd);
        m.setTimeStart(timeStart);
        m.setTimeMultiplicity(timeMultiplicity);
        m.setSpaceMultiplicity(spaceMultiplicity);
        m.setScaleMultiplicity(scaleMultiplicity);
        m.setSpatial(isSpatial);
        m.setTemporal(isTemporal);
        m.setShape(spaceExtent);
        m.setEnumeratedSpaceDomain(enumeratedSpaceDomain);
        m.setEnumeratedSpaceLocation(enumeratedSpaceLocation);

        m.setObservable(obs.getUrn());
        m.setObservationType(
            obs.getContextualization() == null
                ? Contextualization.VOID.name()
                : obs.getContextualization().name());
        m.setObservableConcept(obs.getSemantics());
        m.setScope(model.getScope());
        m.setInScenario(namespace.isScenario());
        m.setReification(isInstantiator);
        m.setResolved(model.getDependencies().isEmpty());
        m.setHasDirectData(
            m.isResolved()
                && model.getObservables().getFirst().getSemantics().is(SemanticType.QUALITY));
        m.setHasDirectObjects(
            m.isResolved()
                && model.getObservables().getFirst().getSemantics().is(SemanticType.COUNTABLE));

        m.setMinSpatialScaleFactor(
            model.getMetadata().get(Metadata.IM_MIN_SPATIAL_SCALE, Space.MIN_SCALE_RANK));
        m.setMaxSpatialScaleFactor(
            model.getMetadata().get(Metadata.IM_MAX_SPATIAL_SCALE, Space.MAX_SCALE_RANK));
        m.setMinTimeScaleFactor(
            model.getMetadata().get(Metadata.IM_MIN_TEMPORAL_SCALE, Time.MIN_SCALE_RANK));
        m.setMaxTimeScaleFactor(
            model.getMetadata().get(Metadata.IM_MAX_TEMPORAL_SCALE, Time.MAX_SCALE_RANK));
        m.setTimestamp(namespace.getLastUpdateTimestamp());
        m.setPrimaryObservable(first);

        // if (first && obs.isSpecialized()) {
        // m.setSpecializedObservable(true);
        // }

        first = false;

        m.setMetadata(translateMetadata(model.getMetadata()));

        ret.add(m);
      }

      /*
       * For now just disable additional observables in instantiators and use their attribute
       * observers upstream. We may do different things here:
       *
       * 0. keep ignoring them 1. keep them all, contextualized to the instantiated
       * observable; 2. keep only the non-statically contextualized ones (w/o the value)
       *
       */
      if (isInstantiator) {
        break;
      }
    }
    return ret;
  }

  @SuppressWarnings("unchecked")
  private <T extends Extent<T>> T resolveEnumeratedExtensions(T extent) {
    if (extent instanceof EnumeratedExtension) {
      return (T) ((EnumeratedExtension<?>) extent).getPhysicalExtent();
    }
    return extent;
  }

  private List<Observable> unpackObservables(
      Observable oobs, Observable main, boolean first, Scope monitor) {

    List<Observable> ret = new ArrayList<>();
    if (!first && !main.is(SemanticType.PROCESS) && !main.is(SemanticType.EVENT)) {
      /*
       * Subsequent observables inherit any explicit specialization in the main observable of a
       * model
       */
      Concept specialized = monitor.getService(Reasoner.class).directInherent(main.getSemantics());
      Concept oobsContext = monitor.getService(Reasoner.class).inherent(oobs);
      if (specialized != null
          && (oobsContext == null
              || !monitor.getService(Reasoner.class).is(oobsContext, specialized))) {
        oobs = oobs.builder(monitor).of(specialized).buildObservable();
      }
    }
    ret.add(oobs);
    return ret;
  }

  private static Map<String, String> translateMetadata(Metadata metadata) {
    Map<String, String> ret = new HashMap<>();
    for (String key : metadata.keySet()) {
      ret.put(key, metadata.get(key) == null ? "null" : metadata.get(key).toString());
    }
    return ret;
  }

}

package org.integratedmodelling.klab.api.knowledge;

import java.io.Serializable;
import java.util.Collection;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.ResourcesService;

/**
 * All k.LAB assets have a URN, a version, metadata and possibly annotations. They are
 * <em>syntactic</em> resources, created by users through k.IM and k.Actors language specifications.
 * Assets are managed by the {@link ResourcesService}, which handles review, versioning and
 * dependencies but knows no semantics; they are retrieved and promoted to {@link Knowledge} in
 * other services to become operational. The needed assets are transferred to the {@link Reasoner}
 * (yielding {@link Concept}s and {@link Observable}s) and to the {@link Resolver} (yielding {@link
 * Model}s to play their role within the k.LAB ecosystem.
 *
 * @author Ferd
 */
public interface KlabAsset extends Serializable {

  enum KnowledgeClass {
    CONCEPT,
    OBSERVABLE,
    MODEL,
    DEFINITION,
    RESOURCE,
    NAMESPACE,
    BEHAVIOR,
    SCRIPT,
    TESTCASE,
    APPLICATION,
    ONTOLOGY,
    OBSERVATION_STRATEGY,
    OBSERVATION_STRATEGY_DOCUMENT,
    COMPONENT,
    PROJECT,
    WORLDVIEW,
    WORKSPACE,
    CONCEPT_STATEMENT,
    SERVICE_IMPLEMENTATION,
    OBSERVATION,
    /**
     * This is used to tag a variety of informational assets, such as adapter descriptors, reports,
     * language info, etc. When this is used, more information is always supplied so that the actual
     * informational object wanted can be identified and processed correctly.
     */
    INFORMATION;

    public Class<? extends KlabAsset> getAssetClass() {
      return switch (this) {
        case RESOURCE -> Resource.class;
        case NAMESPACE -> KimNamespace.class;
        case BEHAVIOR, SCRIPT, TESTCASE, APPLICATION -> KActorsBehavior.class;
        case ONTOLOGY -> KimOntology.class;
        case OBSERVATION_STRATEGY_DOCUMENT -> KimObservationStrategyDocument.class;
        case PROJECT -> Project.class;
        case WORLDVIEW -> Worldview.class;
        case WORKSPACE -> Workspace.class;
        case OBSERVABLE -> KimObservable.class;
        case CONCEPT -> KimConcept.class;
        case CONCEPT_STATEMENT -> KimConceptStatement.class;
        case OBSERVATION_STRATEGY -> KimObservationStrategy.class;
        case MODEL -> KimModel.class;
        case DEFINITION -> KimSymbolDefinition.class;
        default ->
            throw new KlabIllegalStateException(
                "Cannot convert  " + this + " into serializable asset class");
      };
    }

    public ProjectStorage.ResourceType getResourceType() {
      return switch (this) {
        case NAMESPACE -> ProjectStorage.ResourceType.MODEL_NAMESPACE;
        case BEHAVIOR -> ProjectStorage.ResourceType.BEHAVIOR;
        case SCRIPT -> ProjectStorage.ResourceType.SCRIPT;
        case TESTCASE -> ProjectStorage.ResourceType.TESTCASE;
        case APPLICATION -> ProjectStorage.ResourceType.APPLICATION;
        case ONTOLOGY -> ProjectStorage.ResourceType.ONTOLOGY;
        case OBSERVATION_STRATEGY_DOCUMENT -> ProjectStorage.ResourceType.STRATEGY;
        default ->
            throw new KlabIllegalStateException(
                "Cannot convert  " + this + " into a project resource type");
      };
    }

    public static KnowledgeClass classify(Class<? extends KlabAsset> cls) {
      if (Concept.class.isAssignableFrom(cls)) {
        return CONCEPT;
      } else if (KimObservable.class.isAssignableFrom(cls)) {
        return OBSERVABLE;
      } else if (KimConceptStatement.class.isAssignableFrom(cls)) {
        return CONCEPT_STATEMENT;
      } else if (KimObservationStrategy.class.isAssignableFrom(cls)) {
        return OBSERVATION_STRATEGY;
      } else if (KimObservationStrategyDocument.class.isAssignableFrom(cls)) {
        return OBSERVATION_STRATEGY_DOCUMENT;
      } else if (KimOntology.class.isAssignableFrom(cls)) {
        return ONTOLOGY;
      } else if (Project.class.isAssignableFrom(cls)) {
        return PROJECT;
      } else if (Worldview.class.isAssignableFrom(cls)) {
        return WORLDVIEW;
      } else if (Workspace.class.isAssignableFrom(cls)) {
        return WORKSPACE;
      } else if (KimSymbolDefinition.class.isAssignableFrom(cls)) {
        return DEFINITION;
      } else if (KimNamespace.class.isAssignableFrom(cls)) {
        return NAMESPACE;
      } else if (KimModel.class.isAssignableFrom(cls)) {
        return MODEL;
      } else if (Resource.class.isAssignableFrom(cls)) {
        return RESOURCE;
      } else if (KActorsBehavior.class.isAssignableFrom(cls)) {
        return BEHAVIOR;
      } else {
        throw new KlabUnimplementedException("Classification of asset class " + cls);
      }
    }
  }

  static KnowledgeClass classify(KlabAsset asset) {
    return switch (asset) {
      case KimConcept c -> KnowledgeClass.CONCEPT;
      case KimConceptStatement c -> KnowledgeClass.CONCEPT_STATEMENT;
      case KimObservationStrategy c -> KnowledgeClass.OBSERVATION_STRATEGY;
      case KimObservable c -> KnowledgeClass.OBSERVABLE;
      case KimOntology o -> KnowledgeClass.ONTOLOGY;
      case Project p -> KnowledgeClass.PROJECT;
      case Worldview w -> KnowledgeClass.WORLDVIEW;
      case Workspace w -> KnowledgeClass.WORKSPACE;
      case Observation w -> KnowledgeClass.OBSERVATION;
      case KimSymbolDefinition sd -> KnowledgeClass.DEFINITION;
      case KimObservationStrategyDocument s -> KnowledgeClass.OBSERVATION_STRATEGY_DOCUMENT;
      case KimNamespace n -> KnowledgeClass.NAMESPACE;
      case KimModel model -> KnowledgeClass.MODEL;
      case Resource resource -> KnowledgeClass.RESOURCE;
      case KActorsBehavior behavior ->
          switch (behavior.getBehaviorType()) {
            case BEHAVIOR, TASK, USER, TRAIT, LIBRARY -> KnowledgeClass.BEHAVIOR;
            case APP -> KnowledgeClass.APPLICATION;
            case UNITTEST -> KnowledgeClass.TESTCASE;
            case COMPONENT -> KnowledgeClass.COMPONENT;
            case SCRIPT -> KnowledgeClass.SCRIPT;
          };
      default -> throw new KlabUnimplementedException("Classification of asset " + asset);
    };
  }

  /**
   * Anything that represents knowledge must return a stable, unique identifier that can be resolved
   * back to the original or to an identical object. Only {@link Resource} must use proper URN
   * syntax; for other types of knowledge may use expressions or paths.
   *
   * @return the unique identifier that specifies this.
   */
  String getUrn();

  /**
   * Never null, possibly empty.
   *
   * @return
   */
  Metadata getMetadata();

  /**
   * All the annotations proceeding from the k.IM lineage of this artifact (from the model that
   * produced it, the concepts it incarnates, etc.). Never null, possibly empty.
   *
   * <p>When artifacts are persisted, these may or may not be preserved.
   *
   * @return k.IM annotations in the lineage of this artifact.
   */
  Collection<Annotation> getAnnotations();
}

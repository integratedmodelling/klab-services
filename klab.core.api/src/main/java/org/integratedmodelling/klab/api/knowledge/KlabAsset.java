package org.integratedmodelling.klab.api.knowledge;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.ResourcesService;

import java.io.Serializable;

/**
 * All k.LAB assets have a URN, a version, metadata and possibly annotations. They are
 * <em>syntactic</em> resources, created by users through k.IM and k.Actors
 * language specifications. Assets are managed by the {@link ResourcesService}, which handles review,
 * versioning and dependencies but knows no semantics; they are retrieved and promoted to {@link Knowledge} in
 * other services to become operational. The needed assets are transferred to the {@link Reasoner} (yielding
 * {@link Concept}s and {@link Observable}s) and to the {@link Resolver} (yielding {@link Model}s and
 * {@link Instance}s) to play their role within the k.LAB ecosystem.
 *
 * @author Ferd
 */
public interface KlabAsset extends Serializable {

    public enum KnowledgeClass {
        CONCEPT, OBSERVABLE, MODEL, DEFINITION, INSTANCE, RESOURCE, NAMESPACE, BEHAVIOR, SCRIPT, TESTCASE,
        APPLICATION, ONTOLOGY, OBSERVATION_STRATEGY, OBSERVATION_STRATEGY_DOCUMENT,
        COMPONENT, PROJECT, WORLDVIEW
    }

    public static KnowledgeClass classify(KlabAsset document) {
        return switch(document) {
            case KimConcept c -> KnowledgeClass.CONCEPT;
            case KimObservable c -> KnowledgeClass.OBSERVABLE;
            case KimOntology o -> KnowledgeClass.ONTOLOGY;
            case KimObservationStrategyDocument s -> KnowledgeClass.OBSERVATION_STRATEGY_DOCUMENT;
            case KimNamespace n -> KnowledgeClass.NAMESPACE;
            case KActorsBehavior behavior -> switch(behavior.getType()) {
                case BEHAVIOR, TASK, USER, TRAITS -> KnowledgeClass.BEHAVIOR;
                case APP -> KnowledgeClass.APPLICATION;
                case UNITTEST -> KnowledgeClass.TESTCASE;
                case COMPONENT -> KnowledgeClass.COMPONENT;
                case SCRIPT -> KnowledgeClass.SCRIPT;
            };
            default -> throw new KlabUnimplementedException("Classification of asset " + document);
        };
    }

    /**
     * Anything that represents knowledge must return a stable, unique identifier that can be resolved back to
     * the original or to an identical object. Only {@link Resource} must use proper URN syntax; for other
     * types of knowledge may use expressions or paths.
     *
     * @return the unique identifier that specifies this.
     */
    public String getUrn();

    /**
     * Never null, possibly empty.
     *
     * @return
     */
    Metadata getMetadata();
}

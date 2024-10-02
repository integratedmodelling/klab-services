package org.integratedmodelling.klab.api.data;

import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.provenance.Plan;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

/**
 * A runtime asset is anything that can be part of the {@link KnowledgeGraph} managed by the
 * {@link org.integratedmodelling.klab.api.digitaltwin.DigitalTwin}. Runtime assets are the nodes in the
 * knowledge graph, which guarantees the consistency of all assets and their connection to provenance.
 * <p>
 * For now this is just a tag interface. It will contain methods to subscribe to events relative to the asset
 * through the messaging subsystem.
 */
public interface RuntimeAsset {

    // TODO listeners and subscriptions mechanism

    enum Type {

        OBSERVATION(Observation.class),
        ACTUATOR(Actuator.class),
        DATAFLOW(Dataflow.class),
        PROVENANCE(Provenance.class),
        ACTIVITY(Activity.class),
        PLAN(Plan.class),
        AGENT(Agent.class),
        ARTIFACT(Storage.class);

        public final Class<? extends RuntimeAsset> assetClass;

        private Type(Class<? extends RuntimeAsset> assetClass) {
            this.assetClass = assetClass;
        }

        public static <T extends RuntimeAsset> Type forClass(Class<T> assetClass) {
            if (Observation.class.isAssignableFrom(assetClass)) {
                return OBSERVATION;
            }
            if (Dataflow.class.isAssignableFrom(assetClass)) {
                return DATAFLOW;
            }
            if (Actuator.class.isAssignableFrom(assetClass)) {
                return ACTUATOR;
            }
            if (Provenance.class.isAssignableFrom(assetClass)) {
                return PROVENANCE;
            }
            if (Activity.class.isAssignableFrom(assetClass)) {
                return ACTIVITY;
            }
            if (Plan.class.isAssignableFrom(assetClass)) {
                return PLAN;
            }
            if (Agent.class.isAssignableFrom(assetClass)) {
                return AGENT;
            }
            if (Artifact.class.isAssignableFrom(assetClass) || Storage.class.isAssignableFrom(assetClass)) {
                return ARTIFACT;
            }
            throw new KlabIllegalArgumentException("No runtime asset class for " + assetClass);
        }
    }

    Type classify();
}

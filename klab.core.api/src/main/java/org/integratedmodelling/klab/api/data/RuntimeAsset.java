package org.integratedmodelling.klab.api.data;

import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
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
 * A runtime asset is anything that can be part of the {@link KnowledgeGraph} managed by the {@link
 * org.integratedmodelling.klab.api.digitaltwin.DigitalTwin}. Runtime assets are the nodes in the
 * knowledge graph, which guarantees the consistency of all assets and their connection to
 * provenance. We index the nodes with unique long IDs managed by the KG implementation.
 *
 * <p>For now this is little more than a tag interface. It will contain methods to subscribe to
 * events relative to the asset through the messaging subsystem.
 */
public interface RuntimeAsset {

  ContextAsset CONTEXT_ASSET = new ContextAsset();
  ProvenanceAsset PROVENANCE_ASSET = new ProvenanceAsset();
  DataflowAsset DATAFLOW_ASSET = new DataflowAsset();
  long CONTEXT_ID = 1L;

  /** The status of an asset, which may be added to the metadata using the "status" property. */
  enum Status {
    UNRESOLVED,
    CONTEXTUALIZED,
    CORRUPTED,
    DELETED,
    ACTIVE
  }

  enum Type {
    OBSERVATION(Observation.class),
    ACTUATOR(Actuator.class),
    CONTEXT(DigitalTwin.class),
    DATAFLOW(Dataflow.class),
    PROVENANCE(Provenance.class),
    ACTIVITY(Activity.class),
    PLAN(Plan.class),
    AGENT(Agent.class),
    ARTIFACT(Storage.class),
    DATA(Storage.Buffer.class),
    LINK(KnowledgeGraph.Link.class);

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
      if (KnowledgeGraph.Link.class.isAssignableFrom(assetClass)) {
        return LINK;
      }
      if (Artifact.class.isAssignableFrom(assetClass)
          || Storage.class.isAssignableFrom(assetClass)) {
        return ARTIFACT;
      }
      throw new KlabIllegalArgumentException("No runtime asset class for " + assetClass);
    }
  }

  /**
   * The primary ID is assigned only upon insertion in the knowledge graph and is stored in it. This
   * means that it is unique and persistent, but also that it is not guaranteed to be assigned when
   * an asset is first created, for example before a {@link DigitalTwin.Transaction} is started. If
   * an object must be tracked from its creation to the end of its lifetime in RAM, use the {@link
   * #getTransientId()} instead.
   *
   * @return the primary ID of this object. Will be -1 if the object has not yet been inserted into
   *     the knowledge graph.
   */
  long getId();

  /**
   * The transientId is assigned on creation but is not stored in the knowledge graph. It is used to
   * track the lifetime of an object only from the time of creation to the time of last use. When
   * retrieved from the knowledge graph, the objectId will be different from that of the object that
   * was stored.
   *
   * <p>The transient ID differs from the simple object hash as it is transmitted through serialized
   * objects and can be used to track ownership when objects are created on another service.
   *
   * @return the transient ID of this object
   */
  long getTransientId();

  Type classify();

  class ContextAsset implements RuntimeAsset {

    @Override
    public long getId() {
      return -1000;
    }

    @Override
    public long getTransientId() {
      return -1000;
    }

    @Override
    public Type classify() {
      return Type.CONTEXT;
    }
  }

  class ProvenanceAsset implements RuntimeAsset {

    @Override
    public long getId() {
      return -1001;
    }

    @Override
    public long getTransientId() {
      return -1001;
    }

    @Override
    public Type classify() {
      return Type.PROVENANCE;
    }
  }

  class DataflowAsset implements RuntimeAsset {

    @Override
    public long getId() {
      return -1002;
    }

    @Override
    public long getTransientId() {
      return -1002;
    }

    @Override
    public Type classify() {
      return Type.DATAFLOW;
    }
  }
}

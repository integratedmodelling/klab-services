package org.integratedmodelling.klab.api.data;

import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Cohort;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
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
 *
 * <p>TODO this should extend KlabAsset
 */
public interface RuntimeAsset /*extends KlabAsset*/ {

  long CONTEXT_ASSET_ID = -1000;
  long PROVENANCE_ASSET_ID = -1001;
  long DATAFLOW_ASSET_ID = -1002;

  ContextAsset CONTEXT_ASSET = new ContextAsset();
  ProvenanceAsset PROVENANCE_ASSET = new ProvenanceAsset();
  DataflowAsset DATAFLOW_ASSET = new DataflowAsset();

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
    DATA(Storage.Shard.class),
    COHORT(Cohort.class),
    LINK(KnowledgeGraph.Link.class);

    public final Class<? extends RuntimeAsset> assetClass;

    Type(Class<? extends RuntimeAsset> assetClass) {
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
      //      if (Artifact.class.isAssignableFrom(assetClass)
      //          || Storage.class.isAssignableFrom(assetClass)) {
      //        return ARTIFACT;
      //      }
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
   * Also only available after insertion into the knowledge graph, -1 otherwise. Used at client side
   * when the graph must be reconstructed piecewise but efficiently, and the parent observation may
   * be already cached at client side.
   *
   * <p>The parent ID of the CONTEXT_ASSET is always 0 and nothing else should have it.
   *
   * @return the primary ID of the parent observation, or -1 if the object has not yet been inserted
   *     into the knowledge graph. Root-level objects will contain the fixed IDs of the
   *     CONTEXT_ASSET, DATAFLOW_ASSET or PROVENANCE_ASSET.
   * @return
   */
  long getParentId();

  /**
   * The transientId is assigned on creation but is not stored in the knowledge graph. It is used to
   * track the lifetime of an object only from the time of creation to the end of the {@link
   * DigitalTwin.Transaction} in which it is created. When retrieved from the knowledge graph, the
   * objectId will be different from that of the object that was stored.
   *
   * <p>The transient ID differs from the simple object hash as it is transmitted through serialized
   * objects and can be used to track ownership when objects are created on another service.
   *
   * @return the transient ID of this object
   */
  long getTransientId();

  /**
   * When a new child is added, this is increased and maintained at service side. At client side,
   * it's used as a invalidation flag: if < 0, the system knows that the asset has been modified and
   * its child count must be reassessed. If greater than 0, the system knows that the asset has
   * children in the client-side knowledge graph and can use that information for visualization.
   *
   * @return
   */
  int getChildrenCount();

  /**
   * Get the transient ID of the parent asset if any exists, 0 otherwise. Used to reconstruct a
   * client-side knowledge graph without having to wait for finalization of the operations that
   * created the asset.
   *
   * @return
   */
  long getParentTransientId();

  Type classify();

  class ContextAsset implements RuntimeAsset {

    @Override
    public long getId() {
      return CONTEXT_ASSET_ID;
    }

    @Override
    public long getParentId() {
      return 0;
    }

    @Override
    public long getTransientId() {
      return -1000;
    }

    @Override
    public int getChildrenCount() {
      return -1;
    }

    @Override
    public long getParentTransientId() {
      return 0;
    }

    @Override
    public Type classify() {
      return Type.CONTEXT;
    }
  }

  class ProvenanceAsset implements RuntimeAsset {

    @Override
    public long getId() {
      return PROVENANCE_ASSET_ID;
    }

    @Override
    public long getParentId() {
      return -1000;
    }

    @Override
    public long getTransientId() {
      return -1001;
    }

    @Override
    public int getChildrenCount() {
      return -1;
    }

    @Override
    public long getParentTransientId() {
      return -1000;
    }

    @Override
    public Type classify() {
      return Type.PROVENANCE;
    }
  }

  class DataflowAsset implements RuntimeAsset {

    @Override
    public long getId() {
      return DATAFLOW_ASSET_ID;
    }

    @Override
    public long getParentId() {
      return -1000;
    }

    @Override
    public long getTransientId() {
      return -1002;
    }

    @Override
    public int getChildrenCount() {
      return -1;
    }

    @Override
    public long getParentTransientId() {
      return -1000;
    }

    @Override
    public Type classify() {
      return Type.DATAFLOW;
    }
  }
}

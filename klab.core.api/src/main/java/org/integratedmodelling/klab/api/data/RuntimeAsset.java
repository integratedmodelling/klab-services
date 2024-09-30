package org.integratedmodelling.klab.api.data;

/**
 * A runtime asset is anything that can be part of the {@link KnowledgeGraph} managed by the
 * {@link org.integratedmodelling.klab.api.digitaltwin.DigitalTwin}. Runtime assets are the nodes in the
 * knowledge graph, which guarantees the consistency of all assets and their connection to provenance.
 * <p>
 * For now this is just a tag interface. It will contain methods to subscribe to events relative to the
 * asset through the messaging subsystem.
 */
public interface RuntimeAsset {
    // TODO listeners and subscriptions mechanism
}

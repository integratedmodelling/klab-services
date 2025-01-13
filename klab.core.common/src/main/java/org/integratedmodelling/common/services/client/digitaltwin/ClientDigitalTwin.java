package org.integratedmodelling.common.services.client.digitaltwin;

import org.integratedmodelling.common.services.client.scope.ClientContextScope;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.StateStorage;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Message;

/**
 * Client-side digital twin, connected to the actual DT through the messages it gets from it (and
 * nothing more for now). Will NOT reconstruct the entire runtime-side DT and many functions will
 * throw exxeptions at the beginning. Later we may implement a lazy request-driven strategy to
 * obtain the rest, but requesting entire knowledge graphs should not be expected to work at client
 * side for now. Same for state storage if it needs to be used with any level of efficiency - the
 * implementation should be limited to statistical info.
 */
public class ClientDigitalTwin implements DigitalTwin {

  private final ClientContextScope scope;

  public ClientDigitalTwin(ClientContextScope scope, String id) {
    this.scope = scope;
    scope.installQueueConsumer(id, Message.Queue.Events, this::ingest);
  }

  /**
   * Main function that constructs the client-side KG structure. Not all elements in the remote KG
   * will be present, but those that are must be coherently linked.
   *
   * @param event
   */
  public void ingest(Message event) {
    // TODO
    System.out.println("CLIENT-SIDE DT INGESTING " + event);
  }

  @Override
  public KnowledgeGraph knowledgeGraph() {
    return null;
  }

  @Override
  public Provenance getProvenanceGraph(ContextScope context) {
    return null;
  }

  @Override
  public Dataflow<Observation> getDataflowGraph(ContextScope context) {
    return null;
  }

  @Override
  public StateStorage stateStorage() {
    return null;
  }

  @Override
  public boolean ingest(Data data, Observation target, ContextScope scope) {
    // should never be called on the client, at least with the current logic. Technically it is
    // possible for this to operate in client mode.
    throw new KlabIllegalStateException("ingest() called on a client-side digital twin");
  }

  @Override
  public void dispose() {}
}

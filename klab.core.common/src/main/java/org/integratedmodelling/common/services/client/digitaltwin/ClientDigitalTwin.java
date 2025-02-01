package org.integratedmodelling.common.services.client.digitaltwin;

import org.integratedmodelling.common.services.client.runtime.RuntimeClient;
import org.integratedmodelling.common.services.client.scope.ClientContextScope;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.digitaltwin.StateStorage;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
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

  private final ContextScope scope;
  private ClientKnowledgeGraph knowledgeGraph;
  private RuntimeService runtimeClient;

  public ClientDigitalTwin(ContextScope scope, String id) {
    this.scope = scope;
    this.runtimeClient = scope.getService(RuntimeService.class);
    if (this.runtimeClient instanceof RuntimeClient rc) {
      this.knowledgeGraph = new ClientKnowledgeGraph(scope, rc);
      if (scope instanceof ClientContextScope clientContextScope) {
        clientContextScope.installQueueConsumer(id, Message.Queue.Events, this::ingest);
      }
    } else {
      throw new KlabInternalErrorException("Non-client runtime class in client digital twin");
    }
  }

  /**
   * Main function that constructs the client-side KG structure. Not all elements in the remote KG
   * will be present, but those that are must be coherently linked.
   *
   * @param event
   */
  public void ingest(Message event) {
    // TODO build activity tree and inform any UI listeners in the scope
    //    System.out.println("ACTIVITY " + event);
  }

  @Override
  public KnowledgeGraph getKnowledgeGraph() {
    return knowledgeGraph;
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
  public StateStorage getStateStorage() {
    // TODO should throw an exception I guess - images and stats should come from the KG
    return null;
  }

  @Override
  public Scheduler getScheduler() {
    // should never be called on the client, at least with the current logic. Technically it is
    // possible for this to operate in client mode.
    throw new KlabIllegalStateException("getScheduler() called on a client-side digital twin");
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

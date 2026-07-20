package org.integratedmodelling.common.authentication.scope;

import org.integratedmodelling.klab.api.actors.Agent;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.scope.ReactiveScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Message;

/**
 * A scope that can hosts an agent refence and will route messages with {@link
 * org.integratedmodelling.klab.api.services.runtime.Message.MessageClass#AgentCommunication} class
 * to them instead of sending through the normal channels.
 */
public abstract class AbstractReactiveScopeImpl extends MessagingChannelImpl
    implements ReactiveScope {

  protected Agent agent;
  private String hostServiceId;

  public AbstractReactiveScopeImpl(Identity identity, boolean isSender, boolean isReceiver) {
    super(identity, isSender, isReceiver);
  }

  @Override
  public Agent getAgent() {
    return this.agent;
  }

  public void setAgent(Agent agent) {
    this.agent = agent;
  }

  @Override
  public Message send(Object... args) {
    var message = Message.create(this, args);
    if (message.getMessageClass() == Message.MessageClass.AgentCommunication
        && agent != null
        && agent.isAlive()) {
      agent.tell(message);
      return message;
    } else {
      return super.send(args);
    }
  }

  @Override
  public <T extends Scope> T getParentScope(Type type, Class<T> scopeClass) {
    Scope scope = this.getParentScope();
    while (scope != null) {
      if (scope.getType() == type) {
        return (T) scope;
      }
      scope = scope.getParentScope();
    }
    return null;
  }

  @Override
  public String getHostServiceId() {
    return hostServiceId;
  }

  public void setHostServiceId(String hostServiceId) {
    this.hostServiceId = hostServiceId;
  }
}

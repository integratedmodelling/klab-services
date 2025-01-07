package org.integratedmodelling.klab.api.services.runtime.impl;

import org.integratedmodelling.klab.api.services.runtime.Message;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class MatchImpl implements Message.Match {

  private Set<Message.MessageClass> applicableClasses = EnumSet.noneOf(Message.MessageClass.class);
  private Set<Message.MessageType> applicableTypes = EnumSet.noneOf(Message.MessageType.class);
  private Set<Message.Queue> applicableQueues = EnumSet.noneOf(Message.Queue.class);
  private Predicate<Message> messagePredicate;
  private Consumer<Message> messageConsumer;
  private Object payloadMatch;
  boolean persistent = false;

  @Override
  public Message.Match when(Predicate<Message> predicate) {
    this.messagePredicate = predicate;
    return this;
  }

  @Override
  public Message.Match persistent(boolean persistent) {
    this.persistent = persistent;
    return this;
  }

  @Override
  public Message.Match thenDo(Consumer<Message> consumer) {
    this.messageConsumer = consumer;
    return this;
  }

  @Override
  public Set<Message.MessageClass> getApplicableClasses() {
    return applicableClasses;
  }

  @Override
  public Set<Message.MessageType> getApplicableTypes() {
    return applicableTypes;
  }

  @Override
  public Set<Message.Queue> getApplicableQueues() {
    return applicableQueues;
  }

  @Override
  public Consumer<Message> getMessageConsumer() {
    return messageConsumer;
  }

  @Override
  public boolean isPersistent() {
    return persistent;
  }

  @Override
  public Object getPayloadMatch() {
    return payloadMatch;
  }

  @Override
  public Predicate<Message> getMessagePredicate() {
    return messagePredicate;
  }

  public static MatchImpl create(Object... args) {
    var ret = new MatchImpl();
    if (args != null) {
      for (var arg : args) {
        if (arg instanceof Message.MessageType type) {
          ret.applicableTypes.add(type);
        } else if (arg instanceof Message.MessageClass type) {
          ret.applicableClasses.add(type);
        } else if (arg instanceof Message.Queue queue) {
          ret.applicableQueues.add(queue);
        } else {
          ret.payloadMatch = arg;
        }
      }
    }
    return ret;
  }
}

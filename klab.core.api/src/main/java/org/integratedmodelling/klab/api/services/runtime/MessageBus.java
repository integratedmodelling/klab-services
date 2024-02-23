package org.integratedmodelling.klab.api.services.runtime;

import org.integratedmodelling.klab.api.scope.Scope;

import java.util.Collection;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * A message bus is a channel that a scope can use to send/receive messages so that a peer scope can be
 * defined at a remote end. The send() function in the scope will be notified by anything that is sent to the
 * send() method of the remote peer. Implementation can decide whether the logging messages should result in
 * an exchange of notifications.
 *
 * @author ferdinando.villa
 */
public interface MessageBus {

    /**
     * Explicitly subscribe a scope to the message bus. Its send() method will be notified of all incoming
     * messages from the connected peer scope.
     *
     * @param scope
     */
    void subscribe(Scope scope);

    void unsubscribe(Scope scope);

    /**
     * Post a message asynchronously to all subscribed scopes. A response may or may not be sent. Any
     * subscribers will be notified. If get() is called on the result and a response is not sent, the calling
     * side may deadlock.
     *
     * @param message
     */
    void post(Message message);

    /**
     * Post a message asynchronously and return a future to access the response message. If get() is called on
     * the result and a response is not sent, the calling side may deadlock.
     *
     * @param message
     */
    Future<Message> ask(Message message);

    /**
     * Post a message with a specified response handler. If this one is used, the subscriber is expected to
     * send a response, which will be handled by the passed responder when it is sent.
     *
     * @param message
     * @param responder
     */
    void post(Message message, Consumer<Message> responder);

    Collection<Scope> getReceivers(String identity);

}

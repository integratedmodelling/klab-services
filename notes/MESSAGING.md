# Messaging setup

* Messaging is only available for federated users. The federation data contain the federation ID and the broker URL.
  They come from a user group that is managed by the administrator and is part of the user profile.
* If the user is not part of a federation and local services are started, a federation with ID
  `Federation.LOCAL_FEDERATION_ID` is created and a broker is started within the local runtime, so that a local
  messaging service can be used.
* All messaging is managed in scopes through the `AMQPChannel` class. One channel is created as part of the
  `MessagingChannelImpl` class, from which all scopes derive.
* The channel connects to an `exchange` whose ID define the destination of messages. Currently the exchange is a FANOUT
  exchange, which means that all messages are delivered to all subscribers to the same exchange.
* The content of each message is expected to be a JSON object representing a `Message` object. The message belongs to a
  `Queue` that is used for filtering. The queues that are sent through the messaging system are negotiated through a
  QUEUES_HEADER when the scopes are created: the client sends a list of desired queues in the header, the service
  records the queues and validates them, then the header is sent back to the client which records the queues that have
  been accepted. The default queues are Events, Errors and Warnings.
* The entire system should not depend on having certain queues available. The modeler and engine will use the `Events`
  queue to provide logging and to maintain the `ClientDigitalTwin`, which will not contain a complete knowledge graph
  unless the `Events` queue is available.
* All scopes receive a AMQPChannel for communication, but the channel is activated and connected only in UserScope (with
  the ID of the federation as the exchange name) and ContextScope (with the ID of the context scope as the exchange
  name).
* At service side, the channel is only activated in the runtime service. In other services, the messaging is disabled.

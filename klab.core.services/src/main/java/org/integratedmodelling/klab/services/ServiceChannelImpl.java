package org.integratedmodelling.klab.services;

import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import org.integratedmodelling.common.authentication.scope.ChannelImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.impl.ScopeOptions;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import org.springframework.web.socket.sockjs.frame.Jackson2SockJsMessageCodec;

import java.security.Provider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Server-side messaging channel. Gets a websocket supplier and channels send() through it if the template is
 * provided.
 */
public class ServiceChannelImpl extends ChannelImpl {

    // default provenance for notifications: set to Forwarded so that client messages are not sent to server
    private Message.ForwardingPolicy notificationProvenance = Message.ForwardingPolicy.DoNotForward;
    private Supplier<SimpMessagingTemplate> websocketSupplier;

    public ServiceChannelImpl(Identity identity) {
        super(identity);
    }

    @Override
    public void info(Object... info) {
        if (isPaired()) {
            var notification = Notification.create(info);
            if (notification.getForwardingPolicy() == Message.ForwardingPolicy.Forward) {
                send(Message.MessageClass.Notification, Message.MessageType.Info, notificationProvenance,
                        notification);
            }
        } else {
            Logging.INSTANCE.info(info);
        }
    }

    @Override
    public void warn(Object... o) {
        if (isPaired()) {
            var notification = Notification.create(o);
            if (notification.getForwardingPolicy() == Message.ForwardingPolicy.Forward) {
                send(Message.MessageClass.Notification, Message.MessageType.Warning, notificationProvenance,
                        notification);
            }
        } else {
            Logging.INSTANCE.warn(o);
        }
    }

    @Override
    public void error(Object... o) {
        //        errors.set(true);
        if (isPaired()) {
            var notification = Notification.create(o);
            if (notification.getForwardingPolicy() == Message.ForwardingPolicy.Forward) {
                send(Message.MessageClass.Notification, Message.MessageType.Error, notificationProvenance,
                        notification);
            }
        } else {
            Logging.INSTANCE.error(o);
        }
    }

    @Override
    public void debug(Object... o) {
        if (!isPaired()) {
            var notification = Notification.create(o);
            if (notification.getForwardingPolicy() == Message.ForwardingPolicy.Forward) {
                send(Message.MessageClass.Notification, Message.MessageType.Debug, notificationProvenance,
                        notification);
            }
        } else {
            Logging.INSTANCE.debug(o);
        }
    }


    /**
     * This returning true doesn't mean anyone is listening
     *
     * @return
     */
    public boolean isPaired() {
        return websocketSupplier != null && websocketSupplier.get() != null;
    }

//    @Override
//    public boolean connect(KlabService service) {
//        return true;
//    }
//
//    @Override
//    public boolean disconnect(KlabService service) {
//        return true;
//    }

    @Override
    public Message send(Object... message) {
        var ret = super.send(message);
        // TODO must have TEMPLATE and call convertAndSend()
        if (websocketSupplier != null && ret.getForwardingPolicy() == Message.ForwardingPolicy.Forward) {
            var websocket = websocketSupplier.get();
            if (websocket != null) {
                websocket.convertAndSend("/klab", ret);
            }
        }
        return ret;
    }

    @Override
    public Message post(Consumer<Message> handler, Object... message) {
        var ret = super.post(handler, message);
        if (websocketSupplier != null && ret.getForwardingPolicy() == Message.ForwardingPolicy.Forward) {
            var websocket = websocketSupplier.get();
            if (websocket != null) {
                websocket.convertAndSend("/klab", ret);
            }
        }
        return ret;
    }

    public void setWebsocketProvider(Supplier<SimpMessagingTemplate> o) {
        this.websocketSupplier = o;
    }
}
